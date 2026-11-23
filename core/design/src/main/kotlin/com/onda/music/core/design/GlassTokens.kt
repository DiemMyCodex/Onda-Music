package com.onda.music.core.design

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    val AmoledBlack = Color(0xFF000000)
    val ObsidianSurface = Color(0xFF141414)
    val SmokedGlassBase = Color(0xFF141414)
    val SmokedGlassCard = Color(0xFF141414)
    val SmokedGlassElevated = Color(0xFF1C1C1C)
    val SmokedGlassDock = Color(0xE6141414)
    val SmokedGlassScrim = Color(0xE6000000)

    val MatteCard = Color(0xFF141414)
    val IconBadgeSurface = Color(0xFF242424)
    val InactiveSurface = Color(0xFF2E2E2E)
    val HairlineDivider = Color(0xFF1F1F1F)

    val WaveCyan = Color(0xFFFFFFFF)
    val IceBlue = Color(0xFFFFFFFF)
    val SilverLavender = Color(0xFF9E9E9E)
    val DeepSlateBlue = Color(0xFF242424)

    val SpecularBright = Color(0x00FFFFFF)
    val SpecularIce = Color(0x00FFFFFF)
    val SpecularEdgeDim = Color(0xFF1F1F1F)
    val SpecularBottomRight = Color(0x00FFFFFF)
    val GlassHighlight = Color(0xFFFFFFFF)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9E9E9E)
    val TextTertiary = Color(0xFF8A8A8A)

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
        if (intensity <= 0f) {
            return SolidColor(Color.Transparent)
        }
        val clamped = intensity.coerceIn(0f, 1.5f)
        val alpha = (0.10f * clamped).coerceIn(0f, 0.24f)
        return SolidColor(Color.White.copy(alpha = alpha))
    }

    fun topEdgeSpecularBrush(
        intensity: Float = 1f,
        accentTint: Color = IceBlue
    ): Brush {
        if (intensity <= 0f) {
            return SolidColor(Color.Transparent)
        }
        val clamped = intensity.coerceIn(0f, 1.5f)
        return Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = (0.24f * clamped).coerceIn(0f, 1f)),
                0.35f to Color.White.copy(alpha = (0.12f * clamped).coerceIn(0f, 1f)),
                1.00f to Color.White.copy(alpha = (0.04f * clamped).coerceIn(0f, 1f))
            )
        )
    }

    fun smokedFillBrush(
        baseColor: Color = SmokedGlassCard,
        accentTint: Color = IceBlue,
        highlightAlpha: Float = 0.08f
    ): Brush {
        return SolidColor(baseColor)
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
            primary = Color.White,
            secondary = Color(0xFF9E9E9E),
            tertiary = Color(0xFF242424),
            muted = Color(0xFF2E2E2E),
            deepShadow = Color(0xFF000000),
            onAccent = Color(0xFF000000)
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
