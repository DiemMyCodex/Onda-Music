package com.onda.music.core.sources

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class MonochromeServerKind {
    STREAMER,
    RELAY
}

data class MonochromeServer(
    val url: String,
    val kind: MonochromeServerKind = MonochromeServerKind.STREAMER,
    val name: String = url
)

data class FlacStreamInfo(
    val bitsPerSample: Int,
    val sampleRateHz: Int
) {
    val isHiRes: Boolean get() = bitsPerSample > 16 || sampleRateHz > 48_000
    val shortLabel: String get() = if (isHiRes) "Hi-Res FLAC" else "FLAC"
    val qualityLabel: String
        get() {
            val rate = if (sampleRateHz % 1000 == 0) {
                "${sampleRateHz / 1000}"
            } else {
                String.format(Locale.US, "%.1f", sampleRateHz / 1000.0)
            }
            return "$shortLabel · $bitsPerSample-bit / $rate kHz"
        }
}

@Singleton
class MonochromeClient @Inject constructor(
    @SourcesHttpClient private val httpClient: HttpClient,
    @SourcesJson private val json: Json,
    private val trackMatcher: TrackMatcher
) {
    @Volatile
    private var customServers: List<MonochromeServer> = emptyList()

    private val downUntil = ConcurrentHashMap<String, Long>()
    private val resolvedTrackCache = ConcurrentHashMap<String, Pair<MonochromeServer, Track>>()

    private val activeServers: List<MonochromeServer>
        get() = customServers + DEFAULT_SERVER

    fun setServers(servers: List<MonochromeServer>) {
        customServers = servers.distinctBy { it.url.trimEnd('/') }
    }

    fun getServers(): List<MonochromeServer> = activeServers

    suspend fun searchTracks(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): Result<List<Track>> =
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            val usable = usableServers()
            var lastError: Throwable? = null
            for (server in usable) {
                try {
                    val tracks = searchOnServer(server, query, limit)
                    clearDown(server.url)
                    if (tracks.isNotEmpty()) return@runCatching tracks
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    markDown(server.url)
                    lastError = e
                }
            }
            if (lastError != null) throw lastError
            emptyList()
        }

    suspend fun resolveTrackStream(track: Track): Result<StreamOption> = runCatching {
        val directId = track.monochromeId?.takeIf { it.isNotBlank() }
        if (directId != null) {
            return@runCatching resolveStreamById(directId).getOrThrow()
        }
        val (_, stream) = matchAndResolveStream(
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs.takeIf { it > 0L }
        ).getOrThrow()
        stream
    }

    suspend fun resolveStreamById(
        monochromeId: String,
        serverUrl: String? = null
    ): Result<StreamOption> = runCatching {
        val candidates = if (serverUrl != null) {
            activeServers.filter { it.url.equals(serverUrl.trimEnd('/'), ignoreCase = true) }
                .ifEmpty { listOf(MonochromeServer(serverUrl.trimEnd('/'), MonochromeServerKind.STREAMER, serverUrl)) }
        } else {
            usableServers()
        }
        var lastError: Throwable? = null
        for (server in candidates) {
            try {
                val stream = openStreamOnServer(server, monochromeId)
                if (stream != null) {
                    clearDown(server.url)
                    return@runCatching stream
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                markDown(server.url)
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No FLAC stream available on Monochrome for $monochromeId")
    }

    suspend fun matchAndResolveStream(
        title: String,
        artist: String,
        durationMs: Long? = null
    ): Result<Pair<Track, StreamOption>> = runCatching {
        val cacheKey = "${title.lowercase()}|${artist.lowercase()}"
        resolvedTrackCache[cacheKey]?.let { (cachedServer, cachedTrack) ->
            val monoId = cachedTrack.monochromeId ?: cachedTrack.id
            val stream = runCatching { openStreamOnServer(cachedServer, monoId) }.getOrNull()
            if (stream != null) return@runCatching cachedTrack to stream
        }

        val queryTrack = Track(
            id = "query",
            title = title,
            artist = artist,
            durationMs = durationMs ?: 0L,
            source = SourceKind.MONOCHROME
        )
        val searchQuery = "${artist.substringBefore(',').trim()} ${trackMatcher.coreTitle(title)}".trim()
        val usable = usableServers()
        for (server in usable) {
            val candidates = runCatching { searchOnServer(server, searchQuery, DEFAULT_SEARCH_LIMIT) }
                .onFailure { if (it !is CancellationException) markDown(server.url) }
                .getOrNull()
                ?: continue
            clearDown(server.url)
            val best = trackMatcher.bestMatch(queryTrack, candidates)?.track ?: continue
            val monoId = best.monochromeId ?: best.id
            val stream = openStreamOnServer(server, monoId) ?: continue
            resolvedTrackCache[cacheKey] = server to best
            return@runCatching best to stream
        }
        throw IllegalStateException("No matching FLAC track found on Monochrome")
    }

    private suspend fun searchOnServer(
        server: MonochromeServer,
        query: String,
        limit: Int
    ): List<Track> {
        val base = server.url.trimEnd('/')
        val url = when (server.kind) {
            MonochromeServerKind.STREAMER -> "$base/search/tracks?q=${encode(query)}&limit=$limit"
            MonochromeServerKind.RELAY -> "$base/search?q=${encode(query)}&n=$limit"
        }
        val response = httpClient.get(url) {
            header("Accept", "application/json")
            header("User-Agent", USER_AGENT)
            if (server.kind == MonochromeServerKind.RELAY) {
                header("X-Relay-Client", RELAY_CLIENT_VERSION)
            }
        }
        check(response.status.isSuccess()) { "Monochrome search failed: HTTP ${response.status.value}" }
        val body = response.bodyAsText()
        return when (server.kind) {
            MonochromeServerKind.STREAMER -> parseStreamerTracks(body)
            MonochromeServerKind.RELAY -> parseRelayTracks(body)
        }
    }

    private suspend fun openStreamOnServer(
        server: MonochromeServer,
        monochromeId: String
    ): StreamOption? {
        val base = server.url.trimEnd('/')
        val (streamUrl, bytes) = when (server.kind) {
            MonochromeServerKind.STREAMER -> {
                val url = "$base/track/$monochromeId"
                val response = httpClient.get(url) {
                    header("User-Agent", USER_AGENT)
                    header("Range", HEAD_RANGE)
                }
                if (!response.status.isSuccess()) return null
                url to response.bodyAsBytes()
            }
            MonochromeServerKind.RELAY -> {
                val url = "$base/s/${encode(monochromeId)}?q=flac"
                val response = httpClient.get(url) {
                    header("Accept", "application/json")
                    header("User-Agent", USER_AGENT)
                    header("X-Relay-Client", RELAY_CLIENT_VERSION)
                    header("Range", HEAD_RANGE)
                }
                if (!response.status.isSuccess()) return null
                val origin = originOf(base)
                val relayStream = response.headers["X-Relay-Stream"]
                    ?.takeIf { origin.isNotEmpty() && it.startsWith("$origin/") }
                    ?: return null
                relayStream to response.bodyAsBytes()
            }
        }

        val flacInfo = parseFlacHeader(bytes) ?: return null
        val estimatedKbps = ((flacInfo.sampleRateHz * flacInfo.bitsPerSample * 2) / 1000) * 65 / 100
        return StreamOption(
            url = streamUrl,
            mimeType = "audio/flac",
            bitrateKbps = estimatedKbps.coerceAtLeast(900),
            codec = "FLAC",
            qualityLabel = flacInfo.qualityLabel,
            expiresAtMs = null,
            source = SourceKind.MONOCHROME
        )
    }

    internal fun parseStreamerTracks(rawJson: String): List<Track> {
        val root = runCatching { json.parseToJsonElement(rawJson) as? JsonObject }.getOrNull() ?: return emptyList()
        val rows = root["tracks"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            if ((obj["playable"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
            val rawId = obj.str("trackId") ?: obj.str("id") ?: return@mapNotNull null
            val title = obj.str("title") ?: return@mapNotNull null
            val artists = (obj["artistNames"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
                .joinToString(", ")
                .ifBlank { obj.str("artist").orEmpty() }
            val durationMs = obj.longVal("duration")?.let { if (it > 10_000L) it else it * 1000L } ?: 0L
            val cover = obj.str("artworkUrl") ?: obj.str("coverUrl")
            Track(
                id = "mono_$rawId",
                title = title,
                artist = artists,
                album = obj.str("albumTitle") ?: obj.str("releaseTitle"),
                durationMs = durationMs,
                artworkUrl = cover,
                source = SourceKind.MONOCHROME,
                monochromeId = rawId
            )
        }
    }

    internal fun parseRelayTracks(rawJson: String): List<Track> {
        val root = runCatching { json.parseToJsonElement(rawJson) as? JsonObject }.getOrNull() ?: return emptyList()
        val rows = root["data"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val rawId = obj.str("id") ?: return@mapNotNull null
            val title = obj.str("title") ?: return@mapNotNull null
            val artist = obj.str("artist").orEmpty()
                .split(RELAY_ARTIST_JOIN)
                .joinToString(", ") { it.trim() }
            val durationMs = obj.longVal("duration")?.let { if (it > 10_000L) it else it * 1000L } ?: 0L
            Track(
                id = "mono_$rawId",
                title = title,
                artist = artist,
                album = obj.str("album")?.takeIf { it.isNotBlank() },
                durationMs = durationMs,
                artworkUrl = obj.str("cover"),
                source = SourceKind.MONOCHROME,
                monochromeId = rawId
            )
        }
    }

    internal fun parseFlacHeader(bytes: ByteArray): FlacStreamInfo? {
        if (bytes.size < 4 || bytes.copyOfRange(0, 4).decodeToString() != "fLaC") return null
        if (bytes.size < 26) return FlacStreamInfo(16, 44_100)
        val b = bytes.copyOfRange(8, 26).map { it.toInt() and 0xFF }
        val sampleRate = (b[10] shl 12) or (b[11] shl 4) or (b[12] shr 4)
        val bitsPerSample = (((b[12] and 0x01) shl 4) or (b[13] shr 4)) + 1
        return if (sampleRate in 8_000..768_000 && bitsPerSample in 8..32) {
            FlacStreamInfo(bitsPerSample, sampleRate)
        } else {
            FlacStreamInfo(16, 44_100)
        }
    }

    private fun usableServers(): List<MonochromeServer> {
        val now = System.currentTimeMillis()
        val healthy = activeServers.filter { (downUntil[it.url] ?: 0L) <= now }
        return healthy.ifEmpty { activeServers }
    }

    private fun markDown(url: String) {
        downUntil[url] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
    }

    private fun clearDown(url: String) {
        downUntil.remove(url)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.longVal(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun originOf(url: String): String = runCatching {
        URI(url).let { "${it.scheme}://${it.rawAuthority}" }
    }.getOrDefault("")

    companion object {
        val DEFAULT_SERVER = MonochromeServer(
            url = "https://tracks.monochrome.st",
            kind = MonochromeServerKind.STREAMER,
            name = "tracks.monochrome.st"
        )
        private const val DEFAULT_SEARCH_LIMIT = 8
        private const val SERVER_COOLDOWN_MS = 10 * 60_000L
        private const val RELAY_CLIENT_VERSION = "relay/2"
        private const val HEAD_RANGE = "bytes=0-41"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val RELAY_ARTIST_JOIN = Regex("""(?<! )&(?! )""")
    }
}
