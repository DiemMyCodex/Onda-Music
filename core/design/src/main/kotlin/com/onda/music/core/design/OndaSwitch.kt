package com.onda.music.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val SwitchTrackWidth = 52.dp
private val SwitchTrackHeight = 32.dp
private val SwitchThumbDiameter = 24.dp
private val SwitchThumbInset = (SwitchTrackHeight - SwitchThumbDiameter) * 0.5f

@Composable
fun OndaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumbContent: (@Composable () -> Unit)? = null
) {
    val reducedMotion = OndaTheme.reducedMotion
    val isPressed by interactionSource.collectIsPressedAsState()
    val alphaScale = if (enabled) 1f else 0.38f

    val progressSpec = if (reducedMotion) {
        snap<Float>()
    } else {
        spring(dampingRatio = 0.78f, stiffness = 560f)
    }
    val stretchSpec = if (reducedMotion) {
        snap<Float>()
    } else {
        spring(dampingRatio = 0.72f, stiffness = 620f)
    }

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = progressSpec,
        label = "OndaSwitchProgress"
    )
    val pressStretch by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 1f else 0f,
        animationSpec = stretchSpec,
        label = "OndaSwitchStretch"
    )

    val offBorderColor = GlassTokens.TextSecondary.copy(alpha = 0.45f * alphaScale)
    val onTrackColor = GlassTokens.TextPrimary.copy(alpha = alphaScale)
    val offThumbColor = GlassTokens.TextSecondary.copy(alpha = alphaScale)
    val onThumbColor = GlassTokens.AmoledBlack.copy(alpha = alphaScale)

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = SwitchTrackWidth,
                minHeight = DockMetrics.MinTapTarget
            )
            .clip(RoundedCornerShape(999.dp))
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { next -> onCheckedChange?.invoke(next) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(SwitchTrackWidth, SwitchTrackHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val p = progress.coerceIn(0f, 1f)
                val cornerPx = size.height * 0.5f
                val trackColor = lerp(Color.Transparent, onTrackColor, p)
                val borderColor = lerp(offBorderColor, Color.Transparent, p)
                val borderWidthPx = 1.dp.toPx()

                if (trackColor.alpha > 0.001f) {
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )
                }
                if (borderColor.alpha > 0.001f) {
                    val halfStroke = borderWidthPx * 0.5f
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size(
                            width = (size.width - borderWidthPx).coerceAtLeast(0f),
                            height = (size.height - borderWidthPx).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = borderWidthPx)
                    )
                }

                val insetPx = SwitchThumbInset.toPx()
                val baseDiameter = SwitchThumbDiameter.toPx()
                val travelMotion = if (reducedMotion) 0f else (1f - abs(2f * p - 1f)).coerceIn(0f, 1f)
                val combinedStretch = maxOf(pressStretch, travelMotion * 0.75f).coerceIn(0f, 1f)
                val thumbWidth = baseDiameter * (1f + 0.24f * combinedStretch)
                val maxTravel = (size.width - insetPx * 2f - thumbWidth).coerceAtLeast(0f)
                val thumbX = insetPx + maxTravel * p
                val thumbY = (size.height - baseDiameter) * 0.5f
                val thumbRadius = baseDiameter * 0.5f
                val thumbColor = lerp(offThumbColor, onThumbColor, p)

                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(thumbX, thumbY),
                    size = Size(width = thumbWidth, height = baseDiameter),
                    cornerRadius = CornerRadius(thumbRadius, thumbRadius)
                )
            }

            if (thumbContent != null) {
                Box(
                    modifier = Modifier
                        .size(SwitchThumbDiameter)
                        .graphicsLayer {
                            val p = progress.coerceIn(0f, 1f)
                            val insetPx = SwitchThumbInset.toPx()
                            val diameterPx = SwitchThumbDiameter.toPx()
                            val trackWidthPx = SwitchTrackWidth.toPx()
                            val travelMotion = if (reducedMotion) 0f else (1f - abs(2f * p - 1f)).coerceIn(0f, 1f)
                            val combinedStretch = maxOf(pressStretch, travelMotion * 0.75f).coerceIn(0f, 1f)
                            val thumbWidth = diameterPx * (1f + 0.24f * combinedStretch)
                            val maxTravel = (trackWidthPx - insetPx * 2f - thumbWidth).coerceAtLeast(0f)
                            translationX = insetPx + maxTravel * p + (thumbWidth - diameterPx) * 0.5f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    thumbContent()
                }
            }
        }
    }
}
