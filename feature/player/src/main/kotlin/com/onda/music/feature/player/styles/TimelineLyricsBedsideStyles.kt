package com.onda.music.feature.player.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.feature.player.LikeActionButton
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.formatDurationMs
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimelinePlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val state = ctx.playbackState
    val track = state.currentTrack
    val timelineTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val activeIndex = state.currentIndex.coerceIn(0, (timelineTracks.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex, timelineTracks.size) {
        if (timelineTracks.isNotEmpty() && activeIndex in timelineTracks.indices) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = timelineTracks,
                key = { index, item -> "${item.id}_$index" }
            ) { index, item ->
                val isCurrent = index == activeIndex
                val isPast = index < activeIndex
                val nodeColor = when {
                    isCurrent -> Color.White
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
                            .height(if (isCurrent) 112.dp else 68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width * 0.5f
                            drawLine(
                                color = Color(0xFF1F1F1F),
                                start = Offset(cx, 0f),
                                end = Offset(cx, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(
                                color = nodeColor,
                                radius = if (isCurrent) 7.dp.toPx() else 4.5.dp.toPx(),
                                center = Offset(cx, size.height * 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                            .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                            .background(if (isCurrent) Color(0xFF242424) else Color(0xFF141414))
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isCurrent -> "NOW PLAYING"
                                        isPast -> "PLAYED"
                                        else -> "UP NEXT"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
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
                            }
                            if (isCurrent) {
                                LikeActionButton(
                                    liked = ctx.liked,
                                    hapticsEnabled = ctx.settings.hapticsEnabled,
                                    onToggleLike = ctx.onToggleLike,
                                    onButtonPositioned = ctx.onLikePositioned
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
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val state = ctx.playbackState
    val track = state.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                .background(Color(0xFF141414))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkImage(
                artworkUrl = track?.artworkUrl,
                contentDescription = track?.title,
                shape = RoundedCornerShape(GlassTokens.RadiusSm),
                modifier = Modifier.size(48.dp)
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
                onToggleLike = ctx.onToggleLike,
                onButtonPositioned = ctx.onLikePositioned
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
                lyricsFontScale = ctx.settings.lyricsFontScale,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onSeekToMs = ctx.onSeekToMs,
                onRefreshLyrics = ctx.onRefreshLyrics,
                onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                centered = true,
                showControls = true
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
private fun rememberClockStrings(): Pair<String, String> {
    var nowTime by remember { mutableStateOf(LocalTime.now()) }
    var nowDate by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTime = LocalTime.now()
            nowDate = LocalDate.now()
            delay(1_000L)
        }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US) }
    return nowTime.format(timeFormatter) to nowDate.format(dateFormatter)
}

@Composable
fun BedsidePlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val state = ctx.playbackState
    val track = state.currentTrack
    val (clockText, dateText) = rememberClockStrings()
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    val durationMs = state.durationMs.coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-160f, 160f)
                    },
                    onDragEnd = {
                        if (swipeOffsetX < -64f) {
                            ctx.onSkipNext()
                        } else if (swipeOffsetX > 64f) {
                            ctx.onSkipPrevious()
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = {
                        swipeOffsetX = 0f
                    }
                )
            }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = swipeOffsetX * 0.35f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = dateText.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF8A8A8A)
                )
                OdometerText(
                    text = clockText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        lineHeight = 76.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = Color.White.copy(alpha = 0.92f)
                )
                state.sleepTimerRemainingMs?.let { remaining ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clickable(onClick = ctx.onOpenSleepTimer)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Sleep in ${formatDurationMs(remaining)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TrackArtworkImage(
                    artworkUrl = track?.artworkUrl,
                    contentDescription = track?.title,
                    shape = CircleShape,
                    modifier = Modifier.size(84.dp)
                )
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.90f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "Swipe to skip tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9E9E9E),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.85f))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            .clip(CircleShape)
                            .background(Color(0xFF141414))
                            .clickable(onClick = ctx.onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = Color.White,
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
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val state = ctx.playbackState
    val track = state.currentTrack
    val (clockText, dateText) = rememberClockStrings()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val wideLayout = maxWidth >= 560.dp

        val clockTile: @Composable (Modifier) -> Unit = { tileModifier ->
            Column(
                modifier = tileModifier
                    .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                    .background(Color(0xFF141414))
                    .padding(18.dp),
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
                        color = Color(0xFF9E9E9E)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                            .clickable(onClick = ctx.onOpenSleepTimer)
                            .padding(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepTimerRemainingMs != null) Color.White else Color(0xFF8A8A8A),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = state.sleepTimerRemainingMs?.let(::formatDurationMs) ?: "Timer",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.sleepTimerRemainingMs != null) Color.White else Color(0xFF8A8A8A)
                        )
                    }
                }

                OdometerText(
                    text = clockText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 56.sp,
                        lineHeight = 60.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
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

        val trackTile: @Composable (Modifier) -> Unit = { tileModifier ->
            Column(
                modifier = tileModifier
                    .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                    .background(Color(0xFF141414))
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
                        onToggleLike = ctx.onToggleLike,
                        onButtonPositioned = ctx.onLikePositioned
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
                        isPlaying = state.status == PlaybackStatus.PLAYING,
                        isLoading = ctx.isLyricsLoading,
                        errorMessage = ctx.lyricsError,
                        lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                        lyricsFontScale = ctx.settings.lyricsFontScale * 0.85f,
                        hapticsEnabled = ctx.settings.hapticsEnabled,
                        onSeekToMs = ctx.onSeekToMs,
                        onRefreshLyrics = ctx.onRefreshLyrics,
                        onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                        centered = false,
                        showControls = false
                    )
                }
            }
        }

        if (wideLayout) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                clockTile(Modifier.weight(1f).fillMaxHeight())
                trackTile(Modifier.weight(1.15f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                clockTile(Modifier.weight(0.95f).fillMaxWidth())
                trackTile(Modifier.weight(1.25f).fillMaxWidth())
            }
        }
    }
}
