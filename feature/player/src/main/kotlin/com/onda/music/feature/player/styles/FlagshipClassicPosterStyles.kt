package com.onda.music.feature.player.styles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ProgressiveGlassScrim
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.feature.player.DownloadActionButton
import com.onda.music.feature.player.GlassPillButton
import com.onda.music.feature.player.LikeActionButton
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.SongCarousel
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.TrackMetaHeader

@Composable
fun FlagshipPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val palette = OndaTheme.palette
    val state = ctx.playbackState
    val track = state.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = ctx.showInlineLyrics,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "FlagshipHeroToggle"
            ) { showingLyrics ->
                if (showingLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .smokedGlass(
                                shape = RoundedCornerShape(GlassTokens.RadiusXl),
                                elevation = GlassElevation.CARD,
                                accentTint = palette.primary
                            )
                            .padding(vertical = 8.dp)
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
                            onAdjustOffsetMs = ctx.onAdjustLyricsOffset
                        )
                    }
                } else {
                    SongCarousel(
                        queue = state.queue,
                        currentIndex = state.currentIndex,
                        currentTrack = track,
                        onSelectIndex = ctx.onSelectQueueIndex,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 14.dp,
                        enablePress3D = true,
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .smokedGlass(
                    shape = RoundedCornerShape(GlassTokens.RadiusXl),
                    elevation = GlassElevation.HERO,
                    accentTint = palette.primary
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                onSeekToFraction = ctx.onSeekToFraction
            )

            PlayerTransportControls(
                playbackState = state,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleShuffle = ctx.onToggleShuffle,
                onSkipPrevious = ctx.onSkipPrevious,
                onTogglePlayPause = ctx.onTogglePlayPause,
                onSkipNext = ctx.onSkipNext,
                onToggleRepeat = ctx.onToggleRepeat
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                GlassPillButton(
                    label = if (ctx.showInlineLyrics) "Artwork" else "Lyrics",
                    active = ctx.showInlineLyrics,
                    onClick = ctx.onToggleInlineLyrics,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = if (ctx.showInlineLyrics) palette.primary else GlassTokens.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                GlassPillButton(
                    label = "Queue (${state.queue.size})",
                    onClick = ctx.onOpenQueue,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = GlassTokens.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ClassicPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val palette = OndaTheme.palette
    val state = ctx.playbackState
    val track = state.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
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
                    onAdjustOffsetMs = ctx.onAdjustLyricsOffset
                )
            } else {
                SongCarousel(
                    queue = state.queue,
                    currentIndex = state.currentIndex,
                    currentTrack = track,
                    onSelectIndex = ctx.onSelectQueueIndex,
                    artworkShape = RoundedCornerShape(GlassTokens.RadiusLg),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    pageSpacing = 12.dp,
                    enablePress3D = false,
                    modifier = Modifier
                        .widthIn(max = 336.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                onSeekToFraction = ctx.onSeekToFraction
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .smokedGlass(
                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                        elevation = GlassElevation.CARD,
                        accentTint = palette.secondary
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPillButton(
                    label = if (ctx.showInlineLyrics) "Cover" else "Lyrics",
                    active = ctx.showInlineLyrics,
                    onClick = ctx.onToggleInlineLyrics
                )
                GlassPillButton(
                    label = "Queue",
                    onClick = ctx.onOpenQueue
                )
            }
        }
    }
}

@Composable
fun PosterPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val palette = OndaTheme.palette
    val state = ctx.playbackState
    val track = state.currentTrack

    Box(modifier = modifier.fillMaxSize()) {
        if (ctx.showInlineLyrics) {
            TrackArtworkImage(
                artworkUrl = track?.artworkUrl,
                contentDescription = track?.title,
                shape = RectangleShape,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlassTokens.AmoledBlack.copy(alpha = 0.72f))
            )
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 250.dp)
            )
        } else {
            SongCarousel(
                queue = state.queue,
                currentIndex = state.currentIndex,
                currentTrack = track,
                onSelectIndex = ctx.onSelectQueueIndex,
                artworkShape = RectangleShape,
                contentPadding = PaddingValues(0.dp),
                pageSpacing = 0.dp,
                enablePress3D = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        ProgressiveGlassScrim(
            topToBottom = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.32f to GlassTokens.AmoledBlack.copy(alpha = 0.65f),
                            1.0f to GlassTokens.AmoledBlack.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .smokedGlass(
                    shape = RoundedCornerShape(GlassTokens.RadiusXl),
                    elevation = GlassElevation.SHEET,
                    accentTint = palette.primary
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = GlassTokens.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DownloadActionButton(
                    downloadItem = ctx.downloadItem,
                    onClick = ctx.onDownloadClick
                )
                LikeActionButton(
                    liked = ctx.liked,
                    hapticsEnabled = ctx.settings.hapticsEnabled,
                    onToggleLike = ctx.onToggleLike,
                    onButtonPositioned = ctx.onLikePositioned
                )
            }

            PlayerSeekSection(
                playbackState = state,
                onSeekToFraction = ctx.onSeekToFraction
            )

            PlayerTransportControls(
                playbackState = state,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleShuffle = ctx.onToggleShuffle,
                onSkipPrevious = ctx.onSkipPrevious,
                onTogglePlayPause = ctx.onTogglePlayPause,
                onSkipNext = ctx.onSkipNext,
                onToggleRepeat = ctx.onToggleRepeat
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                GlassPillButton(
                    label = if (ctx.showInlineLyrics) "Poster" else "Lyrics",
                    active = ctx.showInlineLyrics,
                    onClick = ctx.onToggleInlineLyrics,
                    modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                )
                GlassPillButton(
                    label = "Up Next",
                    onClick = ctx.onOpenQueue,
                    modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                )
            }
        }
    }
}
