package com.onda.music.core.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp

object AppMotion {
    const val HomeRecedeScale = 0.92f
    const val HomeRecedeAlpha = 0.45f
    const val LyricsStaggerDelayMs = 55L
    const val DefaultTiltDegrees = 7f
    const val DefaultPressScale = 0.97f

    val MorphSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SnappySpring: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 520f
    )

    val ExpressiveSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 420f
    )

    val GentleSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    val PopSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.62f,
        stiffness = 460f
    )

    fun <T> adaptiveSpring(
        dampingRatio: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessMediumLow,
        reducedMotion: Boolean = false
    ): AnimationSpec<T> {
        return if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = dampingRatio, stiffness = stiffness)
        }
    }

    fun homeRecedeScale(fraction: Float): Float {
        return lerp(1f, HomeRecedeScale, fraction.coerceIn(0f, 1f))
    }

    fun homeRecedeAlpha(fraction: Float): Float {
        return lerp(1f, HomeRecedeAlpha, fraction.coerceIn(0f, 1f))
    }

    fun morphCornerRadius(
        startRadius: Dp,
        endRadius: Dp,
        fraction: Float
    ): Dp {
        return lerp(startRadius, endRadius, fraction.coerceIn(0f, 1f)).coerceAtLeast(0.dp)
    }
}

class MorphingCornerClipShape(
    private val startRadius: Dp,
    private val endRadius: Dp,
    private val fractionProvider: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusDp = AppMotion.morphCornerRadius(startRadius, endRadius, fractionProvider())
        val radiusPx = with(density) { radiusDp.toPx() }.coerceAtLeast(0f)
        val corner = CornerRadius(radiusPx, radiusPx)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = corner,
                topRightCornerRadius = corner,
                bottomRightCornerRadius = corner,
                bottomLeftCornerRadius = corner
            )
        )
    }
}

fun Modifier.morphingCornerClip(
    startRadius: Dp,
    endRadius: Dp,
    fractionProvider: () -> Float
): Modifier {
    val morphShape = MorphingCornerClipShape(
        startRadius = startRadius,
        endRadius = endRadius,
        fractionProvider = fractionProvider
    )
    return this.graphicsLayer {
        shape = morphShape
        clip = true
    }
}

fun Modifier.homeRecede(
    fractionProvider: () -> Float,
    reducedMotion: Boolean = false
): Modifier {
    return this.graphicsLayer {
        val f = fractionProvider().coerceIn(0f, 1f)
        if (!reducedMotion) {
            val s = AppMotion.homeRecedeScale(f)
            scaleX = s
            scaleY = s
        }
        alpha = AppMotion.homeRecedeAlpha(f)
    }
}

fun Modifier.homeRecede(
    fraction: Float,
    reducedMotion: Boolean = false
): Modifier = homeRecede(fractionProvider = { fraction }, reducedMotion = reducedMotion)
