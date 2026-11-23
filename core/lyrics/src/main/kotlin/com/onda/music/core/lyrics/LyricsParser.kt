package com.onda.music.core.lyrics

import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.LyricsWord
import javax.inject.Inject
import javax.inject.Singleton

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

        val sorted = parsedEntries.sortedBy { it.startTimeMs }
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
            val naturalEnd = nextStart ?: maxOf(
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
            1 -> (fraction.toLongOrNull() ?: 0L) * 100L
            2 -> (fraction.toLongOrNull() ?: 0L) * 10L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return min * 60_000L + sec * 1000L + fracMs
    }

    private fun isInstrumentalMarker(text: String): Boolean {
        val t = text.trim()
        return t.isEmpty() || t == "♪" || t == "♫" || t == "..." || t.equals("[Instrumental]", ignoreCase = true)
    }

    private data class ParsedTimedEntry(
        val startTimeMs: Long,
        val text: String,
        val words: List<LyricsWord>,
        val explicitInterlude: Boolean
    )

    companion object {
        const val DEFAULT_INTERLUDE_GAP_MS = 5_000L
        private const val ESTIMATED_LINE_DURATION_MS = 4_500L
        private const val DEFAULT_TAIL_DURATION_MS = 5_000L
        private const val DEFAULT_WORD_TAIL_MS = 450L

        private val LINE_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})[.:](\d{1,3})]""")
        private val WORD_TIMING_REGEX = Regex("""<(\d{1,3}):(\d{2})[.:](\d{1,3})>\s*([^<\r\n]+)""")
        private val WORD_TAG_REGEX = Regex("""<\d{1,3}:\d{2}[.:]\d{1,3}>""")
        private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")
        private val OFFSET_TAG_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
        private val MULTI_SPACE_REGEX = Regex("""\s+""")
    }
}
