package com.onda.music.core.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

object AppMotion {
    const val HomeRecedeScale = 0.92f
    const val HomeRecedeAlpha = 0.45f
    const val LyricsStaggerDelayMs = 55L
    const val DefaultTiltDegrees = 7f
    const val DefaultPressScale = 0.97f

    private const val CORNER_SHAPE_STEP_DP = 0.5f
    private const val MAX_CACHED_CORNER_STEPS = 96
    private val roundedCornerShapeCache = arrayOfNulls<RoundedCornerShape>(MAX_CACHED_CORNER_STEPS + 1)

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

    fun morphCornerShape(
        startRadius: Dp,
        endRadius: Dp,
        fraction: Float
    ): Shape {
        val radiusDp = morphCornerRadius(startRadius, endRadius, fraction)
        if (radiusDp.value <= 0.25f) return RectangleShape
        val step = (radiusDp.value / CORNER_SHAPE_STEP_DP)
            .roundToInt()
            .coerceIn(1, MAX_CACHED_CORNER_STEPS)
        return roundedCornerShapeCache[step]
            ?: RoundedCornerShape((step * CORNER_SHAPE_STEP_DP).dp).also {
                roundedCornerShapeCache[step] = it
            }
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
    return this.graphicsLayer {
        val f = fractionProvider()
        val radiusDp = AppMotion.morphCornerRadius(startRadius, endRadius, f)
        shape = AppMotion.morphCornerShape(startRadius, endRadius, f)
        clip = radiusDp.value > 0.25f
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
