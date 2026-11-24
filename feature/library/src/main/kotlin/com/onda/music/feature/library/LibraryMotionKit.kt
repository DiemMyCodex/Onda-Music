package com.onda.music.feature.library

import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

private val LibraryRevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val LibraryHighlightColor = Color.White.copy(alpha = 0.11f)
private val StaticLibraryEqualizerFractions = floatArrayOf(0.45f, 0.85f, 0.62f, 0.94f, 0.50f)

internal object LibraryBlurCache {
    private val effects = LruCache<Int, RenderEffect>(24)

    @RequiresApi(Build.VERSION_CODES.S)
    fun blur(radiusPx: Float): RenderEffect? {
        val quantized = (radiusPx / 3f).roundToInt() * 3
        if (quantized < 2) return null
        effects.get(quantized)?.let { return it }
        val created = android.graphics.RenderEffect.createBlurEffect(
            quantized.toFloat(),
            quantized.toFloat(),
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
        effects.put(quantized, created)
        return created
    }
}

@Immutable
internal class LibraryHighlightIndication(
    private val cornerRadius: Dp = 16.dp,
    private val spillX: Dp = 0.dp,
    private val spillY: Dp = 0.dp,
    private val color: Color = LibraryHighlightColor
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return LibraryHighlightNode(interactionSource, cornerRadius, spillX, spillY, color)
    }

    override fun equals(other: Any?): Boolean {
        return other is LibraryHighlightIndication &&
            other.cornerRadius == cornerRadius &&
            other.spillX == spillX &&
            other.spillY == spillY &&
            other.color == color
    }

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + spillX.hashCode()
        result = 31 * result + spillY.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }
}

private class LibraryHighlightNode(
    private val interactionSource: InteractionSource,
    private val cornerRadius: Dp,
    private val spillX: Dp,
    private val spillY: Dp,
    private val color: Color
) : Modifier.Node(), DrawModifierNode {
    private val strength = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            val presses = ArrayList<PressInteraction.Press>()
            var focused = false
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    else -> return@collect
                }
                if (presses.isNotEmpty() || focused) {
                    launch { strength.animateTo(1f, tween(85)) }
                } else {
                    launch {
                        if (strength.value < 1f) strength.animateTo(1f, tween(65))
                        strength.animateTo(0f, tween(300))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = strength.value
        if (s > 0f) {
            val dx = spillX.toPx()
            val dy = spillY.toPx()
            drawRoundRect(
                color = color.copy(alpha = color.alpha * s),
                topLeft = Offset(-dx, -dy),
                size = Size(size.width + dx * 2f, size.height + dy * 2f),
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        }
        drawContent()
    }
}

@Composable
internal fun Modifier.libraryPressBounce(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val reducedMotion = OndaTheme.reducedMotion
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource, pressedScale, reducedMotion) {
        if (reducedMotion) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        var pressed = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressed++
                    launch { scale.animateTo(pressedScale, spring(dampingRatio = 1f, stiffness = 1500f)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = (pressed - 1).coerceAtLeast(0)
                    if (pressed == 0) {
                        launch {
                            if (scale.value > pressedScale + 0.02f) {
                                scale.animateTo(pressedScale, tween(65))
                            }
                            scale.animateTo(1f, spring(dampingRatio = 0.44f, stiffness = 520f))
                        }
                    }
                }
            }
        }
    }
    return this.graphicsLayer {
        val s = if (reducedMotion) 1f else scale.value
        scaleX = s
        scaleY = s
    }
}

@Stable
internal class LibraryRevealSeenState(initial: Collection<String> = emptyList()) {
    private val seen = HashSet(initial)
    private var burstStartedAt = 0L
    private var burstCount = 0

    fun hasSeen(key: String): Boolean = key in seen

    fun markSeen(key: String) {
        seen.add(key)
    }

    fun nextBurstDelayMs(): Int {
        val now = SystemClock.uptimeMillis()
        if (now - burstStartedAt > 120L) {
            burstStartedAt = now
            burstCount = 0
        }
        return min(burstCount++, 6) * 50
    }

    companion object {
        val Saver: Saver<LibraryRevealSeenState, ArrayList<String>> = Saver(
            save = { ArrayList(it.seen) },
            restore = { LibraryRevealSeenState(it) }
        )
    }
}

@Composable
internal fun rememberLibraryRevealSeenState(): LibraryRevealSeenState {
    return rememberSaveable(saver = LibraryRevealSeenState.Saver) { LibraryRevealSeenState() }
}

@Composable
internal fun Modifier.libraryRevealOnAppear(
    key: String,
    seenState: LibraryRevealSeenState,
    cascade: Boolean = true
): Modifier {
    val reducedMotion = OndaTheme.reducedMotion
    val alreadySeen = remember(key) { seenState.hasSeen(key) }
    if (alreadySeen || reducedMotion) {
        LaunchedEffect(key, reducedMotion) {
            if (reducedMotion) seenState.markSeen(key)
        }
        return this
    }

    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        seenState.markSeen(key)
        val wait = if (cascade) seenState.nextBurstDelayMs() else 0
        progress.animateTo(1f, tween(420, delayMillis = wait, easing = LibraryRevealEasing))
    }

    val slidePx = with(LocalDensity.current) { 16.dp.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * slidePx
    }
}

@Composable
internal fun rememberLibraryCollapsed(
    listState: LazyListState,
    enterDp: Dp = 24.dp,
    exitDp: Dp = 6.dp
): State<Boolean> {
    val density = LocalDensity.current
    val enterPx = with(density) { enterDp.toPx() }
    val exitPx = with(density) { exitDp.toPx() }
    val latch = remember(listState) { booleanArrayOf(false) }
    return remember(listState, enterPx, exitPx) {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val next = when {
                listState.firstVisibleItemIndex > 0 -> true
                offset > enterPx -> true
                offset < exitPx -> false
                else -> latch[0]
            }
            latch[0] = next
            next
        }
    }
}

@Composable
internal fun rememberLibraryCollapseProgress(
    isCollapsed: Boolean
): State<Float> {
    val reducedMotion = OndaTheme.reducedMotion
    return animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 440f)
        },
        label = "libraryHeaderCollapse"
    )
}

@Composable
internal fun LibraryPlayingIndicator(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 5,
    barWidth: Dp = 2.dp,
    spacing: Dp = 1.5.dp
) {
    val reducedMotion = OndaTheme.reducedMotion
    val levels = remember(bars) {
        List(bars) { idx -> Animatable(StaticLibraryEqualizerFractions[idx % StaticLibraryEqualizerFractions.size]) }
    }

    LaunchedEffect(isPlaying, reducedMotion, levels) {
        if (!isPlaying || reducedMotion) {
            levels.forEachIndexed { idx, anim ->
                anim.snapTo(StaticLibraryEqualizerFractions[idx % StaticLibraryEqualizerFractions.size])
            }
            return@LaunchedEffect
        }
        levels.forEachIndexed { idx, level ->
            launch {
                while (true) {
                    level.animateTo(
                        targetValue = 0.24f + Random.nextFloat() * 0.76f,
                        animationSpec = tween(
                            durationMillis = 150 + (idx * 22 + Random.nextInt(130)),
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }
    }

    Canvas(modifier = modifier.width(barWidth * bars + spacing * (bars - 1))) {
        val w = barWidth.toPx()
        val gap = spacing.toPx()
        val radius = CornerRadius(w / 2f)
        levels.forEachIndexed { index, level ->
            val h = (size.height * level.value).coerceAtLeast(w)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (w + gap), (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = radius
            )
        }
    }
}

@Composable
internal fun LibraryCoverStack(
    covers: List<String?>,
    modifier: Modifier = Modifier,
    tile: Dp = 40.dp,
    cornerRadius: Dp = 8.dp,
    showFan: Boolean = true,
    round: Boolean = false,
    fallbackIcon: ImageVector = Icons.AutoMirrored.Rounded.QueueMusic
) {
    val shown = remember(covers) { covers.filterNotNull().filter { it.isNotBlank() }.distinct().take(3) }
    val shape = if (round) CircleShape else SmoothCornerShape(cornerRadius.coerceAtLeast(0.dp))

    if (shown.isEmpty()) {
        val stackWidth = if (showFan) tile * 1.45f else tile
        Box(modifier = modifier.size(width = stackWidth, height = tile)) {
            if (showFan) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = tile * 0.08f, y = 2.dp)
                        .size(tile * 0.88f)
                        .graphicsLayer {
                            rotationZ = if (round) 0f else -10f
                            alpha = 0.65f
                        }
                        .clip(shape)
                        .background(Color(0xFF1C1C20))
                        .border(0.5.dp, Color(0x1CFFFFFF), shape)
                )
            }
            Box(
                modifier = Modifier
                    .align(if (showFan) Alignment.CenterEnd else Alignment.Center)
                    .size(tile)
                    .graphicsLayer {
                        rotationZ = if (showFan && !round) 5f else 0f
                    }
                    .clip(shape)
                    .background(Color(0xFF1E1E24))
                    .border(0.5.dp, Color(0x28FFFFFF), shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = Color(0xCCFFFFFF),
                    modifier = Modifier.size(tile * 0.48f)
                )
            }
        }
        return
    }

    if (!showFan || shown.size == 1) {
        val url = shown.first()
        val stackWidth = if (showFan) tile * 1.38f else tile
        Box(modifier = modifier.size(width = stackWidth, height = tile)) {
            if (showFan) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(tile * 0.88f)
                        .graphicsLayer {
                            rotationZ = if (round) 0f else -9f
                            alpha = 0.45f
                        }
                        .clip(shape)
                        .background(Color(0xFF1C1C20))
                        .border(0.5.dp, Color(0x18FFFFFF), shape)
                )
            }
            Box(
                modifier = Modifier
                    .align(if (showFan) Alignment.CenterEnd else Alignment.Center)
                    .size(tile)
                    .graphicsLayer {
                        rotationZ = if (showFan && !round) 5f else 0f
                    }
                    .clip(shape)
                    .background(Color(0xFF1B1B20))
                    .border(0.5.dp, Color(0x2AFFFFFF), shape)
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    val layers = shown.take(3).reversed()
    val stepOffset = tile * 0.30f
    val totalWidth = tile + stepOffset * (layers.size - 1)

    Box(modifier = modifier.size(width = totalWidth, height = tile)) {
        layers.forEachIndexed { idx, url ->
            val isFront = idx == layers.lastIndex
            val rotation = when (layers.size) {
                2 -> if (idx == 0) -8f else 6f
                else -> when (idx) {
                    0 -> -11f
                    1 -> -2f
                    else -> 7f
                }
            }
            val tileAlpha = when {
                isFront -> 1f
                layers.size == 3 && idx == 0 -> 0.70f
                else -> 0.86f
            }
            val verticalOffset = when {
                isFront -> 0.dp
                layers.size == 3 && idx == 0 -> 3.dp
                else -> 1.5.dp
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = stepOffset * idx, y = verticalOffset)
                    .size(tile)
                    .graphicsLayer {
                        if (!round) rotationZ = rotation
                        alpha = tileAlpha
                    }
                    .clip(shape)
                    .background(Color(0xFF1A1A1F))
                    .border(
                        width = 0.5.dp,
                        color = if (isFront) Color(0x32FFFFFF) else Color(0x20FFFFFF),
                        shape = shape
                    )
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
internal fun DraggableScrollbar(
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White.copy(alpha = 0.42f),
    thumbColorActive: Color = Color.White,
    thumbHeight: Dp = 64.dp,
    thumbWidth: Dp = 6.dp,
    activeThumbWidth: Dp = 8.dp,
    thumbCornerRadius: Dp = 4.dp,
    trackWidth: Dp = 28.dp,
    minItemCountForScroll: Int = 15,
    minScrollRangeForDrag: Int = 4,
    headerItems: Int = 0,
    indexLabelForIndex: ((Int) -> String?)? = null
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }
    var smoothedY by remember { mutableFloatStateOf(0f) }
    var smoothedThumbY by remember { mutableFloatStateOf(0f) }
    var lastThumbPosition by remember { mutableFloatStateOf(0f) }
    val animatedThumbY = remember { Animatable(0f) }
    var lastTargetIndex by remember { mutableIntStateOf(-1) }
    var activeIndicatorIndex by remember { mutableIntStateOf(0) }
    var cachedIndicatorLabel by remember { mutableStateOf<String?>(null) }

    val isUserScrolling by remember(scrollState) {
        derivedStateOf { scrollState.isScrollInProgress }
    }
    val isScrollable by remember(scrollState, minItemCountForScroll, headerItems) {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo.size
            val contentCount = total - headerItems
            contentCount > minItemCountForScroll && contentCount > visible
        }
    }

    if (!isScrollable) return

    val animatedWidth by animateFloatAsState(
        targetValue = with(density) { (if (isDragging) activeThumbWidth else thumbWidth).toPx() },
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "scrollbarThumbWidth"
    )

    val currentIndexLabel = if (isDragging && indexLabelForIndex != null) {
        indexLabelForIndex(activeIndicatorIndex)?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    if (currentIndexLabel != null) {
        cachedIndicatorLabel = currentIndexLabel
    }

    val bubbleProgress by animateFloatAsState(
        targetValue = if (isDragging && currentIndexLabel != null) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else if (isDragging) {
            spring(dampingRatio = 0.72f, stiffness = 560f)
        } else {
            tween(durationMillis = 160)
        },
        label = "scrollbarIndicatorBubble"
    )

    BoxWithConstraints(
        modifier = modifier
            .width(trackWidth)
            .fillMaxHeight()
            .pointerInput(scrollState, headerItems) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        lastTargetIndex = scrollState.firstVisibleItemIndex
                        val viewportHeight = size.height.toFloat().coerceAtLeast(1f)
                        val constThumbHeight = with(density) { thumbHeight.toPx() }
                        val maxThumbY = (viewportHeight - constThumbHeight).coerceAtLeast(1f)
                        smoothedThumbY = (offset.y - constThumbHeight * 0.5f).coerceIn(0f, maxThumbY)
                        val touchProgress = (offset.y / viewportHeight).coerceIn(0f, 1f)
                        smoothedY = touchProgress
                        val totalContentItems = (scrollState.layoutInfo.totalItemsCount - headerItems).coerceAtLeast(1)
                        activeIndicatorIndex = (touchProgress * (totalContentItems - 1))
                            .roundToInt()
                            .coerceIn(0, (totalContentItems - 1).coerceAtLeast(0))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragEnd = {
                        isDragging = false
                        lastScrollTime = 0L
                    },
                    onDragCancel = {
                        isDragging = false
                        lastScrollTime = 0L
                    }
                ) { change, _ ->
                    change.consume()
                    val currentTime = SystemClock.uptimeMillis()
                    val viewportHeight = size.height.toFloat().coerceAtLeast(1f)
                    val constThumbHeight = with(density) { thumbHeight.toPx() }
                    val maxThumbY = (viewportHeight - constThumbHeight).coerceAtLeast(1f)

                    val targetThumbY = (change.position.y - constThumbHeight * 0.5f).coerceIn(0f, maxThumbY)
                    val layoutInfo = scrollState.layoutInfo
                    val totalContentItems = (layoutInfo.totalItemsCount - headerItems).coerceAtLeast(1)

                    val thumbSmoothing = when {
                        totalContentItems < 20 -> 0.25f
                        totalContentItems < 50 -> 0.45f
                        else -> 0.78f
                    }
                    smoothedThumbY = smoothedThumbY * (1f - thumbSmoothing) + targetThumbY * thumbSmoothing

                    val touchProgress = (change.position.y / viewportHeight).coerceIn(0f, 1f)
                    val listSmoothing = when {
                        totalContentItems < 20 -> 0.25f
                        totalContentItems < 50 -> 0.5f
                        else -> 0.85f
                    }
                    smoothedY = smoothedY * (1f - listSmoothing) + touchProgress * listSmoothing
                    activeIndicatorIndex = (smoothedY * (totalContentItems - 1))
                        .roundToInt()
                        .coerceIn(0, (totalContentItems - 1).coerceAtLeast(0))

                    if (currentTime - lastScrollTime < 32L) return@detectDragGestures
                    lastScrollTime = currentTime

                    val visibleItems = layoutInfo.visibleItemsInfo
                    if (visibleItems.isEmpty()) return@detectDragGestures

                    val maxScrollIndex = max(1, totalContentItems - visibleItems.size)
                    if (maxScrollIndex > minScrollRangeForDrag) {
                        val targetIndex = (headerItems + (smoothedY * maxScrollIndex).roundToInt())
                            .coerceIn(headerItems, (layoutInfo.totalItemsCount - 1).coerceAtLeast(headerItems))

                        if (abs(targetIndex - lastTargetIndex) >= 1) {
                            lastTargetIndex = targetIndex
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                runCatching {
                                    scrollState.scrollToItem(index = targetIndex, scrollOffset = 0)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val viewportHeight = with(density) { maxHeight.toPx() }
        val constThumbHeight = with(density) { thumbHeight.toPx() }
        val maxThumbY = (viewportHeight - constThumbHeight).coerceAtLeast(1f)

        val targetThumbY by remember(scrollState, viewportHeight, constThumbHeight, headerItems) {
            derivedStateOf {
                val layoutInfo = scrollState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@derivedStateOf lastThumbPosition
                val totalContentItems = (layoutInfo.totalItemsCount - headerItems).coerceAtLeast(1)
                val maxScrollIndex = max(1, totalContentItems - visibleItems.size)
                if (maxScrollIndex <= minScrollRangeForDrag) return@derivedStateOf lastThumbPosition
                val rawIndex = (scrollState.firstVisibleItemIndex - headerItems).coerceAtLeast(0)
                val progress = (rawIndex.toFloat() / maxScrollIndex).coerceIn(0f, 1f)
                val next = (progress * maxThumbY).coerceIn(0f, maxThumbY)
                lastThumbPosition = next
                next
            }
        }

        LaunchedEffect(targetThumbY, isDragging, isUserScrolling, smoothedThumbY, reducedMotion) {
            when {
                isDragging -> animatedThumbY.snapTo(smoothedThumbY)
                reducedMotion -> animatedThumbY.snapTo(targetThumbY)
                isUserScrolling -> animatedThumbY.snapTo(targetThumbY)
                else -> animatedThumbY.animateTo(
                    targetValue = targetThumbY,
                    animationSpec = spring(stiffness = 180f, dampingRatio = 0.9f)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .width(activeThumbWidth)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
        ) {
            val color = if (isDragging) thumbColorActive else thumbColor
            val radiusPx = thumbCornerRadius.toPx()
            val w = animatedWidth.coerceAtMost(size.width)
            val left = size.width - w
            drawRoundRect(
                color = color,
                topLeft = Offset(left, animatedThumbY.value.coerceIn(0f, (size.height - constThumbHeight).coerceAtLeast(0f))),
                size = Size(w, constThumbHeight.coerceAtMost(size.height)),
                cornerRadius = CornerRadius(radiusPx)
            )
        }

        val bubbleText = currentIndexLabel ?: cachedIndicatorLabel
        if (bubbleProgress > 0.01f && !bubbleText.isNullOrBlank()) {
            val bubbleSizePx = with(density) { 40.dp.toPx() }
            val bubbleShiftXPx = with(density) { 24.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .requiredSize(40.dp)
                    .graphicsLayer {
                        val clampedThumbY = animatedThumbY.value.coerceIn(0f, maxThumbY)
                        val centerY = clampedThumbY + constThumbHeight * 0.5f - bubbleSizePx * 0.5f
                        translationX = -bubbleShiftXPx
                        translationY = centerY.coerceIn(0f, (viewportHeight - bubbleSizePx).coerceAtLeast(0f))
                        alpha = bubbleProgress.coerceIn(0f, 1f)
                        val scale = if (reducedMotion) 1f else 0.72f + 0.28f * bubbleProgress
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(0.5.dp, Color(0x24000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bubbleText,
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Immutable
internal data class LibrarySwipeAction(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val onTrigger: () -> Unit
)

@Composable
internal fun LibrarySwipeActionsBox(
    modifier: Modifier = Modifier,
    start: (@Composable () -> LibrarySwipeAction)? = null,
    end: (@Composable () -> LibrarySwipeAction)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableIntStateOf(0) }
    var fired by remember { mutableIntStateOf(0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val startAction = remember { mutableStateOf<LibrarySwipeAction?>(null) }
    val endAction = remember { mutableStateOf<LibrarySwipeAction?>(null) }
    var startNeed by remember { mutableFloatStateOf(0f) }
    var endNeed by remember { mutableFloatStateOf(0f) }

    val baseThreshold = with(density) { (rowSize.width * 0.26f).coerceIn(92.dp.toPx(), 138.dp.toPx()) }
    val pillGapPx = with(density) { 6.dp.toPx() }
    val deadSideLimit = with(density) { 20.dp.toPx() }
    val hysteresis = with(density) { 4.dp.toPx() }
    val resistance = 0.35f

    fun thresholdFor(direction: Float): Float {
        return maxOf(baseThreshold, (if (direction > 0f) startNeed else endNeed) + pillGapPx)
    }

    fun resisted(raw: Float): Float {
        val allowed = if (raw > 0f) start != null else end != null
        val distance = abs(raw)
        val line = thresholdFor(raw)
        val shown = when {
            !allowed -> minOf(distance * 0.2f, deadSideLimit)
            distance <= line -> distance
            else -> line + (distance - line) * resistance
        }
        return sign(raw) * shown
    }

    fun unresisted(shown: Float): Float {
        val distance = abs(shown)
        val line = thresholdFor(shown)
        return if (distance <= line) shown else sign(shown) * (line + (distance - line) / resistance)
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            offset = 0f
            travel = 0f
            armed = 0
        }
    }

    val dragState = rememberDraggableState { delta ->
        travel += delta
        offset = resisted(travel)
        val side = when {
            offset > 0f && start != null && offset >= thresholdFor(1f) -> 1
            offset < 0f && end != null && -offset >= thresholdFor(-1f) -> -1
            armed != 0 && sign(offset) == armed.toFloat() && abs(offset) >= thresholdFor(offset) - hysteresis -> armed
            else -> 0
        }
        if (side != armed) {
            armed = side
            haptic.performHapticFeedback(
                if (side != 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
    }

    val baseConfiguration = LocalViewConfiguration.current
    val longHold = remember(baseConfiguration) {
        object : ViewConfiguration by baseConfiguration {
            override val longPressTimeoutMillis: Long
                get() = maxOf(baseConfiguration.longPressTimeoutMillis, 600L)
        }
    }

    val showStart by remember { derivedStateOf { offset > 0.5f } }
    val showEnd by remember { derivedStateOf { offset < -0.5f } }

    CompositionLocalProvider(LocalViewConfiguration provides longHold) {
        Box(
            modifier = modifier
                .onSizeChanged { rowSize = it }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStarted = {
                        settleJob?.cancel()
                        fired = 0
                        travel = unresisted(offset)
                    },
                    onDragStopped = { velocity ->
                        val side = armed
                        val action = when (side) {
                            1 -> startAction.value
                            -1 -> endAction.value
                            else -> null
                        }
                        if (action != null) {
                            fired = side
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            action.onTrigger()
                        }
                        armed = 0
                        settleJob = scope.launch {
                            if (reducedMotion) {
                                offset = 0f
                                travel = 0f
                                fired = 0
                            } else {
                                androidx.compose.animation.core.animate(
                                    initialValue = offset,
                                    targetValue = 0f,
                                    initialVelocity = velocity.coerceIn(-2800f, 2800f) * 0.35f,
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)
                                ) { value, _ ->
                                    offset = value
                                    travel = value
                                }
                                fired = 0
                            }
                        }
                    }
                )
        ) {
            if (showStart && start != null) {
                val action = start()
                SideEffect { startAction.value = action }
                LibrarySwipePill(
                    action = action,
                    atStart = true,
                    room = { (offset - pillGapPx).coerceAtLeast(0f) },
                    rowHeight = rowSize.height,
                    armed = armed == 1,
                    fired = fired == 1,
                    onNeed = { startNeed = it }
                )
            }
            if (showEnd && end != null) {
                val action = end()
                SideEffect { endAction.value = action }
                LibrarySwipePill(
                    action = action,
                    atStart = false,
                    room = { (-offset - pillGapPx).coerceAtLeast(0f) },
                    rowHeight = rowSize.height,
                    armed = armed == -1,
                    fired = fired == -1,
                    onNeed = { endNeed = it }
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = offset }
                    .drawBehind {
                        val strength = (abs(offset) / 20.dp.toPx()).coerceIn(0f, 1f)
                        if (strength > 0f) {
                            drawRoundRect(
                                color = LibraryHighlightColor.copy(alpha = LibraryHighlightColor.alpha * strength),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                        }
                    }
            ) {
                content()
            }
        }
    }
}

@Composable
private fun BoxScope.LibrarySwipePill(
    action: LibrarySwipeAction,
    atStart: Boolean,
    room: () -> Float,
    rowHeight: Int,
    armed: Boolean,
    fired: Boolean,
    onNeed: (Float) -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp
        )
    }
    val labelWidth = remember(action.label, density) {
        measurer.measure(action.label, labelStyle).size.width.toFloat()
    }
    val need = with(density) { (28.dp + 18.dp + 6.dp).toPx() } + labelWidth
    SideEffect { onNeed(need) }

    val fill by animateColorAsState(
        targetValue = if (armed || fired) action.color else Color(0xFF1D1D22),
        animationSpec = if (reducedMotion) snap() else spring(stiffness = 650f),
        label = "swipePillColor"
    )
    val borderTint by animateColorAsState(
        targetValue = if (armed || fired) Color(0x36FFFFFF) else Color(0x16FFFFFF),
        animationSpec = if (reducedMotion) snap() else spring(stiffness = 650f),
        label = "swipePillBorder"
    )
    val bump = remember { Animatable(1f) }
    LaunchedEffect(armed, fired, reducedMotion) {
        if (reducedMotion) {
            bump.snapTo(1f)
        } else if (fired) {
            bump.animateTo(1.28f, tween(70))
            bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 560f))
        } else if (armed) {
            bump.animateTo(1.22f, spring(dampingRatio = 0.48f, stiffness = 920f))
            bump.animateTo(1.06f, spring(dampingRatio = 0.56f, stiffness = 520f))
        } else {
            bump.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 500f))
        }
    }

    val shape = remember { SmoothCornerShape(16.dp) }
    val iconRevealFloorPx = with(density) { 28.dp.toPx() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd)
            .layout { measurable, _ ->
                val w = room().roundToInt().coerceAtLeast(0)
                val h = rowHeight.coerceAtLeast(0)
                if (w == 0 || h == 0) {
                    layout(0, 0) {}
                } else {
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
            }
            .clip(shape)
            .background(fill)
            .border(0.5.dp, borderTint, shape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(unbounded = true)
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = Color.White,
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer {
                        val grown = (room() / iconRevealFloorPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val s = (0.45f + 0.55f * grown) * bump.value
                        scaleX = s
                        scaleY = s
                        alpha = grown
                    }
            )
            AnimatedVisibility(
                visible = armed || fired,
                enter = if (reducedMotion) {
                    fadeIn(snap())
                } else {
                    expandHorizontally(
                        spring(dampingRatio = 0.78f, stiffness = 680f),
                        expandFrom = Alignment.Start
                    ) + fadeIn(tween(140))
                },
                exit = if (reducedMotion) {
                    fadeOut(snap())
                } else {
                    shrinkHorizontally(tween(120), shrinkTowards = Alignment.Start) + fadeOut(tween(80))
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = action.label,
                        style = labelStyle,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
