package com.onda.music.core.design

import android.graphics.Matrix
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositing
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class BlurDirection {
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP
}

object BlurEffectCache {
    private val composeCache = ConcurrentHashMap<Int, RenderEffect>()
    private val nativeCache = ConcurrentHashMap<Int, android.graphics.RenderEffect>()

    fun effectiveRadius(radius: Float, allowLargeRadius: Boolean = false): Float {
        return if (Build.VERSION.SDK_INT <= 34 && !allowLargeRadius) {
            radius.coerceAtMost(54f)
        } else {
            radius
        }
    }

    fun quantize(radius: Float): Float {
        val safe = radius.coerceAtLeast(0f)
        if (safe <= 0.5f) return 0f
        val quant = when {
            safe <= 16f -> 0.5f
            safe <= 32f -> 1.0f
            safe <= 64f -> 2.0f
            else -> 4.0f
        }
        return (safe / quant).roundToInt().coerceAtLeast(1) * quant
    }

    fun get(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
        allowLargeRadius: Boolean = false
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) return null
        val effective = effectiveRadius(radius, allowLargeRadius)
        val quant = when {
            effective <= 16f -> 0.5f
            effective <= 32f -> 1.0f
            effective <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effective / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal
        return composeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect
                .createBlurEffect(r, r, tileMode)
                .asComposeRenderEffect()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun getNative(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
        allowLargeRadius: Boolean = false
    ): android.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) return null
        val effective = effectiveRadius(radius, allowLargeRadius)
        val quant = when {
            effective <= 16f -> 0.5f
            effective <= 32f -> 1.0f
            effective <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effective / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal
        return nativeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect.createBlurEffect(r, r, tileMode)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun createDamageNode(width: Int, height: Int): Any {
    return RenderNode("OndaDamageBoundary").apply {
        setPosition(0, 0, width, height)
        setClipToBounds(true)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun beginDamageRecord(node: Any): android.graphics.Canvas {
    return (node as RenderNode).beginRecording()
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun endDamageRecord(node: Any) {
    (node as RenderNode).endRecording()
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun android.graphics.Canvas.drawDamage(node: Any) {
    drawRenderNode(node as RenderNode)
}

fun Modifier.damageBoundary(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return this
    return this
        .drawWithCache {
            val w = size.width.toInt()
            val h = size.height.toInt()
            val node = if (w > 0 && h > 0) createDamageNode(w, h) else null
            onDrawWithContent {
                val target = drawContext.canvas
                if (node == null || !target.nativeCanvas.isHardwareAccelerated) {
                    drawContent()
                    return@onDrawWithContent
                }
                drawContext.canvas = Canvas(beginDamageRecord(node))
                try {
                    drawContent()
                } finally {
                    drawContext.canvas = target
                    endDamageRecord(node)
                }
                target.nativeCanvas.drawDamage(node)
            }
        }
        .graphicsLayer()
}

@Stable
class OndaBackdropState {
    internal var renderNode by mutableStateOf<Any?>(null)
    internal var rootPosition by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberOndaBackdropState(): OndaBackdropState {
    return remember { OndaBackdropState() }
}

val LocalOndaBackdropState = staticCompositionLocalOf<OndaBackdropState?> { null }

@RequiresApi(Build.VERSION_CODES.Q)
private fun backdropNodeFor(existing: Any?, width: Int, height: Int): Any {
    return (existing as? RenderNode ?: RenderNode("OndaBackdrop")).apply {
        setPosition(0, 0, width, height)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun backdropHasDisplayList(node: Any): Boolean = (node as RenderNode).hasDisplayList()

@RequiresApi(Build.VERSION_CODES.Q)
private fun backdropBegin(node: Any): android.graphics.Canvas = (node as RenderNode).beginRecording()

@RequiresApi(Build.VERSION_CODES.Q)
private fun backdropEnd(node: Any) {
    (node as RenderNode).endRecording()
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun android.graphics.Canvas.drawBackdrop(node: Any) {
    drawRenderNode(node as RenderNode)
}

fun Modifier.ondaBlurSource(
    state: OndaBackdropState,
    isActive: () -> Boolean = { true }
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return this
    return this
        .onGloballyPositioned { state.rootPosition = it.positionInWindow() }
        .drawWithCache {
            val w = size.width.toInt()
            val h = size.height.toInt()
            val node = if (w > 0 && h > 0) backdropNodeFor(state.renderNode, w, h) else null
            state.renderNode = node
            onDrawWithContent {
                val target = drawContext.canvas
                if (node == null || !isActive() || !target.nativeCanvas.isHardwareAccelerated) {
                    drawContent()
                    return@onDrawWithContent
                }
                drawContext.canvas = Canvas(backdropBegin(node))
                try {
                    drawContent()
                } finally {
                    drawContext.canvas = target
                    backdropEnd(node)
                }
                target.nativeCanvas.drawBackdrop(node)
            }
        }
}

private const val VARIABLE_BLUR_AGSL = """
uniform shader content;
uniform float2 dir;
uniform float2 size;
uniform float2 origin;
uniform float2 full;
uniform float maxRadius;
uniform float growDown;
uniform float ramp;
uniform float preSigma;

half4 main(float2 p) {
    float2 g = (p + origin) / full;
    float along = clamp(g.y, 0.0, 1.0);
    if (growDown < 0.5) { along = 1.0 - along; }
    float t = clamp(along / ramp, 0.0, 1.0);
    float r = maxRadius * t * t;
    float sigma = 0.57735 * r + 0.5;
    float rest = sigma * sigma - preSigma * preSigma;
    if (r < 0.5 || rest < 0.25) { return content.eval(p); }
    float stepPx = sqrt(rest) * 0.25;
    float4 sum = float4(0.0);
    float total = 0.0;
    for (int i = -10; i <= 10; i++) {
        float2 q = p + dir * (float(i) * stepPx);
        float inside = step(0.0, q.x) * step(q.x, size.x) * step(0.0, q.y) * step(q.y, size.y);
        float w = exp(-0.045 * float(i * i)) * inside;
        sum += float4(content.eval(q)) * w;
        total += w;
    }
    return half4(sum / max(total, 0.0001));
}
"""

private object VariableBlurShaderHolder {
    var shader: Any? = null
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun getOrCreateVariableBlurShader(): RuntimeShader {
    val existing = VariableBlurShaderHolder.shader as? RuntimeShader
    if (existing != null) return existing
    return RuntimeShader(VARIABLE_BLUR_AGSL).also { VariableBlurShaderHolder.shader = it }
}

private const val BandGridPx = 4f
private const val MaxProgressiveBands = 3
private val StepRadiusThresholds = floatArrayOf(8f, 16f, 32f)

private class ProgressiveBand {
    var downscale = 1
    var start = 0f
    var end = 0f
    var captureStart = 0f
    var captureEnd = 0f
    var preSigma = 0f
}

private class ProgressiveBandPlan {
    val bands = Array(MaxProgressiveBands) { ProgressiveBand() }
    var count = 0
    private var cachedLength = -1f
    private var cachedRadius = -1f
    private var cachedRamp = -1f

    fun update(length: Float, radius: Float, ramp: Float) {
        if (length == cachedLength && radius == cachedRadius && ramp == cachedRamp) return
        cachedLength = length
        cachedRadius = radius
        cachedRamp = ramp

        val rampLength = max(ramp * length, 1f)
        fun radiusAt(pos: Float): Float {
            val t = (pos / rampLength).coerceIn(0f, 1f)
            return radius * t * t
        }
        fun downscaleFor(r: Float): Int = when {
            r <= 8f -> 1
            r <= 24f -> 2
            else -> 4
        }
        fun reachPx(r: Float, k: Int, preSigma: Float): Float {
            val sigma = 0.57735f * r / k + 0.5f
            return (3f * sigma + 3f * preSigma + 1f) * k
        }

        val starts = FloatArray(MaxProgressiveBands)
        val scales = IntArray(MaxProgressiveBands)
        count = 1
        starts[0] = 0f
        scales[0] = downscaleFor(0f)

        for (step in StepRadiusThresholds) {
            if (count == MaxProgressiveBands || step >= radius || radius <= 0.1f) break
            val k = downscaleFor(step)
            if (k == scales[count - 1]) continue
            val at = (rampLength * sqrt(step / radius) / BandGridPx).roundToInt() * BandGridPx
            if (at <= starts[count - 1] || at >= length) continue
            starts[count] = at
            scales[count] = k
            count += 1
        }

        for (i in 0 until count) {
            val band = bands[i]
            band.downscale = scales[i]
            band.start = starts[i]
            band.end = if (i + 1 < count) starts[i + 1] else length
            val strongest = 0.57735f * radiusAt(band.end) / band.downscale + 0.5f
            band.preSigma = (strongest * 0.15f).takeIf { it >= 0.6f } ?: 0f
            var before = 0f
            var after = 0f
            for (j in 0..12) {
                val a = band.start + (band.end - band.start) * j / 12f
                val r = reachPx(radiusAt(a), band.downscale, band.preSigma)
                before = max(before, r - (a - band.start))
                after = max(after, a + r - band.end)
            }
            band.captureStart = (floor((band.start - before) / BandGridPx) * BandGridPx).coerceAtLeast(0f)
            band.captureEnd = (ceil((band.end + after) / BandGridPx) * BandGridPx).coerceAtMost(length)
        }
    }
}

private class CachedBandEffect {
    var downscale = 0
    var preSigma = 0f
    var maxRadius = 0f
    var full = Size.Zero
    var layerSize = IntSize.Zero
    var origin = Offset.Zero
    var growDown = false
    var ramp = 0f
    var mask: Brush? = null
    var effect: RenderEffect? = null
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun CachedBandEffect.effectFor(
    band: ProgressiveBand,
    maxRadius: Float,
    full: Size,
    layerSize: IntSize,
    origin: Offset,
    growDown: Boolean,
    ramp: Float,
    mask: Brush?
): RenderEffect {
    val current = effect
    if (current != null &&
        downscale == band.downscale &&
        preSigma == band.preSigma &&
        this.maxRadius == maxRadius &&
        this.full == full &&
        this.layerSize == layerSize &&
        this.origin == origin &&
        this.growDown == growDown &&
        this.ramp == ramp &&
        this.mask == mask
    ) {
        return current
    }
    downscale = band.downscale
    preSigma = band.preSigma
    this.maxRadius = maxRadius
    this.full = full
    this.layerSize = layerSize
    this.origin = origin
    this.growDown = growDown
    this.ramp = ramp
    this.mask = mask
    return buildVariableBlurEffect(band, maxRadius, full, layerSize, origin, growDown, ramp, mask)
        .also { effect = it }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildVariableBlurEffect(
    band: ProgressiveBand,
    maxRadius: Float,
    full: Size,
    layerSize: IntSize,
    origin: Offset,
    growDown: Boolean,
    ramp: Float,
    mask: Brush?
): RenderEffect {
    val k = band.downscale.toFloat()
    val shader = getOrCreateVariableBlurShader()
    shader.setFloatUniform("size", layerSize.width.toFloat(), layerSize.height.toFloat())
    shader.setFloatUniform("origin", origin.x, origin.y)
    shader.setFloatUniform("full", full.width / k, full.height / k)
    shader.setFloatUniform("maxRadius", maxRadius / k)
    shader.setFloatUniform("growDown", if (growDown) 1f else 0f)
    shader.setFloatUniform("ramp", ramp.coerceIn(0.01f, 1f))
    shader.setFloatUniform("preSigma", band.preSigma)

    shader.setFloatUniform("dir", 1f, 0f)
    val horizontal = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    shader.setFloatUniform("dir", 0f, 1f)
    val vertical = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")

    val firstPass = if (band.preSigma > 0f) {
        val preRadius = ((band.preSigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
        val pre = android.graphics.RenderEffect.createBlurEffect(preRadius, preRadius, Shader.TileMode.CLAMP)
        android.graphics.RenderEffect.createChainEffect(horizontal, pre)
    } else {
        horizontal
    }
    val blurred = android.graphics.RenderEffect.createChainEffect(vertical, firstPass)
    val maskShader = (mask as? ShaderBrush)?.createShader(Size(full.width / k, full.height / k))
    val resolved = if (maskShader != null) {
        maskShader.setLocalMatrix(Matrix().apply { setTranslate(-origin.x, -origin.y) })
        android.graphics.RenderEffect.createBlendModeEffect(
            blurred,
            android.graphics.RenderEffect.createShaderEffect(maskShader),
            android.graphics.BlendMode.DST_IN
        )
    } else {
        blurred
    }
    return resolved.asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ProgressiveBlurBandsLayer(
    radius: () -> Float,
    mask: Brush?,
    growDown: Boolean,
    ramp: Float,
    modifier: Modifier = Modifier,
    drawContentBlock: DrawScope.(fullSize: Size) -> Unit
) {
    val currentRadius by rememberUpdatedState(radius)
    val currentDraw by rememberUpdatedState(drawContentBlock)
    val layers = arrayOf(rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer())
    val frames = arrayOf(rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer())
    val plan = remember { ProgressiveBandPlan() }
    val effects = remember { Array(MaxProgressiveBands) { CachedBandEffect() } }

    Box(modifier = modifier.clipToBounds()) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val full = size
                    val length = full.height
                    if (length <= 0f || full.minDimension <= 0f) return@drawBehind
                    val r = currentRadius()
                    if (r <= 0.5f) {
                        currentDraw(full)
                        return@drawBehind
                    }
                    val coarsest = if (r <= 8f) 1 else if (r <= 24f) 2 else 4
                    val maxRadius = BlurEffectCache.quantize(r / coarsest) * coarsest
                    plan.update(length, maxRadius, ramp)

                    for (i in 0 until plan.count) {
                        val band = plan.bands[i]
                        val k = band.downscale.toFloat()
                        val from = (if (growDown) band.captureStart else length - band.captureEnd)
                            .roundToInt().toFloat()
                        val to = if (growDown) band.captureEnd else length - band.captureStart
                        val shownFrom = if (growDown) band.start else length - band.end
                        val shownTo = if (growDown) band.end else length - band.start
                        val height = (to - from).coerceAtLeast(1f)
                        val layerSize = IntSize(
                            width = ceil(full.width / k).toInt().coerceAtLeast(1),
                            height = ceil(height / k).toInt().coerceAtLeast(1)
                        )

                        val layer = layers[i]
                        layer.record(layerSize) {
                            scale(1f / k, 1f / k, pivot = Offset.Zero) {
                                translate(left = 0f, top = -from) {
                                    currentDraw(full)
                                }
                            }
                        }
                        layer.renderEffect = effects[i].effectFor(
                            band = band,
                            maxRadius = maxRadius,
                            full = full,
                            layerSize = layerSize,
                            origin = Offset(0f, from / k),
                            growDown = growDown,
                            ramp = ramp,
                            mask = mask
                        )
                        layer.clip = true
                        layer.compositingStrategy = LayerCompositing.Auto

                        val frame = frames[i]
                        frame.record(layerSize) { drawLayer(layer) }
                        frame.compositingStrategy = LayerCompositing.Offscreen
                        frame.pivotOffset = Offset.Zero
                        frame.scaleX = k
                        frame.scaleY = k
                        frame.topLeft = IntOffset(0, from.roundToInt())
                        frame.clip = true

                        clipRect(left = 0f, top = shownFrom, right = full.width, bottom = shownTo) {
                            drawLayer(frame)
                        }
                    }
                }
        )
    }
}

@Composable
fun ProgressiveBlurBackground(
    modifier: Modifier = Modifier,
    backdropState: OndaBackdropState? = null,
    direction: BlurDirection = BlurDirection.BOTTOM_TO_TOP,
    maxBlurRadius: () -> Float = { 28f },
    tint: Color = Color.Transparent,
    foundationColor: Color = GlassTokens.AmoledBlack,
    blurEnabled: Boolean = true,
    steps: Int = 3
) {
    val growDown = direction == BlurDirection.TOP_TO_BOTTOM
    var headerPos by remember { mutableStateOf(Offset.Zero) }
    val safeSteps = steps.coerceIn(1, 5)
    val fade = 1f / safeSteps

    val mask = remember(direction, fade) {
        if (direction == BlurDirection.TOP_TO_BOTTOM) {
            Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black, 1f to Color.Black)
        } else {
            Brush.verticalGradient(0f to Color.Black, (1f - fade) to Color.Black, 1f to Color.Transparent)
        }
    }

    val scrimBrush = remember(direction, foundationColor, tint) {
        val base = foundationColor
        if (direction == BlurDirection.TOP_TO_BOTTOM) {
            Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.32f to base.copy(alpha = 0.42f),
                0.70f to base.copy(alpha = 0.78f),
                1.00f to base.copy(alpha = 0.94f)
            )
        } else {
            Brush.verticalGradient(
                0.00f to base.copy(alpha = 0.95f),
                0.45f to base.copy(alpha = 0.74f),
                0.78f to base.copy(alpha = 0.36f),
                1.00f to Color.Transparent
            )
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { headerPos = it.positionInWindow() }
            .clipToBounds()
    ) {
        if (backdropState != null && blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ProgressiveBlurBandsLayer(
                radius = maxBlurRadius,
                mask = mask,
                growDown = growDown,
                ramp = 0.85f,
                modifier = Modifier.fillMaxSize()
            ) { fullSize ->
                drawRect(color = foundationColor, size = fullSize)
                val node = backdropState.renderNode
                if (node != null && backdropHasDisplayList(node)) {
                    val relX = headerPos.x - backdropState.rootPosition.x
                    val relY = headerPos.y - backdropState.rootPosition.y
                    translate(left = -relX, top = -relY) {
                        drawIntoCanvas { it.nativeCanvas.drawBackdrop(node) }
                    }
                }
            }
        } else if (backdropState != null && blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (i in 1..safeSteps) {
                val fraction = i.toFloat() / safeSteps
                val fadeStart = (i - 1).toFloat() / safeSteps
                val heightFraction = 1f - fadeStart
                if (heightFraction > 0f) {
                    val internalFade = (1f / safeSteps) / heightFraction
                    val stepMask = remember(direction, internalFade) {
                        if (direction == BlurDirection.TOP_TO_BOTTOM) {
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                internalFade to Color.Black,
                                1f to Color.Black
                            )
                        } else {
                            Brush.verticalGradient(
                                0f to Color.Black,
                                (1f - internalFade) to Color.Black,
                                1f to Color.Transparent
                            )
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction)
                            .align(
                                if (direction == BlurDirection.TOP_TO_BOTTOM) {
                                    Alignment.BottomCenter
                                } else {
                                    Alignment.TopCenter
                                }
                            )
                            .graphicsLayer {
                                val r = maxBlurRadius() * fraction * fraction
                                renderEffect = BlurEffectCache.get(r)
                                compositingStrategy = CompositingStrategy.Offscreen
                                clip = true
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = stepMask, blendMode = BlendMode.DstIn)
                            }
                            .drawBehind {
                                drawRect(color = foundationColor)
                                val node = backdropState.renderNode
                                if (node != null && backdropHasDisplayList(node)) {
                                    val relX = headerPos.x - backdropState.rootPosition.x
                                    val relY = headerPos.y - backdropState.rootPosition.y
                                    translate(left = -relX, top = -relY) {
                                        drawIntoCanvas { it.nativeCanvas.drawBackdrop(node) }
                                    }
                                }
                            }
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(brush = scrimBrush)
                    if (tint != Color.Transparent) {
                        drawRect(color = tint)
                    }
                }
        )
    }
}

@Composable
fun OndaSampledGlassBackdrop(
    backdropState: OndaBackdropState?,
    modifier: Modifier = Modifier,
    blurRadiusPx: Float = 42f,
    tintColor: Color = Color(0xFF141416),
    tintAlpha: Float = 0.74f,
    fallbackAlpha: Float = 0.94f,
    foundationColor: Color = GlassTokens.AmoledBlack,
    sheenAlpha: Float = 0.08f,
    blurEnabled: Boolean = true
) {
    var surfacePos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val dx = pos.x - surfacePos.x
                val dy = pos.y - surfacePos.y
                if (dx * dx + dy * dy > 0.25f) {
                    surfacePos = pos
                }
            }
            .clipToBounds()
            .graphicsLayer()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdropState != null && blurEnabled) {
            val sheenBrush = remember(sheenAlpha) {
                if (sheenAlpha > 0f) {
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = sheenAlpha),
                        0.38f to Color.Transparent
                    )
                } else {
                    null
                }
            }
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = BlurEffectCache.get(blurRadiusPx)
                        clip = true
                    }
                    .drawBehind {
                        drawRect(color = foundationColor)
                        val node = backdropState.renderNode
                        if (node != null && backdropHasDisplayList(node)) {
                            val relX = surfacePos.x - backdropState.rootPosition.x
                            val relY = surfacePos.y - backdropState.rootPosition.y
                            translate(left = -relX, top = -relY) {
                                drawIntoCanvas { it.nativeCanvas.drawBackdrop(node) }
                            }
                        }
                    }
            )
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(color = tintColor.copy(alpha = tintAlpha))
                        if (sheenBrush != null) {
                            drawRect(brush = sheenBrush)
                        }
                    }
            )
        } else {
            val sheenBrush = remember(sheenAlpha) {
                if (sheenAlpha > 0f) {
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = sheenAlpha * 0.75f),
                        0.35f to Color.Transparent
                    )
                } else {
                    null
                }
            }
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(color = tintColor.copy(alpha = fallbackAlpha))
                        if (sheenBrush != null) {
                            drawRect(brush = sheenBrush)
                        }
                    }
            )
        }
    }
}
