package com.onda.music.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LaunchRiseDurationMs = 480f
private const val LaunchRiseStaggerMs = 55f
private const val LaunchRiseOrderCount = 4
private const val LaunchRiseTotalMs = LaunchRiseDurationMs + LaunchRiseStaggerMs * (LaunchRiseOrderCount - 1)
private val LaunchRiseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val LaunchOverlayEasing = CubicBezierEasing(0.32f, 0f, 0.12f, 1f)

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

    val overlayProgress = Animatable(if (playOnStart) 0f else 1f)
    val riseProgress = Animatable(if (playOnStart) 0f else 1f)

    fun orderProgress(order: Int): Float {
        return if (isSettled) 1f else ondaLaunchRiseOrderProgress(riseProgress.value, order)
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

    val plateShape = remember { SmoothCornerShape(18.dp) }

    LaunchedEffect(state, reducedMotion) {
        if (reducedMotion) {
            state.overlayProgress.snapTo(1f)
            state.riseProgress.snapTo(1f)
            state.markSettled()
            return@LaunchedEffect
        }
        withFrameNanos { }
        coroutineScope {
            launch {
                state.overlayProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 380, easing = LaunchOverlayEasing)
                )
            }
            launch {
                delay(85L)
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
                        if (state.overlayProgress.value < 0.55f) {
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
                    alpha = (1f - launchSmoothstep(0.15f, 0.82f, p)).coerceIn(0f, 1f)
                }
                .background(Color.Black)
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = state.overlayProgress.value.coerceIn(0f, 1f)
            val ringAlpha = 0.30f * (1f - p)
            if (ringAlpha > 0.005f) {
                val radius = 36.dp.toPx() + 48.dp.toPx() * p
                drawCircle(
                    color = Color.White.copy(alpha = ringAlpha),
                    radius = radius,
                    style = Stroke(width = (2.2f * (1f - p) + 0.8f).dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    val p = state.overlayProgress.value.coerceIn(0f, 1f)
                    val s = 1f + 0.14f * p
                    scaleX = s
                    scaleY = s
                    alpha = (1f - launchSmoothstep(0.25f, 0.88f, p)).coerceIn(0f, 1f)
                }
                .clip(plateShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            OndaMonogramMark(
                modifier = Modifier.size(72.dp),
                tint = Color.White,
                cutoutColor = Color(0xFF1A1A1A),
                filled = true
            )
        }
    }
}

private fun launchSmoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val span = (edge1 - edge0).coerceAtLeast(0.0001f)
    val t = ((value - edge0) / span).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
