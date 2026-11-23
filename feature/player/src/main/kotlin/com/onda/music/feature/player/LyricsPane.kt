package com.onda.music.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsWord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

fun findActiveLyricsLineIndex(
    lines: List<LyricsLine>,
    positionMs: Long,
    isSynced: Boolean
): Int {
    if (!isSynced || lines.isEmpty()) return -1
    var active = -1
    for (index in lines.indices) {
        val line = lines[index]
        if (positionMs >= line.startTimeMs) {
            active = index
        } else {
            break
        }
    }
    return active
}

@Composable
fun LyricsPane(
    lyrics: LyricsDocument?,
    positionMs: Long,
    lyricsOffsetMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    lyricsMotionEnabled: Boolean,
    lyricsFontScale: Float,
    hapticsEnabled: Boolean,
    onSeekToMs: (Long) -> Unit,
    onRefreshLyrics: () -> Unit,
    onAdjustOffsetMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
    showControls: Boolean = true
) {
    val palette = OndaTheme.palette
    val reducedMotion = OndaTheme.reducedMotion
    val motionActive = lyricsMotionEnabled && !reducedMotion
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val effectivePositionMs = (positionMs + lyricsOffsetMs).coerceAtLeast(0L)
    val lines = lyrics?.lines.orEmpty()
    val isSynced = lyrics?.isSynced == true
    val hasWordTiming = lyrics?.hasWordTiming == true
    val activeIndex = remember(lines, effectivePositionMs, isSynced) {
        findActiveLyricsLineIndex(lines, effectivePositionMs, isSynced)
    }

    var userDetachedScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userDetachedScroll = true
        }
    }

    LaunchedEffect(activeIndex, userDetachedScroll, isSynced) {
        if (isSynced && activeIndex >= 0 && !userDetachedScroll) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -140
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showControls) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onAdjustOffsetMs(-500L) },
                            modifier = Modifier.defaultMinSize(
                                minWidth = DockMetrics.MinTapTarget,
                                minHeight = DockMetrics.MinTapTarget
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Delay lyrics 500ms",
                                tint = GlassTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (lyricsOffsetMs == 0L) "Sync 0.0s" else String.format(Locale.US, "%.1fs", lyricsOffsetMs / 1000f),
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTokens.TextSecondary
                        )
                        IconButton(
                            onClick = { onAdjustOffsetMs(500L) },
                            modifier = Modifier.defaultMinSize(
                                minWidth = DockMetrics.MinTapTarget,
                                minHeight = DockMetrics.MinTapTarget
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Advance lyrics 500ms",
                                tint = GlassTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshLyrics,
                        modifier = Modifier.defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh lyrics",
                            tint = GlassTokens.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            when {
                isLoading && lines.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = palette.primary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Loading synced lyrics...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTokens.TextSecondary
                            )
                        }
                    }
                }

                lines.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = null,
                                tint = GlassTokens.TextTertiary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = errorMessage ?: "No lyrics available for this track",
                                style = MaterialTheme.typography.bodyLarge,
                                color = GlassTokens.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            GlassPillButton(
                                label = "Retry",
                                onClick = onRefreshLyrics
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.08f to Color.Black,
                                            0.90f to Color.Black,
                                            1.0f to Color.Transparent
                                        )
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { index, line -> "${index}_${line.startTimeMs}" }
                        ) { index, line ->
                            val isActive = isSynced && index == activeIndex
                            val isPast = isSynced && activeIndex >= 0 && index < activeIndex
                            val distance = if (activeIndex >= 0) abs(index - activeIndex).coerceAtMost(8) else 0

                            LyricsLineRow(
                                line = line,
                                isActive = isActive,
                                isPast = isPast,
                                isSynced = isSynced,
                                hasWordTiming = hasWordTiming,
                                effectivePositionMs = effectivePositionMs,
                                distanceFromActive = distance,
                                isPlaying = isPlaying,
                                motionActive = motionActive,
                                fontScale = lyricsFontScale,
                                centered = centered,
                                onClick = {
                                    if (isSynced) {
                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        userDetachedScroll = false
                                        onSeekToMs((line.startTimeMs - lyricsOffsetMs).coerceAtLeast(0L))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = userDetachedScroll && isSynced && activeIndex >= 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            GlassPillButton(
                label = "Sync",
                active = true,
                onClick = {
                    userDetachedScroll = false
                    scope.launch {
                        if (activeIndex >= 0) {
                            listState.animateScrollToItem(activeIndex, -140)
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun LyricsLineRow(
    line: LyricsLine,
    isActive: Boolean,
    isPast: Boolean,
    isSynced: Boolean,
    hasWordTiming: Boolean,
    effectivePositionMs: Long,
    distanceFromActive: Int,
    isPlaying: Boolean,
    motionActive: Boolean,
    fontScale: Float,
    centered: Boolean,
    onClick: () -> Unit
) {
    val palette = OndaTheme.palette
    val staggerTranslationY = remember { Animatable(0f) }

    LaunchedEffect(isActive, distanceFromActive, motionActive) {
        if (!motionActive) {
            staggerTranslationY.snapTo(0f)
            return@LaunchedEffect
        }
        val delayMs = distanceFromActive * AppMotion.LyricsStaggerDelayMs
        if (delayMs > 0L) {
            delay(delayMs)
        }
        val targetOffset = when {
            isActive -> 0f
            isPast -> -4f
            else -> 4f
        }
        staggerTranslationY.animateTo(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val targetScale = when {
        !isSynced -> 1f
        isActive -> 1.04f
        else -> 0.96f
    }
    val animatedScale by animateFloatAsState(
        targetValue = if (motionActive) targetScale else 1f,
        animationSpec = AppMotion.adaptiveSpring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            reducedMotion = !motionActive
        ),
        label = "LyricRowScale"
    )

    val textColor = when {
        !isSynced -> GlassTokens.TextPrimary
        isActive -> GlassTokens.TextPrimary
        isPast -> GlassTokens.TextTertiary.copy(alpha = 0.72f)
        else -> GlassTokens.TextSecondary.copy(alpha = 0.62f)
    }

    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clip(RoundedCornerShape(GlassTokens.RadiusSm))
            .clickable(enabled = isSynced, onClick = onClick)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                translationY = staggerTranslationY.value
            }
            .then(
                if (isActive) {
                    Modifier.background(
                        color = palette.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(GlassTokens.RadiusSm)
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (line.isInterlude) {
            val interludeDuration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(1L)
            val interludeProgress = ((effectivePositionMs - line.startTimeMs).toFloat() / interludeDuration).coerceIn(0f, 1f)
            LyricsInterludeDots(
                isActive = isActive,
                isPlaying = isPlaying,
                motionActive = motionActive,
                progress = interludeProgress,
                accentColor = palette.primary
            )
        } else if (hasWordTiming && line.words.isNotEmpty() && isActive) {
            WordSweepLine(
                words = line.words,
                effectivePositionMs = effectivePositionMs,
                fontScale = fontScale,
                centered = centered,
                activeColor = palette.primary,
                baseColor = GlassTokens.TextSecondary.copy(alpha = 0.55f)
            )
        } else {
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = (21f * fontScale).sp,
                    lineHeight = (28f * fontScale).sp,
                    fontWeight = if (isActive || !isSynced) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = textColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!line.romanizedText.isNullOrBlank()) {
            Text(
                text = line.romanizedText.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = (13f * fontScale).sp,
                    lineHeight = (18f * fontScale).sp
                ),
                color = if (isActive) palette.secondary else GlassTokens.TextTertiary,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!line.translatedText.isNullOrBlank()) {
            Text(
                text = line.translatedText.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (14f * fontScale).sp,
                    lineHeight = (20f * fontScale).sp
                ),
                color = if (isActive) GlassTokens.TextSecondary else GlassTokens.TextTertiary.copy(alpha = 0.8f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordSweepLine(
    words: List<LyricsWord>,
    effectivePositionMs: Long,
    fontScale: Float,
    centered: Boolean,
    activeColor: Color,
    baseColor: Color
) {
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = (22f * fontScale).sp,
        lineHeight = (30f * fontScale).sp,
        fontWeight = FontWeight.Bold
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        words.forEach { word ->
            val duration = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
            val progress = ((effectivePositionMs - word.startTimeMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val isLit = progress > 0f && progress < 1f
            val liftScale by animateFloatAsState(
                targetValue = if (isLit) 1.06f else 1f,
                animationSpec = AppMotion.SnappySpring,
                label = "WordLift"
            )

            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = liftScale
                    scaleY = liftScale
                }
            ) {
                Text(
                    text = word.text,
                    style = style,
                    color = baseColor
                )
                if (progress > 0f) {
                    Text(
                        text = word.text,
                        style = style,
                        color = if (progress >= 1f) GlassTokens.TextPrimary else activeColor,
                        modifier = Modifier.drawWithContent {
                            clipRect(right = size.width * progress) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LyricsInterludeDots(
    isActive: Boolean,
    isPlaying: Boolean,
    motionActive: Boolean,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isActive, isPlaying, motionActive) {
        if (!isActive || !isPlaying || !motionActive) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    phase = (phase + dt * 4.2f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    Canvas(
        modifier = modifier
            .height(26.dp)
            .width(72.dp)
    ) {
        val spacing = size.width / 4f
        val baseRadius = 5.dp.toPx()
        for (i in 0 until 3) {
            val dotThreshold = (i + 1) / 3f
            val dotEnergy = if (isActive) {
                ((progress - i * 0.25f) / 0.45f).coerceIn(0.25f, 1f)
            } else {
                0.25f
            }
            val bobOffset = if (isActive && motionActive && isPlaying) {
                sin(phase - i * 0.85f) * 4.5.dp.toPx()
            } else {
                0f
            }
            val radius = baseRadius * (0.85f + dotEnergy * 0.35f)
            val cx = spacing * (i + 1)
            val cy = size.height * 0.5f + bobOffset
            val color = if (isActive && progress >= dotThreshold * 0.7f) {
                accentColor
            } else {
                GlassTokens.TextSecondary.copy(alpha = 0.45f + dotEnergy * 0.4f)
            }
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(cx, cy)
            )
        }
    }
}
