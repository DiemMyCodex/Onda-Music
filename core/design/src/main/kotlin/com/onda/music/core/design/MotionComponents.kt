package com.onda.music.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

object DockMetrics {
    val NavBarHeight: Dp = 64.dp
    val MiniPlayerHeight: Dp = 62.dp
    val ContentClearance: Dp = 34.dp
    val MinTapTarget: Dp = 48.dp

    fun contentBottomPadding(
        hasMiniPlayer: Boolean,
        navigationBarInset: Dp,
        navBarHeight: Dp = NavBarHeight,
        miniPlayerHeight: Dp = MiniPlayerHeight,
        extraClearance: Dp = ContentClearance
    ): Dp {
        val playerPart = if (hasMiniPlayer) miniPlayerHeight else 0.dp
        return (navBarHeight + playerPart + navigationBarInset + extraClearance)
            .coerceAtLeast(0.dp)
    }

    @Composable
    fun rememberBottomPadding(
        hasMiniPlayer: Boolean,
        navBarHeight: Dp = NavBarHeight,
        miniPlayerHeight: Dp = MiniPlayerHeight,
        extraClearance: Dp = ContentClearance
    ): Dp {
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        return contentBottomPadding(
            hasMiniPlayer = hasMiniPlayer,
            navigationBarInset = navInset,
            navBarHeight = navBarHeight,
            miniPlayerHeight = miniPlayerHeight,
            extraClearance = extraClearance
        )
    }

    @Composable
    fun rememberContentPadding(
        hasMiniPlayer: Boolean,
        horizontal: Dp = 16.dp,
        top: Dp = 0.dp,
        extraClearance: Dp = ContentClearance
    ): PaddingValues {
        val bottom = rememberBottomPadding(
            hasMiniPlayer = hasMiniPlayer,
            extraClearance = extraClearance
        )
        return PaddingValues(
            start = horizontal,
            top = top,
            end = horizontal,
            bottom = bottom
        )
    }
}

@Composable
fun Modifier.dockBottomPadding(
    hasMiniPlayer: Boolean,
    extraClearance: Dp = DockMetrics.ContentClearance
): Modifier {
    val bottom = DockMetrics.rememberBottomPadding(
        hasMiniPlayer = hasMiniPlayer,
        extraClearance = extraClearance
    )
    return this.padding(bottom = bottom)
}

fun Modifier.press3D(
    enabled: Boolean = true,
    maxTiltDegrees: Float = AppMotion.DefaultTiltDegrees,
    pressedScale: Float = AppMotion.DefaultPressScale
): Modifier = composed {
    if (!enabled) return@composed this
    val reducedMotion = OndaTheme.reducedMotion
    val scope = rememberCoroutineScope()
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    this
        .pointerInput(enabled, reducedMotion, maxTiltDegrees, pressedScale) {
            awaitPointerEventScope {
                var wasPressed = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val activePointer = event.changes.firstOrNull { it.pressed }
                    if (activePointer != null) {
                        val w = size.width.coerceAtLeast(1).toFloat()
                        val h = size.height.coerceAtLeast(1).toFloat()
                        val nx = ((activePointer.position.x / w) * 2f - 1f).coerceIn(-1f, 1f)
                        val ny = ((activePointer.position.y / h) * 2f - 1f).coerceIn(-1f, 1f)
                        val targetScale = if (reducedMotion) 1f else pressedScale
                        val targetRotX = if (reducedMotion) 0f else -ny * maxTiltDegrees
                        val targetRotY = if (reducedMotion) 0f else nx * maxTiltDegrees

                        if (!wasPressed) {
                            wasPressed = true
                            scope.launch { scale.animateTo(targetScale, AppMotion.SnappySpring) }
                        }
                        scope.launch {
                            rotX.snapTo(targetRotX)
                            rotY.snapTo(targetRotY)
                        }
                    } else if (wasPressed) {
                        wasPressed = false
                        scope.launch { rotX.animateTo(0f, AppMotion.ExpressiveSpring) }
                        scope.launch { rotY.animateTo(0f, AppMotion.ExpressiveSpring) }
                        scope.launch { scale.animateTo(1f, AppMotion.ExpressiveSpring) }
                    }
                }
            }
        }
        .graphicsLayer {
            rotationX = rotX.value
            rotationY = rotY.value
            scaleX = scale.value
            scaleY = scale.value
            cameraDistance = 12f * density
        }
}

private class TextHistoryRef(var value: String)

@Composable
fun OdometerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val reducedMotion = OndaTheme.reducedMotion
    val resolvedStyle = remember(style) {
        style.copy(fontFeatureSettings = "tnum")
    }
    val previousText = remember { TextHistoryRef(text) }
    val increasing = remember(text) {
        val prevNum = previousText.value.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val currNum = text.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val result = currNum >= prevNum
        previousText.value = text
        result
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        text.forEachIndexed { index, char ->
            val fromRightIndex = text.length - index
            androidx.compose.runtime.key(fromRightIndex) {
                if (reducedMotion || !char.isDigit()) {
                    Text(
                        text = char.toString(),
                        style = resolvedStyle,
                        color = color,
                        maxLines = 1
                    )
                } else {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val direction = if (increasing) 1 else -1
                            (slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = 520f
                                )
                            ) { fullHeight -> direction * fullHeight } + fadeIn(tween(110)))
                                .togetherWith(
                                    slideOutVertically(
                                        animationSpec = spring(
                                            dampingRatio = 0.85f,
                                            stiffness = 520f
                                        )
                                    ) { fullHeight -> -direction * fullHeight } + fadeOut(tween(90))
                                )
                                .using(SizeTransform(clip = true))
                        },
                        label = "OdometerDigit"
                    ) { targetChar ->
                        Text(
                            text = targetChar.toString(),
                            style = resolvedStyle,
                            color = color,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WavySeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekFinished: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    bufferedProgress: Float = 0f,
    activeColor: Color = OndaTheme.colors.accent,
    trackColor: Color = GlassTokens.GlassHighlight.copy(alpha = 0.16f),
    bufferedColor: Color = GlassTokens.GlassHighlight.copy(alpha = 0.28f),
    thumbColor: Color = GlassTokens.TextPrimary,
    waveAmplitude: Dp = 3.dp,
    waveLength: Dp = 22.dp,
    trackThickness: Dp = 3.dp,
    enabled: Boolean = true
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    val rubberBandPx = remember { Animatable(0f) }
    var phaseRadians by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val displayedProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)
    val maxRubberBandPx = with(density) { 18.dp.toPx() }

    val animatedAmplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && !reducedMotion) 1f else 0f,
        animationSpec = AppMotion.GentleSpring,
        label = "WaveAmplitude"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1f,
        animationSpec = AppMotion.SnappySpring,
        label = "ThumbScale"
    )

    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                    phaseRadians = (phaseRadians + dt * 5.2f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .height(DockMetrics.MinTapTarget)
            .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1).toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    dragProgress = fraction
                    onSeek(fraction)
                    onSeekFinished?.invoke()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val fraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        dragProgress = fraction
                        onSeek(fraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val raw = change.position.x / size.width.coerceAtLeast(1).toFloat()
                        val clamped = raw.coerceIn(0f, 1f)
                        dragProgress = clamped
                        onSeek(clamped)

                        val overscroll = when {
                            raw < 0f -> raw * trackWidthPx
                            raw > 1f -> (raw - 1f) * trackWidthPx
                            else -> 0f
                        }
                        val damped = if (overscroll == 0f) {
                            0f
                        } else {
                            maxRubberBandPx * tanh(overscroll / (maxRubberBandPx * 2.5f))
                        }
                        scope.launch { rubberBandPx.snapTo(damped) }
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeekFinished?.invoke()
                        scope.launch {
                            rubberBandPx.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = 420f
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        scope.launch {
                            rubberBandPx.animateTo(0f, AppMotion.ExpressiveSpring)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val wavePath = remember { Path() }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = rubberBandPx.value
                }
        ) {
            val strokePx = with(density) { trackThickness.toPx() }
            val padPx = with(density) { 8.dp.toPx() }
            val startX = padPx
            val endX = (size.width - padPx).coerceAtLeast(startX + 1f)
            val usableWidth = endX - startX
            val centerY = size.height * 0.5f

            val clampedBuffered = bufferedProgress.coerceIn(0f, 1f)
            val activeEndX = startX + usableWidth * displayedProgress
            val bufferedEndX = startX + usableWidth * clampedBuffered

            drawLine(
                color = trackColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )

            if (clampedBuffered > displayedProgress) {
                drawLine(
                    color = bufferedColor,
                    start = Offset(activeEndX, centerY),
                    end = Offset(bufferedEndX, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            val ampPx = with(density) { waveAmplitude.toPx() } * animatedAmplitudeFactor
            val wavelengthPx = with(density) { waveLength.toPx() }.coerceAtLeast(8f)

            if (ampPx < 0.25f) {
                drawLine(
                    color = activeColor,
                    start = Offset(startX, centerY),
                    end = Offset(activeEndX, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            } else {
                wavePath.reset()
                val stepPx = 3f
                var x = startX
                val twoPiOverLambda = (2f * PI.toFloat()) / wavelengthPx
                var started = false
                while (x <= activeEndX) {
                    val distFromEdge = minOf(x - startX, activeEndX - x)
                    val edgeEnvelope = (distFromEdge / 16f).coerceIn(0f, 1f)
                    val y = centerY + sin((x - startX) * twoPiOverLambda - phaseRadians) * ampPx * edgeEnvelope
                    if (!started) {
                        wavePath.moveTo(x, y)
                        started = true
                    } else {
                        wavePath.lineTo(x, y)
                    }
                    x += stepPx
                }
                if (started) {
                    wavePath.lineTo(activeEndX, centerY)
                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            val thumbRadius = with(density) { 6.5.dp.toPx() } * thumbScale
            drawCircle(
                color = activeColor.copy(alpha = 0.28f),
                radius = thumbRadius * 1.65f,
                center = Offset(activeEndX, centerY)
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(activeEndX, centerY)
            )
        }
    }
}

@Stable
data class BurstParticle(
    val id: Int,
    val startOffset: Offset,
    val controlOffset: Offset,
    val endOffset: Offset,
    val sizeDp: Float,
    val isHeart: Boolean,
    val tint: Color,
    val spinDegrees: Float
)

@Stable
class LikeBurstState {
    internal var burstId by mutableIntStateOf(0)
        private set
    internal var origin by mutableStateOf<Offset?>(null)
        private set
    internal val particles = mutableStateListOf<BurstParticle>()

    fun trigger(
        origin: Offset? = null,
        primaryColor: Color = GlassTokens.WaveCyan,
        secondaryColor: Color = GlassTokens.ErrorCoral
    ) {
        this.origin = origin
        particles.clear()
        val count = 12
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() + ((i % 3) - 1) * 0.14f
            val distance = 58f + (i % 4) * 24f
            val end = Offset(cos(angle) * distance, sin(angle) * distance - 18f)
            val ctrlAngle = angle + if (i % 2 == 0) 0.42f else -0.42f
            val ctrlDist = distance * 0.56f
            val ctrl = Offset(cos(ctrlAngle) * ctrlDist, sin(ctrlAngle) * ctrlDist - 26f)
            particles.add(
                BurstParticle(
                    id = i,
                    startOffset = Offset.Zero,
                    controlOffset = ctrl,
                    endOffset = end,
                    sizeDp = if (i % 2 == 0) 8f else 5.5f,
                    isHeart = i % 2 == 0,
                    tint = when (i % 3) {
                        0 -> secondaryColor
                        1 -> primaryColor
                        else -> GlassTokens.IceBlue
                    },
                    spinDegrees = if (i % 2 == 0) 36f else -28f
                )
            )
        }
        burstId += 1
    }

    companion object {
        fun quadraticBezier(
            p0: Offset,
            p1: Offset,
            p2: Offset,
            t: Float
        ): Offset {
            val u = 1f - t
            val tt = t * t
            val uu = u * u
            val twoUt = 2f * u * t
            return Offset(
                x = uu * p0.x + twoUt * p1.x + tt * p2.x,
                y = uu * p0.y + twoUt * p1.y + tt * p2.y
            )
        }
    }
}

@Composable
fun rememberLikeBurstState(): LikeBurstState {
    return remember { LikeBurstState() }
}

@Composable
fun LikeBurstOverlay(
    state: LikeBurstState,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val progress = remember { Animatable(0f) }

    LaunchedEffect(state.burstId) {
        if (state.burstId == 0) return@LaunchedEffect
        if (reducedMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 640, easing = FastOutSlowInEasing)
        )
    }

    if (state.burstId == 0 || (progress.value >= 1f && !progress.isRunning)) return

    val heartPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value.coerceIn(0f, 1f)
        val anchor = state.origin ?: Offset(size.width * 0.5f, size.height * 0.5f)
        val ringAlpha = (1f - t).coerceIn(0f, 1f) * 0.55f
        val ringRadius = (12.dp.toPx() + 42.dp.toPx() * t)
        drawCircle(
            color = GlassTokens.ErrorCoral.copy(alpha = ringAlpha),
            radius = ringRadius,
            center = anchor,
            style = Stroke(width = 2.dp.toPx())
        )

        val alpha = if (t < 0.7f) 1f else ((1f - t) / 0.3f).coerceIn(0f, 1f)
        val scale = if (t < 0.25f) t / 0.25f else 1f - ((t - 0.25f) * 0.45f)

        state.particles.forEach { particle ->
            val rel = LikeBurstState.quadraticBezier(
                p0 = particle.startOffset,
                p1 = particle.controlOffset,
                p2 = particle.endOffset,
                t = t
            )
            val center = Offset(
                x = anchor.x + rel.x.dp.toPx() * 0.35f,
                y = anchor.y + rel.y.dp.toPx() * 0.35f
            )
            val particleSizePx = particle.sizeDp.dp.toPx() * scale.coerceAtLeast(0.1f)
            val tint = particle.tint.copy(alpha = alpha)

            if (particle.isHeart) {
                rotate(degrees = particle.spinDegrees * t, pivot = center) {
                    val half = particleSizePx * 0.5f
                    heartPath.reset()
                    heartPath.moveTo(center.x, center.y + half * 0.85f)
                    heartPath.cubicTo(
                        center.x - half * 1.4f, center.y - half * 0.1f,
                        center.x - half * 0.7f, center.y - half * 1.15f,
                        center.x, center.y - half * 0.35f
                    )
                    heartPath.cubicTo(
                        center.x + half * 0.7f, center.y - half * 1.15f,
                        center.x + half * 1.4f, center.y - half * 0.1f,
                        center.x, center.y + half * 0.85f
                    )
                    heartPath.close()
                    drawPath(path = heartPath, color = tint)
                }
            } else {
                drawCircle(
                    color = tint,
                    radius = particleSizePx * 0.45f,
                    center = center
                )
            }
        }
    }
}
