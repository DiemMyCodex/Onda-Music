package com.onda.music.feature.player.styles

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ondaBackdropBlur
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.feature.player.GlassPillButton
import com.onda.music.feature.player.LyricsInterludeDots
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.SongCarousel
import com.onda.music.feature.player.TrackMetaHeader
import com.onda.music.feature.player.findActiveLyricsLineIndex
import kotlinx.coroutines.delay

@Composable
fun FlagshipPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
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
                            .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                            .background(Color(0xFF141414))
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
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
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
                            tint = if (ctx.showInlineLyrics) Color.White else Color(0xFF9E9E9E),
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
                            tint = Color(0xFF9E9E9E),
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
                onSeekToFraction = ctx.onSeekToFraction,
                accentColor = Color.White
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(Color(0xFF141414))
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

private fun clampDarkForLyricsContrast(
    color: Color,
    maxLuminance: Double = 0.042
): Color {
    if (GlassTokens.relativeLuminance(color) <= maxLuminance) {
        return color.copy(alpha = 1f)
    }
    var low = 0f
    var high = 1f
    var best = Color.Black
    repeat(12) {
        val mid = (low + high) * 0.5f
        val candidate = Color(
            red = color.red * mid,
            green = color.green * mid,
            blue = color.blue * mid,
            alpha = 1f
        )
        if (GlassTokens.relativeLuminance(candidate) <= maxLuminance) {
            best = candidate
            low = mid
        } else {
            high = mid
        }
    }
    return best
}

@Composable
fun PosterPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val palette = OndaTheme.palette
    val state = ctx.playbackState
    val track = state.currentTrack
    val haptics = LocalHapticFeedback.current
    val isPlaying = state.status == PlaybackStatus.PLAYING

    val darkTertiary = remember(palette.tertiary) {
        clampDarkForLyricsContrast(palette.tertiary, maxLuminance = 0.042)
    }
    val darkDeepShadow = remember(palette.deepShadow) {
        clampDarkForLyricsContrast(palette.deepShadow, maxLuminance = 0.028)
    }
    val darkAccent = remember(palette.primary) {
        clampDarkForLyricsContrast(palette.primary, maxLuminance = 0.055)
    }

    val curvedArcShape = remember {
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.76f)
            cubicTo(
                w * 0.66f, h * 0.77f,
                w * 0.36f, h * 0.98f,
                0f, h * 0.96f
            )
            close()
        }
    }

    val arcShadowPath = remember { Path() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = Color(0xFF000000))
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFF000000),
                            0.36f to darkTertiary,
                            0.64f to darkDeepShadow,
                            1.00f to Color(0xFF000000)
                        )
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            darkAccent.copy(alpha = 0.75f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.28f, size.height * 0.56f),
                        radius = maxOf(size.width, size.height) * 0.68f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            darkTertiary.copy(alpha = 0.50f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.80f, size.height * 0.74f),
                        radius = maxOf(size.width, size.height) * 0.60f
                    )
                )
            }
    ) {
        val artworkHeight = maxHeight * 0.52f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artworkHeight + 18.dp)
                .align(Alignment.TopCenter)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artworkHeight)
            ) {
                val w = size.width
                val h = size.height
                arcShadowPath.reset()
                arcShadowPath.moveTo(0f, h * 0.96f)
                arcShadowPath.cubicTo(
                    w * 0.36f, h * 0.98f,
                    w * 0.66f, h * 0.77f,
                    w, h * 0.76f
                )
                drawPath(
                    path = arcShadowPath,
                    color = Color.Black.copy(alpha = 0.14f),
                    style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = arcShadowPath,
                    color = Color.Black.copy(alpha = 0.18f),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = arcShadowPath,
                    color = Color.Black.copy(alpha = 0.24f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artworkHeight)
                    .clip(curvedArcShape)
            ) {
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x52000000),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = artworkHeight * 0.95f)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(0.76f)
                    .fillMaxHeight()
                    .padding(start = 20.dp, end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    PosterKineticLyrics(
                        lyrics = ctx.lyrics,
                        positionMs = state.positionMs,
                        lyricsOffsetMs = ctx.lyricsOffsetMs,
                        isPlaying = isPlaying,
                        isLoading = ctx.isLyricsLoading,
                        lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                        hapticsEnabled = ctx.settings.hapticsEnabled,
                        onSeekToMs = ctx.onSeekToMs,
                        onRefreshLyrics = ctx.onRefreshLyrics,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = ctx.onOpenQueue)
                        .padding(top = 10.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = track?.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PosterGlassActionPill(
                    icon = if (ctx.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (ctx.liked) "Remove from liked" else "Like track",
                    glassBlurEnabled = ctx.settings.glassBlurEnabled,
                    onClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        ctx.onToggleLike()
                    },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        ctx.onLikePositioned(
                            Offset(
                                x = pos.x + coords.size.width * 0.5f,
                                y = pos.y + coords.size.height * 0.5f
                            )
                        )
                    }
                )

                PosterGlassActionPill(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous track",
                    glassBlurEnabled = ctx.settings.glassBlurEnabled,
                    onClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        ctx.onSkipPrevious()
                    }
                )

                PosterGlassActionPill(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    glassBlurEnabled = ctx.settings.glassBlurEnabled,
                    onClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        ctx.onTogglePlayPause()
                    }
                )

                PosterGlassActionPill(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next track",
                    glassBlurEnabled = ctx.settings.glassBlurEnabled,
                    onClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        ctx.onSkipNext()
                    }
                )
            }
        }
    }
}

@Composable
private fun PosterGlassActionPill(
    icon: ImageVector,
    contentDescription: String,
    glassBlurEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = AppMotion.SnappySpring,
        label = "PosterPillScale"
    )
    val pillShape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 58.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(42.dp)
                .clip(pillShape)
                .background(Color(0x2EFFFFFF), pillShape)
                .then(
                    if (glassBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.ondaBackdropBlur(radius = 12.dp)
                    } else {
                        Modifier
                    }
                )
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosterKineticLyrics(
    lyrics: LyricsDocument?,
    positionMs: Long,
    lyricsOffsetMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    lyricsMotionEnabled: Boolean,
    hapticsEnabled: Boolean,
    onSeekToMs: (Long) -> Unit,
    onRefreshLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val motionActive = lyricsMotionEnabled && !reducedMotion
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

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

    LaunchedEffect(userDetachedScroll) {
        if (userDetachedScroll) {
            delay(3_500L)
            userDetachedScroll = false
        }
    }

    LaunchedEffect(activeIndex, userDetachedScroll, isSynced) {
        if (isSynced && activeIndex >= 0 && !userDetachedScroll) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -32
            )
        }
    }

    when {
        isLoading && lines.isEmpty() -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Loading lyrics...",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color(0x73FFFFFF)
                )
            }
        }

        lines.isEmpty() -> {
            Box(
                modifier = modifier
                    .clickable(onClick = onRefreshLyrics),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Instrumental / No lyrics",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color(0x73FFFFFF)
                )
            }
        }

        else -> {
            LazyColumn(
                state = listState,
                modifier = modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.10f to Color.Black,
                                    0.86f to Color.Black,
                                    1.00f to Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${index}_${line.startTimeMs}" }
                ) { index, line ->
                    val isActive = isSynced && index == activeIndex
                    PosterLyricRow(
                        line = line,
                        isActive = isActive,
                        isSynced = isSynced,
                        hasWordTiming = hasWordTiming,
                        effectivePositionMs = effectivePositionMs,
                        isPlaying = isPlaying,
                        motionActive = motionActive,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosterLyricRow(
    line: LyricsLine,
    isActive: Boolean,
    isSynced: Boolean,
    hasWordTiming: Boolean,
    effectivePositionMs: Long,
    isPlaying: Boolean,
    motionActive: Boolean,
    onClick: () -> Unit
) {
    val activeScale by animateFloatAsState(
        targetValue = if (isActive && motionActive) 1.03f else 1f,
        animationSpec = AppMotion.adaptiveSpring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            reducedMotion = !motionActive
        ),
        label = "PosterLyricScale"
    )

    val textStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 27.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isSynced, onClick = onClick)
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
            }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (line.isInterlude) {
            val interludeDuration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(1L)
            val interludeProgress = ((effectivePositionMs - line.startTimeMs).toFloat() / interludeDuration).coerceIn(0f, 1f)
            LyricsInterludeDots(
                isActive = isActive,
                isPlaying = isPlaying,
                motionActive = motionActive,
                progress = interludeProgress,
                accentColor = Color.White
            )
        } else if (hasWordTiming && line.words.isNotEmpty() && isActive) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                line.words.forEach { word ->
                    val reached = effectivePositionMs >= word.startTimeMs
                    val isCurrent = reached && effectivePositionMs <= word.endTimeMs
                    val wordScale by animateFloatAsState(
                        targetValue = if (isCurrent && motionActive) 1.05f else 1f,
                        animationSpec = AppMotion.SnappySpring,
                        label = "PosterWordScale"
                    )
                    Text(
                        text = word.text,
                        style = textStyle,
                        color = if (reached) Color.White else Color(0x73FFFFFF),
                        modifier = Modifier.graphicsLayer {
                            scaleX = wordScale
                            scaleY = wordScale
                        }
                    )
                }
            }
        } else {
            Text(
                text = line.text,
                style = textStyle,
                color = if (isActive || !isSynced) Color.White else Color(0x73FFFFFF),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
