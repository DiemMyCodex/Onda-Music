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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

private val LibraryRevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val LibraryCollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)
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
    private val cornerRadius: Dp = 18.dp,
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
internal fun rememberLibraryCollapseProgress(
    listState: LazyListState,
    enterDp: Dp = 24.dp,
    exitDp: Dp = 6.dp
): State<Float> {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val enterPx = with(density) { enterDp.toPx() }
    val exitPx = with(density) { exitDp.toPx() }
    val latch = remember(listState) { booleanArrayOf(false) }
    val collapsed by remember(listState, enterPx, exitPx) {
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
    return animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(700, easing = LibraryCollapseEasing),
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
    tile: Dp = 46.dp,
    showFan: Boolean = true,
    round: Boolean = false
) {
    val shown = remember(covers) { covers.filterNotNull().distinct().take(2) }
    val frontUrl = shown.firstOrNull()
    val backUrl = shown.getOrNull(1)
    val corner = if (round) tile * 0.5f else tile * 0.22f
    val shape = if (round) CircleShape else RoundedCornerShape(corner)
    val stackWidth = if (showFan) tile * 1.48f else tile

    Box(modifier = modifier.size(width = stackWidth, height = tile)) {
        if (showFan) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(tile)
                    .graphicsLayer {
                        alpha = 0.55f
                        rotationZ = if (round) 0f else -9f
                    }
                    .clip(shape)
                    .background(GlassTokens.SmokedGlassElevated)
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), shape),
                contentAlignment = Alignment.Center
            ) {
                if (!backUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = backUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = OndaTheme.colors.textTertiary,
                        modifier = Modifier.size(tile * 0.42f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(if (showFan) Alignment.CenterEnd else Alignment.Center)
                .size(tile)
                .graphicsLayer {
                    rotationZ = if (showFan && !round) 7f else 0f
                }
                .clip(shape)
                .background(GlassTokens.IconBadgeSurface)
                .border(0.75.dp, Color.White.copy(alpha = 0.18f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (!frontUrl.isNullOrBlank()) {
                AsyncImage(
                    model = frontUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = OndaTheme.colors.accent,
                    modifier = Modifier.size(tile * 0.48f)
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
    headerItems: Int = 0
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

    BoxWithConstraints(
        modifier = modifier
            .width(trackWidth)
            .fillMaxHeight()
            .pointerInput(scrollState, headerItems) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        lastTargetIndex = -1
                        val viewportHeight = size.height.toFloat()
                        val constThumbHeight = with(density) { thumbHeight.toPx() }
                        val maxThumbY = (viewportHeight - constThumbHeight).coerceAtLeast(1f)
                        smoothedThumbY = (offset.y - constThumbHeight * 0.5f).coerceIn(0f, maxThumbY)
                        smoothedY = (offset.y / viewportHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
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

                    if (currentTime - lastScrollTime < 32L) return@detectDragGestures
                    lastScrollTime = currentTime

                    val visibleItems = layoutInfo.visibleItemsInfo
                    if (visibleItems.isEmpty()) return@detectDragGestures

                    val maxScrollIndex = max(1, totalContentItems - visibleItems.size)
                    if (maxScrollIndex > minScrollRangeForDrag) {
                        val touchProgress = (change.position.y / viewportHeight).coerceIn(0f, 1f)
                        val listSmoothing = when {
                            totalContentItems < 20 -> 0.25f
                            totalContentItems < 50 -> 0.5f
                            else -> 0.85f
                        }
                        smoothedY = smoothedY * (1f - listSmoothing) + touchProgress * listSmoothing
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

    val baseThreshold = with(density) { (rowSize.width * 0.28f).coerceIn(96.dp.toPx(), 144.dp.toPx()) }
    val pillRoom = with(density) { 8.dp.toPx() }
    val deadSideLimit = with(density) { 20.dp.toPx() }
    val hysteresis = with(density) { 4.dp.toPx() }
    val resistance = 0.35f

    fun thresholdFor(direction: Float): Float {
        return maxOf(baseThreshold, (if (direction > 0f) startNeed else endNeed) + pillRoom)
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
                                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 400f)
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
                    room = { (offset - pillRoom).coerceAtLeast(0f) },
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
                    room = { (-offset - pillRoom).coerceAtLeast(0f) },
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
                                cornerRadius = CornerRadius(GlassTokens.RadiusMd.toPx())
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
    val pillHeight = with(density) { (rowHeight.toDp() * 0.58f).coerceIn(34.dp, 40.dp).toPx() }
    val labelWidth = remember(action.label, density) {
        measurer.measure(action.label, labelStyle).size.width.toFloat()
    }
    val need = with(density) { (26.dp + 18.dp + 6.dp).toPx() } + labelWidth
    SideEffect { onNeed(need) }

    val width by animateFloatAsState(
        targetValue = room().coerceIn(0f, need),
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.74f, stiffness = 1000f),
        label = "swipePillWidth"
    )
    val fill by animateColorAsState(
        targetValue = if (armed || fired) action.color else Color.White.copy(alpha = 0.14f),
        animationSpec = if (reducedMotion) snap() else spring(stiffness = 650f),
        label = "swipePillColor"
    )
    val bump = remember { Animatable(1f) }
    LaunchedEffect(armed, fired, reducedMotion) {
        if (!reducedMotion && (armed || fired)) {
            bump.animateTo(if (fired) 1.25f else 1.15f, tween(80))
            bump.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 540f))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 4.dp)
            .layout { measurable, _ ->
                val w = minOf(width, room()).coerceAtLeast(0f)
                val h = minOf(w, pillHeight)
                val placeable = measurable.measure(Constraints.fixed(w.roundToInt(), h.roundToInt()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(RoundedCornerShape(50))
            .background(fill)
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
                    .size(18.dp)
                    .graphicsLayer {
                        val grown = (minOf(width, room()) / pillHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val s = (0.4f + 0.6f * grown) * bump.value
                        scaleX = s
                        scaleY = s
                        alpha = grown
                    }
            )
            AnimatedVisibility(
                visible = armed,
                enter = if (reducedMotion) fadeIn(snap()) else expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 680f), expandFrom = Alignment.Start) + fadeIn(tween(150)),
                exit = if (reducedMotion) fadeOut(snap()) else shrinkHorizontally(tween(130), shrinkTowards = Alignment.Start) + fadeOut(tween(90))
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
