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
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.LocalOndaColors
import com.onda.music.core.design.LocalOndaPalette
import com.onda.music.core.design.MorphingCornerClipShape
import com.onda.music.core.design.OndaTheme
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun FullPlayerOverlay(
    morphFraction: Float,
    onMorphFractionChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
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

    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current

    var showInlineLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showStylePickerSheet by remember { mutableStateOf(false) }
    var showPosterOverflowMenu by remember { mutableStateOf(false) }

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

    val anySheetOpen = showQueueSheet || showSleepTimerSheet || showStylePickerSheet || showPosterOverflowMenu
    PredictiveBackHandler(enabled = clampedMorph > 0.05f && !anySheetOpen) { backEvents: Flow<BackEventCompat> ->
        MorphGestureArbitrator.claim()
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
        onLikeFromDoubleTap
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
            onAttachTextureView = viewModel::attachVideoTextureView,
            onOpenOverflowMenu = { showPosterOverflowMenu = true },
            likeBurstState = likeBurstState,
            onLikeFromButton = onLikeFromButton,
            onLikeFromDoubleTap = onLikeFromDoubleTap
        )
    }

    val collapseTravelOffsetPx = with(density) { 64.dp.toPx() }
    val contentRevealAlpha = ((clampedMorph - 0.12f) / 0.56f).coerceIn(0f, 1f)

    CompositionLocalProvider(
        LocalOndaPalette provides palette,
        LocalOndaColors provides dynamicColors
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { containerHeightPx = it.height.coerceAtLeast(1).toFloat() }
                .graphicsLayer {
                    val travelRange = (containerHeightPx - collapseTravelOffsetPx).coerceAtLeast(1f)
                    translationY = (1f - clampedMorph) * travelRange
                    alpha = (clampedMorph * 2.4f).coerceIn(0f, 1f)
                    shape = morphShape
                    clip = true
                }
                .background(Color(0xFF000000))
                .verticalCollapseDrag(
                    enabled = !anySheetOpen,
                    morphFraction = clampedMorph,
                    containerHeightPx = containerHeightPx,
                    reducedMotion = reducedMotion,
                    onMorphFractionChange = onMorphFractionChange,
                    onCollapse = onCollapse
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (!reducedMotion) {
                            val backdropScale = lerp(1.06f, 1f, clampedMorph)
                            scaleX = backdropScale
                            scaleY = backdropScale
                        }
                        alpha = contentRevealAlpha
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

                        IconButton(
                            onClick = {
                                MorphGestureArbitrator.claim()
                                onMorphFractionChange(0f)
                                onCollapse()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 6.dp, end = 12.dp)
                                .defaultMinSize(
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

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(start = 20.dp, bottom = 72.dp)
                        ) {
                            DropdownMenu(
                                expanded = showPosterOverflowMenu,
                                onDismissRequest = { showPosterOverflowMenu = false },
                                containerColor = Color(0xFF141414)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Player Style", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Style,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(textColor = Color.White),
                                    onClick = {
                                        showPosterOverflowMenu = false
                                        showStylePickerSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Up Next Queue", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(textColor = Color.White),
                                    onClick = {
                                        showPosterOverflowMenu = false
                                        showQueueSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (playbackState.sleepTimerRemainingMs != null) {
                                                "Sleep Timer (${formatDurationMs(playbackState.sleepTimerRemainingMs ?: 0L)})"
                                            } else {
                                                "Sleep Timer"
                                            },
                                            color = Color.White
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Bedtime,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(textColor = Color.White),
                                    onClick = {
                                        showPosterOverflowMenu = false
                                        showSleepTimerSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(textColor = Color.White),
                                    onClick = {
                                        showPosterOverflowMenu = false
                                        onOpenSettings()
                                    }
                                )
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
                            sleepTimerRemainingMs = playbackState.sleepTimerRemainingMs,
                            onCollapse = {
                                MorphGestureArbitrator.claim()
                                onMorphFractionChange(0f)
                                onCollapse()
                            },
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
                }
            }

            OndaLikeBurstOverlay(
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
private fun Modifier.verticalCollapseDrag(
    enabled: Boolean,
    morphFraction: Float,
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

    val currentMorph by rememberUpdatedState(morphFraction)
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
                startMorph = currentMorph
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
                                currentOnMorph(0f)
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
                                currentOnMorph(0f)
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
    sleepTimerRemainingMs: Long?,
    onCollapse: () -> Unit,
    onOpenStylePicker: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                modifier = Modifier.defaultMinSize(
                    minWidth = DockMetrics.MinTapTarget,
                    minHeight = DockMetrics.MinTapTarget
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
                modifier = Modifier
                    .weight(1f, fill = false)
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(Color(0xFF141414))
                    .clickable(onClick = onOpenStylePicker)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Style,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
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
                        tint = if (sleepTimerRemainingMs != null) Color.White else Color(0xFF9E9E9E),
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
                        tint = Color(0xFF9E9E9E),
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
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}
