package com.onda.music.feature.home

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val HomeRevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val HomeBoneColor = Color(0xFFD9D9D9).copy(alpha = 0.18f)
private val HomeHighlightColor = Color.White.copy(alpha = 0.11f)
private const val HomeGlowStopCount = 10
private val HomeGlowStopPositions = FloatArray(HomeGlowStopCount + 1) { it / HomeGlowStopCount.toFloat() }

internal fun homeSmoothstep(from: Float, to: Float, x: Float): Float {
    if (to <= from) return if (x >= to) 1f else 0f
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal object HomeBlurCache {
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
internal class HomeHighlightIndication(
    private val cornerRadius: Dp = 18.dp,
    private val spillX: Dp = 0.dp,
    private val spillY: Dp = 0.dp,
    private val color: Color = HomeHighlightColor
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return HomeHighlightNode(interactionSource, cornerRadius, spillX, spillY, color)
    }

    override fun equals(other: Any?): Boolean {
        return other is HomeHighlightIndication &&
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

private class HomeHighlightNode(
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
internal fun Modifier.homePressBounce(
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
        var pressedCount = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressedCount++
                    launch {
                        scale.animateTo(pressedScale, spring(dampingRatio = 1f, stiffness = 1500f))
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressedCount = (pressedCount - 1).coerceAtLeast(0)
                    if (pressedCount == 0) {
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
internal class HomeRevealSeenState(initial: Collection<String> = emptyList()) {
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
        return min(burstCount++, 6) * 55
    }

    companion object {
        val Saver: Saver<HomeRevealSeenState, ArrayList<String>> = Saver(
            save = { ArrayList(it.seen) },
            restore = { HomeRevealSeenState(it) }
        )
    }
}

@Composable
internal fun rememberHomeRevealSeenState(): HomeRevealSeenState {
    return rememberSaveable(saver = HomeRevealSeenState.Saver) { HomeRevealSeenState() }
}

@Composable
internal fun Modifier.homeRevealOnAppear(
    key: String,
    seenState: HomeRevealSeenState,
    enabled: Boolean = true,
    delayMillis: Int = 0,
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
        val wait = delayMillis + if (cascade) seenState.nextBurstDelayMs() else 0
        progress.animateTo(1f, tween(460, delayMillis = wait, easing = HomeRevealEasing))
    }

    val slidePx = with(LocalDensity.current) { 18.dp.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * slidePx
    }
}

@Composable
internal fun HomeBonesHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val phase = if (reducedMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "homeBonesWave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
            label = "homeBonesPhase"
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
                            val w = size.width
                            val h = size.height
                            val band = maxOf(w, h) * 0.55f + 1f
                            val centerX = -band + phase.value * (w + band * 2f)
                            val centerY = -band + phase.value * (h + band * 2f)
                            drawRect(
                                brush = Brush.linearGradient(
                                    0f to Color.Black.copy(alpha = 0.48f),
                                    0.5f to Color.Black,
                                    1f to Color.Black.copy(alpha = 0.48f),
                                    start = Offset(centerX - band * 0.55f, centerY - band * 0.55f),
                                    end = Offset(centerX + band * 0.55f, centerY + band * 0.55f)
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
internal fun HomeBone(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    Box(
        modifier = modifier
            .size(width, height)
            .background(HomeBoneColor, shape)
    )
}

@Composable
internal fun HomeHeroSkeleton(
    modifier: Modifier = Modifier
) {
    val heroHeight = rememberHomeHeroHeight()
    HomeBonesHost(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF141416),
                        0.55f to Color(0xFF0B0B0D),
                        1.0f to Color(0xFF000000)
                    )
                )
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            OndaMonogramMark(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .graphicsLayer { alpha = 0.22f },
                tint = Color.White,
                filled = false
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeBone(width = 136.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                HomeBone(width = 244.dp, height = 28.dp, shape = RoundedCornerShape(10.dp))
                HomeBone(width = 148.dp, height = 14.dp, shape = RoundedCornerShape(7.dp))
                HomeBone(width = 92.dp, height = 36.dp, shape = RoundedCornerShape(999.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp)
            ) {
                HomeBone(width = 34.dp, height = 2.dp, shape = RoundedCornerShape(50))
            }
        }
    }
}

@Composable
internal fun HomeShelfSkeleton(
    modifier: Modifier = Modifier
) {
    HomeBonesHost(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        content = {
            HomeBone(width = 148.dp, height = 18.dp, shape = RoundedCornerShape(8.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeBone(width = 140.dp, height = 140.dp, shape = SmoothCornerShape(22.dp))
                        HomeBone(width = 108.dp, height = 14.dp, shape = RoundedCornerShape(6.dp))
                        HomeBone(width = 74.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                    }
                }
            }
        }
    )
}

@Composable
internal fun DiscoverSkeleton(
    modifier: Modifier = Modifier
) {
    HomeBonesHost(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        content = {
            HomeBone(width = 156.dp, height = 18.dp, shape = RoundedCornerShape(8.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeBone(width = 140.dp, height = 140.dp, shape = SmoothCornerShape(22.dp))
                        HomeBone(width = 108.dp, height = 14.dp, shape = RoundedCornerShape(6.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            HomeBone(width = 140.dp, height = 18.dp, shape = RoundedCornerShape(8.dp))
            Spacer(modifier = Modifier.height(12.dp))
            repeat(3) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeBone(width = 48.dp, height = 48.dp, shape = SmoothCornerShape(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HomeBone(width = 176.dp, height = 14.dp, shape = RoundedCornerShape(6.dp))
                        HomeBone(width = 116.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                    }
                }
            }
        }
    )
}

private val StaticEqualizerFractions = floatArrayOf(0.45f, 0.85f, 0.62f, 0.94f, 0.50f)

@Composable
internal fun HomePlayingIndicator(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 5,
    barWidth: Dp = 2.5.dp,
    spacing: Dp = 2.dp
) {
    val reducedMotion = OndaTheme.reducedMotion
    val levels = remember(bars) {
        List(bars) { idx -> Animatable(StaticEqualizerFractions[idx % StaticEqualizerFractions.size]) }
    }

    LaunchedEffect(isPlaying, reducedMotion, levels) {
        if (!isPlaying || reducedMotion) {
            levels.forEachIndexed { idx, anim ->
                anim.snapTo(StaticEqualizerFractions[idx % StaticEqualizerFractions.size])
            }
            return@LaunchedEffect
        }
        levels.forEachIndexed { idx, level ->
            launch {
                while (true) {
                    level.animateTo(
                        targetValue = 0.24f + Random.nextFloat() * 0.76f,
                        animationSpec = tween(
                            durationMillis = 150 + (idx * 23 + Random.nextInt(130)),
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }
    }

    Canvas(
        modifier = modifier.width(barWidth * bars + spacing * (bars - 1))
    ) {
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

internal object HomeGlowColorCache {
    private val cache = LruCache<String, Int>(48)

    fun cached(url: String): Color? = cache.get(url)?.let { Color(it) }

    suspend fun load(context: android.content.Context, url: String): Color? {
        cached(url)?.let { return it }
        return try {
            withContext(Dispatchers.IO) {
                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(64)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                val argb = withContext(Dispatchers.Default) { sampleGlowTone(bitmap) } ?: return@withContext null
                cache.put(url, argb)
                Color(argb)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private fun sampleGlowTone(bitmap: Bitmap): Int? {
        val swatches = Palette.from(bitmap).maximumColorCount(12).generate().swatches
        val dominant = swatches.maxByOrNull { it.population } ?: return null
        val pick = swatches
            .filter { it.hsl[1] >= 0.2f && it.hsl[2] in 0.08f..0.9f && it.population >= dominant.population / 6 }
            .maxByOrNull { it.population }
            ?: dominant
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(pick.rgb, hsv)
        hsv[1] = hsv[1].coerceAtMost(0.65f)
        hsv[2] = hsv[2].coerceIn(0.24f, 0.38f)
        return android.graphics.Color.HSVToColor(hsv)
    }
}

@Composable
internal fun HomeTopGlow(
    coverUrls: List<String>,
    depth: Dp,
    visibility: () -> Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = OndaTheme.reducedMotion
    var pair by remember(coverUrls) {
        val left = coverUrls.getOrNull(0)?.let(HomeGlowColorCache::cached)
        val right = coverUrls.getOrNull(1)?.let(HomeGlowColorCache::cached)
        mutableStateOf(
            when {
                left != null -> left to (right ?: left)
                right != null -> right to right
                else -> null
            }
        )
    }

    LaunchedEffect(coverUrls) {
        val left = coverUrls.getOrNull(0)?.let { HomeGlowColorCache.load(context, it) }
        val right = coverUrls.getOrNull(1)?.let { HomeGlowColorCache.load(context, it) }
        pair = when {
            left != null -> left to (right ?: left)
            right != null -> right to right
            else -> null
        }
    }

    val leftColor by animateColorAsState(
        targetValue = pair?.first ?: Color.Transparent,
        animationSpec = if (reducedMotion) snap() else tween(650),
        label = "homeGlowLeft"
    )
    val rightColor by animateColorAsState(
        targetValue = pair?.second ?: Color.Transparent,
        animationSpec = if (reducedMotion) snap() else tween(650),
        label = "homeGlowRight"
    )
    val leftPaint = remember { Paint().apply { isDither = true } }
    val rightPaint = remember { Paint().apply { isDither = true } }

    Spacer(
        modifier = modifier
            .graphicsLayer {
                alpha = visibility().coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithCache {
                val width = size.width
                val height = depth.toPx()
                if (width <= 0f || height <= 0f || (leftColor.alpha == 0f && rightColor.alpha == 0f)) {
                    return@drawWithCache onDrawBehind {}
                }
                leftPaint.shader = buildCornerGlowShader(leftColor, width * 0.08f, width, height)
                rightPaint.shader = buildCornerGlowShader(rightColor, width * 0.92f, width, height)
                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(0f, 0f, width, height, leftPaint)
                        canvas.nativeCanvas.drawRect(0f, 0f, width, height, rightPaint)
                    }
                }
            }
    )
}

private fun buildCornerGlowShader(
    color: Color,
    centerX: Float,
    width: Float,
    height: Float
): Shader {
    val baseArgb = color.copy(alpha = 1f).toArgb()
    val r = android.graphics.Color.red(baseArgb)
    val g = android.graphics.Color.green(baseArgb)
    val b = android.graphics.Color.blue(baseArgb)
    val stops = IntArray(HomeGlowStopCount + 1) { i ->
        val remaining = 1f - HomeGlowStopPositions[i]
        val falloff = remaining * remaining * (3f - 2f * remaining)
        val alpha = (color.alpha * 0.85f * falloff * 255f).roundToInt().coerceIn(0, 255)
        android.graphics.Color.argb(alpha, r, g, b)
    }
    return RadialGradient(centerX, 0f, height, stops, HomeGlowStopPositions, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale((width * 0.92f) / height, 1f, centerX, 0f) })
    }
}

@Composable
internal fun ScrollAwayCover(
    progress: () -> Float,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    follow: Float = 0.18f,
    softFrom: Float = 0.08f,
    content: @Composable BoxScope.() -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val canBlur = blurEnabled && !reducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val frame = rememberGraphicsLayer()

    Box(
        modifier = modifier
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                if (!reducedMotion) {
                    translationY = p * size.height * follow
                    val zoom = 1f + p * 0.18f
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0.5f, 0.35f)
                }
                alpha = 1f - homeSmoothstep(0.2f, 0.85f, p)
                compositingStrategy = if (p > 0.01f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                val p = progress().coerceIn(0f, 1f)
                if (p > 0.01f) {
                    drawRect(Color.Black.copy(alpha = 0.22f * p))
                    val clear = 0.1f + 0.45f * homeSmoothstep(0f, 0.8f, p)
                    val top = size.height * (1f - clear)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startY = top,
                            endY = size.height
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (canBlur) {
                        Modifier.drawWithContent {
                            frame.record { this@drawWithContent.drawContent() }
                            drawLayer(frame)
                        }
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
        if (canBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val soft = homeSmoothstep(softFrom, 0.55f, progress().coerceIn(0f, 1f))
                        alpha = soft
                        renderEffect = if (soft > 0.02f) HomeBlurCache.blur(20f) else null
                        clip = true
                    }
                    .drawBehind { drawLayer(frame) }
            )
        }
    }
}

@Immutable
internal data class CardLaunchOrigin(
    val bounds: Rect? = null,
    val cornerRadius: Dp = 24.dp
)

@Immutable
internal data class HomeDetailCollapseState(
    val headerContentAlpha: Float,
    val morphProgress: Float
)

@Composable
internal fun rememberHomeDetailCollapseProgress(
    lazyListState: LazyListState,
    headerHeightPx: Float,
    triggerOffset: Dp = 44.dp
): HomeDetailCollapseState {
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
        label = "detailHeaderFade"
    )
    val morph by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.88f, stiffness = 360f),
        label = "detailHeaderMorph"
    )
    return HomeDetailCollapseState(
        headerContentAlpha = 1f - fade,
        morphProgress = morph
    )
}

@Composable
internal fun HomeDetailCollapsingTopBar(
    morphProgress: Float,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isAlbum: Boolean,
    palette: OndaArtworkPalette,
    closeDescription: String,
    playAllLabel: String,
    hasTracks: Boolean,
    glassBlurEnabled: Boolean,
    coverBoundsInWindow: Rect?,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val closeInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .size(DockMetrics.MinTapTarget)
                    .homePressBounce(closeInteraction, pressedScale = 0.90f)
                    .clip(CircleShape)
                    .background(Color(0x66141414))
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = HomeHighlightIndication(cornerRadius = 24.dp),
                        onClick = onClose
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = closeDescription,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .onGloballyPositioned {
                        slotBoundsInWindow = Rect(it.positionInWindow(), it.size.toRectSize())
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
                val playInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .homePressBounce(playInteraction, pressedScale = 0.94f)
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(palette.primary, palette.secondary)
                            )
                        )
                        .clickable(
                            interactionSource = playInteraction,
                            indication = HomeHighlightIndication(cornerRadius = 24.dp),
                            onClick = onPlayAll
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = palette.onAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = playAllLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.onAccent,
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
            val motionBlurRadius = if (!reducedMotion && glassBlurEnabled) {
                sin(morphProgress * PI.toFloat()).coerceAtLeast(0f) * 18f
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
                            renderEffect = HomeBlurCache.blur(motionBlurRadius)
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
                        tint = palette.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun IntSize.toRectSize(): Size = Size(width.toFloat(), height.toFloat())
