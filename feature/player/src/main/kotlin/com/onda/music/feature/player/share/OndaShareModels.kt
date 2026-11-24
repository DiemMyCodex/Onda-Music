package com.onda.music.feature.player.share

import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.model.LyricsLine
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

enum class OndaCardTemplate(val label: String) {
    VINYL_TICKET("Vinyl Ticket"),
    EDITORIAL_LYRIC("Editorial Lyric"),
    STAGE_POSTER("Stage Poster")
}

enum class OndaCardBackdrop(val label: String) {
    ARTWORK_MIST("Artwork Mist"),
    OLED_SOLID("OLED Solid"),
    ACOUSTIC_GRID("Acoustic Grid"),
    WARM_PAPER("Warm Paper")
}

enum class OndaCardSurfaceTone(val label: String) {
    OBSIDIAN("Obsidian"),
    CHALK("Chalk"),
    PALETTE_FROST("Palette"),
    CLEAR("Clear")
}

enum class OndaWordEmphasis {
    NONE,
    BOLD,
    CAPS,
    LUMINOUS
}

fun OndaWordEmphasis.displayLabel(): String = when (this) {
    OndaWordEmphasis.NONE -> "Normal"
    OndaWordEmphasis.BOLD -> "Bold"
    OndaWordEmphasis.CAPS -> "Caps"
    OndaWordEmphasis.LUMINOUS -> "Luminous"
}

data class EditorialSurfaceStyle(
    val fillArgb: Int,
    val primaryInkArgb: Int,
    val secondaryInkArgb: Int,
    val borderArgb: Int,
    val isLightSurface: Boolean
)

object OndaLyricSelectionLogic {
    const val MAX_CARD_WORDS = 26
    const val MAX_POSTER_WORDS = 42

    private val whitespaceRegex = Regex("\\s+")

    fun splitWords(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(whitespaceRegex).filter { it.isNotBlank() }
    }

    fun isSelectableLine(line: LyricsLine): Boolean {
        return !line.isInterlude && line.text.isNotBlank()
    }

    fun countWords(indices: List<Int>, lines: List<LyricsLine>): Int {
        var total = 0
        for (index in indices.distinct()) {
            if (index in lines.indices) {
                val line = lines[index]
                if (isSelectableLine(line)) {
                    total += splitWords(line.text).size
                }
            }
        }
        return total
    }

    fun toggleContiguousLine(
        currentIndices: List<Int>,
        tappedIndex: Int,
        lines: List<LyricsLine>,
        maxWords: Int = MAX_CARD_WORDS
    ): List<Int> {
        if (tappedIndex !in lines.indices || !isSelectableLine(lines[tappedIndex])) {
            return currentIndices
        }
        val clean = currentIndices
            .filter { it in lines.indices && isSelectableLine(lines[it]) }
            .distinct()
            .sorted()

        val candidate = when {
            clean.isEmpty() -> listOf(tappedIndex)
            tappedIndex < clean.first() -> {
                (tappedIndex..clean.last()).filter { isSelectableLine(lines[it]) }
            }
            tappedIndex > clean.last() -> {
                (clean.first()..tappedIndex).filter { isSelectableLine(lines[it]) }
            }
            clean.size == 1 && tappedIndex == clean.first() -> emptyList()
            tappedIndex == clean.first() -> clean.drop(1)
            tappedIndex == clean.last() -> clean.dropLast(1)
            else -> {
                val before = clean.filter { it < tappedIndex }
                val after = clean.filter { it > tappedIndex }
                if (after.size > before.size) after else before
            }
        }

        val candidateWords = countWords(candidate, lines)
        val currentWords = countWords(clean, lines)
        return if (candidateWords > maxWords && candidateWords > currentWords) {
            clean
        } else {
            candidate
        }
    }

    fun trimToMaxWords(
        indices: List<Int>,
        lines: List<LyricsLine>,
        maxWords: Int
    ): List<Int> {
        val clean = indices
            .filter { it in lines.indices && isSelectableLine(lines[it]) }
            .distinct()
            .sorted()
            .toMutableList()
        while (clean.isNotEmpty() && countWords(clean, lines) > maxWords) {
            clean.removeAt(clean.lastIndex)
        }
        return clean
    }
}

fun wordEmphasisKey(lineIndex: Int, wordIndex: Int): String = "$lineIndex:$wordIndex"

fun buildOndaWaveformBars(seedKey: String, count: Int = 36): List<Float> {
    if (count <= 0) return emptyList()
    var state = seedKey.hashCode().toLong() and 0xFFFFFFFFL
    if (state == 0L) state = 0x5EED1024L
    return List(count) { index ->
        state = state xor (state shl 13)
        state = state xor (state ushr 17)
        state = state xor (state shl 5)
        val raw = ((abs(state) % 1000L).toFloat() / 1000f)
        val centerBias = 1f - (abs((index.toFloat() / (count - 1).coerceAtLeast(1)) - 0.5f) * 0.72f)
        (0.18f + raw * 0.82f * centerBias).coerceIn(0.18f, 1.0f)
    }
}

fun backdropBaseArgb(backdrop: OndaCardBackdrop): Int = when (backdrop) {
    OndaCardBackdrop.ARTWORK_MIST -> 0xFF0A0A0C.toInt()
    OndaCardBackdrop.OLED_SOLID -> 0xFF050507.toInt()
    OndaCardBackdrop.ACOUSTIC_GRID -> 0xFF0A0B0E.toInt()
    OndaCardBackdrop.WARM_PAPER -> 0xFFEDE6D8.toInt()
}

fun computeArgbLuminance(argb: Int): Float {
    fun linearize(channel: Int): Float {
        val c = (channel and 0xFF) / 255f
        return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    val r = linearize((argb ushr 16) and 0xFF)
    val g = linearize((argb ushr 8) and 0xFF)
    val b = linearize(argb and 0xFF)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

fun composeColorToArgb(color: androidx.compose.ui.graphics.Color): Int {
    val a = (color.alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    val r = (color.red.coerceIn(0f, 1f) * 255f).roundToInt()
    val g = (color.green.coerceIn(0f, 1f) * 255f).roundToInt()
    val b = (color.blue.coerceIn(0f, 1f) * 255f).roundToInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun withArgbAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    return (a shl 24) or (argb and 0x00FFFFFF)
}

fun blendArgbOver(fgRgb: Int, bgArgb: Int, fgAlpha: Float): Int {
    val a = fgAlpha.coerceIn(0f, 1f)
    val inv = 1f - a
    val fr = (fgRgb ushr 16) and 0xFF
    val fg = (fgRgb ushr 8) and 0xFF
    val fb = fgRgb and 0xFF
    val br = (bgArgb ushr 16) and 0xFF
    val bg = (bgArgb ushr 8) and 0xFF
    val bb = bgArgb and 0xFF
    val r = (fr * a + br * inv).roundToInt().coerceIn(0, 255)
    val g = (fg * a + bg * inv).roundToInt().coerceIn(0, 255)
    val b = (fb * a + bb * inv).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

fun resolveEditorialSurfaceStyle(
    backdrop: OndaCardBackdrop,
    surfaceTone: OndaCardSurfaceTone,
    cardOpacity: Float,
    palette: OndaArtworkPalette
): EditorialSurfaceStyle {
    val clampedOpacity = cardOpacity.coerceIn(0.35f, 1.0f)
    val backdropBase = backdropBaseArgb(backdrop)

    val (surfaceRgb, fillAlpha) = when (surfaceTone) {
        OndaCardSurfaceTone.OBSIDIAN -> 0xFF121216.toInt() to clampedOpacity
        OndaCardSurfaceTone.CHALK -> 0xFFF4F3EE.toInt() to clampedOpacity
        OndaCardSurfaceTone.PALETTE_FROST -> {
            val primaryRgb = composeColorToArgb(palette.primary)
            val tertiaryRgb = composeColorToArgb(palette.tertiary)
            val mixed = blendArgbOver(primaryRgb, tertiaryRgb, 0.30f)
            mixed to clampedOpacity
        }
        OndaCardSurfaceTone.CLEAR -> {
            val clearTint = if (backdrop == OndaCardBackdrop.WARM_PAPER) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF0A0A0D.toInt()
            }
            clearTint to (0.06f + (clampedOpacity - 0.35f) * 0.14f).coerceIn(0.05f, 0.16f)
        }
    }

    val effectiveSurface = blendArgbOver(surfaceRgb, backdropBase, fillAlpha)
    val isLight = computeArgbLuminance(effectiveSurface) > 0.55f
    val fillArgb = withArgbAlpha(surfaceRgb, fillAlpha)

    val primaryInk = if (isLight) 0xFF111113.toInt() else 0xFFFFFFFF.toInt()
    val secondaryInk = if (isLight) 0xB8111113.toInt() else 0xB8FFFFFF.toInt()
    val border = if (isLight) 0x24111113.toInt() else 0x2AFFFFFF.toInt()

    return EditorialSurfaceStyle(
        fillArgb = fillArgb,
        primaryInkArgb = primaryInk,
        secondaryInkArgb = secondaryInk,
        borderArgb = border,
        isLightSurface = isLight
    )
}
