package com.onda.music.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI

private const val LaunchRiseDurationMs = 480f
private const val LaunchRiseStaggerMs = 55f
private const val LaunchRiseOrderCount = 4
private const val LaunchRiseTotalMs = LaunchRiseDurationMs + LaunchRiseStaggerMs * (LaunchRiseOrderCount - 1)
private val LaunchRiseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val LaunchOverlayEasing = CubicBezierEasing(0.32f, 0f, 0.12f, 1f)

fun ondaLaunchWindowProgress(
    elapsedMs: Float,
    startMs: Float,
    durationMs: Float
): Float {
    if (durationMs <= 0f) return if (elapsedMs >= startMs) 1f else 0f
    return ((elapsedMs - startMs) / durationMs).coerceIn(0f, 1f)
}

fun ondaLaunchRiseOrderProgress(riseProgress: Float, order: Int): Float {
    val clampedOrder = order.coerceIn(0, LaunchRiseOrderCount - 1)
    val clampedProgress = riseProgress.coerceIn(0f, 1f)
    val local = ((clampedProgress * LaunchRiseTotalMs - clampedOrder * LaunchRiseStaggerMs) / LaunchRiseDurationMs)
        .coerceIn(0f, 1f)
    return LaunchRiseEasing.transform(local)
}

@Stable
class OndaLaunchChoreographyState(playOnStart: Boolean) {
    var isSettled by mutableStateOf(!playOnStart)
        private set

    var isContentReady by mutableStateOf(!playOnStart)
        private set

    val overlayProgress = Animatable(if (playOnStart) 0f else 1f)
    val riseProgress = Animatable(if (playOnStart) 0f else 1f)

    fun orderProgress(order: Int): Float {
        return if (isSettled) 1f else ondaLaunchRiseOrderProgress(riseProgress.value, order)
    }

    fun markContentReady() {
        isContentReady = true
    }

    fun markSettled() {
        isSettled = true
    }
}

val LocalOndaLaunchChoreography = staticCompositionLocalOf<OndaLaunchChoreographyState?> { null }

@Composable
fun rememberOndaLaunchChoreographyState(playOnStart: Boolean): OndaLaunchChoreographyState {
    return remember { OndaLaunchChoreographyState(playOnStart = playOnStart) }
}

@Composable
fun Modifier.ondaLaunchRise(
    order: Int,
    distance: Dp = 44.dp
): Modifier {
    val state = LocalOndaLaunchChoreography.current ?: return this
    if (state.isSettled || OndaTheme.reducedMotion) return this
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    return this.graphicsLayer {
        val e = state.orderProgress(order)
        alpha = e.coerceIn(0f, 1f)
        translationY = (1f - e) * distancePx
        val s = 0.985f + 0.015f * e
        scaleX = s
        scaleY = s
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

@Composable
fun OndaLaunchSplashOverlay(
    state: OndaLaunchChoreographyState,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    if (state.isSettled) return

    val ringProgress = remember(state) { Animatable(0f) }
    val waveProgress = remember(state) { Animatable(0f) }
    val dotProgress = remember(state) { Animatable(0f) }
    val wordmarkProgress = remember(state) { Animatable(0f) }
    val hairlineProgress = remember(state) { Animatable(0f) }
    val ripple1Progress = remember(state) { Animatable(0f) }
    val ripple2Progress = remember(state) { Animatable(0f) }
    val breathStrength = remember(state) { Animatable(0f) }
    var breathPhase by remember(state) { mutableFloatStateOf(0f) }

    LaunchedEffect(state, reducedMotion) {
        if (reducedMotion) {
            ringProgress.snapTo(1f)
            waveProgress.snapTo(1f)
            dotProgress.snapTo(1f)
            wordmarkProgress.snapTo(1f)
            hairlineProgress.snapTo(1f)
            state.riseProgress.snapTo(1f)
            withTimeoutOrNull(220L) {
                snapshotFlow { state.isContentReady }.first { it }
            }
            state.overlayProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 150)
            )
            state.markSettled()
            return@LaunchedEffect
        }

        withFrameNanos { }

        coroutineScope {
            launch {
                ringProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 340, easing = CubicBezierEasing(0.18f, 0.88f, 0.24f, 1f))
                )
            }
            launch {
                delay(130L)
                waveProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 390, easing = CubicBezierEasing(0.22f, 0f, 0.12f, 1f))
                )
            }
            launch {
                delay(360L)
                dotProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.46f, stiffness = 460f)
                )
            }
            launch {
                delay(360L)
                ripple1Progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 520, easing = CubicBezierEasing(0.16f, 0.84f, 0.30f, 1f))
                )
            }
            launch {
                delay(490L)
                ripple2Progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 540, easing = CubicBezierEasing(0.16f, 0.84f, 0.30f, 1f))
                )
            }
            launch {
                delay(250L)
                wordmarkProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 410, easing = CubicBezierEasing(0.18f, 0.90f, 0.22f, 1f))
                )
            }
            launch {
                delay(340L)
                hairlineProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 340, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                )
            }
            launch {
                delay(780L)
            }
        }

        if (!state.isContentReady) {
            coroutineScope {
                val breathJob = launch {
                    launch {
                        breathStrength.animateTo(1f, tween(durationMillis = 220))
                    }
                    var lastNanos = 0L
                    while (true) {
                        withFrameNanos { frameNanos ->
                            if (lastNanos != 0L) {
                                val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                                breathPhase = (breathPhase + dt * 4.4f) % (2f * PI.toFloat())
                            }
                            lastNanos = frameNanos
                        }
                    }
                }
                withTimeoutOrNull(900L) {
                    snapshotFlow { state.isContentReady }.first { it }
                }
                breathJob.cancel()
            }
        }

        coroutineScope {
            if (breathStrength.value > 0.01f) {
                launch {
                    breathStrength.animateTo(0f, tween(durationMillis = 180))
                }
            }
            launch {
                state.overlayProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = LaunchOverlayEasing)
                )
            }
            launch {
                delay(65L)
                state.riseProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = LaunchRiseTotalMs.toInt(), easing = LinearEasing)
                )
            }
        }
        state.markSettled()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (state.overlayProgress.value < 0.45f) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = state.overlayProgress.value
                    alpha = (1f - launchSmoothstep(0.18f, 0.88f, p)).coerceIn(0f, 1f)
                }
                .background(Color.Black)
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = state.overlayProgress.value.coerceIn(0f, 1f)
            val exitFade = (1f - launchSmoothstep(0.08f, 0.78f, p)).coerceIn(0f, 1f)
            val auraAlpha = (ringProgress.value * exitFade).coerceIn(0f, 1f)
            if (auraAlpha > 0.005f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.075f * auraAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.48f),
                        radius = 180.dp.toPx()
                    ),
                    radius = 180.dp.toPx(),
                    center = Offset(size.width * 0.5f, size.height * 0.48f)
                )
            }

            val r1 = ripple1Progress.value.coerceIn(0f, 1f)
            val r1Alpha = 0.26f * (1f - r1) * exitFade
            if (r1 > 0.005f && r1Alpha > 0.005f) {
                drawCircle(
                    color = Color.White.copy(alpha = r1Alpha),
                    radius = (36f + 106f * r1).dp.toPx(),
                    style = Stroke(width = (2.0f * (1f - r1) + 0.4f).dp.toPx())
                )
            }

            val r2 = ripple2Progress.value.coerceIn(0f, 1f)
            val r2Alpha = 0.16f * (1f - r2) * exitFade
            if (r2 > 0.005f && r2Alpha > 0.005f) {
                drawCircle(
                    color = Color.White.copy(alpha = r2Alpha),
                    radius = (36f + 142f * r2).dp.toPx(),
                    style = Stroke(width = (1.4f * (1f - r2) + 0.35f).dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier.graphicsLayer {
                val p = state.overlayProgress.value.coerceIn(0f, 1f)
                val fade = (1f - launchSmoothstep(0.05f, 0.72f, p)).coerceIn(0f, 1f)
                alpha = fade
                translationY = -18.dp.toPx() * p
                val s = 1f + 0.07f * p
                scaleX = s
                scaleY = s
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OndaAnimatedBrandMark(
                ringProgressProvider = { ringProgress.value },
                waveProgressProvider = { waveProgress.value },
                dotProgressProvider = { dotProgress.value },
                waveBreathPhaseProvider = { breathPhase },
                waveBreathStrengthProvider = { breathStrength.value },
                tint = Color.White,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val s = 0.86f + 0.14f * ringProgress.value.coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                    }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                "ONDA".forEachIndexed { index, ch ->
                    Text(
                        text = ch.toString(),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.graphicsLayer {
                            val w = wordmarkProgress.value.coerceIn(0f, 1f)
                            translationX = (index - 1.5f) * 14.dp.toPx() * (1f - w)
                            translationY = (1f - w) * 10.dp.toPx()
                            alpha = w
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(1.5.dp)
                    .graphicsLayer {
                        val h = hairlineProgress.value.coerceIn(0f, 1f)
                        scaleX = h
                        alpha = h * 0.45f
                    }
                    .background(Color.White)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .height(18.dp)
                    .graphicsLayer {
                        val b = breathStrength.value.coerceIn(0f, 1f)
                        alpha = b
                        translationY = (1f - b) * 6.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                OndaAcousticBarLoader(
                    barCount = 5,
                    barWidth = 2.5.dp,
                    barSpacing = 4.dp,
                    maxHeight = 14.dp,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}

private fun launchSmoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val span = (edge1 - edge0).coerceAtLeast(0.0001f)
    val t = ((value - edge0) / span).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
