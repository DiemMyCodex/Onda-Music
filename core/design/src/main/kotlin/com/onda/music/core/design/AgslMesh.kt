package com.onda.music.core.design

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val MESH_LOOP_SECONDS = 100f
private const val MESH_STEP_INTERVAL_SECONDS = 1f / 30f
private const val MESH_DOWNSCALE = 4

private const val ONDA_MESH_AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform half4 uBaseColor;
uniform half4 uColor0;
uniform half4 uColor1;
uniform half4 uColor2;
uniform half4 uColor3;
uniform float uDimAlpha;

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = fragCoord / res;
    float aspect = res.x / res.y;
    float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);

    float t = uTime * (6.2831853 / 100.0) * 4.0;

    float2 c0 = float2(sin(t * 2.0) * 0.35 * aspect, cos(t * 1.0) * 0.30 - 0.12);
    float2 c1 = float2(cos(t * 1.0) * 0.38 * aspect, sin(t * 2.0) * 0.32 + 0.10);
    float2 c2 = float2(sin(t * 1.0 + 2.1) * 0.32 * aspect, cos(t * 2.0 + 1.3) * 0.28);
    float2 c3 = float2(cos(t * 1.0 + 4.0) * 0.26 * aspect, sin(t * 1.0 + 2.4) * 0.25 - 0.05);

    float w0 = exp(-dot(p - c0, p - c0) * 2.4);
    float w1 = exp(-dot(p - c1, p - c1) * 2.1);
    float w2 = exp(-dot(p - c2, p - c2) * 2.6);
    float w3 = exp(-dot(p - c3, p - c3) * 1.9);

    half4 mesh = uBaseColor;
    mesh = mix(mesh, uColor0, half(clamp(w0 * 0.78, 0.0, 1.0)));
    mesh = mix(mesh, uColor1, half(clamp(w1 * 0.68, 0.0, 1.0)));
    mesh = mix(mesh, uColor2, half(clamp(w2 * 0.58, 0.0, 1.0)));
    mesh = mix(mesh, uColor3, half(clamp(w3 * 0.62, 0.0, 1.0)));

    float vignette = smoothstep(1.15, 0.15, length(p));
    mesh = mix(uBaseColor, mesh, half(clamp(vignette * (1.0 - uDimAlpha * 0.45), 0.15, 1.0)));
    mesh.a = half(1.0);
    return mesh;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslMeshShaderHolder {
    val shader: RuntimeShader? = runCatching { RuntimeShader(ONDA_MESH_AGSL) }.getOrNull()
    val brush: ShaderBrush? = shader?.let { ShaderBrush(it) }

    fun updateUniforms(
        width: Float,
        height: Float,
        timeSeconds: Float,
        palette: OndaArtworkPalette,
        dimAlpha: Float
    ) {
        val s = shader ?: return
        s.setFloatUniform("uResolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        s.setFloatUniform("uTime", timeSeconds)
        s.setFloatUniform(
            "uBaseColor",
            GlassTokens.AmoledBlack.red,
            GlassTokens.AmoledBlack.green,
            GlassTokens.AmoledBlack.blue,
            1f
        )
        s.setFloatUniform(
            "uColor0",
            palette.primary.red,
            palette.primary.green,
            palette.primary.blue,
            1f
        )
        s.setFloatUniform(
            "uColor1",
            palette.secondary.red,
            palette.secondary.green,
            palette.secondary.blue,
            1f
        )
        s.setFloatUniform(
            "uColor2",
            palette.tertiary.red,
            palette.tertiary.green,
            palette.tertiary.blue,
            1f
        )
        s.setFloatUniform(
            "uColor3",
            palette.deepShadow.red,
            palette.deepShadow.green,
            palette.deepShadow.blue,
            1f
        )
        s.setFloatUniform("uDimAlpha", dimAlpha.coerceIn(0f, 1f))
    }
}

object AgslMesh {
    val isAgslSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

private fun Modifier.downscaledMeshLayer(): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    } else {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val downWidth = ((width + MESH_DOWNSCALE - 1) / MESH_DOWNSCALE).coerceAtLeast(1)
        val downHeight = ((height + MESH_DOWNSCALE - 1) / MESH_DOWNSCALE).coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(downWidth, downHeight))
        layout(width, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = MESH_DOWNSCALE.toFloat()
                scaleY = MESH_DOWNSCALE.toFloat()
                transformOrigin = TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }
}

@Composable
fun AgslMeshBackground(
    modifier: Modifier = Modifier,
    palette: OndaArtworkPalette = OndaTheme.palette,
    animated: Boolean = true,
    speed: Float = 1f,
    dimAlpha: Float = 0.58f,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    val reducedMotion = OndaTheme.reducedMotion
    val shouldAnimate = animated && !reducedMotion
    var steppedTimeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(shouldAnimate, speed) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastNanos = 0L
        var accumulator = 0f
        var rawTime = steppedTimeSeconds
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                    val scaledDt = dt * speed
                    rawTime = (rawTime + scaledDt) % MESH_LOOP_SECONDS
                    accumulator += dt
                    if (accumulator >= MESH_STEP_INTERVAL_SECONDS) {
                        accumulator %= MESH_STEP_INTERVAL_SECONDS
                        steppedTimeSeconds = rawTime
                    }
                }
                lastNanos = frameNanos
            }
        }
    }

    val agslHolder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { AgslMeshShaderHolder() }
    } else {
        null
    }

    val meshLayerModifier = remember(palette, dimAlpha, agslHolder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslHolder?.brush != null) {
            Modifier.drawWithCache {
                val dimOverlayAlpha = (dimAlpha * 0.42f).coerceIn(0f, 0.85f)
                val dimColor = GlassTokens.AmoledBlack.copy(alpha = dimOverlayAlpha)
                onDrawBehind {
                    agslHolder.updateUniforms(
                        width = size.width,
                        height = size.height,
                        timeSeconds = steppedTimeSeconds,
                        palette = palette,
                        dimAlpha = dimAlpha
                    )
                    drawRect(brush = agslHolder.brush)
                    if (dimAlpha > 0f) {
                        drawRect(color = dimColor)
                    }
                }
            }
        } else {
            Modifier.drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val maxDim = maxOf(w, h)
                val orbAlphaScale = (1f - dimAlpha * 0.48f).coerceIn(0.25f, 0.85f)

                val radius0 = maxDim * 0.72f
                val radius1 = maxDim * 0.68f
                val radius2 = maxDim * 0.78f

                val brush0 = Brush.radialGradient(
                    colors = listOf(palette.primary.copy(alpha = 0.45f * orbAlphaScale), Color.Transparent),
                    center = Offset.Zero,
                    radius = radius0
                )
                val brush1 = Brush.radialGradient(
                    colors = listOf(palette.secondary.copy(alpha = 0.36f * orbAlphaScale), Color.Transparent),
                    center = Offset.Zero,
                    radius = radius1
                )
                val brush2 = Brush.radialGradient(
                    colors = listOf(palette.tertiary.copy(alpha = 0.40f * orbAlphaScale), Color.Transparent),
                    center = Offset.Zero,
                    radius = radius2
                )

                onDrawBehind {
                    drawRect(color = GlassTokens.AmoledBlack)
                    val t = steppedTimeSeconds * (2f * PI.toFloat() / MESH_LOOP_SECONDS) * 4f
                    val c0x = w * (0.28f + 0.14f * sin(t))
                    val c0y = h * (0.22f + 0.10f * cos(t * 0.8f))
                    val c1x = w * (0.76f + 0.12f * cos(t * 0.7f))
                    val c1y = h * (0.38f + 0.12f * sin(t * 0.9f))
                    val c2x = w * (0.45f + 0.15f * sin(t * 0.6f + 1.8f))
                    val c2y = h * (0.74f + 0.08f * cos(t * 0.7f + 0.9f))

                    translate(left = c0x, top = c0y) {
                        drawCircle(brush = brush0, radius = radius0, center = Offset.Zero)
                    }
                    translate(left = c1x, top = c1y) {
                        drawCircle(brush = brush1, radius = radius1, center = Offset.Zero)
                    }
                    translate(left = c2x, top = c2y) {
                        drawCircle(brush = brush2, radius = radius2, center = Offset.Zero)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .damageBoundary()
                .downscaledMeshLayer()
                .then(meshLayerModifier)
        )
        content?.invoke(this)
    }
}
