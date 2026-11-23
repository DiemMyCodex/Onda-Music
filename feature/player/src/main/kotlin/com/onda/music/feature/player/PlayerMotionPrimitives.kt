package com.onda.music.feature.player

import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

internal object MorphGestureArbitrator {
    private val counter = AtomicLong(0L)

    @Volatile
    var activeToken: Long = 0L
        private set

    fun claim(): Long {
        val next = counter.incrementAndGet()
        activeToken = next
        return next
    }
}

internal suspend fun settleMorphFraction(
    token: Long,
    startFraction: Float,
    targetFraction: Float,
    velocityFractionPerSec: Float,
    reducedMotion: Boolean,
    onStep: (Float) -> Unit,
    onComplete: () -> Unit
) {
    if (MorphGestureArbitrator.activeToken != token) return
    val clampedStart = startFraction.coerceIn(0f, 1f)
    val clampedTarget = targetFraction.coerceIn(0f, 1f)
    val distance = abs(clampedTarget - clampedStart)
    if (reducedMotion || distance < 0.002f) {
        onComplete()
        return
    }
    val toward = if (clampedTarget > clampedStart) velocityFractionPerSec else -velocityFractionPerSec
    val omega = maxOf(
        8.94f,
        if (distance > 0.02f && toward > 0f) toward / distance else 0f
    ).coerceAtMost(42f)
    val spec = spring<Float>(dampingRatio = 1f, stiffness = omega * omega)
    val anim = Animatable(clampedStart)
    anim.animateTo(
        targetValue = clampedTarget,
        animationSpec = spec,
        initialVelocity = velocityFractionPerSec
    ) {
        if (MorphGestureArbitrator.activeToken != token) {
            throw CancellationException("Superseded by newer morph gesture")
        }
        onStep(value.coerceIn(0.006f, 0.994f))
    }
    if (MorphGestureArbitrator.activeToken == token) {
        onComplete()
    }
}

internal fun rubberBandOffset(over: Float, reach: Float): Float {
    if (over == 0f || reach <= 0f) return 0f
    val d = abs(over)
    return sign(over) * (1f - 1f / (d * 0.55f / reach + 1f)) * reach
}

@Composable
internal fun Modifier.playerPressBounce(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.85f,
    releaseDampingRatio: Float = 0.38f,
    releaseStiffness: Float = 520f,
    reducedMotion: Boolean = false
): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource, pressedScale, releaseDampingRatio, releaseStiffness, reducedMotion) {
        if (reducedMotion) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        var pressedCount = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressedCount++
                    launch {
                        scale.animateTo(
                            targetValue = pressedScale,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 1600f)
                        )
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressedCount = (pressedCount - 1).coerceAtLeast(0)
                    if (pressedCount == 0) {
                        launch {
                            if (scale.value > pressedScale + 0.03f) {
                                scale.animateTo(pressedScale, tween(65))
                            }
                            scale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = releaseDampingRatio,
                                    stiffness = releaseStiffness
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

internal object PlayerLineBlurCache {
    private const val STEP_PX = 0.5f
    private const val MAX_RADIUS_PX = 14f
    private val cache = arrayOfNulls<RenderEffect>((MAX_RADIUS_PX / STEP_PX).toInt() + 2)

    fun forRadiusPx(radiusPx: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radiusPx <= 0.1f) return null
        val step = (radiusPx / STEP_PX).roundToInt().coerceIn(1, cache.lastIndex)
        cache[step]?.let { return it }
        val quantized = step * STEP_PX
        return android.graphics.RenderEffect
            .createBlurEffect(quantized, quantized, Shader.TileMode.DECAL)
            .asComposeRenderEffect()
            .also { cache[step] = it }
    }
}

internal object PlayerSaturationFilterCache {
    private const val MIN_SAT = 0.70f
    private const val STEP = 0.01f
    private val buckets = arrayOfNulls<ColorFilter>(31)

    fun forSaturation(saturation: Float): ColorFilter? {
        val clamped = saturation.coerceIn(MIN_SAT, 1f)
        if (clamped >= 0.995f) return null
        val index = ((clamped - MIN_SAT) / STEP).roundToInt().coerceIn(0, buckets.lastIndex)
        buckets[index]?.let { return it }
        val quantized = MIN_SAT + index * STEP
        val matrix = ColorMatrix().apply { setToSaturation(quantized) }
        return ColorFilter.colorMatrix(matrix).also { buckets[index] = it }
    }
}

@Stable
internal class SeekFillFollower {
    var fraction by mutableFloatStateOf(0f)
        private set

    var widthPx: Float = 0f

    private var shown: Float = 0f
    private var velocity: Float = 0f

    suspend fun follow(
        target: () -> Float,
        moving: () -> Boolean,
        reducedMotion: () -> Boolean
    ) {
        shown = target().coerceIn(0f, 1f)
        velocity = 0f
        fraction = shown
        while (true) {
            snapshotFlow {
                moving() || abs(target().coerceIn(0f, 1f) - shown) > SETTLED_FRACTION
            }.first { it }
            if (reducedMotion()) {
                val snapped = target().coerceIn(0f, 1f)
                shown = snapped
                velocity = 0f
                fraction = snapped
                continue
            }
            var lastNanos = 0L
            while (true) {
                val settled = withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) {
                        1f / 120f
                    } else {
                        ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    }
                    lastNanos = now
                    val to = target().coerceIn(0f, 1f)
                    if (reducedMotion()) {
                        shown = to
                        velocity = 0f
                        fraction = to
                        true
                    } else {
                        step(to, dt)
                        !moving() && abs(to - shown) < SETTLED_FRACTION && abs(velocity) < SETTLED_SPEED
                    }
                }
                if (settled) {
                    val finalTarget = target().coerceIn(0f, 1f)
                    shown = finalTarget
                    velocity = 0f
                    fraction = finalTarget
                    break
                }
            }
        }
    }

    internal fun step(to: Float, dt: Float) {
        val y = shown - to
        val e = exp(-FILL_OMEGA * dt)
        val tmp = (velocity + FILL_OMEGA * y) * dt
        shown = to + (y + tmp) * e
        velocity = (velocity - FILL_OMEGA * tmp) * e
        val minStep = if (widthPx > 0f) 0.25f / widthPx else 0f
        if (abs(shown - fraction) >= minStep) {
            fraction = shown.coerceIn(0f, 1f)
        }
    }

    private companion object {
        const val FILL_OMEGA = 50f
        const val SETTLED_FRACTION = 0.0002f
        const val SETTLED_SPEED = 0.002f
    }
}

enum class OndaLikeSlot {
    PLAYER,
    POSTER,
    TIMELINE,
    LYRICS_STAGE,
    BEDSIDE_TILES
}

private val LikeRed = Color(0xFFFF2D55)
private val LikePink = Color(0xFFFF8FAB)

private val TravelEasing = CubicBezierEasing(0.3f, 1.22f, 0.5f, 1f)
private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val HomeEasing = CubicBezierEasing(0.5f, -0.18f, 0.7f, 1f)

@Stable
class OndaLikeBurstState {
    internal class Flight(
        val id: Long,
        val originWindow: Offset,
        val fromButton: Boolean,
        val seed: Int
    )

    internal val flights = mutableStateListOf<Flight>()
    internal val buttonBounds = HashMap<OndaLikeSlot, Rect>()
    internal val landing = Animatable(1f)
    var slotProvider: () -> OndaLikeSlot = { OndaLikeSlot.PLAYER }

    private var nextId = 0L

    internal fun homeTargetWindow(): Offset? = buttonBounds[slotProvider()]?.center

    fun likeFromButton(
        slot: OndaLikeSlot,
        currentlyLiked: Boolean,
        reducedMotion: Boolean,
        scope: CoroutineScope,
        onToggleLike: () -> Unit
    ) {
        if (!currentlyLiked) {
            if (reducedMotion) {
                scope.launch {
                    landing.snapTo(1f)
                    landing.animateTo(1.12f, tween(70))
                    landing.animateTo(1f, tween(110))
                }
            } else {
                val origin = buttonBounds[slot]?.center ?: homeTargetWindow()
                if (origin != null) {
                    launchFlight(origin, fromButton = true)
                }
            }
        }
        onToggleLike()
    }

    fun likeFromDoubleTap(
        windowPosition: Offset,
        currentlyLiked: Boolean,
        reducedMotion: Boolean,
        scope: CoroutineScope,
        onToggleLike: () -> Unit
    ) {
        if (reducedMotion) {
            scope.launch {
                landing.snapTo(1f)
                landing.animateTo(1.14f, tween(80))
                landing.animateTo(1f, tween(120))
            }
        } else {
            launchFlight(windowPosition, fromButton = false)
        }
        if (!currentlyLiked) {
            onToggleLike()
        }
    }

    private fun launchFlight(originWindow: Offset, fromButton: Boolean) {
        if (flights.size >= 4) {
            flights.removeAt(0)
        }
        flights.add(Flight(nextId++, originWindow, fromButton, Random.nextInt()))
    }
}

fun Modifier.ondaLikeTarget(
    state: OndaLikeBurstState?,
    slot: OndaLikeSlot
): Modifier = if (state == null) {
    this
} else {
    this
        .onGloballyPositioned { coords ->
            state.buttonBounds[slot] = coords.boundsInWindow()
        }
        .graphicsLayer {
            if (state.slotProvider() == slot) {
                val s = state.landing.value
                scaleX = s
                scaleY = s
            }
        }
}

private class HeartBurstParticle(
    val dx: Float,
    val dy: Float,
    val reachDp: Float,
    val scale: Float,
    val spinDeg: Float,
    val color: Color
)

@Composable
fun OndaLikeBurstOverlay(
    state: OndaLikeBurstState,
    modifier: Modifier = Modifier
) {
    val bounceScope = rememberCoroutineScope()
    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    if (state.flights.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayOriginInWindow = coords.positionInWindow()
            }
    ) {
        for (flight in state.flights) {
            key(flight.id) {
                HeartFlightCanvas(
                    state = state,
                    flight = flight,
                    overlayOrigin = { overlayOriginInWindow },
                    bounceScope = bounceScope,
                    onDone = { state.flights.remove(flight) }
                )
            }
        }
    }
}

@Composable
private fun HeartFlightCanvas(
    state: OndaLikeBurstState,
    flight: OndaLikeBurstState.Flight,
    overlayOrigin: () -> Offset,
    bounceScope: CoroutineScope,
    onDone: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val glyphScale = 18f / 100f

    val travel = remember { Animatable(0f) }
    val scale = remember { Animatable(if (flight.fromButton) glyphScale else 0f) }
    val redness = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val floatPhase = remember { Animatable(0f) }
    val home = remember { Animatable(0f) }

    val random = remember(flight.seed) { Random(flight.seed) }
    val side = remember(flight.seed) { if (random.nextBoolean()) 1f else -1f }
    val particles = remember(flight.seed) {
        val colors = listOf(LikeRed, LikePink, Color.White)
        List(8) { index ->
            val angle = index * 2f * PI.toFloat() / 8f + (random.nextFloat() - 0.5f) * 0.5f
            HeartBurstParticle(
                dx = cos(angle),
                dy = sin(angle),
                reachDp = 44f + random.nextInt(36),
                scale = 0.6f + random.nextFloat() * 0.5f,
                spinDeg = (random.nextFloat() - 0.5f) * 70f,
                color = colors[index % colors.size]
            )
        }
    }
    val heartPath = remember { Path() }

    LaunchedEffect(flight.id) {
        coroutineScope {
            if (!flight.fromButton) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            val popJob = launch { scale.animateTo(1f, tween(460, easing = PopEasing)) }
            travel.animateTo(1f, tween(460, easing = TravelEasing))
            popJob.join()

            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            launch { redness.animateTo(1f, tween(110)) }
            launch { burst.animateTo(1f, tween(620, easing = LinearOutSlowInEasing)) }
            scale.animateTo(1.32f, tween(110, easing = LinearOutSlowInEasing))
            launch { scale.animateTo(1f, spring(dampingRatio = 0.30f, stiffness = 360f)) }

            launch {
                delay(160L)
                redness.animateTo(0f, tween(520))
            }
            floatPhase.animateTo(1f, tween(640, easing = LinearEasing))

            home.animateTo(1f, tween(540, easing = HomeEasing))
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            bounceScope.launch {
                state.landing.snapTo(1f)
                state.landing.animateTo(1.3f, tween(90, easing = LinearOutSlowInEasing))
                state.landing.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f))
            }
        }
        onDone()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val originOffset = overlayOrigin()
        val origin = flight.originWindow - originOffset
        val center = Offset(size.width * 0.5f, size.height * 0.44f)
        val heartWidthPx = 100.dp.toPx()

        val travelControl = Offset(
            (origin.x + center.x) * 0.5f + side * 48.dp.toPx(),
            min(origin.y, center.y) - 110.dp.toPx()
        )
        val t = travel.value
        val inPos = quadBezier(origin, travelControl, center, t)

        val f = floatPhase.value
        fun bobOffset(phase: Float): Offset = Offset(
            x = sin(phase * 2f * PI.toFloat() * 1.25f) * 7.dp.toPx() * side,
            y = -sin(phase * 2f * PI.toFloat()) * 9.dp.toPx() - phase * 16.dp.toPx()
        )
        val floatPos = center + bobOffset(f)
        val floatEnd = center + bobOffset(1f)

        val target = state.homeTargetWindow()?.minus(originOffset) ?: origin
        val h = home.value
        val homeControl = Offset(lerp(floatEnd.x, target.x, 0.25f), floatEnd.y - 90.dp.toPx())
        val pos = when {
            h != 0f -> quadBezier(floatEnd, homeControl, target, h)
            f > 0f -> floatPos
            else -> inPos
        }

        val wobble = sin(f * 2f * PI.toFloat() * 1.5f) * 8f
        val rotation = when {
            h != 0f -> lerp(wobble, 0f, h) + sin(h * PI.toFloat()) * 16f * sign(target.x - floatEnd.x)
            f > 0f -> wobble
            else -> lerp(-22f * side, 0f, t.coerceIn(0f, 1f))
        }

        val homeShrink = lerp(1f, glyphScale, h * h)
        val s = scale.value * homeShrink
        val squash = (scale.value - 1f).coerceAtLeast(0f)
        val sx = s * (1f + squash * 0.28f)
        val sy = s * (1f - squash * 0.14f)
        val alpha = 1f - ((h - 0.84f) / 0.16f).coerceIn(0f, 1f)
        val red = redness.value
        val heartColor = lerp(Color.White, LikeRed, red)

        drawHeartBurst(
            center = center,
            heartWidthPx = heartWidthPx,
            particles = particles,
            progress = burst.value,
            heartPath = heartPath
        )

        if (alpha > 0f && s > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.22f * alpha), Color.Transparent),
                    center = pos + Offset(0f, 8.dp.toPx() * s),
                    radius = heartWidthPx * 0.75f * s
                ),
                radius = heartWidthPx * 0.75f * s,
                center = pos + Offset(0f, 8.dp.toPx() * s)
            )
            if (red > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(LikeRed.copy(alpha = 0.45f * red * alpha), Color.Transparent),
                        center = pos,
                        radius = heartWidthPx * 0.95f * s
                    ),
                    radius = heartWidthPx * 0.95f * s,
                    center = pos
                )
            }

            withTransform({
                translate(pos.x, pos.y)
                rotate(rotation, pivot = Offset.Zero)
                scale(sx, sy, pivot = Offset.Zero)
            }) {
                drawVectorHeart(
                    path = heartPath,
                    halfSize = heartWidthPx * 0.5f,
                    color = heartColor.copy(alpha = alpha)
                )
            }
        }
    }
}

private fun DrawScope.drawHeartBurst(
    center: Offset,
    heartWidthPx: Float,
    particles: List<HeartBurstParticle>,
    progress: Float,
    heartPath: Path
) {
    if (progress <= 0f || progress >= 1f) return
    val out = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    val fade = 1f - progress

    drawCircle(
        color = lerp(LikeRed, Color.White, progress * 0.6f).copy(alpha = 0.7f * fade),
        radius = heartWidthPx * 0.5f * lerp(0.7f, 1.9f, out),
        center = center,
        style = Stroke(width = lerp(5.dp.toPx(), 0.5.dp.toPx(), progress))
    )

    val particleHalfPx = 7.dp.toPx()
    val startDist = heartWidthPx * 0.32f
    for (p in particles) {
        val distance = startDist + p.reachDp.dp.toPx() * out
        val at = center + Offset(p.dx * distance, p.dy * distance)
        val s = p.scale * (1f - progress * 0.5f)
        withTransform({
            translate(at.x, at.y)
            rotate(p.spinDeg * progress, pivot = Offset.Zero)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawVectorHeart(
                path = heartPath,
                halfSize = particleHalfPx,
                color = p.color.copy(alpha = fade)
            )
        }
    }
}

private fun DrawScope.drawVectorHeart(
    path: Path,
    halfSize: Float,
    color: Color
) {
    path.reset()
    path.moveTo(0f, halfSize * 0.85f)
    path.cubicTo(
        -halfSize * 1.45f, -halfSize * 0.12f,
        -halfSize * 0.72f, -halfSize * 1.15f,
        0f, -halfSize * 0.36f
    )
    path.cubicTo(
        halfSize * 0.72f, -halfSize * 1.15f,
        halfSize * 1.45f, -halfSize * 0.12f,
        0f, halfSize * 0.85f
    )
    path.close()
    drawPath(path = path, color = color)
}

private fun quadBezier(a: Offset, control: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        x = u * u * a.x + 2f * u * t * control.x + t * t * b.x,
        y = u * u * a.y + 2f * u * t * control.y + t * t * b.y
    )
}
