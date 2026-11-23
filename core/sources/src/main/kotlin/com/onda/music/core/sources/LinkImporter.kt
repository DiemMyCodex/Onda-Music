package com.onda.music.core.sources

import com.onda.music.core.model.ImportedCollection
import com.onda.music.core.model.ImportedTrackCandidate
import com.onda.music.core.model.LinkEntityKind
import com.onda.music.core.model.LinkProvider
import com.onda.music.core.model.ParsedLink
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

class LinkImportException(message: String) : Exception(message)

@Singleton
class LinkImporter @Inject constructor(
    @SourcesHttpClient private val httpClient: HttpClient,
    @SourcesJson private val json: Json,
    private val ytMusicClient: YtMusicClient,
    private val monochromeClient: MonochromeClient,
    private val trackMatcher: TrackMatcher
) {
    fun detect(rawInput: String): ParsedLink? {
        val url = extractUrl(rawInput.trim()) ?: return null
        SPOTIFY_REGEX.find(url)?.let { match ->
            val kind = when (match.groupValues[1].lowercase()) {
                "track" -> LinkEntityKind.TRACK
                "album" -> LinkEntityKind.ALBUM
                else -> LinkEntityKind.PLAYLIST
            }
            return ParsedLink(
                provider = LinkProvider.SPOTIFY,
                kind = kind,
                externalId = match.groupValues[2],
                originalUrl = url
            )
        }
        APPLE_REGEX.find(url)?.let { match ->
            val rawType = match.groupValues[1].lowercase()
            val hasTrackParam = APPLE_TRACK_PARAM_REGEX.find(url)?.groupValues?.get(1)
            val kind = when {
                hasTrackParam != null -> LinkEntityKind.TRACK
                rawType == "album" -> LinkEntityKind.ALBUM
                else -> LinkEntityKind.PLAYLIST
            }
            val extId = hasTrackParam ?: match.groupValues[2]
            return ParsedLink(
                provider = LinkProvider.APPLE_MUSIC,
                kind = kind,
                externalId = extId,
                originalUrl = url
            )
        }
        DEEZER_REGEX.find(url)?.let { match ->
            val kind = when (match.groupValues[1].lowercase()) {
                "track" -> LinkEntityKind.TRACK
                "album" -> LinkEntityKind.ALBUM
                else -> LinkEntityKind.PLAYLIST
            }
            return ParsedLink(
                provider = LinkProvider.DEEZER,
                kind = kind,
                externalId = match.groupValues[2],
                originalUrl = url
            )
        }
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            val browseId = YT_BROWSE_REGEX.find(url)?.groupValues?.get(1)
            val listId = YT_LIST_REGEX.find(url)?.groupValues?.get(1)
            val videoId = YT_VIDEO_REGEX.find(url)?.groupValues?.get(1)
            return when {
                browseId != null && browseId.startsWith("MPREb_") -> ParsedLink(
                    provider = LinkProvider.YOUTUBE,
                    kind = LinkEntityKind.ALBUM,
                    externalId = browseId,
                    originalUrl = url
                )
                listId != null -> ParsedLink(
                    provider = LinkProvider.YOUTUBE,
                    kind = LinkEntityKind.PLAYLIST,
                    externalId = listId,
                    originalUrl = url
                )
                browseId != null -> ParsedLink(
                    provider = LinkProvider.YOUTUBE,
                    kind = LinkEntityKind.PLAYLIST,
                    externalId = browseId.removePrefix("VL"),
                    originalUrl = url
                )
                videoId != null -> ParsedLink(
                    provider = LinkProvider.YOUTUBE,
                    kind = LinkEntityKind.TRACK,
                    externalId = videoId,
                    originalUrl = url
                )
                else -> null
            }
        }
        return null
    }

    suspend fun importCollection(rawInput: String): Result<ImportedCollection> = runCatching {
        val url = extractUrl(rawInput.trim())
            ?: throw LinkImportException("Paste a valid Spotify, Apple Music, Deezer, or YouTube Music link.")
        val resolved = if (SHORT_HOSTS.any { url.contains(it, ignoreCase = true) }) {
            resolveShortLink(url)
        } else {
            url
        }
        val parsed = detect(resolved) ?: detect(url)
            ?: throw LinkImportException("Unsupported music link.")
        when (parsed.provider) {
            LinkProvider.SPOTIFY -> fetchSpotify(parsed)
            LinkProvider.APPLE_MUSIC -> fetchAppleMusic(parsed)
            LinkProvider.DEEZER -> fetchDeezer(parsed)
            LinkProvider.YOUTUBE -> fetchYouTube(parsed)
        }
    }

    suspend fun importAndMatch(
        rawInput: String,
        preferMonochrome: Boolean = false
    ): Result<Pair<ImportedCollection, List<Track>>> = runCatching {
        val collection = importCollection(rawInput).getOrThrow()
        if (collection.provider == LinkProvider.YOUTUBE) {
            val directTracks = collection.tracks.mapNotNull { candidate ->
                val vid = candidate.externalId ?: return@mapNotNull null
                Track(
                    id = "yt_$vid",
                    title = candidate.title,
                    artist = candidate.artist,
                    album = candidate.album,
                    durationMs = candidate.durationMs,
                    artworkUrl = candidate.artworkUrl,
                    source = SourceKind.YOUTUBE,
                    ytId = vid
                )
            }
            if (directTracks.isNotEmpty()) return@runCatching collection to directTracks
        }

        val matched = collection.tracks.mapNotNull { candidate ->
            matchCandidate(candidate, preferMonochrome)
        }
        collection to matched
    }

    private suspend fun matchCandidate(
        candidate: ImportedTrackCandidate,
        preferMonochrome: Boolean
    ): Track? {
        val query = "${candidate.artist.substringBefore(',').trim()} ${trackMatcher.coreTitle(candidate.title)}".trim()
        if (preferMonochrome) {
            val monoCandidates = monochromeClient.searchTracks(query, 6).getOrDefault(emptyList())
            trackMatcher.bestMatch(candidate, monoCandidates)?.let { return it.track }
        }
        val ytCandidates = ytMusicClient.searchTracks(query).getOrDefault(emptyList())
        return trackMatcher.bestMatch(candidate, ytCandidates)?.track
    }

    private suspend fun fetchSpotify(parsed: ParsedLink): ImportedCollection {
        val pathKind = when (parsed.kind) {
            LinkEntityKind.TRACK -> "track"
            LinkEntityKind.ALBUM -> "album"
            LinkEntityKind.PLAYLIST -> "playlist"
        }
        val html = fetchText("https://open.spotify.com/embed/$pathKind/${parsed.externalId}", "Spotify")
        return parseSpotifyEmbedHtml(html, pathKind)
    }

    internal fun parseSpotifyEmbedHtml(html: String, kindLabel: String): ImportedCollection {
        val nextData = SPOTIFY_NEXT_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: throw LinkImportException("Could not parse Spotify embed payload.")
        val entity = json.parseToJsonElement(nextData)
            .path("props", "pageProps", "state", "data", "entity") as? JsonObject
            ?: throw LinkImportException("Spotify $kindLabel is private or unavailable.")
        val title = entity.str("name") ?: entity.str("title") ?: "Spotify $kindLabel"
        val cover = (entity.path("coverArt", "sources") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .maxByOrNull { it.longVal("width") ?: 0L }
            ?.str("url")
        val albumTitle = if (kindLabel == "album") title else null
        val trackList = (entity["trackList"] as? JsonArray).orEmpty()
        val tracks = if (trackList.isNotEmpty()) {
            trackList.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val trackTitle = obj.str("title") ?: return@mapNotNull null
                ImportedTrackCandidate(
                    title = trackTitle,
                    artist = obj.str("subtitle").orEmpty().replace('\u00A0', ' '),
                    album = albumTitle,
                    durationMs = obj.longVal("duration") ?: 0L,
                    artworkUrl = cover,
                    externalId = obj.str("uri")
                )
            }
        } else {
            val singleTitle = entity.str("name") ?: entity.str("title")
            val artists = (entity["artists"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("name") }
                .joinToString(", ")
                .ifBlank { entity.str("subtitle").orEmpty().replace('\u00A0', ' ') }
            if (singleTitle != null && kindLabel == "track") {
                listOf(
                    ImportedTrackCandidate(
                        title = singleTitle,
                        artist = artists,
                        durationMs = entity.longVal("duration") ?: 0L,
                        artworkUrl = cover,
                        externalId = entity.str("uri")
                    )
                )
            } else {
                emptyList()
            }
        }
        if (tracks.isEmpty()) throw LinkImportException("Spotify $kindLabel contains no readable tracks.")
        return ImportedCollection(
            title = title,
            artworkUrl = cover,
            provider = LinkProvider.SPOTIFY,
            tracks = tracks
        )
    }

    private suspend fun fetchAppleMusic(parsed: ParsedLink): ImportedCollection {
        val pageUrl = parsed.originalUrl.substringBefore('#')
        val html = fetchText(pageUrl, "Apple Music")
        val typeLabel = when (parsed.kind) {
            LinkEntityKind.ALBUM -> "album"
            LinkEntityKind.TRACK -> "track"
            LinkEntityKind.PLAYLIST -> "playlist"
        }
        return parseAppleMusicHtml(html, typeLabel, parsed.externalId.takeIf { parsed.kind == LinkEntityKind.TRACK })
    }

    internal fun parseAppleMusicHtml(
        html: String,
        typeLabel: String,
        targetTrackId: String? = null
    ): ImportedCollection {
        val raw = APPLE_SERVER_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: throw LinkImportException("Could not parse Apple Music page.")
        val root = json.parseToJsonElement(raw)
        val page = (root.path("data") as? JsonArray)?.firstOrNull()?.path("data")
            ?: (root as? JsonArray)?.firstOrNull()?.path("data")
            ?: throw LinkImportException("Apple Music $typeLabel is private or unavailable.")
        val sections = (page.path("sections") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val header = sections.firstOrNull { it.str("itemKind") == "containerDetailHeaderLockup" }
            ?.let { (it["items"] as? JsonArray)?.firstOrNull() as? JsonObject }
        val collectionTitle = header?.str("title") ?: "Apple Music $typeLabel"
        val albumArtist = (header?.get("subtitleLinks") as? JsonArray)
            ?.firstOrNull()
            ?.path("title")
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
        val cover = (header?.path("artwork", "dictionary", "url") as? JsonPrimitive)?.contentOrNull
            ?.replace("{w}", "600")
            ?.replace("{h}", "600")
            ?.replace("{c}", "bb")
            ?.replace("{f}", "jpg")

        val candidates = sections.filter { it.str("itemKind") == "trackLockup" }
            .flatMap { (it["items"] as? JsonArray).orEmpty() }
            .mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val trackId = obj.str("id") ?: obj.path("playAction", "actionMetrics", "data", "id")
                    ?.let { (it as? JsonPrimitive)?.contentOrNull }
                if (targetTrackId != null && trackId != null && trackId != targetTrackId) {
                    return@mapNotNull null
                }
                val title = obj.str("title") ?: return@mapNotNull null
                val artists = (obj["subtitleLinks"] as? JsonArray).orEmpty()
                    .mapNotNull { (it.path("title") as? JsonPrimitive)?.contentOrNull }
                val album = if (typeLabel == "album" || targetTrackId != null) {
                    collectionTitle
                } else {
                    (obj["tertiaryLinks"] as? JsonArray)?.firstOrNull()
                        ?.let { (it.path("title") as? JsonPrimitive)?.contentOrNull }
                }
                ImportedTrackCandidate(
                    title = title,
                    artist = obj.str("artistName") ?: artists.joinToString(", ").ifEmpty { albumArtist.orEmpty() },
                    album = album,
                    durationMs = obj.longVal("duration") ?: 0L,
                    artworkUrl = cover,
                    externalId = trackId
                )
            }
        if (candidates.isEmpty()) throw LinkImportException("Apple Music $typeLabel contains no readable tracks.")
        return ImportedCollection(
            title = if (targetTrackId != null && candidates.size == 1) candidates.first().title else collectionTitle,
            artworkUrl = cover,
            provider = LinkProvider.APPLE_MUSIC,
            tracks = candidates
        )
    }

    private suspend fun fetchDeezer(parsed: ParsedLink): ImportedCollection {
        val kindPath = when (parsed.kind) {
            LinkEntityKind.TRACK -> "track"
            LinkEntityKind.ALBUM -> "album"
            LinkEntityKind.PLAYLIST -> "playlist"
        }
        val infoRaw = fetchText("https://api.deezer.com/$kindPath/${parsed.externalId}", "Deezer")
        if (parsed.kind == LinkEntityKind.TRACK) {
            return parseDeezerTrackJson(infoRaw)
        }
        val info = parseDeezerObject(infoRaw)
        val name = info.str("title") ?: "Deezer $kindPath"
        val cover = info.str("picture_xl") ?: info.str("cover_xl")
        val albumName = if (parsed.kind == LinkEntityKind.ALBUM) name else null
        val tracks = mutableListOf<ImportedTrackCandidate>()
        var nextUrl: String? = "https://api.deezer.com/$kindPath/${parsed.externalId}/tracks?limit=100"
        var pageCount = 0
        while (nextUrl != null && pageCount < 50) {
            val pageObj = parseDeezerObject(fetchText(nextUrl, "Deezer"))
            tracks += parseDeezerTrackPage(pageObj, albumName, cover)
            nextUrl = pageObj.str("next")?.takeIf { it.startsWith("https://api.deezer.com/") }
            pageCount++
        }
        if (tracks.isEmpty()) throw LinkImportException("Deezer $kindPath contains no readable tracks.")
        return ImportedCollection(
            title = name,
            description = info.str("description"),
            artworkUrl = cover,
            provider = LinkProvider.DEEZER,
            tracks = tracks
        )
    }

    internal fun parseDeezerTrackJson(rawJson: String): ImportedCollection {
        val obj = parseDeezerObject(rawJson)
        val title = obj.str("title") ?: throw LinkImportException("Deezer track missing title.")
        val artist = (obj["artist"] as? JsonObject)?.str("name").orEmpty()
        val albumObj = obj["album"] as? JsonObject
        val cover = albumObj?.str("cover_xl")
        return ImportedCollection(
            title = title,
            artworkUrl = cover,
            provider = LinkProvider.DEEZER,
            tracks = listOf(
                ImportedTrackCandidate(
                    title = title,
                    artist = artist,
                    album = albumObj?.str("title"),
                    durationMs = (obj.longVal("duration") ?: 0L) * 1000L,
                    artworkUrl = cover,
                    externalId = obj.str("id")
                )
            )
        )
    }

    internal fun parseDeezerTrackPage(
        pageObj: JsonObject,
        forcedAlbumTitle: String? = null,
        fallbackCover: String? = null
    ): List<ImportedTrackCandidate> =
        (pageObj["data"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val title = row.str("title") ?: return@mapNotNull null
            val artist = (row["artist"] as? JsonObject)?.str("name").orEmpty()
            val albumObj = row["album"] as? JsonObject
            ImportedTrackCandidate(
                title = title,
                artist = artist,
                album = forcedAlbumTitle ?: albumObj?.str("title"),
                durationMs = (row.longVal("duration") ?: 0L) * 1000L,
                artworkUrl = albumObj?.str("cover_xl") ?: fallbackCover,
                externalId = row.str("id")
            )
        }

    private fun parseDeezerObject(rawJson: String): JsonObject {
        val obj = json.parseToJsonElement(rawJson) as? JsonObject
            ?: throw LinkImportException("Unexpected Deezer response.")
        val err = obj["error"] as? JsonObject
        if (err != null) {
            throw LinkImportException("Deezer error: ${err.str("message") ?: "unavailable"}")
        }
        return obj
    }

    private suspend fun fetchYouTube(parsed: ParsedLink): ImportedCollection {
        return when (parsed.kind) {
            LinkEntityKind.TRACK -> {
                val track = ytMusicClient.getTrackMetadata(parsed.externalId).getOrThrow()
                ImportedCollection(
                    title = track.title,
                    artworkUrl = track.artworkUrl,
                    provider = LinkProvider.YOUTUBE,
                    tracks = listOf(track.toImportedCandidate())
                )
            }
            LinkEntityKind.ALBUM -> {
                val (album, tracks) = ytMusicClient.getAlbumTracks(parsed.externalId).getOrThrow()
                ImportedCollection(
                    title = album.title,
                    artworkUrl = album.artworkUrl,
                    provider = LinkProvider.YOUTUBE,
                    tracks = tracks.map { it.toImportedCandidate() }
                )
            }
            LinkEntityKind.PLAYLIST -> {
                if (parsed.externalId.startsWith("RD")) {
                    throw LinkImportException("YouTube Radio mixes are dynamic; save as a playlist first.")
                }
                val (playlist, tracks) = ytMusicClient.getPlaylistTracks(parsed.externalId).getOrThrow()
                ImportedCollection(
                    title = playlist.name,
                    description = playlist.description,
                    artworkUrl = playlist.artworkUrl,
                    provider = LinkProvider.YOUTUBE,
                    tracks = tracks.map { it.toImportedCandidate() }
                )
            }
        }
    }

    private suspend fun resolveShortLink(url: String): String {
        val response = get(url)
        val finalUrl = response.call.request.url.toString()
        if (detect(finalUrl) != null) return finalUrl
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        SPOTIFY_REGEX.find(body)?.let { return "https://open.spotify.com/${it.groupValues[1]}/${it.groupValues[2]}" }
        DEEZER_REGEX.find(body)?.let { return "https://www.deezer.com/${it.groupValues[1]}/${it.groupValues[2]}" }
        return finalUrl
    }

    private suspend fun fetchText(url: String, providerLabel: String): String {
        val response = get(url)
        val code = response.status.value
        if (response.status.isSuccess()) return response.bodyAsText()
        when (code) {
            404 -> throw LinkImportException("$providerLabel item not found or private.")
            429 -> throw LinkImportException("$providerLabel is rate-limiting requests.")
            else -> throw LinkImportException("$providerLabel returned HTTP $code.")
        }
    }

    private suspend fun get(url: String): HttpResponse = try {
        httpClient.get(url) {
            header("User-Agent", USER_AGENT)
            header("Accept-Language", "en-US,en;q=0.9")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw LinkImportException("Network error while reaching $url")
    }

    private fun extractUrl(text: String): String? {
        if (text.startsWith("spotify:")) return text
        return URL_EXTRACT_REGEX.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
    }

    private fun Track.toImportedCandidate(): ImportedTrackCandidate = ImportedTrackCandidate(
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        externalId = ytId ?: id
    )

    private fun JsonElement.path(vararg keys: String): JsonElement? {
        var current: JsonElement? = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.longVal(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
        private val URL_EXTRACT_REGEX = Regex("""https?://\S+""")
        private val SPOTIFY_REGEX = Regex(
            """(?:open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?|spotify:)(playlist|album|track)[/:]([A-Za-z0-9]{22})"""
        )
        private val APPLE_REGEX = Regex(
            """music\.apple\.com/[a-z]{2}/(playlist|album)/(?:[^/?#]+/)?(pl\.[A-Za-z0-9-]+|\d+)"""
        )
        private val APPLE_TRACK_PARAM_REGEX = Regex("""[?&]i=(\d+)""")
        private val DEEZER_REGEX = Regex(
            """deezer\.com/(?:[a-z]{2}/)?(playlist|album|track)/(\d+)"""
        )
        private val YT_LIST_REGEX = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
        private val YT_BROWSE_REGEX = Regex("""music\.youtube\.com/(?:browse|playlist)/((?:VL|MPREb_)?[A-Za-z0-9_-]+)""")
        private val YT_VIDEO_REGEX = Regex("""(?:youtu\.be/|youtube\.com/watch\?(?:.*&)?v=|music\.youtube\.com/watch\?(?:.*&)?v=)([A-Za-z0-9_-]{11})""")
        private val SPOTIFY_NEXT_DATA_REGEX = Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
        private val APPLE_SERVER_DATA_REGEX = Regex(
            """<script[^>]*id="serialized-server-data"[^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
        private val SHORT_HOSTS = listOf("spotify.link", "spotify.app.link", "deezer.page.link", "link.deezer.com")
    }
}
