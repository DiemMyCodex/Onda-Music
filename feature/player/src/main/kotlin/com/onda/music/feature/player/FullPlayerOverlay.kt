package com.onda.music.feature.player

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.onda.music.core.design.AgslMeshBackground
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.LocalOndaColors
import com.onda.music.core.design.LocalOndaPalette
import com.onda.music.core.design.LocalOndaToastState
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.StudioBackdropMode
import com.onda.music.core.model.Track
import com.onda.music.feature.player.queue.OndaQueueOverlay
import com.onda.music.feature.player.share.OndaShareSheet
import com.onda.music.feature.player.sheets.OndaLyricsOffsetSheet
import com.onda.music.feature.player.sheets.OndaPlaybackSpeedSheet
import com.onda.music.feature.player.sheets.OndaPlayerActionSheet
import com.onda.music.feature.player.sheets.OndaSleepTimerSheet
import com.onda.music.feature.player.sheets.OndaSongMenuSheet
import com.onda.music.feature.player.sheets.OndaStreamTelemetrySheet
import com.onda.music.feature.player.studio.OndaStudioOverlay
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
fun FullPlayerOverlay(
    morphFractionProvider: () -> Float,
    onMorphFractionChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val isPlayerVisible by remember(morphFractionProvider) {
        derivedStateOf { morphFractionProvider() > 0.005f }
    }
    if (!isPlayerVisible) return

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
    val eqConfig by viewModel.eqConfig.collectAsState()

    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val toastState = LocalOndaToastState.current

    var showInlineLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showStudioOverlay by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var shareInitialLineIndex by remember { mutableStateOf<Int?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showStreamInfoSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showLyricsOffsetSheet by remember { mutableStateOf(false) }
    var songMenuTarget by remember { mutableStateOf<Pair<Track, Int?>?>(null) }

    var containerHeightPx by remember { mutableFloatStateOf(1600f) }
    var likeCenterInWindow by remember { mutableStateOf<Offset?>(null) }
    val likeBurstState = remember { OndaLikeBurstState() }

    LaunchedEffect(settings.playerStyle) {
        likeBurstState.slotProvider = {
            when (settings.playerStyle) {
                PlayerStyle.POSTER -> OndaLikeSlot.POSTER
                PlayerStyle.TIMELINE -> OndaLikeSlot.TIMELINE
                PlayerStyle.LYRICS -> OndaLikeSlot.LYRICS_STAGE
                PlayerStyle.BEDSIDE_TILES -> OndaLikeSlot.BEDSIDE_TILES
                else -> OndaLikeSlot.PLAYER
            }
        }
    }

    val currentLiked by rememberUpdatedState(liked)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)

    val onLikeFromButton = remember(likeBurstState, scope, viewModel) {
        {
            likeBurstState.likeFromButton(
                slot = likeBurstState.slotProvider(),
                currentlyLiked = currentLiked,
                reducedMotion = currentReducedMotion,
                scope = scope,
                onToggleLike = viewModel::toggleLike
            )
        }
    }

    val onLikeFromDoubleTap = remember(likeBurstState, scope, viewModel) {
        { windowPosition: Offset ->
            likeBurstState.likeFromDoubleTap(
                windowPosition = windowPosition,
                currentlyLiked = currentLiked,
                reducedMotion = currentReducedMotion,
                scope = scope,
                onToggleLike = viewModel::toggleLike
            )
        }
    }

    val anySheetOpen = showQueueSheet ||
        showSleepTimerSheet ||
        showStudioOverlay ||
        showShareSheet ||
        showActionSheet ||
        showStreamInfoSheet ||
        showSpeedSheet ||
        showLyricsOffsetSheet ||
        songMenuTarget != null
    val canPredictiveBack by remember(morphFractionProvider) {
        derivedStateOf { morphFractionProvider() > 0.05f }
    }
    PredictiveBackHandler(enabled = canPredictiveBack && !anySheetOpen) { backEvents: Flow<BackEventCompat> ->
        MorphGestureArbitrator.claim()
        try {
            backEvents.collect { event ->
                val targetMorph = (1f - event.progress * 0.85f).coerceIn(0f, 1f)
                onMorphFractionChange(targetMorph)
            }
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

    val isPosterStyle = settings.playerStyle == PlayerStyle.POSTER

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
        showInlineLyrics,
        likeBurstState,
        onLikeFromButton,
        onLikeFromDoubleTap,
        eqConfig
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
            onOpenStylePicker = { showStudioOverlay = true },
            onRefreshLyrics = viewModel::refreshLyrics,
            onAdjustLyricsOffset = viewModel::adjustLyricsOffset,
            onAttachSurfaceView = viewModel::attachVideoSurfaceView,
            onAttachTextureView = viewModel::attachVideoTextureView,
            onOpenOverflowMenu = { showActionSheet = true },
            likeBurstState = likeBurstState,
            onLikeFromButton = onLikeFromButton,
            onLikeFromDoubleTap = onLikeFromDoubleTap,
            eqConfig = eqConfig,
            onOpenStudio = { showStudioOverlay = true },
            onOpenShare = { lineIndex ->
                shareInitialLineIndex = lineIndex
                showShareSheet = true
            },
            onOpenSpeedSheet = { showSpeedSheet = true },
            onOpenStreamInfo = { showStreamInfoSheet = true },
            onOpenLyricsOffset = { showLyricsOffsetSheet = true },
            onOpenSongMenu = { targetTrack, queueIdx ->
                songMenuTarget = targetTrack to queueIdx
            },
            onPlayNext = { targetTrack ->
                viewModel.playNextTrack(targetTrack)
                toastState?.show(
                    title = "Playing next",
                    subtitle = targetTrack.title,
                    artworkUrl = targetTrack.artworkUrl
                )
            },
            onAddToQueue = { targetTrack ->
                viewModel.addTrackToQueue(targetTrack)
                toastState?.show(
                    title = "Added to queue",
                    subtitle = targetTrack.title,
                    artworkUrl = targetTrack.artworkUrl
                )
            },
            onUpdateStudioConfig = viewModel::updateStudioConfig
        )
    }

    val collapseTravelOffsetPx = with(density) { 64.dp.toPx() }

    CompositionLocalProvider(
        LocalOndaPalette provides palette,
        LocalOndaColors provides dynamicColors
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { containerHeightPx = it.height.coerceAtLeast(1).toFloat() }
                .graphicsLayer {
                    val morph = morphFractionProvider().coerceIn(0f, 1f)
                    val travelRange = (containerHeightPx - collapseTravelOffsetPx).coerceAtLeast(1f)
                    translationY = (1f - morph) * travelRange
                    alpha = (morph * 2.4f).coerceIn(0f, 1f)
                    shape = AppMotion.morphCornerShape(GlassTokens.RadiusLg, 0.dp, morph)
                    clip = morph < 0.99f
                }
                .background(Color(0xFF000000))
                .verticalCollapseDrag(
                    enabled = !anySheetOpen,
                    morphFractionProvider = morphFractionProvider,
                    containerHeightPx = containerHeightPx,
                    reducedMotion = reducedMotion,
                    onMorphFractionChange = onMorphFractionChange,
                    onCollapse = onCollapse
                )
        ) {
            OndaPlayerBackdropLayer(
                mode = settings.studioConfig.backdropMode,
                palette = palette,
                dynamicMeshEnabled = settings.dynamicMeshEnabled,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val morph = morphFractionProvider().coerceIn(0f, 1f)
                        if (!reducedMotion) {
                            val backdropScale = lerp(1.06f, 1f, morph)
                            scaleX = backdropScale
                            scaleY = backdropScale
                        }
                        alpha = ((morph - 0.12f) / 0.56f).coerceIn(0f, 1f)
                    }
            ) {
                if (isPosterStyle) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PosterPlayerStyle(
                            ctx = stageContext,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 10.dp)
                                .width(42.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0x38FFFFFF))
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(top = 6.dp, start = 12.dp)
                                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                .clip(SmoothCornerShape(999.dp))
                                .clickable { showStudioOverlay = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(SmoothCornerShape(999.dp))
                                    .background(Color(0x24FFFFFF))
                                    .border(0.75.dp, Color(0x26FFFFFF), SmoothCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                OndaMonogramMark(
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White,
                                    filled = false
                                )
                                Text(
                                    text = "Studio",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 6.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showActionSheet = true },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = DockMetrics.MinTapTarget,
                                    minHeight = DockMetrics.MinTapTarget
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x24FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Player actions",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    MorphGestureArbitrator.claim()
                                    onCollapse()
                                },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = DockMetrics.MinTapTarget,
                                    minHeight = DockMetrics.MinTapTarget
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x24FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .drawWithCache {
                                                val chevronPath = Path().apply {
                                                    moveTo(size.width * 0.18f, size.height * 0.34f)
                                                    lineTo(size.width * 0.50f, size.height * 0.68f)
                                                    lineTo(size.width * 0.82f, size.height * 0.34f)
                                                }
                                                val chevronStroke = Stroke(
                                                    width = 1.85.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                                onDrawBehind {
                                                    drawPath(
                                                        path = chevronPath,
                                                        color = Color.White,
                                                        style = chevronStroke
                                                    )
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        FullPlayerTopHeader(
                            activeStyle = settings.playerStyle,
                            playbackSpeed = playbackState.playbackSpeed,
                            playbackPitch = playbackState.playbackPitch,
                            sleepTimerActive = playbackState.sleepTimerRemainingMs != null || playbackState.sleepTimerEndOfTrack,
                            onCollapse = {
                                MorphGestureArbitrator.claim()
                                onCollapse()
                            },
                            onOpenStudio = { showStudioOverlay = true },
                            onOpenSpeedSheet = { showSpeedSheet = true },
                            onOpenQueue = { showQueueSheet = true },
                            onOpenSleepTimer = { showSleepTimerSheet = true },
                            onOpenActionSheet = { showActionSheet = true }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            AnimatedContent(
                                targetState = settings.playerStyle,
                                transitionSpec = {
                                    if (reducedMotion) {
                                        fadeIn(tween(100)) togetherWith fadeOut(tween(90))
                                    } else {
                                        (fadeIn(tween(220)) + scaleIn(
                                            animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
                                            initialScale = 0.96f
                                        )) togetherWith (fadeOut(tween(150)) + scaleOut(
                                            animationSpec = tween(150),
                                            targetScale = 1.03f
                                        ))
                                    }
                                },
                                label = "PlayerStyleStage"
                            ) { style ->
                                OndaPlayerStyleStage(
                                    style = style,
                                    ctx = stageContext
                                )
                            }
                        }
                    }
                }
            }

            OndaLikeBurstOverlay(
                state = likeBurstState,
                modifier = Modifier.fillMaxSize()
            )

            if (showQueueSheet) {
                val activeStreamLabel = playbackState.activeStream?.let { stream ->
                    val codecText = stream.codec.uppercase(Locale.US)
                    if (stream.bitrateKbps > 0) {
                        "$codecText ${stream.bitrateKbps}k"
                    } else {
                        "$codecText · ${stream.qualityLabel}"
                    }
                }
                OndaQueueOverlay(
                    queue = playbackState.queue,
                    currentIndex = playbackState.currentIndex,
                    isPlaying = playbackState.status == com.onda.music.core.model.PlaybackStatus.PLAYING,
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    activeStreamLabel = activeStreamLabel,
                    hapticsEnabled = settings.hapticsEnabled,
                    onSelectIndex = viewModel::skipToQueueIndex,
                    onMoveItem = viewModel::moveQueueItem,
                    onRemoveItem = viewModel::removeQueueItem,
                    onPlayNext = { targetTrack ->
                        val insertTarget = (playbackState.currentIndex + 1).coerceAtMost(playbackState.queue.lastIndex)
                        val existingIdx = playbackState.queue.indexOfFirst { it.id == targetTrack.id }
                        if (existingIdx > playbackState.currentIndex + 1 && insertTarget in playbackState.queue.indices) {
                            viewModel.moveQueueItem(existingIdx, insertTarget)
                        } else if (existingIdx == -1) {
                            viewModel.playNextTrack(targetTrack)
                        }
                    },
                    onClearUpNext = viewModel::clearUpNext,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onToggleRepeat = viewModel::toggleRepeatMode,
                    onOpenSongMenu = { targetTrack, queueIdx ->
                        songMenuTarget = targetTrack to queueIdx
                    },
                    onDismiss = { showQueueSheet = false }
                )
            }

            if (showSleepTimerSheet) {
                OndaSleepTimerSheet(
                    remainingMs = playbackState.sleepTimerRemainingMs,
                    endOfTrack = playbackState.sleepTimerEndOfTrack,
                    isFading = playbackState.sleepTimerFading,
                    onSelectDurationMs = viewModel::startSleepTimer,
                    onSelectEndOfTrack = viewModel::startSleepTimerEndOfTrack,
                    onAdjustDurationMs = viewModel::adjustSleepTimer,
                    onCancelTimer = viewModel::cancelSleepTimer,
                    onDismiss = { showSleepTimerSheet = false }
                )
            }

            AnimatedVisibility(
                visible = showStudioOverlay,
                enter = if (reducedMotion) {
                    fadeIn(tween(120))
                } else {
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f)
                    ) { it / 8 } + scaleIn(
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f),
                        initialScale = 0.96f
                    ) + fadeIn(tween(200))
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(100))
                } else {
                    slideOutVertically(
                        animationSpec = tween(180)
                    ) { it / 10 } + scaleOut(
                        animationSpec = tween(180),
                        targetScale = 0.97f
                    ) + fadeOut(tween(150))
                }
            ) {
                OndaStudioOverlay(
                    activeStyle = settings.playerStyle,
                    studioConfig = settings.studioConfig,
                    currentTrack = playbackState.currentTrack,
                    palette = palette,
                    hapticsEnabled = settings.hapticsEnabled,
                    onSelectStyle = viewModel::switchPlayerStyle,
                    onUpdateConfig = viewModel::updateStudioConfig,
                    onDismiss = { showStudioOverlay = false },
                    liveStagePreview = { previewStyle ->
                        val previewCtx = if (previewStyle == PlayerStyle.VIDEO) {
                            stageContext.copy(activePlayer = null)
                        } else {
                            stageContext
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            OndaPlayerBackdropLayer(
                                mode = settings.studioConfig.backdropMode,
                                palette = palette,
                                dynamicMeshEnabled = false,
                                reducedMotion = reducedMotion,
                                modifier = Modifier.fillMaxSize()
                            )
                            OndaPlayerStyleStage(
                                style = previewStyle,
                                ctx = previewCtx,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                )
            }

            val shareTrack = playbackState.currentTrack
            if (showShareSheet && shareTrack != null) {
                OndaShareSheet(
                    track = shareTrack,
                    lyrics = lyrics,
                    palette = palette,
                    initialLineIndex = shareInitialLineIndex,
                    hapticsEnabled = settings.hapticsEnabled,
                    onDismiss = {
                        showShareSheet = false
                        shareInitialLineIndex = null
                    }
                )
            }

            if (showActionSheet) {
                OndaPlayerActionSheet(
                    track = playbackState.currentTrack,
                    liked = liked,
                    downloadItem = downloadItem,
                    playbackState = playbackState,
                    lyricsOffsetMs = lyricsOffsetMs,
                    hapticsEnabled = settings.hapticsEnabled,
                    onOpenShare = {
                        shareInitialLineIndex = null
                        showShareSheet = true
                    },
                    onOpenStudio = { showStudioOverlay = true },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenStreamInfo = { showStreamInfoSheet = true },
                    onOpenSpeedSheet = { showSpeedSheet = true },
                    onOpenLyricsOffset = { showLyricsOffsetSheet = true },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onDownloadClick = viewModel::downloadCurrentTrack,
                    onToggleLike = onLikeFromButton,
                    onPlayNext = { targetTrack ->
                        viewModel.playNextTrack(targetTrack)
                        toastState?.show(
                            title = "Playing next",
                            subtitle = targetTrack.title,
                            artworkUrl = targetTrack.artworkUrl
                        )
                    },
                    onAddToQueue = { targetTrack ->
                        viewModel.addTrackToQueue(targetTrack)
                        toastState?.show(
                            title = "Added to queue",
                            subtitle = targetTrack.title,
                            artworkUrl = targetTrack.artworkUrl
                        )
                    },
                    onRefreshLyrics = {
                        viewModel.refreshLyrics()
                        toastState?.show("Refreshing lyrics")
                    },
                    onOpenSettings = {
                        showActionSheet = false
                        onOpenSettings()
                    },
                    onDismiss = { showActionSheet = false }
                )
            }

            if (showStreamInfoSheet) {
                OndaStreamTelemetrySheet(
                    track = playbackState.currentTrack,
                    playbackState = playbackState,
                    eqConfig = eqConfig,
                    onOpenSettings = {
                        showStreamInfoSheet = false
                        onOpenSettings()
                    },
                    onDismiss = { showStreamInfoSheet = false }
                )
            }

            if (showSpeedSheet) {
                OndaPlaybackSpeedSheet(
                    speed = playbackState.playbackSpeed,
                    pitch = playbackState.playbackPitch,
                    hapticsEnabled = settings.hapticsEnabled,
                    onSetSpeedAndPitch = viewModel::setPlaybackSpeedAndPitch,
                    onReset = {
                        viewModel.resetPlaybackSpeedAndPitch()
                        toastState?.show(
                            title = "Playback speed reset",
                            subtitle = "1.00x speed · 1.00x pitch"
                        )
                    },
                    onDismiss = { showSpeedSheet = false }
                )
            }

            if (showLyricsOffsetSheet) {
                OndaLyricsOffsetSheet(
                    lyrics = lyrics,
                    positionMs = playbackState.positionMs,
                    offsetMs = lyricsOffsetMs,
                    hapticsEnabled = settings.hapticsEnabled,
                    onAdjustOffset = viewModel::adjustLyricsOffset,
                    onSetOffset = viewModel::setLyricsOffsetMs,
                    onRefreshLyrics = viewModel::refreshLyrics,
                    onDismiss = { showLyricsOffsetSheet = false }
                )
            }

            val activeSongMenu = songMenuTarget
            if (activeSongMenu != null) {
                val (menuTrack, menuQueueIdx) = activeSongMenu
                val isCurrentTrack = menuTrack.id == playbackState.currentTrack?.id
                val canRemove = menuQueueIdx != null && menuQueueIdx in playbackState.queue.indices
                OndaSongMenuSheet(
                    track = menuTrack,
                    liked = if (isCurrentTrack) liked else menuTrack.liked,
                    canRemoveFromQueue = canRemove,
                    onPlayNext = {
                        if (menuQueueIdx != null && menuQueueIdx > playbackState.currentIndex + 1) {
                            viewModel.moveQueueItem(menuQueueIdx, playbackState.currentIndex + 1)
                        } else {
                            viewModel.playNextTrack(menuTrack)
                        }
                        toastState?.show(
                            title = "Playing next",
                            subtitle = menuTrack.title,
                            artworkUrl = menuTrack.artworkUrl
                        )
                    },
                    onAddToQueue = {
                        viewModel.addTrackToQueue(menuTrack)
                        toastState?.show(
                            title = "Added to queue",
                            subtitle = menuTrack.title,
                            artworkUrl = menuTrack.artworkUrl
                        )
                    },
                    onToggleLike = {
                        if (isCurrentTrack) {
                            onLikeFromButton()
                        } else {
                            viewModel.toggleLikeForTrack(menuTrack)
                        }
                    },
                    onDownload = {
                        viewModel.downloadTrack(menuTrack)
                        toastState?.show(
                            title = "Download queued",
                            subtitle = menuTrack.title,
                            artworkUrl = menuTrack.artworkUrl
                        )
                    },
                    onShare = {
                        shareInitialLineIndex = null
                        if (isCurrentTrack) {
                            showShareSheet = true
                        } else {
                            toastState?.show("Open track in player to share Soundcard")
                        }
                    },
                    onStreamInfo = { showStreamInfoSheet = true },
                    onRemoveFromQueue = if (canRemove && menuQueueIdx != null) {
                        {
                            viewModel.removeQueueItem(menuQueueIdx)
                            toastState?.show(
                                title = "Removed from queue",
                                subtitle = menuTrack.title
                            )
                        }
                    } else {
                        null
                    },
                    onDismiss = { songMenuTarget = null }
                )
            }
        }
    }
}

@Composable
private fun OndaPlayerBackdropLayer(
    mode: StudioBackdropMode,
    palette: OndaArtworkPalette,
    dynamicMeshEnabled: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    when (mode) {
        StudioBackdropMode.OLED_PURE -> {
            Box(modifier = modifier.background(Color(0xFF000000)))
        }

        StudioBackdropMode.ACOUSTIC_MIST -> {
            Box(
                modifier = modifier
                    .background(Color(0xFF000000))
                    .drawWithCache {
                        val topMist = Brush.radialGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = 0.24f),
                                palette.secondary.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.24f),
                            radius = maxOf(size.width, size.height) * 0.72f
                        )
                        val lowerMist = Brush.radialGradient(
                            colors = listOf(
                                palette.secondary.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.2f, size.height * 0.78f),
                            radius = maxOf(size.width, size.height) * 0.55f
                        )
                        onDrawBehind {
                            drawRect(brush = topMist)
                            drawRect(brush = lowerMist)
                        }
                    }
            )
        }

        StudioBackdropMode.AGSL_MESH -> {
            AgslMeshBackground(
                palette = palette,
                animated = dynamicMeshEnabled && !reducedMotion,
                dimAlpha = 0.72f,
                modifier = modifier
            )
        }

        StudioBackdropMode.VINYL_DARK -> {
            Box(
                modifier = modifier.drawWithCache {
                    val vignette = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1B1B20),
                            Color(0xFF09090B),
                            Color(0xFF000000)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.36f),
                        radius = maxOf(size.width, size.height) * 0.85f
                    )
                    onDrawBehind {
                        drawRect(brush = vignette)
                    }
                }
            )
        }
    }
}

@Composable
private fun OndaPlayerStyleStage(
    style: PlayerStyle,
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    when (style) {
        PlayerStyle.FLAGSHIP -> FlagshipPlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.CLASSIC -> ClassicPlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.POSTER -> PosterPlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.TURNTABLE -> TurntablePlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.VIDEO -> VideoPlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.IPOD -> IpodPlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.TIMELINE -> TimelinePlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.LYRICS -> LyricsStagePlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.BEDSIDE -> BedsidePlayerStyle(ctx = ctx, modifier = modifier)
        PlayerStyle.BEDSIDE_TILES -> BedsideTilesPlayerStyle(ctx = ctx, modifier = modifier)
    }
}

@Composable
private fun Modifier.verticalCollapseDrag(
    enabled: Boolean,
    morphFractionProvider: () -> Float,
    containerHeightPx: Float,
    reducedMotion: Boolean,
    onMorphFractionChange: (Float) -> Unit,
    onCollapse: () -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val releaseSpeedPx = with(density) { 300.dp.toPx() }
    val closeTravelPx = with(density) { 36.dp.toPx() }
    val flickSpeedPx = with(density) { 900.dp.toPx() }
    val flickTravelPx = with(density) { 12.dp.toPx() }

    val currentMorphProvider by rememberUpdatedState(morphFractionProvider)
    val currentHeight by rememberUpdatedState(containerHeightPx)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentOnMorph by rememberUpdatedState(onMorphFractionChange)
    val currentOnCollapse by rememberUpdatedState(onCollapse)
    var settleJob by remember { mutableStateOf<Job?>(null) }

    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val velocityTracker = VelocityTracker()
        var startMorph = 1f
        var dragSumY = 0f
        var lastMorph = 1f
        var activeCollapse = false

        detectVerticalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                startMorph = currentMorphProvider().coerceIn(0f, 1f)
                lastMorph = startMorph
                dragSumY = 0f
                activeCollapse = false
            },
            onVerticalDrag = { change, dragAmount ->
                velocityTracker.addPointerInputChange(change)
                dragSumY += dragAmount
                if (!activeCollapse && (dragSumY > 0f || startMorph < 0.995f)) {
                    activeCollapse = true
                    MorphGestureArbitrator.claim()
                    settleJob?.cancel()
                }
                if (activeCollapse) {
                    change.consume()
                    val spanPx = (currentHeight * 0.65f).coerceAtLeast(1f)
                    val nextMorph = (startMorph - dragSumY / spanPx).coerceIn(0.006f, 0.994f)
                    lastMorph = nextMorph
                    currentOnMorph(nextMorph)
                }
            },
            onDragEnd = {
                if (!activeCollapse) return@detectVerticalDragGestures
                val vy = velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                val spanPx = (currentHeight * 0.65f).coerceAtLeast(1f)
                val shouldCollapse = when {
                    vy > flickSpeedPx && dragSumY > flickTravelPx -> true
                    vy > releaseSpeedPx -> true
                    vy < -releaseSpeedPx -> false
                    else -> dragSumY > closeTravelPx || lastMorph < 0.72f
                }
                val target = if (shouldCollapse) 0f else 1f
                val token = MorphGestureArbitrator.claim()
                settleJob?.cancel()
                settleJob = scope.launch {
                    settleMorphFraction(
                        token = token,
                        startFraction = lastMorph,
                        targetFraction = target,
                        velocityFractionPerSec = -vy / spanPx,
                        reducedMotion = currentReducedMotion,
                        onStep = { currentOnMorph(it) },
                        onComplete = {
                            if (shouldCollapse) {
                                currentOnCollapse()
                            } else {
                                currentOnMorph(1f)
                            }
                        }
                    )
                }
                activeCollapse = false
                dragSumY = 0f
            },
            onDragCancel = {
                if (!activeCollapse) return@detectVerticalDragGestures
                val vy = velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                val spanPx = (currentHeight * 0.65f).coerceAtLeast(1f)
                val shouldCollapse = dragSumY > closeTravelPx || lastMorph < 0.72f
                val target = if (shouldCollapse) 0f else 1f
                val token = MorphGestureArbitrator.claim()
                settleJob?.cancel()
                settleJob = scope.launch {
                    settleMorphFraction(
                        token = token,
                        startFraction = lastMorph,
                        targetFraction = target,
                        velocityFractionPerSec = -vy / spanPx,
                        reducedMotion = currentReducedMotion,
                        onStep = { currentOnMorph(it) },
                        onComplete = {
                            if (shouldCollapse) {
                                currentOnCollapse()
                            } else {
                                currentOnMorph(1f)
                            }
                        }
                    )
                }
                activeCollapse = false
                dragSumY = 0f
            }
        )
    }
}

@Composable
private fun FullPlayerTopHeader(
    activeStyle: PlayerStyle,
    playbackSpeed: Float,
    playbackPitch: Float,
    sleepTimerActive: Boolean,
    onCollapse: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenSpeedSheet: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenActionSheet: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val speedModified = abs(playbackSpeed - 1f) > 0.01f || abs(playbackPitch - 1f) > 0.01f
    val collapseInteraction = remember { MutableInteractionSource() }
    val studioInteraction = remember { MutableInteractionSource() }
    val sleepInteraction = remember { MutableInteractionSource() }
    val queueInteraction = remember { MutableInteractionSource() }
    val actionInteraction = remember { MutableInteractionSource() }
    val sleepIconTint by animateColorAsState(
        targetValue = if (sleepTimerActive) Color.White else Color(0xFF9E9E9E),
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "SleepTimerIconTint"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, bottom = 4.dp)
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                .background(Color(0xFF2E2E2E))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCollapse,
                interactionSource = collapseInteraction,
                modifier = Modifier
                    .defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                    .pressBounce(
                        interactionSource = collapseInteraction,
                        pressedScale = 0.88f,
                        enabled = !reducedMotion
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse player",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .pressBounce(
                            interactionSource = studioInteraction,
                            pressedScale = 0.94f,
                            enabled = !reducedMotion
                        )
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color(0xFF141414))
                        .border(0.75.dp, Color(0x1EFFFFFF), RoundedCornerShape(GlassTokens.RadiusPill))
                        .clickable(
                            interactionSource = studioInteraction,
                            indication = null,
                            onClick = onOpenStudio
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OndaMonogramMark(
                        modifier = Modifier.size(14.dp),
                        tint = Color.White,
                        filled = false
                    )
                    Text(
                        text = activeStyle.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(
                    visible = speedModified,
                    enter = if (reducedMotion) {
                        fadeIn(tween(100))
                    } else {
                        scaleIn(
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
                            initialScale = 0.8f
                        ) + fadeIn(tween(150))
                    },
                    exit = if (reducedMotion) {
                        fadeOut(tween(80))
                    } else {
                        scaleOut(
                            animationSpec = tween(120),
                            targetScale = 0.8f
                        ) + fadeOut(tween(120))
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .clickable(onClick = onOpenSpeedSheet),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2fx", playbackSpeed),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                                fontSize = 11.sp
                            ),
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                .background(Color(0xFF1C1C1E))
                                .border(0.75.dp, Color(0x2AFFFFFF), RoundedCornerShape(GlassTokens.RadiusPill))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenSleepTimer,
                    interactionSource = sleepInteraction,
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                        .pressBounce(
                            interactionSource = sleepInteraction,
                            pressedScale = 0.88f,
                            enabled = !reducedMotion
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = sleepIconTint,
                        modifier = Modifier.size(21.dp)
                    )
                }

                IconButton(
                    onClick = onOpenQueue,
                    interactionSource = queueInteraction,
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                        .pressBounce(
                            interactionSource = queueInteraction,
                            pressedScale = 0.88f,
                            enabled = !reducedMotion
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onOpenActionSheet,
                    interactionSource = actionInteraction,
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                        .pressBounce(
                            interactionSource = actionInteraction,
                            pressedScale = 0.88f,
                            enabled = !reducedMotion
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Player actions",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}
