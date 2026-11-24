package com.onda.music.feature.player.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaPagerIndicator
import com.onda.music.core.design.OndaSwitch
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.BedsideTint
import com.onda.music.core.model.IpodFinish
import com.onda.music.core.model.PlayerStudioConfig
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.StudioArtworkFrame
import com.onda.music.core.model.StudioBackdropMode
import com.onda.music.core.model.StudioBottomDeckMode
import com.onda.music.core.model.StudioQuickAction
import com.onda.music.core.model.StudioSeekbarStyle
import com.onda.music.core.model.Track
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class StudioCustomizerTab(val label: String) {
    ATMOSPHERE("Atmosphere"),
    CONTROLS("Controls"),
    ACTIONS("Actions"),
    STYLE("Style")
}

private val StudioObsidianBackdrop = Color(0xFF070709)
private val StudioCardSurface = Color(0xFF141414)
private val StudioElevatedSurface = Color(0xFF161619)
private val StudioSelectedChipSurface = Color(0xFFFFFFFF)
private val StudioMutedText = Color(0xFF9E9E9E)
private val StudioBorderSubtle = Color(0xFF26262A)

@Composable
fun OndaStudioOverlay(
    activeStyle: PlayerStyle,
    studioConfig: PlayerStudioConfig,
    currentTrack: Track?,
    palette: OndaArtworkPalette,
    hapticsEnabled: Boolean,
    onSelectStyle: (PlayerStyle) -> Unit,
    onUpdateConfig: (PlayerStudioConfig) -> Unit,
    onDismiss: () -> Unit,
    liveStagePreview: @Composable (PlayerStyle) -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val styles = remember { PlayerStyle.entries }
    val cleanConfig = remember(studioConfig) { studioConfig.sanitized() }

    var isCustomizing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(StudioCustomizerTab.ATMOSPHERE) }
    var selectedActionSlot by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(
        initialPage = activeStyle.ordinal.coerceIn(0, styles.lastIndex),
        pageCount = { styles.size }
    )

    val currentActiveStyle by rememberUpdatedState(activeStyle)
    val currentOnSelectStyle by rememberUpdatedState(onSelectStyle)

    LaunchedEffect(activeStyle) {
        val target = activeStyle.ordinal.coerceIn(0, styles.lastIndex)
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            if (reducedMotion) {
                pagerState.scrollToPage(target)
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .filter { (_, isScrolling) -> !isScrolling }
            .distinctUntilChanged()
            .collect { (settled, _) ->
                val settledStyle = styles.getOrNull(settled) ?: return@collect
                if (settledStyle != currentActiveStyle) {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    currentOnSelectStyle(settledStyle)
                }
            }
    }

    BackHandler {
        if (isCustomizing) {
            isCustomizing = false
        } else {
            onDismiss()
        }
    }

    val focusedStyle = styles.getOrElse(pagerState.currentPage) { activeStyle }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidianBackdrop)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = isCustomizing,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else if (targetState) {
                    (slideInVertically(
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f)
                    ) { it / 14 } + scaleIn(
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f),
                        initialScale = 0.97f
                    ) + fadeIn(tween(200))) togetherWith (scaleOut(
                        animationSpec = tween(150),
                        targetScale = 0.96f
                    ) + fadeOut(tween(140)))
                } else {
                    (scaleIn(
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f),
                        initialScale = 0.96f
                    ) + fadeIn(tween(200))) togetherWith (slideOutVertically(
                        animationSpec = tween(160)
                    ) { it / 14 } + fadeOut(tween(140)))
                }
            },
            label = "OndaStudioPhase"
        ) { customizing ->
            if (!customizing) {
                StudioStageCarouselPhase(
                    styles = styles,
                    pagerState = pagerState,
                    focusedStyle = focusedStyle,
                    currentTrack = currentTrack,
                    palette = palette,
                    reducedMotion = reducedMotion,
                    hapticsEnabled = hapticsEnabled,
                    onSelectPage = { index ->
                        val targetStyle = styles.getOrNull(index)
                        if (targetStyle != null) {
                            scope.launch {
                                if (reducedMotion) {
                                    pagerState.scrollToPage(index)
                                } else {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                            onSelectStyle(targetStyle)
                        }
                    },
                    onOpenCustomize = { isCustomizing = true },
                    onApplyAndDismiss = { styleToApply ->
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onSelectStyle(styleToApply)
                        onDismiss()
                    },
                    onDismiss = onDismiss,
                    liveStagePreview = liveStagePreview
                )
            } else {
                StudioCustomizerPhase(
                    focusedStyle = focusedStyle,
                    cleanConfig = cleanConfig,
                    currentTrack = currentTrack,
                    palette = palette,
                    selectedTab = selectedTab,
                    selectedActionSlot = selectedActionSlot,
                    hapticsEnabled = hapticsEnabled,
                    onSelectTab = { selectedTab = it },
                    onSelectActionSlot = { selectedActionSlot = it },
                    onUpdateConfig = onUpdateConfig,
                    onBackToCarousel = { isCustomizing = false },
                    onDone = {
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onSelectStyle(focusedStyle)
                        onDismiss()
                    },
                    liveStagePreview = liveStagePreview
                )
            }
        }
    }
}

@Composable
private fun StudioStageCarouselPhase(
    styles: List<PlayerStyle>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    focusedStyle: PlayerStyle,
    currentTrack: Track?,
    palette: OndaArtworkPalette,
    reducedMotion: Boolean,
    hapticsEnabled: Boolean,
    onSelectPage: (Int) -> Unit,
    onOpenCustomize: () -> Unit,
    onApplyAndDismiss: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit,
    liveStagePreview: @Composable (PlayerStyle) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val closeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .pressBounce(closeInteraction, pressedScale = 0.94f)
                    .clip(CircleShape)
                    .background(StudioElevatedSurface)
                    .border(1.dp, StudioBorderSubtle, CircleShape)
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = RoundedHighlight(cornerRadius = 999.dp),
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Onda Studio",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OndaMonogramMark(
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Onda Studio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "ACOUSTIC ARCHITECTURE",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.1.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = StudioMutedText,
                        maxLines = 1
                    )
                }
            }

            val customizeInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    .pressBounce(customizeInteraction, pressedScale = 0.96f)
                    .clip(SmoothCornerShape(999.dp))
                    .background(StudioElevatedSurface)
                    .border(1.dp, StudioBorderSubtle, SmoothCornerShape(999.dp))
                    .clickable(
                        interactionSource = customizeInteraction,
                        indication = RoundedHighlight(cornerRadius = 999.dp),
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onOpenCustomize()
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Customize",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 50.dp),
                pageSpacing = 14.dp,
                key = { page -> styles.getOrNull(page)?.name ?: page }
            ) { page ->
                val style = styles.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = pagerState.currentPage == page
                val dragOffsetY = remember(style) { Animatable(0f) }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                            val scale = if (reducedMotion) 1f else lerp(1f, 0.88f, absOffset)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.50f, absOffset)
                            translationY = dragOffsetY.value
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val targetAspect = 390f / 810f
                    val availableW = maxWidth
                    val availableH = (maxHeight * 0.96f).coerceAtLeast(160.dp)
                    val cardW: Dp
                    val cardH: Dp
                    if (availableW / availableH > targetAspect) {
                        cardH = availableH
                        cardW = cardH * targetAspect
                    } else {
                        cardW = availableW
                        cardH = cardW / targetAspect
                    }

                    val frameShape = SmoothCornerShape(30.dp)
                    val borderColor = if (isCurrentPage) Color(0x30FFFFFF) else Color(0x14FFFFFF)

                    Box(
                        modifier = Modifier
                            .size(width = cardW, height = cardH)
                            .clip(frameShape)
                            .background(Color(0xFF0A0A0C))
                            .border(1.dp, borderColor, frameShape)
                    ) {
                        StudioStagePreviewFrame(
                            style = style,
                            modifier = Modifier.fillMaxSize(),
                            liveStagePreview = liveStagePreview
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(style, isCurrentPage, reducedMotion, hapticsEnabled) {
                                    val touchSlop = viewConfiguration.touchSlop
                                    val dismissThresholdPx = 64.dp.toPx()
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var totalDx = 0f
                                        var totalDy = 0f
                                        var decided = false
                                        var trackingVertical = false
                                        var released = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                released = true
                                                break
                                            }
                                            val stepDelta = change.position - change.previousPosition
                                            totalDx += stepDelta.x
                                            totalDy += stepDelta.y
                                            if (!decided) {
                                                if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)) {
                                                    decided = true
                                                    trackingVertical = false
                                                } else if (totalDy > touchSlop && totalDy > abs(totalDx) * 1.15f) {
                                                    decided = true
                                                    trackingVertical = true
                                                    change.consume()
                                                } else if (change.isConsumed) {
                                                    decided = true
                                                    trackingVertical = false
                                                }
                                            } else if (trackingVertical) {
                                                change.consume()
                                                val clamped = totalDy.coerceAtLeast(0f)
                                                scope.launch {
                                                    dragOffsetY.snapTo(clamped)
                                                }
                                            }
                                        }
                                        if (trackingVertical) {
                                            if (totalDy > dismissThresholdPx) {
                                                onApplyAndDismiss(style)
                                            } else {
                                                scope.launch {
                                                    if (reducedMotion) {
                                                        dragOffsetY.snapTo(0f)
                                                    } else {
                                                        dragOffsetY.animateTo(0f, AppMotion.ExpressiveSpring)
                                                    }
                                                }
                                            }
                                        } else if (
                                            released &&
                                            !decided &&
                                            abs(totalDx) <= touchSlop &&
                                            abs(totalDy) <= touchSlop
                                        ) {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            if (isCurrentPage) {
                                                onOpenCustomize()
                                            } else {
                                                onSelectPage(page)
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                )
                Text(
                    text = focusedStyle.label,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "%02d/%02d",
                        focusedStyle.ordinal + 1,
                        styles.size
                    ),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    ),
                    color = StudioMutedText
                )
            }

            Text(
                text = studioStyleDescriptor(focusedStyle),
                style = MaterialTheme.typography.bodySmall,
                color = StudioMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (currentTrack != null) {
                Text(
                    text = "${currentTrack.title} - ${currentTrack.artist}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = Color(0xFF707078),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OndaPagerIndicator(
                count = styles.size,
                positionProvider = {
                    if (reducedMotion) {
                        pagerState.currentPage.toFloat()
                    } else {
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    }
                },
                activeWidth = 20.dp,
                inactiveWidth = 6.dp,
                dotHeight = 6.dp,
                spacing = 6.dp,
                activeColor = Color.White,
                inactiveColor = Color(0xFF34343A),
                loop = false,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val customizeBtnInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .pressBounce(customizeBtnInteraction, pressedScale = 0.97f)
                        .clip(SmoothCornerShape(18.dp))
                        .background(StudioElevatedSurface)
                        .border(1.dp, StudioBorderSubtle, SmoothCornerShape(18.dp))
                        .clickable(
                            interactionSource = customizeBtnInteraction,
                            indication = RoundedHighlight(cornerRadius = 18.dp),
                            onClick = {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onOpenCustomize()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Customize",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }

                val applyBtnInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .pressBounce(applyBtnInteraction, pressedScale = 0.97f)
                        .clip(SmoothCornerShape(18.dp))
                        .background(Color.White)
                        .clickable(
                            interactionSource = applyBtnInteraction,
                            indication = RoundedHighlight(cornerRadius = 18.dp, color = Color.Black.copy(alpha = 0.1f)),
                            onClick = { onApplyAndDismiss(focusedStyle) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color(0xFF0E0E10),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Apply Style",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF0E0E10),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioCustomizerPhase(
    focusedStyle: PlayerStyle,
    cleanConfig: PlayerStudioConfig,
    currentTrack: Track?,
    palette: OndaArtworkPalette,
    selectedTab: StudioCustomizerTab,
    selectedActionSlot: Int,
    hapticsEnabled: Boolean,
    onSelectTab: (StudioCustomizerTab) -> Unit,
    onSelectActionSlot: (Int) -> Unit,
    onUpdateConfig: (PlayerStudioConfig) -> Unit,
    onBackToCarousel: () -> Unit,
    onDone: () -> Unit,
    liveStagePreview: @Composable (PlayerStyle) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val previewBoxHeight = (maxHeight * 0.27f).coerceIn(156.dp, 210.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                        .pressBounce(backInteraction, pressedScale = 0.96f)
                        .clip(SmoothCornerShape(999.dp))
                        .background(StudioElevatedSurface)
                        .border(1.dp, StudioBorderSubtle, SmoothCornerShape(999.dp))
                        .clickable(
                            interactionSource = backInteraction,
                            indication = RoundedHighlight(cornerRadius = 999.dp),
                            onClick = onBackToCarousel
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to stage carousel",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Stages",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = focusedStyle.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = currentTrack?.title ?: "ONDA STUDIO",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = StudioMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val doneInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 72.dp, minHeight = DockMetrics.MinTapTarget)
                        .pressBounce(doneInteraction, pressedScale = 0.96f)
                        .clip(SmoothCornerShape(999.dp))
                        .background(Color.White)
                        .clickable(
                            interactionSource = doneInteraction,
                            indication = RoundedHighlight(cornerRadius = 999.dp, color = Color.Black.copy(alpha = 0.1f)),
                            onClick = onDone
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Apply",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF0E0E10),
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewBoxHeight)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val frameHeight = (previewBoxHeight - 8.dp).coerceAtLeast(140.dp)
                val frameWidth = frameHeight * (390f / 810f)
                val previewShape = SmoothCornerShape(22.dp)

                Box(
                    modifier = Modifier
                        .size(width = frameWidth, height = frameHeight)
                        .clip(previewShape)
                        .background(Color(0xFF0A0A0C))
                        .border(1.dp, Color(0x30FFFFFF), previewShape)
                ) {
                    StudioStagePreviewFrame(
                        style = focusedStyle,
                        modifier = Modifier.fillMaxSize(),
                        liveStagePreview = liveStagePreview
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                        if (event.changes.none { it.pressed }) break
                                    }
                                }
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val deckShape = SmoothCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 8.dp)
                    .clip(deckShape)
                    .background(StudioCardSurface)
                    .border(1.dp, StudioBorderSubtle, deckShape)
                    .padding(top = 12.dp, start = 14.dp, end = 14.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioCustomizerTab.entries.forEach { tab ->
                        val active = tab == selectedTab
                        val tabInteraction = remember { MutableInteractionSource() }
                        val tabShape = SmoothCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                .pressBounce(tabInteraction, pressedScale = 0.96f)
                                .clip(tabShape)
                                .background(if (active) StudioSelectedChipSurface else StudioElevatedSurface)
                                .border(
                                    1.dp,
                                    if (active) Color.White else StudioBorderSubtle,
                                    tabShape
                                )
                                .clickable(
                                    interactionSource = tabInteraction,
                                    indication = RoundedHighlight(cornerRadius = 16.dp),
                                    onClick = {
                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSelectTab(tab)
                                    }
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (active) Color(0xFF0E0E10) else StudioMutedText,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        StudioCustomizerTab.ATMOSPHERE -> {
                            StudioSectionHeader("BACKDROP ENGINE")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StudioBackdropMode.entries.forEach { mode ->
                                    StudioOptionChip(
                                        label = mode.label,
                                        selected = cleanConfig.backdropMode == mode,
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(backdropMode = mode))
                                        }
                                    )
                                }
                            }

                            StudioSectionHeader("ARTWORK FRAME")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StudioArtworkFrame.entries.forEach { frame ->
                                    StudioOptionChip(
                                        label = frame.label,
                                        selected = cleanConfig.artworkFrame == frame,
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(artworkFrame = frame))
                                        }
                                    )
                                }
                            }
                        }

                        StudioCustomizerTab.CONTROLS -> {
                            StudioSectionHeader("SEEKBAR STYLE")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StudioSeekbarStyle.entries.forEach { seekStyle ->
                                    StudioOptionChip(
                                        label = seekStyle.label,
                                        selected = cleanConfig.seekbarStyle == seekStyle,
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(seekbarStyle = seekStyle))
                                        }
                                    )
                                }
                            }

                            StudioSectionHeader("BOTTOM DECK")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StudioBottomDeckMode.entries.forEach { deckMode ->
                                    StudioOptionChip(
                                        label = deckMode.label,
                                        selected = cleanConfig.bottomDeckMode == deckMode,
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(bottomDeckMode = deckMode))
                                        }
                                    )
                                }
                            }

                            var localShiftDp by remember { mutableIntStateOf(cleanConfig.controlsShiftDp) }
                            var isDraggingShift by remember { mutableStateOf(false) }
                            LaunchedEffect(cleanConfig.controlsShiftDp, isDraggingShift) {
                                if (!isDraggingShift) {
                                    localShiftDp = cleanConfig.controlsShiftDp
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SmoothCornerShape(18.dp))
                                    .background(StudioElevatedSurface)
                                    .border(1.dp, StudioBorderSubtle, SmoothCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "VERTICAL SHIFT",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.8.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = StudioMutedText
                                    )
                                    val sign = if (localShiftDp > 0) "+" else ""
                                    Text(
                                        text = "$sign$localShiftDp dp",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = Color.White
                                    )
                                }

                                Slider(
                                    value = localShiftDp.toFloat(),
                                    onValueChange = { raw ->
                                        isDraggingShift = true
                                        val nextShift = raw.roundToInt().coerceIn(
                                            PlayerStudioConfig.MIN_CONTROLS_SHIFT_DP,
                                            PlayerStudioConfig.MAX_CONTROLS_SHIFT_DP
                                        )
                                        if (nextShift != localShiftDp) {
                                            if (hapticsEnabled) {
                                                val tickType = if (nextShift == 0) {
                                                    HapticFeedbackType.LongPress
                                                } else {
                                                    HapticFeedbackType.TextHandleMove
                                                }
                                                haptics.performHapticFeedback(tickType)
                                            }
                                            localShiftDp = nextShift
                                            onUpdateConfig(cleanConfig.copy(controlsShiftDp = nextShift))
                                        }
                                    },
                                    onValueChangeFinished = { isDraggingShift = false },
                                    valueRange = PlayerStudioConfig.MIN_CONTROLS_SHIFT_DP.toFloat()..
                                        PlayerStudioConfig.MAX_CONTROLS_SHIFT_DP.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color(0xFF2E2E32)
                                    )
                                )

                                if (localShiftDp != 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        StudioOptionChip(
                                            label = "Center 0 dp",
                                            selected = false,
                                            onClick = {
                                                if (hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                localShiftDp = 0
                                                onUpdateConfig(cleanConfig.copy(controlsShiftDp = 0))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        StudioCustomizerTab.ACTIONS -> {
                            StudioSectionHeader("QUICK ACTION SLOTS")
                            val slotActions = cleanConfig.quickActions
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (rowIndex in 0 until 2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (colIndex in 0 until 2) {
                                            val slotIndex = rowIndex * 2 + colIndex
                                            val assigned = slotActions.getOrNull(slotIndex)
                                            val isSlotSelected = selectedActionSlot == slotIndex
                                            val slotInteraction = remember { MutableInteractionSource() }
                                            val slotShape = SmoothCornerShape(16.dp)
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .defaultMinSize(minHeight = 56.dp)
                                                    .pressBounce(slotInteraction, pressedScale = 0.97f)
                                                    .clip(slotShape)
                                                    .background(
                                                        if (isSlotSelected) Color.White else StudioElevatedSurface
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSlotSelected) Color.White else StudioBorderSubtle,
                                                        slotShape
                                                    )
                                                    .clickable(
                                                        interactionSource = slotInteraction,
                                                        indication = RoundedHighlight(cornerRadius = 16.dp),
                                                        onClick = {
                                                            if (hapticsEnabled) {
                                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                            onSelectActionSlot(slotIndex)
                                                        }
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "SLOT 0${slotIndex + 1}",
                                                    style = TextStyle(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        letterSpacing = 0.7.sp,
                                                        fontFeatureSettings = "tnum"
                                                    ),
                                                    color = if (isSlotSelected) Color(0xFF4A4A50) else StudioMutedText,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = assigned?.label ?: "--",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isSlotSelected) Color(0xFF0E0E10) else Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            StudioSectionHeader("ASSIGN TO SLOT 0${selectedActionSlot + 1}")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StudioQuickAction.entries.forEach { action ->
                                    val currentSlotAction = slotActions.getOrNull(selectedActionSlot)
                                    val isInCurrentSlot = currentSlotAction == action
                                    val assignedSlotIndex = slotActions.indexOf(action)
                                    val chipLabel = if (assignedSlotIndex >= 0 && !isInCurrentSlot) {
                                        "${action.label} (${assignedSlotIndex + 1})"
                                    } else {
                                        action.label
                                    }
                                    StudioOptionChip(
                                        label = chipLabel,
                                        selected = isInCurrentSlot,
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(
                                                cleanConfig.withSwappedQuickAction(selectedActionSlot, action)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        StudioCustomizerTab.STYLE -> {
                            StudioSectionHeader("IPOD CHASSIS FINISH")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IpodFinish.entries.forEach { finish ->
                                    val swatch = when (finish) {
                                        IpodFinish.OBSIDIAN -> Color(0xFF1A1A1E)
                                        IpodFinish.SILVER -> Color(0xFFD0D3D8)
                                        IpodFinish.CRIMSON -> Color(0xFF8F2230)
                                        IpodFinish.MIDNIGHT -> Color(0xFF1E2D45)
                                    }
                                    StudioOptionChip(
                                        label = finish.label,
                                        selected = cleanConfig.ipodFinish == finish,
                                        leadingSwatch = swatch,
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(ipodFinish = finish))
                                        }
                                    )
                                }
                            }

                            StudioSectionHeader("BEDSIDE CLOCK TINT")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BedsideTint.entries.forEach { tint ->
                                    val swatch = when (tint) {
                                        BedsideTint.AMBER -> Color(0xFFFBBF24)
                                        BedsideTint.CRIMSON -> Color(0xFFEF4444)
                                        BedsideTint.ARTWORK -> palette.primary
                                    }
                                    StudioOptionChip(
                                        label = tint.label,
                                        selected = cleanConfig.bedsideTint == tint,
                                        leadingSwatch = swatch,
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(bedsideTint = tint))
                                        }
                                    )
                                }
                            }

                            Surface(
                                color = StudioElevatedSurface,
                                shape = SmoothCornerShape(20.dp),
                                border = BorderStroke(1.dp, StudioBorderSubtle)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    StudioToggleRow(
                                        title = "OLED Burn-In Guard",
                                        subtitle = "Micro-shifts bedside telemetry over time",
                                        checked = cleanConfig.bedsideBurnInGuard,
                                        onCheckedChange = { enabled ->
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(bedsideBurnInGuard = enabled))
                                        }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFF222226))
                                    )
                                    StudioToggleRow(
                                        title = "Cinema Smart Fill",
                                        subtitle = "Adapts video canvas aspect cleanly",
                                        checked = cleanConfig.videoSmartFill,
                                        onCheckedChange = { enabled ->
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(videoSmartFill = enabled))
                                        }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFF222226))
                                    )
                                    StudioToggleRow(
                                        title = "Cinema Ambient Glow",
                                        subtitle = "Radiates edge luminance around video frame",
                                        checked = cleanConfig.videoAmbientGlow,
                                        onCheckedChange = { enabled ->
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onUpdateConfig(cleanConfig.copy(videoAmbientGlow = enabled))
                                        }
                                    )
                                }
                            }

                            val resetInteraction = remember { MutableInteractionSource() }
                            val resetShape = SmoothCornerShape(18.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                    .pressBounce(resetInteraction, pressedScale = 0.97f)
                                    .clip(resetShape)
                                    .background(Color(0xFF1D1D22))
                                    .border(1.dp, StudioBorderSubtle, resetShape)
                                    .clickable(
                                        interactionSource = resetInteraction,
                                        indication = RoundedHighlight(cornerRadius = 18.dp),
                                        onClick = {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            onUpdateConfig(PlayerStudioConfig())
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset Studio Defaults",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StudioStagePreviewFrame(
    style: PlayerStyle,
    modifier: Modifier = Modifier,
    virtualWidthDp: Dp = 390.dp,
    virtualHeightDp: Dp = 810.dp,
    liveStagePreview: @Composable (PlayerStyle) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val scaleX = (maxWidth / virtualWidthDp).coerceAtLeast(0.1f)
        val scaleY = (maxHeight / virtualHeightDp).coerceAtLeast(0.1f)
        val scale = minOf(scaleX, scaleY)
        Box(
            modifier = Modifier
                .requiredSize(virtualWidthDp, virtualHeightDp)
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                }
        ) {
            liveStagePreview(style)
        }
    }
}

@Composable
private fun StudioSectionHeader(title: String) {
    Text(
        text = title,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
            fontFeatureSettings = "tnum"
        ),
        color = StudioMutedText
    )
}

@Composable
private fun StudioOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingSwatch: Color? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = SmoothCornerShape(16.dp)
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .pressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .background(if (selected) StudioSelectedChipSurface else StudioElevatedSurface)
            .border(1.dp, if (selected) Color.White else StudioBorderSubtle, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 16.dp),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingSwatch != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(leadingSwatch)
                    .border(
                        1.dp,
                        if (selected) Color(0xFF0E0E10).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.25f),
                        CircleShape
                    )
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color(0xFF0E0E10) else Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun StudioToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = StudioMutedText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        OndaSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun studioStyleDescriptor(style: PlayerStyle): String {
    return when (style) {
        PlayerStyle.FLAGSHIP -> "Obsidian flagship stage & album carousel"
        PlayerStyle.CLASSIC -> "Balanced studio framing & crisp telemetry"
        PlayerStyle.POSTER -> "Full-bleed curved artwork & kinetic typography"
        PlayerStyle.TURNTABLE -> "Direct-drive tonearm scrubbing & vinyl sheen"
        PlayerStyle.VIDEO -> "Cinema viewport with adaptive ambient glow"
        PlayerStyle.IPOD -> "Onda Dial rotary wheel & 3D sleeve flip"
        PlayerStyle.TIMELINE -> "Horizontal queue rail & live track scrubber"
        PlayerStyle.LYRICS -> "Full-stage karaoke & word-synced focus"
        PlayerStyle.BEDSIDE -> "Nightstand acoustic clock with burn-in guard"
        PlayerStyle.BEDSIDE_TILES -> "Split clock matrix & quick status tiles"
    }
}
