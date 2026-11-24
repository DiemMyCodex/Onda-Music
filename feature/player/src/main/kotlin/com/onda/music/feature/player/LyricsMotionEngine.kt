package com.onda.music.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

fun lyricSmoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val span = (edge1 - edge0).takeIf { abs(it) > 1e-6f } ?: return if (value >= edge1) 1f else 0f
    val t = ((value - edge0) / span).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun computeSmoothWordProgress(
    positionMs: Long,
    startTimeMs: Long,
    endTimeMs: Long
): Float {
    if (positionMs <= startTimeMs) return 0f
    val duration = (endTimeMs - startTimeMs).coerceAtLeast(1L)
    if (positionMs >= startTimeMs + duration) return 1f
    val linear = ((positionMs - startTimeMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val eased = lyricSmoothstep(0f, 1f, linear)
    return (linear * 0.78f + eased * 0.22f).coerceIn(0f, 1f)
}

fun computeWordActiveEnvelope(progress: Float): Float {
    if (progress <= 0f || progress >= 1f) return 0f
    return sin(progress * PI.toFloat()).coerceIn(0f, 1f)
}

fun interpolateLyricPositionMs(
    anchorPositionMs: Long,
    anchorFrameNanos: Long,
    nowFrameNanos: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    currentInterpolatedMs: Long
): Long {
    val clampedAnchor = anchorPositionMs.coerceAtLeast(0L)
    if (!isPlaying || anchorFrameNanos <= 0L) return clampedAnchor
    val safeSpeed = (playbackSpeed.takeIf { it.isFinite() } ?: 1.0f).coerceIn(0.5f, 2.0f)
    val elapsedMs = (((nowFrameNanos - anchorFrameNanos).coerceAtLeast(0L)) / 1_000_000.0 * safeSpeed).toLong()
    val predicted = (clampedAnchor + elapsedMs).coerceAtLeast(0L)
    if (abs(clampedAnchor - currentInterpolatedMs) > 350L) {
        return predicted
    }
    if (predicted < currentInterpolatedMs && (currentInterpolatedMs - predicted) <= 180L) {
        return currentInterpolatedMs
    }
    return predicted
}

@Stable
class SmoothLyricClock(initialPositionMs: Long) {
    var positionMs by mutableLongStateOf(initialPositionMs.coerceAtLeast(0L))
        internal set
}

@Composable
fun rememberSmoothLyricClock(
    anchorPositionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float = 1.0f,
    trackKey: Any? = null,
    active: Boolean = true
): SmoothLyricClock {
    val clock = remember(trackKey) { SmoothLyricClock(anchorPositionMs) }
    var anchorSampleMs by remember(trackKey) { mutableLongStateOf(anchorPositionMs.coerceAtLeast(0L)) }
    var anchorSampleNanos by remember(trackKey) { mutableLongStateOf(System.nanoTime()) }
    var lastObservedTrackKey by remember { mutableStateOf(trackKey) }

    if (lastObservedTrackKey != trackKey) {
        lastObservedTrackKey = trackKey
        clock.positionMs = anchorPositionMs.coerceAtLeast(0L)
    }

    LaunchedEffect(anchorPositionMs, trackKey) {
        anchorSampleMs = anchorPositionMs.coerceAtLeast(0L)
        anchorSampleNanos = System.nanoTime()
        if (!isPlaying || !active || abs(anchorPositionMs - clock.positionMs) > 350L) {
            clock.positionMs = anchorSampleMs
        }
    }

    val latestAnchorMs by rememberUpdatedState(anchorSampleMs)
    val latestAnchorNanos by rememberUpdatedState(anchorSampleNanos)
    val latestSpeed by rememberUpdatedState(playbackSpeed)

    LaunchedEffect(isPlaying, active, trackKey) {
        if (!isPlaying || !active) {
            clock.positionMs = latestAnchorMs
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos {
                val nowWallNanos = System.nanoTime()
                clock.positionMs = interpolateLyricPositionMs(
                    anchorPositionMs = latestAnchorMs,
                    anchorFrameNanos = latestAnchorNanos,
                    nowFrameNanos = nowWallNanos,
                    isPlaying = true,
                    playbackSpeed = latestSpeed,
                    currentInterpolatedMs = clock.positionMs
                )
            }
        }
    }

    return clock
}

@Composable
fun FeatheredSweepWord(
    text: String,
    style: TextStyle,
    startTimeMs: Long,
    endTimeMs: Long,
    positionProvider: () -> Long,
    activeColor: Color,
    baseColor: Color,
    motionActive: Boolean,
    modifier: Modifier = Modifier
) {
    val safeEndMs = endTimeMs.coerceAtLeast(startTimeMs + 1L)
    Box(
        modifier = modifier.graphicsLayer {
            val progress = computeSmoothWordProgress(positionProvider(), startTimeMs, safeEndMs)
            val envelope = if (motionActive) computeWordActiveEnvelope(progress) else 0f
            transformOrigin = TransformOrigin(0.5f, 0.72f)
            val s = 1f + 0.045f * envelope
            scaleX = s
            scaleY = s
            translationY = if (motionActive) -2.dp.toPx() * envelope else 0f
        }
    ) {
        Text(
            text = text,
            style = style,
            color = baseColor
        )
        Text(
            text = text,
            style = style,
            color = activeColor,
            modifier = Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    val progress = computeSmoothWordProgress(positionProvider(), startTimeMs, safeEndMs)
                    if (progress <= 0.001f) {
                        return@drawWithContent
                    }
                    drawContent()
                    if (progress >= 0.999f) {
                        return@drawWithContent
                    }
                    val feather = 14.dp.toPx().coerceAtMost(size.width * 0.42f).coerceAtLeast(1f)
                    val headX = progress * (size.width + feather)
                    val solidX = headX - feather
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = solidX,
                            endX = headX,
                            tileMode = TileMode.Clamp
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        )
    }
}
