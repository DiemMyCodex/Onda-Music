package com.onda.music.core.sources

import com.onda.music.core.model.ImportedTrackCandidate
import com.onda.music.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ScoredTrackMatch(
    val track: Track,
    val score: Float
)

@Singleton
class TrackMatcher @Inject constructor() {

    fun coreTitle(title: String): String {
        var cleaned = title.lowercase()
        cleaned = cleaned.replace(FEAT_PARENS_REGEX, " ")
        cleaned = cleaned.replace(DASH_DECORATION_REGEX, " ")
        cleaned = cleaned.replace(BRACKET_DECORATION_REGEX, " ")
        cleaned = cleaned.replace(TRAILING_FEAT_REGEX, " ")
        return tokenize(cleaned).joinToString(" ")
    }

    fun score(candidate: ImportedTrackCandidate, target: Track): Float =
        scoreRaw(
            queryTitle = candidate.title,
            queryArtist = candidate.artist,
            queryDurationMs = candidate.durationMs.takeIf { it > 0L },
            targetTitle = target.title,
            targetArtist = target.artist,
            targetDurationMs = target.durationMs.takeIf { it > 0L }
        )

    fun score(query: Track, target: Track): Float =
        scoreRaw(
            queryTitle = query.title,
            queryArtist = query.artist,
            queryDurationMs = query.durationMs.takeIf { it > 0L },
            targetTitle = target.title,
            targetArtist = target.artist,
            targetDurationMs = target.durationMs.takeIf { it > 0L }
        )

    fun rank(
        candidate: ImportedTrackCandidate,
        targets: List<Track>,
        minScore: Float = DEFAULT_MIN_SCORE
    ): List<ScoredTrackMatch> = targets
        .map { target -> ScoredTrackMatch(target, score(candidate, target)) }
        .filter { it.score >= minScore }
        .sortedByDescending { it.score }

    fun rank(
        query: Track,
        targets: List<Track>,
        minScore: Float = DEFAULT_MIN_SCORE
    ): List<ScoredTrackMatch> = targets
        .map { target -> ScoredTrackMatch(target, score(query, target)) }
        .filter { it.score >= minScore }
        .sortedByDescending { it.score }

    fun bestMatch(
        candidate: ImportedTrackCandidate,
        targets: List<Track>,
        minScore: Float = DEFAULT_MIN_SCORE
    ): ScoredTrackMatch? = rank(candidate, targets, minScore).firstOrNull()

    fun bestMatch(
        query: Track,
        targets: List<Track>,
        minScore: Float = DEFAULT_MIN_SCORE
    ): ScoredTrackMatch? = rank(query, targets, minScore).firstOrNull()

    private fun scoreRaw(
        queryTitle: String,
        queryArtist: String,
        queryDurationMs: Long?,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long?
    ): Float {
        val qCore = coreTitle(queryTitle)
        val tCore = coreTitle(targetTitle)
        val titleScore = tokenJaccard(qCore, tCore)
        if (titleScore < 0.55f) return 0f

        val artistScore = artistSimilarity(queryArtist, targetArtist)
        if (artistScore < 0.5f) return 0f

        val durationScore = if (queryDurationMs != null && targetDurationMs != null) {
            val diffSec = abs(queryDurationMs - targetDurationMs) / 1000f
            when {
                diffSec <= 2f -> 1f
                diffSec >= 15f -> 0f
                else -> 1f - (diffSec - 2f) / 13f
            }
        } else {
            0.6f
        }

        val qLower = queryTitle.lowercase()
        val tLower = targetTitle.lowercase()
        val versionMismatch = VERSION_PATTERNS.count { pattern ->
            pattern.containsMatchIn(qLower) != pattern.containsMatchIn(tLower)
        }
        val penalty = 0.25f * versionMismatch

        return (0.5f * titleScore + 0.3f * artistScore + 0.2f * durationScore - penalty).coerceIn(0f, 1f)
    }

    private fun artistSimilarity(queryArtist: String, targetArtist: String): Float {
        val targetTokens = tokenize(targetArtist).toSet()
        if (targetTokens.isEmpty()) return 0f
        val parts = queryArtist.split(ARTIST_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return 0f
        return parts.maxOf { part ->
            val partTokens = tokenize(part)
            if (partTokens.isEmpty()) 0f else partTokens.count { it in targetTokens }.toFloat() / partTokens.size
        }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(NON_WORD_REGEX)
            .filter { it.isNotEmpty() && it != "the" && it != "and" }

    private fun tokenJaccard(a: String, b: String): Float {
        if (a == b && a.isNotEmpty()) return 1f
        val wa = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val wb = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        return (wa intersect wb).size.toFloat() / (wa union wb).size
    }

    companion object {
        const val DEFAULT_MIN_SCORE = 0.7f

        private val VERSION_WORDS = listOf(
            "remix", "live", "acoustic", "instrumental", "karaoke", "sped up", "slowed",
            "nightcore", "demo", "cover", "edit", "mix", "version", "unplugged", "orchestral", "piano"
        )
        private val VERSION_PATTERNS = VERSION_WORDS.map { Regex("""\b${Regex.escape(it)}\b""") }
        private val FEAT_PARENS_REGEX = Regex("""[(\[](?:feat|ft|with|prod)\.?\s[^)\]]*[)\]]""")
        private val DASH_DECORATION_REGEX = Regex("""\s[-–]\s.*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv)\b.*$""")
        private val BRACKET_DECORATION_REGEX = Regex("""[(\[][^)\]]*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv|explicit|clean)\b[^)\]]*[)\]]""")
        private val TRAILING_FEAT_REGEX = Regex("""\b(?:feat|ft)\.?\s.*$""")
        private val ARTIST_SPLIT_REGEX = Regex("""[,;&]|(?i:\bfeat\.?\b|\bft\.?\b|\band\b)""")
        private val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")
    }
}
