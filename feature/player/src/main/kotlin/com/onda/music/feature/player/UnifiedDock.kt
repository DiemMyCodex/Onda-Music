package com.onda.music.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.LocalOndaColors
import com.onda.music.core.design.LocalOndaPalette
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ondaBackdropBlur
import com.onda.music.core.design.press3D
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.Track
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private enum class MiniDragAxis {
    NONE,
    HORIZONTAL,
    VERTICAL
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
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val settings by viewModel.userSettings.collectAsState()
    val palette by viewModel.artworkPalette.collectAsState()
    val currentColors = OndaTheme.colors
    val dockColors = remember(currentColors, palette) {
        currentColors.copy(
            accent = palette.primary,
            accentSoft = palette.secondary
        )
    }

    val clampedMorph = morphFraction.coerceIn(0f, 1f)
    val dockAlpha = (1f - clampedMorph * 1.65f).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { (clampedMorph * 52f).dp.toPx() }

    if (dockAlpha <= 0.01f) return

    CompositionLocalProvider(
        LocalOndaPalette provides palette,
        LocalOndaColors provides dockColors
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .graphicsLayer {
                    alpha = dockAlpha
                    translationY = slideOffsetPx
                },
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
                    MiniPlayerBar(
                        track = track,
                        isPlaying = playbackState.status == PlaybackStatus.PLAYING,
                        progress = progress,
                        blurEnabled = settings.glassBlurEnabled,
                        hapticsEnabled = settings.hapticsEnabled,
                        morphFraction = clampedMorph,
                        onMorphFractionChange = onMorphFractionChange,
                        onExpandPlayer = onExpandPlayer,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipNext = viewModel::skipNext,
                        onSkipPrevious = viewModel::skipPrevious
                    )
                }
            }

            DockTabPill(
                tabLabels = tabLabels,
                tabIcons = tabIcons,
                selectedTabIndex = selectedTabIndex,
                blurEnabled = settings.glassBlurEnabled,
                hapticsEnabled = settings.hapticsEnabled,
                onSelectTab = onSelectTab
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    blurEnabled: Boolean,
    hapticsEnabled: Boolean,
    morphFraction: Float,
    onMorphFractionChange: (Float) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val palette = OndaTheme.palette
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val horizontalOffset = remember { Animatable(0f) }
    val dragThresholdPx = with(density) { 58.dp.toPx() }
    val expandDistancePx = with(density) { 360.dp.toPx() }
    val shape = RoundedCornerShape(GlassTokens.RadiusLg)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DockMetrics.MiniPlayerHeight)
            .graphicsLayer {
                translationX = horizontalOffset.value
            }
            .press3D(maxTiltDegrees = 4f, pressedScale = 0.985f)
            .then(
                if (blurEnabled) {
                    Modifier.ondaBackdropBlur(radius = 20.dp)
                } else {
                    Modifier
                }
            )
            .smokedGlass(
                shape = shape,
                elevation = GlassElevation.DOCK,
                accentTint = palette.primary
            )
            .pointerInput(track.id, morphFraction) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var axis = MiniDragAxis.NONE
                    var totalDx = 0f
                    var totalDy = 0f
                    var startMorph = morphFraction

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            when (axis) {
                                MiniDragAxis.NONE -> {
                                    if (!change.isConsumed) {
                                        onExpandPlayer()
                                    }
                                }
                                MiniDragAxis.HORIZONTAL -> {
                                    if (totalDx <= -dragThresholdPx) {
                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSkipNext()
                                    } else if (totalDx >= dragThresholdPx) {
                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSkipPrevious()
                                    }
                                    scope.launch {
                                        horizontalOffset.animateTo(0f)
                                    }
                                }
                                MiniDragAxis.VERTICAL -> {
                                    val draggedUpFraction = (startMorph + (-totalDy / expandDistancePx)).coerceIn(0f, 1f)
                                    if (draggedUpFraction >= 0.24f) {
                                        onMorphFractionChange(1f)
                                        onExpandPlayer()
                                    } else {
                                        onMorphFractionChange(0f)
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
                                    MiniDragAxis.HORIZONTAL
                                } else {
                                    startMorph = morphFraction
                                    MiniDragAxis.VERTICAL
                                }
                            }
                        }

                        when (axis) {
                            MiniDragAxis.HORIZONTAL -> {
                                change.consume()
                                scope.launch {
                                    horizontalOffset.snapTo(totalDx.coerceIn(-dragThresholdPx * 1.4f, dragThresholdPx * 1.4f))
                                }
                            }
                            MiniDragAxis.VERTICAL -> {
                                change.consume()
                                val nextMorph = (startMorph + (-totalDy / expandDistancePx)).coerceIn(0f, 1f)
                                onMorphFractionChange(nextMorph)
                            }
                            MiniDragAxis.NONE -> Unit
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                shape = RoundedCornerShape(GlassTokens.RadiusSm),
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = GlassTokens.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTogglePlayPause()
                },
                modifier = Modifier.defaultMinSize(
                    minWidth = DockMetrics.MinTapTarget,
                    minHeight = DockMetrics.MinTapTarget
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = GlassTokens.TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSkipNext()
                },
                modifier = Modifier.defaultMinSize(
                    minWidth = DockMetrics.MinTapTarget,
                    minHeight = DockMetrics.MinTapTarget
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next track",
                    tint = GlassTokens.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        MiniWavyGlowProgress(
            progress = progress,
            isPlaying = isPlaying,
            accentColor = palette.primary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(6.dp)
                .padding(horizontal = 14.dp)
        )
    }
}

@Composable
private fun MiniWavyGlowProgress(
    progress: Float,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    var phase by remember { mutableFloatStateOf(0f) }
    val wavePath = remember { Path() }

    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    phase = (phase + dt * 5f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    Canvas(modifier = modifier) {
        val yMid = size.height * 0.6f
        val endX = size.width * progress.coerceIn(0f, 1f)

        drawLine(
            color = GlassTokens.GlassHighlight.copy(alpha = 0.14f),
            start = Offset(0f, yMid),
            end = Offset(size.width, yMid),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        if (endX <= 1f) return@Canvas

        if (!isPlaying || reducedMotion) {
            drawLine(
                color = accentColor.copy(alpha = 0.35f),
                start = Offset(0f, yMid),
                end = Offset(endX, yMid),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentColor,
                start = Offset(0f, yMid),
                end = Offset(endX, yMid),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            wavePath.reset()
            val amp = 1.4.dp.toPx()
            val freq = 0.18f
            var x = 0f
            wavePath.moveTo(0f, yMid)
            while (x <= endX) {
                val edge = minOf(x, endX - x) / 12f
                val env = edge.coerceIn(0f, 1f)
                val y = yMid + sin(x * freq - phase) * amp * env
                wavePath.lineTo(x, y)
                x += 3f
            }
            wavePath.lineTo(endX, yMid)

            drawPath(
                path = wavePath,
                color = accentColor.copy(alpha = 0.34f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = wavePath,
                color = accentColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun DockTabPill(
    tabLabels: List<String>,
    tabIcons: List<ImageVector>,
    selectedTabIndex: Int,
    blurEnabled: Boolean,
    hapticsEnabled: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val palette = OndaTheme.palette
    val haptics = LocalHapticFeedback.current
    val itemCount = minOf(tabLabels.size, tabIcons.size)
    if (itemCount == 0) return

    val pillShape = RoundedCornerShape(GlassTokens.RadiusPill)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DockMetrics.NavBarHeight)
            .then(
                if (blurEnabled) {
                    Modifier.ondaBackdropBlur(radius = 24.dp)
                } else {
                    Modifier
                }
            )
            .smokedGlass(
                shape = pillShape,
                elevation = GlassElevation.DOCK,
                accentTint = palette.secondary
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (index in 0 until itemCount) {
            val selected = index == selectedTabIndex
            val label = tabLabels[index]
            val icon = tabIcons[index]
            val contentTint by animateColorAsState(
                targetValue = if (selected) palette.primary else GlassTokens.TextSecondary,
                label = "DockTabTint"
            )
            val indicatorAlpha = if (selected) 0.16f else 0f

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .clip(pillShape)
                    .background(palette.primary.copy(alpha = indicatorAlpha))
                    .clickable {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(index)
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentTint,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = contentTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
