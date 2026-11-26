package com.onda.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.ArtworkUrlResolver
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.press3D
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.StudioArtworkFrame
import com.onda.music.core.model.StudioSeekbarStyle
import com.onda.music.core.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun clampCarouselIndex(index: Int, count: Int): Int {
    return if (count <= 0) 0 else index.coerceIn(0, count - 1)
}

internal fun clampScrubCapsuleOffsetX(
    thumbCenterX: Float,
    capsuleWidthPx: Float,
    containerWidthPx: Float
): Float {
    val safeContainer = if (containerWidthPx.isFinite()) containerWidthPx.coerceAtLeast(0f) else 0f
    val safeCapsule = if (capsuleWidthPx.isFinite()) capsuleWidthPx.coerceAtLeast(0f) else 0f
    val safeThumb = if (thumbCenterX.isFinite()) thumbCenterX else 0f
    val maxOffset = (safeContainer - safeCapsule).coerceAtLeast(0f)
    return (safeThumb - safeCapsule * 0.5f).coerceIn(0f, maxOffset)
}

internal fun computeScrubTiltDegrees(
    dragDeltaPx: Float,
    dtMs: Long,
    reducedMotion: Boolean
): Float {
    if (reducedMotion || !dragDeltaPx.isFinite()) return 0f
    val safeDt = dtMs.coerceAtLeast(1L)
    val vx = (dragDeltaPx / safeDt) * 1000f
    return if (vx.isFinite()) {
        (vx / 280f).coerceIn(-7.5f, 7.5f)
    } else if (vx > 0f) {
        7.5f
    } else {
        -7.5f
    }
}

internal fun resolveScrubPreviewLyric(
    lyrics: LyricsDocument?,
    positionMs: Long,
    lyricsOffsetMs: Long
): String? {
    val doc = lyrics ?: return null
    if (!doc.isSynced || doc.lines.isEmpty()) return null
    val adjustedMs = (positionMs + lyricsOffsetMs).coerceAtLeast(0L)
    val idx = findActiveLyricsLineIndex(doc.lines, adjustedMs, isSynced = true)
    if (idx !in doc.lines.indices) return null
    val line = doc.lines[idx]
    if (line.isInterlude) return null
    return line.text.trim().takeIf { it.isNotEmpty() }
}

internal fun computeLiquidSpectrumBarHeightFraction(
    barIndex: Int,
    barCount: Int,
    phase: Float,
    activeMotion: Boolean,
    collapse: Float
): Float {
    val count = barCount.coerceAtLeast(2)
    val t = (barIndex.toFloat() / (count - 1).toFloat()).coerceIn(0f, 1f)
    val safePhase = if (phase.isFinite()) phase else 0f
    val baseProfile = 0.22f + 0.38f * (0.5f + 0.5f * sin(t * 9.5f))
    val dynamicWave = if (activeMotion) {
        val h1 = 0.5f + 0.5f * sin(t * 14f - safePhase * 1.4f)
        val h2 = 0.5f + 0.5f * cos(t * 23f + safePhase * 0.9f)
        (baseProfile * 0.55f + (h1 * 0.65f + h2 * 0.35f) * 0.45f).coerceIn(0.14f, 1f)
    } else {
        baseProfile.coerceIn(0.14f, 1f)
    }
    val c = if (collapse.isFinite()) collapse.coerceIn(0f, 1f) else 0f
    return (dynamicWave * (1f - c) + 0.14f * c).coerceIn(0.08f, 1f)
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
fun TrackArtworkImage(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassTokens.RadiusLg),
    contentScale: ContentScale = ContentScale.Crop,
    isPlaying: Boolean = true,
    enablePauseTransform: Boolean = false,
    pauseScale: Float = 0.94f,
    pauseSaturation: Float = 0.80f
) {
    val context = LocalContext.current
    val reducedMotion = OndaTheme.reducedMotion
    val upgradedUrl = remember(artworkUrl) { ArtworkUrlResolver.upgradeHighRes(artworkUrl) }
    var useFallbackUrl by remember(artworkUrl) { mutableStateOf(false) }
    var hasFailed by remember(artworkUrl) { mutableStateOf(false) }
    val activeUrl = when {
        artworkUrl.isNullOrBlank() -> null
        !useFallbackUrl && !upgradedUrl.isNullOrBlank() -> upgradedUrl
        else -> artworkUrl
    }

    val targetScale = if (!enablePauseTransform || isPlaying || reducedMotion) 1f else pauseScale
    val targetSat = if (!enablePauseTransform || isPlaying) 1f else pauseSaturation

    val animatedPauseScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.84f, stiffness = 360f),
        label = "ArtworkPauseScale"
    )
    val animatedSaturation by animateFloatAsState(
        targetValue = targetSat,
        animationSpec = if (reducedMotion) snap() else tween(400, easing = FastOutSlowInEasing),
        label = "ArtworkPauseSat"
    )

    Box(
        modifier = modifier
            .then(
                if (enablePauseTransform) {
                    Modifier.graphicsLayer {
                        scaleX = animatedPauseScale
                        scaleY = animatedPauseScale
                        colorFilter = PlayerSaturationFilterCache.forSaturation(animatedSaturation)
                    }
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF242424),
                        Color(0xFF141414),
                        Color(0xFF000000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color(0xFF9E9E9E),
            modifier = Modifier.size(36.dp)
        )
        if (!activeUrl.isNullOrBlank() && !hasFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                onError = {
                    if (!useFallbackUrl && !upgradedUrl.isNullOrBlank() && upgradedUrl != artworkUrl) {
                        useFallbackUrl = true
                    } else {
                        hasFailed = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun rememberSongCarouselPagerState(
    queue: List<Track>,
    currentIndex: Int,
    currentTrack: Track?
): PagerState {
    val count = when {
        queue.isNotEmpty() -> queue.size
        currentTrack != null -> 1
        else -> 1
    }
    val safeIndex = currentIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    return rememberPagerState(
        initialPage = safeIndex,
        pageCount = { count }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongCarousel(
    queue: List<Track>,
    currentIndex: Int,
    currentTrack: Track?,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    artworkShape: Shape = RoundedCornerShape(GlassTokens.RadiusXl),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp),
    pageSpacing: Dp = 16.dp,
    enablePress3D: Boolean = true,
    onArtworkLongClick: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    parallaxFactor: Float = 0.3f,
    enablePauseScale: Boolean = true,
    pauseScale: Float = 0.94f,
    pauseSaturation: Float = 0.80f,
    pagerState: PagerState? = null,
    onArtworkDoubleTap: ((Offset) -> Unit)? = null,
    artworkFrame: StudioArtworkFrame? = null
) {
    val reducedMotion = OndaTheme.reducedMotion
    val resolvedShape = when (artworkFrame) {
        StudioArtworkFrame.SOFT_SQUIRCLE -> SmoothCornerShape(24.dp)
        StudioArtworkFrame.EDGE_BLEED -> RectangleShape
        StudioArtworkFrame.VINYL_DISC -> CircleShape
        StudioArtworkFrame.MINIMAL_FRAME -> RoundedCornerShape(10.dp)
        null -> artworkShape
    }
    val resolvedPadding = if (artworkFrame == StudioArtworkFrame.EDGE_BLEED) {
        PaddingValues(0.dp)
    } else {
        contentPadding
    }
    val resolvedSpacing = if (artworkFrame == StudioArtworkFrame.EDGE_BLEED) {
        0.dp
    } else {
        pageSpacing
    }
    val isVinylFrame = artworkFrame == StudioArtworkFrame.VINYL_DISC
    val isMinimalFrame = artworkFrame == StudioArtworkFrame.MINIMAL_FRAME
    var vinylRotationDeg by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isVinylFrame, isPlaying, reducedMotion) {
        if (!isVinylFrame || !isPlaying || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    vinylRotationDeg = (vinylRotationDeg + dt * 24f) % 360f
                }
                lastNanos = frameNanos
            }
        }
    }

    val displayTracks = remember(queue, currentTrack) {
        when {
            queue.isNotEmpty() -> queue
            currentTrack != null -> listOf(currentTrack)
            else -> emptyList()
        }
    }

    val currentLongClick by rememberUpdatedState(onArtworkLongClick)
    val currentDoubleTap by rememberUpdatedState(onArtworkDoubleTap)

    if (displayTracks.isEmpty()) {
        val emptyCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        TrackArtworkImage(
            artworkUrl = null,
            contentDescription = "No track artwork",
            shape = resolvedShape,
            isPlaying = isPlaying,
            enablePauseTransform = true,
            pauseScale = if (enablePauseScale) pauseScale else 1f,
            pauseSaturation = pauseSaturation,
            modifier = modifier
                .onGloballyPositioned { emptyCoords[0] = it }
                .then(
                    if (isMinimalFrame) {
                        Modifier.border(1.dp, Color(0x28FFFFFF), resolvedShape)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (onArtworkLongClick != null || onArtworkDoubleTap != null) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { localPos ->
                                    val coords = emptyCoords[0]
                                    val windowPos = if (coords != null && coords.isAttached) {
                                        coords.localToWindow(localPos)
                                    } else {
                                        localPos
                                    }
                                    currentDoubleTap?.invoke(windowPos)
                                },
                                onLongPress = { currentLongClick?.invoke() }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        )
        return
    }

    val safeIndex = clampCarouselIndex(currentIndex, displayTracks.size)
    val resolvedPagerState = pagerState ?: rememberPagerState(
        initialPage = safeIndex,
        pageCount = { displayTracks.size }
    )
    val currentSafeIndex by rememberUpdatedState(safeIndex)
    val currentOnSelectIndex by rememberUpdatedState(onSelectIndex)
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(safeIndex, displayTracks.size, resolvedPagerState, reducedMotion) {
        if (resolvedPagerState.currentPage != safeIndex && !resolvedPagerState.isScrollInProgress) {
            isProgrammaticScroll = true
            try {
                if (reducedMotion) {
                    resolvedPagerState.scrollToPage(safeIndex)
                } else {
                    resolvedPagerState.animateScrollToPage(safeIndex)
                }
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    LaunchedEffect(resolvedPagerState, displayTracks.size) {
        snapshotFlow { resolvedPagerState.settledPage to resolvedPagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .distinctUntilChanged()
            .collect { (settled, _) ->
                if (!isProgrammaticScroll && settled != currentSafeIndex && settled in displayTracks.indices) {
                    currentOnSelectIndex(settled)
                }
            }
    }

    val hasCardPadding = resolvedSpacing > 0.dp || resolvedPadding != PaddingValues(0.dp)

    HorizontalPager(
        state = resolvedPagerState,
        modifier = modifier,
        contentPadding = resolvedPadding,
        pageSpacing = resolvedSpacing,
        key = { page ->
            val track = displayTracks.getOrNull(page)
            if (track != null) "${track.id}_$page" else "empty_$page"
        }
    ) { page ->
        val track = displayTracks.getOrNull(page) ?: return@HorizontalPager
        val pageCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { pageCoords[0] = it }
                .graphicsLayer {
                    val pageOffset = (resolvedPagerState.currentPage - page) + resolvedPagerState.currentPageOffsetFraction
                    val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                    val s = if (hasCardPadding && !reducedMotion) lerp(1f, 0.90f, absOffset) else 1f
                    val a = lerp(1f, 0.58f, absOffset)
                    scaleX = s
                    scaleY = s
                    alpha = a
                    shape = resolvedShape
                    clip = true
                }
                .then(
                    if (isMinimalFrame) {
                        Modifier.border(1.dp, Color(0x28FFFFFF), resolvedShape)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (onArtworkLongClick != null || onArtworkDoubleTap != null) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { localPos ->
                                    val coords = pageCoords[0]
                                    val windowPos = if (coords != null && coords.isAttached) {
                                        coords.localToWindow(localPos)
                                    } else {
                                        localPos
                                    }
                                    currentDoubleTap?.invoke(windowPos)
                                },
                                onLongPress = { currentLongClick?.invoke() }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (enablePress3D && page == resolvedPagerState.currentPage) {
                        Modifier.press3D()
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            TrackArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                shape = resolvedShape,
                isPlaying = isPlaying,
                enablePauseTransform = true,
                pauseScale = if (enablePauseScale) pauseScale else 1f,
                pauseSaturation = pauseSaturation,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isVinylFrame && page == resolvedPagerState.currentPage && !reducedMotion) {
                            rotationZ = vinylRotationDeg
                        }
                        if (!reducedMotion && parallaxFactor > 0f && !isVinylFrame) {
                            val relOffset = (page - resolvedPagerState.currentPage) - resolvedPagerState.currentPageOffsetFraction
                            val shift = -relOffset * size.width * parallaxFactor
                            translationX = shift
                            if (hasCardPadding && size.width > 0f) {
                                val coverScale = 1f + 2f * abs(shift) / size.width
                                scaleX = coverScale
                                scaleY = coverScale
                            }
                        }
                    }
            )
            if (isVinylFrame) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141414))
                        .border(1.5.dp, Color(0x4DFFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerSeekSection(
    playbackState: PlaybackState,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
    seekbarStyle: StudioSeekbarStyle = StudioSeekbarStyle.HARMONIC_WAVE,
    lyrics: LyricsDocument? = null,
    lyricsOffsetMs: Long = 0L
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val durationMs = playbackState.durationMs.coerceAtLeast(0L)
    val positionMs = playbackState.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    val bufferedMs = playbackState.bufferedPositionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    val rawProgress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedProgress = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val isPlaying = playbackState.status == PlaybackStatus.PLAYING

    var scrubbingFraction by remember { mutableStateOf<Float?>(null) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(pendingSeekFraction, rawProgress) {
        val pending = pendingSeekFraction ?: return@LaunchedEffect
        if (abs(rawProgress - pending) < 0.025f) {
            pendingSeekFraction = null
        }
    }

    LaunchedEffect(pendingSeekFraction) {
        if (pendingSeekFraction != null) {
            delay(1_400L)
            pendingSeekFraction = null
        }
    }

    val targetFraction = scrubbingFraction ?: pendingSeekFraction ?: rawProgress
    val shownPositionMs = if (durationMs > 0L) {
        (durationMs * targetFraction).toLong()
    } else {
        positionMs
    }

    val fillFollower = remember { SeekFillFollower() }
    val currentTarget by rememberUpdatedState(targetFraction)
    val currentMoving by rememberUpdatedState(isPlaying || scrubbingFraction != null)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentOnSeekToFraction by rememberUpdatedState(onSeekToFraction)

    LaunchedEffect(fillFollower) {
        fillFollower.follow(
            target = { currentTarget },
            moving = { currentMoving },
            reducedMotion = { currentReducedMotion }
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    var isGestureHeld by remember { mutableStateOf(false) }
    val held = isGestureHeld || isPressed || isDragged

    val expandProgress by animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "PlayerSeekExpand"
    )

    val stretchPx = remember { Animatable(0f) }
    val maxStretchPx = with(density) { 26.dp.toPx() }
    val scope = rememberCoroutineScope()

    var wavePhase by remember { mutableFloatStateOf(0f) }
    val waveAmplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && !held && !reducedMotion) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else AppMotion.GentleSpring,
        label = "PlayerWaveAmp"
    )

    LaunchedEffect(isPlaying, held, reducedMotion) {
        if (!isPlaying || held || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    wavePhase = (wavePhase + dt * 5.2f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    val wavePath = remember { Path() }
    val waveLengthPx = with(density) { 22.dp.toPx() }.coerceAtLeast(8f)
    val maxWaveAmpPx = with(density) { 3.dp.toPx() }
    var seekBarWidthPx by remember { mutableFloatStateOf(0f) }
    var targetTiltDeg by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(targetTiltDeg, held) {
        if (!held) {
            targetTiltDeg = 0f
        } else if (targetTiltDeg != 0f) {
            delay(75L)
            targetTiltDeg = 0f
        }
    }

    val capsuleTiltDeg by animateFloatAsState(
        targetValue = if (held && !reducedMotion) targetTiltDeg else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.72f, stiffness = 460f),
        label = "ScrubCapsuleTilt"
    )

    val doc = lyrics
    val activeScrubLyric = remember(doc, shownPositionMs, lyricsOffsetMs) {
        resolveScrubPreviewLyric(doc, shownPositionMs, lyricsOffsetMs)
    }
    var capsuleWidthPx by remember { mutableFloatStateOf(0f) }
    var capsuleHeightPx by remember { mutableFloatStateOf(0f) }
    val capsuleGapPx = with(density) { 10.dp.toPx() }
    val padPxFloat = with(density) { 8.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                .height(DockMetrics.MinTapTarget)
                .onSizeChanged {
                    seekBarWidthPx = it.width.toFloat()
                    fillFollower.widthPx = it.width.toFloat()
                }
                .pointerInput(durationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        isGestureHeld = true
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.id == down.id && it.pressed }) break
                        }
                        isGestureHeld = false
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs <= 0L) return@detectTapGestures
                        val pad = with(density) { 8.dp.toPx() }
                        val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                        val fraction = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                        pendingSeekFraction = fraction
                        currentOnSeekToFraction(fraction)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isGestureHeld = true
                            targetTiltDeg = 0f
                            val pad = with(density) { 8.dp.toPx() }
                            val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                            scrubbingFraction = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val pad = with(density) { 8.dp.toPx() }
                            val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                            val raw = (change.position.x - pad) / usable
                            scrubbingFraction = raw.coerceIn(0f, 1f)

                            val dtMs = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                            targetTiltDeg = computeScrubTiltDegrees(dragAmount, dtMs, currentReducedMotion)

                            if (!currentReducedMotion) {
                                val over = when {
                                    raw < 0f -> raw * usable
                                    raw > 1f -> (raw - 1f) * usable
                                    else -> 0f
                                }
                                val banded = rubberBandOffset(over, maxStretchPx)
                                scope.launch { stretchPx.snapTo(banded) }
                            }
                        },
                        onDragEnd = {
                            isGestureHeld = false
                            targetTiltDeg = 0f
                            val commit = scrubbingFraction
                            scrubbingFraction = null
                            if (commit != null) {
                                pendingSeekFraction = commit
                                currentOnSeekToFraction(commit)
                            }
                            scope.launch {
                                if (currentReducedMotion) {
                                    stretchPx.snapTo(0f)
                                } else {
                                    stretchPx.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isGestureHeld = false
                            targetTiltDeg = 0f
                            scrubbingFraction = null
                            scope.launch {
                                if (currentReducedMotion) {
                                    stretchPx.snapTo(0f)
                                } else {
                                    stretchPx.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f)
                                    )
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if ((held || expandProgress > 0.01f) && durationMs > 0L && seekBarWidthPx > 0f) {
                val horizontalPad = 12.dp
                val horizontalPadPx = with(density) { horizontalPad.toPx() }
                val verticalPad = 18.dp
                val verticalPadPx = with(density) { verticalPad.toPx() }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(
                        -horizontalPadPx.roundToInt(),
                        -(capsuleHeightPx + capsuleGapPx + verticalPadPx).roundToInt()
                    ),
                    properties = PopupProperties(
                        focusable = false,
                        clippingEnabled = false
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .width(with(density) { seekBarWidthPx.toDp() } + horizontalPad * 2)
                            .padding(horizontal = horizontalPad, vertical = verticalPad)
                    ) {
                        Box(
                            modifier = Modifier
                                .onSizeChanged {
                                    capsuleWidthPx = it.width.toFloat()
                                    capsuleHeightPx = it.height.toFloat()
                                }
                                .graphicsLayer {
                                    val endX = (seekBarWidthPx - padPxFloat).coerceAtLeast(padPxFloat + 1f)
                                    val usable = endX - padPxFloat
                                    val thumbCenterX = padPxFloat + usable * fillFollower.fraction.coerceIn(0f, 1f)
                                    translationX = clampScrubCapsuleOffsetX(thumbCenterX, capsuleWidthPx, seekBarWidthPx)
                                    translationY = if (reducedMotion) 0f else (1f - expandProgress) * 10.dp.toPx()
                                    val s = if (reducedMotion) 1f else lerp(0.84f, 1f, expandProgress)
                                    scaleX = s
                                    scaleY = s
                                    alpha = if (capsuleWidthPx > 0f) expandProgress.coerceIn(0f, 1f) else 0f
                                    rotationZ = if (reducedMotion) 0f else capsuleTiltDeg
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .widthIn(max = 260.dp)
                                .clip(RoundedCornerShape(GlassTokens.RadiusMd))
                                .background(Color(0xEB141418))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(GlassTokens.RadiusMd)
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    OdometerText(
                                        text = formatDurationMs(shownPositionMs),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = " / ${formatDurationMs(durationMs)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF9E9E9E),
                                        maxLines = 1
                                    )
                                }
                                if (activeScrubLyric != null) {
                                    Text(
                                        text = activeScrubLyric,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor.copy(alpha = 0.92f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = stretchPx.value
                        if (s != 0f && size.width > 0f && !reducedMotion) {
                            val pull = (abs(s) / maxStretchPx).coerceIn(0f, 1f)
                            scaleX = (size.width + abs(s)) / size.width
                            scaleY = 1f - 0.18f * pull
                            transformOrigin = TransformOrigin(if (s > 0f) 0f else 1f, 0.5f)
                        }
                    }
            ) {
                val strokePx = lerp(3.dp.toPx(), 6.dp.toPx(), expandProgress)
                val padPx = 8.dp.toPx()
                val startX = padPx
                val endX = (size.width - padPx).coerceAtLeast(startX + 1f)
                val usableWidth = endX - startX
                val centerY = size.height * 0.5f
                val displayed = fillFollower.fraction.coerceIn(0f, 1f)
                val clampedBuffered = bufferedProgress.coerceIn(0f, 1f)
                val activeEndX = startX + usableWidth * displayed
                val bufferedEndX = startX + usableWidth * clampedBuffered

                when (seekbarStyle) {
                    StudioSeekbarStyle.PRECISION_BAR -> {
                        val railStroke = lerp(2.5.dp.toPx(), 4.5.dp.toPx(), expandProgress)
                        drawLine(
                            color = GlassTokens.GlassHighlight.copy(alpha = 0.16f + 0.08f * expandProgress),
                            start = Offset(startX, centerY),
                            end = Offset(endX, centerY),
                            strokeWidth = railStroke,
                            cap = StrokeCap.Round
                        )
                        if (clampedBuffered > displayed) {
                            drawLine(
                                color = GlassTokens.GlassHighlight.copy(alpha = 0.28f),
                                start = Offset(activeEndX, centerY),
                                end = Offset(bufferedEndX, centerY),
                                strokeWidth = railStroke,
                                cap = StrokeCap.Round
                            )
                        }
                        drawLine(
                            color = accentColor,
                            start = Offset(startX, centerY),
                            end = Offset(activeEndX, centerY),
                            strokeWidth = railStroke,
                            cap = StrokeCap.Round
                        )
                        val capW = lerp(4.dp.toPx(), 6.dp.toPx(), expandProgress)
                        val capH = lerp(14.dp.toPx(), 20.dp.toPx(), expandProgress)
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.24f + 0.14f * expandProgress),
                            topLeft = Offset(activeEndX - capW * 0.95f, centerY - capH * 0.6f),
                            size = Size(capW * 1.9f, capH * 1.2f),
                            cornerRadius = CornerRadius(capW, capW)
                        )
                        drawRoundRect(
                            color = GlassTokens.TextPrimary,
                            topLeft = Offset(activeEndX - capW * 0.5f, centerY - capH * 0.5f),
                            size = Size(capW, capH),
                            cornerRadius = CornerRadius(capW * 0.5f, capW * 0.5f)
                        )
                    }

                    StudioSeekbarStyle.SEGMENTED_PULSE -> {
                        val tickCount = 28
                        val tickWidth = lerp(2.4.dp.toPx(), 3.2.dp.toPx(), expandProgress)
                        for (i in 0 until tickCount) {
                            val tPos = i.toFloat() / (tickCount - 1).toFloat()
                            val tx = startX + usableWidth * tPos
                            val harmonic = (sin(i * 0.85f) * 0.5f + 0.5f)
                            val proximity = if (isPlaying && !held && !reducedMotion) {
                                exp(-abs(tPos - displayed) * 16f) * 0.45f
                            } else {
                                0f
                            }
                            val baseH = lerp(6.dp.toPx(), 15.dp.toPx(), (harmonic + proximity).coerceIn(0f, 1f))
                            val tickColor = when {
                                tPos <= displayed -> accentColor
                                tPos <= clampedBuffered -> GlassTokens.GlassHighlight.copy(alpha = 0.34f)
                                else -> GlassTokens.GlassHighlight.copy(alpha = 0.16f)
                            }
                            drawLine(
                                color = tickColor,
                                start = Offset(tx, centerY - baseH * 0.5f),
                                end = Offset(tx, centerY + baseH * 0.5f),
                                strokeWidth = tickWidth,
                                cap = StrokeCap.Round
                            )
                        }
                        val thumbRadius = 5.5.dp.toPx() * lerp(1f, 1.32f, expandProgress)
                        drawCircle(
                            color = GlassTokens.TextPrimary,
                            radius = thumbRadius,
                            center = Offset(activeEndX, centerY)
                        )
                    }

                    StudioSeekbarStyle.HARMONIC_WAVE -> {
                        drawLine(
                            color = GlassTokens.GlassHighlight.copy(alpha = 0.16f + 0.08f * expandProgress),
                            start = Offset(startX, centerY),
                            end = Offset(endX, centerY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )

                        if (clampedBuffered > displayed) {
                            drawLine(
                                color = GlassTokens.GlassHighlight.copy(alpha = 0.28f),
                                start = Offset(activeEndX, centerY),
                                end = Offset(bufferedEndX, centerY),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                        }

                        val ampPx = maxWaveAmpPx * waveAmplitudeFactor
                        if (ampPx < 0.25f) {
                            drawLine(
                                color = accentColor,
                                start = Offset(startX, centerY),
                                end = Offset(activeEndX, centerY),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                        } else {
                            wavePath.reset()
                            val stepPx = 3f
                            var x = startX
                            val twoPiOverLambda = (2f * PI.toFloat()) / waveLengthPx
                            var started = false
                            while (x <= activeEndX) {
                                val distFromEdge = minOf(x - startX, activeEndX - x)
                                val edgeEnvelope = (distFromEdge / 16f).coerceIn(0f, 1f)
                                val y = centerY + sin((x - startX) * twoPiOverLambda - wavePhase) * ampPx * edgeEnvelope
                                if (!started) {
                                    wavePath.moveTo(x, y)
                                    started = true
                                } else {
                                    wavePath.lineTo(x, y)
                                }
                                x += stepPx
                            }
                            if (started) {
                                wavePath.lineTo(activeEndX, centerY)
                                drawPath(
                                    path = wavePath,
                                    color = accentColor,
                                    style = Stroke(
                                        width = strokePx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        val thumbScale = lerp(1f, 1.38f, expandProgress)
                        val thumbRadius = 6.2.dp.toPx() * thumbScale
                        drawCircle(
                            color = accentColor.copy(alpha = 0.28f + 0.14f * expandProgress),
                            radius = thumbRadius * 1.6f,
                            center = Offset(activeEndX, centerY)
                        )
                        drawCircle(
                            color = GlassTokens.TextPrimary,
                            radius = thumbRadius,
                            center = Offset(activeEndX, centerY)
                        )
                    }

                    StudioSeekbarStyle.LIQUID_SPECTRUM -> {
                        val collapse = expandProgress.coerceIn(0f, 1f)
                        val railAlpha = collapse * 0.24f
                        if (railAlpha > 0.01f) {
                            val railStroke = lerp(2.5.dp.toPx(), 4.5.dp.toPx(), collapse)
                            drawLine(
                                color = GlassTokens.GlassHighlight.copy(alpha = railAlpha),
                                start = Offset(startX, centerY),
                                end = Offset(endX, centerY),
                                strokeWidth = railStroke,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = accentColor.copy(alpha = collapse),
                                start = Offset(startX, centerY),
                                end = Offset(activeEndX, centerY),
                                strokeWidth = railStroke,
                                cap = StrokeCap.Round
                            )
                        }
                        val barCount = 36
                        val barStroke = lerp(2.6.dp.toPx(), 1.8.dp.toPx(), collapse)
                        val maxBarHeight = lerp(18.dp.toPx(), 4.5.dp.toPx(), collapse)
                        val activeMotion = isPlaying && !held && !reducedMotion
                        for (i in 0 until barCount) {
                            val tPos = i.toFloat() / (barCount - 1).toFloat()
                            val bx = startX + usableWidth * tPos
                            val heightFraction = computeLiquidSpectrumBarHeightFraction(
                                barIndex = i,
                                barCount = barCount,
                                phase = wavePhase,
                                activeMotion = activeMotion,
                                collapse = collapse
                            )
                            val barHalfHeight = maxBarHeight * heightFraction * 0.5f
                            val barColor = when {
                                tPos <= displayed -> accentColor.copy(alpha = lerp(0.95f, 0.25f, collapse))
                                tPos <= clampedBuffered -> GlassTokens.GlassHighlight.copy(alpha = lerp(0.34f, 0.12f, collapse))
                                else -> GlassTokens.GlassHighlight.copy(alpha = lerp(0.18f, 0.08f, collapse))
                            }
                            drawLine(
                                color = barColor,
                                start = Offset(bx, centerY - barHalfHeight),
                                end = Offset(bx, centerY + barHalfHeight),
                                strokeWidth = barStroke,
                                cap = StrokeCap.Round
                            )
                        }
                        val thumbRadius = lerp(4.5.dp.toPx(), 6.8.dp.toPx(), collapse)
                        drawCircle(
                            color = accentColor.copy(alpha = 0.26f + 0.16f * collapse),
                            radius = thumbRadius * 1.55f,
                            center = Offset(activeEndX, centerY)
                        )
                        drawCircle(
                            color = GlassTokens.TextPrimary,
                            radius = thumbRadius,
                            center = Offset(activeEndX, centerY)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val timeAlpha = 0.78f + 0.22f * expandProgress
            OdometerText(
                text = formatDurationMs(shownPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = timeAlpha),
                modifier = Modifier.graphicsLayer {
                    if (!reducedMotion) {
                        val s = stretchPx.value
                        translationX = minOf(s, 0f)
                        translationY = 3.5.dp.toPx() * expandProgress
                        val scale = 1f + 0.10f * expandProgress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                }
            )
            Text(
                text = formatDurationMs(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.68f + 0.32f * expandProgress),
                modifier = Modifier.graphicsLayer {
                    if (!reducedMotion) {
                        val s = stretchPx.value
                        translationX = maxOf(s, 0f)
                        translationY = 3.5.dp.toPx() * expandProgress
                        val scale = 1f + 0.10f * expandProgress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                }
            )
        }
    }
}

@Composable
fun PlayerTransportControls(
    playbackState: PlaybackState,
    hapticsEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    val reducedMotion = OndaTheme.reducedMotion
    val scope = rememberCoroutineScope()
    val isPlaying = playbackState.status == PlaybackStatus.PLAYING
    val isBuffering = playbackState.status == PlaybackStatus.BUFFERING
    val primarySize = if (compact) 56.dp else 68.dp

    val shuffleInteraction = remember { MutableInteractionSource() }
    val prevInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val repeatInteraction = remember { MutableInteractionSource() }

    val shuffleKick = remember { Animatable(0f) }
    val prevKick = remember { Animatable(0f) }
    val nextKick = remember { Animatable(0f) }
    val repeatSpin = remember { Animatable(0f) }
    var shuffleKickJob by remember { mutableStateOf<Job?>(null) }
    var prevKickJob by remember { mutableStateOf<Job?>(null) }
    var nextKickJob by remember { mutableStateOf<Job?>(null) }
    var repeatSpinJob by remember { mutableStateOf<Job?>(null) }

    val shuffleTint by animateColorAsState(
        targetValue = if (playbackState.shuffleEnabled) Color.White else Color(0xFF9E9E9E),
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "ShuffleTint"
    )
    val repeatTint by animateColorAsState(
        targetValue = if (playbackState.repeatMode != RepeatMode.OFF) Color.White else Color(0xFF9E9E9E),
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "RepeatTint"
    )

    var displayedPlaying by remember { mutableStateOf(isPlaying) }
    val playSquash = remember { Animatable(1f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (reducedMotion) {
            displayedPlaying = isPlaying
            playSquash.snapTo(1f)
            return@LaunchedEffect
        }
        if (isPlaying != displayedPlaying) {
            playSquash.animateTo(0.55f, tween(durationMillis = 100, easing = FastOutLinearInEasing))
            displayedPlaying = isPlaying
            playSquash.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 420f))
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!reducedMotion) {
                    shuffleKickJob?.cancel()
                    shuffleKickJob = scope.launch {
                        shuffleKick.snapTo(0f)
                        shuffleKick.animateTo(1f, tween(75))
                        shuffleKick.animateTo(0f, spring(dampingRatio = 0.48f, stiffness = 500f))
                    }
                }
                onToggleShuffle()
            },
            interactionSource = shuffleInteraction,
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .playerPressBounce(
                    interactionSource = shuffleInteraction,
                    pressedScale = 0.86f,
                    releaseDampingRatio = 0.54f,
                    releaseStiffness = 720f,
                    reducedMotion = reducedMotion
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = shuffleTint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        val s = 1f + shuffleKick.value * 0.14f
                        scaleX = s
                        scaleY = s
                    }
            )
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!reducedMotion) {
                    prevKickJob?.cancel()
                    prevKickJob = scope.launch {
                        prevKick.animateTo(-1f, tween(80))
                        prevKick.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 460f))
                    }
                }
                onSkipPrevious()
            },
            interactionSource = prevInteraction,
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .playerPressBounce(
                    interactionSource = prevInteraction,
                    pressedScale = 0.86f,
                    releaseDampingRatio = 0.54f,
                    releaseStiffness = 720f,
                    reducedMotion = reducedMotion
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                tint = Color.White,
                modifier = Modifier
                    .size(if (compact) 30.dp else 34.dp)
                    .graphicsLayer {
                        translationX = prevKick.value * 5.dp.toPx()
                    }
            )
        }

        Box(
            modifier = Modifier
                .size(primarySize)
                .playerPressBounce(
                    interactionSource = playInteraction,
                    pressedScale = 0.86f,
                    releaseDampingRatio = 0.54f,
                    releaseStiffness = 720f,
                    reducedMotion = reducedMotion
                )
                .graphicsLayer {
                    val s = if (reducedMotion) 1f else 0.88f + 0.12f * playSquash.value
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = playInteraction,
                    indication = null
                ) {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isBuffering,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        (fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.8f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f))
                    }
                },
                label = "PlayBufferState"
            ) { buffering ->
                if (buffering) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    OndaAnimatedPlayGlyph(
                        isPlaying = isPlaying,
                        reducedMotion = reducedMotion,
                        tint = Color.Black,
                        modifier = Modifier
                            .size(if (compact) 26.dp else 30.dp)
                            .semantics {
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            }
                            .graphicsLayer {
                                val s = if (reducedMotion) 1f else playSquash.value
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!reducedMotion) {
                    nextKickJob?.cancel()
                    nextKickJob = scope.launch {
                        nextKick.animateTo(1f, tween(80))
                        nextKick.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 460f))
                    }
                }
                onSkipNext()
            },
            interactionSource = nextInteraction,
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .playerPressBounce(
                    interactionSource = nextInteraction,
                    pressedScale = 0.86f,
                    releaseDampingRatio = 0.54f,
                    releaseStiffness = 720f,
                    reducedMotion = reducedMotion
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                tint = Color.White,
                modifier = Modifier
                    .size(if (compact) 30.dp else 34.dp)
                    .graphicsLayer {
                        translationX = nextKick.value * 5.dp.toPx()
                    }
            )
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!reducedMotion) {
                    repeatSpinJob?.cancel()
                    repeatSpinJob = scope.launch {
                        repeatSpin.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
                        repeatSpin.animateTo(0f, spring(dampingRatio = 0.40f, stiffness = 260f))
                    }
                }
                onToggleRepeat()
            },
            interactionSource = repeatInteraction,
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .playerPressBounce(
                    interactionSource = repeatInteraction,
                    pressedScale = 0.86f,
                    releaseDampingRatio = 0.54f,
                    releaseStiffness = 720f,
                    reducedMotion = reducedMotion
                )
        ) {
            val repeatIcon = if (playbackState.repeatMode == RepeatMode.ONE) {
                Icons.Rounded.RepeatOne
            } else {
                Icons.Rounded.Repeat
            }
            Icon(
                imageVector = repeatIcon,
                contentDescription = "Repeat mode",
                tint = repeatTint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        rotationZ = repeatSpin.value * 24f
                    }
            )
        }
    }
}

@Composable
internal fun OndaAnimatedPlayGlyph(
    isPlaying: Boolean,
    reducedMotion: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val morph = remember { Animatable(if (isPlaying) 1f else 0f) }
    val pathA = remember { Path() }
    val pathB = remember { Path() }

    LaunchedEffect(isPlaying, reducedMotion) {
        val target = if (isPlaying) 1f else 0f
        if (reducedMotion) {
            morph.snapTo(target)
        } else {
            morph.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 460f
                )
            )
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val p = morph.value.coerceIn(0f, 1f)
        val roundStroke = Stroke(
            width = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        if (p <= 0.01f) {
            pathA.reset()
            pathA.moveTo(w * 0.30f, h * 0.18f)
            pathA.lineTo(w * 0.82f, h * 0.50f)
            pathA.lineTo(w * 0.30f, h * 0.82f)
            pathA.close()
            drawPath(path = pathA, color = tint)
            drawPath(path = pathA, color = tint, style = roundStroke)
        } else {
            pathA.reset()
            pathA.moveTo(w * lerp(0.30f, 0.23f, p), h * lerp(0.18f, 0.20f, p))
            pathA.lineTo(w * lerp(0.56f, 0.42f, p), h * lerp(0.34f, 0.20f, p))
            pathA.lineTo(w * lerp(0.56f, 0.42f, p), h * lerp(0.66f, 0.80f, p))
            pathA.lineTo(w * lerp(0.30f, 0.23f, p), h * lerp(0.82f, 0.80f, p))
            pathA.close()

            pathB.reset()
            pathB.moveTo(w * lerp(0.56f, 0.58f, p), h * lerp(0.34f, 0.20f, p))
            pathB.lineTo(w * lerp(0.82f, 0.77f, p), h * lerp(0.50f, 0.20f, p))
            pathB.lineTo(w * lerp(0.82f, 0.77f, p), h * lerp(0.50f, 0.80f, p))
            pathB.lineTo(w * lerp(0.56f, 0.58f, p), h * lerp(0.66f, 0.80f, p))
            pathB.close()

            drawPath(path = pathA, color = tint)
            drawPath(path = pathA, color = tint, style = roundStroke)
            drawPath(path = pathB, color = tint)
            drawPath(path = pathB, color = tint, style = roundStroke)
        }
    }
}

@Composable
fun LikeActionButton(
    liked: Boolean,
    hapticsEnabled: Boolean,
    onToggleLike: () -> Unit,
    onButtonPositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    likeBurstState: OndaLikeBurstState? = null,
    likeSlot: OndaLikeSlot = OndaLikeSlot.PLAYER
) {
    val haptics = LocalHapticFeedback.current
    val reducedMotion = OndaTheme.reducedMotion
    val interactionSource = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (liked) Color(0xFFFF2D55) else Color(0xFF9E9E9E),
        animationSpec = if (reducedMotion) snap() else tween(200),
        label = "LikeTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (liked) 1.10f else 1f,
        animationSpec = if (reducedMotion) snap() else AppMotion.PopSpring,
        label = "LikeScale"
    )

    IconButton(
        onClick = {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggleLike()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .ondaLikeTarget(likeBurstState, likeSlot)
            .playerPressBounce(
                interactionSource = interactionSource,
                pressedScale = 0.85f,
                reducedMotion = reducedMotion
            )
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val center = Offset(
                    x = pos.x + coords.size.width * 0.5f,
                    y = pos.y + coords.size.height * 0.5f
                )
                onButtonPositioned(center)
            }
    ) {
        Icon(
            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (liked) "Remove from liked" else "Like track",
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

@Composable
fun DownloadActionButton(
    downloadItem: DownloadItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val interactionSource = remember { MutableInteractionSource() }
    val status = downloadItem?.status
    val isActiveDownload = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED
    var pulsePhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isActiveDownload, reducedMotion) {
        if (!isActiveDownload || reducedMotion) {
            pulsePhase = 0f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    pulsePhase = (pulsePhase + dt * 4.2f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    val icon = when (status) {
        DownloadStatus.COMPLETED -> Icons.Rounded.CheckCircle
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Icons.Rounded.Downloading
        else -> Icons.Rounded.Download
    }
    val tint = when (status) {
        DownloadStatus.COMPLETED -> Color.White
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Color.White
        DownloadStatus.FAILED -> GlassTokens.ErrorCoral
        null -> Color(0xFF9E9E9E)
    }

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .playerPressBounce(
                interactionSource = interactionSource,
                pressedScale = 0.85f,
                reducedMotion = reducedMotion
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Download track",
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    if (isActiveDownload && !reducedMotion) {
                        val wave = 0.5f + 0.5f * sin(pulsePhase)
                        val s = lerp(0.94f, 1.08f, wave)
                        scaleX = s
                        scaleY = s
                        alpha = lerp(0.72f, 1f, wave)
                        rotationZ = sin(pulsePhase * 0.5f) * 6f
                    }
                }
        )
    }
}

@Composable
private fun MarqueeFadeText(
    text: String,
    style: TextStyle,
    color: Color,
    active: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val textWidthPx = remember(text, style, textMeasurer) {
        textMeasurer.measure(AnnotatedString(text), style = style, maxLines = 1).size.width.toFloat()
    }
    val gapPx = with(density) { 40.dp.toPx() }
    val overflowPx = (textWidthPx - containerWidthPx).coerceAtLeast(0f)
    val shouldMarquee = active && !reducedMotion && containerWidthPx > 0f && overflowPx > 2f
    var scrollOffsetPx by remember(text, shouldMarquee) { mutableFloatStateOf(0f) }

    LaunchedEffect(text, shouldMarquee, textWidthPx, gapPx) {
        scrollOffsetPx = 0f
        if (!shouldMarquee) return@LaunchedEffect
        val cyclePx = textWidthPx + gapPx
        val speedPxPerSec = with(density) { 36.dp.toPx() }.coerceAtLeast(24f)
        while (true) {
            scrollOffsetPx = 0f
            delay(1_800L)
            var lastNanos = 0L
            while (scrollOffsetPx < cyclePx) {
                withFrameNanos { frameNanos ->
                    if (lastNanos != 0L) {
                        val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                        scrollOffsetPx = (scrollOffsetPx + speedPxPerSec * dt).coerceAtMost(cyclePx)
                    }
                    lastNanos = frameNanos
                }
            }
        }
    }

    if (reducedMotion || containerWidthPx <= 0f || overflowPx <= 2f) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
        )
    } else {
        val idleFadeBrush = remember {
            Brush.horizontalGradient(
                0.00f to Color.Black,
                0.88f to Color.Black,
                1.00f to Color.Transparent
            )
        }
        val scrollingFadeBrush = remember {
            Brush.horizontalGradient(
                0.00f to Color.Transparent,
                0.06f to Color.Black,
                0.90f to Color.Black,
                1.00f to Color.Transparent
            )
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = if (scrollOffsetPx > 1f) scrollingFadeBrush else idleFadeBrush,
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            val cyclePx = textWidthPx + gapPx
            Row(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
                        )
                        val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
                        layout(layoutWidth.coerceAtLeast(0), placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .graphicsLayer {
                        translationX = -(scrollOffsetPx % cyclePx)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(40.dp))
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun TrackMetaHeader(
    track: Track?,
    liked: Boolean,
    hapticsEnabled: Boolean,
    onToggleLike: () -> Unit,
    onLikePositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    downloadItem: DownloadItem? = null,
    onDownloadClick: (() -> Unit)? = null,
    queue: List<Track> = emptyList(),
    pagerState: PagerState? = null,
    likeBurstState: OndaLikeBurstState? = null,
    likeSlot: OndaLikeSlot = OndaLikeSlot.PLAYER
) {
    val reducedMotion = OndaTheme.reducedMotion
    val titleCascade = remember { Animatable(1f) }
    val artistCascade = remember { Animatable(1f) }
    var isPagerUserDragging by remember(pagerState) { mutableStateOf(false) }
    var lastUserDragEndMs by remember(pagerState) { mutableLongStateOf(0L) }
    val currentTrack = track
    var initializedTrackId by remember { mutableStateOf(currentTrack?.id) }

    if (pagerState != null) {
        LaunchedEffect(pagerState) {
            pagerState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        isPagerUserDragging = true
                        lastUserDragEndMs = System.currentTimeMillis()
                    }
                    is DragInteraction.Stop, is DragInteraction.Cancel -> {
                        isPagerUserDragging = false
                        lastUserDragEndMs = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    val trackId = currentTrack?.id
    LaunchedEffect(trackId, reducedMotion) {
        if (trackId == initializedTrackId) return@LaunchedEffect
        initializedTrackId = trackId
        val draggedRecently = pagerState != null && (
            isPagerUserDragging || (System.currentTimeMillis() - lastUserDragEndMs) < 850L
            )
        if (reducedMotion || draggedRecently) {
            titleCascade.snapTo(1f)
            artistCascade.snapTo(1f)
            return@LaunchedEffect
        }
        titleCascade.snapTo(0f)
        artistCascade.snapTo(0f)
        launch {
            titleCascade.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 460f)
            )
        }
        launch {
            delay(45L)
            artistCascade.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 460f)
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pagerState != null && queue.isNotEmpty()) {
            val visibleSlotPages by remember(pagerState, queue) {
                derivedStateOf {
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                for (slotPage in visibleSlotPages) {
                    val slotTrack = queue.getOrNull(slotPage) ?: continue
                    val isCurrentSlot = slotPage == pagerState.currentPage
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
                            MarqueeFadeText(
                                text = slotTrack.title,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                active = isCurrentSlot && !pagerState.isScrollInProgress,
                                reducedMotion = reducedMotion,
                                modifier = Modifier.graphicsLayer {
                                    if (isCurrentSlot && !reducedMotion) {
                                        val p = titleCascade.value
                                        translationY = (1f - p) * 14.dp.toPx()
                                        val s = lerp(0.96f, 1f, p)
                                        scaleX = s
                                        scaleY = s
                                        alpha = p
                                    }
                                }
                            )
                            MarqueeFadeText(
                                text = slotTrack.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9E9E9E),
                                active = isCurrentSlot && !pagerState.isScrollInProgress,
                                reducedMotion = reducedMotion,
                                modifier = Modifier.graphicsLayer {
                                    if (isCurrentSlot && !reducedMotion) {
                                        val p = artistCascade.value
                                        translationY = (1f - p) * 14.dp.toPx()
                                        val s = lerp(0.96f, 1f, p)
                                        scaleX = s
                                        scaleY = s
                                        alpha = p
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MarqueeFadeText(
                    text = currentTrack?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    active = currentTrack != null,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.graphicsLayer {
                        if (!reducedMotion) {
                            val p = titleCascade.value
                            translationY = (1f - p) * 14.dp.toPx()
                            val s = lerp(0.96f, 1f, p)
                            scaleX = s
                            scaleY = s
                            alpha = p
                        }
                    }
                )
                MarqueeFadeText(
                    text = currentTrack?.artist ?: "Select a song to start listening",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9E9E9E),
                    active = currentTrack != null,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.graphicsLayer {
                        if (!reducedMotion) {
                            val p = artistCascade.value
                            translationY = (1f - p) * 14.dp.toPx()
                            val s = lerp(0.96f, 1f, p)
                            scaleX = s
                            scaleY = s
                            alpha = p
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (onDownloadClick != null) {
            DownloadActionButton(
                downloadItem = downloadItem,
                onClick = onDownloadClick
            )
        }
        LikeActionButton(
            liked = liked,
            hapticsEnabled = hapticsEnabled,
            onToggleLike = onToggleLike,
            onButtonPositioned = onLikePositioned,
            likeBurstState = likeBurstState,
            likeSlot = likeSlot
        )
    }
}

@Composable
fun GlassPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val reducedMotion = OndaTheme.reducedMotion
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .playerPressBounce(
                interactionSource = interactionSource,
                pressedScale = 0.94f,
                reducedMotion = reducedMotion
            )
            .clip(shape)
            .background(if (active) Color(0xFF242424) else Color(0xFF141414))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leadingIcon?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Color.White else Color(0xFF9E9E9E),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
