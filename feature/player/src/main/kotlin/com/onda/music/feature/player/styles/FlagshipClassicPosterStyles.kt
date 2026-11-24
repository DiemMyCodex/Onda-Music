package com.onda.music.feature.player.styles

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.StudioQuickAction
import com.onda.music.feature.player.GlassPillButton
import com.onda.music.feature.player.LyricsInterludeDots
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.OndaLikeSlot
import com.onda.music.feature.player.PlayerLineBlurCache
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.SongCarousel
import com.onda.music.feature.player.TrackMetaHeader
import com.onda.music.feature.player.findActiveLyricsLineIndex
import com.onda.music.feature.player.formatDurationMs
import com.onda.music.feature.player.ondaLikeTarget
import com.onda.music.feature.player.playerPressBounce
import com.onda.music.feature.player.queue.OndaBottomDeck
import com.onda.music.feature.player.rememberSongCarouselPagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun FlagshipPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val state = ctx.playbackState
    val track = state.currentTrack
    val studio = ctx.settings.studioConfig
    val isPlaying = state.status == PlaybackStatus.PLAYING
    val pagerState = rememberSongCarouselPagerState(
        queue = state.queue,
        currentIndex = state.currentIndex,
        currentTrack = track
    )
    val carouselTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val activeLyricText = remember(ctx.lyrics, state.positionMs, ctx.lyricsOffsetMs) {
        resolveActiveLyricText(ctx.lyrics, state.positionMs, ctx.lyricsOffsetMs)
    }
    val nextTrack = remember(state.queue, state.currentIndex) {
        state.queue.getOrNull(state.currentIndex + 1)
    }
    val upNextCount = remember(state.queue.size, state.currentIndex) {
        if (state.currentIndex >= 0) {
            (state.queue.size - (state.currentIndex + 1)).coerceAtLeast(0)
        } else {
            state.queue.size
        }
    }

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
                            isPlaying = isPlaying,
                            isLoading = ctx.isLyricsLoading,
                            errorMessage = ctx.lyricsError,
                            lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                            lyricsFontScale = ctx.settings.lyricsFontScale,
                            hapticsEnabled = ctx.settings.hapticsEnabled,
                            onSeekToMs = ctx.onSeekToMs,
                            onRefreshLyrics = ctx.onRefreshLyrics,
                            onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                            onLongPressLine = { lineIdx -> ctx.onOpenShare(lineIdx) }
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
                        isPlaying = isPlaying,
                        pagerState = pagerState,
                        onArtworkDoubleTap = ctx.onLikeFromDoubleTap,
                        artworkFrame = studio.artworkFrame,
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (studio.controlsShiftDp * 0.5f).dp.toPx()
                }
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackMetaHeader(
                track = track,
                liked = ctx.liked,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleLike = ctx.onLikeFromButton ?: ctx.onToggleLike,
                onLikePositioned = ctx.onLikePositioned,
                downloadItem = ctx.downloadItem,
                onDownloadClick = ctx.onDownloadClick,
                queue = carouselTracks,
                pagerState = if (ctx.showInlineLyrics) null else pagerState,
                likeBurstState = ctx.likeBurstState,
                likeSlot = OndaLikeSlot.PLAYER
            )

            PlayerSeekSection(
                playbackState = state,
                onSeekToFraction = ctx.onSeekToFraction,
                accentColor = Color.White,
                seekbarStyle = studio.seekbarStyle
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

            OndaBottomDeck(
                mode = studio.bottomDeckMode,
                quickActions = studio.quickActions,
                currentTrack = track,
                nextTrack = nextTrack,
                upNextCount = upNextCount,
                activeLyricText = activeLyricText,
                liked = ctx.liked,
                downloadItem = ctx.downloadItem,
                playbackSpeed = state.playbackSpeed,
                sleepTimerActive = state.sleepTimerRemainingMs != null || state.sleepTimerEndOfTrack,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onOpenQueue = ctx.onOpenQueue,
                onToggleLyrics = ctx.onToggleInlineLyrics,
                onQuickAction = { action -> dispatchQuickAction(ctx, action) }
            )
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
    val studio = ctx.settings.studioConfig
    val isPlaying = state.status == PlaybackStatus.PLAYING
    val pagerState = rememberSongCarouselPagerState(
        queue = state.queue,
        currentIndex = state.currentIndex,
        currentTrack = track
    )
    val carouselTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val activeLyricText = remember(ctx.lyrics, state.positionMs, ctx.lyricsOffsetMs) {
        resolveActiveLyricText(ctx.lyrics, state.positionMs, ctx.lyricsOffsetMs)
    }
    val nextTrack = remember(state.queue, state.currentIndex) {
        state.queue.getOrNull(state.currentIndex + 1)
    }
    val upNextCount = remember(state.queue.size, state.currentIndex) {
        if (state.currentIndex >= 0) {
            (state.queue.size - (state.currentIndex + 1)).coerceAtLeast(0)
        } else {
            state.queue.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
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
                    isPlaying = isPlaying,
                    isLoading = ctx.isLyricsLoading,
                    errorMessage = ctx.lyricsError,
                    lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                    lyricsFontScale = ctx.settings.lyricsFontScale,
                    hapticsEnabled = ctx.settings.hapticsEnabled,
                    onSeekToMs = ctx.onSeekToMs,
                    onRefreshLyrics = ctx.onRefreshLyrics,
                    onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                    onLongPressLine = { lineIdx -> ctx.onOpenShare(lineIdx) }
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
                    isPlaying = isPlaying,
                    pagerState = pagerState,
                    onArtworkDoubleTap = ctx.onLikeFromDoubleTap,
                    artworkFrame = studio.artworkFrame,
                    modifier = Modifier
                        .widthIn(max = 336.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (studio.controlsShiftDp * 0.5f).dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrackMetaHeader(
                track = track,
                liked = ctx.liked,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleLike = ctx.onLikeFromButton ?: ctx.onToggleLike,
                onLikePositioned = ctx.onLikePositioned,
                downloadItem = ctx.downloadItem,
                onDownloadClick = ctx.onDownloadClick,
                queue = carouselTracks,
                pagerState = if (ctx.showInlineLyrics) null else pagerState,
                likeBurstState = ctx.likeBurstState,
                likeSlot = OndaLikeSlot.PLAYER
            )

            PlayerSeekSection(
                playbackState = state,
                onSeekToFraction = ctx.onSeekToFraction,
                accentColor = Color.White,
                seekbarStyle = studio.seekbarStyle
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

            OndaBottomDeck(
                mode = studio.bottomDeckMode,
                quickActions = studio.quickActions,
                currentTrack = track,
                nextTrack = nextTrack,
                upNextCount = upNextCount,
                activeLyricText = activeLyricText,
                liked = ctx.liked,
                downloadItem = ctx.downloadItem,
                playbackSpeed = state.playbackSpeed,
                sleepTimerActive = state.sleepTimerRemainingMs != null || state.sleepTimerEndOfTrack,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onOpenQueue = ctx.onOpenQueue,
                onToggleLyrics = ctx.onToggleInlineLyrics,
                onQuickAction = { action -> dispatchQuickAction(ctx, action) }
            )
        }
    }
}

private fun dispatchQuickAction(ctx: PlayerStageContext, action: StudioQuickAction) {
    when (action) {
        StudioQuickAction.LIKE -> (ctx.onLikeFromButton ?: ctx.onToggleLike).invoke()
        StudioQuickAction.LYRICS -> ctx.onToggleInlineLyrics()
        StudioQuickAction.QUEUE -> ctx.onOpenQueue()
        StudioQuickAction.SHARE -> ctx.onOpenShare(null)
        StudioQuickAction.SPEED -> ctx.onOpenSpeedSheet()
        StudioQuickAction.STREAM_INFO -> ctx.onOpenStreamInfo()
        StudioQuickAction.LYRICS_OFFSET -> ctx.onOpenLyricsOffset()
        StudioQuickAction.SLEEP_TIMER -> ctx.onOpenSleepTimer()
        StudioQuickAction.EQUALIZER -> ctx.onOpenStreamInfo()
        StudioQuickAction.DOWNLOAD -> ctx.onDownloadClick()
    }
}

private fun resolveActiveLyricText(
    lyrics: LyricsDocument?,
    positionMs: Long,
    offsetMs: Long
): String? {
    val lines = lyrics?.lines ?: return null
    if (lines.isEmpty()) return null
    val effectiveMs = (positionMs + offsetMs).coerceAtLeast(0L)
    val idx = findActiveLyricsLineIndex(lines, effectiveMs, lyrics.isSynced)
    return lines.getOrNull(idx)?.takeIf { !it.isInterlude && it.text.isNotBlank() }?.text
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

internal data class PosterArcHit(
    val fraction: Float,
    val distancePx: Float,
    val tangent: Offset
)

internal fun evaluatePosterArcPoint(t: Float, w: Float, h: Float): Offset {
    val u = 1f - t
    val p0 = Offset(0f, h * 0.964f)
    val p1 = Offset(w * 0.34f, h * 0.995f)
    val p2 = Offset(w * 0.72f, h * 0.880f)
    val p3 = Offset(w, h * 0.643f)
    val uu = u * u
    val tt = t * t
    return Offset(
        x = uu * u * p0.x + 3f * uu * t * p1.x + 3f * u * tt * p2.x + tt * t * p3.x,
        y = uu * u * p0.y + 3f * uu * t * p1.y + 3f * u * tt * p2.y + tt * t * p3.y
    )
}

internal fun evaluatePosterArcTangent(t: Float, w: Float, h: Float): Offset {
    val u = 1f - t
    val p0 = Offset(0f, h * 0.964f)
    val p1 = Offset(w * 0.34f, h * 0.995f)
    val p2 = Offset(w * 0.72f, h * 0.880f)
    val p3 = Offset(w, h * 0.643f)
    val dx = 3f * u * u * (p1.x - p0.x) + 6f * u * t * (p2.x - p1.x) + 3f * t * t * (p3.x - p2.x)
    val dy = 3f * u * u * (p1.y - p0.y) + 6f * u * t * (p2.y - p1.y) + 3f * t * t * (p3.y - p2.y)
    val len = hypot(dx, dy).coerceAtLeast(1f)
    return Offset(dx / len, dy / len)
}

internal fun closestPosterArcHit(touch: Offset, w: Float, h: Float): PosterArcHit {
    var bestT = 0f
    var bestDist = Float.MAX_VALUE
    val steps = 36
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val pt = evaluatePosterArcPoint(t, w, h)
        val dist = hypot(touch.x - pt.x, touch.y - pt.y)
        if (dist < bestDist) {
            bestDist = dist
            bestT = t
        }
    }
    val stepHalf = 1f / steps
    val low = (bestT - stepHalf).coerceAtLeast(0f)
    val high = (bestT + stepHalf).coerceAtMost(1f)
    for (i in 0..8) {
        val t = lerp(low, high, i / 8f)
        val pt = evaluatePosterArcPoint(t, w, h)
        val dist = hypot(touch.x - pt.x, touch.y - pt.y)
        if (dist < bestDist) {
            bestDist = dist
            bestT = t
        }
    }
    return PosterArcHit(
        fraction = bestT.coerceIn(0f, 1f),
        distancePx = bestDist,
        tangent = evaluatePosterArcTangent(bestT, w, h)
    )
}

private val PosterExpandSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 340f)
private val PosterCollapseSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 170f)

@Stable
private class PosterArcRingState(private val scope: CoroutineScope) {
    val expand = Animatable(0f)
    var scrubMs by mutableLongStateOf(-1L)
    var held by mutableStateOf(false)
    private var job: Job? = null

    fun peek(forMs: Long, reducedMotion: Boolean) {
        if (reducedMotion) return
        job?.cancel()
        job = scope.launch {
            expand.animateTo(1f, PosterExpandSpring)
            delay(forMs)
            if (!held) {
                expand.animateTo(0f, PosterCollapseSpring)
            }
        }
    }

    fun grab(reducedMotion: Boolean) {
        job?.cancel()
        held = true
        job = scope.launch {
            if (reducedMotion) expand.snapTo(1f) else expand.animateTo(1f, PosterExpandSpring)
        }
    }

    fun letGo(afterMs: Long, reducedMotion: Boolean) {
        held = false
        job?.cancel()
        job = scope.launch {
            if (!reducedMotion) {
                delay(afterMs)
                if (!held) expand.animateTo(0f, PosterCollapseSpring)
            } else {
                expand.snapTo(0f)
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
    val reducedMotion = OndaTheme.reducedMotion
    val state = ctx.playbackState
    val track = state.currentTrack
    val haptics = LocalHapticFeedback.current
    val isPlaying = state.status == PlaybackStatus.PLAYING
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val pagerState = rememberSongCarouselPagerState(
        queue = state.queue,
        currentIndex = state.currentIndex,
        currentTrack = track
    )
    val carouselTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }

    val ringState = remember(scope) { PosterArcRingState(scope) }
    LaunchedEffect(isPlaying, track?.id, reducedMotion) {
        if (isPlaying && track != null && !reducedMotion && !ringState.held) {
            ringState.peek(3_000L, reducedMotion = false)
        }
    }

    val durationMs = state.durationMs.coerceAtLeast(0L)
    val positionMs = state.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    val playbackFraction = if (ringState.scrubMs >= 0L && durationMs > 0L) {
        (ringState.scrubMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else if (durationMs > 0L) {
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

    val measurer = rememberTextMeasurer(cacheSize = 16)
    val arcTimeStyle = remember {
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Color.White
        )
    }

    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentOnSeekToFraction by rememberUpdatedState(ctx.onSeekToFraction)
    val currentHaptics by rememberUpdatedState(ctx.settings.hapticsEnabled)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)

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
        val artworkHeightPx = with(density) { artworkHeight.toPx() }
        val currentArtworkHeightPx by rememberUpdatedState(artworkHeightPx)
        val activePlaybackScale by animateFloatAsState(
            targetValue = if (isPlaying && !reducedMotion) 1.03f else 1f,
            animationSpec = if (reducedMotion) snap() else tween(400),
            label = "PosterBleedScale"
        )

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

                        val expand = ringState.expand.value.coerceIn(0f, 1.3f)
                        val strokeWidthPx = lerp(2.5f, 6.5f, expand.coerceIn(0f, 1f)).dp.toPx()
                        val trackStroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        val glowOuterStroke = Stroke(width = strokeWidthPx * 2.8f, cap = StrokeCap.Round)
                        val glowInnerStroke = Stroke(width = strokeWidthPx * 1.7f, cap = StrokeCap.Round)

                        drawPath(
                            path = fullArcPath,
                            color = Color.White.copy(alpha = lerp(0.18f, 0.36f, expand.coerceIn(0f, 1f))),
                            style = trackStroke
                        )

                        val clampedProgress = playbackFraction.coerceIn(0f, 1f)
                        if (clampedProgress > 0.001f && arcLength > 0f) {
                            val stopDistance = arcLength * clampedProgress
                            progressArcPath.reset()
                            pathMeasure.getSegment(
                                startDistance = 0f,
                                stopDistance = stopDistance,
                                destination = progressArcPath,
                                startWithMoveTo = true
                            )
                            drawPath(
                                path = progressArcPath,
                                color = Color.White.copy(alpha = lerp(0.12f, 0.24f, expand.coerceIn(0f, 1f))),
                                style = glowOuterStroke
                            )
                            drawPath(
                                path = progressArcPath,
                                color = Color.White.copy(alpha = lerp(0.22f, 0.42f, expand.coerceIn(0f, 1f))),
                                style = glowInnerStroke
                            )
                            drawPath(
                                path = progressArcPath,
                                color = Color.White.copy(alpha = lerp(0.70f, 0.96f, expand.coerceIn(0f, 1f))),
                                style = trackStroke
                            )
                            if (expand > 0.05f) {
                                val tip = pathMeasure.getPosition(stopDistance)
                                drawCircle(
                                    color = Color.White.copy(alpha = expand.coerceIn(0f, 1f)),
                                    radius = lerp(3f, 5.5f, expand.coerceIn(0f, 1f)).dp.toPx(),
                                    center = tip
                                )
                            }
                        }

                        val labelAlpha = ((expand - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        if (labelAlpha > 0.01f && durationMs > 0L) {
                            val displayedMs = if (ringState.scrubMs >= 0L) ringState.scrubMs else positionMs
                            val startLayout = measurer.measure(formatDurationMs(displayedMs), arcTimeStyle)
                            val endLayout = measurer.measure(formatDurationMs(durationMs), arcTimeStyle)
                            drawText(
                                textLayoutResult = startLayout,
                                topLeft = Offset(16.dp.toPx(), (h * 0.964f + 12.dp.toPx()).coerceAtMost(h - startLayout.size.height)),
                                alpha = labelAlpha
                            )
                            drawText(
                                textLayoutResult = endLayout,
                                topLeft = Offset(w - endLayout.size.width - 16.dp.toPx(), h * 0.643f + 12.dp.toPx()),
                                alpha = labelAlpha
                            )
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(curvedArcShape)
                    .graphicsLayer {
                        scaleX = activePlaybackScale
                        scaleY = activePlaybackScale
                    }
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
                    isPlaying = isPlaying,
                    parallaxFactor = 0.3f,
                    enablePauseScale = false,
                    pauseSaturation = 0.80f,
                    pagerState = pagerState,
                    onArtworkDoubleTap = ctx.onLikeFromDoubleTap,
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

        Spacer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(artworkHeight + 36.dp)
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val hitMarginPx = with(density) { 36.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val w = size.width.toFloat()
                        val h = currentArtworkHeightPx
                        val duration = currentDurationMs
                        if (duration <= 0L || w <= 0f || h <= 0f) return@awaitEachGesture
                        val initialHit = closestPosterArcHit(down.position, w, h)
                        if (initialHit.distancePx > hitMarginPx) return@awaitEachGesture

                        ringState.grab(currentReducedMotion)
                        var travel = Offset.Zero
                        var scrubbing = false
                        var lastFraction = initialHit.fraction
                        var lastTick = -1L

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            if (!scrubbing) {
                                travel += delta
                                if (travel.getDistance() < slop) continue
                                val moveLen = travel.getDistance().coerceAtLeast(1f)
                                val hitNow = closestPosterArcHit(change.position, w, h)
                                val along = abs((travel.x * hitNow.tangent.x + travel.y * hitNow.tangent.y) / moveLen)
                                if (along < 0.45f) {
                                    ringState.letGo(250L, currentReducedMotion)
                                    return@awaitEachGesture
                                }
                                scrubbing = true
                                if (currentHaptics) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            change.consume()
                            val hit = closestPosterArcHit(change.position, w, h)
                            lastFraction = hit.fraction
                            val ms = (duration * hit.fraction).toLong()
                            ringState.scrubMs = ms
                            val tick = ms / 10_000L
                            if (tick != lastTick) {
                                if (lastTick >= 0L && currentHaptics) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                lastTick = tick
                            }
                        }

                        if (scrubbing) {
                            currentOnSeekToFraction(lastFraction)
                        }
                        ringState.letGo(1_400L, currentReducedMotion)
                        scope.launch {
                            delay(420L)
                            if (!ringState.held) {
                                ringState.scrubMs = -1L
                            }
                        }
                    }
                }
        )

        val hasLyrics = !ctx.lyrics?.lines.isNullOrEmpty()
        val lyricsFound = remember { Animatable(if (hasLyrics) 1f else 0f) }
        LaunchedEffect(hasLyrics, reducedMotion) {
            val target = if (hasLyrics) 1f else 0f
            if (reducedMotion) {
                lyricsFound.snapTo(target)
            } else {
                lyricsFound.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 680, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f))
                )
            }
        }
        val titleOverlayVisible by remember { derivedStateOf { lyricsFound.value < 0.999f } }

        val columnCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val bigTitleCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val smallTitleCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }

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
                    .padding(start = 20.dp, end = 10.dp)
                    .onPlaced { columnCoords[0] = it },
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = artworkHeight * 0.14f, bottom = 22.dp)
                        .graphicsLayer {
                            if (!reducedMotion) {
                                translationY = ringState.expand.value * 12.dp.toPx()
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (hasLyrics || ctx.isLyricsLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (ctx.isLyricsLoading && !hasLyrics) {
                                        1f
                                    } else {
                                        ((lyricsFound.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                    }
                                }
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
                                onLongPressLine = { lineIdx -> ctx.onOpenShare(lineIdx) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (titleOverlayVisible && !ctx.isLyricsLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPlaced { bigTitleCoords[0] = it }
                                .graphicsLayer {
                                    val f = lyricsFound.value
                                    val col = columnCoords[0]
                                    val from = bigTitleCoords[0]
                                    val to = smallTitleCoords[0]
                                    if (f > 0.001f && col != null && from != null && to != null &&
                                        col.isAttached && from.isAttached && to.isAttached
                                    ) {
                                        val a = col.localPositionOf(from, Offset.Zero)
                                        val b = col.localPositionOf(to, Offset.Zero)
                                        translationX = (b.x - a.x) * f
                                        translationY = (b.y - a.y) * f
                                    }
                                    val scale = 1f + (18f / 28f - 1f) * f
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    alpha = 1f - ((f - 0.6f) / 0.35f).coerceIn(0f, 1f)
                                }
                        ) {
                            Text(
                                text = track?.title ?: "Nothing playing",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 28.sp,
                                    lineHeight = 34.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                PosterMetadataBlock(
                    track = track,
                    queue = carouselTracks,
                    pagerState = pagerState,
                    smallTitleAlpha = {
                        when {
                            ctx.isLyricsLoading && !hasLyrics -> 1f
                            hasLyrics -> ((lyricsFound.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                            else -> 0f
                        }
                    },
                    onSmallTitlePlaced = { smallTitleCoords[0] = it },
                    onClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        ctx.onOpenOverflowMenu()
                    }
                )
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
                    icon = if (ctx.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    iconTint = if (ctx.liked) Color(0xFFFF2D55) else Color.White,
                    contentDescription = if (ctx.liked) "Remove from liked" else "Like track",
                    onClick = {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        (ctx.onLikeFromButton ?: ctx.onToggleLike).invoke()
                    },
                    modifier = Modifier.ondaLikeTarget(ctx.likeBurstState, OndaLikeSlot.POSTER)
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
private fun PosterMetadataBlock(
    track: com.onda.music.core.model.Track?,
    queue: List<com.onda.music.core.model.Track>,
    pagerState: PagerState,
    smallTitleAlpha: () -> Float,
    onSmallTitlePlaced: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val visibleSlots by remember(pagerState, queue) {
        derivedStateOf {
            if (queue.isEmpty()) {
                emptyList()
            } else {
                val base = pagerState.currentPage.coerceIn(0, queue.lastIndex)
                val offset = pagerState.currentPageOffsetFraction
                buildList {
                    add(base)
                    if (offset > 0.005f && base + 1 in queue.indices) {
                        add(base + 1)
                    } else if (offset < -0.005f && base - 1 in queue.indices) {
                        add(base - 1)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clipToBounds()
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        if (visibleSlots.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onPlaced(onSmallTitlePlaced)
                        .graphicsLayer { alpha = smallTitleAlpha() }
                )
            }
        } else {
            for (slotPage in visibleSlots) {
                val slotTrack = queue.getOrNull(slotPage) ?: continue
                key("${slotTrack.id}_$slotPage") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val rel = (slotPage - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                                if (!reducedMotion) {
                                    translationX = rel * size.width * 0.75f
                                }
                                alpha = (1f - abs(rel) * 1.5f).coerceIn(0f, 1f)
                            },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = slotTrack.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xB3FFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = slotTrack.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .then(
                                    if (slotPage == pagerState.currentPage) {
                                        Modifier.onPlaced(onSmallTitlePlaced)
                                    } else {
                                        Modifier
                                    }
                                )
                                .graphicsLayer { alpha = smallTitleAlpha() }
                        )
                    }
                }
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
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White
) {
    val reducedMotion = OndaTheme.reducedMotion
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.90f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.54f, stiffness = 720f),
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
            tint = iconTint,
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    onLongPressLine: ((Int) -> Unit)? = null,
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
            if (reducedMotion) {
                listState.scrollToItem(index = activeIndex, scrollOffset = -32)
            } else {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = -32)
            }
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
                    .combinedClickable(
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onToggleExpanded()
                        },
                        onLongClick = if (onLongPressLine != null) {
                            {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onLongPressLine(safeIndex)
                            }
                        } else {
                            null
                        }
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = safeIndex to currentLine,
                    transitionSpec = {
                        if (!motionActive) {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                        } else {
                            (fadeIn(tween(240)) + slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { fullHeight -> fullHeight / 3 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
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
            Box(modifier = modifier) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
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
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "${index}_${line.startTimeMs}" }
                    ) { index, line ->
                        val isActive = isSynced && index == activeIndex
                        val distance = if (activeIndex >= 0) abs(index - activeIndex).coerceAtMost(8) else 0
                        PosterLyricRow(
                            line = line,
                            isActive = isActive,
                            isSynced = isSynced,
                            hasWordTiming = hasWordTiming,
                            effectivePositionMs = effectivePositionMs,
                            distanceFromActive = distance,
                            isUserScrolling = userDetachedScroll,
                            isPlaying = isPlaying,
                            motionActive = motionActive,
                            onClick = {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (isSynced) {
                                    if (isActive) {
                                        onToggleExpanded()
                                    } else {
                                        userDetachedScroll = false
                                        onSeekToMs((line.startTimeMs - lyricsOffsetMs).coerceAtLeast(0L))
                                    }
                                } else {
                                    onToggleExpanded()
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

                val collapseInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp)
                        .size(36.dp)
                        .playerPressBounce(
                            interactionSource = collapseInteraction,
                            pressedScale = 0.90f,
                            reducedMotion = !motionActive
                        )
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x28FFFFFF))
                        .clickable(
                            interactionSource = collapseInteraction,
                            indication = RoundedHighlight(cornerRadius = 18.dp),
                            onClick = onToggleExpanded
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
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
                accentColor = Color.White,
                visible = true
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
                targetValue = if (isLeadActiveWord && motionActive) 1.10f else 1f,
                animationSpec = if (motionActive) AppMotion.SnappySpring else snap(),
                label = "SpotlightWordScale"
            )
            val wordLiftDp by animateFloatAsState(
                targetValue = when {
                    !motionActive -> 0f
                    isLeadActiveWord -> -1.5f
                    isSpokenOrCurrent -> 0f
                    else -> 2f
                },
                animationSpec = if (motionActive) spring(dampingRatio = 0.85f, stiffness = 360f) else snap(),
                label = "SpotlightWordLift"
            )
            val wordAlpha by animateFloatAsState(
                targetValue = when {
                    isSpokenOrCurrent -> 1f
                    distanceAhead == 1 -> 0.56f
                    else -> 0.38f
                },
                animationSpec = if (motionActive) tween(durationMillis = 180) else snap(),
                label = "SpotlightWordAlpha"
            )

            Text(
                text = slice.text,
                style = spotlightStyle,
                color = Color.White,
                modifier = Modifier
                    .padding(end = if (isLeadActiveWord && motionActive) 3.dp else 0.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.55f)
                        scaleX = wordScale
                        scaleY = wordScale
                        translationY = wordLiftDp.dp.toPx()
                        alpha = wordAlpha
                    }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PosterLyricRow(
    line: LyricsLine,
    isActive: Boolean,
    isSynced: Boolean,
    hasWordTiming: Boolean,
    effectivePositionMs: Long,
    distanceFromActive: Int,
    isUserScrolling: Boolean,
    isPlaying: Boolean,
    motionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    if (line.isInterlude) {
        val interludeDuration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(1L)
        val interludeProgress = ((effectivePositionMs - line.startTimeMs).toFloat() / interludeDuration).coerceIn(0f, 1f)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            LyricsInterludeDots(
                isActive = isActive,
                isPlaying = isPlaying,
                motionActive = motionActive,
                progress = interludeProgress,
                accentColor = Color.White,
                visible = isActive
            )
        }
        return
    }

    val activeScale by animateFloatAsState(
        targetValue = if (isActive && motionActive) 1.03f else 1f,
        animationSpec = AppMotion.adaptiveSpring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            reducedMotion = !motionActive
        ),
        label = "PosterLyricScale"
    )

    val targetLineBlurPx = when {
        !motionActive || !isSynced || isActive || isUserScrolling || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> 0f
        distanceFromActive == 1 -> 3.5f
        else -> 7f
    }
    val lineBlurPx by animateFloatAsState(
        targetValue = targetLineBlurPx,
        animationSpec = if (!motionActive) snap() else tween(durationMillis = 320),
        label = "PosterLineBlur"
    )

    val textStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold
    )
    val rowInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .playerPressBounce(
                interactionSource = rowInteraction,
                pressedScale = 0.985f,
                releaseDampingRatio = 0.54f,
                releaseStiffness = 720f,
                reducedMotion = !motionActive
            )
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = RoundedHighlight(cornerRadius = 10.dp),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
                renderEffect = if (motionActive) PlayerLineBlurCache.forRadiusPx(lineBlurPx) else null
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (hasWordTiming && line.words.isNotEmpty() && isActive) {
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
                        animationSpec = if (motionActive) AppMotion.SnappySpring else snap(),
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
