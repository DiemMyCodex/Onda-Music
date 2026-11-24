package com.onda.music.feature.player.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.PlayerStudioConfig
import com.onda.music.core.model.StudioBottomDeckMode
import com.onda.music.core.model.StudioQuickAction
import com.onda.music.core.model.Track
import com.onda.music.feature.player.TrackArtworkImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val DeckCardSurface = Color(0xFF141414)
private val DeckBackSurface = Color(0xFF101013)
private val DeckChipSurface = Color(0xFF1A1A1E)
private val DeckHairlineBorder = Color(0x1AFFFFFF)
private val DeckActiveBorder = Color(0x38FFFFFF)
private val DeckLikeTint = Color(0xFFFF2D55)

@Composable
fun OndaBottomDeck(
    mode: StudioBottomDeckMode,
    quickActions: List<StudioQuickAction>,
    currentTrack: Track?,
    nextTrack: Track?,
    upNextCount: Int,
    activeLyricText: String?,
    liked: Boolean,
    downloadItem: DownloadItem?,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    hapticsEnabled: Boolean,
    onOpenQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQuickAction: (StudioQuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when (mode) {
            StudioBottomDeckMode.DUAL_DECK -> {
                OndaDualDeck(
                    currentTrack = currentTrack,
                    nextTrack = nextTrack,
                    upNextCount = upNextCount,
                    activeLyricText = activeLyricText,
                    hapticsEnabled = hapticsEnabled,
                    onOpenQueue = onOpenQueue,
                    onToggleLyrics = onToggleLyrics
                )
            }
            StudioBottomDeckMode.QUICK_ACTIONS -> {
                OndaQuickActionsDeck(
                    quickActions = quickActions,
                    liked = liked,
                    downloadItem = downloadItem,
                    playbackSpeed = playbackSpeed,
                    sleepTimerActive = sleepTimerActive,
                    hapticsEnabled = hapticsEnabled,
                    onQuickAction = onQuickAction
                )
            }
            StudioBottomDeckMode.MINIMAL -> {
                OndaMinimalDeck(
                    upNextCount = upNextCount,
                    hapticsEnabled = hapticsEnabled,
                    onOpenQueue = onOpenQueue
                )
            }
        }
    }
}

@Composable
private fun OndaDualDeck(
    currentTrack: Track?,
    nextTrack: Track?,
    upNextCount: Int,
    activeLyricText: String?,
    hapticsEnabled: Boolean,
    onOpenQueue: () -> Unit,
    onToggleLyrics: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var frontIndex by remember { mutableIntStateOf(0) }
    val flipProgress = remember { Animatable(0f) }
    var flipJob by remember { mutableStateOf<Job?>(null) }
    var isCompletingFlip by remember { mutableStateOf(false) }

    val flipThresholdPx = remember(density) { with(density) { 52.dp.toPx() } }
    val peekOffsetPx = remember(density) { with(density) { 8.dp.toPx() } }
    val arcLiftPx = remember(density) { with(density) { 14.dp.toPx() } }
    val cardShape = remember { SmoothCornerShape(22.dp) }
    val cardFlipSpring = remember { spring<Float>(dampingRatio = 0.76f, stiffness = 360f) }

    val triggerButtonFlip: () -> Unit = {
        if (hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (reducedMotion) {
            flipJob?.cancel()
            isCompletingFlip = false
            frontIndex = 1 - frontIndex
            scope.launch { flipProgress.snapTo(0f) }
        } else {
            flipJob?.cancel()
            flipJob = scope.launch {
                if (isCompletingFlip) {
                    frontIndex = 1 - frontIndex
                    flipProgress.snapTo(0f)
                }
                isCompletingFlip = true
                flipProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = cardFlipSpring
                )
                frontIndex = 1 - frontIndex
                flipProgress.snapTo(0f)
                isCompletingFlip = false
            }
        }
    }

    val startInteractiveDrag: () -> Unit = {
        flipJob?.cancel()
        if (isCompletingFlip) {
            frontIndex = 1 - frontIndex
            isCompletingFlip = false
            scope.launch { flipProgress.snapTo(0f) }
        }
    }

    val finishInteractiveDrag: (Boolean) -> Unit = { shouldFlip ->
        flipJob?.cancel()
        flipJob = scope.launch {
            if (shouldFlip) {
                if (hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (reducedMotion) {
                    frontIndex = 1 - frontIndex
                    flipProgress.snapTo(0f)
                    isCompletingFlip = false
                } else {
                    isCompletingFlip = true
                    flipProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = cardFlipSpring
                    )
                    frontIndex = 1 - frontIndex
                    flipProgress.snapTo(0f)
                    isCompletingFlip = false
                }
            } else {
                isCompletingFlip = false
                if (reducedMotion) {
                    flipProgress.snapTo(0f)
                } else {
                    flipProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = cardFlipSpring
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .pointerInput(reducedMotion, hapticsEnabled, flipThresholdPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var lockedUpward = false
                    var dragUpPx = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            if (lockedUpward) {
                                change?.consume()
                            }
                            break
                        }
                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!lockedUpward) {
                            if (totalDy > touchSlop && abs(totalDy) > abs(totalDx)) {
                                break
                            }
                            if (abs(totalDx) > touchSlop && abs(totalDx) >= abs(totalDy)) {
                                break
                            }
                            if (totalDy < -touchSlop && abs(totalDy) > abs(totalDx)) {
                                lockedUpward = true
                                change.consume()
                                startInteractiveDrag()
                            }
                        } else {
                            change.consume()
                        }

                        if (lockedUpward) {
                            dragUpPx = (-totalDy - touchSlop).coerceAtLeast(0f)
                            val raw = dragUpPx / flipThresholdPx
                            val damped = if (raw <= 1f) raw else 1f + (raw - 1f) * 0.22f
                            scope.launch {
                                flipProgress.snapTo(damped.coerceIn(0f, 1.18f))
                            }
                        }
                    }

                    if (lockedUpward) {
                        finishInteractiveDrag(dragUpPx >= flipThresholdPx)
                    }
                }
            }
    ) {
        val progress = flipProgress.value
        val backIndex = 1 - frontIndex
        val renderOrder = if (progress >= 0.5f) {
            listOf(frontIndex, backIndex)
        } else {
            listOf(backIndex, frontIndex)
        }

        for (cardIndex in renderOrder) {
            val isCurrentFront = cardIndex == frontIndex
            val prominence = if (isCurrentFront) {
                (1f - progress).coerceIn(0f, 1f)
            } else {
                progress.coerceIn(0f, 1f)
            }
            val horizontalInset = lerp(10.dp, 0.dp, prominence)
            val surfaceColor = lerp(DeckBackSurface, DeckCardSurface, prominence)
            val cardAlpha = lerp(0.72f, 1f, prominence)
            val cardScaleX = lerp(0.94f, 1f, prominence)
            val cardScaleY = lerp(0.96f, 1f, prominence)
            val cardTranslationY = if (isCurrentFront) {
                val arc = sin(PI.toFloat() * progress.coerceIn(0f, 1f)) * arcLiftPx
                -peekOffsetPx * progress.coerceIn(0f, 1.18f) - arc
            } else {
                -peekOffsetPx * (1f - progress)
            }
            val cardZIndex = if (isCurrentFront) {
                if (progress < 0.5f) 1f else 0f
            } else {
                if (progress < 0.5f) 0f else 1f
            }
            val isInteractiveFront = isCurrentFront && progress < 0.2f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(cardZIndex)
                    .padding(horizontal = horizontalInset)
                    .graphicsLayer {
                        translationY = cardTranslationY
                        scaleX = cardScaleX
                        scaleY = cardScaleY
                        alpha = cardAlpha
                    }
                    .height(60.dp)
                    .clip(cardShape)
                    .background(surfaceColor, cardShape)
                    .border(1.dp, DeckHairlineBorder, cardShape)
                    .then(
                        if (isInteractiveFront) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClick = {
                                    if (hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    if (cardIndex == 0) onToggleLyrics() else onOpenQueue()
                                }
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (cardIndex == 0) {
                    LyricTickerCardContent(
                        activeLyricText = activeLyricText,
                        isFront = isInteractiveFront,
                        onFlipClick = triggerButtonFlip
                    )
                } else {
                    UpNextPeekCardContent(
                        currentTrack = currentTrack,
                        nextTrack = nextTrack,
                        upNextCount = upNextCount,
                        isFront = isInteractiveFront,
                        onFlipClick = triggerButtonFlip
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricTickerCardContent(
    activeLyricText: String?,
    isFront: Boolean,
    onFlipClick: () -> Unit
) {
    val hasActiveLyric = !activeLyricText.isNullOrBlank()
    val badgeShape = remember { SmoothCornerShape(10.dp) }
    val tagShape = remember { SmoothCornerShape(6.dp) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(badgeShape)
                .background(DeckChipSurface, badgeShape)
                .border(1.dp, DeckHairlineBorder, badgeShape),
            contentAlignment = Alignment.Center
        ) {
            AcousticTickerBars(animated = hasActiveLyric && isFront)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(tagShape)
                    .background(DeckChipSurface, tagShape)
                    .border(1.dp, DeckHairlineBorder, tagShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "LYRICS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = GlassTokens.TextSecondary,
                    maxLines = 1
                )
            }
            Text(
                text = activeLyricText?.takeIf { it.isNotBlank() } ?: "Tap for live lyrics",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (hasActiveLyric) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (hasActiveLyric) Color.White else GlassTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DeckFlipButton(
            enabled = isFront,
            contentDescription = "Show up next card",
            onClick = onFlipClick
        )
    }
}

@Composable
private fun UpNextPeekCardContent(
    currentTrack: Track?,
    nextTrack: Track?,
    upNextCount: Int,
    isFront: Boolean,
    onFlipClick: () -> Unit
) {
    val thumbShape = remember { SmoothCornerShape(10.dp) }
    val displayArtUrl = nextTrack?.artworkUrl ?: currentTrack?.artworkUrl

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtworkImage(
            artworkUrl = displayArtUrl,
            contentDescription = nextTrack?.title ?: "Up next",
            shape = thumbShape,
            modifier = Modifier
                .size(36.dp)
                .border(1.dp, DeckHairlineBorder, thumbShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = GlassTokens.TextSecondary,
                    maxLines = 1
                )
                Text(
                    text = upNextCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = Color.White,
                    maxLines = 1
                )
            }
            Text(
                text = nextTrack?.title ?: "End of queue",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (nextTrack != null) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (nextTrack != null) Color.White else GlassTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DeckFlipButton(
            enabled = isFront,
            contentDescription = "Show live lyrics card",
            onClick = onFlipClick
        )
    }
}

@Composable
private fun DeckFlipButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.defaultMinSize(
            minWidth = DockMetrics.MinTapTarget,
            minHeight = DockMetrics.MinTapTarget
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapVert,
            contentDescription = contentDescription,
            tint = GlassTokens.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AcousticTickerBars(
    animated: Boolean,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animated, reducedMotion) {
        if (!animated || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                    phase = (phase + dt * 5.6f) % (2f * PI.toFloat() * 100f)
                }
                lastNanos = frameNanos
            }
        }
    }

    Canvas(modifier = modifier.size(16.dp)) {
        val barCount = 3
        val barWidth = 2.4.dp.toPx()
        val gap = (size.width - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)
        val radius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
        val baseFractions = floatArrayOf(0.45f, 0.82f, 0.56f)
        val speeds = floatArrayOf(1.0f, 1.38f, 0.84f)
        val offsets = floatArrayOf(0.2f, 1.7f, 3.1f)

        for (index in 0 until barCount) {
            val fraction = if (animated && !reducedMotion) {
                0.28f + 0.66f * (0.5f + 0.5f * sin(phase * speeds[index] + offsets[index]))
            } else {
                baseFractions[index]
            }
            val barHeight = (size.height * fraction).coerceAtLeast(barWidth)
            val left = index * (barWidth + gap)
            val top = (size.height - barHeight) * 0.5f
            drawRoundRect(
                color = if (animated) Color.White else GlassTokens.TextSecondary,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
        }
    }
}

@Composable
private fun OndaQuickActionsDeck(
    quickActions: List<StudioQuickAction>,
    liked: Boolean,
    downloadItem: DownloadItem?,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    hapticsEnabled: Boolean,
    onQuickAction: (StudioQuickAction) -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val resolvedActions = remember(quickActions) {
        if (quickActions.isEmpty()) {
            PlayerStudioConfig.DEFAULT_QUICK_ACTIONS
        } else {
            quickActions.take(PlayerStudioConfig.QUICK_ACTION_SLOTS)
        }
    }
    val pillShape = remember { SmoothCornerShape(18.dp) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        resolvedActions.forEach { action ->
            val descriptor = resolveQuickActionVisual(
                action = action,
                liked = liked,
                downloadItem = downloadItem,
                playbackSpeed = playbackSpeed,
                sleepTimerActive = sleepTimerActive
            )
            val interactionSource = remember(action) { MutableInteractionSource() }
            val containerColor = if (descriptor.active) DeckChipSurface else DeckCardSurface
            val borderColor = if (descriptor.active) DeckActiveBorder else DeckHairlineBorder

            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .pressBounce(
                        interactionSource = interactionSource,
                        pressedScale = 0.94f,
                        enabled = !reducedMotion
                    )
                    .clip(pillShape)
                    .background(containerColor, pillShape)
                    .border(1.dp, borderColor, pillShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = RoundedHighlight(cornerRadius = 18.dp),
                        role = Role.Button,
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onQuickAction(action)
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = descriptor.icon,
                    contentDescription = descriptor.label,
                    tint = descriptor.tint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = descriptor.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (descriptor.active) FontWeight.SemiBold else FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (descriptor.active) Color.White else GlassTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class QuickActionVisual(
    val icon: ImageVector,
    val label: String,
    val active: Boolean,
    val tint: Color
)

private fun resolveQuickActionVisual(
    action: StudioQuickAction,
    liked: Boolean,
    downloadItem: DownloadItem?,
    playbackSpeed: Float,
    sleepTimerActive: Boolean
): QuickActionVisual {
    return when (action) {
        StudioQuickAction.LIKE -> QuickActionVisual(
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = if (liked) "Liked" else "Like",
            active = liked,
            tint = if (liked) DeckLikeTint else GlassTokens.TextSecondary
        )
        StudioQuickAction.LYRICS -> QuickActionVisual(
            icon = Icons.Rounded.Lyrics,
            label = "Lyrics",
            active = false,
            tint = GlassTokens.TextSecondary
        )
        StudioQuickAction.QUEUE -> QuickActionVisual(
            icon = Icons.Rounded.QueueMusic,
            label = "Up Next",
            active = false,
            tint = GlassTokens.TextSecondary
        )
        StudioQuickAction.SHARE -> QuickActionVisual(
            icon = Icons.Rounded.Share,
            label = "Share",
            active = false,
            tint = GlassTokens.TextSecondary
        )
        StudioQuickAction.SPEED -> {
            val speedActive = abs(playbackSpeed - 1f) > 0.01f
            QuickActionVisual(
                icon = Icons.Rounded.Speed,
                label = if (speedActive) String.format(Locale.US, "%.2gx", playbackSpeed) else "Speed",
                active = speedActive,
                tint = if (speedActive) Color.White else GlassTokens.TextSecondary
            )
        }
        StudioQuickAction.STREAM_INFO -> QuickActionVisual(
            icon = Icons.Rounded.GraphicEq,
            label = "Signal",
            active = false,
            tint = GlassTokens.TextSecondary
        )
        StudioQuickAction.LYRICS_OFFSET -> QuickActionVisual(
            icon = Icons.Rounded.Tune,
            label = "Sync",
            active = false,
            tint = GlassTokens.TextSecondary
        )
        StudioQuickAction.SLEEP_TIMER -> QuickActionVisual(
            icon = Icons.Rounded.Bedtime,
            label = "Timer",
            active = sleepTimerActive,
            tint = if (sleepTimerActive) Color.White else GlassTokens.TextSecondary
        )
        StudioQuickAction.EQUALIZER -> QuickActionVisual(
            icon = Icons.Rounded.Equalizer,
            label = "EQ",
            active = false,
            tint = GlassTokens.TextSecondary
        )
        StudioQuickAction.DOWNLOAD -> {
            val status = downloadItem?.status
            val completed = status == DownloadStatus.COMPLETED
            val inProgress = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED
            val failed = status == DownloadStatus.FAILED
            QuickActionVisual(
                icon = when {
                    completed -> Icons.Rounded.CheckCircle
                    inProgress -> Icons.Rounded.Downloading
                    else -> Icons.Rounded.Download
                },
                label = when {
                    completed -> "Saved"
                    inProgress -> "Saving"
                    failed -> "Retry"
                    else -> "Save"
                },
                active = completed,
                tint = when {
                    completed -> Color.White
                    failed -> GlassTokens.ErrorCoral
                    else -> GlassTokens.TextSecondary
                }
            )
        }
    }
}

@Composable
private fun OndaMinimalDeck(
    upNextCount: Int,
    hapticsEnabled: Boolean,
    onOpenQueue: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pillShape = remember { SmoothCornerShape(24.dp) }
    val countChipShape = remember { SmoothCornerShape(10.dp) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .pressBounce(
                    interactionSource = interactionSource,
                    pressedScale = 0.95f,
                    enabled = !reducedMotion
                )
                .clip(pillShape)
                .background(DeckCardSurface, pillShape)
                .border(1.dp, DeckHairlineBorder, pillShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = RoundedHighlight(cornerRadius = 24.dp),
                    role = Role.Button,
                    onClick = {
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onOpenQueue()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = null,
                tint = GlassTokens.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Up Next · Lyrics",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .clip(countChipShape)
                    .background(DeckChipSurface, countChipShape)
                    .border(1.dp, DeckHairlineBorder, countChipShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                OdometerText(
                    text = upNextCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }
        }
    }
}
