package com.onda.music.core.design

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class OndaColors(
    val background: Color = GlassTokens.AmoledBlack,
    val surface: Color = GlassTokens.ObsidianSurface,
    val surfaceGlass: Color = GlassTokens.SmokedGlassBase,
    val surfaceGlassCard: Color = GlassTokens.SmokedGlassCard,
    val surfaceGlassElevated: Color = GlassTokens.SmokedGlassElevated,
    val surfaceGlassDock: Color = GlassTokens.SmokedGlassDock,
    val scrim: Color = GlassTokens.SmokedGlassScrim,
    val accent: Color = GlassTokens.WaveCyan,
    val accentSoft: Color = GlassTokens.IceBlue,
    val accentSecondary: Color = GlassTokens.SilverLavender,
    val textPrimary: Color = GlassTokens.TextPrimary,
    val textSecondary: Color = GlassTokens.TextSecondary,
    val textTertiary: Color = GlassTokens.TextTertiary,
    val borderBright: Color = GlassTokens.SpecularBright,
    val borderDim: Color = GlassTokens.SpecularEdgeDim,
    val error: Color = GlassTokens.ErrorCoral,
    val success: Color = GlassTokens.SuccessMint,
    val warning: Color = GlassTokens.WarningAmber
)

val LocalOndaColors = staticCompositionLocalOf { OndaColors() }
val LocalOndaPalette = staticCompositionLocalOf { OndaArtworkPalette.Default }
val LocalReducedMotion = staticCompositionLocalOf { false }

val OndaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

val OndaShapes = Shapes(
    extraSmall = RoundedCornerShape(GlassTokens.RadiusXs),
    small = RoundedCornerShape(GlassTokens.RadiusSm),
    medium = RoundedCornerShape(GlassTokens.RadiusMd),
    large = RoundedCornerShape(GlassTokens.RadiusLg),
    extraLarge = RoundedCornerShape(GlassTokens.RadiusXl)
)

object OndaTheme {
    val colors: OndaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOndaColors.current

    val palette: OndaArtworkPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalOndaPalette.current

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current
}

private fun readSystemReducedMotion(context: Context): Boolean {
    return runCatching {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }.getOrDefault(false)
}

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    var reducedMotion by remember(context) { mutableStateOf(readSystemReducedMotion(context)) }

    DisposableEffect(context) {
        reducedMotion = readSystemReducedMotion(context)
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = readSystemReducedMotion(context)
            }
        }
        val uri = runCatching {
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        }.getOrNull()
        if (uri != null) {
            runCatching {
                context.contentResolver.registerContentObserver(uri, false, observer)
            }
        }
        onDispose {
            runCatching {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }
    }

    return reducedMotion
}

fun computeOledAdaptiveAccent(source: Color): Color {
    val muted = Color(
        red = source.red * 0.48f + 0.52f,
        green = source.green * 0.48f + 0.52f,
        blue = source.blue * 0.48f + 0.52f,
        alpha = 1f
    )
    return GlassTokens.ensureContrastOnDark(
        color = muted,
        background = GlassTokens.AmoledBlack,
        minRatio = 4.5
    )
}

fun computeOledAdaptivePalette(source: OndaArtworkPalette): OndaArtworkPalette {
    if (source == OndaArtworkPalette.Default) return source
    val adaptivePrimary = computeOledAdaptiveAccent(source.primary)
    val adaptiveSecondary = computeOledAdaptiveAccent(source.secondary).copy(alpha = 0.85f)
    return source.copy(
        primary = adaptivePrimary,
        secondary = adaptiveSecondary,
        onAccent = GlassTokens.AmoledBlack
    )
}

@Composable
fun OndaTheme(
    artworkPalette: OndaArtworkPalette = OndaArtworkPalette.Default,
    adaptiveAccentEnabled: Boolean = false,
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    content: @Composable () -> Unit
) {
    val effectivePalette = remember(artworkPalette, adaptiveAccentEnabled) {
        if (adaptiveAccentEnabled) computeOledAdaptivePalette(artworkPalette) else artworkPalette
    }

    val ondaColors = remember(effectivePalette) {
        OndaColors(
            background = GlassTokens.AmoledBlack,
            surface = GlassTokens.ObsidianSurface,
            accent = effectivePalette.primary,
            accentSoft = effectivePalette.secondary
        )
    }

    val colorScheme = remember(effectivePalette, ondaColors) {
        darkColorScheme(
            primary = effectivePalette.primary,
            onPrimary = effectivePalette.onAccent,
            primaryContainer = effectivePalette.tertiary,
            onPrimaryContainer = GlassTokens.TextPrimary,
            secondary = effectivePalette.secondary,
            onSecondary = GlassTokens.AmoledBlack,
            secondaryContainer = effectivePalette.muted,
            onSecondaryContainer = GlassTokens.TextPrimary,
            tertiary = effectivePalette.tertiary,
            onTertiary = GlassTokens.TextPrimary,
            background = GlassTokens.AmoledBlack,
            onBackground = GlassTokens.TextPrimary,
            surface = GlassTokens.ObsidianSurface,
            onSurface = GlassTokens.TextPrimary,
            surfaceVariant = GlassTokens.SmokedGlassElevated,
            onSurfaceVariant = GlassTokens.TextSecondary,
            outline = GlassTokens.HairlineDivider,
            outlineVariant = GlassTokens.SpecularIce,
            error = GlassTokens.ErrorCoral,
            onError = GlassTokens.AmoledBlack,
            scrim = GlassTokens.SmokedGlassScrim
        )
    }

    CompositionLocalProvider(
        LocalOndaColors provides ondaColors,
        LocalOndaPalette provides effectivePalette,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OndaTypography,
            shapes = OndaShapes,
            content = content
        )
    }
}
