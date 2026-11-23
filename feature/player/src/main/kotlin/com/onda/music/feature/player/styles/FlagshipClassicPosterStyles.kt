package com.onda.music.feature.player.styles

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import com.onda.music.core.design.GlassKit
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaTheme
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

@Immutable
private data class PosterAtmosphericColors(
    val upperRightGlow: Color,
    val diagonalMidTone: Color,
    val lowerLeftDeep: Color,
    val bottomEdgeShade: Color
)

private fun rgbToHsl(color: Color): FloatArray {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    var h = 0f
    val l = (maxC + minC) * 0.5f
    val s = if (delta < 0.0001f) {
        0f
    } else {
        delta / (1f - abs(2f * l - 1f)).coerceAtLeast(0.0001f)
    }
    if (delta >= 0.0001f) {
        h = when (maxC) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f
        if (h < 0f) h += 360f
    }
    return floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val safeH = ((h % 360f) + 360f) % 360f
    val safeS = s.coerceIn(0f, 1f)
    val safeL = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * safeL - 1f)) * safeS
    val x = c * (1f - abs(((safeH / 60f) % 2f) - 1f))
    val m = safeL - c * 0.5f
    val (r1, g1, b1) = when {
        safeH < 60f -> Triple(c, x, 0f)
        safeH < 120f -> Triple(x, c, 0f)
        safeH < 180f -> Triple(0f, c, x)
        safeH < 240f -> Triple(0f, x, c)
        safeH < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun extractPosterAtmosphericColors(palette: OndaArtworkPalette): PosterAtmosphericColors {
    val candidates = listOf(palette.tertiary, palette.muted, palette.primary, palette.secondary)
    val seed = candidates.maxByOrNull { candidate ->
        val hsl = rgbToHsl(candidate)
        val chromaScore = hsl[1] * 1.4f
        val midLightnessBonus = 1f - abs(hsl[2] - 0.42f)
        chromaScore + midLightnessBonus * 0.5f
    } ?: palette.tertiary

    val hsl = rgbToHsl(seed)
    val baseHue = if (hsl[1] < 0.05f) 218f else hsl[0]
    val glowSat = if (hsl[1] < 0.05f) 0.14f else (hsl[1] * 1.15f).coerceIn(0.36f, 0.76f)
    val glowLightness = hsl[2].coerceIn(0.28f, 0.36f)
    val midSat = if (hsl[1] < 0.05f) 0.12f else (glowSat * 0.88f).coerceIn(0.28f, 0.66f)
    val deepSat = if (hsl[1] < 0.05f) 0.08f else (glowSat * 0.72f).coerceIn(0.20f, 0.52f)

    return PosterAtmosphericColors(
        upperRightGlow = hslToColor(baseHue, glowSat, glowLightness),
        diagonalMidTone = hslToColor(baseHue, midSat, 0.17f),
        lowerLeftDeep = hslToColor(baseHue, deepSat, 0.065f),
        bottomEdgeShade = hslToColor(baseHue, deepSat * 0.8f, 0.032f)
    )
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

    val durationMs = state.durationMs.coerceAtLeast(0L)
    val positionMs = state.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    val playbackFraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val atmosphericColors = remember(palette) {
        extractPosterAtmosphericColors(palette)
    }

    val curvedArcShape = remember {
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.643f)
            cubicTo(
                w * 0.72f, h * 0.880f,
                w * 0.34f, h * 0.995f,
                0f, h * 0.964f
            )
            close()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val maxDim = maxOf(w, h)
                val diagonalBackdropBrush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to atmosphericColors.upperRightGlow.copy(alpha = 0.86f),
                        0.38f to atmosphericColors.diagonalMidTone.copy(alpha = 0.82f),
                        0.74f to atmosphericColors.lowerLeftDeep.copy(alpha = 0.94f),
                        1.00f to atmosphericColors.bottomEdgeShade
                    ),
                    start = Offset(w * 0.90f, h * 0.33f),
                    end = Offset(w * 0.06f, h * 0.98f)
                )
                val upperRightCurveGlowBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to atmosphericColors.upperRightGlow.copy(alpha = 0.85f),
                        0.46f to atmosphericColors.diagonalMidTone.copy(alpha = 0.48f),
                        1.00f to Color.Transparent
                    ),
                    center = Offset(w * 0.84f, h * 0.40f),
                    radius = maxDim * 0.62f
                )
                val backlitGlassAtmosphereBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to atmosphericColors.upperRightGlow.copy(alpha = 0.30f),
                        0.55f to atmosphericColors.diagonalMidTone.copy(alpha = 0.16f),
                        1.00f to Color.Transparent
                    ),
                    center = Offset(w * 0.52f, h * 0.66f),
                    radius = maxDim * 0.56f
                )
                val bottomReadabilityScrimBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1.00f to atmosphericColors.bottomEdgeShade.copy(alpha = 0.82f)
                    )
                )
                onDrawBehind {
                    drawRect(color = atmosphericColors.bottomEdgeShade)
                    drawRect(brush = diagonalBackdropBrush)
                    drawRect(brush = upperRightCurveGlowBrush)
                    drawRect(brush = backlitGlassAtmosphereBrush)
                    drawRect(brush = bottomReadabilityScrimBrush)
                }
            }
    ) {
        val artworkHeight = maxHeight * 0.56f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artworkHeight)
                .align(Alignment.TopCenter)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val fullArcPath = Path().apply {
                        moveTo(0f, h * 0.964f)
                        cubicTo(
                            w * 0.34f, h * 0.995f,
                            w * 0.72f, h * 0.880f,
                            w, h * 0.643f
                        )
                    }
                    val progressArcPath = Path()
                    val pathMeasure = PathMeasure().apply {
                        setPath(fullArcPath, false)
                    }
                    val arcLength = pathMeasure.length
                    val shadowOuterStroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
                    val shadowMidStroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    val trackStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    val glowOuterStroke = Stroke(width = 7.5.dp.toPx(), cap = StrokeCap.Round)
                    val glowInnerStroke = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)

                    onDrawWithContent {
                        drawPath(
                            path = fullArcPath,
                            color = Color.Black.copy(alpha = 0.16f),
                            style = shadowOuterStroke
                        )
                        drawPath(
                            path = fullArcPath,
                            color = Color.Black.copy(alpha = 0.22f),
                            style = shadowMidStroke
                        )

                        drawContent()

                        drawPath(
                            path = fullArcPath,
                            color = Color(0x2EFFFFFF),
                            style = trackStroke
                        )

                        val clampedProgress = playbackFraction.coerceIn(0f, 1f)
                        if (clampedProgress > 0.001f && arcLength > 0f) {
                            progressArcPath.reset()
                            pathMeasure.getSegment(
                                startDistance = 0f,
                                stopDistance = arcLength * clampedProgress,
                                destination = progressArcPath,
                                startWithMoveTo = true
                            )
                            drawPath(
                                path = progressArcPath,
                                color = Color(0x1FFFFFFF),
                                style = glowOuterStroke
                            )
                            drawPath(
                                path = progressArcPath,
                                color = Color(0x38FFFFFF),
                                style = glowInnerStroke
                            )
                            drawPath(
                                path = progressArcPath,
                                color = Color(0xB3FFFFFF),
                                style = trackStroke
                            )
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                    onArtworkLongClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        ctx.onOpenOverflowMenu()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = artworkHeight * 0.86f)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(0.76f)
                    .fillMaxHeight()
                    .padding(start = 20.dp, end = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = artworkHeight * 0.14f, bottom = 22.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    PosterKineticLyrics(
                        lyrics = ctx.lyrics,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        lyricsOffsetMs = ctx.lyricsOffsetMs,
                        isPlaying = isPlaying,
                        isLoading = ctx.isLyricsLoading,
                        expanded = ctx.showInlineLyrics,
                        lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                        hapticsEnabled = ctx.settings.hapticsEnabled,
                        onToggleExpanded = ctx.onToggleInlineLyrics,
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
                        .clickable {
                            if (ctx.settings.hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            ctx.onOpenOverflowMenu()
                        }
                        .padding(top = 8.dp, bottom = 2.dp),
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

            val rewindIcon = rememberPosterRewindIcon()
            val fastForwardIcon = rememberPosterFastForwardIcon()

            Column(
                modifier = Modifier
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PosterGlassActionPill(
                    icon = Icons.Rounded.Favorite,
                    contentDescription = if (ctx.liked) "Remove from liked" else "Like track",
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
                    icon = rewindIcon,
                    contentDescription = "Previous track",
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
                    onClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        ctx.onTogglePlayPause()
                    }
                )

                PosterGlassActionPill(
                    icon = fastForwardIcon,
                    contentDescription = "Next track",
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
private fun rememberPosterRewindIcon(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "PosterRoundedRewind",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11.0f, 16.07f)
                lineTo(11.0f, 7.93f)
                curveTo(11.0f, 7.12f, 10.08f, 6.65f, 9.42f, 7.11f)
                lineTo(3.65f, 11.18f)
                curveTo(3.09f, 11.58f, 3.09f, 12.42f, 3.65f, 12.82f)
                lineTo(9.42f, 16.89f)
                curveTo(10.08f, 17.35f, 11.0f, 16.88f, 11.0f, 16.07f)
                close()
                moveTo(20.0f, 16.07f)
                lineTo(20.0f, 7.93f)
                curveTo(20.0f, 7.12f, 19.08f, 6.65f, 18.42f, 7.11f)
                lineTo(12.65f, 11.18f)
                curveTo(12.09f, 11.58f, 12.09f, 12.42f, 12.65f, 12.82f)
                lineTo(18.42f, 16.89f)
                curveTo(19.08f, 17.35f, 20.0f, 16.88f, 20.0f, 16.07f)
                close()
            }
        }.build()
    }
}

@Composable
private fun rememberPosterFastForwardIcon(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "PosterRoundedFastForward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.0f, 16.07f)
                lineTo(4.0f, 7.93f)
                curveTo(4.0f, 7.12f, 4.92f, 6.65f, 5.58f, 7.11f)
                lineTo(11.35f, 11.18f)
                curveTo(11.91f, 11.58f, 11.91f, 12.42f, 11.35f, 12.82f)
                lineTo(5.58f, 16.89f)
                curveTo(4.92f, 17.35f, 4.0f, 16.88f, 4.0f, 16.07f)
                close()
                moveTo(13.0f, 16.07f)
                lineTo(13.0f, 7.93f)
                curveTo(13.0f, 7.12f, 13.92f, 6.65f, 14.58f, 7.11f)
                lineTo(20.35f, 11.18f)
                curveTo(20.91f, 11.58f, 20.91f, 12.42f, 20.35f, 12.82f)
                lineTo(14.58f, 16.89f)
                curveTo(13.92f, 17.35f, 13.0f, 16.88f, 13.0f, 16.07f)
                close()
            }
        }.build()
    }
}

@Composable
private fun PosterGlassActionPill(
    icon: ImageVector,
    contentDescription: String,
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
                .background(Color(0x26FFFFFF), pillShape)
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Immutable
private data class SpotlightWordSlice(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosterKineticLyrics(
    lyrics: LyricsDocument?,
    positionMs: Long,
    durationMs: Long,
    lyricsOffsetMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    expanded: Boolean,
    lyricsMotionEnabled: Boolean,
    hapticsEnabled: Boolean,
    onToggleExpanded: () -> Unit,
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

    val spotlightIndex = remember(lines, activeIndex, isSynced, positionMs, durationMs) {
        when {
            lines.isEmpty() -> -1
            isSynced && activeIndex >= 0 -> activeIndex
            isSynced -> 0
            durationMs > 0L -> {
                val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.999f)
                (fraction * lines.size).toInt().coerceIn(0, lines.lastIndex)
            }
            else -> 0
        }
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

    LaunchedEffect(activeIndex, userDetachedScroll, isSynced, expanded) {
        if (expanded && isSynced && activeIndex >= 0 && !userDetachedScroll) {
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
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
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
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color(0x73FFFFFF)
                )
            }
        }

        !expanded -> {
            val safeIndex = spotlightIndex.coerceIn(0, lines.lastIndex)
            val currentLine = lines[safeIndex]
            val nextLineStartMs = lines.getOrNull(safeIndex + 1)?.startTimeMs

            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onToggleExpanded()
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = safeIndex to currentLine,
                    transitionSpec = {
                        if (!motionActive) {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                        } else {
                            (fadeIn(tween(240)) + slideInVertically(
                                animationSpec = AppMotion.SmoothSpring
                            ) { fullHeight -> fullHeight / 3 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(
                                    animationSpec = AppMotion.SmoothSpring
                                ) { fullHeight -> -fullHeight / 3 })
                        }
                    },
                    label = "PosterSpotlightStanza"
                ) { (_, stanzaLine) ->
                    PosterSpotlightStanza(
                        line = stanzaLine,
                        nextLineStartMs = nextLineStartMs,
                        isSynced = isSynced,
                        hasWordTiming = hasWordTiming,
                        effectivePositionMs = effectivePositionMs,
                        isPlaying = isPlaying,
                        motionActive = motionActive
                    )
                }
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (isSynced) {
                                userDetachedScroll = false
                                onSeekToMs((line.startTimeMs - lyricsOffsetMs).coerceAtLeast(0L))
                            }
                            onToggleExpanded()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosterSpotlightStanza(
    line: LyricsLine,
    nextLineStartMs: Long?,
    isSynced: Boolean,
    hasWordTiming: Boolean,
    effectivePositionMs: Long,
    isPlaying: Boolean,
    motionActive: Boolean
) {
    val spotlightStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp
    )

    if (line.isInterlude) {
        val interludeEnd = when {
            line.endTimeMs > line.startTimeMs -> line.endTimeMs
            nextLineStartMs != null && nextLineStartMs > line.startTimeMs -> nextLineStartMs
            else -> line.startTimeMs + 3_000L
        }
        val interludeDuration = (interludeEnd - line.startTimeMs).coerceAtLeast(1L)
        val interludeProgress = ((effectivePositionMs - line.startTimeMs).toFloat() / interludeDuration).coerceIn(0f, 1f)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            LyricsInterludeDots(
                isActive = true,
                isPlaying = isPlaying,
                motionActive = motionActive,
                progress = interludeProgress,
                accentColor = Color.White
            )
        }
        return
    }

    val wordSlices = remember(line, nextLineStartMs, hasWordTiming) {
        if (hasWordTiming && line.words.isNotEmpty()) {
            line.words.map { w ->
                SpotlightWordSlice(
                    text = w.text,
                    startTimeMs = w.startTimeMs,
                    endTimeMs = w.endTimeMs.coerceAtLeast(w.startTimeMs + 80L)
                )
            }
        } else {
            val rawTokens = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val boundedTokens = if (rawTokens.size > 16) {
                rawTokens.take(15) + listOf("${rawTokens[15]}...")
            } else {
                rawTokens
            }
            val startMs = line.startTimeMs
            val resolvedEndMs = when {
                line.endTimeMs > startMs + 250L -> line.endTimeMs
                nextLineStartMs != null && nextLineStartMs > startMs + 250L -> nextLineStartMs
                else -> startMs + 3_200L
            }
            val spanMs = (resolvedEndMs - startMs).coerceIn(600L, 9_000L)
            val totalChars = boundedTokens.sumOf { it.length.coerceAtLeast(2) }.coerceAtLeast(1)
            var cursorMs = startMs
            boundedTokens.mapIndexed { idx, token ->
                val weight = token.length.coerceAtLeast(2).toFloat() / totalChars.toFloat()
                val sliceDuration = (spanMs * weight).toLong().coerceAtLeast(90L)
                val sliceStart = cursorMs
                val sliceEnd = if (idx == boundedTokens.lastIndex) {
                    max(sliceStart + 90L, startMs + spanMs)
                } else {
                    sliceStart + sliceDuration
                }
                cursorMs = sliceEnd
                SpotlightWordSlice(
                    text = token,
                    startTimeMs = sliceStart,
                    endTimeMs = sliceEnd
                )
            }
        }
    }

    val activeWordIndex = remember(wordSlices, effectivePositionMs, isSynced) {
        if (!isSynced || wordSlices.isEmpty()) {
            -1
        } else {
            val exact = wordSlices.indexOfFirst { slice ->
                effectivePositionMs in slice.startTimeMs..slice.endTimeMs
            }
            when {
                exact >= 0 -> exact
                effectivePositionMs < wordSlices.first().startTimeMs -> 0
                else -> wordSlices.indexOfLast { effectivePositionMs >= it.startTimeMs }.coerceAtLeast(0)
            }
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        wordSlices.forEachIndexed { index, slice ->
            val isSpokenOrCurrent = !isSynced || index <= activeWordIndex || effectivePositionMs >= slice.startTimeMs
            val isLeadActiveWord = isSynced && index == activeWordIndex
            val distanceAhead = (index - activeWordIndex).coerceAtLeast(0)

            val wordScale by animateFloatAsState(
                targetValue = if (isLeadActiveWord && motionActive) 1.12f else 1f,
                animationSpec = AppMotion.SnappySpring,
                label = "SpotlightWordScale"
            )
            val wordAlpha by animateFloatAsState(
                targetValue = when {
                    isSpokenOrCurrent -> 1f
                    distanceAhead == 1 -> 0.54f
                    else -> 0.45f
                },
                animationSpec = tween(durationMillis = 180),
                label = "SpotlightWordAlpha"
            )

            Text(
                text = slice.text,
                style = spotlightStyle,
                color = if (isSpokenOrCurrent) Color.White else Color(0x73FFFFFF),
                modifier = Modifier
                    .padding(end = if (isLeadActiveWord && motionActive) 3.dp else 0.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.55f)
                        scaleX = wordScale
                        scaleY = wordScale
                        alpha = if (isSpokenOrCurrent) 1f else (wordAlpha / 0.45f).coerceIn(0.75f, 1f)
                        if (!isSpokenOrCurrent && motionActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = GlassKit.createBlurRenderEffect(1.1.dp.toPx())
                        } else {
                            renderEffect = null
                        }
                    }
            )
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
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
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
                        targetValue = if (isCurrent && motionActive) 1.12f else 1f,
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
