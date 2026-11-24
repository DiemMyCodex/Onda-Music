package com.onda.music.feature.player.styles

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.model.BedsideTint
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.feature.player.LikeActionButton
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.OndaLikeSlot
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.formatDurationMs
import com.onda.music.feature.player.playerPressBounce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findHostActivity()
    else -> null
}

@Composable
private fun KeepBedsideScreenOn() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findHostActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun TimelinePlayerStyle(
    playbackState: PlaybackState,
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val palette = OndaTheme.palette
    val state = playbackState
    val track = state.currentTrack
    val isPlaying = state.status == PlaybackStatus.PLAYING
    val timelineTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val activeIndex = state.currentIndex.coerceIn(0, (timelineTracks.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex, timelineTracks.size, reducedMotion) {
        if (timelineTracks.isNotEmpty() && activeIndex in timelineTracks.indices) {
            val target = (activeIndex - 1).coerceAtLeast(0)
            if (reducedMotion) {
                listState.scrollToItem(target)
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    var pulsePhase by remember { mutableFloatStateOf(0.25f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) {
            pulsePhase = 0.25f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    pulsePhase = (pulsePhase + dt * 0.56f) % 1f
                }
                lastNanos = frameNanos
            }
        }
    }

    val streamChipLabel = remember(state.activeStream) {
        state.activeStream?.let { stream ->
            val codecText = stream.codec.ifBlank { stream.qualityLabel }.uppercase(Locale.US)
            val rateText = if (stream.bitrateKbps > 0) "${stream.bitrateKbps}K" else stream.qualityLabel
            "${stream.source.name} · $codecText · $rateText"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .clickable(onClick = ctx.onOpenQueue),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(GlassTokens.RadiusPill))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Queue (${timelineTracks.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .clickable(onClick = ctx.onToggleInlineLyrics),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(if (ctx.showInlineLyrics) Color.White else Color(0xFF141414))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(GlassTokens.RadiusPill))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = if (ctx.showInlineLyrics) Color.Black else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (ctx.showInlineLyrics) "Timeline" else "Lyrics",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ctx.showInlineLyrics) Color.Black else Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        if (ctx.showInlineLyrics) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                    .background(Color(0xFF141414))
            ) {
                LyricsPane(
                    lyrics = ctx.lyrics,
                    positionMs = state.positionMs,
                    lyricsOffsetMs = ctx.lyricsOffsetMs,
                    isPlaying = isPlaying,
                    isLoading = ctx.isLyricsLoading,
                    errorMessage = ctx.lyricsError,
                    lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                    lyricsMotionStyle = ctx.settings.lyricsMotionStyle,
                    lyricsFontScale = ctx.settings.lyricsFontScale,
                    hapticsEnabled = ctx.settings.hapticsEnabled,
                    onSeekToMs = ctx.onSeekToMs,
                    onRefreshLyrics = ctx.onRefreshLyrics,
                    onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                    centered = false,
                    showControls = true,
                    playbackSpeed = state.playbackSpeed,
                    trackKey = track?.id,
                    accentColor = palette.primary
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = timelineTracks,
                    key = { index, item -> "${item.id}_$index" }
                ) { index, item ->
                    val isCurrent = index == activeIndex
                    val isPast = index < activeIndex
                    val nodeColor = when {
                        isCurrent -> palette.primary
                        isPast -> Color(0xFF555555)
                        else -> Color(0xFF9E9E9E)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(if (isCurrent) 118.dp else 68.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width * 0.5f
                                val cy = size.height * 0.5f
                                drawLine(
                                    color = Color(0xFF1F1F1F),
                                    start = Offset(cx, 0f),
                                    end = Offset(cx, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                                if (isCurrent) {
                                    val haloRadius = if (isPlaying && !reducedMotion) {
                                        6.5.dp.toPx() + 6.5.dp.toPx() * pulsePhase
                                    } else {
                                        10.dp.toPx()
                                    }
                                    val haloAlpha = if (isPlaying && !reducedMotion) {
                                        (1f - pulsePhase).coerceIn(0f, 1f) * 0.50f
                                    } else {
                                        0.24f
                                    }
                                    drawCircle(
                                        color = nodeColor.copy(alpha = haloAlpha),
                                        radius = haloRadius,
                                        center = Offset(cx, cy)
                                    )
                                }
                                drawCircle(
                                    color = nodeColor,
                                    radius = if (isCurrent) 6.5.dp.toPx() else 4.5.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                                .background(if (isCurrent) Color(0xFF202024) else Color(0xFF141414))
                                .clickable { ctx.onSelectQueueIndex(index) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TrackArtworkImage(
                                    artworkUrl = item.artworkUrl,
                                    contentDescription = item.title,
                                    shape = RoundedCornerShape(GlassTokens.RadiusSm),
                                    modifier = Modifier.size(if (isCurrent) 56.dp else 44.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isCurrent -> "NOW PLAYING"
                                            isPast -> "PLAYED"
                                            else -> "UP NEXT"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = nodeColor
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isCurrent) Color.White else Color(0xFF9E9E9E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF8A8A8A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCurrent && streamChipLabel != null) {
                                        Text(
                                            text = streamChipLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            ),
                                            color = palette.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                                .background(Color(0xFF141417))
                                                .border(
                                                    1.dp,
                                                    palette.primary.copy(alpha = 0.28f),
                                                    RoundedCornerShape(GlassTokens.RadiusPill)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isCurrent) {
                                    LikeActionButton(
                                        liked = ctx.liked,
                                        hapticsEnabled = ctx.settings.hapticsEnabled,
                                        onToggleLike = ctx.onLikeFromButton ?: ctx.onToggleLike,
                                        onButtonPositioned = ctx.onLikePositioned,
                                        likeBurstState = ctx.likeBurstState,
                                        likeSlot = OndaLikeSlot.TIMELINE
                                    )
                                } else {
                                    Text(
                                        text = formatDurationMs(item.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8A8A8A)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerSeekSection(
                playbackState = state,
                onSeekToFraction = ctx.onSeekToFraction,
                accentColor = Color.White
            )
            PlayerTransportControls(
                playbackState = state,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleShuffle = ctx.onToggleShuffle,
                onSkipPrevious = ctx.onSkipPrevious,
                onTogglePlayPause = ctx.onTogglePlayPause,
                onSkipNext = ctx.onSkipNext,
                onToggleRepeat = ctx.onToggleRepeat,
                compact = true
            )
        }
    }
}

@Composable
fun LyricsStagePlayerStyle(
    playbackState: PlaybackState,
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val palette = OndaTheme.palette
    val state = playbackState
    val track = state.currentTrack
    val offsetLabel = remember(ctx.lyricsOffsetMs) {
        val sign = if (ctx.lyricsOffsetMs >= 0L) "+" else ""
        "$sign${ctx.lyricsOffsetMs} ms"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            palette.primary.copy(alpha = 0.16f),
                            Color(0xFF141414),
                            Color(0xFF141414)
                        )
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(GlassTokens.RadiusLg)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtworkImage(
                    artworkUrl = track?.artworkUrl,
                    contentDescription = track?.title,
                    shape = RoundedCornerShape(GlassTokens.RadiusSm),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LikeActionButton(
                    liked = ctx.liked,
                    hapticsEnabled = ctx.settings.hapticsEnabled,
                    onToggleLike = ctx.onLikeFromButton ?: ctx.onToggleLike,
                    onButtonPositioned = ctx.onLikePositioned,
                    likeBurstState = ctx.likeBurstState,
                    likeSlot = OndaLikeSlot.LYRICS_STAGE
                )
                IconButton(
                    onClick = ctx.onTogglePlayPause,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ctx.lyrics?.source?.name?.replace('_', ' ') ?: "LYRICS STAGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp
                    ),
                    color = Color(0xFF9E9E9E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = offsetLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (ctx.lyricsOffsetMs == 0L) Color(0xFFD0D0D6) else palette.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color(0xFF1E1E22))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(GlassTokens.RadiusPill)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LyricsPane(
                lyrics = ctx.lyrics,
                positionMs = state.positionMs,
                lyricsOffsetMs = ctx.lyricsOffsetMs,
                isPlaying = state.status == PlaybackStatus.PLAYING,
                isLoading = ctx.isLyricsLoading,
                errorMessage = ctx.lyricsError,
                lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                lyricsMotionStyle = ctx.settings.lyricsMotionStyle,
                lyricsFontScale = ctx.settings.lyricsFontScale,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onSeekToMs = ctx.onSeekToMs,
                onRefreshLyrics = ctx.onRefreshLyrics,
                onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                centered = true,
                showControls = true,
                playbackSpeed = state.playbackSpeed,
                trackKey = track?.id,
                accentColor = palette.primary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                .background(Color(0xFF141414))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlayerSeekSection(
                playbackState = state,
                onSeekToFraction = ctx.onSeekToFraction,
                accentColor = Color.White
            )
            PlayerTransportControls(
                playbackState = state,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleShuffle = ctx.onToggleShuffle,
                onSkipPrevious = ctx.onSkipPrevious,
                onTogglePlayPause = ctx.onTogglePlayPause,
                onSkipNext = ctx.onSkipNext,
                onToggleRepeat = ctx.onToggleRepeat,
                compact = true
            )
        }
    }
}

@Composable
private fun rememberAnalogClockTime(smoothSeconds: Boolean): LocalTime {
    var nowTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(smoothSeconds) {
        if (smoothSeconds) {
            while (true) {
                withFrameNanos {
                    nowTime = LocalTime.now()
                }
            }
        } else {
            while (true) {
                nowTime = LocalTime.now()
                delay(1_000L)
            }
        }
    }
    return nowTime
}

@Composable
private fun AnalogWatchDialCanvas(
    time: LocalTime,
    reducedMotion: Boolean,
    dialColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val radius = size.minDimension * 0.46f

        drawCircle(
            color = dialColor.copy(alpha = 0.14f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.4.dp.toPx())
        )

        for (tick in 0 until 60) {
            val isMajor = tick % 5 == 0
            val angleRad = (tick * 6.0 - 90.0) * (PI / 180.0)
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            val outer = radius * 0.94f
            val inner = radius * if (isMajor) 0.80f else 0.88f
            drawLine(
                color = dialColor.copy(alpha = if (isMajor) 0.88f else 0.34f),
                start = Offset(center.x + cosA * inner, center.y + sinA * inner),
                end = Offset(center.x + cosA * outer, center.y + sinA * outer),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        val secondsFloat = if (reducedMotion) {
            time.second.toFloat()
        } else {
            time.second + (time.nano / 1_000_000_000f)
        }
        val minutesFloat = time.minute + secondsFloat / 60f
        val hoursFloat = (time.hour % 12) + minutesFloat / 60f

        val hourRad = (hoursFloat * 30.0 - 90.0) * (PI / 180.0)
        val minRad = (minutesFloat * 6.0 - 90.0) * (PI / 180.0)
        val secRad = (secondsFloat * 6.0 - 90.0) * (PI / 180.0)

        drawLine(
            color = dialColor,
            start = center,
            end = Offset(
                center.x + cos(hourRad).toFloat() * radius * 0.52f,
                center.y + sin(hourRad).toFloat() * radius * 0.52f
            ),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            color = dialColor.copy(alpha = 0.92f),
            start = center,
            end = Offset(
                center.x + cos(minRad).toFloat() * radius * 0.72f,
                center.y + sin(minRad).toFloat() * radius * 0.72f
            ),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            color = accentColor,
            start = Offset(
                center.x - cos(secRad).toFloat() * radius * 0.16f,
                center.y - sin(secRad).toFloat() * radius * 0.16f
            ),
            end = Offset(
                center.x + cos(secRad).toFloat() * radius * 0.82f,
                center.y + sin(secRad).toFloat() * radius * 0.82f
            ),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawCircle(
            color = accentColor,
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

private val BURN_IN_DRIFT_OFFSETS = listOf(
    0f to 0f,
    6f to -4f,
    8f to 4f,
    4f to 8f,
    -4f to 7f,
    -8f to 2f,
    -6f to -6f,
    2f to -8f
)

@Composable
fun BedsidePlayerStyle(
    playbackState: PlaybackState,
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    KeepBedsideScreenOn()

    val reducedMotion = OndaTheme.reducedMotion
    val palette = OndaTheme.palette
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = playbackState
    val track = state.currentTrack
    val isPlaying = state.status == PlaybackStatus.PLAYING

    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(lastInteractionMs) {
        nowEpochMs = lastInteractionMs
    }

    val idleMs = (nowEpochMs - lastInteractionMs).coerceAtLeast(0L)
    val secondaryDimmed = idleMs >= 4_000L
    val nightRestActive = idleMs >= 15_000L

    val restTintColor = when (ctx.settings.studioConfig.bedsideTint) {
        BedsideTint.AMBER -> Color(0xFFF4A68C)
        BedsideTint.CRIMSON -> Color(0xFFE56B6F)
        BedsideTint.ARTWORK -> palette.primary
    }

    val secondaryAlpha by animateFloatAsState(
        targetValue = if (secondaryDimmed) 0.22f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(450),
        label = "BedsideSecondaryAlpha"
    )
    val restClockColor by animateColorAsState(
        targetValue = if (nightRestActive) restTintColor else Color.White.copy(alpha = 0.94f),
        animationSpec = if (reducedMotion) snap() else tween(2_200),
        label = "BedsideClockColor"
    )
    val restLuminanceAlpha by animateFloatAsState(
        targetValue = if (nightRestActive) 0.55f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(2_200),
        label = "BedsideRestAlpha"
    )

    val burnInGuard = ctx.settings.studioConfig.bedsideBurnInGuard
    var driftStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(burnInGuard, reducedMotion) {
        if (!burnInGuard || reducedMotion) {
            driftStep = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(60_000L)
            driftStep = (driftStep + 1) % BURN_IN_DRIFT_OFFSETS.size
        }
    }
    val targetDrift = BURN_IN_DRIFT_OFFSETS[driftStep]
    val driftXDp by animateFloatAsState(
        targetValue = if (burnInGuard && !reducedMotion) targetDrift.first else 0f,
        animationSpec = if (reducedMotion) snap() else tween(4_000),
        label = "BedsideDriftX"
    )
    val driftYDp by animateFloatAsState(
        targetValue = if (burnInGuard && !reducedMotion) targetDrift.second else 0f,
        animationSpec = if (reducedMotion) snap() else tween(4_000),
        label = "BedsideDriftY"
    )

    val facePagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val clockTime = rememberAnalogClockTime(smoothSeconds = !reducedMotion && facePagerState.currentPage == 2)
    val nowDate = remember(clockTime.hour, clockTime.minute) { LocalDate.now() }
    val hhmmFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.US) }
    val hourFormatter = remember { DateTimeFormatter.ofPattern("HH", Locale.US) }
    val minuteFormatter = remember { DateTimeFormatter.ofPattern("mm", Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US) }

    val clockText = clockTime.format(hhmmFormatter)
    val hourText = clockTime.format(hourFormatter)
    val minuteText = clockTime.format(minuteFormatter)
    val dateText = nowDate.format(dateFormatter)

    val durationMs = state.durationMs.coerceAtLeast(1L)
    val baseProgress = (state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val shownProgress = scrubFraction ?: baseProgress

    var mediaSwipeOffsetX by remember { mutableFloatStateOf(0f) }
    val playInteraction = remember { MutableInteractionSource() }
    val currentHaptics by rememberUpdatedState(ctx.settings.hapticsEnabled)
    val currentOnSkipNext by rememberUpdatedState(ctx.onSkipNext)
    val currentOnSkipPrevious by rememberUpdatedState(ctx.onSkipPrevious)
    val currentOnSeekToFraction by rememberUpdatedState(ctx.onSeekToFraction)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .navigationBarsPadding()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionMs = System.currentTimeMillis()
                    }
                }
            }
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(driftXDp.dp.roundToPx(), driftYDp.dp.roundToPx()) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = secondaryAlpha * restLuminanceAlpha },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OndaMonogramMark(
                        tint = restClockColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ONDA NIGHTSTAND",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.9.sp
                        ),
                        color = Color(0xFF8A8A8A)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .clickable(onClick = ctx.onOpenSleepTimer)
                        .padding(horizontal = 10.dp)
                ) {
                    val sleepRemainingMs = state.sleepTimerRemainingMs
                    val timerActive = sleepRemainingMs != null || state.sleepTimerEndOfTrack
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (timerActive) restClockColor else Color(0xFF8A8A8A),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            state.sleepTimerEndOfTrack -> "End of track"
                            sleepRemainingMs != null -> "Sleep ${formatDurationMs(sleepRemainingMs)}"
                            else -> "Timer"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (timerActive) restClockColor else Color(0xFF8A8A8A)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = restLuminanceAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                HorizontalPager(
                    state = facePagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { face ->
                    when (face) {
                        0 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dateText.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        letterSpacing = 1.4.sp
                                    ),
                                    color = Color(0xFF8A8A8A)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OdometerText(
                                    text = clockText,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 82.sp,
                                        lineHeight = 86.sp,
                                        fontWeight = FontWeight.Light,
                                        letterSpacing = (-2.0).sp
                                    ),
                                    color = restClockColor
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = track?.let { "${it.title} — ${it.artist}" } ?: "Standby",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8A8A8A),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        1 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dateText.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = Color(0xFF8A8A8A)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OdometerText(
                                    text = hourText,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 64.sp,
                                        lineHeight = 68.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = restClockColor
                                )
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .padding(vertical = 6.dp)
                                        .height(1.5.dp)
                                        .background(restClockColor.copy(alpha = 0.35f))
                                )
                                OdometerText(
                                    text = minuteText,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 64.sp,
                                        lineHeight = 68.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = restClockColor.copy(alpha = 0.88f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                        .background(Color.White.copy(alpha = 0.14f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(shownProgress)
                                            .fillMaxHeight()
                                            .background(restClockColor)
                                    )
                                }
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AnalogWatchDialCanvas(
                                    time = clockTime,
                                    reducedMotion = reducedMotion,
                                    dialColor = restClockColor,
                                    accentColor = restTintColor,
                                    modifier = Modifier
                                        .fillMaxHeight(0.84f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.graphicsLayer { alpha = secondaryAlpha },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val faceLabels = listOf("Monolith", "Split Stack", "Analog Dial")
                    faceLabels.forEachIndexed { idx, label ->
                        val selected = facePagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = DockMetrics.MinTapTarget,
                                    minHeight = DockMetrics.MinTapTarget
                                )
                                .clickable {
                                    scope.launch {
                                        if (reducedMotion) {
                                            facePagerState.scrollToPage(idx)
                                        } else {
                                            facePagerState.animateScrollToPage(idx)
                                        }
                                    }
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (selected) 20.dp else 6.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                    .background(if (selected) restClockColor else Color(0xFF3A3A3F))
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = restLuminanceAlpha
                        translationX = mediaSwipeOffsetX * 0.35f
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                mediaSwipeOffsetX = (mediaSwipeOffsetX + dragAmount).coerceIn(-160f, 160f)
                            },
                            onDragEnd = {
                                if (mediaSwipeOffsetX < -64f) {
                                    if (currentHaptics) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    currentOnSkipNext()
                                } else if (mediaSwipeOffsetX > 64f) {
                                    if (currentHaptics) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    currentOnSkipPrevious()
                                }
                                mediaSwipeOffsetX = 0f
                            },
                            onDragCancel = {
                                mediaSwipeOffsetX = 0f
                            }
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrackArtworkImage(
                        artworkUrl = track?.artworkUrl,
                        contentDescription = track?.title,
                        shape = SmoothCornerShape(GlassTokens.RadiusSm),
                        modifier = Modifier.size(50.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "Nothing playing",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = restClockColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track?.artist ?: "Swipe media bar to skip",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8A8A8A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatDurationMs((durationMs * shownProgress).toLong()),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF9E9E9E),
                        modifier = Modifier.graphicsLayer { alpha = secondaryAlpha }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .height(DockMetrics.MinTapTarget)
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                currentOnSeekToFraction(fraction)
                            }
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    scrubFraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    scrubFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    scrubFraction?.let(currentOnSeekToFraction)
                                    scrubFraction = null
                                },
                                onDragCancel = {
                                    scrubFraction = null
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (scrubFraction != null) 5.dp else 3.dp)
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(shownProgress)
                                .fillMaxHeight()
                                .background(restClockColor.copy(alpha = 0.90f))
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = secondaryAlpha },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = ctx.onSkipPrevious,
                        modifier = Modifier.defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color(0xFF9E9E9E)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .playerPressBounce(
                                interactionSource = playInteraction,
                                pressedScale = 0.86f,
                                releaseDampingRatio = 0.54f,
                                releaseStiffness = 720f,
                                reducedMotion = reducedMotion
                            )
                            .clip(CircleShape)
                            .background(Color(0xFF141414))
                            .clickable(
                                interactionSource = playInteraction,
                                indication = null,
                                onClick = ctx.onTogglePlayPause
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = restClockColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = ctx.onSkipNext,
                        modifier = Modifier.defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = Color(0xFF9E9E9E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BedsideTilesPlayerStyle(
    playbackState: PlaybackState,
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    KeepBedsideScreenOn()

    val reducedMotion = OndaTheme.reducedMotion
    val palette = OndaTheme.palette
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = playbackState
    val track = state.currentTrack
    val isPlaying = state.status == PlaybackStatus.PLAYING

    val infoPagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val clockTime = rememberAnalogClockTime(smoothSeconds = !reducedMotion && infoPagerState.currentPage == 1)
    val nowDate = remember(clockTime.hour, clockTime.minute) { LocalDate.now() }
    val clockFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US) }
    val clockText = clockTime.format(clockFormatter)
    val dateText = nowDate.format(dateFormatter)

    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) {
            wavePhase = 0f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    wavePhase = (wavePhase + dt * 3.9f) % (2f * PI).toFloat()
                }
                lastNanos = frameNanos
            }
        }
    }

    val swipeElasticX = remember { Animatable(0f) }
    var swipeActionPreview by remember { mutableStateOf<String?>(null) }
    val currentHaptics by rememberUpdatedState(ctx.settings.hapticsEnabled)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentOnSkipNext by rememberUpdatedState(ctx.onSkipNext)
    val currentOnSkipPrevious by rememberUpdatedState(ctx.onSkipPrevious)

    val burnInGuard = ctx.settings.studioConfig.bedsideBurnInGuard
    var tilesDriftStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(burnInGuard, reducedMotion) {
        if (!burnInGuard || reducedMotion) {
            tilesDriftStep = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(60_000L)
            tilesDriftStep = (tilesDriftStep + 1) % BURN_IN_DRIFT_OFFSETS.size
        }
    }
    val tilesTargetDrift = BURN_IN_DRIFT_OFFSETS[tilesDriftStep]
    val tilesDriftXDp by animateFloatAsState(
        targetValue = if (burnInGuard && !reducedMotion) tilesTargetDrift.first else 0f,
        animationSpec = if (reducedMotion) snap() else tween(4_000),
        label = "BedsideTilesDriftX"
    )
    val tilesDriftYDp by animateFloatAsState(
        targetValue = if (burnInGuard && !reducedMotion) tilesTargetDrift.second else 0f,
        animationSpec = if (reducedMotion) snap() else tween(4_000),
        label = "BedsideTilesDriftY"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val wideLayout = maxWidth >= 560.dp

        val clockInfoTile: @Composable (Modifier) -> Unit = { tileModifier ->
            Column(
                modifier = tileModifier
                    .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                    .background(Color(0xFF141414))
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF9E9E9E),
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modeName = when (infoPagerState.currentPage) {
                            0 -> "WAVE"
                            1 -> "DIAL"
                            else -> "LYRICS"
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = DockMetrics.MinTapTarget,
                                    minHeight = DockMetrics.MinTapTarget
                                )
                                .clickable {
                                    val next = (infoPagerState.currentPage + 1) % 3
                                    scope.launch {
                                        if (reducedMotion) {
                                            infoPagerState.scrollToPage(next)
                                        } else {
                                            infoPagerState.animateScrollToPage(next)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = palette.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                    .background(Color(0xFF222228))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = DockMetrics.MinTapTarget,
                                    minHeight = DockMetrics.MinTapTarget
                                )
                                .clickable(onClick = ctx.onOpenSleepTimer)
                                .padding(horizontal = 6.dp)
                        ) {
                            val sleepRemainingMs = state.sleepTimerRemainingMs
                            val timerActive = sleepRemainingMs != null || state.sleepTimerEndOfTrack
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (timerActive) Color.White else Color(0xFF8A8A8A),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when {
                                    state.sleepTimerEndOfTrack -> "EOT"
                                    sleepRemainingMs != null -> formatDurationMs(sleepRemainingMs)
                                    else -> "Timer"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (timerActive) Color.White else Color(0xFF8A8A8A)
                            )
                        }
                    }
                }

                VerticalPager(
                    state = infoPagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        scope.launch {
                                            if (reducedMotion) {
                                                infoPagerState.scrollToPage(1)
                                            } else {
                                                infoPagerState.animateScrollToPage(1)
                                            }
                                        }
                                    },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                OdometerText(
                                    text = clockText,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 54.sp,
                                        lineHeight = 58.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                ) {
                                    val barCount = 24
                                    val barGap = size.width / barCount
                                    val barWidth = (barGap * 0.48f).coerceAtLeast(2.dp.toPx())
                                    for (i in 0 until barCount) {
                                        val amp = if (isPlaying && !reducedMotion) {
                                            0.28f + 0.72f * (0.5f + 0.5f * sin(wavePhase + i * 0.55f))
                                        } else if (isPlaying) {
                                            0.35f + 0.45f * (0.5f + 0.5f * sin(i * 0.65f))
                                        } else {
                                            0.18f
                                        }
                                        val h = (size.height * amp).coerceAtLeast(3.dp.toPx())
                                        val top = (size.height - h) * 0.5f
                                        drawRoundRect(
                                            color = if (i % 3 == 0) palette.primary else Color.White.copy(alpha = 0.68f),
                                            topLeft = Offset(i * barGap, top),
                                            size = Size(barWidth, h),
                                            cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        scope.launch {
                                            if (reducedMotion) {
                                                infoPagerState.scrollToPage(2)
                                            } else {
                                                infoPagerState.animateScrollToPage(2)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnalogWatchDialCanvas(
                                    time = clockTime,
                                    reducedMotion = reducedMotion,
                                    dialColor = Color.White,
                                    accentColor = palette.primary,
                                    modifier = Modifier
                                        .fillMaxHeight(0.92f)
                                        .aspectRatio(1f)
                                )
                            }
                        }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LyricsPane(
                                    lyrics = ctx.lyrics,
                                    positionMs = state.positionMs,
                                    lyricsOffsetMs = ctx.lyricsOffsetMs,
                                    isPlaying = isPlaying,
                                    isLoading = ctx.isLyricsLoading,
                                    errorMessage = ctx.lyricsError,
                                    lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                                    lyricsMotionStyle = ctx.settings.lyricsMotionStyle,
                                    lyricsFontScale = ctx.settings.lyricsFontScale * 0.85f,
                                    hapticsEnabled = ctx.settings.hapticsEnabled,
                                    onSeekToMs = ctx.onSeekToMs,
                                    onRefreshLyrics = ctx.onRefreshLyrics,
                                    onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                                    centered = true,
                                    showControls = false,
                                    playbackSpeed = state.playbackSpeed,
                                    trackKey = track?.id,
                                    accentColor = palette.primary
                                )
                            }
                        }
                    }
                }

                PlayerTransportControls(
                    playbackState = state,
                    hapticsEnabled = ctx.settings.hapticsEnabled,
                    onToggleShuffle = ctx.onToggleShuffle,
                    onSkipPrevious = ctx.onSkipPrevious,
                    onTogglePlayPause = ctx.onTogglePlayPause,
                    onSkipNext = ctx.onSkipNext,
                    onToggleRepeat = ctx.onToggleRepeat,
                    compact = true
                )
            }
        }

        val mediaTile: @Composable (Modifier) -> Unit = { tileModifier ->
            Box(
                modifier = tileModifier
                    .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                    .background(Color(0xFF141414))
                    .pointerInput(Unit) {
                        var totalSwipeX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalSwipeX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                totalSwipeX = (totalSwipeX + dragAmount).coerceIn(-180f, 180f)
                                scope.launch { swipeElasticX.snapTo(totalSwipeX * 0.32f) }
                                swipeActionPreview = when {
                                    totalSwipeX < -58f -> "NEXT TRACK"
                                    totalSwipeX > 58f -> "PREV TRACK"
                                    else -> null
                                }
                            },
                            onDragEnd = {
                                if (totalSwipeX < -58f) {
                                    if (currentHaptics) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    currentOnSkipNext()
                                } else if (totalSwipeX > 58f) {
                                    if (currentHaptics) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    currentOnSkipPrevious()
                                }
                                swipeActionPreview = null
                                scope.launch {
                                    if (currentReducedMotion) {
                                        swipeElasticX.snapTo(0f)
                                    } else {
                                        swipeElasticX.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 480f))
                                    }
                                }
                            },
                            onDragCancel = {
                                swipeActionPreview = null
                                scope.launch { swipeElasticX.animateTo(0f, spring()) }
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = swipeElasticX.value
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtworkImage(
                            artworkUrl = track?.artworkUrl,
                            contentDescription = track?.title,
                            shape = RoundedCornerShape(GlassTokens.RadiusSm),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track?.title ?: "Nothing playing",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track?.artist ?: "Swipe tile to skip",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9E9E9E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LikeActionButton(
                            liked = ctx.liked,
                            hapticsEnabled = ctx.settings.hapticsEnabled,
                            onToggleLike = ctx.onLikeFromButton ?: ctx.onToggleLike,
                            onButtonPositioned = ctx.onLikePositioned,
                            likeBurstState = ctx.likeBurstState,
                            likeSlot = OndaLikeSlot.BEDSIDE_TILES
                        )
                    }

                    PlayerSeekSection(
                        playbackState = state,
                        onSeekToFraction = ctx.onSeekToFraction,
                        accentColor = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LyricsPane(
                            lyrics = ctx.lyrics,
                            positionMs = state.positionMs,
                            lyricsOffsetMs = ctx.lyricsOffsetMs,
                            isPlaying = isPlaying,
                            isLoading = ctx.isLyricsLoading,
                            errorMessage = ctx.lyricsError,
                            lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                            lyricsMotionStyle = ctx.settings.lyricsMotionStyle,
                            lyricsFontScale = ctx.settings.lyricsFontScale * 0.85f,
                            hapticsEnabled = ctx.settings.hapticsEnabled,
                            onSeekToMs = ctx.onSeekToMs,
                            onRefreshLyrics = ctx.onRefreshLyrics,
                            onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                            centered = false,
                            showControls = false,
                            playbackSpeed = state.playbackSpeed,
                            trackKey = track?.id,
                            accentColor = palette.primary
                        )
                    }
                }

                swipeActionPreview?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.Black,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .background(palette.primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (wideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = tilesDriftXDp.dp.toPx()
                        translationY = tilesDriftYDp.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                clockInfoTile(Modifier.weight(1f).fillMaxHeight())
                mediaTile(Modifier.weight(1.15f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = tilesDriftXDp.dp.toPx()
                        translationY = tilesDriftYDp.dp.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                clockInfoTile(Modifier.weight(0.95f).fillMaxWidth())
                mediaTile(Modifier.weight(1.25f).fillMaxWidth())
            }
        }
    }
}
