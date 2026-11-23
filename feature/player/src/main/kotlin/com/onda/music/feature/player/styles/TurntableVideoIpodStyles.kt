package com.onda.music.feature.player.styles

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.TrackMetaHeader
import com.onda.music.feature.player.formatDurationMs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TurntablePlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val state = ctx.playbackState
    val track = state.currentTrack
    val isPlaying = state.status == PlaybackStatus.PLAYING

    val durationMs = state.durationMs.coerceAtLeast(1L)
    val trackProgress = (state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var draggingTonearmProgress by remember { mutableStateOf<Float?>(null) }
    val activeTonearmProgress = draggingTonearmProgress ?: trackProgress

    var discAngleDeg by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    discAngleDeg = (discAngleDeg + dt * 42f) % 360f
                }
                lastNanos = frameNanos
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val discSize = minOf(maxWidth, maxHeight) * 0.86f
                Box(
                    modifier = Modifier
                        .size(discSize)
                        .graphicsLayer {
                            rotationZ = discAngleDeg
                        }
                        .clip(CircleShape)
                        .background(Color(0xFF141414)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width * 0.5f, size.height * 0.5f)
                        val maxR = size.minDimension * 0.49f
                        for (ring in 1..10) {
                            val r = maxR * (0.44f + ring * 0.052f)
                            drawCircle(
                                color = Color.White.copy(alpha = if (ring % 2 == 0) 0.09f else 0.04f),
                                radius = r,
                                center = center,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent,
                                    Color(0xFF9E9E9E).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = maxR * 0.45f)
                        )
                    }

                    TrackArtworkImage(
                        artworkUrl = track?.artworkUrl,
                        contentDescription = track?.title,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize(0.44f)
                    )

                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000))
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(durationMs) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val pivot = Offset(size.width * 0.87f, size.height * 0.14f)
                                    val rad = atan2(pos.y - pivot.y, pos.x - pivot.x)
                                    val deg = Math.toDegrees(rad.toDouble()).toFloat()
                                    val normalized = ((deg - 105f) / 36f).coerceIn(0f, 1f)
                                    draggingTonearmProgress = normalized
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pivot = Offset(size.width * 0.87f, size.height * 0.14f)
                                    val rad = atan2(change.position.y - pivot.y, change.position.x - pivot.x)
                                    val deg = Math.toDegrees(rad.toDouble()).toFloat()
                                    val normalized = ((deg - 105f) / 36f).coerceIn(0f, 1f)
                                    draggingTonearmProgress = normalized
                                },
                                onDragEnd = {
                                    draggingTonearmProgress?.let(ctx.onSeekToFraction)
                                    draggingTonearmProgress = null
                                },
                                onDragCancel = {
                                    draggingTonearmProgress = null
                                }
                            )
                        }
                ) {
                    val pivot = Offset(size.width * 0.87f, size.height * 0.14f)
                    val armLength = size.minDimension * 0.56f
                    val restAngle = 105f
                    val endAngle = 141f
                    val tonearmAngleDeg = if (isPlaying || draggingTonearmProgress != null) {
                        lerp(restAngle, endAngle, activeTonearmProgress)
                    } else {
                        92f
                    }

                    drawCircle(
                        color = Color(0xFF242424),
                        radius = 18.dp.toPx(),
                        center = pivot
                    )

                    rotate(degrees = tonearmAngleDeg, pivot = pivot) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f),
                            start = Offset(pivot.x - 16.dp.toPx(), pivot.y),
                            end = Offset(pivot.x + armLength, pivot.y),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(pivot.x + armLength, pivot.y)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackMetaHeader(
                track = track,
                liked = ctx.liked,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleLike = ctx.onToggleLike,
                onLikePositioned = ctx.onLikePositioned,
                downloadItem = ctx.downloadItem,
                onDownloadClick = ctx.onDownloadClick
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OdometerText(
                    text = formatDurationMs((durationMs * activeTonearmProgress).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Text(
                    text = "Drag tonearm to seek",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A8A8A)
                )
                Text(
                    text = formatDurationMs(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9E9E9E)
                )
            }

            PlayerTransportControls(
                playbackState = state,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleShuffle = ctx.onToggleShuffle,
                onSkipPrevious = ctx.onSkipPrevious,
                onTogglePlayPause = ctx.onTogglePlayPause,
                onSkipNext = ctx.onSkipNext,
                onToggleRepeat = ctx.onToggleRepeat
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val state = ctx.playbackState
    val track = state.currentTrack
    val player = ctx.activePlayer

    var hasVideoFrames by remember(player, track?.id) {
        val currentSize = player?.videoSize
        mutableStateOf(currentSize != null && currentSize.width > 0 && currentSize.height > 0)
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    hasVideoFrames = videoSize.width > 0 && videoSize.height > 0
                }
            }
            val initial = player.videoSize
            hasVideoFrames = initial.width > 0 && initial.height > 0
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center
        ) {
            TrackArtworkImage(
                artworkUrl = track?.artworkUrl,
                contentDescription = track?.title,
                shape = RoundedCornerShape(GlassTokens.RadiusLg),
                modifier = Modifier.fillMaxSize()
            )

            if (player != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        if (playerView.player !== player) {
                            playerView.player = player
                        }
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (hasVideoFrames) 1f else 0f
                        }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !hasVideoFrames,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Audio Fallback",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color(0xCC141414))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

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
                isPlaying = state.status == PlaybackStatus.PLAYING,
                isLoading = ctx.isLyricsLoading,
                errorMessage = ctx.lyricsError,
                lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                lyricsFontScale = ctx.settings.lyricsFontScale,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onSeekToMs = ctx.onSeekToMs,
                onRefreshLyrics = ctx.onRefreshLyrics,
                onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                centered = false,
                showControls = true
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrackMetaHeader(
                track = track,
                liked = ctx.liked,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleLike = ctx.onToggleLike,
                onLikePositioned = ctx.onLikePositioned,
                downloadItem = ctx.downloadItem,
                onDownloadClick = ctx.onDownloadClick
            )
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
fun IpodPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val state = ctx.playbackState
    val track = state.currentTrack
    val displayTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val safeIndex = state.currentIndex.coerceIn(0, (displayTracks.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { displayTracks.size.coerceAtLeast(1) }
    )

    LaunchedEffect(safeIndex, displayTracks.size) {
        if (displayTracks.isNotEmpty() && pagerState.currentPage != safeIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    LaunchedEffect(pagerState, displayTracks.size) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .distinctUntilChanged()
            .collect { (settled, _) ->
                if (displayTracks.isNotEmpty() && settled != safeIndex && settled in displayTracks.indices) {
                    ctx.onSelectQueueIndex(settled)
                }
            }
    }

    var wheelScrubFraction by remember { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs.coerceAtLeast(1L)
    val baseProgress = (state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val shownProgress = wheelScrubFraction ?: baseProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ONDA POD",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = if (displayTracks.isNotEmpty()) "${safeIndex + 1} / ${displayTracks.size}" else "0 / 0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (ctx.showInlineLyrics) {
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
                        showControls = false
                    )
                } else if (displayTracks.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        pageSpacing = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = displayTracks[page]
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val absOff = abs(offset).coerceIn(0f, 1.5f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp)
                                .graphicsLayer {
                                    rotationY = offset.coerceIn(-1f, 1f) * 34f
                                    scaleX = lerp(1f, 0.78f, absOff)
                                    scaleY = lerp(1f, 0.78f, absOff)
                                    alpha = lerp(1f, 0.55f, absOff)
                                    cameraDistance = 14f * density
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            TrackArtworkImage(
                                artworkUrl = item.artworkUrl,
                                contentDescription = item.title,
                                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                } else {
                    TrackArtworkImage(
                        artworkUrl = null,
                        contentDescription = null,
                        modifier = Modifier.size(140.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "Select a track",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(shownProgress)
                            .height(6.dp)
                            .background(Color.White)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OdometerText(
                        text = formatDurationMs((durationMs * shownProgress).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9E9E9E)
                    )
                    Text(
                        text = formatDurationMs(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8A8A8A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .size(228.dp)
                .clip(CircleShape)
                .background(Color(0xFF141414))
                .pointerInput(durationMs, ctx.settings.hapticsEnabled) {
                    var lastAngleRad = 0f
                    var workingFraction = baseProgress
                    var accumulatedForHaptic = 0f
                    detectDragGestures(
                        onDragStart = { start ->
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            lastAngleRad = atan2(start.y - cy, start.x - cx)
                            workingFraction = wheelScrubFraction ?: baseProgress
                            accumulatedForHaptic = 0f
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            val currentAngle = atan2(change.position.y - cy, change.position.x - cx)
                            var delta = currentAngle - lastAngleRad
                            if (delta > PI.toFloat()) delta -= (2f * PI.toFloat())
                            if (delta < -PI.toFloat()) delta += (2f * PI.toFloat())
                            lastAngleRad = currentAngle

                            workingFraction = (workingFraction + (delta / (2f * PI.toFloat())) * 0.35f).coerceIn(0f, 1f)
                            wheelScrubFraction = workingFraction
                            accumulatedForHaptic += abs(delta)
                            if (accumulatedForHaptic >= 0.45f) {
                                accumulatedForHaptic = 0f
                                if (ctx.settings.hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            wheelScrubFraction?.let(ctx.onSeekToFraction)
                            wheelScrubFraction = null
                        },
                        onDragCancel = {
                            wheelScrubFraction = null
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = ctx.onOpenStylePicker,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Text(
                    text = "MENU",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            IconButton(
                onClick = ctx.onSkipPrevious,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = ctx.onSkipNext,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = ctx.onTogglePlayPause,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Icon(
                    imageVector = if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF242424))
                    .clickable {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        ctx.onToggleInlineLyrics()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (ctx.showInlineLyrics) Icons.Rounded.Style else Icons.Rounded.Lyrics,
                    contentDescription = "Toggle lyrics",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
