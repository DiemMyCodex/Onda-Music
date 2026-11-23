package com.onda.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
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
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.WavySeekBar
import com.onda.music.core.design.press3D
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.Track
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.util.Locale
import kotlin.math.abs

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
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val palette = OndaTheme.palette
    var hasFailed by remember(artworkUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.tertiary.copy(alpha = 0.85f),
                        GlassTokens.ObsidianSurface,
                        palette.deepShadow
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = palette.secondary.copy(alpha = 0.55f),
            modifier = Modifier.size(36.dp)
        )
        if (!artworkUrl.isNullOrBlank() && !hasFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                onError = { hasFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

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
    enablePress3D: Boolean = true
) {
    val displayTracks = remember(queue, currentTrack) {
        when {
            queue.isNotEmpty() -> queue
            currentTrack != null -> listOf(currentTrack)
            else -> emptyList()
        }
    }

    if (displayTracks.isEmpty()) {
        TrackArtworkImage(
            artworkUrl = null,
            contentDescription = "No track artwork",
            shape = artworkShape,
            modifier = modifier
        )
        return
    }

    val safeIndex = currentIndex.coerceIn(0, displayTracks.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { displayTracks.size }
    )

    LaunchedEffect(safeIndex, displayTracks.size) {
        if (pagerState.currentPage != safeIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    LaunchedEffect(pagerState, displayTracks.size) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .distinctUntilChanged()
            .collect { (settled, _) ->
                if (settled != safeIndex && settled in displayTracks.indices) {
                    onSelectIndex(settled)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        key = { page -> "${displayTracks[page].id}_$page" }
    ) { page ->
        val track = displayTracks[page]
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        val absOffset = abs(pageOffset).coerceIn(0f, 1f)
        val scale = lerp(1f, 0.90f, absOffset)
        val alpha = lerp(1f, 0.58f, absOffset)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .then(
                    if (enablePress3D && page == pagerState.currentPage) {
                        Modifier.press3D()
                    } else {
                        Modifier
                    }
                )
                .border(
                    width = GlassTokens.BorderStandard,
                    brush = GlassTokens.specularBorderBrush(
                        intensity = 1.15f,
                        accentTint = OndaTheme.palette.secondary
                    ),
                    shape = artworkShape
                ),
            contentAlignment = Alignment.Center
        ) {
            TrackArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                shape = artworkShape,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PlayerSeekSection(
    playbackState: PlaybackState,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = OndaTheme.colors.accent
) {
    val durationMs = playbackState.durationMs.coerceAtLeast(0L)
    val positionMs = playbackState.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    val bufferedMs = playbackState.bufferedPositionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedProgress = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val isPlaying = playbackState.status == PlaybackStatus.PLAYING

    var scrubbingFraction by remember { mutableStateOf<Float?>(null) }
    val shownProgress = scrubbingFraction ?: progress
    val shownPositionMs = if (scrubbingFraction != null && durationMs > 0L) {
        (durationMs * shownProgress).toLong()
    } else {
        positionMs
    }

    Column(modifier = modifier.fillMaxWidth()) {
        WavySeekBar(
            progress = shownProgress,
            bufferedProgress = bufferedProgress,
            isPlaying = isPlaying,
            activeColor = accentColor,
            onSeek = { fraction ->
                scrubbingFraction = fraction
            },
            onSeekFinished = {
                scrubbingFraction?.let(onSeekToFraction)
                scrubbingFraction = null
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OdometerText(
                text = formatDurationMs(shownPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = GlassTokens.TextSecondary
            )
            Text(
                text = formatDurationMs(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = GlassTokens.TextTertiary
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
    val palette = OndaTheme.palette
    val isPlaying = playbackState.status == PlaybackStatus.PLAYING
    val isBuffering = playbackState.status == PlaybackStatus.BUFFERING
    val primarySize = if (compact) 56.dp else 68.dp

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
            modifier = Modifier.defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (playbackState.shuffleEnabled) palette.primary else GlassTokens.TextTertiary,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSkipPrevious()
            },
            modifier = Modifier.defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                tint = GlassTokens.TextPrimary,
                modifier = Modifier.size(if (compact) 30.dp else 34.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(primarySize)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.primary, palette.secondary)
                    )
                )
                .border(
                    width = GlassTokens.BorderStandard,
                    color = GlassTokens.SpecularBright,
                    shape = CircleShape
                )
                .press3D(maxTiltDegrees = 5f, pressedScale = 0.94f)
                .clickable {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = palette.onAccent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = palette.onAccent,
                    modifier = Modifier.size(if (compact) 30.dp else 36.dp)
                )
            }
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSkipNext()
            },
            modifier = Modifier.defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                tint = GlassTokens.TextPrimary,
                modifier = Modifier.size(if (compact) 30.dp else 34.dp)
            )
        }

        IconButton(
            onClick = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleRepeat()
            },
            modifier = Modifier.defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
        ) {
            val repeatIcon = if (playbackState.repeatMode == RepeatMode.ONE) {
                Icons.Rounded.RepeatOne
            } else {
                Icons.Rounded.Repeat
            }
            Icon(
                imageVector = repeatIcon,
                contentDescription = "Repeat mode",
                tint = if (playbackState.repeatMode != RepeatMode.OFF) palette.primary else GlassTokens.TextTertiary,
                modifier = Modifier.size(22.dp)
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
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (liked) GlassTokens.ErrorCoral else GlassTokens.TextSecondary,
        label = "LikeTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (liked) 1.12f else 1f,
        animationSpec = AppMotion.PopSpring,
        label = "LikeScale"
    )

    IconButton(
        onClick = {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggleLike()
        },
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
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
    val palette = OndaTheme.palette
    val icon = when (downloadItem?.status) {
        DownloadStatus.COMPLETED -> Icons.Rounded.CheckCircle
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Icons.Rounded.Downloading
        else -> Icons.Rounded.Download
    }
    val tint = when (downloadItem?.status) {
        DownloadStatus.COMPLETED -> GlassTokens.SuccessMint
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> palette.primary
        DownloadStatus.FAILED -> GlassTokens.ErrorCoral
        null -> GlassTokens.TextSecondary
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
    onDownloadClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = GlassTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track?.artist ?: "Select a song to start listening",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            onButtonPositioned = onLikePositioned
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
    val palette = OndaTheme.palette
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .smokedGlass(
                shape = shape,
                elevation = GlassElevation.SUBTLE,
                accentTint = if (active) palette.primary else palette.secondary
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leadingIcon?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) palette.primary else GlassTokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
