package com.onda.music.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.OndaTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

object SettingsHighlight {
    var pending by mutableStateOf<String?>(null)
    var active by mutableStateOf<String?>(null)

    fun request(key: String) {
        pending = key
    }

    fun clear() {
        pending = null
        active = null
    }
}

class SettingsScroll(
    val state: ScrollState,
    val clearTop: Dp
)

val LocalSettingsScroll = staticCompositionLocalOf<SettingsScroll?> { null }

private class SettingsPlacementRef {
    var coords: LayoutCoordinates? = null
}

suspend fun SettingsScroll.reveal(target: LayoutCoordinates, density: Density) {
    if (!target.isAttached) return
    val clearTopPx = with(density) { clearTop.toPx() }
    val delta = target.positionInRoot().y - clearTopPx
    val desired = (state.value + delta).roundToInt().coerceIn(0, state.maxValue)
    state.animateScrollTo(desired)
}

@Composable
fun Modifier.settingsHighlightTarget(key: String): Modifier = composed {
    val requester = remember(key) { BringIntoViewRequester() }
    val settingsScroll = LocalSettingsScroll.current
    val density = LocalDensity.current
    val reducedMotion = OndaTheme.reducedMotion
    val placement = remember(key) { SettingsPlacementRef() }
    val pulse = remember(key) { Animatable(0f) }

    LaunchedEffect(key, settingsScroll, reducedMotion) {
        snapshotFlow { SettingsHighlight.pending }.collect { pending ->
            if (pending != key) return@collect
            SettingsHighlight.pending = null
            SettingsHighlight.active = key
            delay(if (reducedMotion) 40L else 280L)
            val target = placement.coords
            if (settingsScroll != null && target != null) {
                settingsScroll.reveal(target, density)
            } else {
                requester.bringIntoView()
            }
            delay(if (reducedMotion) 1000L else 1500L)
            if (SettingsHighlight.active == key) {
                SettingsHighlight.active = null
            }
        }
    }

    val isFlashing = SettingsHighlight.active == key
    LaunchedEffect(isFlashing, reducedMotion) {
        if (isFlashing) {
            if (reducedMotion) {
                pulse.snapTo(1f)
            } else {
                pulse.snapTo(0f)
                pulse.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f)
                )
                delay(420L)
                pulse.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 520)
                )
            }
        } else {
            if (reducedMotion) {
                pulse.snapTo(0f)
            } else if (pulse.value > 0.001f) {
                pulse.animateTo(0f, tween(durationMillis = 220))
            }
        }
    }

    this
        .bringIntoViewRequester(requester)
        .onPlaced { placement.coords = it }
        .drawBehind {
            val strength = pulse.value.coerceIn(0f, 1f)
            if (strength > 0.001f) {
                val radiusPx = 16.dp.toPx().coerceAtLeast(0f)
                val insetPx = 4.dp.toPx()
                val boxSize = Size(
                    width = (size.width - insetPx * 2f).coerceAtLeast(0f),
                    height = (size.height - insetPx * 2f).coerceAtLeast(0f)
                )
                val topLeft = Offset(insetPx, insetPx)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f * strength),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
                val strokePx = 1.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.36f * strength),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
}
