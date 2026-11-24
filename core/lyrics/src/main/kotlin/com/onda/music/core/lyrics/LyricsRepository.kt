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
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        forceRefresh: Boolean = false,
        onDemandTranslate: Boolean = false,
        targetLanguageCode: String = "en"
    ): Result<LyricsDocument> = runCatching {
        if (!forceRefresh) {
            val cached = lyricsDao.getByTrackId(track.id)?.toModel()
            if (cached != null && cached.lines.isNotEmpty()) {
                val enriched = if (onDemandTranslate && !hasTranslations(cached)) {
                    applyOnDemandTranslation(cached, targetLanguageCode).also { updated ->
                        if (hasTranslations(updated)) {
                            lyricsDao.upsert(updated.toEntity())
                        }
                    }
                } else {
                    cached
                }
                return@runCatching enriched.copy(source = LyricsSource.CACHE)
            }
        }

        val resolved = resolveHedged(track)
            ?: fetchFromYouTubeMusic(track).getOrNull()
            ?: throw IllegalStateException("No lyrics found for ${track.title}")

        val finalDoc = if (onDemandTranslate && !hasTranslations(resolved)) {
            applyOnDemandTranslation(resolved, targetLanguageCode)
        } else {
            resolved
        }
        lyricsDao.upsert(finalDoc.toEntity())
        finalDoc
    }

    suspend fun translateCachedLyrics(
        trackId: String,
        targetLanguageCode: String
    ): Result<LyricsDocument> = runCatching {
        val cached = lyricsDao.getByTrackId(trackId)?.toModel()
            ?: throw IllegalStateException("No cached lyrics for $trackId")
        if (cached.lines.isEmpty() || hasTranslations(cached)) {
            return@runCatching cached
        }
        val translated = applyOnDemandTranslation(cached, targetLanguageCode)
        if (hasTranslations(translated)) {
            lyricsDao.upsert(translated.toEntity())
        }
        translated
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

    private suspend fun resolveHedged(track: Track): LyricsDocument? = coroutineScope {
        val betterDeferred = async {
            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { fetchFromBetterLyrics(track).getOrNull() }
        }
        val simpDeferred = async {
            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { fetchFromSimpMusic(track).getOrNull() }
        }
        val lrcLibDeferred = async {
            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { fetchFromLrcLib(track).getOrNull() }
        }
        val netEaseDeferred = async {
            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { fetchFromNetEase(track).getOrNull() }
        }

        val better = betterDeferred.await()
        val simp = simpDeferred.await()
        val lrcLib = lrcLibDeferred.await()
        val netEase = netEaseDeferred.await()

        val candidates = listOfNotNull(
            better,
            simp,
            lrcLib,
            netEase?.document
        ).filter { it.lines.isNotEmpty() }

        val best = selectBestLyricsCandidate(candidates) ?: return@coroutineScope null
        val netEaseTrans = netEase?.translatedLrc
        if (!hasTranslations(best) && !netEaseTrans.isNullOrBlank()) {
            lyricsParser.mergeTranslation(best, netEaseTrans)
        } else {
            best
        }
    }

    private suspend fun fetchFromBetterLyrics(track: Track): Result<LyricsDocument> = runCatching {
        val cleanedTitle = cleanTitle(track.title)
        val cleanedArtist = cleanArtist(track.artist)
        val durationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
        val response = httpClient.get(BETTER_LYRICS_ENDPOINT) {
            header("User-Agent", ONDA_LYRICS_USER_AGENT)
            parameter("s", cleanedTitle)
            parameter("a", cleanedArtist)
            track.album?.takeIf { it.isNotBlank() }?.let { parameter("al", it) }
            durationSec?.let { parameter("d", it) }
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("BetterLyrics HTTP ${response.status.value}")
        }
        val payload = json.decodeFromString<BetterLyricsResponse>(response.bodyAsText())
        val encodedTtml = payload.ttml?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("BetterLyrics empty ttml")
        val decodedXml = runCatching {
            String(Base64.getDecoder().decode(encodedTtml), Charsets.UTF_8)
        }.getOrElse { encodedTtml }

        lyricsParser.parseTtml(
            trackId = track.id,
            ttmlXml = decodedXml,
            source = LyricsSource.BETTER_LYRICS,
            trackDurationMs = track.durationMs
        )
    }

    private suspend fun fetchFromSimpMusic(track: Track): Result<LyricsDocument> = runCatching {
        val videoId = track.ytId?.takeIf { it.isNotBlank() }
        val rawBody = if (videoId != null) {
            val direct = httpClient.get("$SIMP_MUSIC_BASE_URL/$videoId") {
                header("User-Agent", ONDA_LYRICS_USER_AGENT)
            }
            if (direct.status.isSuccess()) direct.bodyAsText() else null
        } else {
            null
        } ?: run {
            val q = "${cleanTitle(track.title)} ${cleanArtist(track.artist)}".trim()
            val search = httpClient.get("$SIMP_MUSIC_BASE_URL/search") {
                header("User-Agent", ONDA_LYRICS_USER_AGENT)
                parameter("q", q)
            }
            if (!search.status.isSuccess()) {
                throw IllegalStateException("SimpMusic HTTP ${search.status.value}")
            }
            search.bodyAsText()
        }

        val entry = parseSimpMusicPayload(rawBody, track)
            ?: throw IllegalStateException("SimpMusic returned no lyrics")
        val content = entry.richSyncLyrics?.takeIf { it.isNotBlank() }
            ?: entry.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: entry.plainLyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SimpMusic entry empty")

        lyricsParser.parse(
            trackId = track.id,
            rawContent = content,
            source = LyricsSource.SIMP_MUSIC,
            trackDurationMs = track.durationMs
        )
    }

    private fun parseSimpMusicPayload(rawBody: String, track: Track): SimpMusicEntry? {
        val root = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() ?: return null
        val entries = when (root) {
            is JsonArray -> root.mapNotNull { decodeSimpEntry(it) }
            is JsonObject -> {
                val dataNode = root["data"]
                when (dataNode) {
                    is JsonArray -> dataNode.mapNotNull { decodeSimpEntry(it) }
                    is JsonObject -> listOfNotNull(decodeSimpEntry(dataNode))
                    else -> listOfNotNull(decodeSimpEntry(root))
                }
            }
            else -> emptyList()
        }.filter { !it.richSyncLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }

        if (entries.isEmpty()) return null
        val durationSec = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
        val targetTitle = cleanTitle(track.title)
        return entries.maxByOrNull { item ->
            var score = 0.0
            if (!item.richSyncLyrics.isNullOrBlank()) score += 4.0
            if (!item.syncedLyrics.isNullOrBlank()) score += 2.0
            if (item.title.equals(targetTitle, ignoreCase = true)) score += 1.5
            if (durationSec != null && item.durationSeconds != null && item.durationSeconds > 0) {
                val diff = abs(item.durationSeconds - durationSec)
                if (diff <= 3) score += 1.5 else if (diff > 15) score -= 1.0
            }
            score
        }
    }

    private fun decodeSimpEntry(element: JsonElement): SimpMusicEntry? {
        val obj = element as? JsonObject ?: return null
        return SimpMusicEntry(
            title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
            durationSeconds = obj["durationSeconds"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: obj["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt(),
            richSyncLyrics = obj["richSyncLyrics"]?.jsonPrimitive?.content
                ?: obj["richSyncedLyrics"]?.jsonPrimitive?.content,
            syncedLyrics = obj["syncedLyrics"]?.jsonPrimitive?.content,
            plainLyrics = obj["plainLyrics"]?.jsonPrimitive?.content
        )
    }

    private suspend fun fetchFromNetEase(track: Track): Result<NetEaseResult> = runCatching {
        val cleanedTitle = cleanTitle(track.title)
        val cleanedArtist = cleanArtist(track.artist)
        val query = "$cleanedTitle $cleanedArtist".trim()
        val searchResponse = httpClient.get(NETEASE_SEARCH_ENDPOINT) {
            header("User-Agent", ONDA_LYRICS_USER_AGENT)
            header("Referer", "https://music.163.com/")
            parameter("s", query)
            parameter("type", "1")
            parameter("limit", "5")
        }
        if (!searchResponse.status.isSuccess()) {
            throw IllegalStateException("NetEase search HTTP ${searchResponse.status.value}")
        }
        val searchRoot = json.parseToJsonElement(searchResponse.bodyAsText()).jsonObject
        val songs = searchRoot["result"]?.jsonObject?.get("songs")?.jsonArray ?: emptyList()
        val durationMs = track.durationMs
        val bestSongId = songs.mapNotNull { elem ->
            val obj = elem.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
            val dur = obj["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            var score = 0
            if (name.equals(cleanedTitle, ignoreCase = true)) score += 3
            else if (name.contains(cleanedTitle, ignoreCase = true)) score += 1
            if (durationMs > 0L && dur > 0L && abs(dur - durationMs) <= 4_000L) score += 2
            id to score
        }.maxByOrNull { it.second }?.first ?: throw IllegalStateException("NetEase no match")

        val lyricResponse = httpClient.get(NETEASE_LYRIC_ENDPOINT) {
            header("User-Agent", ONDA_LYRICS_USER_AGENT)
            header("Referer", "https://music.163.com/")
            parameter("id", bestSongId)
            parameter("lv", "1")
            parameter("tv", "1")
        }
        if (!lyricResponse.status.isSuccess()) {
            throw IllegalStateException("NetEase lyric HTTP ${lyricResponse.status.value}")
        }
        val lyricRoot = json.parseToJsonElement(lyricResponse.bodyAsText()).jsonObject
        val mainLrc = lyricRoot["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content.orEmpty()
        val transLrc = lyricRoot["tlyric"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content
        if (mainLrc.isBlank()) {
            throw IllegalStateException("NetEase empty lyric")
        }
        var doc = lyricsParser.parse(
            trackId = track.id,
            rawContent = mainLrc,
            source = LyricsSource.NETEASE,
            trackDurationMs = track.durationMs
        )
        if (!transLrc.isNullOrBlank()) {
            doc = lyricsParser.mergeTranslation(doc, transLrc)
        }
        NetEaseResult(document = doc, translatedLrc = transLrc)
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

    private suspend fun applyOnDemandTranslation(
        document: LyricsDocument,
        targetLanguageCode: String
    ): LyricsDocument {
        val targetLang = if (targetLanguageCode.startsWith("vi", ignoreCase = true)) "vi" else "en"
        val translatableIndices = document.lines.indices.filter { idx ->
            val line = document.lines[idx]
            !line.isInterlude && line.text.isNotBlank() && line.translatedText.isNullOrBlank()
        }
        if (translatableIndices.isEmpty()) return document

        val translatedByIndex = HashMap<Int, String>(translatableIndices.size)
        val chunks = translatableIndices.chunked(12)
        for (chunk in chunks) {
            val joined = chunk.joinToString("\n") { document.lines[it].text }
            val translatedText = requestLineTranslation(joined, targetLang) ?: continue
            val translatedLines = translatedText.lines()
            if (translatedLines.size == chunk.size) {
                chunk.forEachIndexed { offset, lineIdx ->
                    val original = document.lines[lineIdx].text.trim()
                    val candidate = translatedLines[offset].trim()
                    if (candidate.isNotEmpty() && !candidate.equals(original, ignoreCase = true)) {
                        translatedByIndex[lineIdx] = candidate
                    }
                }
            }
        }
        if (translatedByIndex.isEmpty()) return document
        val updated = document.lines.mapIndexed { idx, line ->
            val t = translatedByIndex[idx]
            if (t != null) line.copy(translatedText = t) else line
        }
        return document.copy(lines = updated)
    }

    private suspend fun requestLineTranslation(text: String, targetLang: String): String? = try {
        val response = withTimeoutOrNull(TRANSLATE_TIMEOUT_MS) {
            httpClient.get(TRANSLATE_ENDPOINT) {
                header("User-Agent", ONDA_LYRICS_USER_AGENT)
                parameter("client", "gtx")
                parameter("sl", "auto")
                parameter("tl", targetLang)
                parameter("dt", "t")
                parameter("q", text)
            }
        } ?: return null
        if (!response.status.isSuccess()) return null
        val root = json.parseToJsonElement(response.bodyAsText()).jsonArray
        val segments = root.firstOrNull()?.jsonArray ?: return null
        buildString {
            segments.forEach { seg ->
                val translatedChunk = seg.jsonArray.firstOrNull()?.jsonPrimitive?.content
                if (!translatedChunk.isNullOrEmpty()) {
                    append(translatedChunk)
                }
            }
        }.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    private suspend fun queryLrcLibSearch(
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        query: String? = null
    ): List<LrcLibEntry> = try {
        val response = httpClient.get("https://lrclib.net/api/search") {
            header("User-Agent", ONDA_LYRICS_USER_AGENT)
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

    private fun hasTranslations(doc: LyricsDocument): Boolean =
        doc.lines.any { !it.translatedText.isNullOrBlank() }

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

    private data class NetEaseResult(
        val document: LyricsDocument,
        val translatedLrc: String?
    )

    private data class SimpMusicEntry(
        val title: String = "",
        val durationSeconds: Int? = null,
        val richSyncLyrics: String? = null,
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null
    )

    @Serializable
    private data class BetterLyricsResponse(
        val ttml: String? = null
    )

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
        private const val ONDA_LYRICS_USER_AGENT = "OndaMusic/1.2.8 (+https://github.com/DiemMyCodex/Onda-Music)"
        private const val BETTER_LYRICS_ENDPOINT = "https://lyrics-api.boidu.dev/getLyrics"
        private const val SIMP_MUSIC_BASE_URL = "https://api-lyrics.simpmusic.org/v1"
        private const val NETEASE_SEARCH_ENDPOINT = "https://music.163.com/api/search/get"
        private const val NETEASE_LYRIC_ENDPOINT = "https://music.163.com/api/song/lyric"
        private const val TRANSLATE_ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        private const val PROVIDER_TIMEOUT_MS = 5_500L
        private const val TRANSLATE_TIMEOUT_MS = 5_000L

        internal fun selectBestLyricsCandidate(candidates: List<LyricsDocument>): LyricsDocument? {
            return candidates.filter { it.lines.isNotEmpty() }.maxByOrNull { doc ->
                var score = 0
                if (doc.hasWordTiming) score += 100
                if (doc.isSynced) score += 40
                if (doc.lines.any { !it.translatedText.isNullOrBlank() }) score += 15
                score += when (doc.source) {
                    LyricsSource.BETTER_LYRICS -> 12
                    LyricsSource.SIMP_MUSIC -> 10
                    LyricsSource.LRCLIB -> 8
                    LyricsSource.NETEASE -> 7
                    LyricsSource.YOUTUBE_MUSIC -> 2
                    LyricsSource.EMBEDDED -> 6
                    LyricsSource.CACHE -> 0
                }
                score
            }
        }

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
