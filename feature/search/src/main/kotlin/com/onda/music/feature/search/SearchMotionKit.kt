package com.onda.music.feature.search

import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val SearchRevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val SearchCollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)
private val SearchBoneColor = Color(0xFFD9D9D9).copy(alpha = 0.18f)
private val SearchHighlightColor = Color.White.copy(alpha = 0.11f)
private val StaticSearchEqualizerFractions = floatArrayOf(0.42f, 0.88f, 0.60f, 0.94f, 0.48f)

internal fun searchSmoothstep(from: Float, to: Float, x: Float): Float {
    if (to <= from) return if (x >= to) 1f else 0f
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal object SearchBlurCache {
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
internal class SearchHighlightIndication(
    private val cornerRadius: Dp = 16.dp,
    private val spillX: Dp = 0.dp,
    private val spillY: Dp = 0.dp,
    private val color: Color = SearchHighlightColor
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return SearchHighlightNode(interactionSource, cornerRadius, spillX, spillY, color)
    }

    override fun equals(other: Any?): Boolean {
        return other is SearchHighlightIndication &&
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

private class SearchHighlightNode(
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
internal fun Modifier.searchPressBounce(
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
internal class SearchRevealSeenState(initial: Collection<String> = emptyList()) {
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
        val Saver: Saver<SearchRevealSeenState, ArrayList<String>> = Saver(
            save = { ArrayList(it.seen) },
            restore = { SearchRevealSeenState(it) }
        )
    }
}

@Composable
internal fun rememberSearchRevealSeenState(): SearchRevealSeenState {
    return rememberSaveable(saver = SearchRevealSeenState.Saver) { SearchRevealSeenState() }
}

@Composable
internal fun Modifier.searchRevealOnAppear(
    key: String,
    seenState: SearchRevealSeenState,
    enabled: Boolean = true,
    cascade: Boolean = false
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
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        seenState.markSeen(key)
        val wait = if (cascade) seenState.nextBurstDelayMs() else 0
        progress.animateTo(1f, tween(440, delayMillis = wait, easing = SearchRevealEasing))
    }

    val slidePx = with(LocalDensity.current) { 16.dp.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * slidePx
    }
}

@Composable
internal fun SearchLoadingDots(
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White
) {
    val reducedMotion = OndaTheme.reducedMotion
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (reducedMotion) {
            repeat(3) { idx ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.45f + idx * 0.22f))
                )
            }
        } else {
            val transition = rememberInfiniteTransition(label = "searchDots")
            repeat(3) { index ->
                val phase by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 840,
                            delayMillis = index * 140,
                            easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "searchDot_$index"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            alpha = 0.3f + 0.7f * phase
                            val s = 0.75f + 0.25f * phase
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
internal fun SearchBonesHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val phase = if (reducedMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "searchBonesWave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
            label = "searchBonesPhase"
        )
    }

    Column(
        modifier = modifier
            .then(
                if (phase != null) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val h = size.height
                            val band = h * 0.8f + 1f
                            val center = -band + phase.value * (h + band * 2f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.45f),
                                    0.5f to Color.Black,
                                    1f to Color.Black.copy(alpha = 0.45f),
                                    startY = center - band,
                                    endY = center + band
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                } else {
                    Modifier
                }
            ),
        content = content
    )
}

@Composable
internal fun SearchBone(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Box(modifier = modifier.background(SearchBoneColor, shape))
}

@Composable
internal fun MoodGridSkeleton(
    modifier: Modifier = Modifier
) {
    SearchBonesHost(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SearchBone(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            shape = RoundedCornerShape(14.dp)
                        )
                        SearchBone(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun SearchPlayingIndicator(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 5,
    barWidth: Dp = 2.dp,
    spacing: Dp = 1.5.dp
) {
    val reducedMotion = OndaTheme.reducedMotion
    val levels = remember(bars) {
        List(bars) { idx -> Animatable(StaticSearchEqualizerFractions[idx % StaticSearchEqualizerFractions.size]) }
    }

    LaunchedEffect(isPlaying, reducedMotion, levels) {
        if (!isPlaying || reducedMotion) {
            levels.forEachIndexed { idx, anim ->
                anim.snapTo(StaticSearchEqualizerFractions[idx % StaticSearchEqualizerFractions.size])
            }
            return@LaunchedEffect
        }
        levels.forEachIndexed { idx, level ->
            launch {
                while (true) {
                    level.animateTo(
                        targetValue = 0.24f + Random.nextFloat() * 0.76f,
                        animationSpec = tween(
                            durationMillis = 150 + (idx * 21 + Random.nextInt(130)),
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
internal fun rememberSearchCollapseProgress(
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
        animationSpec = if (reducedMotion) snap() else tween(700, easing = SearchCollapseEasing),
        label = "searchBarCollapse"
    )
}

@Immutable
data class SearchLaunchOrigin(
    val bounds: Rect? = null,
    val cornerRadius: Dp = 18.dp
)

@Immutable
internal data class SearchDetailCollapseState(
    val headerContentAlpha: Float,
    val morphProgress: Float
)

@Composable
internal fun rememberSearchDetailCollapseProgress(
    lazyListState: LazyListState,
    headerHeightPx: Float,
    triggerOffset: Dp = 44.dp
): SearchDetailCollapseState {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val triggerPx = with(density) { triggerOffset.toPx() }
    val isCollapsed by remember(lazyListState, headerHeightPx, triggerPx) {
        derivedStateOf {
            if (headerHeightPx <= 0f) {
                false
            } else if (lazyListState.firstVisibleItemIndex > 0) {
                true
            } else {
                lazyListState.firstVisibleItemScrollOffset > (headerHeightPx - triggerPx).coerceAtLeast(0f)
            }
        }
    }
    val fade by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(260, easing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)),
        label = "searchDetailFade"
    )
    val morph by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.88f, stiffness = 360f),
        label = "searchDetailMorph"
    )
    return SearchDetailCollapseState(
        headerContentAlpha = 1f - fade,
        morphProgress = morph
    )
}

@Composable
internal fun SearchDetailCollapsingTopBar(
    morphProgress: Float,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isAlbum: Boolean,
    hasTracks: Boolean,
    coverBoundsInWindow: Rect?,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    var barPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    var slotBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { barPositionInWindow = it.positionInWindow() }
    ) {
        if (morphProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = morphProgress }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to GlassTokens.AmoledBlack.copy(alpha = 0.94f),
                                0.72f to GlassTokens.AmoledBlack.copy(alpha = 0.84f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .graphicsLayer { alpha = morphProgress * 0.65f }
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val closeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .size(DockMetrics.MinTapTarget)
                    .searchPressBounce(closeInteraction, pressedScale = 0.90f)
                    .clip(CircleShape)
                    .background(GlassTokens.IconBadgeSurface.copy(alpha = 0.75f))
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = SearchHighlightIndication(cornerRadius = 24.dp),
                        onClick = onClose
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close collection",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .onGloballyPositioned {
                        slotBoundsInWindow = Rect(it.positionInWindow(), it.size.toSearchRectSize())
                    }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = morphProgress }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (hasTracks) {
                val shuffleInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                        .size(DockMetrics.MinTapTarget)
                        .searchPressBounce(shuffleInteraction, pressedScale = 0.90f)
                        .clip(CircleShape)
                        .background(GlassTokens.IconBadgeSurface.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = shuffleInteraction,
                            indication = SearchHighlightIndication(cornerRadius = 24.dp),
                            onClick = onShufflePlay
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                val playInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .searchPressBounce(playInteraction, pressedScale = 0.94f)
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color.White)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = SearchHighlightIndication(cornerRadius = 24.dp),
                            onClick = onPlayAll
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = GlassTokens.AmoledBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.labelLarge,
                            color = GlassTokens.AmoledBlack,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        val slot = slotBoundsInWindow
        if (morphProgress > 0.005f && slot != null) {
            val start = coverBoundsInWindow ?: slot
            val currentRect = lerp(start, slot, morphProgress)
            val localLeft = currentRect.left - barPositionInWindow.x
            val localTop = currentRect.top - barPositionInWindow.y
            val cornerRadius = lerp(GlassTokens.RadiusMd, 8.dp, morphProgress).coerceAtLeast(0.dp)
            val motionBlurRadius = if (!reducedMotion) {
                sin(morphProgress * PI.toFloat()).coerceAtLeast(0f) * 16f
            } else {
                0f
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(localLeft.roundToInt(), localTop.roundToInt()) }
                    .size(
                        with(density) { currentRect.width.toDp() },
                        with(density) { currentRect.height.toDp() }
                    )
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(GlassTokens.ObsidianSurface)
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && motionBlurRadius > 1f) {
                            renderEffect = SearchBlurCache.blur(motionBlurRadius)
                        } else {
                            renderEffect = null
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (isAlbum) Icons.Rounded.Album else Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun IntSize.toSearchRectSize(): Size = Size(width.toFloat(), height.toFloat())
