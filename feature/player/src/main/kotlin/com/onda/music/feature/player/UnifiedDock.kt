package com.onda.music.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.LocalOndaBackdropState
import com.onda.music.core.design.LocalOndaColors
import com.onda.music.core.design.LocalOndaPalette
import com.onda.music.core.design.OndaSampledGlassBackdrop
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.press3D
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MiniDragAxis {
    NONE,
    HORIZONTAL,
    VERTICAL
}

private val NavHighlightLead = spring<Float>(dampingRatio = 0.82f, stiffness = 700f)
private val NavHighlightTrail = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)
private val PillRiseSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 420f)
private val PillSettleSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)
private val NavHighlightInset = 8.dp
private val NavHighlightGap = 6.dp

@Stable
private class DockHighlightState(initialIndex: Int) {
    val start = Animatable(initialIndex.coerceAtLeast(0).toFloat())
    val end = Animatable(initialIndex.coerceAtLeast(0).toFloat())
    val shown = Animatable(if (initialIndex >= 0) 1f else 0f)
    val lift = Animatable(0f)

    fun rect(rowSize: Size, tabCount: Int, density: Density): Rect? {
        if (tabCount <= 0 || shown.value <= 0.001f) return null
        val slot = rowSize.width / tabCount
        val halfGap = with(density) { NavHighlightGap.toPx() } * 0.5f
        val inset = with(density) { NavHighlightInset.toPx() }
        return Rect(
            left = start.value * slot + halfGap,
            top = inset,
            right = (end.value + 1f) * slot - halfGap,
            bottom = (rowSize.height - inset).coerceAtLeast(inset + 1f)
        )
    }

    suspend fun moveTo(target: Float, reducedMotion: Boolean) {
        if (reducedMotion) {
            start.snapTo(target)
            end.snapTo(target)
            return
        }
        coroutineScope {
            val movingRight = target > (start.value + end.value) * 0.5f
            launch {
                start.animateTo(
                    targetValue = target,
                    animationSpec = if (movingRight) NavHighlightTrail else NavHighlightLead
                )
            }
            end.animateTo(
                targetValue = target,
                animationSpec = if (movingRight) NavHighlightLead else NavHighlightTrail
            )
        }
    }
}

@Composable
fun UnifiedDock(
    tabLabels: List<String>,
    tabIcons: List<ImageVector>,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    morphFraction: Float,
    onMorphFractionChange: (Float) -> Unit,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    inactiveTabIcons: List<ImageVector> = tabIcons,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val settings by viewModel.userSettings.collectAsState()
    val palette by viewModel.artworkPalette.collectAsState()
    val reducedMotion = OndaTheme.reducedMotion
    val currentColors = OndaTheme.colors
    val dockColors = remember(currentColors, palette) {
        currentColors.copy(
            accent = palette.primary,
            accentSoft = palette.secondary
        )
    }

    val scope = rememberCoroutineScope()
    var isVerticalDragging by remember { mutableStateOf(false) }
    var isSettlingMorph by remember { mutableStateOf(false) }
    val clampedMorph = morphFraction.coerceIn(0f, 1f)
    val dockAlpha = (1f - clampedMorph * 1.75f).coerceIn(0f, 1f)
    val density = LocalDensity.current

    if (dockAlpha <= 0.01f && !isVerticalDragging && !isSettlingMorph) return

    val queue = playbackState.queue
    val currentIndex = playbackState.currentIndex
    val previousTrack = if (currentIndex > 0) queue.getOrNull(currentIndex - 1) else null
    val nextTrack = if (currentIndex >= 0 && currentIndex < queue.lastIndex) queue.getOrNull(currentIndex + 1) else null

    val bottomScrimBrush = remember {
        Brush.verticalGradient(
            0.00f to Color.Transparent,
            0.34f to Color(0x74000000),
            0.74f to Color(0xDF000000),
            1.00f to Color(0xFF000000)
        )
    }

    CompositionLocalProvider(
        LocalOndaPalette provides palette,
        LocalOndaColors provides dockColors
    ) {
        Box(modifier = modifier.fillMaxWidth()) {
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = dockAlpha }
                    .drawBehind { drawRect(brush = bottomScrimBrush) }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 42.dp)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
                    .graphicsLayer { alpha = dockAlpha },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = playbackState.currentTrack != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    playbackState.currentTrack?.let { track ->
                        val durationMs = playbackState.durationMs.coerceAtLeast(1L)
                        val progress = (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationY = -with(density) { 38.dp.toPx() } * clampedMorph
                                if (!reducedMotion) {
                                    val s = lerp(1f, 1.03f, clampedMorph)
                                    scaleX = s
                                    scaleY = s
                                }
                            }
                        ) {
                            MiniPlayerBar(
                                track = track,
                                previousTrack = previousTrack,
                                nextTrack = nextTrack,
                                isPlaying = playbackState.status == PlaybackStatus.PLAYING,
                                progress = progress,
                                blurEnabled = settings.glassBlurEnabled,
                                hapticsEnabled = settings.hapticsEnabled,
                                morphFraction = clampedMorph,
                                onMorphFractionChange = onMorphFractionChange,
                                onExpandPlayer = onExpandPlayer,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSkipNext = viewModel::skipNext,
                                onSkipPrevious = viewModel::skipPrevious,
                                settleScope = scope,
                                onVerticalDraggingChange = { isVerticalDragging = it },
                                onSettlingMorphChange = { isSettlingMorph = it }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.graphicsLayer {
                        val tabFade = (1f - clampedMorph * 2.3f).coerceIn(0f, 1f)
                        alpha = tabFade
                        translationY = with(density) { 42.dp.toPx() } * clampedMorph
                    }
                ) {
                    DockTabPill(
                        tabLabels = tabLabels,
                        tabIcons = tabIcons,
                        inactiveTabIcons = inactiveTabIcons,
                        selectedTabIndex = selectedTabIndex,
                        blurEnabled = settings.glassBlurEnabled,
                        hapticsEnabled = settings.hapticsEnabled,
                        onSelectTab = onSelectTab
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    track: Track,
    previousTrack: Track?,
    nextTrack: Track?,
    isPlaying: Boolean,
    progress: Float,
    blurEnabled: Boolean,
    hapticsEnabled: Boolean,
    morphFraction: Float,
    onMorphFractionChange: (Float) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    settleScope: kotlinx.coroutines.CoroutineScope,
    onVerticalDraggingChange: (Boolean) -> Unit,
    onSettlingMorphChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reducedMotion = OndaTheme.reducedMotion
    val backdropState = LocalOndaBackdropState.current
    val scope = rememberCoroutineScope()
    val horizontalOffset = remember { Animatable(0f) }
    var pillWidthPx by remember { mutableFloatStateOf(1f) }
    var contentAreaWidthPx by remember { mutableFloatStateOf(1f) }
    var skipCommitGeneration by remember { mutableIntStateOf(0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var dragSnapJob by remember { mutableStateOf<Job?>(null) }
    var skipJob by remember { mutableStateOf<Job?>(null) }

    val expandDistancePx = with(density) { 380.dp.toPx() }.coerceAtLeast(1f)
    val releaseSpeedPx = with(density) { 300.dp.toPx() }
    val openTravelPx = with(density) { 36.dp.toPx() }
    val horizontalFlingPx = with(density) { 420.dp.toPx() }
    val shape = SmoothCornerShape(20.dp)

    val currentMorph by rememberUpdatedState(morphFraction)
    val currentOnMorph by rememberUpdatedState(onMorphFractionChange)
    val currentOnExpand by rememberUpdatedState(onExpandPlayer)
    val currentOnSkipNext by rememberUpdatedState(onSkipNext)
    val currentOnSkipPrev by rememberUpdatedState(onSkipPrevious)
    val currentPrevTrack by rememberUpdatedState(previousTrack)
    val currentNextTrack by rememberUpdatedState(nextTrack)
    val currentHaptics by rememberUpdatedState(hapticsEnabled)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentOnVerticalDragging by rememberUpdatedState(onVerticalDraggingChange)
    val currentOnSettlingMorph by rememberUpdatedState(onSettlingMorphChange)

    LaunchedEffect(track.id) {
        skipCommitGeneration++
        dragSnapJob?.cancel()
        skipJob?.cancel()
        horizontalOffset.snapTo(0f)
    }

    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DockMetrics.MiniPlayerHeight)
            .onSizeChanged { pillWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = horizontalOffset.value * 0.05f
            }
            .press3D(maxTiltDegrees = 4f, pressedScale = 0.985f)
            .clip(shape)
            .border(width = 0.5.dp, color = Color(0x22FFFFFF), shape = shape)
            .pointerInput(track.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentMorph > 0.65f) return@awaitEachGesture
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPointerInputChange(down)

                    var axis = MiniDragAxis.NONE
                    var totalDx = 0f
                    var totalDy = 0f
                    var startMorph = currentMorph
                    var lastMorph = startMorph

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocityTracker.addPointerInputChange(change)

                            if (!change.pressed) {
                                when (axis) {
                                    MiniDragAxis.NONE -> {
                                        if (!change.isConsumed) {
                                            MorphGestureArbitrator.claim()
                                            settleJob?.cancel()
                                            currentOnExpand()
                                        }
                                    }
                                    MiniDragAxis.HORIZONTAL -> {
                                        val vx = velocityTracker.calculateVelocity().x
                                        val span = contentAreaWidthPx.coerceAtLeast(1f)
                                        val offsetNow = horizontalOffset.value
                                        val forward = currentNextTrack != null &&
                                            ((offsetNow < -span * 0.34f || vx < -horizontalFlingPx) && vx <= horizontalFlingPx)
                                        val backward = !forward && currentPrevTrack != null &&
                                            ((offsetNow > span * 0.34f || vx > horizontalFlingPx) && vx >= -horizontalFlingPx)

                                        dragSnapJob?.cancel()
                                        skipJob?.cancel()

                                        if (forward || backward) {
                                            if (currentHaptics) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            val targetOffset = if (forward) -span else span
                                            val commitGen = ++skipCommitGeneration
                                            skipJob = scope.launch {
                                                if (currentReducedMotion) {
                                                    horizontalOffset.snapTo(targetOffset)
                                                } else {
                                                    horizontalOffset.animateTo(
                                                        targetValue = targetOffset,
                                                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 540f),
                                                        initialVelocity = vx
                                                    )
                                                }
                                                if (forward) currentOnSkipNext() else currentOnSkipPrev()
                                                delay(180L)
                                                if (skipCommitGeneration == commitGen) {
                                                    horizontalOffset.animateTo(
                                                        0f,
                                                        spring(dampingRatio = 0.85f, stiffness = 460f)
                                                    )
                                                }
                                            }
                                        } else {
                                            skipJob = scope.launch {
                                                if (currentReducedMotion) {
                                                    horizontalOffset.snapTo(0f)
                                                } else {
                                                    horizontalOffset.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
                                                        initialVelocity = vx
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    MiniDragAxis.VERTICAL -> {
                                        val vy = velocityTracker.calculateVelocity().y
                                        val shouldExpand = when {
                                            vy < -releaseSpeedPx -> true
                                            vy > releaseSpeedPx -> false
                                            else -> -totalDy > openTravelPx || lastMorph >= 0.35f
                                        }
                                        val target = if (shouldExpand) 1f else 0f
                                        val token = MorphGestureArbitrator.claim()
                                        currentOnSettlingMorph(true)
                                        currentOnVerticalDragging(false)
                                        settleJob?.cancel()
                                        settleJob = settleScope.launch {
                                            try {
                                                settleMorphFraction(
                                                    token = token,
                                                    startFraction = lastMorph,
                                                    targetFraction = target,
                                                    velocityFractionPerSec = -vy / expandDistancePx,
                                                    reducedMotion = currentReducedMotion,
                                                    onStep = { currentOnMorph(it) },
                                                    onComplete = { currentOnMorph(target) }
                                                )
                                            } finally {
                                                currentOnSettlingMorph(false)
                                            }
                                        }
                                    }
                                }
                                break
                            }

                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y

                            if (axis == MiniDragAxis.NONE) {
                                val touchSlop = viewConfiguration.touchSlop
                                if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                    axis = if (abs(totalDx) >= abs(totalDy)) {
                                        skipJob?.cancel()
                                        MiniDragAxis.HORIZONTAL
                                    } else if (totalDy < 0f || currentMorph > 0.01f) {
                                        startMorph = currentMorph
                                        lastMorph = startMorph
                                        MorphGestureArbitrator.claim()
                                        settleJob?.cancel()
                                        currentOnVerticalDragging(true)
                                        MiniDragAxis.VERTICAL
                                    } else {
                                        MiniDragAxis.NONE
                                    }
                                }
                            }

                            when (axis) {
                                MiniDragAxis.HORIZONTAL -> {
                                    change.consume()
                                    val span = contentAreaWidthPx.coerceAtLeast(1f)
                                    val resisted = when {
                                        totalDx < 0f && currentNextTrack == null ->
                                            rubberBandOffset(totalDx, pillWidthPx * 0.14f)
                                        totalDx > 0f && currentPrevTrack == null ->
                                            rubberBandOffset(totalDx, pillWidthPx * 0.14f)
                                        else -> totalDx.coerceIn(-span, span)
                                    }
                                    dragSnapJob?.cancel()
                                    dragSnapJob = scope.launch {
                                        horizontalOffset.snapTo(resisted)
                                    }
                                }
                                MiniDragAxis.VERTICAL -> {
                                    change.consume()
                                    val nextMorph = (startMorph + (-totalDy / expandDistancePx)).coerceIn(0.006f, 0.994f)
                                    lastMorph = nextMorph
                                    currentOnMorph(nextMorph)
                                }
                                MiniDragAxis.NONE -> Unit
                            }
                        }
                    } finally {
                        currentOnVerticalDragging(false)
                    }
                }
            }
    ) {
        OndaSampledGlassBackdrop(
            backdropState = backdropState,
            modifier = Modifier.matchParentSize(),
            blurRadiusPx = 42f,
            tintColor = Color(0xFF161619),
            tintAlpha = 0.76f,
            fallbackAlpha = 0.94f,
            sheenAlpha = 0.09f,
            blurEnabled = blurEnabled
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .onSizeChanged { contentAreaWidthPx = it.width.toFloat().coerceAtLeast(1f) },
                contentAlignment = Alignment.CenterStart
            ) {
                if (previousTrack != null) {
                    MiniTrackPeekContent(
                        track = previousTrack,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = horizontalOffset.value
                                if (offset <= 0.5f) {
                                    alpha = 0f
                                } else {
                                    val w = contentAreaWidthPx.coerceAtLeast(1f)
                                    translationX = -w + offset
                                    alpha = (abs(offset) / (w * 0.72f)).coerceIn(0f, 1f)
                                }
                            }
                    )
                }

                MiniTrackPeekContent(
                    track = track,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = horizontalOffset.value
                            val w = contentAreaWidthPx.coerceAtLeast(1f)
                            translationX = offset
                            alpha = (1f - (abs(offset) / w) * 0.85f).coerceIn(0f, 1f)
                        }
                )

                if (nextTrack != null) {
                    MiniTrackPeekContent(
                        track = nextTrack,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = horizontalOffset.value
                                if (offset >= -0.5f) {
                                    alpha = 0f
                                } else {
                                    val w = contentAreaWidthPx.coerceAtLeast(1f)
                                    translationX = w + offset
                                    alpha = (abs(offset) / (w * 0.72f)).coerceIn(0f, 1f)
                                }
                            }
                    )
                }
            }

            IconButton(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTogglePlayPause()
                },
                interactionSource = playInteraction,
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .playerPressBounce(
                        interactionSource = playInteraction,
                        pressedScale = 0.86f,
                        releaseDampingRatio = 0.54f,
                        releaseStiffness = 720f,
                        reducedMotion = reducedMotion
                    )
            ) {
                OndaAnimatedPlayGlyph(
                    isPlaying = isPlaying,
                    reducedMotion = reducedMotion,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        MiniTelemetryProgress(
            progress = progress,
            accentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
                .height(3.dp)
        )
    }
}

@Composable
private fun MiniTrackPeekContent(
    track: Track,
    modifier: Modifier = Modifier
) {
    val textFadeMask = remember {
        Brush.horizontalGradient(
            0.00f to Color.Black,
            0.80f to Color.Black,
            1.00f to Color.Transparent
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtworkImage(
            artworkUrl = track.artworkUrl,
            contentDescription = track.title,
            shape = SmoothCornerShape(12.dp),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = textFadeMask, blendMode = BlendMode.DstIn)
                },
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                ),
                color = Color(0xFFA0A0A6),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun MiniTelemetryProgress(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val yMid = size.height * 0.5f
        val endX = size.width * progress.coerceIn(0f, 1f)

        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(0f, yMid),
            end = Offset(size.width, yMid),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )

        if (endX <= 1f) return@Canvas

        drawLine(
            color = accentColor.copy(alpha = 0.24f),
            start = Offset(0f, yMid),
            end = Offset(endX, yMid),
            strokeWidth = 3.2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = accentColor,
            start = Offset(0f, yMid),
            end = Offset(endX, yMid),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun DockTabPill(
    tabLabels: List<String>,
    tabIcons: List<ImageVector>,
    selectedTabIndex: Int,
    blurEnabled: Boolean,
    hapticsEnabled: Boolean,
    onSelectTab: (Int) -> Unit,
    inactiveTabIcons: List<ImageVector> = tabIcons
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reducedMotion = OndaTheme.reducedMotion
    val backdropState = LocalOndaBackdropState.current
    val itemCount = minOf(tabLabels.size, tabIcons.size)
    if (itemCount == 0) return

    var hoveredDragTab by remember { mutableIntStateOf(-1) }
    val pillState = remember { DockHighlightState(selectedTabIndex) }

    LaunchedEffect(selectedTabIndex, reducedMotion) {
        if (selectedTabIndex < 0) {
            if (reducedMotion) pillState.shown.snapTo(0f) else pillState.shown.animateTo(0f, tween(200))
            return@LaunchedEffect
        }
        val target = selectedTabIndex.coerceIn(0, itemCount - 1).toFloat()
        if (pillState.shown.value == 0f) {
            pillState.start.snapTo(target)
            pillState.end.snapTo(target)
        }
        if (reducedMotion) {
            pillState.shown.snapTo(1f)
        } else {
            launch { pillState.shown.animateTo(1f, tween(200)) }
        }
        pillState.moveTo(target, reducedMotion)
    }

    val currentSelected by rememberUpdatedState(selectedTabIndex)
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    val currentHaptics by rememberUpdatedState(hapticsEnabled)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)

    val pillShape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DockMetrics.NavBarHeight)
            .press3D(maxTiltDegrees = 4f, pressedScale = 0.985f)
            .clip(pillShape)
            .border(width = 0.5.dp, color = Color(0x1EFFFFFF), shape = pillShape)
    ) {
        OndaSampledGlassBackdrop(
            backdropState = backdropState,
            modifier = Modifier.matchParentSize(),
            blurRadiusPx = 42f,
            tintColor = Color(0xFF141416),
            tintAlpha = 0.74f,
            fallbackAlpha = 0.94f,
            sheenAlpha = 0.08f,
            blurEnabled = blurEnabled
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .drawBehind {
                    val rect = pillState.rect(size, itemCount, density) ?: return@drawBehind
                    val lift = if (currentReducedMotion) 0f else pillState.lift.value
                    val grow = lift * 3.dp.toPx()
                    val baseAlpha = 0.14f + 0.12f * lift
                    drawRoundRect(
                        color = Color.White.copy(alpha = baseAlpha),
                        topLeft = Offset(rect.left - grow, rect.top - grow),
                        size = Size(rect.width + grow * 2f, rect.height + grow * 2f),
                        cornerRadius = CornerRadius(rect.height * 0.5f + grow),
                        alpha = pillState.shown.value
                    )
                }
                .pointerInput(pillState, itemCount) {
                    coroutineScope {
                        var moveJob: Job? = null
                        var liftJob: Job? = null
                        var revertJob: Job? = null

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val slot = size.width.toFloat() / itemCount.coerceAtLeast(1)
                            fun tabAt(x: Float): Float = (x / slot - 0.5f).coerceIn(0f, (itemCount - 1).toFloat())

                            revertJob?.cancel()
                            if (!currentReducedMotion) {
                                liftJob?.cancel()
                                liftJob = launch { pillState.lift.animateTo(1f, PillRiseSpring) }
                            }
                            moveJob?.cancel()
                            moveJob = launch { pillState.moveTo(tabAt(down.position.x), currentReducedMotion) }

                            var dragging = false
                            var travel = 0f
                            var currentX = down.position.x
                            var lastHoveredTab = tabAt(currentX).roundToInt()

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                travel += change.positionChange().x
                                currentX = change.position.x
                                if (!dragging && abs(travel) > viewConfiguration.touchSlop) {
                                    dragging = true
                                }
                                if (dragging) {
                                    change.consume()
                                    val mapped = tabAt(currentX)
                                    val hoveredTab = mapped.roundToInt()
                                    hoveredDragTab = hoveredTab
                                    if (hoveredTab != lastHoveredTab) {
                                        lastHoveredTab = hoveredTab
                                        if (currentHaptics) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    moveJob?.cancel()
                                    moveJob = launch { pillState.moveTo(mapped, currentReducedMotion) }
                                }
                            }

                            hoveredDragTab = -1
                            if (!currentReducedMotion) {
                                liftJob?.cancel()
                                liftJob = launch { pillState.lift.animateTo(0f, PillSettleSpring) }
                            }
                            val target = tabAt(currentX).roundToInt().coerceIn(0, itemCount - 1)
                            moveJob?.cancel()
                            moveJob = launch { pillState.moveTo(target.toFloat(), currentReducedMotion) }
                            if (dragging) {
                                if (currentHaptics) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                currentOnSelectTab(target)
                            } else {
                                revertJob = launch {
                                    delay(450L)
                                    val lit = currentSelected
                                    if (lit in 0 until itemCount && lit != target) {
                                        moveJob?.cancel()
                                        pillState.moveTo(lit.toFloat(), currentReducedMotion)
                                    }
                                }
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until itemCount) {
                val selected = index == selectedTabIndex
                val isLit = selected || index == hoveredDragTab
                val label = tabLabels[index]
                val activeIcon = tabIcons[index]
                val inactiveIcon = inactiveTabIcons.getOrElse(index) { activeIcon }
                val interactionSource = remember { MutableInteractionSource() }
                val litProgress = animateFloatAsState(
                    targetValue = if (isLit) 1f else 0f,
                    animationSpec = if (reducedMotion) snap() else tween(180),
                    label = "DockTabLit"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                        .playerPressBounce(
                            interactionSource = interactionSource,
                            pressedScale = 0.86f,
                            reducedMotion = reducedMotion
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = inactiveIcon,
                            contentDescription = label,
                            tint = Color.White.copy(alpha = 0.64f),
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    alpha = 1f - litProgress.value
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                        )
                        Icon(
                            imageVector = activeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    alpha = litProgress.value
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                        )
                    }
                }
            }
        }
    }
}
