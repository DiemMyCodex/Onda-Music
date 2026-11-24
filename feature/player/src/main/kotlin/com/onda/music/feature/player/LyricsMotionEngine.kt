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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsWord
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
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

fun computeWordLiftEnvelope(progress: Float): Float {
    if (progress <= 0f || progress >= 1f) return 0f
    if (progress <= 0.28f) {
        return lyricSmoothstep(0f, 0.28f, progress)
    }
    val u = ((progress - 0.28f) / 0.72f).coerceIn(0f, 1f)
    val release = lyricSmoothstep(0f, 1f, u)
    return (1f - release * 0.85f - u * 0.15f).coerceIn(0f, 1f)
}

private val LYRIC_WHITESPACE_REGEX = Regex("\\s+")

private fun isCompactScriptChar(ch: Char): Boolean {
    val code = ch.code
    return code in 0x4E00..0x9FFF ||
        code in 0x3040..0x30FF ||
        code in 0xAC00..0xD7AF ||
        code in 0x0E00..0x0E7F
}

private fun lyricTokenWeight(token: String): Float {
    val base = token.count { it.isLetterOrDigit() }.coerceAtLeast(2).toFloat()
    val pauseBonus = when (token.lastOrNull()) {
        ',', ';', '、', '：' -> 1.4f
        '.', '!', '?', '…', '。' -> 2.2f
        else -> 0f
    }
    return base + pauseBonus
}

fun synthesizeWordTimings(
    line: LyricsLine,
    nextLineStartMs: Long?,
    preferExplicit: Boolean = true,
    maxTokens: Int = 0
): List<LyricsWord> {
    if (preferExplicit && line.words.isNotEmpty()) {
        val explicit = line.words.filter { it.text.isNotBlank() }.map { word ->
            LyricsWord(
                text = word.text,
                startTimeMs = word.startTimeMs,
                endTimeMs = word.endTimeMs.coerceAtLeast(word.startTimeMs + 80L)
            )
        }
        if (explicit.isNotEmpty()) {
            return if (maxTokens > 0 && explicit.size > maxTokens) {
                explicit.take(maxTokens - 1) + explicit[maxTokens - 1].let { it.copy(text = "${it.text}...") }
            } else {
                explicit
            }
        }
    }

    val trimmed = line.text.trim()
    if (trimmed.isEmpty()) return emptyList()

    val rawTokens = trimmed
        .split(LYRIC_WHITESPACE_REGEX)
        .filter { it.isNotEmpty() }
        .flatMap { token ->
            if (token.length >= 8 && token.any(::isCompactScriptChar)) {
                token.chunked(3)
            } else {
                listOf(token)
            }
        }
    if (rawTokens.isEmpty()) return emptyList()

    val boundedTokens = if (maxTokens > 0 && rawTokens.size > maxTokens) {
        rawTokens.take(maxTokens - 1) + "${rawTokens[maxTokens - 1]}..."
    } else {
        rawTokens
    }

    val weights = boundedTokens.map(::lyricTokenWeight)
    val totalWeight = weights.sum().coerceAtLeast(1f)
    val startMs = line.startTimeMs
    val naturalVocalMs = (totalWeight * 155f + 900f).toLong().coerceIn(1_250L, 6_800L)
    val availableSpanMs = when {
        line.endTimeMs > startMs + 250L -> line.endTimeMs - startMs
        nextLineStartMs != null && nextLineStartMs > startMs + 250L ->
            ((nextLineStartMs - startMs) * 0.92f).toLong().coerceAtLeast(500L)
        else -> naturalVocalMs
    }
    val spanMs = minOf(availableSpanMs, naturalVocalMs).coerceIn(350L, 7_500L)

    var accumulatedWeight = 0f
    var cursorMs = startMs
    return weights.mapIndexed { index, weight ->
        val sliceStart = cursorMs
        accumulatedWeight += weight
        val sliceEnd = if (index == weights.lastIndex) {
            max(sliceStart + 60L, startMs + spanMs)
        } else {
            val remainingMinMs = (weights.lastIndex - index) * 45L
            val targetEndMs = startMs + ((accumulatedWeight / totalWeight) * spanMs).toLong()
            targetEndMs.coerceIn(
                sliceStart + 60L,
                max(sliceStart + 60L, startMs + spanMs - remainingMinMs)
            )
        }
        cursorMs = sliceEnd
        LyricsWord(
            text = boundedTokens[index],
            startTimeMs = sliceStart,
            endTimeMs = sliceEnd
        )
    }
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
    modifier: Modifier = Modifier,
    glowColor: Color = activeColor
) {
    val safeEndMs = endTimeMs.coerceAtLeast(startTimeMs + 1L)
    Box(
        modifier = modifier
            .drawBehind {
                if (!motionActive) return@drawBehind
                val progress = computeSmoothWordProgress(positionProvider(), startTimeMs, safeEndMs)
                if (progress <= 0.005f) return@drawBehind
                val lift = computeWordLiftEnvelope(progress)
                val settled = lyricSmoothstep(0.70f, 1.0f, progress)
                val haloEnergy = maxOf(lift, settled * 0.24f)
                if (haloEnergy <= 0.01f) return@drawBehind
                val radius = maxOf(size.width * 0.78f, size.height * 1.25f).coerceAtLeast(1f)
                val centerX = (progress * size.width).coerceIn(0f, size.width)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to glowColor.copy(alpha = 0.28f * haloEnergy),
                        0.45f to glowColor.copy(alpha = 0.09f * haloEnergy),
                        1f to Color.Transparent,
                        center = Offset(centerX, size.height * 0.52f),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(centerX, size.height * 0.52f)
                )
            }
            .graphicsLayer {
                val progress = computeSmoothWordProgress(positionProvider(), startTimeMs, safeEndMs)
                val lift = if (motionActive) computeWordLiftEnvelope(progress) else 0f
                val settled = if (motionActive) lyricSmoothstep(0.65f, 1.0f, progress) else 0f
                transformOrigin = TransformOrigin(0.5f, 0.78f)
                val s = 1f + 0.068f * lift + 0.014f * settled
                scaleX = s
                scaleY = s
                translationY = if (motionActive) {
                    -3.6.dp.toPx() * lift - 0.75.dp.toPx() * settled
                } else {
                    0f
                }
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
                    if (motionActive) {
                        val lift = computeWordLiftEnvelope(progress)
                        if (lift > 0.02f) {
                            val crestX = progress * size.width
                            val crestHalf = 18.dp.toPx().coerceAtMost(size.width * 0.45f).coerceAtLeast(4f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        glowColor.copy(alpha = 0.44f * lift),
                                        Color.White.copy(alpha = 0.34f * lift),
                                        Color.Transparent
                                    ),
                                    startX = crestX - crestHalf,
                                    endX = crestX + crestHalf,
                                    tileMode = TileMode.Clamp
                                ),
                                blendMode = BlendMode.SrcAtop
                            )
                        }
                    }
                    val feather = 22.dp.toPx().coerceAtMost(size.width * 0.58f).coerceAtLeast(1f)
                    val headX = progress * (size.width + feather)
                    val solidX = headX - feather
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black,
                            0.55f to Color.Black.copy(alpha = 0.92f),
                            1f to Color.Transparent,
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
