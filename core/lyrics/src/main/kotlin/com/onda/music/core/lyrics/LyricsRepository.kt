package com.onda.music.core.lyrics

import com.onda.music.core.database.LyricsDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.Track
import com.onda.music.core.sources.YtMusicClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LyricsRepository @Inject constructor(
    private val lyricsDao: LyricsDao,
    private val ytMusicClient: YtMusicClient,
    private val lyricsParser: LyricsParser,
    @LyricsHttpClient private val httpClient: HttpClient,
    @LyricsJson private val json: Json
) {
    fun observeLyrics(trackId: String): Flow<LyricsDocument?> =
        lyricsDao.observeByTrackId(trackId).map { entity ->
            entity?.toModel()
        }

    suspend fun getLyrics(
        track: Track,
        forceRefresh: Boolean = false
    ): Result<LyricsDocument> = runCatching {
        if (!forceRefresh) {
            val cached = lyricsDao.getByTrackId(track.id)?.toModel()
            if (cached != null && cached.lines.isNotEmpty()) {
                return@runCatching cached.copy(source = LyricsSource.CACHE)
            }
        }

        val lrcLibDoc = fetchFromLrcLib(track).getOrNull()
        if (lrcLibDoc != null && lrcLibDoc.lines.isNotEmpty()) {
            lyricsDao.upsert(lrcLibDoc.toEntity())
            return@runCatching lrcLibDoc
        }

        val ytDoc = fetchFromYouTubeMusic(track).getOrNull()
        if (ytDoc != null && ytDoc.lines.isNotEmpty()) {
            lyricsDao.upsert(ytDoc.toEntity())
            return@runCatching ytDoc
        }

        throw IllegalStateException("No lyrics found for ${track.title}")
    }

    suspend fun saveLyrics(
        trackId: String,
        rawContent: String,
        source: LyricsSource = LyricsSource.EMBEDDED,
        trackDurationMs: Long = 0L
    ): LyricsDocument {
        val doc = lyricsParser.parse(
            trackId = trackId,
            rawContent = rawContent,
            source = source,
            trackDurationMs = trackDurationMs
        )
        if (doc.lines.isNotEmpty()) {
            lyricsDao.upsert(doc.toEntity())
        }
        return doc
    }

    suspend fun clearCachedLyrics(trackId: String) {
        lyricsDao.deleteByTrackId(trackId)
    }

    private suspend fun fetchFromLrcLib(track: Track): Result<LyricsDocument> = runCatching {
        val cleanedTitle = cleanTitle(track.title)
        val cleanedArtist = cleanArtist(track.artist)
        val durationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }

        val candidates = queryLrcLibSearch(
            trackName = cleanedTitle,
            artistName = cleanedArtist,
            albumName = track.album
        ).ifEmpty {
            queryLrcLibSearch(query = "$cleanedArtist $cleanedTitle".trim())
        }.ifEmpty {
            queryLrcLibSearch(trackName = cleanedTitle)
        }

        val valid = candidates.filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
        val best = pickBestLrcLibCandidate(valid, cleanedTitle, cleanedArtist, durationSec)
            ?: throw IllegalStateException("LRCLib returned no matching lyrics")

        val raw = best.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: best.plainLyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("LRCLib candidate had empty lyrics")

        lyricsParser.parse(
            trackId = track.id,
            rawContent = raw,
            source = LyricsSource.LRCLIB,
            trackDurationMs = track.durationMs
        )
    }

    private suspend fun fetchFromYouTubeMusic(track: Track): Result<LyricsDocument> = runCatching {
        val videoId = track.ytId?.takeIf { it.isNotBlank() } ?: run {
            val query = "${cleanArtist(track.artist)} ${cleanTitle(track.title)}".trim()
            ytMusicClient.searchTracks(query).getOrNull()?.firstOrNull()?.ytId
        } ?: throw IllegalStateException("No YouTube videoId for lyrics fallback")

        val raw = ytMusicClient.getLyrics(videoId).getOrThrow()
        lyricsParser.parse(
            trackId = track.id,
            rawContent = raw,
            source = LyricsSource.YOUTUBE_MUSIC,
            trackDurationMs = track.durationMs
        )
    }

    private suspend fun queryLrcLibSearch(
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        query: String? = null
    ): List<LrcLibEntry> = try {
        val response = httpClient.get("https://lrclib.net/api/search") {
            header("User-Agent", LRCLIB_USER_AGENT)
            query?.let { parameter("q", it) }
            trackName?.let { parameter("track_name", it) }
            artistName?.let { parameter("artist_name", it) }
            albumName?.let { parameter("album_name", it) }
        }
        if (!response.status.isSuccess()) {
            emptyList()
        } else {
            json.decodeFromString<List<LrcLibEntry>>(response.bodyAsText())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        emptyList()
    }

    private fun pickBestLrcLibCandidate(
        candidates: List<LrcLibEntry>,
        targetTitle: String,
        targetArtist: String,
        durationSec: Int?
    ): LrcLibEntry? {
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { item ->
            var score = 0.0
            if (!item.syncedLyrics.isNullOrBlank()) score += 2.0
            if (item.trackName.equals(targetTitle, ignoreCase = true)) score += 1.5
            else if (item.trackName.contains(targetTitle, ignoreCase = true)) score += 0.8
            if (item.artistName.contains(targetArtist, ignoreCase = true)) score += 1.0
            if (durationSec != null && item.duration != null) {
                val diff = abs(item.duration - durationSec)
                score += when {
                    diff <= 2.0 -> 1.5
                    diff <= 5.0 -> 0.8
                    diff <= 15.0 -> 0.2
                    else -> -1.0
                }
            }
            score
        }
    }

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in TITLE_CLEANUP_PATTERNS) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        for (sep in ARTIST_SEPARATORS) {
            if (cleaned.contains(sep, ignoreCase = true)) {
                cleaned = cleaned.split(sep, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    @Serializable
    private data class LrcLibEntry(
        val id: Long = 0L,
        val trackName: String = "",
        val artistName: String = "",
        val albumName: String? = null,
        val duration: Double? = null,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null
    )

    companion object {
        private const val LRCLIB_USER_AGENT = "OndaMusic/1.0 (+https://github.com/DiemMyCodex/Onda-Music)"
        private val TITLE_CLEANUP_PATTERNS = listOf(
            Regex("""\s*\(.*?(official|video|audio|lyrics?|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*\[.*?(official|video|audio|lyrics?|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?]""", RegexOption.IGNORE_CASE),
            Regex("""\s*\|.*$"""),
            Regex("""\s*\((?:feat|ft)\..*?\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*(?:feat|ft)\..*$""", RegexOption.IGNORE_CASE)
        )
        private val ARTIST_SEPARATORS = listOf(" & ", " and ", ", ", " x ", " feat. ", " feat ", " ft. ", " ft ", " with ")
    }
}
