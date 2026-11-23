package com.onda.music.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Immutable
data class SmoothCornerShape(
    val radius: Dp,
    val exponent: Float = 4f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val safeW = size.width.coerceAtLeast(0f)
        val safeH = size.height.coerceAtLeast(0f)
        val r = clampSmoothRadiusPx(
            radiusPx = with(density) { radius.toPx() },
            widthPx = safeW,
            heightPx = safeH
        )
        if (r <= 0f || safeW <= 0f || safeH <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, safeW, safeH))
        }
        val safeExponent = exponent.coerceAtLeast(2f).toDouble()
        val path = Path().apply {
            addSuperellipseRect(
                width = safeW,
                height = safeH,
                radius = r,
                exponent = safeExponent
            )
        }
        return Outline.Generic(path)
    }
}

private const val SuperellipseSteps = 12

internal fun clampSmoothRadiusPx(radiusPx: Float, widthPx: Float, heightPx: Float): Float {
    val maxRadius = minOf(widthPx.coerceAtLeast(0f), heightPx.coerceAtLeast(0f)) * 0.5f
    return radiusPx.coerceIn(0f, maxRadius)
}

internal fun superellipseQuarterOffsets(
    radius: Float,
    exponent: Double,
    angleRad: Double
): Pair<Float, Float> {
    val power = 2.0 / exponent.coerceAtLeast(2.0)
    val along = (radius * abs(cos(angleRad)).pow(power)).toFloat()
    val across = (radius * abs(sin(angleRad)).pow(power)).toFloat()
    return along to across
}

private fun Path.addSuperellipseRect(
    width: Float,
    height: Float,
    radius: Float,
    exponent: Double
) {
    moveTo(radius, 0f)
    lineTo(width - radius, 0f)

    for (i in 1..SuperellipseSteps) {
        val t = (PI * 0.5) * i / SuperellipseSteps
        val (along, across) = superellipseQuarterOffsets(radius, exponent, t)
        lineTo(width - radius + across, radius - along)
    }
    lineTo(width, height - radius)

    for (i in 1..SuperellipseSteps) {
        val t = (PI * 0.5) * i / SuperellipseSteps
        val (along, across) = superellipseQuarterOffsets(radius, exponent, t)
        lineTo(width - radius + along, height - radius + across)
    }
    lineTo(radius, height)

    for (i in 1..SuperellipseSteps) {
        val t = (PI * 0.5) * i / SuperellipseSteps
        val (along, across) = superellipseQuarterOffsets(radius, exponent, t)
        lineTo(radius - across, height - radius + along)
    }
    lineTo(0f, radius)

    for (i in 1..SuperellipseSteps) {
        val t = (PI * 0.5) * i / SuperellipseSteps
        val (along, across) = superellipseQuarterOffsets(radius, exponent, t)
        lineTo(radius - along, radius - across)
    }
    close()
}
