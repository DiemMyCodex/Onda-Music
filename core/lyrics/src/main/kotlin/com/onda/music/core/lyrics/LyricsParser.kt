package com.onda.music.core.lyrics

import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.LyricsWord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LyricsParser @Inject constructor() {

    fun parse(
        trackId: String,
        rawContent: String,
        source: LyricsSource = LyricsSource.LRCLIB,
        trackDurationMs: Long = 0L,
        interludeGapThresholdMs: Long = DEFAULT_INTERLUDE_GAP_MS
    ): LyricsDocument {
        val normalized = rawContent.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) {
            return LyricsDocument(trackId = trackId, lines = emptyList(), isSynced = false, hasWordTiming = false, source = source)
        }
        if (normalized.contains("<tt", ignoreCase = true) && normalized.contains("<p", ignoreCase = true)) {
            val ttmlDoc = parseTtml(
                trackId = trackId,
                ttmlXml = normalized,
                source = source,
                trackDurationMs = trackDurationMs,
                interludeGapThresholdMs = interludeGapThresholdMs
            )
            if (ttmlDoc.lines.isNotEmpty()) return ttmlDoc
        }

        val rawLines = normalized.lines()
        val offsetMs = extractOffsetMs(rawLines)
        val parsedEntries = mutableListOf<ParsedTimedEntry>()

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (METADATA_TAG_REGEX.matches(trimmed) && !LINE_TIMESTAMP_REGEX.containsMatchIn(trimmed)) continue

            val timestamps = LINE_TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (timestamps.isEmpty()) continue

            val lastMatchEnd = timestamps.last().range.last + 1
            val payload = trimmed.substring(lastMatchEnd).trimStart()

            for (ts in timestamps) {
                val baseTimeMs = parseTimestampMs(
                    minutes = ts.groupValues[1],
                    seconds = ts.groupValues[2],
                    fraction = ts.groupValues[3]
                )
                val adjustedStart = (baseTimeMs - offsetMs).coerceAtLeast(0L)
                val words = parseWords(payload, adjustedStart, offsetMs)
                val cleanText = WORD_TAG_REGEX.replace(payload, "").replace(MULTI_SPACE_REGEX, " ").trim()
                val explicitInterlude = isInstrumentalMarker(cleanText)
                parsedEntries += ParsedTimedEntry(
                    startTimeMs = adjustedStart,
                    text = if (explicitInterlude) "" else cleanText,
                    words = if (explicitInterlude) emptyList() else words,
                    explicitInterlude = explicitInterlude
                )
            }
        }

        if (parsedEntries.isEmpty()) {
            val plainLines = rawLines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !METADATA_TAG_REGEX.matches(it) }
                .map { line ->
                    LyricsLine(
                        startTimeMs = 0L,
                        endTimeMs = 0L,
                        text = line,
                        isInterlude = isInstrumentalMarker(line)
                    )
                }
            return LyricsDocument(
                trackId = trackId,
                lines = plainLines,
                isSynced = false,
                hasWordTiming = false,
                source = source
            )
        }

        val collapsed = collapseBilingualSameTimestamp(parsedEntries.sortedBy { it.startTimeMs })
        val finalized = buildTimelineWithInterludes(collapsed, trackDurationMs, interludeGapThresholdMs)
        val hasWordTiming = finalized.any { it.words.isNotEmpty() }

        return LyricsDocument(
            trackId = trackId,
            lines = finalized,
            isSynced = true,
            hasWordTiming = hasWordTiming,
            source = source
        )
    }

    fun parseTtml(
        trackId: String,
        ttmlXml: String,
        source: LyricsSource = LyricsSource.BETTER_LYRICS,
        trackDurationMs: Long = 0L,
        interludeGapThresholdMs: Long = DEFAULT_INTERLUDE_GAP_MS
    ): LyricsDocument {
        val entries = mutableListOf<ParsedTimedEntry>()
        for (pMatch in TTML_P_REGEX.findAll(ttmlXml)) {
            val attrs = pMatch.groupValues[1]
            val body = pMatch.groupValues[2]
            val lineStart = parseTtmlAttrTimeMs(attrs, "begin") ?: continue
            val lineEnd = parseTtmlAttrTimeMs(attrs, "end")

            var translation: String? = null
            val words = mutableListOf<LyricsWord>()
            val lineTextBuilder = StringBuilder()

            val spanMatches = TTML_SPAN_REGEX.findAll(body).toList()
            if (spanMatches.isNotEmpty()) {
                var cursor = 0
                for (span in spanMatches) {
                    val gapBefore = body.substring(cursor, span.range.first)
                    val spanAttrs = span.groupValues[1]
                    val rawInner = decodeXmlEntities(XML_TAG_STRIP_REGEX.replace(span.groupValues[2], ""))
                    cursor = span.range.last + 1

                    if (spanAttrs.contains("x-translation", ignoreCase = true)) {
                        val cleanTrans = rawInner.trim()
                        if (cleanTrans.isNotEmpty()) {
                            translation = cleanTrans
                        }
                        continue
                    }
                    if (spanAttrs.contains("x-bg", ignoreCase = true) && rawInner.isBlank()) {
                        continue
                    }

                    val spanBegin = parseTtmlAttrTimeMs(spanAttrs, "begin")
                    val spanEnd = parseTtmlAttrTimeMs(spanAttrs, "end")
                    val needsLeadingSpace = gapBefore.contains(' ') || gapBefore.contains('\n') || gapBefore.contains('\t')
                    if (needsLeadingSpace && lineTextBuilder.isNotEmpty() && !lineTextBuilder.last().isWhitespace()) {
                        lineTextBuilder.append(' ')
                    }
                    lineTextBuilder.append(rawInner)

                    val trimmedWord = rawInner.trim()
                    if (spanBegin != null && trimmedWord.isNotEmpty()) {
                        val resolvedEnd = spanEnd?.coerceAtLeast(spanBegin + 40L) ?: (spanBegin + DEFAULT_WORD_TAIL_MS)
                        words += LyricsWord(
                            text = trimmedWord,
                            startTimeMs = spanBegin,
                            endTimeMs = resolvedEnd
                        )
                    }
                }
            } else {
                val stripped = decodeXmlEntities(XML_TAG_STRIP_REGEX.replace(body, ""))
                lineTextBuilder.append(stripped)
            }

            val cleanText = lineTextBuilder.toString().replace(MULTI_SPACE_REGEX, " ").trim()
            val explicitInterlude = isInstrumentalMarker(cleanText)
            entries += ParsedTimedEntry(
                startTimeMs = lineStart,
                explicitEndMs = lineEnd,
                text = if (explicitInterlude) "" else cleanText,
                words = if (explicitInterlude) emptyList() else words,
                translatedText = translation,
                explicitInterlude = explicitInterlude
            )
        }

        if (entries.isEmpty()) {
            return LyricsDocument(trackId = trackId, lines = emptyList(), isSynced = false, hasWordTiming = false, source = source)
        }

        val sorted = entries.sortedBy { it.startTimeMs }
        val finalized = buildTimelineWithInterludes(sorted, trackDurationMs, interludeGapThresholdMs)
        val hasWordTiming = finalized.any { it.words.isNotEmpty() }
        return LyricsDocument(
            trackId = trackId,
            lines = finalized,
            isSynced = true,
            hasWordTiming = hasWordTiming,
            source = source
        )
    }

    fun mergeTranslation(
        primary: LyricsDocument,
        translatedLrc: String,
        toleranceMs: Long = 1_500L
    ): LyricsDocument {
        if (primary.lines.isEmpty() || translatedLrc.isBlank()) return primary
        val transDoc = parse(
            trackId = primary.trackId,
            rawContent = translatedLrc,
            source = primary.source,
            interludeGapThresholdMs = Long.MAX_VALUE
        )
        val transLines = transDoc.lines.filter { !it.isInterlude && it.text.isNotBlank() }
        if (transLines.isEmpty()) return primary

        val updated = primary.lines.map { line ->
            if (line.isInterlude || line.text.isBlank() || !line.translatedText.isNullOrBlank()) {
                line
            } else {
                val best = transLines.minByOrNull { abs(it.startTimeMs - line.startTimeMs) }
                if (best != null && abs(best.startTimeMs - line.startTimeMs) <= toleranceMs &&
                    !best.text.equals(line.text, ignoreCase = true)
                ) {
                    line.copy(translatedText = best.text)
                } else {
                    line
                }
            }
        }
        return primary.copy(lines = updated)
    }

    private fun collapseBilingualSameTimestamp(sorted: List<ParsedTimedEntry>): List<ParsedTimedEntry> {
        if (sorted.size < 2) return sorted
        val result = ArrayList<ParsedTimedEntry>(sorted.size)
        var i = 0
        while (i < sorted.size) {
            val current = sorted[i]
            val next = sorted.getOrNull(i + 1)
            if (next != null &&
                !current.explicitInterlude &&
                !next.explicitInterlude &&
                current.startTimeMs == next.startTimeMs &&
                current.text.isNotBlank() &&
                next.text.isNotBlank() &&
                !current.text.equals(next.text, ignoreCase = true) &&
                current.translatedText == null
            ) {
                result += current.copy(translatedText = next.text)
                i += 2
            } else {
                result += current
                i += 1
            }
        }
        return result
    }

    private fun buildTimelineWithInterludes(
        entries: List<ParsedTimedEntry>,
        trackDurationMs: Long,
        interludeGapThresholdMs: Long
    ): List<LyricsLine> {
        val output = mutableListOf<LyricsLine>()
        val first = entries.first()
        if (!first.explicitInterlude && first.startTimeMs >= interludeGapThresholdMs) {
            output += LyricsLine(
                startTimeMs = 0L,
                endTimeMs = first.startTimeMs,
                text = "",
                isInterlude = true
            )
        }

        for (i in entries.indices) {
            val current = entries[i]
            val nextStart = entries.getOrNull(i + 1)?.startTimeMs
            val naturalEnd = current.explicitEndMs?.takeIf { it > current.startTimeMs }
                ?: nextStart
                ?: maxOf(
                    current.startTimeMs + DEFAULT_TAIL_DURATION_MS,
                    if (trackDurationMs > current.startTimeMs) trackDurationMs else 0L
                )

            if (current.explicitInterlude) {
                output += LyricsLine(
                    startTimeMs = current.startTimeMs,
                    endTimeMs = naturalEnd,
                    text = "",
                    isInterlude = true
                )
                continue
            }

            val wordLastEnd = current.words.lastOrNull()?.endTimeMs
            val sungEnd = when {
                current.explicitEndMs != null && current.explicitEndMs > current.startTimeMs -> current.explicitEndMs
                nextStart == null -> naturalEnd
                wordLastEnd != null && nextStart - wordLastEnd >= interludeGapThresholdMs -> wordLastEnd
                wordLastEnd == null && nextStart - current.startTimeMs >= interludeGapThresholdMs * 2L -> {
                    minOf(nextStart - 500L, current.startTimeMs + ESTIMATED_LINE_DURATION_MS)
                }
                else -> naturalEnd
            }.coerceAtLeast(current.startTimeMs)

            val resolvedWords = resolveWordEndTimes(current.words, sungEnd)
            output += LyricsLine(
                startTimeMs = current.startTimeMs,
                endTimeMs = sungEnd,
                text = current.text,
                words = resolvedWords,
                translatedText = current.translatedText,
                isInterlude = false
            )

            if (nextStart != null && nextStart - sungEnd >= interludeGapThresholdMs) {
                output += LyricsLine(
                    startTimeMs = sungEnd,
                    endTimeMs = nextStart,
                    text = "",
                    isInterlude = true
                )
            }
        }
        return output
    }

    private fun parseWords(
        payload: String,
        lineStartMs: Long,
        offsetMs: Long
    ): List<LyricsWord> {
        val matches = WORD_TIMING_REGEX.findAll(payload).toList()
        if (matches.isEmpty()) return emptyList()

        val rawWords = mutableListOf<Pair<Long, String>>()
        val prefixText = payload.substring(0, matches.first().range.first).trim()
        if (prefixText.isNotEmpty()) {
            rawWords += lineStartMs to prefixText
        }
        for (m in matches) {
            val wordStart = (parseTimestampMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]) - offsetMs)
                .coerceAtLeast(lineStartMs)
            val wordText = m.groupValues[4].trim()
            if (wordText.isNotEmpty()) {
                rawWords += wordStart to wordText
            }
        }

        return rawWords.mapIndexed { index, (startMs, text) ->
            val nextWordStart = rawWords.getOrNull(index + 1)?.first
            val endMs = nextWordStart ?: (startMs + DEFAULT_WORD_TAIL_MS)
            LyricsWord(
                text = text,
                startTimeMs = startMs,
                endTimeMs = endMs.coerceAtLeast(startMs)
            )
        }
    }

    private fun resolveWordEndTimes(words: List<LyricsWord>, lineEndMs: Long): List<LyricsWord> {
        if (words.isEmpty()) return emptyList()
        return words.mapIndexed { index, word ->
            val clampedEnd = if (index == words.lastIndex) {
                minOf(word.endTimeMs, lineEndMs).coerceAtLeast(word.startTimeMs)
            } else {
                val nextStart = words[index + 1].startTimeMs
                minOf(word.endTimeMs, nextStart).coerceAtLeast(word.startTimeMs)
            }
            word.copy(endTimeMs = clampedEnd)
        }
    }

    private fun extractOffsetMs(lines: List<String>): Long {
        for (line in lines) {
            val match = OFFSET_TAG_REGEX.find(line.trim()) ?: continue
            return match.groupValues[1].toLongOrNull() ?: 0L
        }
        return 0L
    }

    private fun parseTimestampMs(minutes: String, seconds: String, fraction: String): Long {
        val min = minutes.toLongOrNull() ?: 0L
        val sec = seconds.toLongOrNull() ?: 0L
        val fracMs = when (fraction.length) {
            0 -> 0L
            1 -> (fraction.toLongOrNull() ?: 0L) * 100L
            2 -> (fraction.toLongOrNull() ?: 0L) * 10L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return min * 60_000L + sec * 1000L + fracMs
    }

    private fun parseTtmlAttrTimeMs(attrs: String, attrName: String): Long? {
        val match = Regex("""\b$attrName\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)
            ?: return null
        return parseTtmlTimeValueMs(match.groupValues[1].trim())
    }

    private fun parseTtmlTimeValueMs(raw: String): Long? {
        if (raw.isEmpty()) return null
        if (raw.endsWith("ms", ignoreCase = true)) {
            return raw.dropLast(2).trim().toDoubleOrNull()?.toLong()?.coerceAtLeast(0L)
        }
        if (raw.endsWith("s", ignoreCase = true)) {
            return raw.dropLast(1).trim().toDoubleOrNull()?.let { (it * 1000.0).toLong().coerceAtLeast(0L) }
        }
        val parts = raw.split(':')
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toDoubleOrNull() ?: return null
                ((h * 3600L + m * 60L) * 1000L + (s * 1000.0).toLong()).coerceAtLeast(0L)
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toDoubleOrNull() ?: return null
                (m * 60_000L + (s * 1000.0).toLong()).coerceAtLeast(0L)
            }
            1 -> parts[0].toDoubleOrNull()?.let { (it * 1000.0).toLong().coerceAtLeast(0L) }
            else -> null
        }
    }

    private fun decodeXmlEntities(raw: String): String {
        var out = raw
            .replace("&amp;", "&")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        out = XML_NUMERIC_ENTITY_REGEX.replace(out) { m ->
            val num = m.groupValues[1]
            val codePoint = if (num.startsWith("x", ignoreCase = true)) {
                num.drop(1).toIntOrNull(16)
            } else {
                num.toIntOrNull(10)
            }
            codePoint?.let { String(Character.toChars(it)) } ?: m.value
        }
        return out
    }

    private fun isInstrumentalMarker(text: String): Boolean {
        val t = text.trim()
        return t.isEmpty() || t == "♪" || t == "♫" || t == "..." || t.equals("[Instrumental]", ignoreCase = true)
    }

    private data class ParsedTimedEntry(
        val startTimeMs: Long,
        val explicitEndMs: Long? = null,
        val text: String,
        val words: List<LyricsWord>,
        val translatedText: String? = null,
        val explicitInterlude: Boolean
    )

    companion object {
        const val DEFAULT_INTERLUDE_GAP_MS = 5_000L
        private const val ESTIMATED_LINE_DURATION_MS = 4_500L
        private const val DEFAULT_TAIL_DURATION_MS = 5_000L
        private const val DEFAULT_WORD_TAIL_MS = 450L

        private val LINE_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val WORD_TIMING_REGEX = Regex("""<(\d{1,3}):(\d{2})[.:](\d{1,3})>\s*([^<\r\n]+)""")
        private val WORD_TAG_REGEX = Regex("""<\d{1,3}:\d{2}[.:]\d{1,3}>""")
        private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")
        private val OFFSET_TAG_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
        private val MULTI_SPACE_REGEX = Regex("""\s+""")
        private val TTML_P_REGEX = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
        private val TTML_SPAN_REGEX = Regex("""<span\b([^>]*)>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE)
        private val XML_TAG_STRIP_REGEX = Regex("""<[^>]+>""")
        private val XML_NUMERIC_ENTITY_REGEX = Regex("""&#(x[0-9a-fA-F]+|\d+);""")
    }
}
