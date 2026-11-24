package com.onda.music.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun OndaMonogramMark(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    cutoutColor: Color = Color(0xFF1A1A1A),
    filled: Boolean = false
) {
    val wavePath = remember { Path() }
    Canvas(
        modifier = modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
    ) {
        val boxSize = minOf(size.width, size.height)
        if (boxSize <= 0f) return@Canvas
        val offsetX = (size.width - boxSize) * 0.5f
        val offsetY = (size.height - boxSize) * 0.5f
        val center = Offset(offsetX + boxSize * 0.5f, offsetY + boxSize * 0.5f)
        wavePath.reset()

        if (filled) {
            val scale = boxSize / 148f
            val discRadius = 56f * scale
            fun mapPt(vx: Float, vy: Float): Offset {
                return Offset(
                    x = center.x + (vx - 146f) * scale,
                    y = center.y + (vy - 146f) * scale
                )
            }

            drawCircle(
                color = tint,
                radius = discRadius,
                center = center
            )
            drawCircle(
                color = cutoutColor,
                radius = discRadius,
                center = center,
                style = Stroke(
                    width = 16f * scale,
                    join = StrokeJoin.Round
                )
            )

            val p0 = mapPt(92f, 150f)
            val c1 = mapPt(108f, 104f)
            val c2 = mapPt(129f, 104f)
            val mid = mapPt(146f, 146f)
            val c3 = mapPt(163f, 188f)
            val c4 = mapPt(184f, 188f)
            val p1 = mapPt(200f, 142f)
            wavePath.moveTo(p0.x, p0.y)
            wavePath.cubicTo(c1.x, c1.y, c2.x, c2.y, mid.x, mid.y)
            wavePath.cubicTo(c3.x, c3.y, c4.x, c4.y, p1.x, p1.y)
            drawPath(
                path = wavePath,
                color = cutoutColor,
                style = Stroke(
                    width = 18f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            drawCircle(
                color = cutoutColor,
                radius = 7f * scale,
                center = mapPt(171f, 117f)
            )
        } else {
            val scale = boxSize / 24f
            fun mapPt(vx: Float, vy: Float): Offset {
                return Offset(
                    x = offsetX + vx * scale,
                    y = offsetY + vy * scale
                )
            }

            drawCircle(
                color = tint,
                radius = 8.5f * scale,
                center = center,
                style = Stroke(
                    width = 2.4f * scale,
                    join = StrokeJoin.Round
                )
            )

            val p0 = mapPt(4.5f, 12.5f)
            val c1 = mapPt(7.0f, 7.2f)
            val c2 = mapPt(9.8f, 7.2f)
            val mid = mapPt(12.0f, 12.0f)
            val c3 = mapPt(14.2f, 16.8f)
            val c4 = mapPt(17.0f, 16.8f)
            val p1 = mapPt(19.5f, 11.5f)
            wavePath.moveTo(p0.x, p0.y)
            wavePath.cubicTo(c1.x, c1.y, c2.x, c2.y, mid.x, mid.y)
            wavePath.cubicTo(c3.x, c3.y, c4.x, c4.y, p1.x, p1.y)
            drawPath(
                path = wavePath,
                color = tint,
                style = Stroke(
                    width = 2.4f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            drawCircle(
                color = tint,
                radius = 1.05f * scale,
                center = mapPt(15.5f, 8.6f)
            )
        }
    }
}

@Composable
fun OndaAnimatedBrandMark(
    ringProgressProvider: () -> Float,
    waveProgressProvider: () -> Float,
    dotProgressProvider: () -> Float,
    waveBreathPhaseProvider: () -> Float = { 0f },
    waveBreathStrengthProvider: () -> Float = { 0f },
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val fullWavePath = remember { Path() }
    val segmentWavePath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Canvas(
        modifier = modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
    ) {
        val boxSize = minOf(size.width, size.height)
        if (boxSize <= 0f) return@Canvas
        val offsetX = (size.width - boxSize) * 0.5f
        val offsetY = (size.height - boxSize) * 0.5f
        val center = Offset(offsetX + boxSize * 0.5f, offsetY + boxSize * 0.5f)
        val scale = boxSize / 24f

        fun mapPt(vx: Float, vy: Float): Offset {
            return Offset(
                x = offsetX + vx * scale,
                y = offsetY + vy * scale
            )
        }

        val ringProgress = ringProgressProvider().coerceIn(0f, 1f)
        if (ringProgress > 0.001f) {
            val radius = 8.5f * scale
            val diameter = radius * 2f
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(diameter, diameter)
            val sweep = 360f * ringProgress

            drawArc(
                color = tint.copy(alpha = 0.16f * ringProgress),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(
                    width = 5.4f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(
                    width = 2.4f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        val waveProgress = waveProgressProvider().coerceIn(0f, 1f)
        if (waveProgress > 0.001f) {
            val breathStrength = waveBreathStrengthProvider().coerceIn(0f, 1f)
            val breathOffset = if (breathStrength > 0.001f) {
                sin(waveBreathPhaseProvider()) * 1.35f * breathStrength
            } else {
                0f
            }
            fullWavePath.reset()
            val p0 = mapPt(4.5f, 12.5f)
            val c1 = mapPt(7.0f, 7.2f - breathOffset)
            val c2 = mapPt(9.8f, 7.2f - breathOffset)
            val mid = mapPt(12.0f, 12.0f)
            val c3 = mapPt(14.2f, 16.8f + breathOffset)
            val c4 = mapPt(17.0f, 16.8f + breathOffset)
            val p1 = mapPt(19.5f, 11.5f)
            fullWavePath.moveTo(p0.x, p0.y)
            fullWavePath.cubicTo(c1.x, c1.y, c2.x, c2.y, mid.x, mid.y)
            fullWavePath.cubicTo(c3.x, c3.y, c4.x, c4.y, p1.x, p1.y)

            pathMeasure.setPath(fullWavePath, false)
            val waveLen = pathMeasure.length
            if (waveLen > 0f) {
                val stopDistance = waveLen * waveProgress
                segmentWavePath.reset()
                pathMeasure.getSegment(
                    startDistance = 0f,
                    stopDistance = stopDistance,
                    destination = segmentWavePath,
                    startWithMoveTo = true
                )
                drawPath(
                    path = segmentWavePath,
                    color = tint.copy(alpha = 0.18f),
                    style = Stroke(
                        width = 5.2f * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                drawPath(
                    path = segmentWavePath,
                    color = tint,
                    style = Stroke(
                        width = 2.4f * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                if (waveProgress < 0.995f) {
                    val tip = pathMeasure.getPosition(stopDistance)
                    if (tip.isSpecified) {
                        drawCircle(
                            color = tint.copy(alpha = 0.34f * (1f - waveProgress * 0.6f)),
                            radius = 2.6f * scale,
                            center = tip
                        )
                        drawCircle(
                            color = tint,
                            radius = 1.25f * scale,
                            center = tip
                        )
                    }
                }
            }
        }

        val dotProgress = dotProgressProvider().coerceIn(0f, 1.3f)
        if (dotProgress > 0.001f) {
            val dotCenter = mapPt(15.5f, 8.6f)
            val clampedAlpha = dotProgress.coerceIn(0f, 1f)
            val haloAlpha = (1f - clampedAlpha).coerceIn(0f, 1f) * 0.45f
            if (haloAlpha > 0.01f) {
                drawCircle(
                    color = tint.copy(alpha = haloAlpha),
                    radius = (1.05f + 2.4f * dotProgress) * scale,
                    center = dotCenter,
                    style = Stroke(width = 0.9f * scale)
                )
            }
            drawCircle(
                color = tint.copy(alpha = clampedAlpha),
                radius = 1.05f * scale * dotProgress,
                center = dotCenter
            )
        }
    }
}
