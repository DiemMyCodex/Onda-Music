package com.onda.music.feature.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaAcousticBarLoader
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsWord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
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

@Stable
private class OndaLyricsStagger(rowCount: Int) {
    private val lags = FloatArray(rowCount)
    private val velocities = FloatArray(rowCount)
    private val version = mutableIntStateOf(0)
    private val moving = mutableStateOf(false)

    var anchorRow: Int = -1

    fun lagOf(row: Int): Float {
        version.intValue
        return if (row in lags.indices) lags[row] else 0f
    }

    fun holdBack(row: Int, deltaPx: Float) {
        if (row !in lags.indices) return
        lags[row] += deltaPx
        version.intValue++
        moving.value = true
    }

    fun clear() {
        lags.fill(0f)
        velocities.fill(0f)
        moving.value = false
        version.intValue++
    }

    suspend fun run() {
        while (true) {
            snapshotFlow { moving.value }.first { it }
            var lastNanos = 0L
            var stillActive: Boolean
            do {
                stillActive = withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) {
                        0.016f
                    } else {
                        ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)
                    }
                    lastNanos = now
                    step(dt).also { version.intValue++ }
                }
            } while (stillActive || lags.any { it != 0f })
            moving.value = false
        }
    }

    private fun step(dt: Float): Boolean {
        var anyActive = false
        for (row in lags.indices) {
            val lag = lags[row]
            val v = velocities[row]
            if (lag == 0f && v == 0f) continue
            val distance = abs(row - anchorRow).coerceIn(1, STAGGER_MAX_ROWS)
            val omega = 1000f / (STAGGER_LAG_PER_ROW_MS * distance)
            val e = exp(-omega * dt)
            val tmp = (v + omega * lag) * dt
            val nextLag = (lag + tmp) * e
            val nextV = (v - omega * tmp) * e
            if (abs(nextLag) < 0.3f && abs(nextV) < 4f) {
                lags[row] = 0f
                velocities[row] = 0f
            } else {
                lags[row] = nextLag
                velocities[row] = nextV
                anyActive = true
            }
        }
        return anyActive
    }

    private companion object {
        const val STAGGER_LAG_PER_ROW_MS = 38f
        const val STAGGER_MAX_ROWS = 7
    }
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
    showControls: Boolean = true,
    onLongPressLine: ((Int) -> Unit)? = null,
    playbackSpeed: Float = 1.0f,
    trackKey: Any? = null,
    accentColor: Color = Color.White
) {
    val reducedMotion = OndaTheme.reducedMotion
    val motionActive = lyricsMotionEnabled && !reducedMotion
    val glowTint = remember(accentColor) {
        colorLerp(Color.White, accentColor, 0.32f)
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val lines = lyrics?.lines.orEmpty()
    val isSynced = lyrics?.isSynced == true
    val hasWordTiming = lyrics?.hasWordTiming == true

    val lyricClock = rememberSmoothLyricClock(
        anchorPositionMs = positionMs,
        isPlaying = isPlaying,
        playbackSpeed = playbackSpeed,
        trackKey = trackKey,
        active = isSynced && lines.isNotEmpty()
    )

    val effectivePositionMs = (positionMs + lyricsOffsetMs).coerceAtLeast(0L)
    val activeIndex = remember(lines, effectivePositionMs, isSynced) {
        findActiveLyricsLineIndex(lines, effectivePositionMs, isSynced)
    }

    val stagger = remember(lines.size) { OndaLyricsStagger(lines.size) }
    LaunchedEffect(stagger, motionActive) {
        if (!motionActive) {
            stagger.clear()
            return@LaunchedEffect
        }
        stagger.run()
    }

    var userDetachedScroll by remember(trackKey) { mutableStateOf(false) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userDetachedScroll = true
            }
        }
    }

    LaunchedEffect(userDetachedScroll, listState.isScrollInProgress) {
        if (userDetachedScroll && !listState.isScrollInProgress) {
            delay(5_500L)
            userDetachedScroll = false
        }
    }

    val minOffsetPx = with(density) { 96.dp.toPx() }
    val maxOffsetPx = with(density) { 190.dp.toPx() }
    val fallbackOffsetPx = with(density) { 140.dp.toPx() }

    LaunchedEffect(activeIndex, userDetachedScroll, isSynced, motionActive, lines.size) {
        if (!isSynced || activeIndex !in lines.indices || userDetachedScroll) return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
        val restingOffsetPx = if (viewportHeight > 0f) {
            (viewportHeight * 0.28f).coerceIn(minOffsetPx, maxOffsetPx)
        } else {
            fallbackOffsetPx
        }
        val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (visible != null && motionActive) {
            val currentTop = visible.offset - layoutInfo.viewportStartOffset
            val collapsingAbove = layoutInfo.visibleItemsInfo
                .filter { info ->
                    info.index < activeIndex && lines.getOrNull(info.index)?.isInterlude == true
                }
                .sumOf { it.size }
            val distance = (currentTop - restingOffsetPx - collapsingAbove).toFloat()
            stagger.anchorRow = activeIndex
            if (abs(distance) > 1f) {
                listState.scroll {
                    var previous = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = distance,
                        animationSpec = spring(
                            dampingRatio = 0.88f,
                            stiffness = 170f
                        )
                    ) { value, _ ->
                        val consumed = scrollBy(value - previous)
                        previous += consumed
                        if (consumed != 0f) {
                            for (item in listState.layoutInfo.visibleItemsInfo) {
                                if ((consumed > 0f && item.index > activeIndex) ||
                                    (consumed < 0f && item.index < activeIndex)
                                ) {
                                    stagger.holdBack(item.index, consumed)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            stagger.clear()
            if (reducedMotion || !motionActive) {
                listState.scrollToItem(index = activeIndex, scrollOffset = -restingOffsetPx.roundToInt())
            } else {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = -restingOffsetPx.roundToInt())
            }
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
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OndaAcousticBarLoader(
                                barCount = 5,
                                barWidth = 3.dp,
                                barSpacing = 5.dp,
                                maxHeight = 22.dp,
                                color = glowTint
                            )
                            Text(
                                text = "Loading synced lyrics...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9E9E9E)
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
                    val smoothPositionProvider: () -> Long = remember(lyricClock, lyricsOffsetMs) {
                        { (lyricClock.positionMs + lyricsOffsetMs).coerceAtLeast(0L) }
                    }
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
                            val nextLineStartMs = lines.getOrNull(index + 1)?.startTimeMs

                            LyricsLineRow(
                                rowIndex = index,
                                line = line,
                                nextLineStartMs = nextLineStartMs,
                                isActive = isActive,
                                isPast = isPast,
                                isSynced = isSynced,
                                hasWordTiming = hasWordTiming,
                                smoothPositionProvider = smoothPositionProvider,
                                glowColor = glowTint,
                                distanceFromActive = distance,
                                isUserScrolling = userDetachedScroll,
                                isPlaying = isPlaying,
                                motionActive = motionActive,
                                stagger = stagger,
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
                                },
                                onLongClick = if (onLongPressLine != null) {
                                    {
                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        onLongPressLine(index)
                                    }
                                } else {
                                    null
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
                            val vh = listState.layoutInfo.viewportSize.height.toFloat()
                            val targetOffsetPx = if (vh > 0f) {
                                (vh * 0.28f).coerceIn(minOffsetPx, maxOffsetPx)
                            } else {
                                fallbackOffsetPx
                            }
                            listState.animateScrollToItem(activeIndex, -targetOffsetPx.roundToInt())
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricsLineRow(
    rowIndex: Int,
    line: LyricsLine,
    nextLineStartMs: Long?,
    isActive: Boolean,
    isPast: Boolean,
    isSynced: Boolean,
    hasWordTiming: Boolean,
    smoothPositionProvider: () -> Long,
    glowColor: Color,
    distanceFromActive: Int,
    isUserScrolling: Boolean,
    isPlaying: Boolean,
    motionActive: Boolean,
    stagger: OndaLyricsStagger,
    fontScale: Float,
    centered: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    if (line.isInterlude) {
        val interludeDuration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(1L)
        val interludeProgress = if (isActive) {
            ((smoothPositionProvider() - line.startTimeMs).toFloat() / interludeDuration).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = if (motionActive) stagger.lagOf(rowIndex) else 0f
                },
            contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
        ) {
            LyricsInterludeDots(
                isActive = isActive,
                isPlaying = isPlaying,
                motionActive = motionActive,
                progress = interludeProgress,
                accentColor = glowColor,
                visible = isActive
            )
        }
        return
    }

    val focus by animateFloatAsState(
        targetValue = if (!isSynced || isActive) 1f else 0f,
        animationSpec = AppMotion.adaptiveSpring(
            dampingRatio = 0.86f,
            stiffness = 240f,
            reducedMotion = !motionActive
        ),
        label = "LyricRowFocus"
    )

    val inactiveAlpha = when {
        distanceFromActive <= 1 -> if (isPast) 0.40f else 0.58f
        distanceFromActive == 2 -> if (isPast) 0.26f else 0.38f
        else -> if (isPast) 0.16f else 0.24f
    }
    val rowAlpha = if (!isSynced) 1f else lerp(inactiveAlpha, 1f, focus)
    val rowScale = if (motionActive && isSynced) lerp(0.935f, 1.035f, focus) else 1f
    val rowBlurPx = when {
        !motionActive || !isSynced || isActive || isUserScrolling || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> 0f
        distanceFromActive <= 1 -> 1.0f
        distanceFromActive == 2 -> 2.5f
        distanceFromActive == 3 -> 4.0f
        else -> 5.5f
    }

    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    val rowInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val pillRadiusPx = with(density) { GlassTokens.RadiusSm.toPx() }
    val pillarWidthPx = with(density) { 2.5.dp.toPx() }
    val showWordSweep = isSynced && line.text.isNotBlank() && (isActive || (isPast && focus > 0.08f))
    val resolvedWords = remember(line, nextLineStartMs, hasWordTiming) {
        if (isSynced && line.text.isNotBlank()) {
            synthesizeWordTimings(
                line = line,
                nextLineStartMs = nextLineStartMs,
                preferExplicit = hasWordTiming
            )
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .playerPressBounce(
                interactionSource = rowInteraction,
                pressedScale = 0.985f,
                releaseDampingRatio = 0.54f,
                releaseStiffness = 720f,
                reducedMotion = !motionActive
            )
            .clip(RoundedCornerShape(GlassTokens.RadiusSm))
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = RoundedHighlight(cornerRadius = GlassTokens.RadiusSm),
                enabled = isSynced || onLongClick != null,
                onClick = {
                    if (isSynced) {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            )
            .graphicsLayer {
                transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
                scaleX = rowScale
                scaleY = rowScale
                alpha = rowAlpha
                translationY = if (motionActive) stagger.lagOf(rowIndex) else 0f
                renderEffect = if (motionActive && rowBlurPx > 0f) {
                    PlayerLineBlurCache.forRadiusPx(rowBlurPx)
                } else {
                    null
                }
            }
            .drawBehind {
                val glowAlpha = 0.10f * focus
                if (isSynced && glowAlpha > 0.002f) {
                    val spotlightBrush = if (centered) {
                        Brush.horizontalGradient(
                            0.0f to Color.Transparent,
                            0.5f to glowColor.copy(alpha = glowAlpha),
                            1.0f to Color.Transparent
                        )
                    } else {
                        Brush.horizontalGradient(
                            0.0f to glowColor.copy(alpha = glowAlpha),
                            0.65f to glowColor.copy(alpha = glowAlpha * 0.42f),
                            1.0f to Color.Transparent
                        )
                    }
                    drawRoundRect(
                        brush = spotlightBrush,
                        cornerRadius = CornerRadius(pillRadiusPx, pillRadiusPx)
                    )
                    if (!centered) {
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.72f * focus),
                            topLeft = Offset(0f, size.height * 0.16f),
                            size = Size(pillarWidthPx, size.height * 0.68f),
                            cornerRadius = CornerRadius(pillarWidthPx * 0.5f, pillarWidthPx * 0.5f)
                        )
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showWordSweep && resolvedWords.isNotEmpty()) {
            WordSweepLine(
                words = resolvedWords,
                smoothPositionProvider = smoothPositionProvider,
                fontScale = fontScale,
                centered = centered,
                motionActive = motionActive,
                activeColor = Color.White,
                baseColor = Color(0x52FFFFFF),
                glowColor = glowColor
            )
        } else {
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = (21f * fontScale).sp,
                    lineHeight = (28f * fontScale).sp,
                    fontWeight = FontWeight.Bold
                ),
                color = GlassTokens.TextPrimary,
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
                color = Color(0xFF9E9E9E),
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
                color = GlassTokens.TextSecondary,
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
    smoothPositionProvider: () -> Long,
    fontScale: Float,
    centered: Boolean,
    motionActive: Boolean,
    activeColor: Color,
    baseColor: Color,
    glowColor: Color
) {
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = (21f * fontScale).sp,
        lineHeight = (28f * fontScale).sp,
        fontWeight = FontWeight.Bold
    )
    val wordGap = (5.5f * fontScale.coerceIn(0.8f, 1.4f)).dp

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) {
            Arrangement.spacedBy(wordGap, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(wordGap)
        },
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        words.forEach { word ->
            FeatheredSweepWord(
                text = word.text,
                style = style,
                startTimeMs = word.startTimeMs,
                endTimeMs = word.endTimeMs.coerceAtLeast(word.startTimeMs + 80L),
                positionProvider = smoothPositionProvider,
                activeColor = activeColor,
                baseColor = baseColor,
                motionActive = motionActive,
                glowColor = glowColor
            )
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
    modifier: Modifier = Modifier,
    visible: Boolean = isActive
) {
    val reducedMotion = OndaTheme.reducedMotion
    val animateMotion = motionActive && !reducedMotion
    val density = LocalDensity.current
    val fullHeightPx = with(density) { 40.dp.roundToPx() }

    val heightFraction = remember { Animatable(if (visible) 1f else 0f) }
    val pops = remember { Array(3) { Animatable(if (visible) 1f else 0f) } }

    LaunchedEffect(visible, animateMotion) {
        if (visible) {
            if (!animateMotion) {
                heightFraction.snapTo(1f)
                pops.forEach { it.snapTo(1f) }
            } else {
                launch {
                    heightFraction.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
                pops.forEachIndexed { index, pop ->
                    launch {
                        delay(index * 70L)
                        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
                    }
                }
            }
        } else {
            if (!animateMotion) {
                heightFraction.snapTo(0f)
                pops.forEach { it.snapTo(0f) }
            } else {
                launch {
                    heightFraction.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
                    )
                }
                pops.forEach { pop ->
                    launch {
                        pop.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
    }

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isActive, isPlaying, animateMotion, visible) {
        if (!visible || !isActive || !isPlaying || !animateMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    phase = (phase + dt * 3.8f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    val clampedProgress = progress.coerceIn(0f, 1f)
    val finale = smoothStep((clampedProgress - 0.88f) / 0.12f)

    Canvas(
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minHeight = fullHeightPx, maxHeight = fullHeightPx)
                )
                val shownHeight = (fullHeightPx * heightFraction.value.coerceIn(0f, 1f)).roundToInt()
                layout(placeable.width, shownHeight) {
                    placeable.place(0, (shownHeight - fullHeightPx) / 2)
                }
            }
            .height(40.dp)
            .width(84.dp)
            .graphicsLayer {
                alpha = heightFraction.value.coerceIn(0f, 1f)
            }
    ) {
        val shown = heightFraction.value.coerceIn(0f, 1f)
        if (shown <= 0.005f) return@Canvas

        val spacing = size.width / 4f
        val baseRadius = 5.5.dp.toPx()
        for (i in 0 until 3) {
            val sliceStart = i.toFloat() / 3f
            val sequentialFill = if (isActive) {
                smoothStep((clampedProgress - sliceStart) * 3f / 0.55f)
            } else {
                0f
            }
            val combinedEnergy = maxOf(sequentialFill, finale)
            val pop = pops[i].value.coerceAtLeast(0f)
            val bobOffset = if (isActive && animateMotion && isPlaying) {
                sin(phase - i * 0.9f) * 2.5.dp.toPx()
            } else {
                0f
            }
            val radius = baseRadius * (0.86f + combinedEnergy * 0.24f) * pop
            val cx = spacing * (i + 1)
            val cy = size.height * 0.5f + bobOffset
            val dotAlpha = (0.30f + 0.70f * combinedEnergy) * pop.coerceIn(0f, 1f)

            if (combinedEnergy > 0.2f && isActive) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.22f * combinedEnergy * pop.coerceIn(0f, 1f)),
                    radius = radius * 1.65f,
                    center = Offset(cx, cy)
                )
            }
            drawCircle(
                color = accentColor.copy(alpha = dotAlpha),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
    }
}

private fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
