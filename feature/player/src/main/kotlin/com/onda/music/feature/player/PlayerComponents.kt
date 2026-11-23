package com.onda.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.ArtworkUrlResolver
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.press3D
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

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
    onArtworkDoubleTap: ((Offset) -> Unit)? = null
) {
    val reducedMotion = OndaTheme.reducedMotion
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
            shape = artworkShape,
            isPlaying = isPlaying,
            enablePauseTransform = true,
            pauseScale = if (enablePauseScale) pauseScale else 1f,
            pauseSaturation = pauseSaturation,
            modifier = modifier
                .onGloballyPositioned { emptyCoords[0] = it }
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

    val safeIndex = currentIndex.coerceIn(0, displayTracks.lastIndex)
    val resolvedPagerState = pagerState ?: rememberPagerState(
        initialPage = safeIndex,
        pageCount = { displayTracks.size }
    )

    LaunchedEffect(safeIndex, displayTracks.size, resolvedPagerState) {
        if (resolvedPagerState.currentPage != safeIndex && !resolvedPagerState.isScrollInProgress) {
            resolvedPagerState.animateScrollToPage(safeIndex)
        }
    }

    LaunchedEffect(resolvedPagerState, displayTracks.size) {
        snapshotFlow { resolvedPagerState.settledPage to resolvedPagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .distinctUntilChanged()
            .collect { (settled, _) ->
                if (settled != safeIndex && settled in displayTracks.indices) {
                    onSelectIndex(settled)
                }
            }
    }

    val hasCardPadding = pageSpacing > 0.dp || contentPadding != PaddingValues(0.dp)

    HorizontalPager(
        state = resolvedPagerState,
        modifier = modifier,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
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
                    shape = artworkShape
                    clip = true
                }
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
                shape = artworkShape,
                isPlaying = isPlaying,
                enablePauseTransform = true,
                pauseScale = if (enablePauseScale) pauseScale else 1f,
                pauseSaturation = pauseSaturation,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (!reducedMotion && parallaxFactor > 0f) {
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
        }
    }
}

@Composable
fun PlayerSeekSection(
    playbackState: PlaybackState,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White
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

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                .height(DockMetrics.MinTapTarget)
                .onSizeChanged { fillFollower.widthPx = it.width.toFloat() }
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
                        onSeekToFraction(fraction)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isGestureHeld = true
                            val pad = with(density) { 8.dp.toPx() }
                            val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                            scrubbingFraction = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val pad = with(density) { 8.dp.toPx() }
                            val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                            val raw = (change.position.x - pad) / usable
                            scrubbingFraction = raw.coerceIn(0f, 1f)

                            if (!reducedMotion) {
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
                            val commit = scrubbingFraction
                            scrubbingFraction = null
                            if (commit != null) {
                                pendingSeekFraction = commit
                                onSeekToFraction(commit)
                            }
                            scope.launch {
                                if (reducedMotion) {
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
                            scrubbingFraction = null
                            scope.launch {
                                if (reducedMotion) {
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

    val prevKick = remember { Animatable(0f) }
    val nextKick = remember { Animatable(0f) }
    val repeatSpin = remember { Animatable(0f) }

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
                tint = if (playbackState.shuffleEnabled) Color.White else Color(0xFF9E9E9E),
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!reducedMotion) {
                    scope.launch {
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
            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Icon(
                    imageVector = if (displayedPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(if (compact) 30.dp else 36.dp)
                        .graphicsLayer {
                            val s = if (reducedMotion) 1f else playSquash.value
                            scaleX = s
                            scaleY = s
                        }
                )
            }
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!reducedMotion) {
                    scope.launch {
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
                    scope.launch {
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
                tint = if (playbackState.repeatMode != RepeatMode.OFF) Color.White else Color(0xFF9E9E9E),
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
    val icon = when (downloadItem?.status) {
        DownloadStatus.COMPLETED -> Icons.Rounded.CheckCircle
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Icons.Rounded.Downloading
        else -> Icons.Rounded.Download
    }
    val tint = when (downloadItem?.status) {
        DownloadStatus.COMPLETED -> Color.White
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Color.White
        DownloadStatus.FAILED -> GlassTokens.ErrorCoral
        null -> Color(0xFF9E9E9E)
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Download track",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
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
                                text = slotTrack.title,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = slotTrack.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9E9E9E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "Select a song to start listening",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9E9E9E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
