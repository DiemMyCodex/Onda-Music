package com.onda.music.feature.home

import com.onda.music.core.model.ListenerTasteProfile
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.TasteSeed
import com.onda.music.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

data class PlaySignal(
    val trackId: String,
    val timestampMs: Long,
    val durationPlayedMs: Long
)

object ListenerTasteAnalyzer {

    private val vietnameseCharRegex = Regex(
        "[ăâđêôơưĂÂĐÊÔƠƯáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]"
    )

    private val vietnameseKeywordRegex = Regex(
        "\\b(" +
            "son tung|den vau|hoang dung|tlinh|mck|low g|hieuthuhai|soobin|amee|erik|duc phuc|" +
            "my tam|ha anh tuan|phan manh quynh|chillies|da lab|wren evans|grey d|bich phuong|" +
            "hoa minzy|phuong ly|karik|binz|justatee|rhymastic|suboi|truc nhan|van mai huong|" +
            "quang hung|duong domic|tang duy tan|bui truong linh|ca hoi hoang|quan a\\.p|" +
            "vpop|v-pop|nhac tre|nhac viet|lofi viet|bolero" +
            ")\\b",
        RegexOption.IGNORE_CASE
    )

    fun analyze(
        currentTrack: Track?,
        recentSongs: List<Track>,
        playSignals: List<PlaySignal>,
        allSongs: List<Track>,
        appLanguageCode: String,
        nowMs: Long = System.currentTimeMillis()
    ): ListenerTasteProfile {
        val trackLookup = LinkedHashMap<String, Track>()
        for (song in allSongs) {
            trackLookup[song.id] = song
        }
        for (recent in recentSongs) {
            val existing = trackLookup[recent.id]
            trackLookup[recent.id] = if (existing == null) recent else existing.copy(
                liked = existing.liked || recent.liked,
                totalPlayTimeMs = maxOf(existing.totalPlayTimeMs, recent.totalPlayTimeMs)
            )
        }
        if (currentTrack != null) {
            val existing = trackLookup[currentTrack.id]
            val mergedLiked = (existing?.liked ?: false) || currentTrack.liked
            trackLookup[currentTrack.id] = currentTrack.copy(liked = mergedLiked)
        }

        val playCounts = HashMap<String, Int>()
        val recentDecayById = HashMap<String, Double>()
        for (signal in playSignals) {
            playCounts[signal.trackId] = (playCounts[signal.trackId] ?: 0) + 1
            val ageHours = ((nowMs - signal.timestampMs).coerceAtLeast(0L) / 3_600_000.0)
            val decay = exp(-ageHours / 48.0)
            val completionFactor = if (signal.durationPlayedMs in 1L..4_999L) 0.2 else 1.0
            recentDecayById[signal.trackId] = (recentDecayById[signal.trackId] ?: 0.0) + (2.5 * decay * completionFactor)
        }

        val recentRankById = HashMap<String, Int>()
        recentSongs.forEachIndexed { index, track ->
            recentRankById.putIfAbsent(track.id, index)
        }

        val curTrackYtId = currentTrack?.let { extractVideoId(it) }
        val scoredEntries = trackLookup.values.mapNotNull { track ->
            var score = 0.0
            val isActive = currentTrack != null && (
                track.id == currentTrack.id ||
                    (curTrackYtId != null && curTrackYtId == extractVideoId(track))
                )
            if (isActive) {
                score += 6.0
            }
            score += recentDecayById[track.id] ?: 0.0
            val count = playCounts[track.id] ?: 0
            val playMins = track.totalPlayTimeMs.coerceAtLeast(0L) / 60_000.0
            if (count > 0 || playMins > 0.0) {
                score += ln(1.0 + count) * 1.8 + ln(1.0 + playMins) * 0.9
            }
            if (track.liked) {
                score += 4.2
            }
            val recentIdx = recentRankById[track.id]
            if (recentIdx != null && (recentDecayById[track.id] ?: 0.0) == 0.0) {
                score += 1.5 / (recentIdx + 1)
            }
            if (score <= 0.0) return@mapNotNull null
            ScoredTrack(track = track, videoId = extractVideoId(track), score = score, isActive = isActive)
        }.sortedWith(
            compareByDescending<ScoredTrack> { it.isActive }
                .thenByDescending { it.score }
        )

        val hasHistory = currentTrack != null ||
            playSignals.isNotEmpty() ||
            recentSongs.isNotEmpty() ||
            allSongs.any { it.liked || it.totalPlayTimeMs > 0L }

        val topSeeds = buildTopSeeds(scoredEntries, maxSeeds = 5)
        val topArtists = buildTopArtists(scoredEntries, limit = 12)

        val topHistory = scoredEntries.take(25)
        var vnWeight = 0.0
        var totalWeight = 0.0
        for (entry in topHistory) {
            val w = entry.score.coerceAtLeast(0.5)
            totalWeight += w
            if (isVietnameseTrack(entry.track.title, entry.track.artist)) {
                vnWeight += w
            }
        }
        val historyVnScore = if (totalWeight > 0.0) (vnWeight / totalWeight).toFloat().coerceIn(0f, 1f) else 0f
        val activeVnIndicator = currentTrack?.let { if (isVietnameseTrack(it.title, it.artist)) 1f else 0f }
        val vietnameseAffinityScore = when {
            activeVnIndicator != null && totalWeight > 0.0 ->
                (0.70f * historyVnScore + 0.30f * activeVnIndicator).coerceIn(0f, 1f)
            activeVnIndicator != null -> activeVnIndicator
            else -> historyVnScore
        }

        val normalizedAppLang = if (appLanguageCode.startsWith("vi", ignoreCase = true)) "vi" else "en"
        val (dominantRegionCode, dominantLanguageTag) = when {
            !hasHistory -> "VN" to normalizedAppLang
            vietnameseAffinityScore >= 0.35f -> "VN" to "vi"
            vietnameseAffinityScore <= 0.15f ->
                (if (normalizedAppLang == "vi") "VN" else "US") to normalizedAppLang
            else -> "VN" to normalizedAppLang
        }

        val excludedIds = LinkedHashSet<String>()
        if (currentTrack != null) {
            excludedIds += currentTrack.id
            extractVideoId(currentTrack)?.let { vid ->
                excludedIds += vid
                excludedIds += "yt_$vid"
            }
        }
        recentSongs.take(3).forEach { recent ->
            excludedIds += recent.id
            extractVideoId(recent)?.let { vid ->
                excludedIds += vid
                excludedIds += "yt_$vid"
            }
        }

        val recentIds = LinkedHashSet<String>()
        recentSongs.take(10).forEach { recent ->
            recentIds += recent.id
            extractVideoId(recent)?.let { vid ->
                recentIds += vid
                recentIds += "yt_$vid"
            }
        }

        val likedCount = trackLookup.values.count { it.liked }
        val signature = if (!hasHistory) {
            "cold_start_${dominantRegionCode}_${dominantLanguageTag}"
        } else {
            val seedSig = topSeeds.take(3).joinToString(",") { it.videoId }
            val vnBucket = (vietnameseAffinityScore * 10f).toInt()
            "$seedSig|$dominantRegionCode|$dominantLanguageTag|$vnBucket|$likedCount|${playSignals.size / 2}"
        }

        return ListenerTasteProfile(
            topSeeds = topSeeds,
            topArtists = topArtists,
            vietnameseAffinityScore = vietnameseAffinityScore,
            dominantRegionCode = dominantRegionCode,
            dominantLanguageTag = dominantLanguageTag,
            excludedTrackIds = excludedIds,
            recentTrackIds = recentIds,
            hasListeningHistory = hasHistory,
            tasteSignature = signature
        )
    }

    fun isVietnameseTrack(title: String, artist: String): Boolean {
        val combined = "$title $artist"
        if (vietnameseCharRegex.containsMatchIn(combined)) {
            return true
        }
        val folded = Normalizer.normalize(combined, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
        return vietnameseKeywordRegex.containsMatchIn(folded)
    }

    fun rankCandidateTracks(
        candidates: List<Pair<Track, Int>>,
        profile: ListenerTasteProfile,
        likedTrackIds: Set<String> = emptySet(),
        excludedIds: Set<String> = profile.excludedTrackIds,
        maxPerArtist: Int = 2,
        limit: Int = 16
    ): List<Track> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val artistRankBoost = HashMap<String, Double>()
        profile.topArtists.forEachIndexed { index, artist ->
            val key = normalizePrimaryArtist(artist)
            if (key.isNotEmpty()) {
                artistRankBoost.putIfAbsent(key, (3.2 - index * 0.25).coerceAtLeast(0.4))
            }
        }

        val seenVideoOrIds = HashSet<String>()
        val seenTitleArtist = HashSet<String>()
        val scoredCandidates = ArrayList<Pair<Track, Double>>(candidates.size)

        for ((rawTrack, seedPriorityIndex) in candidates) {
            val trackVideoId = extractVideoId(rawTrack)
            if (rawTrack.id in excludedIds) continue
            if (trackVideoId != null && (trackVideoId in excludedIds || "yt_$trackVideoId" in excludedIds)) continue

            val canonicalId = trackVideoId ?: rawTrack.id
            if (!seenVideoOrIds.add(canonicalId)) continue

            val primaryArtistKey = normalizePrimaryArtist(rawTrack.artist)
            val titleKey = normalizeTitle(rawTrack.title)
            if (titleKey.isNotEmpty()) {
                val pairKey = "$titleKey|$primaryArtistKey"
                if (!seenTitleArtist.add(pairKey)) continue
            }

            var score = when (seedPriorityIndex) {
                0 -> 5.0
                1 -> 3.6
                2 -> 2.8
                else -> 1.6
            }

            if (primaryArtistKey.isNotEmpty()) {
                score += artistRankBoost[primaryArtistKey] ?: 0.0
                if (primaryArtistKey !in artistRankBoost) {
                    val partialMatch = artistRankBoost.entries.firstOrNull { (topKey, _) ->
                        topKey.length >= 4 && (primaryArtistKey.contains(topKey) || topKey.contains(primaryArtistKey))
                    }
                    if (partialMatch != null) {
                        score += partialMatch.value * 0.7
                    }
                }
            }

            val isVn = isVietnameseTrack(rawTrack.title, rawTrack.artist)
            score += if (isVn) {
                (profile.vietnameseAffinityScore - 0.3f) * 4.5
            } else {
                (0.4f - profile.vietnameseAffinityScore) * 3.0
            }

            val inRecentPool = rawTrack.id in profile.recentTrackIds ||
                (trackVideoId != null && trackVideoId in profile.recentTrackIds)
            score += if (inRecentPool) -2.5 else 0.8

            val artworkUrl = rawTrack.artworkUrl
            if (!artworkUrl.isNullOrBlank()) {
                score += 0.4
            }

            val syncedTrack = if (rawTrack.id in likedTrackIds && !rawTrack.liked) {
                rawTrack.copy(liked = true)
            } else {
                rawTrack
            }
            scoredCandidates += syncedTrack to score
        }

        val sorted = scoredCandidates.sortedByDescending { it.second }
        val result = ArrayList<Track>(limit.coerceAtMost(sorted.size))
        val artistCounts = HashMap<String, Int>()
        val overflow = ArrayList<Track>()

        for ((track, _) in sorted) {
            if (result.size >= limit) break
            val artistKey = normalizePrimaryArtist(track.artist)
            val count = if (artistKey.isEmpty()) 0 else (artistCounts[artistKey] ?: 0)
            if (artistKey.isEmpty() || count < maxPerArtist) {
                result += track
                if (artistKey.isNotEmpty()) {
                    artistCounts[artistKey] = count + 1
                }
            } else {
                overflow += track
            }
        }

        if (result.size < limit && overflow.isNotEmpty()) {
            for (track in overflow) {
                if (result.size >= limit) break
                val artistKey = normalizePrimaryArtist(track.artist)
                val count = if (artistKey.isEmpty()) 0 else (artistCounts[artistKey] ?: 0)
                if (artistKey.isEmpty() || count < maxPerArtist) {
                    result += track
                    if (artistKey.isNotEmpty()) {
                        artistCounts[artistKey] = count + 1
                    }
                }
            }
        }

        return result
    }

    fun selectSeedSubset(
        profile: ListenerTasteProfile,
        maxSeeds: Int = 3
    ): List<TasteSeed> {
        val allSeeds = profile.topSeeds
        if (allSeeds.size <= maxSeeds) return allSeeds
        val score = profile.vietnameseAffinityScore
        if (score !in 0.15f..0.85f) {
            return allSeeds.take(maxSeeds)
        }
        val selected = ArrayList<TasteSeed>(maxSeeds)
        val first = allSeeds.first()
        selected += first
        val firstIsVn = isVietnameseTrack(first.title, first.artist)
        val oppositeSeed = allSeeds.drop(1).firstOrNull { isVietnameseTrack(it.title, it.artist) != firstIsVn }
        if (oppositeSeed != null && selected.size < maxSeeds) {
            selected += oppositeSeed
        }
        for (seed in allSeeds) {
            if (selected.size >= maxSeeds) break
            if (selected.none { it.videoId == seed.videoId }) {
                selected += seed
            }
        }
        return selected
    }

    private fun buildTopSeeds(entries: List<ScoredTrack>, maxSeeds: Int): List<TasteSeed> {
        val result = ArrayList<TasteSeed>(maxSeeds)
        val seenVideos = HashSet<String>()
        val artistCounts = HashMap<String, Int>()
        val hasMultipleArtists = entries
            .map { normalizePrimaryArtist(it.track.artist) }
            .filter { it.isNotEmpty() }
            .toSet()
            .size > 1

        for (entry in entries) {
            if (result.size >= maxSeeds) break
            val vid = entry.videoId ?: continue
            if (!seenVideos.add(vid)) continue
            val artistKey = normalizePrimaryArtist(entry.track.artist)
            val count = artistCounts[artistKey] ?: 0
            if (hasMultipleArtists && !entry.isActive && artistKey.isNotEmpty() && count >= 2) {
                continue
            }
            result += TasteSeed(
                trackId = entry.track.id,
                videoId = vid,
                title = entry.track.title,
                artist = entry.track.artist,
                score = entry.score
            )
            if (artistKey.isNotEmpty()) {
                artistCounts[artistKey] = count + 1
            }
        }
        return result
    }

    private fun buildTopArtists(entries: List<ScoredTrack>, limit: Int): List<String> {
        val scores = HashMap<String, Double>()
        val displayNames = HashMap<String, String>()
        for (entry in entries) {
            val rawArtist = entry.track.artist.trim()
            val key = normalizePrimaryArtist(rawArtist)
            if (key.isEmpty()) continue
            scores[key] = (scores[key] ?: 0.0) + entry.score
            displayNames.putIfAbsent(key, rawArtist.substringBefore(',').substringBefore('&').trim().ifBlank { rawArtist })
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { displayNames[it.key] }
    }

    private fun extractVideoId(track: Track): String? {
        val ytId = track.ytId
        if (!ytId.isNullOrBlank()) return ytId
        if (track.source == SourceKind.YOUTUBE && track.id.isNotBlank()) {
            return track.id.removePrefix("yt_").takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun normalizePrimaryArtist(artist: String): String {
        val primary = artist
            .lowercase(Locale.ROOT)
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore("feat.")
            .substringBefore("ft.")
            .substringBefore(" x ")
            .trim()
        return primary
    }

    private fun normalizeTitle(title: String): String {
        return title
            .lowercase(Locale.ROOT)
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private data class ScoredTrack(
        val track: Track,
        val videoId: String?,
        val score: Double,
        val isActive: Boolean
    )
}
