package com.onda.music.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

val DefaultHighlightColor = Color.White.copy(alpha = 0.12f)

@Immutable
class RoundedHighlight(
    private val cornerRadius: Dp = 16.dp,
    private val spillX: Dp = 0.dp,
    private val spillY: Dp = 0.dp,
    private val color: Color = DefaultHighlightColor
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return RoundedHighlightNode(
            interactionSource = interactionSource,
            cornerRadius = cornerRadius,
            spillX = spillX,
            spillY = spillY,
            color = color
        )
    }

    override fun equals(other: Any?): Boolean {
        return other is RoundedHighlight &&
            other.cornerRadius == cornerRadius &&
            other.spillX == spillX &&
            other.spillY == spillY &&
            other.color == color
    }

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + spillX.hashCode()
        result = 31 * result + spillY.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }
}

private class RoundedHighlightNode(
    private val interactionSource: InteractionSource,
    private val cornerRadius: Dp,
    private val spillX: Dp,
    private val spillY: Dp,
    private val color: Color
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val strength = Animatable(0f)
    private var activeJob: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            val activePresses = ArrayList<PressInteraction.Press>()
            var focused = false
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> activePresses.add(interaction)
                    is PressInteraction.Release -> activePresses.remove(interaction.press)
                    is PressInteraction.Cancel -> activePresses.remove(interaction.press)
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    else -> return@collect
                }
                val reduced = currentValueOf(LocalReducedMotion)
                activeJob?.cancel()
                if (activePresses.isNotEmpty() || focused) {
                    activeJob = launch {
                        if (reduced) {
                            strength.snapTo(1f)
                        } else {
                            strength.animateTo(1f, tween(durationMillis = 85))
                        }
                    }
                } else {
                    activeJob = launch {
                        if (reduced) {
                            strength.snapTo(0f)
                        } else {
                            if (strength.value < 0.85f) {
                                strength.animateTo(1f, tween(durationMillis = 65))
                            }
                            strength.animateTo(0f, tween(durationMillis = 300))
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val currentStrength = strength.value
        if (currentStrength > 0.001f) {
            val dx = spillX.toPx()
            val dy = spillY.toPx()
            val radiusPx = cornerRadius.toPx().coerceAtLeast(0f)
            drawRoundRect(
                color = color.copy(alpha = (color.alpha * currentStrength).coerceIn(0f, 1f)),
                topLeft = Offset(-dx, -dy),
                size = Size(
                    width = (size.width + dx * 2f).coerceAtLeast(0f),
                    height = (size.height + dy * 2f).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )
        }
        drawContent()
    }
}

@Composable
fun Modifier.pressBounce(
    interactionSource: InteractionSource,
    pressedScale: Float = AppMotion.DefaultPressScale,
    enabled: Boolean = true
): Modifier {
    val reducedMotion = OndaTheme.reducedMotion
    if (!enabled || reducedMotion) return this

    val scale = remember(interactionSource) { Animatable(1f) }
    LaunchedEffect(interactionSource, pressedScale) {
        var pressCount = 0
        var activeJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressCount += 1
                    activeJob?.cancel()
                    activeJob = launch {
                        scale.animateTo(
                            targetValue = pressedScale,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 1400f)
                        )
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressCount = (pressCount - 1).coerceAtLeast(0)
                    if (pressCount == 0) {
                        activeJob?.cancel()
                        activeJob = launch {
                            if (scale.value > pressedScale + 0.015f) {
                                scale.animateTo(pressedScale, tween(durationMillis = 60))
                            }
                            scale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f)
                            )
                        }
                    }
                }
            }
        }
    }

    return this.graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}

@Composable
fun Modifier.pressBounce(
    pressedScale: Float = AppMotion.DefaultPressScale,
    enabled: Boolean = true
): Modifier {
    val reducedMotion = OndaTheme.reducedMotion
    if (!enabled || reducedMotion) return this

    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    return this
        .pointerInput(pressedScale) {
            val touchSlop = viewConfiguration.touchSlop
            var activeJob: Job? = null
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
                    val startPos = down.position
                    activeJob?.cancel()
                    activeJob = scope.launch {
                        scale.animateTo(
                            targetValue = pressedScale,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 1400f)
                        )
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val active = event.changes.firstOrNull { it.pressed }
                        val stolenByScroll = active != null &&
                            active.isConsumed &&
                            (active.position - startPos).getDistance() > touchSlop
                        if (event.type == PointerEventType.Cancel || active == null || stolenByScroll) {
                            break
                        }
                    }

                    activeJob?.cancel()
                    activeJob = scope.launch {
                        if (scale.value > pressedScale + 0.015f) {
                            scale.animateTo(pressedScale, tween(durationMillis = 60))
                        }
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f)
                        )
                    }
                }
            }
        }
        .graphicsLayer {
            val s = scale.value
            scaleX = s
            scaleY = s
        }
}

fun Modifier.springPress3D(
    enabled: Boolean = true,
    maxTiltDegrees: Float = AppMotion.DefaultTiltDegrees,
    pressedScale: Float = AppMotion.DefaultPressScale,
    origin: TransformOrigin = TransformOrigin.Center
): Modifier = composed {
    val reducedMotion = OndaTheme.reducedMotion
    if (!enabled || reducedMotion) return@composed this

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }

    val pressSpring = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f)
    }
    val tiltFollowSpring = remember {
        spring<Float>(dampingRatio = 0.86f, stiffness = 620f)
    }
    val releaseSpring = remember {
        spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
    }

    this
        .pointerInput(maxTiltDegrees, pressedScale) {
            val touchSlop = viewConfiguration.touchSlop
            var pressJob: Job? = null
            var tiltJob: Job? = null
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
                    val startPos = down.position
                    val w = size.width.coerceAtLeast(1).toFloat()
                    val h = size.height.coerceAtLeast(1).toFloat()

                    val nx0 = ((startPos.x / w) * 2f - 1f).coerceIn(-1f, 1f)
                    val ny0 = ((startPos.y / h) * 2f - 1f).coerceIn(-1f, 1f)

                    pressJob?.cancel()
                    tiltJob?.cancel()
                    pressJob = scope.launch { press.animateTo(1f, pressSpring) }
                    tiltJob = scope.launch {
                        launch { tiltX.animateTo(-ny0 * maxTiltDegrees, tiltFollowSpring) }
                        launch { tiltY.animateTo(nx0 * maxTiltDegrees, tiltFollowSpring) }
                    }

                    var lastNx = nx0
                    var lastNy = ny0

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val active = event.changes.firstOrNull { it.pressed }
                        val stolenByScroll = active != null &&
                            active.isConsumed &&
                            (active.position - startPos).getDistance() > touchSlop
                        if (event.type == PointerEventType.Cancel || active == null || stolenByScroll) {
                            break
                        }
                        val nx = ((active.position.x / w) * 2f - 1f).coerceIn(-1f, 1f)
                        val ny = ((active.position.y / h) * 2f - 1f).coerceIn(-1f, 1f)
                        if (abs(nx - lastNx) > 0.04f || abs(ny - lastNy) > 0.04f) {
                            lastNx = nx
                            lastNy = ny
                            tiltJob?.cancel()
                            tiltJob = scope.launch {
                                launch { tiltX.animateTo(-ny * maxTiltDegrees, tiltFollowSpring) }
                                launch { tiltY.animateTo(nx * maxTiltDegrees, tiltFollowSpring) }
                            }
                        }
                    }

                    pressJob?.cancel()
                    tiltJob?.cancel()
                    pressJob = scope.launch { press.animateTo(0f, releaseSpring) }
                    tiltJob = scope.launch {
                        launch { tiltX.animateTo(0f, releaseSpring) }
                        launch { tiltY.animateTo(0f, releaseSpring) }
                    }
                }
            }
        }
        .graphicsLayer {
            val t = press.value.coerceIn(0f, 1f)
            if (t > 0.001f || abs(tiltX.value) > 0.01f || abs(tiltY.value) > 0.01f) {
                val s = 1f - (1f - pressedScale) * t
                scaleX = s
                scaleY = s
                rotationX = tiltX.value
                rotationY = tiltY.value
                cameraDistance = (size.width.takeIf { it > 0f } ?: 1000f) * 3f
                transformOrigin = origin
            }
        }
}
