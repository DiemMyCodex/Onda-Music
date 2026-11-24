package com.onda.music.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

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
