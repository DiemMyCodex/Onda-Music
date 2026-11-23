package com.onda.music.core.design

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object GlassTokens {
    val AmoledBlack = Color(0xFF050609)
    val ObsidianSurface = Color(0xFF0C0F16)
    val SmokedGlassBase = Color(0xCC111520)
    val SmokedGlassCard = Color(0xB8141926)
    val SmokedGlassElevated = Color(0xD91A2130)
    val SmokedGlassDock = Color(0xE010141E)
    val SmokedGlassScrim = Color(0xCC06080D)

    val WaveCyan = Color(0xFF7AD3FF)
    val IceBlue = Color(0xFF9EE2FF)
    val SilverLavender = Color(0xFFB8C4E0)
    val DeepSlateBlue = Color(0xFF1F2B3D)

    val SpecularBright = Color(0x94FFFFFF)
    val SpecularIce = Color(0x478ED6FF)
    val SpecularEdgeDim = Color(0x14FFFFFF)
    val SpecularBottomRight = Color(0x249BC3EB)
    val GlassHighlight = Color(0xFFFFFFFF)

    val TextPrimary = Color(0xFFF5F7FB)
    val TextSecondary = Color(0xFFB4BED1)
    val TextTertiary = Color(0xFF8893A8)

    val ErrorCoral = Color(0xFFFF6B7A)
    val SuccessMint = Color(0xFF4ADE80)
    val WarningAmber = Color(0xFFFBBF24)

    val RadiusXs = 8.dp
    val RadiusSm = 12.dp
    val RadiusMd = 18.dp
    val RadiusLg = 24.dp
    val RadiusXl = 30.dp
    val RadiusPill = 999.dp

    val BorderHairline = 0.75.dp
    val BorderStandard = 1.dp
    val BorderProminent = 1.25.dp

    fun specularBorderBrush(
        intensity: Float = 1f,
        accentTint: Color = IceBlue
    ): Brush {
        val clamped = intensity.coerceIn(0f, 1.5f)
        return Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = (0.58f * clamped).coerceIn(0f, 1f)),
                0.18f to accentTint.copy(alpha = (0.28f * clamped).coerceIn(0f, 1f)),
                0.52f to Color.White.copy(alpha = (0.07f * clamped).coerceIn(0f, 1f)),
                0.82f to Color.White.copy(alpha = (0.04f * clamped).coerceIn(0f, 1f)),
                1.00f to SilverLavender.copy(alpha = (0.16f * clamped).coerceIn(0f, 1f))
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    fun topEdgeSpecularBrush(
        intensity: Float = 1f,
        accentTint: Color = IceBlue
    ): Brush {
        val clamped = intensity.coerceIn(0f, 1.5f)
        return Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = (0.48f * clamped).coerceIn(0f, 1f)),
                0.35f to accentTint.copy(alpha = (0.22f * clamped).coerceIn(0f, 1f)),
                1.00f to Color.White.copy(alpha = (0.05f * clamped).coerceIn(0f, 1f))
            )
        )
    }

    fun smokedFillBrush(
        baseColor: Color = SmokedGlassCard,
        accentTint: Color = IceBlue,
        highlightAlpha: Float = 0.08f
    ): Brush {
        return Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to accentTint.copy(alpha = highlightAlpha.coerceIn(0f, 0.35f)),
                0.28f to baseColor,
                1.00f to ObsidianSurface.copy(alpha = baseColor.alpha.coerceAtLeast(0.75f))
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    fun bottomScrimBrush(
        scrimColor: Color = AmoledBlack
    ): Brush {
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.45f to scrimColor.copy(alpha = 0.55f),
                1.00f to scrimColor.copy(alpha = 0.94f)
            )
        )
    }

    fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val c = channel.toDouble().coerceIn(0.0, 1.0)
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }

    fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun meetsWcagAa(
        foreground: Color,
        background: Color,
        largeText: Boolean = false
    ): Boolean {
        val threshold = if (largeText) 3.0 else 4.5
        return contrastRatio(foreground, background) >= threshold
    }

    fun ensureContrastOnDark(
        color: Color,
        background: Color = AmoledBlack,
        minRatio: Double = 4.5
    ): Color {
        if (contrastRatio(color, background) >= minRatio) return color
        var low = 0f
        var high = 1f
        var best = Color.White
        repeat(12) {
            val mid = (low + high) * 0.5f
            val candidate = Color(
                red = color.red + (1f - color.red) * mid,
                green = color.green + (1f - color.green) * mid,
                blue = color.blue + (1f - color.blue) * mid,
                alpha = 1f
            )
            if (contrastRatio(candidate, background) >= minRatio) {
                best = candidate
                high = mid
            } else {
                low = mid
            }
        }
        return best
    }
}

@Immutable
data class OndaArtworkPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val muted: Color,
    val deepShadow: Color,
    val onAccent: Color
) {
    companion object {
        val Default = OndaArtworkPalette(
            primary = GlassTokens.WaveCyan,
            secondary = GlassTokens.SilverLavender,
            tertiary = GlassTokens.DeepSlateBlue,
            muted = Color(0xFF28374D),
            deepShadow = Color(0xFF0A0F1A),
            onAccent = GlassTokens.AmoledBlack
        )
    }
}

object OndaPaletteExtractor {
    fun extractFromBitmapSync(bitmap: Bitmap): OndaArtworkPalette {
        val usableBitmap = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return OndaArtworkPalette.Default
        } else {
            bitmap
        }

        val palette = Palette.from(usableBitmap)
            .maximumColorCount(16)
            .generate()

        val defaultPalette = OndaArtworkPalette.Default
        val vibrant = palette.vibrantSwatch?.rgb?.let(::Color)
        val lightVibrant = palette.lightVibrantSwatch?.rgb?.let(::Color)
        val darkVibrant = palette.darkVibrantSwatch?.rgb?.let(::Color)
        val mutedSwatch = palette.mutedSwatch?.rgb?.let(::Color)
        val darkMuted = palette.darkMutedSwatch?.rgb?.let(::Color)
        val dominant = palette.dominantSwatch?.rgb?.let(::Color)

        val rawPrimary = vibrant ?: lightVibrant ?: dominant ?: defaultPalette.primary
        val primary = GlassTokens.ensureContrastOnDark(rawPrimary, GlassTokens.AmoledBlack, 4.5)
        val secondary = lightVibrant
            ?: mutedSwatch
            ?: GlassTokens.ensureContrastOnDark(dominant ?: defaultPalette.secondary, GlassTokens.AmoledBlack, 3.2)
        val tertiary = darkVibrant ?: mutedSwatch ?: defaultPalette.tertiary
        val muted = mutedSwatch ?: darkMuted ?: defaultPalette.muted
        val deepShadow = darkMuted ?: darkVibrant?.copy(
            red = darkVibrant.red * 0.35f,
            green = darkVibrant.green * 0.35f,
            blue = darkVibrant.blue * 0.35f
        ) ?: defaultPalette.deepShadow

        val onAccent = if (GlassTokens.relativeLuminance(primary) > 0.38) {
            GlassTokens.AmoledBlack
        } else {
            GlassTokens.TextPrimary
        }

        return OndaArtworkPalette(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            muted = muted,
            deepShadow = deepShadow,
            onAccent = onAccent
        )
    }

    suspend fun extractFromBitmap(bitmap: Bitmap): OndaArtworkPalette {
        return withContext(Dispatchers.Default) {
            runCatching { extractFromBitmapSync(bitmap) }
                .getOrDefault(OndaArtworkPalette.Default)
        }
    }

    suspend fun extractFromImageRequest(
        imageLoader: ImageLoader,
        request: ImageRequest
    ): OndaArtworkPalette {
        return runCatching {
            val safeRequest = request.newBuilder()
                .allowHardware(false)
                .size(128)
                .build()
            val result = imageLoader.execute(safeRequest)
            if (result is SuccessResult) {
                val bitmap = result.image.toBitmap()
                extractFromBitmap(bitmap)
            } else {
                OndaArtworkPalette.Default
            }
        }.getOrDefault(OndaArtworkPalette.Default)
    }

    suspend fun extractFromData(
        context: Context,
        imageLoader: ImageLoader,
        data: Any?
    ): OndaArtworkPalette {
        if (data == null) return OndaArtworkPalette.Default
        val request = ImageRequest.Builder(context)
            .data(data)
            .allowHardware(false)
            .size(128)
            .build()
        return extractFromImageRequest(imageLoader, request)
    }

    fun toArgbHex(color: Color): Int = color.toArgb()
}
