package com.onda.music.core.transfer

import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.LyricsWord
import kotlin.math.abs

object MomiLyricsConverter {

    private val lineTimestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val agentPrefixRegex = Regex("""^\{[^}]+\}""")
    private val inlineWordTagRegex = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)""")
    private val blockWordLineRegex = Regex("""^<(.+)>$""")

    fun convert(row: MomiRawLyricsRow): LyricsDocument {
        val translationsByMs = parseTranslationTimestamps(row.translatedLyrics)
        val rawLines = row.rawLyrics.lines()
        val parsed = mutableListOf<LyricsLine>()
        var hasWordTiming = false

        var index = 0
        while (index < rawLines.size) {
            val line = rawLines[index].trim()
            if (line.isEmpty()) {
                index++
                continue
            }

            val timestampMatches = lineTimestampRegex.findAll(line).toList()
            if (timestampMatches.isEmpty()) {
                index++
                continue
            }

            val lastMatch = timestampMatches.last()
            var content = line.substring(lastMatch.range.last + 1).trim()
            content = agentPrefixRegex.replace(content, "").trim()

            var words = emptyList<LyricsWord>()
            val inlineMatches = inlineWordTagRegex.findAll(content).toList()
            if (inlineMatches.isNotEmpty()) {
                val builtWords = mutableListOf<LyricsWord>()
                for (i in inlineMatches.indices) {
                    val m = inlineMatches[i]
                    val startMs = parseTimestampMs(
                        m.groupValues[1],
                        m.groupValues[2],
                        m.groupValues[3]
                    )
                    val endMs = if (i + 1 < inlineMatches.size) {
                        val next = inlineMatches[i + 1]
                        parseTimestampMs(
                            next.groupValues[1],
                            next.groupValues[2],
                            next.groupValues[3]
                        )
                    } else {
                        startMs + 400L
                    }
                    val token = m.groupValues[4].trim()
                    if (token.isNotEmpty()) {
                        builtWords.add(
                            LyricsWord(
                                text = token,
                                startTimeMs = startMs,
                                endTimeMs = maxOf(endMs, startMs + 50L)
                            )
                        )
                    }
                }
                if (builtWords.isNotEmpty()) {
                    words = builtWords
                    content = builtWords.joinToString(" ") { it.text }
                    hasWordTiming = true
                }
            } else if (index + 1 < rawLines.size) {
                val nextLine = rawLines[index + 1].trim()
                val blockMatch = blockWordLineRegex.matchEntire(nextLine)
                if (blockMatch != null && !lineTimestampRegex.containsMatchIn(nextLine)) {
                    val blockWords = parseBetterLyricsWords(blockMatch.groupValues[1])
                    if (blockWords.isNotEmpty()) {
                        words = blockWords
                        hasWordTiming = true
                    }
                    index++
                }
            }

            for (match in timestampMatches) {
                val startMs = parseTimestampMs(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues[3]
                )
                val translated = findNearestTranslation(translationsByMs, startMs)
                parsed.add(
                    LyricsLine(
                        startTimeMs = startMs,
                        endTimeMs = startMs + 4000L,
                        text = content,
                        words = words,
                        translatedText = translated
                    )
                )
            }
            index++
        }

        val ordered = parsed.sortedBy { it.startTimeMs }
        val sorted = ordered.mapIndexed { idx, current ->
            val nextStart = ordered.getOrNull(idx + 1)?.startTimeMs
            val computedEnd = when {
                current.words.isNotEmpty() -> maxOf(
                    current.words.last().endTimeMs,
                    current.startTimeMs + 200L
                )
                nextStart != null && nextStart > current.startTimeMs -> nextStart
                else -> current.startTimeMs + 4000L
            }
            current.copy(endTimeMs = computedEnd)
        }

        val finalLines = if (sorted.isNotEmpty()) {
            sorted
        } else {
            row.rawLyrics.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { plain ->
                    LyricsLine(
                        startTimeMs = 0L,
                        endTimeMs = 0L,
                        text = plain
                    )
                }
        }

        return LyricsDocument(
            trackId = row.trackId,
            lines = finalLines,
            isSynced = sorted.isNotEmpty(),
            hasWordTiming = hasWordTiming,
            source = mapLyricsSource(row.provider)
        )
    }

    private fun parseBetterLyricsWords(body: String): List<LyricsWord> {
        val result = mutableListOf<LyricsWord>()
        val parts = body.split('|')
        for (part in parts) {
            val segments = part.split(':')
            if (segments.size < 3) continue
            val endSec = segments.last().toDoubleOrNull() ?: continue
            val startSec = segments[segments.size - 2].toDoubleOrNull() ?: continue
            val text = segments.subList(0, segments.size - 2).joinToString(":").trim()
            if (text.isNotEmpty()) {
                val startMs = (startSec * 1000.0).toLong().coerceAtLeast(0L)
                val endMs = (endSec * 1000.0).toLong().coerceAtLeast(startMs + 40L)
                result.add(
                    LyricsWord(
                        text = text,
                        startTimeMs = startMs,
                        endTimeMs = endMs
                    )
                )
            }
        }
        return result
    }

    private fun parseTranslationTimestamps(translated: String): List<Pair<Long, String>> {
        if (translated.isBlank()) return emptyList()
        val list = mutableListOf<Pair<Long, String>>()
        for (line in translated.lines()) {
            val trimmed = line.trim()
            val match = lineTimestampRegex.find(trimmed) ?: continue
            val ms = parseTimestampMs(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3]
            )
            val text = trimmed.substring(match.range.last + 1).trim()
            if (text.isNotEmpty()) {
                list.add(ms to text)
            }
        }
        return list
    }

    private fun findNearestTranslation(
        translations: List<Pair<Long, String>>,
        targetMs: Long
    ): String? {
        if (translations.isEmpty()) return null
        val best = translations.minByOrNull { abs(it.first - targetMs) } ?: return null
        return if (abs(best.first - targetMs) <= 450L) best.second else null
    }

    private fun parseTimestampMs(minStr: String, secStr: String, fracStr: String): Long {
        val minutes = minStr.toLongOrNull() ?: 0L
        val seconds = secStr.toLongOrNull() ?: 0L
        val millis = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1000L + millis
    }

    private fun mapLyricsSource(provider: String): LyricsSource {
        return when {
            provider.contains("lrclib", ignoreCase = true) -> LyricsSource.LRCLIB
            provider.contains("youtube", ignoreCase = true) -> LyricsSource.YOUTUBE_MUSIC
            else -> LyricsSource.CACHE
        }
    }
}
