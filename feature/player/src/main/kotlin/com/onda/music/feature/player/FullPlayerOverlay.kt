package com.onda.music.feature.player

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.onda.music.core.design.AgslMeshBackground
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.LikeBurstOverlay
import com.onda.music.core.design.LocalOndaColors
import com.onda.music.core.design.LocalOndaPalette
import com.onda.music.core.design.MorphingCornerClipShape
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.rememberLikeBurstState
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.PlayerStyle
import com.onda.music.feature.player.styles.BedsidePlayerStyle
import com.onda.music.feature.player.styles.BedsideTilesPlayerStyle
import com.onda.music.feature.player.styles.ClassicPlayerStyle
import com.onda.music.feature.player.styles.FlagshipPlayerStyle
import com.onda.music.feature.player.styles.IpodPlayerStyle
import com.onda.music.feature.player.styles.LyricsStagePlayerStyle
import com.onda.music.feature.player.styles.PlayerStageContext
import com.onda.music.feature.player.styles.PosterPlayerStyle
import com.onda.music.feature.player.styles.TimelinePlayerStyle
import com.onda.music.feature.player.styles.TurntablePlayerStyle
import com.onda.music.feature.player.styles.VideoPlayerStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

@Composable
fun FullPlayerOverlay(
    morphFraction: Float,
    onMorphFractionChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val clampedMorph = morphFraction.coerceIn(0f, 1f)
    if (clampedMorph <= 0.005f) return

    val playbackState by viewModel.playbackState.collectAsState()
    val settings by viewModel.userSettings.collectAsState()
    val palette by viewModel.artworkPalette.collectAsState()
    val lyrics by viewModel.lyricsDocument.collectAsState()
    val lyricsOffsetMs by viewModel.currentTrackLyricsOffsetMs.collectAsState()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsState()
    val lyricsError by viewModel.lyricsError.collectAsState()
    val liked by viewModel.currentTrackLiked.collectAsState()
    val downloadItem by viewModel.currentDownload.collectAsState()
    val activePlayer by viewModel.activePlayerFlow.collectAsState()
    val likeBurstTrigger by viewModel.likeBurstTrigger.collectAsState()

    var showInlineLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showStylePickerSheet by remember { mutableStateOf(false) }

    var containerHeightPx by remember { mutableFloatStateOf(1600f) }
    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var likeCenterInWindow by remember { mutableStateOf<Offset?>(null) }
    val likeBurstState = rememberLikeBurstState()

    LaunchedEffect(likeBurstTrigger) {
        if (likeBurstTrigger > 0L) {
            val relativeOrigin = likeCenterInWindow?.let { it - overlayOriginInWindow }
            likeBurstState.trigger(
                origin = relativeOrigin,
                primaryColor = palette.primary,
                secondaryColor = GlassTokens.ErrorCoral
            )
        }
    }

    val anySheetOpen = showQueueSheet || showSleepTimerSheet || showStylePickerSheet
    PredictiveBackHandler(enabled = clampedMorph > 0.05f && !anySheetOpen) { backEvents: Flow<BackEventCompat> ->
        try {
            backEvents.collect { event ->
                val targetMorph = (1f - event.progress * 0.85f).coerceIn(0f, 1f)
                onMorphFractionChange(targetMorph)
            }
            onMorphFractionChange(0f)
            onCollapse()
        } catch (e: CancellationException) {
            onMorphFractionChange(1f)
            throw e
        }
    }

    val baseColors = OndaTheme.colors
    val dynamicColors = remember(baseColors, palette) {
        baseColors.copy(
            accent = palette.primary,
            accentSoft = palette.secondary
        )
    }

    val morphShape = remember {
        MorphingCornerClipShape(
            startRadius = GlassTokens.RadiusLg,
            endRadius = 0.dp,
            fractionProvider = { clampedMorph }
        )
    }

    val stageContext = remember(
        playbackState,
        settings,
        liked,
        downloadItem,
        lyrics,
        lyricsOffsetMs,
        isLyricsLoading,
        lyricsError,
        activePlayer,
        showInlineLyrics
    ) {
        PlayerStageContext(
            playbackState = playbackState,
            settings = settings,
            liked = liked,
            downloadItem = downloadItem,
            lyrics = lyrics,
            lyricsOffsetMs = lyricsOffsetMs,
            isLyricsLoading = isLyricsLoading,
            lyricsError = lyricsError,
            activePlayer = activePlayer,
            showInlineLyrics = showInlineLyrics,
            onToggleInlineLyrics = { showInlineLyrics = !showInlineLyrics },
            onTogglePlayPause = viewModel::togglePlayPause,
            onSeekToMs = viewModel::seekTo,
            onSeekToFraction = viewModel::seekToFraction,
            onSkipNext = viewModel::skipNext,
            onSkipPrevious = viewModel::skipPrevious,
            onSelectQueueIndex = viewModel::skipToQueueIndex,
            onToggleLike = viewModel::toggleLike,
            onLikePositioned = { center -> likeCenterInWindow = center },
            onDownloadClick = viewModel::downloadCurrentTrack,
            onToggleShuffle = viewModel::toggleShuffle,
            onToggleRepeat = viewModel::toggleRepeatMode,
            onOpenQueue = { showQueueSheet = true },
            onOpenSleepTimer = { showSleepTimerSheet = true },
            onOpenStylePicker = { showStylePickerSheet = true },
            onRefreshLyrics = viewModel::refreshLyrics,
            onAdjustLyricsOffset = viewModel::adjustLyricsOffset,
            onAttachSurfaceView = viewModel::attachVideoSurfaceView,
            onAttachTextureView = viewModel::attachVideoTextureView
        )
    }

    CompositionLocalProvider(
        LocalOndaPalette provides palette,
        LocalOndaColors provides dynamicColors
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { containerHeightPx = it.height.coerceAtLeast(1).toFloat() }
                .onGloballyPositioned { coords ->
                    overlayOriginInWindow = coords.positionInWindow()
                }
                .graphicsLayer {
                    translationY = (1f - clampedMorph) * containerHeightPx
                    alpha = (clampedMorph * 2.2f).coerceIn(0f, 1f)
                    shape = morphShape
                    clip = true
                }
        ) {
            AgslMeshBackground(
                palette = palette,
                animated = settings.dynamicMeshEnabled && playbackState.status == PlaybackStatus.PLAYING,
                dimAlpha = if (settings.playerStyle == PlayerStyle.BEDSIDE) 0.85f else 0.52f,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                FullPlayerTopHeader(
                    activeStyle = settings.playerStyle,
                    sleepTimerRemainingMs = playbackState.sleepTimerRemainingMs,
                    morphFraction = clampedMorph,
                    containerHeightPx = containerHeightPx,
                    onMorphFractionChange = onMorphFractionChange,
                    onCollapse = onCollapse,
                    onOpenStylePicker = { showStylePickerSheet = true },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onOpenSettings = onOpenSettings
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = settings.playerStyle,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "PlayerStyleStage"
                    ) { style ->
                        when (style) {
                            PlayerStyle.FLAGSHIP -> FlagshipPlayerStyle(ctx = stageContext)
                            PlayerStyle.CLASSIC -> ClassicPlayerStyle(ctx = stageContext)
                            PlayerStyle.POSTER -> PosterPlayerStyle(ctx = stageContext)
                            PlayerStyle.TURNTABLE -> TurntablePlayerStyle(ctx = stageContext)
                            PlayerStyle.VIDEO -> VideoPlayerStyle(ctx = stageContext)
                            PlayerStyle.IPOD -> IpodPlayerStyle(ctx = stageContext)
                            PlayerStyle.TIMELINE -> TimelinePlayerStyle(ctx = stageContext)
                            PlayerStyle.LYRICS -> LyricsStagePlayerStyle(ctx = stageContext)
                            PlayerStyle.BEDSIDE -> BedsidePlayerStyle(ctx = stageContext)
                            PlayerStyle.BEDSIDE_TILES -> BedsideTilesPlayerStyle(ctx = stageContext)
                        }
                    }
                }
            }

            LikeBurstOverlay(
                state = likeBurstState,
                modifier = Modifier.fillMaxSize()
            )

            if (showQueueSheet) {
                QueueSheet(
                    queue = playbackState.queue,
                    currentIndex = playbackState.currentIndex,
                    hapticsEnabled = settings.hapticsEnabled,
                    onSelectIndex = viewModel::skipToQueueIndex,
                    onMoveItem = viewModel::moveQueueItem,
                    onRemoveItem = viewModel::removeQueueItem,
                    onDismiss = { showQueueSheet = false }
                )
            }

            if (showSleepTimerSheet) {
                SleepTimerSheet(
                    remainingMs = playbackState.sleepTimerRemainingMs,
                    onSelectDurationMs = viewModel::startSleepTimer,
                    onCancelTimer = viewModel::cancelSleepTimer,
                    onDismiss = { showSleepTimerSheet = false }
                )
            }

            if (showStylePickerSheet) {
                PlayerStyleSelectorSheet(
                    activeStyle = settings.playerStyle,
                    onSelectStyle = viewModel::switchPlayerStyle,
                    onDismiss = { showStylePickerSheet = false }
                )
            }
        }
    }
}

@Composable
private fun FullPlayerTopHeader(
    activeStyle: PlayerStyle,
    sleepTimerRemainingMs: Long?,
    morphFraction: Float,
    containerHeightPx: Float,
    onMorphFractionChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onOpenStylePicker: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = OndaTheme.palette
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(morphFraction, containerHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        val nextMorph = (1f - (dragAccumulator / (containerHeightPx * 0.65f))).coerceIn(0f, 1f)
                        onMorphFractionChange(nextMorph)
                    },
                    onDragEnd = {
                        val current = (1f - (dragAccumulator / (containerHeightPx * 0.65f))).coerceIn(0f, 1f)
                        if (current < 0.72f) {
                            onMorphFractionChange(0f)
                            onCollapse()
                        } else {
                            onMorphFractionChange(1f)
                        }
                        dragAccumulator = 0f
                    },
                    onDragCancel = {
                        onMorphFractionChange(1f)
                        dragAccumulator = 0f
                    }
                )
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, bottom = 4.dp)
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                .background(GlassTokens.SpecularBright.copy(alpha = 0.45f))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    onMorphFractionChange(0f)
                    onCollapse()
                },
                modifier = Modifier.defaultMinSize(
                    minWidth = DockMetrics.MinTapTarget,
                    minHeight = DockMetrics.MinTapTarget
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse player",
                    tint = GlassTokens.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    .smokedGlass(
                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                        elevation = GlassElevation.SUBTLE,
                        accentTint = palette.primary
                    )
                    .clickable(onClick = onOpenStylePicker)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Style,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = activeStyle.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = GlassTokens.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = GlassTokens.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenSleepTimer,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (sleepTimerRemainingMs != null) palette.primary else GlassTokens.TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = GlassTokens.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = GlassTokens.TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}
