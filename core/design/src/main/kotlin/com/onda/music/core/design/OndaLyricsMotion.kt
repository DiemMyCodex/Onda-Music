package com.onda.music.core.design

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.onda.music.core.model.LyricsMotionStyle
import com.onda.music.core.model.LyricsWord
import kotlinx.coroutines.delay
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val ONDA_EDGE_SOFT_CHARS = 1
private const val ONDA_EDGE_SOFT_PX = 24f
private const val ONDA_EDGE_LEAD_PX = 6f
private const val ONDA_LATIN_LETTER_EM = 0.55f
private const val ONDA_WAVE_CHARS_BEHIND = 4f
private const val ONDA_WAVE_CHARS_AHEAD = 3f
private const val ONDA_WAVE_LIFT_PX = 4.2f
private const val ONDA_WAVE_ZOOM = 0.085f
private val ONDA_WORD_WAIT_DROP = 3.dp
private val ONDA_HELD_LIFT = 2.2.dp
private const val ONDA_HELD_ZOOM = 0.068f
private const val ONDA_RISE_FOLLOW_SEC = 0.085f
private const val ONDA_SWELL_FOLLOW_SEC = 0.20f
private const val ONDA_RISE_COMPLETE_AT = 0.85f
private const val ONDA_LETTER_RISE_SHARE = 0.55f
private const val ONDA_LETTER_RISE_LEAD_CHARS = 0.5f
private const val ONDA_LETTER_RISE_SPAN_CHARS = 2.5f
private const val ONDA_LETTER_SWELL_LEAD_CHARS = 0.35f
private const val ONDA_LETTER_SWELL_SPAN_CHARS = 1.4f
private const val ONDA_SWELL_ATTACK_FRACTION = 0.20f
private const val ONDA_SWELL_RELEASE_FRACTION = 0.14f
private val ONDA_MOTION_BLEED = 10.dp
private const val ONDA_MERGE_EPSILON_PX = 0.1f
private const val ONDA_NO_LIGATURES = "liga 0, clig 0"
private const val ONDA_SPACE_TIME_WEIGHT = 0.42f
private const val ONDA_NO_GAP_SEC = 0.04
private const val ONDA_FAST_SEC_PER_CHAR = 0.11f
private const val ONDA_SLOW_SEC_PER_CHAR = 0.34f
private const val ONDA_SYLLABLE_GLYPH_WEIGHT = 3
private const val ONDA_GLOW_ATTACK_FRACTION = 0.35f
private const val ONDA_GLOW_RELEASE_FRACTION = 0.30f
private const val ONDA_BLOOM_CUTOFF = 0.01f
private const val ONDA_WAVE_CUTOFF = 0.02f
private const val ONDA_BLOOM_RADIUS_PX = 2.6f
private const val ONDA_BLOOM_STRENGTH = 1.45f
private const val ONDA_BLOOM_LAG_CHARS = 1.5f
private const val ONDA_BLOOM_SPAN_CHARS = 3.4f
private const val ONDA_FAKE_GLOW_ALPHA = 0.18f
private const val ONDA_WORD_BOX_PAD_PX = 3f
private const val ONDA_WAVE_HEAD_STEP_PX = 0.5f
private const val ONDA_WAVE_AMP_STEP = 0.02f

private const val ONDA_BLOOM_AGSL = """
uniform shader content;
uniform float2 layerSize;
uniform float headX;
uniform float lineTop;
uniform float lineBottom;
uniform float lineStart;
uniform float prevTop;
uniform float prevBottom;
uniform float prevEnd;
uniform float hasPrev;
uniform float flowDir;
uniform float bloom;
uniform float bloomR;
uniform float bloomLag;
uniform float bloomSpan;
uniform float3 glowTint;

half4 sampleIn(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x > layerSize.x || p.y > layerSize.y) return half4(0.0);
    return content.eval(p);
}

half4 main(float2 coord) {
    half4 c = sampleIn(coord);

    float onCur = (coord.y < lineTop || coord.y > lineBottom) ? 0.0 : 1.0;
    float onPrev = (hasPrev < 0.5 || coord.y < prevTop || coord.y > prevBottom) ? 0.0 : 1.0;
    float onLine = max(onCur, onPrev);
    if (onLine <= 0.0) return c;

    float dxCur = (coord.x - headX) * flowDir;
    float dxPrev = -((prevEnd - coord.x) + (headX - lineStart)) * flowDir;
    float dx = onPrev > 0.5 ? dxPrev : dxCur;

    float bdx = dx + bloomLag;
    float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
    float bw = 1.0 - ba;
    float bbell = bw * bw * (3.0 - 2.0 * bw);
    if (bbell <= 0.0) return c;

    half4 b = sampleIn(coord + float2(bloomR, bloomR))
            + sampleIn(coord + float2(bloomR, -bloomR))
            + sampleIn(coord + float2(-bloomR, bloomR))
            + sampleIn(coord + float2(-bloomR, -bloomR))
            + sampleIn(coord + float2(bloomR, 0.0))
            + sampleIn(coord - float2(bloomR, 0.0))
            + sampleIn(coord + float2(0.0, bloomR))
            + sampleIn(coord - float2(0.0, bloomR));
    half4 tinted = half4(b.rgb * half3(glowTint), b.a);
    return c + tinted * (bloom * bbell * 0.125);
}
"""

private const val ONDA_OPTICAL_WAVE_AGSL = """
uniform shader content;
uniform float2 layerSize;
uniform float headX;
uniform float spanBehind;
uniform float spanAhead;
uniform float liftPx;
uniform float zoomAmt;
uniform float baseY;
uniform float lineTop;
uniform float lineBottom;
uniform float lineStart;
uniform float prevTop;
uniform float prevBottom;
uniform float prevEnd;
uniform float prevBaseY;
uniform float hasPrev;
uniform float flowDir;
uniform float bloom;
uniform float bloomR;
uniform float bloomLag;
uniform float bloomSpan;
uniform float3 glowTint;

half4 sampleIn(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x > layerSize.x || p.y > layerSize.y) return half4(0.0);
    return content.eval(p);
}

half4 main(float2 coord) {
    float onCur = (coord.y < lineTop || coord.y > lineBottom) ? 0.0 : 1.0;
    float onPrev = (hasPrev < 0.5 || coord.y < prevTop || coord.y > prevBottom) ? 0.0 : 1.0;
    float onLine = max(onCur, onPrev);

    float dxCur = (coord.x - headX) * flowDir;
    float dxPrev = -((prevEnd - coord.x) + (headX - lineStart)) * flowDir;
    float dx = onPrev > 0.5 ? dxPrev : dxCur;
    float bY = onPrev > 0.5 ? prevBaseY : baseY;

    float span = dx < 0.0 ? spanBehind : spanAhead;
    float a = min(1.0, abs(dx) / max(span, 1.0));
    float w = 1.0 - a;
    float bell = w * w * (3.0 - 2.0 * w) * onLine;

    float2 p = coord;
    p.y += liftPx * bell;
    p.y = bY + (p.y - bY) / (1.0 + zoomAmt * bell);

    half4 c = sampleIn(p);

    if (bloom > 0.0 && onLine > 0.0) {
        float bdx = dx + bloomLag;
        float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
        float bw = 1.0 - ba;
        float bbell = bw * bw * (3.0 - 2.0 * bw) * onLine;
        half4 b = sampleIn(p + float2(bloomR, bloomR))
                + sampleIn(p + float2(bloomR, -bloomR))
                + sampleIn(p + float2(-bloomR, bloomR))
                + sampleIn(p + float2(-bloomR, -bloomR))
                + sampleIn(p + float2(bloomR, 0.0))
                + sampleIn(p - float2(bloomR, 0.0))
                + sampleIn(p + float2(0.0, bloomR))
                + sampleIn(p - float2(0.0, bloomR));
        half4 tinted = half4(b.rgb * half3(glowTint), b.a);
        c += tinted * (bloom * bbell * 0.125);
    }
    return c;
}
"""

private var sharedOndaBloomShader: Any? = null
private var sharedOndaWaveShader: Any? = null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun getOrCreateOndaBloomShader(): RuntimeShader? {
    val existing = sharedOndaBloomShader as? RuntimeShader
    if (existing != null) return existing
    return runCatching { RuntimeShader(ONDA_BLOOM_AGSL) }
        .getOrNull()
        ?.also { sharedOndaBloomShader = it }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun getOrCreateOndaWaveShader(): RuntimeShader? {
    val existing = sharedOndaWaveShader as? RuntimeShader
    if (existing != null) return existing
    return runCatching { RuntimeShader(ONDA_OPTICAL_WAVE_AGSL) }
        .getOrNull()
        ?.also { sharedOndaWaveShader = it }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createShaderRenderEffect(shader: RuntimeShader): RenderEffect {
    return android.graphics.RenderEffect
        .createRuntimeShaderEffect(shader, "content")
        .asComposeRenderEffect()
}

class OndaTimedRange(
    val start: Int,
    val end: Int,
    val startTimeSec: Double,
    val endTimeSec: Double,
    val sungLength: Int
)

class OndaMotionCells(
    val starts: IntArray,
    val ends: IntArray,
    val word: IntArray,
    val tiled: Boolean
) {
    companion object {
        val Empty = OndaMotionCells(IntArray(0), IntArray(0), IntArray(0), tiled = false)
    }
}

@Stable
class OndaKaraokeHead(
    val wordStarts: IntArray = IntArray(0),
    val wordEnds: IntArray = IntArray(0),
    val cells: OndaMotionCells = OndaMotionCells.Empty
) {
    val offset = mutableFloatStateOf(0f)
    val glow = mutableFloatStateOf(0f)
    val wordCount: Int get() = wordStarts.size
    val cellCount: Int get() = cells.starts.size
    val rise = FloatArray(cells.starts.size)
    val riseVelocity = FloatArray(cells.starts.size)
    val swell = FloatArray(cells.starts.size)
    val swellVelocity = FloatArray(cells.starts.size)
    val motionTick = mutableIntStateOf(0)

    fun seedSettledFor(offsetChars: Float) {
        if (cellCount == 0) return
        for (c in 0 until cellCount) {
            val w = cells.word[c]
            val wordEnd = wordEnds[w]
            val letterStart = cells.starts[c]
            val risen = if (offsetChars >= wordEnd) {
                1f
            } else {
                ondaSmoothstep((offsetChars - letterStart + ONDA_LETTER_RISE_LEAD_CHARS) / ONDA_LETTER_RISE_SPAN_CHARS)
            }
            rise[c] = risen
            riseVelocity[c] = 0f
            swell[c] = 0f
            swellVelocity[c] = 0f
        }
        motionTick.intValue++
    }

    fun advance(
        timed: List<OndaTimedRange>,
        positionSec: Double,
        dt: Float,
        wordMotion: Boolean
    ): Boolean {
        var next = 0f
        var nextGlow = 0f
        var nextSwell = 0f

        for (index in timed.indices) {
            val word = timed[index]
            if (positionSec < word.startTimeSec) break

            if (positionSec >= word.endTimeSec) {
                next = word.end.toFloat()
                val following = timed.getOrNull(index + 1) ?: continue
                val gap = following.startTimeSec - word.endTimeSec
                if (gap > 0.0 && positionSec < following.startTimeSec) {
                    val through = ((positionSec - word.endTimeSec) / gap).coerceIn(0.0, 1.0)
                    next = word.end + (following.start - word.end) * ondaSmoothstep(through.toFloat())
                }
                continue
            }

            val span = word.endTimeSec - word.startTimeSec
            if (span <= 0.0) break
            val within = ((positionSec - word.startTimeSec) / span).coerceIn(0.0, 1.0)
            val length = (word.end - word.start).coerceAtLeast(1)

            val following = timed.getOrNull(index + 1)
            val tail = if (
                following != null &&
                following.startTimeSec - word.endTimeSec <= ONDA_NO_GAP_SEC &&
                following.start > word.end
            ) {
                following.start - word.end
            } else {
                0
            }

            next = if (tail > 0) {
                val letters = length.toFloat()
                val letterShare = letters / (letters + tail * ONDA_SPACE_TIME_WEIGHT)
                val w = within.toFloat()
                if (w <= letterShare) {
                    word.start + letters * (w / letterShare)
                } else {
                    word.end + tail * ((w - letterShare) / (1f - letterShare))
                }
            } else {
                word.start + (length * within).toFloat()
            }

            val secPerChar = (span / word.sungLength).toFloat()
            val hold = ondaSmoothstep(
                (secPerChar - ONDA_FAST_SEC_PER_CHAR) / (ONDA_SLOW_SEC_PER_CHAR - ONDA_FAST_SEC_PER_CHAR)
            )
            val w = within.toFloat()
            nextGlow = hold *
                ondaSmoothstep(w / ONDA_GLOW_ATTACK_FRACTION) *
                ondaSmoothstep((1f - w) / ONDA_GLOW_RELEASE_FRACTION)
            nextSwell = hold *
                ondaSmoothstep(w / ONDA_SWELL_ATTACK_FRACTION) *
                ondaSmoothstep((1f - w) / ONDA_SWELL_RELEASE_FRACTION)
            break
        }

        var moved = false
        if (next != offset.floatValue) {
            offset.floatValue = next
            moved = true
        }

        val glowNow = glow.floatValue
        val glowNext = if (nextGlow >= glowNow) nextGlow else glowNow + (nextGlow - glowNow) * 0.14f
        if (abs(glowNext - glowNow) > 0.0005f) {
            glow.floatValue = if (glowNext < 0.001f) 0f else glowNext
            moved = true
        } else if (nextGlow == 0f && glowNow != 0f) {
            glow.floatValue = 0f
        }

        if (wordMotion && cellCount > 0) {
            var changed = false
            for (c in 0 until cellCount) {
                val w = cells.word[c]
                val wordStart = wordStarts[w]
                val wordEnd = wordEnds[w]
                val letterStart = cells.starts[c]

                val through = (next - wordStart) / (wordEnd - wordStart).coerceAtLeast(1)
                val wordRise = ondaSmoothstep(through / ONDA_RISE_COMPLETE_AT)
                val letterRise = if (next >= wordEnd) {
                    1f
                } else {
                    ondaSmoothstep((next - letterStart + ONDA_LETTER_RISE_LEAD_CHARS) / ONDA_LETTER_RISE_SPAN_CHARS)
                }
                val riseTarget = wordRise + (letterRise - wordRise) * ONDA_LETTER_RISE_SHARE

                val swellTarget = if (next >= wordStart && next <= wordEnd) {
                    nextSwell * ondaSmoothstep(
                        (next - letterStart + ONDA_LETTER_SWELL_LEAD_CHARS) / ONDA_LETTER_SWELL_SPAN_CHARS
                    )
                } else {
                    0f
                }
                changed = ondaDampedStep(rise, riseVelocity, c, riseTarget, ONDA_RISE_FOLLOW_SEC, dt) or changed
                changed = ondaDampedStep(swell, swellVelocity, c, swellTarget, ONDA_SWELL_FOLLOW_SEC, dt) or changed
            }
            if (changed) {
                motionTick.intValue++
                moved = true
            }
        }
        return moved
    }
}

fun ondaSungLength(text: String, start: Int = 0, end: Int = text.length): Int {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    var count = 0
    var i = safeStart
    while (i < safeEnd) {
        val cp = text.codePointAt(i)
        count += if (isOndaSyllableGlyph(cp)) ONDA_SYLLABLE_GLYPH_WEIGHT else 1
        i += Character.charCount(cp)
    }
    return count.coerceAtLeast(1)
}

fun ondaVisualWordRanges(text: String): Pair<IntArray, IntArray> {
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val width = Character.charCount(cp)
        when {
            Character.isWhitespace(cp) -> i += width
            isOndaSingleGlyphWord(cp) -> {
                starts += i
                ends += i + width
                i += width
            }
            else -> {
                val start = i
                while (i < text.length) {
                    val c = text.codePointAt(i)
                    if (Character.isWhitespace(c) || isOndaSingleGlyphWord(c)) break
                    i += Character.charCount(c)
                }
                starts += start
                ends += i
            }
        }
    }
    return starts.toIntArray() to ends.toIntArray()
}

fun ondaMotionCells(text: String, wordStarts: IntArray, wordEnds: IntArray): OndaMotionCells {
    val ltr = text.none {
        val dir = Character.getDirectionality(it)
        dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }
    val graphemes = if (ltr) {
        BreakIterator.getCharacterInstance().also { it.setText(text) }
    } else {
        null
    }
    val starts = ArrayList<Int>(text.length)
    val ends = ArrayList<Int>(text.length)
    val owner = ArrayList<Int>(text.length)
    for (w in wordStarts.indices) {
        val ws = wordStarts[w]
        val we = wordEnds[w]
        if (graphemes == null || !ondaSplitsIntoLetters(text, ws, we)) {
            starts += ws
            ends += we
            owner += w
            continue
        }
        var s = ws
        while (s < we) {
            var e = graphemes.following(s)
            if (e == BreakIterator.DONE || e > we) e = we
            starts += s
            ends += e
            owner += w
            s = e
        }
    }
    return OndaMotionCells(starts.toIntArray(), ends.toIntArray(), owner.toIntArray(), tiled = ltr)
}

fun ondaKaraokeHeadFor(text: String): OndaKaraokeHead {
    val (starts, ends) = ondaVisualWordRanges(text)
    return OndaKaraokeHead(starts, ends, ondaMotionCells(text, starts, ends))
}

private val ONDA_SYNTH_WHITESPACE_REGEX = Regex("\\s+")

private fun isOndaCompactScriptChar(ch: Char): Boolean {
    val code = ch.code
    return code in 0x4E00..0x9FFF ||
        code in 0x3040..0x30FF ||
        code in 0xAC00..0xD7AF ||
        code in 0x0E00..0x0E7F
}

private fun ondaLyricTokenWeight(token: String): Float {
    val letterDigits = token.count { it.isLetterOrDigit() }
    val compactWeight = if (token.any(::isOndaCompactScriptChar)) {
        ondaSungLength(token)
    } else {
        letterDigits
    }
    val base = compactWeight.coerceAtLeast(2).toFloat()
    val trailingChar = token.lastOrNull { it !in "\"')]}」』”’" }
    val pauseBonus = when (trailingChar) {
        ',', ';', '、', '：' -> 1.4f
        '.', '!', '?', '…', '。' -> 2.2f
        else -> 0f
    }
    return base + pauseBonus
}

fun synthesizeOndaWordTimings(
    text: String,
    lineStartMs: Long,
    lineEndMs: Long = 0L,
    nextLineStartMs: Long? = null,
    explicitWords: List<LyricsWord> = emptyList(),
    preferExplicit: Boolean = true,
    maxTokens: Int = 0
): List<LyricsWord> {
    if (preferExplicit && explicitWords.isNotEmpty()) {
        val explicit = explicitWords.filter { it.text.isNotBlank() }.map { word ->
            LyricsWord(
                text = word.text,
                startTimeMs = word.startTimeMs,
                endTimeMs = word.endTimeMs.coerceAtLeast(word.startTimeMs + 80L)
            )
        }
        if (explicit.isNotEmpty()) {
            return if (maxTokens > 0 && explicit.size > maxTokens) {
                explicit.take(maxTokens - 1) + explicit[maxTokens - 1].let { it.copy(text = "${it.text}...") }
            } else {
                explicit
            }
        }
    }

    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()

    val rawTokens = trimmed
        .split(ONDA_SYNTH_WHITESPACE_REGEX)
        .filter { it.isNotEmpty() }
        .flatMap { token ->
            if (token.length >= 8 && token.any(::isOndaCompactScriptChar)) {
                token.chunked(3)
            } else {
                listOf(token)
            }
        }
    if (rawTokens.isEmpty()) return emptyList()

    val boundedTokens = if (maxTokens > 0 && rawTokens.size > maxTokens) {
        rawTokens.take(maxTokens - 1) + "${rawTokens[maxTokens - 1]}..."
    } else {
        rawTokens
    }

    val weights = boundedTokens.map(::ondaLyricTokenWeight)
    val totalWeight = weights.sum().coerceAtLeast(1f)
    val startMs = lineStartMs.coerceAtLeast(0L)
    val naturalVocalMs = (totalWeight * 155f + 900f).toLong().coerceIn(1_250L, 6_800L)
    val availableSpanMs = when {
        lineEndMs > startMs + 120L -> lineEndMs - startMs
        nextLineStartMs != null && nextLineStartMs > startMs + 120L ->
            ((nextLineStartMs - startMs) * 0.92f).toLong().coerceAtLeast(220L)
        else -> naturalVocalMs
    }
    val minSpanMs = minOf(availableSpanMs, 350L).coerceAtLeast(120L)
    val spanMs = minOf(availableSpanMs, naturalVocalMs).coerceIn(minSpanMs, 6_800L)

    var accumulatedWeight = 0f
    var cursorMs = startMs
    return weights.mapIndexed { index, weight ->
        val sliceStart = cursorMs
        accumulatedWeight += weight
        val sliceEnd = if (index == weights.lastIndex) {
            maxOf(sliceStart + 60L, startMs + spanMs)
        } else {
            val remainingMinMs = (weights.lastIndex - index) * 45L
            val targetEndMs = startMs + ((accumulatedWeight / totalWeight) * spanMs).toLong()
            targetEndMs.coerceIn(
                sliceStart + 60L,
                maxOf(sliceStart + 60L, startMs + spanMs - remainingMinMs)
            )
        }
        cursorMs = sliceEnd
        LyricsWord(
            text = boundedTokens[index],
            startTimeMs = sliceStart,
            endTimeMs = sliceEnd
        )
    }
}

fun computeOndaTimedRanges(
    text: String,
    words: List<LyricsWord>,
    fallbackStartMs: Long = 0L,
    fallbackEndMs: Long = 0L
): List<OndaTimedRange> {
    if (text.isEmpty()) return emptyList()
    val effectiveWords = if (words.isNotEmpty()) {
        words
    } else {
        synthesizeOndaWordTimings(
            text = text,
            lineStartMs = fallbackStartMs,
            lineEndMs = fallbackEndMs,
            preferExplicit = false
        )
    }
    var searchFrom = 0
    val matched = effectiveWords.mapNotNull { word ->
        val trimmed = word.text.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        var start = text.indexOf(trimmed, searchFrom)
        var matchLen = trimmed.length
        if (start < 0) {
            start = text.indexOf(trimmed, searchFrom, ignoreCase = true)
        }
        if (start < 0) {
            val bare = trimmed.removeSuffix("...").trim()
            if (bare.isNotEmpty()) {
                start = text.indexOf(bare, searchFrom, ignoreCase = true)
                matchLen = bare.length
            }
        }
        if (start < 0) return@mapNotNull null
        val end = (start + matchLen).coerceAtMost(text.length)
        searchFrom = end
        val startSec = word.startTimeMs.coerceAtLeast(0L) / 1000.0
        val endMs = word.endTimeMs.coerceAtLeast(word.startTimeMs + 40L)
        val endSec = maxOf(endMs / 1000.0, startSec + 0.04)
        OndaTimedRange(
            start = start,
            end = end,
            startTimeSec = startSec,
            endTimeSec = endSec,
            sungLength = ondaSungLength(text, start, end)
        )
    }
    if (matched.isNotEmpty()) return matched

    val safeStartMs = fallbackStartMs.coerceAtLeast(0L)
    val (starts, ends) = ondaVisualWordRanges(text)
    if (starts.isEmpty()) {
        val fallbackSpanMs = (fallbackEndMs - safeStartMs).takeIf { it > 180L }
            ?.coerceAtMost(6_800L)
            ?: (ondaSungLength(text) * 155L + 900L).coerceIn(1_250L, 6_800L)
        return listOf(
            OndaTimedRange(
                start = 0,
                end = text.length,
                startTimeSec = safeStartMs / 1000.0,
                endTimeSec = (safeStartMs + fallbackSpanMs) / 1000.0,
                sungLength = ondaSungLength(text)
            )
        )
    }
    val weights = FloatArray(starts.size) { idx ->
        ondaLyricTokenWeight(text.substring(starts[idx], ends[idx]))
    }
    val totalWeight = weights.sum().coerceAtLeast(1f)
    val naturalVocalMs = (totalWeight * 155f + 900f).toLong().coerceIn(1_250L, 6_800L)
    val availableSpanMs = (fallbackEndMs - safeStartMs).takeIf { it > 120L } ?: naturalVocalMs
    val minSpanMs = minOf(availableSpanMs, 350L).coerceAtLeast(120L)
    val safeSpanMs = minOf(availableSpanMs, naturalVocalMs).coerceIn(minSpanMs, 6_800L)
    var acc = 0f
    var cursorMs = safeStartMs
    return List(starts.size) { idx ->
        val sliceStart = cursorMs
        acc += weights[idx]
        val sliceEnd = if (idx == starts.lastIndex) {
            maxOf(sliceStart + 60L, safeStartMs + safeSpanMs)
        } else {
            val remainingMinMs = (starts.size - 1 - idx) * 45L
            val target = safeStartMs + ((acc / totalWeight) * safeSpanMs).toLong()
            target.coerceIn(sliceStart + 60L, maxOf(sliceStart + 60L, safeStartMs + safeSpanMs - remainingMinMs))
        }
        cursorMs = sliceEnd
        OndaTimedRange(
            start = starts[idx],
            end = ends[idx],
            startTimeSec = sliceStart / 1000.0,
            endTimeSec = sliceEnd / 1000.0,
            sungLength = ondaSungLength(text, starts[idx], ends[idx])
        )
    }
}

fun Modifier.ondaLayerBleed(bleed: Dp): Modifier =
    layout { measurable, constraints ->
        val px = bleed.roundToPx()
        val placeable = measurable.measure(constraints.offset(px * 2, px * 2))
        layout(
            (placeable.width - px * 2).coerceIn(constraints.minWidth, constraints.maxWidth),
            (placeable.height - px * 2).coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            placeable.place(-px, -px)
        }
    }

@Composable
fun OndaLyricSweepLine(
    text: String,
    words: List<LyricsWord>,
    lineStartMs: Long,
    lineEndMs: Long,
    positionProvider: () -> Long,
    style: TextStyle,
    motionStyle: LyricsMotionStyle,
    motionActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    baseColor: Color = Color(0x5AFFFFFF),
    glowColor: Color = Color.White,
    textAlign: TextAlign = TextAlign.Start,
    waveAmplitudeProvider: () -> Float = { 1f },
    riseAmplitudeProvider: () -> Float = { 1f },
    motionScale: Float = 1f
) {
    val reducedMotion = OndaTheme.reducedMotion
    val effectiveMotion = motionActive && !reducedMotion
    val wordMotion = effectiveMotion && motionStyle.risesLetters
    val useShaderGlow = effectiveMotion && motionStyle.glowEnabled
    val resolvedStyle = remember(style, wordMotion, textAlign) {
        val base = style.copy(textAlign = textAlign)
        if (wordMotion) base.copy(fontFeatureSettings = ONDA_NO_LIGATURES) else base
    }

    if (!effectiveMotion) {
        BasicText(
            text = text,
            style = resolvedStyle,
            color = ColorProducer { activeColor },
            overflow = TextOverflow.Clip,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    val head = rememberOndaKaraokeHead(
        text = text,
        words = words,
        lineStartMs = lineStartMs,
        lineEndMs = lineEndMs,
        positionProvider = positionProvider,
        wordMotion = wordMotion
    )

    val layoutHolder = remember(text) { arrayOfNulls<TextLayoutResult>(1) }
    val sungPath = remember(text) { Path() }
    val reached by remember(head) { derivedStateOf { head.offset.floatValue > 0f } }
    val hasAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val effectModifier = when {
        wordMotion && useShaderGlow -> rememberOndaBloomModifier(
            layoutHolder = layoutHolder,
            head = head,
            textLength = text.length,
            amplitudeProvider = waveAmplitudeProvider,
            glowColor = glowColor,
            bleed = ONDA_MOTION_BLEED,
            pixelScale = motionScale
        )
        !wordMotion && useShaderGlow -> rememberOndaOpticalWaveModifier(
            layoutHolder = layoutHolder,
            head = head,
            textLength = text.length,
            amplitudeProvider = waveAmplitudeProvider,
            glowColor = glowColor,
            bleed = ONDA_MOTION_BLEED,
            pixelScale = motionScale
        )
        else -> Modifier
    }

    val motionModifier = when {
        wordMotion -> rememberOndaWordMotionModifier(
            layoutHolder = layoutHolder,
            head = head,
            riseAmplitudeProvider = riseAmplitudeProvider,
            fakeGlowProvider = { if (useShaderGlow && !hasAgsl) waveAmplitudeProvider() else 0f },
            glowColor = glowColor,
            motionScale = motionScale,
            bleed = ONDA_MOTION_BLEED
        )
        useShaderGlow && !hasAgsl -> rememberOndaFallbackWaveGlowModifier(
            layoutHolder = layoutHolder,
            head = head,
            textLength = text.length,
            amplitudeProvider = waveAmplitudeProvider,
            glowColor = glowColor
        )
        else -> Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .ondaLayerBleed(ONDA_MOTION_BLEED)
            .then(effectModifier)
            .padding(ONDA_MOTION_BLEED)
            .then(motionModifier)
    ) {
        BasicText(
            text = text,
            style = resolvedStyle,
            color = ColorProducer { baseColor },
            overflow = TextOverflow.Clip,
            onTextLayout = { layoutHolder[0] = it },
            modifier = Modifier.fillMaxWidth()
        )

        if (reached) {
            BasicText(
                text = text,
                style = resolvedStyle,
                color = ColorProducer { activeColor },
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .ondaLayerBleed(ONDA_MOTION_BLEED)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .padding(ONDA_MOTION_BLEED)
                    .drawWithContent {
                        val layout = layoutHolder[0] ?: return@drawWithContent
                        val h = head.offset.floatValue
                        val len = text.length
                        if (h <= 0f || len == 0 || layout.lineCount == 0) return@drawWithContent

                        val reach = ONDA_MOTION_BLEED.toPx()
                        val lastLine = layout.lineCount - 1
                        fun topOf(line: Int) = layout.getLineTop(line) - if (line == 0) reach else 0f
                        fun bottomOf(line: Int) = layout.getLineBottom(line) + if (line == lastLine) reach else 0f

                        val headOffset = h.toInt().coerceIn(0, len)
                        val headLine = layout.ondaShownLineFor(headOffset.coerceAtMost(len - 1))
                        val rtl = layout.ondaIsRtlLine(headLine)
                        val flow = if (rtl) -1f else 1f
                        val headX = layout.ondaSweepHeadX(h, headOffset, headLine, len, rtl)
                        val lineTop = topOf(headLine)
                        val lineBottom = bottomOf(headLine)

                        val softFrom = layout.getHorizontalPosition(
                            (headOffset - ONDA_EDGE_SOFT_CHARS).coerceAtLeast(layout.getLineStart(headLine)),
                            true
                        )
                        val softMax = ondaFontPx(layout) * ONDA_LATIN_LETTER_EM
                        val fromX = if ((headX - softFrom) * flow > 0f) {
                            headX - flow * minOf((headX - softFrom) * flow, softMax)
                        } else {
                            headX - ONDA_EDGE_SOFT_PX * flow
                        }
                        val toX = headX + ONDA_EDGE_LEAD_PX * flow

                        sungPath.rewind()
                        for (line in 0..headLine) {
                            var left = layout.getLineLeft(line) - reach
                            var right = layout.getLineRight(line) + reach
                            if (line == headLine && headOffset < len) {
                                if (rtl) left = maxOf(toX, left) else right = minOf(toX, right)
                            }
                            if (right > left) {
                                sungPath.addRect(Rect(left, topOf(line), right, bottomOf(line)))
                            }
                        }

                        clipPath(sungPath) { this@drawWithContent.drawContent() }

                        if (headOffset < len) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black,
                                    1f to Color.Transparent,
                                    startX = fromX,
                                    endX = toX
                                ),
                                topLeft = Offset(minOf(fromX, toX), lineTop),
                                size = Size(abs(toX - fromX), lineBottom - lineTop),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            )
        }
    }
}

@Composable
private fun rememberOndaKaraokeHead(
    text: String,
    words: List<LyricsWord>,
    lineStartMs: Long,
    lineEndMs: Long,
    positionProvider: () -> Long,
    wordMotion: Boolean
): OndaKaraokeHead {
    val timed = remember(text, words, lineStartMs, lineEndMs) {
        computeOndaTimedRanges(
            text = text,
            words = words,
            fallbackStartMs = lineStartMs,
            fallbackEndMs = lineEndMs
        )
    }
    val head = remember(text, words) { ondaKaraokeHeadFor(text) }

    LaunchedEffect(text, timed, positionProvider, wordMotion) {
        val firstStart = timed.firstOrNull()?.startTimeSec
        val lastEnd = timed.lastOrNull()?.endTimeSec
        var lastFrameNanos = -1L
        var lastPosSec = Double.NaN
        var seededInitial = false

        while (true) {
            var moved = false
            var posSec = 0.0
            withFrameNanos { frameNanos ->
                val dt = if (lastFrameNanos < 0L) {
                    0.016f
                } else {
                    ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)
                }
                posSec = positionProvider() / 1000.0
                moved = head.advance(timed, posSec, dt, wordMotion)
                if (!seededInitial && wordMotion) {
                    seededInitial = true
                    if (head.offset.floatValue > 0.5f) {
                        head.seedSettledFor(head.offset.floatValue)
                    }
                }
                lastFrameNanos = frameNanos
            }

            val paused = posSec == lastPosSec
            lastPosSec = posSec
            if (!moved && (firstStart == null || lastEnd == null || paused || posSec < firstStart - 0.35 || posSec > lastEnd + 1.5)) {
                val waitMs = if (!paused && firstStart != null && posSec < firstStart - 0.35) {
                    ((firstStart - 0.30 - posSec) * 1000.0).toLong().coerceIn(32L, 180L)
                } else {
                    180L
                }
                lastFrameNanos = -1L
                delay(waitMs)
            }
        }
    }

    return head
}

@Suppress("NewApi")
@Composable
private fun rememberOndaBloomModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: OndaKaraokeHead,
    textLength: Int,
    amplitudeProvider: () -> Float,
    glowColor: Color,
    bleed: Dp,
    pixelScale: Float
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Modifier
    val shader = remember { getOrCreateOndaBloomShader() } ?: return Modifier
    val cache = remember(pixelScale, glowColor) { OndaWaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val amp = amplitudeProvider().coerceIn(0f, 1f)
        val glow = head.glow.floatValue
        val bloom = ONDA_BLOOM_STRENGTH * glow * amp
        if (layout == null || textLength == 0 || layout.lineCount == 0 || bloom <= ONDA_BLOOM_CUTOFF) {
            renderEffect = null
            return@graphicsLayer
        }

        val h = head.offset.floatValue
        val headOffset = h.toInt().coerceIn(0, textLength)
        val headLine = layout.ondaShownLineFor(headOffset.coerceAtMost(textLength - 1))
        val rtl = layout.ondaIsRtlLine(headLine)
        val headX = layout.ondaSweepHeadX(h, headOffset, headLine, textLength, rtl)
        val lineStart = layout.getLineStart(headLine)
        val lineEnd = layout.getLineEnd(headLine, visibleEnd = true)
        val charWidth = if (lineEnd > lineStart) {
            ((layout.getLineRight(headLine) - layout.getLineLeft(headLine)) / (lineEnd - lineStart))
                .coerceAtMost(ondaFontPx(layout) * ONDA_LATIN_LETTER_EM)
        } else {
            ondaFontPx(layout) * 0.5f
        }

        val o = bleed.roundToPx().toFloat()
        shader.setFloatUniform("layerSize", size.width, size.height)
        shader.setFloatUniform("headX", headX + o)
        shader.setFloatUniform("lineTop", layout.getLineTop(headLine) + o)
        shader.setFloatUniform("lineBottom", layout.getLineBottom(headLine) + o)
        shader.setFloatUniform("lineStart", layout.ondaLineStartX(headLine, rtl) + o)
        shader.setFloatUniform("flowDir", if (rtl) -1f else 1f)
        shader.setFloatUniform("bloom", bloom)
        shader.setFloatUniform("bloomR", ONDA_BLOOM_RADIUS_PX * pixelScale)
        shader.setFloatUniform("bloomLag", charWidth * ONDA_BLOOM_LAG_CHARS)
        shader.setFloatUniform("bloomSpan", charWidth * ONDA_BLOOM_SPAN_CHARS)
        shader.setFloatUniform("glowTint", glowColor.red, glowColor.green, glowColor.blue)
        if (headLine > 0) {
            shader.setFloatUniform("hasPrev", 1f)
            shader.setFloatUniform("prevTop", layout.getLineTop(headLine - 1) + o)
            shader.setFloatUniform("prevBottom", layout.getLineBottom(headLine - 1) + o)
            shader.setFloatUniform("prevEnd", layout.ondaLineEndX(headLine - 1, rtl) + o)
        } else {
            shader.setFloatUniform("hasPrev", 0f)
            shader.setFloatUniform("prevTop", 0f)
            shader.setFloatUniform("prevBottom", 0f)
            shader.setFloatUniform("prevEnd", 0f)
        }

        renderEffect = cache.effectFor(shader, headX, amp, glow, headLine, size.width, size.height)
    }
}

@Suppress("NewApi")
@Composable
private fun rememberOndaOpticalWaveModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: OndaKaraokeHead,
    textLength: Int,
    amplitudeProvider: () -> Float,
    glowColor: Color,
    bleed: Dp,
    pixelScale: Float
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Modifier
    val shader = remember { getOrCreateOndaWaveShader() } ?: return Modifier
    val cache = remember(pixelScale, glowColor) { OndaWaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val h = head.offset.floatValue
        val amp = amplitudeProvider().coerceIn(0f, 1f)
        if (layout == null || textLength == 0 || layout.lineCount == 0 || h <= 0f || amp <= ONDA_WAVE_CUTOFF) {
            renderEffect = null
            return@graphicsLayer
        }

        val headOffset = h.toInt().coerceIn(0, textLength)
        val headLine = layout.ondaShownLineFor(headOffset.coerceAtMost(textLength - 1))
        val rtl = layout.ondaIsRtlLine(headLine)
        val headX = layout.ondaSweepHeadX(h, headOffset, headLine, textLength, rtl)
        val lineStart = layout.getLineStart(headLine)
        val lineEnd = layout.getLineEnd(headLine, visibleEnd = true)
        val charWidth = if (lineEnd > lineStart) {
            ((layout.getLineRight(headLine) - layout.getLineLeft(headLine)) / (lineEnd - lineStart))
                .coerceAtMost(ondaFontPx(layout) * ONDA_LATIN_LETTER_EM)
        } else {
            ondaFontPx(layout) * 0.5f
        }

        val o = bleed.roundToPx().toFloat()
        shader.setFloatUniform("layerSize", size.width, size.height)
        shader.setFloatUniform("headX", headX + o)
        shader.setFloatUniform("spanBehind", charWidth * ONDA_WAVE_CHARS_BEHIND)
        shader.setFloatUniform("spanAhead", charWidth * ONDA_WAVE_CHARS_AHEAD)
        shader.setFloatUniform("liftPx", ONDA_WAVE_LIFT_PX * amp * pixelScale)
        shader.setFloatUniform("zoomAmt", ONDA_WAVE_ZOOM * amp)
        shader.setFloatUniform("baseY", layout.getLineBaseline(headLine) + o)
        shader.setFloatUniform("lineTop", layout.getLineTop(headLine) + o)
        shader.setFloatUniform("lineBottom", layout.getLineBottom(headLine) + o)
        shader.setFloatUniform("lineStart", layout.ondaLineStartX(headLine, rtl) + o)
        shader.setFloatUniform("flowDir", if (rtl) -1f else 1f)
        shader.setFloatUniform("bloom", ONDA_BLOOM_STRENGTH * head.glow.floatValue * amp)
        shader.setFloatUniform("bloomR", ONDA_BLOOM_RADIUS_PX * pixelScale)
        shader.setFloatUniform("bloomLag", charWidth * ONDA_BLOOM_LAG_CHARS)
        shader.setFloatUniform("bloomSpan", charWidth * ONDA_BLOOM_SPAN_CHARS)
        shader.setFloatUniform("glowTint", glowColor.red, glowColor.green, glowColor.blue)
        if (headLine > 0) {
            shader.setFloatUniform("hasPrev", 1f)
            shader.setFloatUniform("prevTop", layout.getLineTop(headLine - 1) + o)
            shader.setFloatUniform("prevBottom", layout.getLineBottom(headLine - 1) + o)
            shader.setFloatUniform("prevEnd", layout.ondaLineEndX(headLine - 1, rtl) + o)
            shader.setFloatUniform("prevBaseY", layout.getLineBaseline(headLine - 1) + o)
        } else {
            shader.setFloatUniform("hasPrev", 0f)
            shader.setFloatUniform("prevTop", 0f)
            shader.setFloatUniform("prevBottom", 0f)
            shader.setFloatUniform("prevEnd", 0f)
            shader.setFloatUniform("prevBaseY", 0f)
        }

        renderEffect = cache.effectFor(shader, headX, amp, head.glow.floatValue, headLine, size.width, size.height)
    }
}

@Composable
private fun rememberOndaFallbackWaveGlowModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: OndaKaraokeHead,
    textLength: Int,
    amplitudeProvider: () -> Float,
    glowColor: Color
): Modifier {
    return Modifier.drawWithContent {
        val layout = layoutHolder[0]
        val h = head.offset.floatValue
        val amp = amplitudeProvider().coerceIn(0f, 1f)
        if (layout != null && textLength > 0 && layout.lineCount > 0 && h > 0f && amp > ONDA_WAVE_CUTOFF) {
            val headOffset = h.toInt().coerceIn(0, textLength)
            val headLine = layout.ondaShownLineFor(headOffset.coerceAtMost(textLength - 1))
            val rtl = layout.ondaIsRtlLine(headLine)
            val headX = layout.ondaSweepHeadX(h, headOffset, headLine, textLength, rtl)
            val top = layout.getLineTop(headLine)
            val bottom = layout.getLineBottom(headLine)
            val lineH = (bottom - top).coerceAtLeast(16f)
            val haloAlpha = (0.12f + 0.14f * head.glow.floatValue) * amp
            val radius = lineH * 1.15f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = haloAlpha), Color.Transparent),
                    center = Offset(headX, (top + bottom) * 0.5f),
                    radius = radius
                ),
                radius = radius,
                center = Offset(headX, (top + bottom) * 0.5f)
            )
        }
        drawContent()
    }
}

@Composable
private fun rememberOndaWordMotionModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: OndaKaraokeHead,
    riseAmplitudeProvider: () -> Float,
    fakeGlowProvider: () -> Float,
    glowColor: Color,
    motionScale: Float,
    bleed: Dp
): Modifier {
    val cache = remember(head) { OndaMotionLayoutCache() }
    val textLayer = rememberGraphicsLayer()

    return Modifier.drawWithContent {
        val tick = head.motionTick.intValue
        if (tick < 0) return@drawWithContent
        val layout = layoutHolder[0]
        if (layout == null || head.cellCount == 0 || layout.lineCount == 0) {
            drawContent()
            return@drawWithContent
        }
        val m = cache.layoutFor(layout, head, bleed.toPx())
        val n = m.count
        if (n == 0) {
            drawContent()
            return@drawWithContent
        }

        val drop = ONDA_WORD_WAIT_DROP.toPx() * motionScale * riseAmplitudeProvider().coerceIn(0f, 1f)
        val lift = ONDA_HELD_LIFT.toPx() * motionScale
        val glow = fakeGlowProvider().coerceIn(0f, 1f)

        var uniform = true
        var i = 0
        while (i < n) {
            var j = i
            while (j + 1 < n && m.word[j + 1] == m.word[i] && m.line[j + 1] == m.line[i]) j++

            var swellSum = 0f
            for (k in i..j) swellSum += ondaSettledSwell(head.swell[m.cell[k]])
            val meanSwell = swellSum / (j - i + 1)
            val pivot = (m.coreLeft[i] + m.coreRight[j]) / 2f
            for (k in i..j) {
                val swell = ondaSettledSwell(head.swell[m.cell[k]])
                val dy = drop * (1f - head.rise[m.cell[k]]) - lift * swell
                m.dy[k] = if (abs(dy) < 0.05f) 0f else dy
                m.dx[k] = ((m.coreLeft[k] + m.coreRight[k]) / 2f - pivot) * ONDA_HELD_ZOOM * meanSwell
                m.scale[k] = 1f + ONDA_HELD_ZOOM * swell
                if (m.scale[k] != 1f || abs(m.dy[k] - m.dy[0]) > ONDA_MERGE_EPSILON_PX) {
                    uniform = false
                }
            }

            if (glow > 0f && meanSwell > 0.01f) {
                val width = (m.coreRight[j] - m.coreLeft[i]).coerceAtLeast(12f)
                val height = (m.clipBottom[i] - m.clipTop[i]).coerceAtLeast(12f)
                val centreY = (m.clipTop[i] + m.clipBottom[i]) / 2f + (m.dy[i] + m.dy[j]) / 2f
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = ONDA_FAKE_GLOW_ALPHA * meanSwell * glow), Color.Transparent),
                        center = Offset(pivot, centreY),
                        radius = maxOf(width, height) * 0.78f
                    ),
                    topLeft = Offset(pivot - width * 0.78f, centreY - height * 0.62f),
                    size = Size(width * 1.56f, height * 1.24f)
                )
            }
            i = j + 1
        }

        if (uniform) {
            val dy = m.dy[0]
            if (dy == 0f) {
                drawContent()
            } else {
                withTransform({ translate(0f, dy) }) { this@drawWithContent.drawContent() }
            }
            return@drawWithContent
        }

        textLayer.record {
            this@drawWithContent.drawContent()
        }

        var k = 0
        while (k < n) {
            if (m.tiled && m.scale[k] == 1f && m.dx[k] == 0f) {
                var e = k
                while (
                    e + 1 < n &&
                    m.line[e + 1] == m.line[k] &&
                    m.scale[e + 1] == 1f &&
                    m.dx[e + 1] == 0f &&
                    abs(m.dy[e + 1] - m.dy[k]) <= ONDA_MERGE_EPSILON_PX
                ) {
                    e++
                }
                withTransform({ translate(0f, m.dy[k]) }) {
                    clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[e], m.clipBottom[k]) {
                        drawLayer(textLayer)
                    }
                }
                k = e + 1
                continue
            }

            val s = m.scale[k]
            withTransform({
                translate(m.dx[k], m.dy[k])
                if (s != 1f) {
                    scale(s, s, Offset((m.coreLeft[k] + m.coreRight[k]) / 2f, m.baseline[k]))
                }
            }) {
                clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[k], m.clipBottom[k]) {
                    drawLayer(textLayer)
                }
            }
            k++
        }
    }
}

private class OndaWaveEffectCache {
    private var lastHead = Int.MIN_VALUE
    private var lastAmp = Int.MIN_VALUE
    private var lastGlow = Int.MIN_VALUE
    private var lastLine = Int.MIN_VALUE
    private var lastWidth = Int.MIN_VALUE
    private var lastHeight = Int.MIN_VALUE
    private var effect: RenderEffect? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun effectFor(
        shader: RuntimeShader,
        headX: Float,
        amplitude: Float,
        glow: Float,
        headLine: Int,
        width: Float,
        height: Float
    ): RenderEffect? {
        val h = (headX / ONDA_WAVE_HEAD_STEP_PX).roundToInt()
        val a = (amplitude / ONDA_WAVE_AMP_STEP).roundToInt()
        val g = (glow / ONDA_WAVE_AMP_STEP).roundToInt()
        val w = width.roundToInt()
        val ht = height.roundToInt()
        val cached = effect
        if (cached != null && h == lastHead && a == lastAmp && g == lastGlow && headLine == lastLine &&
            w == lastWidth && ht == lastHeight
        ) {
            return cached
        }
        lastWidth = w
        lastHeight = ht
        lastHead = h
        lastAmp = a
        lastGlow = g
        lastLine = headLine
        return createShaderRenderEffect(shader).also { effect = it }
    }
}

private class OndaMotionLayout(
    val count: Int,
    val tiled: Boolean,
    val cell: IntArray,
    val word: IntArray,
    val line: IntArray,
    val coreLeft: FloatArray,
    val coreRight: FloatArray,
    val clipLeft: FloatArray,
    val clipTop: FloatArray,
    val clipRight: FloatArray,
    val clipBottom: FloatArray,
    val baseline: FloatArray
) {
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val scale = FloatArray(count)
}

private class OndaMotionLayoutCache {
    private var forLayout: TextLayoutResult? = null
    private var cached: OndaMotionLayout? = null

    fun layoutFor(layout: TextLayoutResult, head: OndaKaraokeHead, edgePadPx: Float): OndaMotionLayout {
        val current = cached
        if (current != null && layout === forLayout) return current
        forLayout = layout
        return buildOndaMotionLayout(layout, head, edgePadPx).also { cached = it }
    }
}

private fun buildOndaMotionLayout(
    layout: TextLayoutResult,
    head: OndaKaraokeHead,
    edgePadPx: Float
): OndaMotionLayout {
    val text = layout.layoutInput.text.text
    val tiled = head.cells.tiled
    val capacity = head.cellCount + layout.lineCount + 2
    val cells = IntArray(capacity)
    val lines = IntArray(capacity)
    val segStarts = IntArray(capacity)
    val segEnds = IntArray(capacity)
    val lefts = FloatArray(capacity)
    val rights = FloatArray(capacity)
    var n = 0

    if (text.isNotEmpty() && layout.lineCount > 0) {
        val boxes = layout.multiParagraph.fillBoundingBoxes(
            TextRange(0, text.length),
            FloatArray(text.length * 4),
            0
        )
        for (c in 0 until head.cellCount) {
            val start = head.cells.starts[c].coerceIn(0, text.length)
            val end = head.cells.ends[c].coerceIn(start, text.length)
            if (end <= start) continue
            val firstLine = layout.ondaShownLineFor(start)
            val lastCellLine = layout.ondaShownLineFor(end - 1)
            for (line in firstLine..lastCellLine) {
                val segStart = maxOf(start, layout.getLineStart(line))
                val segEnd = minOf(end, layout.getLineEnd(line))
                if (segEnd <= segStart) continue
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                for (i in segStart until segEnd) {
                    if (text[i].isWhitespace()) continue
                    left = minOf(left, boxes[i * 4])
                    right = maxOf(right, boxes[i * 4 + 2])
                }
                if (left > right || n >= capacity) continue
                cells[n] = c
                lines[n] = line
                segStarts[n] = segStart
                segEnds[n] = segEnd
                lefts[n] = left
                rights[n] = right
                n++
            }
        }
    }

    val lastLine = (layout.lineCount - 1).coerceAtLeast(0)
    val clipLeft = FloatArray(n)
    val clipRight = FloatArray(n)
    val clipTop = FloatArray(n)
    val clipBottom = FloatArray(n)
    val baseline = FloatArray(n)

    for (k in 0 until n) {
        val line = lines[k]
        clipTop[k] = layout.getLineTop(line) - if (line == 0) edgePadPx else 0f
        clipBottom[k] = layout.getLineBottom(line) + if (line == lastLine) edgePadPx else 0f
        baseline[k] = layout.getLineBaseline(line)
    }

    if (tiled) {
        var a = 0
        while (a < n) {
            var b = a
            while (b + 1 < n && lines[b + 1] == lines[a]) b++
            val line = lines[a]
            clipLeft[a] = minOf(lefts[a], layout.getLineLeft(line)) - edgePadPx
            clipRight[b] = maxOf(rights[b], layout.getLineRight(line)) + edgePadPx
            for (k in a until b) {
                val meet = (rights[k] + lefts[k + 1]) / 2f
                clipRight[k] = meet
                clipLeft[k + 1] = meet
            }
            a = b + 1
        }
    } else {
        for (k in 0 until n) {
            val segStart = segStarts[k]
            val segEnd = segEnds[k]
            val padLeft = if (segStart == 0 || text[segStart - 1].isWhitespace()) ONDA_WORD_BOX_PAD_PX else 0f
            val padRight = if (segEnd >= text.length || text[segEnd].isWhitespace()) ONDA_WORD_BOX_PAD_PX else 0f
            clipLeft[k] = lefts[k] - padLeft
            clipRight[k] = rights[k] + padRight
        }
    }

    return OndaMotionLayout(
        count = n,
        tiled = tiled,
        cell = cells.copyOf(n),
        word = IntArray(n) { head.cells.word[cells[it]] },
        line = lines.copyOf(n),
        coreLeft = lefts.copyOf(n),
        coreRight = rights.copyOf(n),
        clipLeft = clipLeft,
        clipTop = clipTop,
        clipRight = clipRight,
        clipBottom = clipBottom,
        baseline = baseline
    )
}

private fun Density.ondaFontPx(layout: TextLayoutResult): Float =
    layout.layoutInput.style.fontSize.takeIf { it.isSp }?.toPx() ?: 22.sp.toPx()

private fun TextLayoutResult.ondaShownLineFor(offset: Int): Int =
    getLineForOffset(offset.coerceIn(0, (layoutInput.text.length - 1).coerceAtLeast(0)))
        .coerceIn(0, (lineCount - 1).coerceAtLeast(0))

private fun TextLayoutResult.ondaIsRtlLine(line: Int): Boolean =
    getParagraphDirection(getLineStart(line)) == ResolvedTextDirection.Rtl

private fun TextLayoutResult.ondaSweepHeadX(
    h: Float,
    headOffset: Int,
    headLine: Int,
    len: Int,
    rtl: Boolean
): Float {
    if (headOffset >= len) return if (rtl) getLineLeft(headLine) else getLineRight(headLine)
    val x0 = getHorizontalPosition(headOffset, true)
    val x1 = getHorizontalPosition((headOffset + 1).coerceAtMost(len), true)
    val forward = if (rtl) x1 <= x0 else x1 >= x0
    return if (forward) x0 + (x1 - x0) * (h - headOffset) else x0
}

private fun TextLayoutResult.ondaLineStartX(line: Int, rtl: Boolean): Float =
    if (rtl) getLineRight(line) else getLineLeft(line)

private fun TextLayoutResult.ondaLineEndX(line: Int, rtl: Boolean): Float =
    if (rtl) getLineLeft(line) else getLineRight(line)

private fun ondaSmoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun ondaSettledSwell(swell: Float): Float = if (swell < 0.002f) 0f else swell

private fun ondaDampedStep(
    values: FloatArray,
    velocities: FloatArray,
    index: Int,
    target: Float,
    followSec: Float,
    dt: Float
): Boolean {
    val omega = 1f / followSec
    val y = values[index] - target
    val v = velocities[index]
    if (abs(y) < 0.0005f && abs(v) < 0.005f) {
        if (values[index] == target && v == 0f) return false
        values[index] = target
        velocities[index] = 0f
        return true
    }
    val e = exp(-omega * dt)
    val tmp = (v + omega * y) * dt
    values[index] = target + (y + tmp) * e
    velocities[index] = (v - omega * tmp) * e
    return true
}

private fun ondaSplitsIntoLetters(text: String, start: Int, end: Int): Boolean {
    var i = start
    while (i < end) {
        val cp = text.codePointAt(i)
        when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.ARMENIAN,
            Character.UnicodeScript.GEORGIAN,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED -> Unit
            else -> return false
        }
        i += Character.charCount(cp)
    }
    return true
}

private fun isOndaSingleGlyphWord(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA -> true
    else -> false
}

private fun isOndaSyllableGlyph(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL -> true
    else -> false
}
