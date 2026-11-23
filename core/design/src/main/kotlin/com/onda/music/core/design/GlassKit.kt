package com.onda.music.core.design

import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassElevation {
    SUBTLE,
    CARD,
    DOCK,
    SHEET,
    HERO
}

@Immutable
data class GlassStyle(
    val fillColor: Color,
    val borderWidth: Dp,
    val borderIntensity: Float,
    val cornerRadius: Dp,
    val blurRadius: Dp,
    val highlightAlpha: Float
)

object GlassKit {
    fun styleFor(elevation: GlassElevation): GlassStyle {
        return when (elevation) {
            GlassElevation.SUBTLE -> GlassStyle(
                fillColor = Color(0x9910141F),
                borderWidth = GlassTokens.BorderHairline,
                borderIntensity = 0.65f,
                cornerRadius = GlassTokens.RadiusSm,
                blurRadius = 12.dp,
                highlightAlpha = 0.05f
            )
            GlassElevation.CARD -> GlassStyle(
                fillColor = GlassTokens.SmokedGlassCard,
                borderWidth = GlassTokens.BorderStandard,
                borderIntensity = 0.90f,
                cornerRadius = GlassTokens.RadiusMd,
                blurRadius = 18.dp,
                highlightAlpha = 0.08f
            )
            GlassElevation.DOCK -> GlassStyle(
                fillColor = GlassTokens.SmokedGlassDock,
                borderWidth = GlassTokens.BorderStandard,
                borderIntensity = 1.05f,
                cornerRadius = GlassTokens.RadiusLg,
                blurRadius = 26.dp,
                highlightAlpha = 0.10f
            )
            GlassElevation.SHEET -> GlassStyle(
                fillColor = GlassTokens.SmokedGlassElevated,
                borderWidth = GlassTokens.BorderStandard,
                borderIntensity = 1.00f,
                cornerRadius = GlassTokens.RadiusXl,
                blurRadius = 32.dp,
                highlightAlpha = 0.11f
            )
            GlassElevation.HERO -> GlassStyle(
                fillColor = GlassTokens.SmokedGlassBase,
                borderWidth = GlassTokens.BorderProminent,
                borderIntensity = 1.15f,
                cornerRadius = GlassTokens.RadiusXl,
                blurRadius = 28.dp,
                highlightAlpha = 0.14f
            )
        }
    }

    val isBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @RequiresApi(Build.VERSION_CODES.S)
    fun createBlurRenderEffect(radiusPx: Float): RenderEffect? {
        val safeRadius = radiusPx.coerceAtLeast(0.5f)
        return android.graphics.RenderEffect.createBlurEffect(
            safeRadius,
            safeRadius,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    }
}

fun Modifier.smokedGlass(
    shape: Shape = RoundedCornerShape(GlassTokens.RadiusMd),
    elevation: GlassElevation = GlassElevation.CARD,
    accentTint: Color = GlassTokens.IceBlue,
    borderWidth: Dp? = null
): Modifier {
    val style = GlassKit.styleFor(elevation)
    val resolvedBorder = borderWidth ?: style.borderWidth
    return this
        .clip(shape)
        .background(
            brush = GlassTokens.smokedFillBrush(
                baseColor = style.fillColor,
                accentTint = accentTint,
                highlightAlpha = style.highlightAlpha
            ),
            shape = shape
        )
        .border(
            width = resolvedBorder,
            brush = GlassTokens.specularBorderBrush(
                intensity = style.borderIntensity,
                accentTint = accentTint
            ),
            shape = shape
        )
}

fun Modifier.ondaBackdropBlur(
    radius: Dp = 24.dp,
    progressiveTopFade: Boolean = false
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this.drawWithContent {
            drawContent()
            val scrimBrush = if (progressiveTopFade) {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to GlassTokens.AmoledBlack.copy(alpha = 0.15f),
                        0.5f to GlassTokens.AmoledBlack.copy(alpha = 0.52f),
                        1.0f to GlassTokens.AmoledBlack.copy(alpha = 0.86f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        GlassTokens.SmokedGlassScrim.copy(alpha = 0.68f),
                        GlassTokens.SmokedGlassScrim.copy(alpha = 0.78f)
                    )
                )
            }
            drawRect(brush = scrimBrush)
        }
    }

    val blurredModifier = this.graphicsLayer {
        val px = radius.toPx()
        if (px >= 1f) {
            renderEffect = GlassKit.createBlurRenderEffect(px)
        }
    }

    return if (!progressiveTopFade) {
        blurredModifier
    } else {
        blurredModifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.55f),
                            1.0f to Color.Black
                        )
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    elevation: GlassElevation = GlassElevation.CARD,
    shape: Shape = RoundedCornerShape(GlassKit.styleFor(elevation).cornerRadius),
    accentTint: Color = OndaTheme.palette.secondary,
    enableBackdropBlur: Boolean = false,
    backdropContent: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val style = GlassKit.styleFor(elevation)
    Box(
        modifier = modifier
            .clip(shape)
    ) {
        if (backdropContent != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .let { base ->
                        if (enableBackdropBlur) {
                            base.ondaBackdropBlur(radius = style.blurRadius)
                        } else {
                            base
                        }
                    },
                content = backdropContent
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .smokedGlass(
                    shape = shape,
                    elevation = elevation,
                    accentTint = accentTint
                )
        )
        content()
    }
}

@Composable
fun ProgressiveGlassScrim(
    modifier: Modifier = Modifier,
    topToBottom: Boolean = true,
    scrimColor: Color = GlassTokens.AmoledBlack
) {
    val stops = if (topToBottom) {
        arrayOf(
            0.00f to scrimColor.copy(alpha = 0.90f),
            0.42f to scrimColor.copy(alpha = 0.45f),
            1.00f to Color.Transparent
        )
    } else {
        arrayOf(
            0.00f to Color.Transparent,
            0.45f to scrimColor.copy(alpha = 0.52f),
            1.00f to scrimColor.copy(alpha = 0.94f)
        )
    }
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(colorStops = stops)
        )
    )
}
