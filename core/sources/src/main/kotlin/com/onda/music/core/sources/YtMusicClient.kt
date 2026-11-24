package com.onda.music.core.sources

import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.HomeShelf
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SearchResultBundle
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val USER_AGENT_WEB =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

enum class YtClientKind(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val apiOrigin: String,
    val apiFormatVersion: String = "1",
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val clientScreen: String? = null,
    val platform: String? = null,
    val loginSupported: Boolean = false,
    val ownVisitorId: Boolean = false
) {
    VISIONOS(
        clientName = "VISIONOS",
        clientVersion = "1.04",
        clientId = "101",
        userAgent = "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; US)",
        apiOrigin = "https://www.youtube.com",
        apiFormatVersion = "2",
        osName = "visionOS",
        osVersion = "26.6.0.23O770",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1",
        clientScreen = "WATCH",
        platform = "MOBILE",
        ownVisitorId = true
    ),
    ANDROID_VR(
        clientName = "ANDROID_VR",
        clientVersion = "1.61.48",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
        apiOrigin = "https://music.youtube.com",
        osName = "Android",
        osVersion = "12",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = "32",
        platform = "MOBILE"
    ),
    IOS(
        clientName = "IOS",
        clientVersion = "21.03.1",
        clientId = "5",
        userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
        apiOrigin = "https://music.youtube.com",
        osName = "iOS",
        osVersion = "18.2.22C152",
        platform = "MOBILE"
    ),
    WEB_REMIX(
        clientName = "WEB_REMIX",
        clientVersion = "1.20260707.12.00",
        clientId = "67",
        userAgent = USER_AGENT_WEB,
        apiOrigin = "https://music.youtube.com",
        loginSupported = true
    ),
    WEB(
        clientName = "WEB",
        clientVersion = "2.20260708.00.00",
        clientId = "1",
        userAgent = USER_AGENT_WEB,
        apiOrigin = "https://www.youtube.com"
    ),
    TVHTML5(
        clientName = "TVHTML5",
        clientVersion = "5.20260707",
        clientId = "7",
        userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
        apiOrigin = "https://www.youtube.com",
        osName = "Tizen",
        osVersion = "2.4.0",
        deviceMake = "Samsung",
        deviceModel = "UKS9800",
        loginSupported = true
    );

    companion object {
        val defaultOrder: List<YtClientKind> = listOf(ANDROID_VR, IOS, VISIONOS, WEB_REMIX, TVHTML5)
    }
}

enum class YtSearchFilter(val params: String?) {
    ALL(null),
    TRACKS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    ALBUMS("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"),
    ARTISTS("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"),
    PLAYLISTS("EgeKAQQoAEABagoQAxAEEAoQCRAF")
}

@Singleton
class YtMusicClient @Inject constructor(
    @SourcesHttpClient private val httpClient: HttpClient,
    @SourcesOkHttp private val okHttpClient: OkHttpClient,
    @SourcesJson private val json: Json
) {
    @Volatile
    private var cookieHeader: String? = null

    @Volatile
    private var cookieMap: Map<String, String> = emptyMap()

    @Volatile
    private var visitorData: String? = null

    @Volatile
    private var gl: String = Locale.getDefault().country.ifBlank { "US" }

    @Volatile
    private var hl: String = Locale.getDefault().toLanguageTag().ifBlank { "en" }

    @Volatile
    private var newPipeInitialized = false

    private val clientVisitorIds = ConcurrentHashMap<YtClientKind, String>()

    fun setCookie(cookie: String?) {
        val cleaned = cookie?.trim()?.takeIf { it.isNotEmpty() }
        cookieHeader = cleaned
        cookieMap = if (cleaned == null) {
            emptyMap()
        } else {
            cleaned.split(';')
                .mapNotNull { entry ->
                    val idx = entry.indexOf('=')
                    if (idx <= 0) null else entry.substring(0, idx).trim() to entry.substring(idx + 1).trim()
                }
                .toMap()
        }
    }

    fun setLocale(countryCode: String, languageTag: String) {
        if (countryCode.isNotBlank()) gl = countryCode
        if (languageTag.isNotBlank()) hl = languageTag
    }

    suspend fun getHomeShelves(
        continuation: String? = null,
        params: String? = null,
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<List<HomeShelf>> = runCatching {
        val root = postInnerTube(
            endpoint = "browse",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                if (continuation == null) {
                    put("browseId", "FEmusic_home")
                    params?.let { put("params", it) }
                } else {
                    put("continuation", continuation)
                }
            },
            includeAuth = true
        )
        val shelves = parseHomeShelves(root).toMutableList()
        if (continuation == null && params == null) {
            getCharts(countryOverride = countryOverride, languageOverride = languageOverride).getOrNull()?.let { chartShelves ->
                shelves += chartShelves.take(2)
            }
            getNewReleases(countryOverride = countryOverride, languageOverride = languageOverride).getOrNull()?.takeIf { it.isNotEmpty() }?.let { albums ->
                shelves += HomeShelf(
                    id = "new_releases",
                    title = "New Releases",
                    albums = albums
                )
            }
        }
        shelves
    }

    suspend fun getCharts(
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<List<HomeShelf>> = runCatching {
        val root = postInnerTube(
            endpoint = "browse",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                put("browseId", "FEmusic_charts")
                put("params", "ggMGCgQIgAQ%3D")
            }
        )
        parseHomeShelves(root)
    }

    suspend fun getMoodCategories(
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<List<MoodCategory>> = runCatching {
        val root = postInnerTube(
            endpoint = "browse",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                put("browseId", "FEmusic_moods_and_genres")
            }
        )
        val sections = root.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")
            ?.firstOrNullObj()
            ?.obj("tabRenderer")
            ?.obj("content")
            ?.obj("sectionListRenderer")
            ?.arr("contents")
            ?: return@runCatching emptyList()

        sections.mapNotNull { it as? JsonObject }.flatMap { content ->
            content.obj("gridRenderer")?.arr("items").orEmpty().mapNotNull { item ->
                val nav = (item as? JsonObject)?.obj("musicNavigationButtonRenderer") ?: return@mapNotNull null
                val title = nav.obj("buttonText")?.runsText() ?: return@mapNotNull null
                val color = nav.obj("solid")?.longVal("leftStripeColor") ?: 0xFF7B61FF
                val browse = nav.obj("clickCommand")?.obj("browseEndpoint") ?: return@mapNotNull null
                val browseId = browse.str("browseId") ?: return@mapNotNull null
                val params = browse.str("params")
                MoodCategory(
                    id = "$browseId:${params.orEmpty()}",
                    title = title,
                    accentColorHex = color,
                    params = params
                )
            }
        }
    }

    suspend fun getMoodTracks(
        mood: MoodCategory,
        countryOverride: String? = null,
        languageOverride: String? = null,
        vietnameseHint: Boolean = false
    ): Result<List<Track>> = runCatching {
        val browseId = mood.id.substringBefore(':').takeIf { it.isNotBlank() && mood.id.contains(':') }
            ?: "FEmusic_moods_and_genres_category"
        val root = runCatching {
            postInnerTube(
                endpoint = "browse",
                client = YtClientKind.WEB_REMIX,
                body = buildJsonObject {
                    put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                    put("browseId", browseId)
                    mood.params?.let { put("params", it) }
                },
                includeAuth = true
            )
        }.getOrNull()

        val shelfTracks = if (root != null) {
            val carouselTracks = parseHomeShelves(root).flatMap { it.tracks }
            val listTracks = parseMoodListTracks(root)
            (carouselTracks + listTracks).distinctBy { it.ytId ?: it.id }
        } else {
            emptyList()
        }

        if (shelfTracks.isNotEmpty()) {
            return@runCatching shelfTracks
        }

        val query = if (vietnameseHint && !mood.title.contains("Việt", ignoreCase = true)) {
            "${mood.title} nhạc Việt"
        } else {
            "${mood.title} music"
        }
        searchTracks(
            query = query,
            countryOverride = countryOverride,
            languageOverride = languageOverride
        ).getOrThrow()
    }

    suspend fun getNewReleases(
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<List<Album>> = runCatching {
        val root = postInnerTube(
            endpoint = "browse",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                put("browseId", "FEmusic_new_releases_albums")
            }
        )
        val gridItems = root.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")
            ?.firstOrNullObj()
            ?.obj("tabRenderer")
            ?.obj("content")
            ?.obj("sectionListRenderer")
            ?.arr("contents")
            ?.firstOrNullObj()
            ?.obj("gridRenderer")
            ?.arr("items")
            ?: return@runCatching emptyList()

        gridItems.mapNotNull { element ->
            (element as? JsonObject)?.obj("musicTwoRowItemRenderer")?.let(::parseTwoRowAlbum)
        }
    }

    suspend fun search(
        query: String,
        filter: YtSearchFilter = YtSearchFilter.ALL,
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<SearchResultBundle> = runCatching {
        if (query.isBlank()) return@runCatching SearchResultBundle()
        val root = postInnerTube(
            endpoint = "search",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                put("query", query)
                filter.params?.let { put("params", it) }
            },
            includeAuth = true
        )
        parseSearchResults(root)
    }

    suspend fun searchTracks(
        query: String,
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<List<Track>> =
        search(query, YtSearchFilter.TRACKS, countryOverride, languageOverride).map { it.tracks }

    suspend fun searchAlbums(query: String): Result<List<Album>> =
        search(query, YtSearchFilter.ALBUMS).map { it.albums }

    suspend fun searchArtists(query: String): Result<List<Artist>> =
        search(query, YtSearchFilter.ARTISTS).map { it.artists }

    suspend fun searchPlaylists(query: String): Result<List<Playlist>> =
        search(query, YtSearchFilter.PLAYLISTS).map { it.playlists }

    suspend fun searchSuggestions(query: String): Result<List<String>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val root = postInnerTube(
            endpoint = "music/get_search_suggestions",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX))
                put("input", query)
            }
        )
        root.arr("contents")
            ?.firstOrNullObj()
            ?.obj("searchSuggestionsSectionRenderer")
            ?.arr("contents")
            ?.mapNotNull { item ->
                (item as? JsonObject)
                    ?.obj("searchSuggestionRenderer")
                    ?.obj("suggestion")
                    ?.runsText()
            }
            .orEmpty()
    }

    suspend fun getRelatedTracksForSeed(
        videoId: String,
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<List<Track>> = runCatching {
        val radioTracks = getRelatedQueue(
            videoId = videoId,
            playlistId = "RDAMVM$videoId",
            countryOverride = countryOverride,
            languageOverride = languageOverride
        ).getOrDefault(emptyList())

        val combined = if (radioTracks.size > 2) {
            radioTracks
        } else {
            val watchTracks = getRelatedQueue(
                videoId = videoId,
                playlistId = null,
                countryOverride = countryOverride,
                languageOverride = languageOverride
            ).getOrDefault(emptyList())
            (radioTracks + watchTracks).distinctBy { it.ytId ?: it.id }
        }
        combined.filter { !it.ytId.isNullOrBlank() }.distinctBy { it.ytId ?: it.id }
    }

    suspend fun getRelatedQueue(
        videoId: String,
        playlistId: String? = null,
        countryOverride: String? = null,
        languageOverride: String? = null
    ): Result<List<Track>> = runCatching {
        val root = postInnerTube(
            endpoint = "next",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                put("videoId", videoId)
                playlistId?.let { put("playlistId", it) }
            },
            includeAuth = true
        )
        val panel = root.obj("contents")
            ?.obj("singleColumnMusicWatchNextResultsRenderer")
            ?.obj("tabbedRenderer")
            ?.obj("watchNextTabbedResultsRenderer")
            ?.arr("tabs")
            ?.firstOrNullObj()
            ?.obj("tabRenderer")
            ?.obj("content")
            ?.obj("musicQueueRenderer")
            ?.obj("content")
            ?.obj("playlistPanelRenderer")
            ?: return@runCatching emptyList()

        val items = panel.arr("contents").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.obj("playlistPanelVideoRenderer")?.let(::parsePlaylistPanelTrack)
        }.toMutableList()

        val automixPlaylistId = panel.arr("contents")
            ?.lastOrNull()
            ?.let { it as? JsonObject }
            ?.obj("automixPreviewVideoRenderer")
            ?.obj("content")
            ?.obj("automixPlaylistVideoRenderer")
            ?.obj("navigationEndpoint")
            ?.obj("watchPlaylistEndpoint")
            ?.str("playlistId")

        if (automixPlaylistId != null && items.size <= 1) {
            val automixRoot = postInnerTube(
                endpoint = "next",
                client = YtClientKind.WEB_REMIX,
                body = buildJsonObject {
                    put("context", buildContext(YtClientKind.WEB_REMIX, countryOverride = countryOverride, languageOverride = languageOverride))
                    put("playlistId", automixPlaylistId)
                },
                includeAuth = true
            )
            val automixTracks = automixRoot.obj("contents")
                ?.obj("singleColumnMusicWatchNextResultsRenderer")
                ?.obj("tabbedRenderer")
                ?.obj("watchNextTabbedResultsRenderer")
                ?.arr("tabs")
                ?.firstOrNullObj()
                ?.obj("tabRenderer")
                ?.obj("content")
                ?.obj("musicQueueRenderer")
                ?.obj("content")
                ?.obj("playlistPanelRenderer")
                ?.arr("contents")
                .orEmpty()
                .mapNotNull { (it as? JsonObject)?.obj("playlistPanelVideoRenderer")?.let(::parsePlaylistPanelTrack) }
            val existingIds = items.mapTo(HashSet()) { it.ytId ?: it.id }
            automixTracks.filterTo(items) { (it.ytId ?: it.id) !in existingIds }
        }
        items
    }

    suspend fun getPlaylistTracks(playlistId: String): Result<Pair<Playlist, List<Track>>> = runCatching {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val root = postInnerTube(
            endpoint = "browse",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX))
                put("browseId", browseId)
            },
            includeAuth = true
        )
        val twoCol = root.obj("contents")?.obj("twoColumnBrowseResultsRenderer")
        val headerBase = twoCol?.arr("tabs")?.firstOrNullObj()
            ?.obj("tabRenderer")?.obj("content")?.obj("sectionListRenderer")
            ?.arr("contents")?.firstOrNullObj()
        val header = headerBase?.obj("musicResponsiveHeaderRenderer")
            ?: headerBase?.obj("musicEditablePlaylistDetailHeaderRenderer")
                ?.obj("header")?.obj("musicResponsiveHeaderRenderer")
        val title = header?.obj("title")?.runsText() ?: "YouTube Playlist"
        val thumbnail = header?.obj("thumbnail")?.obj("musicThumbnailRenderer")?.bestThumbnailUrl()
        val tracks = twoCol?.obj("secondaryContents")?.obj("sectionListRenderer")
            ?.arr("contents")?.firstOrNullObj()
            ?.obj("musicPlaylistShelfRenderer")?.arr("contents")
            .orEmpty()
            .mapNotNull { (it as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let(::parseResponsiveListTrack) }

        val normalizedId = playlistId.removePrefix("VL")
        Playlist(
            id = "yt_pl_$normalizedId",
            name = title,
            artworkUrl = thumbnail,
            trackCount = tracks.size,
            remoteId = normalizedId
        ) to tracks
    }

    suspend fun getAlbumTracks(browseId: String): Result<Pair<Album, List<Track>>> = runCatching {
        val root = postInnerTube(
            endpoint = "browse",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX))
                put("browseId", browseId)
            },
            includeAuth = true
        )
        val twoCol = root.obj("contents")?.obj("twoColumnBrowseResultsRenderer")
        val header = twoCol?.arr("tabs")?.firstOrNullObj()
            ?.obj("tabRenderer")?.obj("content")?.obj("sectionListRenderer")
            ?.arr("contents")?.firstOrNullObj()?.obj("musicResponsiveHeaderRenderer")
        val albumTitle = header?.obj("title")?.runsText() ?: "YouTube Album"
        val artistName = header?.obj("straplineTextOne")?.runsText().orEmpty()
        val cover = header?.obj("thumbnail")?.obj("musicThumbnailRenderer")?.bestThumbnailUrl()
        val playlistId = root.obj("microformat")?.obj("microformatDataRenderer")
            ?.str("urlCanonical")?.substringAfterLast('=')
        val tracks = if (playlistId != null) {
            getPlaylistTracks(playlistId).getOrNull()?.second.orEmpty().map {
                it.copy(
                    album = it.album ?: albumTitle,
                    albumId = browseId,
                    artist = it.artist.ifBlank { artistName },
                    artworkUrl = it.artworkUrl ?: cover
                )
            }
        } else {
            emptyList()
        }
        Album(
            id = browseId,
            title = albumTitle,
            artist = artistName,
            artworkUrl = cover,
            trackCount = tracks.size,
            source = SourceKind.YOUTUBE
        ) to tracks
    }

    suspend fun getTrackMetadata(videoId: String): Result<Track> = runCatching {
        val root = postInnerTube(
            endpoint = "player",
            client = YtClientKind.ANDROID_VR,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.ANDROID_VR))
                put("videoId", videoId)
            }
        )
        val details = root.obj("videoDetails") ?: throw IllegalStateException("Missing videoDetails for $videoId")
        val durationMs = details.str("lengthSeconds")?.toLongOrNull()?.times(1000L) ?: 0L
        val thumb = details.obj("thumbnail")?.arr("thumbnails")
            ?.mapNotNull { it as? JsonObject }
            ?.maxByOrNull { it.intVal("width") ?: 0 }
            ?.str("url")
        Track(
            id = "yt_$videoId",
            title = details.str("title") ?: videoId,
            artist = details.str("author").orEmpty(),
            durationMs = durationMs,
            artworkUrl = thumb,
            source = SourceKind.YOUTUBE,
            ytId = videoId
        )
    }

    suspend fun getLyrics(videoId: String): Result<String> = runCatching {
        val nextRoot = postInnerTube(
            endpoint = "next",
            client = YtClientKind.WEB_REMIX,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB_REMIX))
                put("videoId", videoId)
            },
            includeAuth = true
        )
        val tabs = nextRoot.obj("contents")
            ?.obj("singleColumnMusicWatchNextResultsRenderer")
            ?.obj("tabbedRenderer")
            ?.obj("watchNextTabbedResultsRenderer")
            ?.arr("tabs")
        val lyricsEndpoint = tabs?.getOrNull(1)?.let { it as? JsonObject }
            ?.obj("tabRenderer")?.obj("endpoint")?.obj("browseEndpoint")
        val browseId = lyricsEndpoint?.str("browseId")
        if (browseId != null) {
            val browseRoot = postInnerTube(
                endpoint = "browse",
                client = YtClientKind.WEB_REMIX,
                body = buildJsonObject {
                    put("context", buildContext(YtClientKind.WEB_REMIX))
                    put("browseId", browseId)
                    lyricsEndpoint.str("params")?.let { put("params", it) }
                },
                includeAuth = true
            )
            val text = browseRoot.obj("contents")
                ?.obj("sectionListRenderer")
                ?.arr("contents")
                ?.firstOrNull { (it as? JsonObject)?.obj("musicDescriptionShelfRenderer") != null }
                ?.let { it as? JsonObject }
                ?.obj("musicDescriptionShelfRenderer")
                ?.obj("description")
                ?.runsText()
            if (!text.isNullOrBlank()) return@runCatching text
        }
        getTranscriptLyrics(videoId).getOrThrow()
    }

    suspend fun resolveStreams(
        videoId: String,
        clientOrder: List<YtClientKind> = YtClientKind.defaultOrder
    ): Result<List<StreamOption>> = runCatching {
        var lastError: Throwable? = null
        for (client in clientOrder) {
            try {
                val streams = if (clientOrder.size > 1) {
                    withTimeout(5_500L) {
                        fetchPlayerStreams(videoId, client)
                    }
                } else {
                    fetchPlayerStreams(videoId, client)
                }
                if (streams.isNotEmpty()) return@runCatching streams
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        val newPipeStreams = extractWithNewPipe(videoId).getOrDefault(emptyList())
        if (newPipeStreams.isNotEmpty()) return@runCatching newPipeStreams
        throw lastError ?: IllegalStateException("No playable YouTube stream found for $videoId")
    }

    suspend fun resolveBestStream(
        videoId: String,
        preferHighestBitrate: Boolean = true,
        excludedClients: Set<YtClientKind> = emptySet()
    ): Result<StreamOption> = runCatching {
        val clients = YtClientKind.defaultOrder.filterNot { it in excludedClients }
            .ifEmpty { YtClientKind.defaultOrder }
        val streams = resolveStreams(videoId, clients).getOrThrow()
        if (preferHighestBitrate) {
            streams.maxByOrNull { it.bitrateKbps }
        } else {
            streams.sortedBy { it.bitrateKbps }.firstOrNull { it.bitrateKbps >= 64 } ?: streams.firstOrNull()
        } ?: throw IllegalStateException("No playable stream in resolved list for $videoId")
    }

    suspend fun extractWithNewPipe(videoId: String): Result<List<StreamOption>> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureNewPipeInitialized()
                val streamInfo = StreamInfo.getInfo(
                    NewPipe.getService(0),
                    "https://www.youtube.com/watch?v=$videoId"
                )
                val expiresAt = System.currentTimeMillis() + DEFAULT_STREAM_LIFE_MS
                streamInfo.audioStreams
                    .filter { it.content.isNotBlank() }
                    .map { audio ->
                        val kbps = audio.averageBitrate.takeIf { it > 0 } ?: 128
                        val mime = audio.format?.mimeType ?: "audio/mp4"
                        val codec = audio.codec ?: if (mime.contains("webm")) "opus" else "mp4a.40.2"
                        StreamOption(
                            url = audio.content,
                            mimeType = mime,
                            bitrateKbps = kbps,
                            codec = codec,
                            qualityLabel = "NewPipe · $kbps kbps",
                            expiresAtMs = expiresAt,
                            source = SourceKind.YOUTUBE
                        )
                    }
                    .sortedByDescending { it.bitrateKbps }
            }
        }

    private suspend fun getTranscriptLyrics(videoId: String): Result<String> = runCatching {
        val encodedParam = java.util.Base64.getEncoder().encodeToString(
            "\n${11.toChar()}$videoId".encodeToByteArray()
        )
        val root = postInnerTube(
            endpoint = "get_transcript",
            client = YtClientKind.WEB,
            body = buildJsonObject {
                put("context", buildContext(YtClientKind.WEB))
                put("params", encodedParam)
            }
        )
        val cueGroups = root.arr("actions")
            ?.firstOrNullObj()
            ?.obj("updateEngagementPanelAction")
            ?.obj("content")
            ?.obj("transcriptRenderer")
            ?.obj("body")
            ?.obj("transcriptBodyRenderer")
            ?.arr("cueGroups")
            ?: throw IllegalStateException("No transcript cues for $videoId")

        val lines = cueGroups.mapNotNull { group ->
            val cue = (group as? JsonObject)
                ?.obj("transcriptCueGroupRenderer")
                ?.arr("cues")
                ?.firstOrNullObj()
                ?.obj("transcriptCueRenderer")
                ?: return@mapNotNull null
            val offsetMs = cue.str("startOffsetMs")?.toLongOrNull() ?: cue.longVal("startOffsetMs") ?: return@mapNotNull null
            val text = cue.obj("cue")?.str("simpleText")?.trim('♪', ' ') ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            val minutes = offsetMs / 60_000L
            val seconds = (offsetMs / 1000L) % 60L
            val millis = offsetMs % 1000L
            String.format(Locale.US, "[%02d:%02d.%03d]%s", minutes, seconds, millis, text)
        }
        check(lines.isNotEmpty()) { "Transcript cues empty for $videoId" }
        lines.joinToString("\n")
    }

    private suspend fun fetchPlayerStreams(videoId: String, client: YtClientKind): List<StreamOption> {
        val visitor = if (client.ownVisitorId) ensureClientVisitorId(client) else visitorData
        val root = postInnerTube(
            endpoint = "player",
            client = client,
            body = buildJsonObject {
                put("context", buildContext(client, visitor))
                put("videoId", videoId)
            },
            visitorOverride = visitor,
            includeAuth = client.loginSupported
        )
        val status = root.obj("playabilityStatus")?.str("status")
        if (status != "OK") return emptyList()
        val streamingData = root.obj("streamingData") ?: return emptyList()
        val expiresSeconds = streamingData.str("expiresInSeconds")?.toLongOrNull() ?: 1800L
        val expiresAtMs = System.currentTimeMillis() + expiresSeconds * 1000L
        val adaptive = streamingData.arr("adaptiveFormats").orEmpty()
        val combined = streamingData.arr("formats").orEmpty()

        val audioOptions = (adaptive + combined).mapNotNull { element ->
            val format = element as? JsonObject ?: return@mapNotNull null
            val rawMime = format.str("mimeType") ?: return@mapNotNull null
            if (!rawMime.startsWith("audio/")) return@mapNotNull null
            val directUrl = format.str("url")
            val resolvedUrl = directUrl ?: format.str("signatureCipher")?.let { cipher ->
                deobfuscateCipherUrl(videoId, cipher)
            } ?: return@mapNotNull null
            val bitrateBps = format.intVal("bitrate") ?: format.intVal("averageBitrate") ?: 128_000
            val bitrateKbps = (bitrateBps / 1000).coerceAtLeast(32)
            val mimeType = rawMime.substringBefore(';').trim()
            val codec = CODEC_REGEX.find(rawMime)?.groupValues?.get(1) ?: mimeType.substringAfter('/')
            StreamOption(
                url = resolvedUrl,
                mimeType = mimeType,
                bitrateKbps = bitrateKbps,
                codec = codec,
                qualityLabel = "${client.clientName} · $bitrateKbps kbps",
                expiresAtMs = expiresAtMs,
                source = SourceKind.YOUTUBE
            )
        }
        return audioOptions.sortedByDescending { it.bitrateKbps }
    }

    private fun deobfuscateCipherUrl(videoId: String, signatureCipher: String): String? = runCatching {
        ensureNewPipeInitialized()
        val params = parseQueryString(signatureCipher)
        val obfuscatedSig = params["s"] ?: return null
        val sigParam = params["sp"] ?: "sig"
        val baseUrl = params["url"] ?: return null
        val builder = URLBuilder(baseUrl)
        builder.parameters[sigParam] = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSig)
        YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, builder.toString())
    }.getOrNull()

    private suspend fun ensureClientVisitorId(client: YtClientKind): String? {
        clientVisitorIds[client]?.let { return it }
        val minted = runCatching {
            val root = postInnerTube(
                endpoint = "visitor_id",
                client = client,
                body = buildJsonObject {
                    put("context", buildContext(client, visitorOverride = null))
                },
                visitorOverride = null
            )
            root.obj("responseContext")?.str("visitorData")
        }.getOrNull()
        if (!minted.isNullOrBlank()) {
            clientVisitorIds[client] = minted
        }
        return minted
    }

    private suspend fun postInnerTube(
        endpoint: String,
        client: YtClientKind,
        body: JsonObject,
        visitorOverride: String? = visitorData,
        includeAuth: Boolean = false
    ): JsonObject {
        val url = "${client.apiOrigin}/youtubei/v1/$endpoint"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            parameter("prettyPrint", false)
            header("User-Agent", client.userAgent)
            header("Accept", "application/json")
            header("X-Goog-Api-Format-Version", client.apiFormatVersion)
            header("X-YouTube-Client-Name", client.clientId)
            header("X-YouTube-Client-Version", client.clientVersion)
            if (client.platform == null) {
                header("X-Origin", client.apiOrigin)
                header("Referer", "${client.apiOrigin}/")
            }
            visitorOverride?.let { header("X-Goog-Visitor-Id", it) }
            if (includeAuth && client.loginSupported) {
                cookieHeader?.let { cookie ->
                    header("Cookie", cookie)
                    val sapisid = cookieMap["SAPISID"]
                    if (sapisid != null) {
                        val ts = System.currentTimeMillis() / 1000L
                        val hash = sha1Hex("$ts $sapisid ${client.apiOrigin}")
                        header("Authorization", "SAPISIDHASH ${ts}_$hash")
                    }
                }
            }
            setBody(body)
        }
        check(response.status.isSuccess()) { "InnerTube $endpoint failed: HTTP ${response.status.value}" }
        return json.parseToJsonElement(response.bodyAsText()) as JsonObject
    }

    private fun buildContext(
        client: YtClientKind,
        visitorOverride: String? = visitorData,
        countryOverride: String? = null,
        languageOverride: String? = null
    ): JsonObject =
        buildJsonObject {
            putJsonObject("client") {
                put("clientName", client.clientName)
                put("clientVersion", client.clientVersion)
                put("gl", countryOverride?.takeIf { it.isNotBlank() } ?: gl)
                put("hl", languageOverride?.takeIf { it.isNotBlank() } ?: hl)
                client.clientScreen?.let { put("clientScreen", it) }
                client.platform?.let { put("platform", it) }
                client.osName?.let { put("osName", it) }
                client.osVersion?.let { put("osVersion", it) }
                client.deviceMake?.let { put("deviceMake", it) }
                client.deviceModel?.let { put("deviceModel", it) }
                client.androidSdkVersion?.let { put("androidSdkVersion", it) }
                visitorOverride?.let { put("visitorData", it) }
            }
        }

    private fun parseHomeShelves(root: JsonObject): List<HomeShelf> {
        val tabContents = root.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")
            ?.firstOrNullObj()
            ?.obj("tabRenderer")
            ?.obj("content")
            ?.obj("sectionListRenderer")
            ?.arr("contents")
            ?: root.obj("continuationContents")?.obj("sectionListContinuation")?.arr("contents")
            ?: return emptyList()

        return tabContents.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val carousel = obj.obj("musicCarouselShelfRenderer") ?: return@mapIndexedNotNull null
            val header = carousel.obj("header")?.obj("musicCarouselShelfBasicHeaderRenderer")
            val title = header?.obj("title")?.runsText() ?: return@mapIndexedNotNull null
            val subtitle = header.obj("strapline")?.runsText()
            val tracks = mutableListOf<Track>()
            val albums = mutableListOf<Album>()
            carousel.arr("contents").orEmpty().forEach { entry ->
                val entryObj = entry as? JsonObject ?: return@forEach
                entryObj.obj("musicTwoRowItemRenderer")?.let { twoRow ->
                    parseTwoRowTrack(twoRow)?.let { tracks += it }
                        ?: parseTwoRowAlbum(twoRow)?.let { albums += it }
                }
                entryObj.obj("musicResponsiveListItemRenderer")?.let { row ->
                    parseResponsiveListTrack(row)?.let { tracks += it }
                }
            }
            if (tracks.isEmpty() && albums.isEmpty()) return@mapIndexedNotNull null
            HomeShelf(
                id = "shelf_${index}_${title.lowercase().replace(' ', '_')}",
                title = title,
                subtitle = subtitle,
                tracks = tracks,
                albums = albums
            )
        }
    }

    private fun parseMoodListTracks(root: JsonObject): List<Track> {
        val tabContents = root.obj("contents")
            ?.obj("singleColumnBrowseResultsRenderer")
            ?.arr("tabs")
            ?.firstOrNullObj()
            ?.obj("tabRenderer")
            ?.obj("content")
            ?.obj("sectionListRenderer")
            ?.arr("contents")
            ?: return emptyList()

        return tabContents.mapNotNull { it as? JsonObject }.flatMap { section ->
            val shelfRows = section.obj("musicShelfRenderer")?.arr("contents").orEmpty()
            val playlistRows = section.obj("musicPlaylistShelfRenderer")?.arr("contents").orEmpty()
            (shelfRows + playlistRows).mapNotNull { row ->
                (row as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let(::parseResponsiveListTrack)
            }
        }
    }

    private fun parseSearchResults(root: JsonObject): SearchResultBundle {
        val contents = root.obj("contents")
            ?.obj("tabbedSearchResultsRenderer")
            ?.arr("tabs")
            ?.firstOrNullObj()
            ?.obj("tabRenderer")
            ?.obj("content")
            ?.obj("sectionListRenderer")
            ?.arr("contents")
            ?: return SearchResultBundle()

        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<Album>()
        val artists = mutableListOf<Artist>()
        val playlists = mutableListOf<Playlist>()

        contents.forEach { section ->
            val secObj = section as? JsonObject ?: return@forEach
            secObj.obj("musicCardShelfRenderer")?.let { card ->
                parseCardShelfItem(card, tracks, albums, artists, playlists)
                card.arr("contents").orEmpty().forEach { row ->
                    (row as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let {
                        classifyResponsiveRow(it, tracks, albums, artists, playlists)
                    }
                }
            }
            secObj.obj("musicShelfRenderer")?.arr("contents").orEmpty().forEach { row ->
                (row as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let {
                    classifyResponsiveRow(it, tracks, albums, artists, playlists)
                }
            }
            secObj.obj("itemSectionRenderer")?.arr("contents").orEmpty().forEach { row ->
                (row as? JsonObject)?.obj("musicResponsiveListItemRenderer")?.let {
                    classifyResponsiveRow(it, tracks, albums, artists, playlists)
                }
            }
        }

        return SearchResultBundle(
            tracks = tracks.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id }
        )
    }

    private fun parseCardShelfItem(
        card: JsonObject,
        tracks: MutableList<Track>,
        albums: MutableList<Album>,
        artists: MutableList<Artist>,
        playlists: MutableList<Playlist>
    ) {
        val title = card.obj("title")?.runsText() ?: return
        val onTap = card.obj("onTap")
        val thumb = card.obj("thumbnail")?.obj("musicThumbnailRenderer")?.bestThumbnailUrl()
        val videoId = onTap?.obj("watchEndpoint")?.str("videoId")
        if (videoId != null) {
            tracks += Track(
                id = "yt_$videoId",
                title = title,
                artist = card.obj("subtitle")?.runsText().orEmpty(),
                artworkUrl = thumb,
                source = SourceKind.YOUTUBE,
                ytId = videoId
            )
            return
        }
        val browseId = onTap?.obj("browseEndpoint")?.str("browseId") ?: return
        when {
            browseId.startsWith("UC") -> artists += Artist(id = browseId, name = title, thumbnailUrl = thumb)
            browseId.startsWith("MPREb_") -> albums += Album(
                id = browseId,
                title = title,
                artist = card.obj("subtitle")?.runsText().orEmpty(),
                artworkUrl = thumb
            )
            browseId.startsWith("VL") -> playlists += Playlist(
                id = "yt_pl_${browseId.removePrefix("VL")}",
                name = title,
                artworkUrl = thumb,
                remoteId = browseId.removePrefix("VL")
            )
        }
    }

    private fun classifyResponsiveRow(
        row: JsonObject,
        tracks: MutableList<Track>,
        albums: MutableList<Album>,
        artists: MutableList<Artist>,
        playlists: MutableList<Playlist>
    ) {
        val videoId = row.obj("playlistItemData")?.str("videoId")
            ?: row.obj("overlay")?.obj("musicItemThumbnailOverlayRenderer")
                ?.obj("content")?.obj("musicPlayButtonRenderer")
                ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
        if (videoId != null) {
            parseResponsiveListTrack(row)?.let { tracks += it }
            return
        }
        val browseId = row.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId") ?: return
        val pageType = row.obj("navigationEndpoint")?.obj("browseEndpoint")
            ?.obj("browseEndpointContextSupportedConfigs")
            ?.obj("browseEndpointContextMusicConfig")
            ?.str("pageType")
        val title = row.flexColumnText(0) ?: return
        val subtitle = row.flexColumnText(1).orEmpty()
        val thumb = row.obj("thumbnail")?.obj("musicThumbnailRenderer")?.bestThumbnailUrl()
        when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") -> {
                artists += Artist(id = browseId, name = title, thumbnailUrl = thumb)
            }
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_") -> {
                albums += Album(
                    id = browseId,
                    title = title,
                    artist = subtitle.substringAfter("•").trim().ifBlank { subtitle },
                    artworkUrl = thumb,
                    source = SourceKind.YOUTUBE
                )
            }
            pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL") -> {
                val cleanId = browseId.removePrefix("VL")
                playlists += Playlist(
                    id = "yt_pl_$cleanId",
                    name = title,
                    artworkUrl = thumb,
                    remoteId = cleanId
                )
            }
        }
    }

    private fun parseResponsiveListTrack(row: JsonObject): Track? {
        val videoId = row.obj("playlistItemData")?.str("videoId")
            ?: row.obj("overlay")?.obj("musicItemThumbnailOverlayRenderer")
                ?.obj("content")?.obj("musicPlayButtonRenderer")
                ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: return null
        val title = row.flexColumnText(0) ?: return null
        val subtitleRuns = row.flexColumnRuns(1).orEmpty()
        val artistNames = mutableListOf<String>()
        var albumName: String? = null
        var albumId: String? = null
        var artistId: String? = null
        var durationMs = 0L

        for (run in subtitleRuns) {
            val text = run.str("text")?.trim().orEmpty()
            if (text.isEmpty() || text == "•" || text == "·" || text == "&" || text == ",") continue
            val browseId = run.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
            val parsedDuration = parseDurationMs(text)
            when {
                parsedDuration != null -> durationMs = parsedDuration
                browseId != null && browseId.startsWith("MPREb_") -> {
                    albumName = text
                    albumId = browseId
                }
                browseId != null && browseId.startsWith("UC") -> {
                    artistNames += text
                    if (artistId == null) artistId = browseId
                }
                text.equals("Song", true) || text.equals("Video", true) -> Unit
                albumName == null && artistNames.isEmpty() -> artistNames += text
                else -> Unit
            }
        }
        val thumb = row.obj("thumbnail")?.obj("musicThumbnailRenderer")?.bestThumbnailUrl()
        val fixedDuration = row.arr("fixedColumns")
            ?.firstOrNullObj()
            ?.obj("musicResponsiveListItemFixedColumnRenderer")
            ?.obj("text")
            ?.runsText()
            ?.let(::parseDurationMs)
        return Track(
            id = "yt_$videoId",
            title = title,
            artist = artistNames.joinToString(", "),
            album = albumName,
            albumId = albumId,
            artistId = artistId,
            durationMs = fixedDuration ?: durationMs,
            artworkUrl = thumb,
            source = SourceKind.YOUTUBE,
            ytId = videoId
        )
    }

    private fun parseTwoRowTrack(twoRow: JsonObject): Track? {
        val videoId = twoRow.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId") ?: return null
        val title = twoRow.obj("title")?.runsText() ?: return null
        val subtitleRuns = twoRow.obj("subtitle")?.arr("runs").orEmpty().mapNotNull { it as? JsonObject }
        val artist = subtitleRuns
            .mapNotNull { it.str("text")?.trim() }
            .filter { it.isNotEmpty() && it != "•" && it != "·" && !it.equals("Song", true) && !it.equals("Video", true) }
            .joinToString(", ")
        val thumb = twoRow.obj("thumbnailRenderer")?.obj("musicThumbnailRenderer")?.bestThumbnailUrl()
        return Track(
            id = "yt_$videoId",
            title = title,
            artist = artist,
            artworkUrl = thumb,
            source = SourceKind.YOUTUBE,
            ytId = videoId
        )
    }

    private fun parseTwoRowAlbum(twoRow: JsonObject): Album? {
        val browseId = twoRow.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId") ?: return null
        if (!browseId.startsWith("MPREb_")) return null
        val title = twoRow.obj("title")?.runsText() ?: return null
        val subtitleRuns = twoRow.obj("subtitle")?.arr("runs").orEmpty().mapNotNull { it as? JsonObject }
        val year = subtitleRuns.lastOrNull()?.str("text")?.toIntOrNull()
        val artist = subtitleRuns
            .mapNotNull { it.str("text")?.trim() }
            .filter { it.isNotEmpty() && it != "•" && it != "·" && it != year?.toString() && !it.equals("Album", true) && !it.equals("Single", true) && !it.equals("EP", true) }
            .joinToString(", ")
        val thumb = twoRow.obj("thumbnailRenderer")?.obj("musicThumbnailRenderer")?.bestThumbnailUrl()
        return Album(
            id = browseId,
            title = title,
            artist = artist,
            year = year,
            artworkUrl = thumb,
            source = SourceKind.YOUTUBE
        )
    }

    private fun parsePlaylistPanelTrack(renderer: JsonObject): Track? {
        val videoId = renderer.str("videoId") ?: return null
        val title = renderer.obj("title")?.runsText() ?: return null
        val runs = renderer.obj("longBylineText")?.arr("runs").orEmpty().mapNotNull { it as? JsonObject }
        val artists = mutableListOf<String>()
        var album: String? = null
        var albumId: String? = null
        for (run in runs) {
            val text = run.str("text")?.trim().orEmpty()
            if (text.isEmpty() || text == "•" || text == "·" || text == "&" || text == ",") continue
            val browseId = run.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
            when {
                browseId != null && browseId.startsWith("MPREb_") -> {
                    album = text
                    albumId = browseId
                }
                browseId != null -> artists += text
                parseDurationMs(text) == null && text.toIntOrNull() == null && artists.isEmpty() -> artists += text
            }
        }
        val durationMs = renderer.obj("lengthText")?.runsText()?.let(::parseDurationMs) ?: 0L
        val thumb = renderer.obj("thumbnail")?.arr("thumbnails")
            ?.mapNotNull { it as? JsonObject }
            ?.maxByOrNull { it.intVal("width") ?: 0 }
            ?.str("url")
        return Track(
            id = "yt_$videoId",
            title = title,
            artist = artists.joinToString(", "),
            album = album,
            albumId = albumId,
            durationMs = durationMs,
            artworkUrl = thumb,
            source = SourceKind.YOUTUBE,
            ytId = videoId
        )
    }

    private fun ensureNewPipeInitialized() {
        if (newPipeInitialized) return
        synchronized(this) {
            if (newPipeInitialized) return
            NewPipe.init(OkHttpNewPipeDownloader(okHttpClient))
            newPipeInitialized = true
        }
    }

    private class OkHttpNewPipeDownloader(
        private val client: OkHttpClient
    ) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .addHeader("User-Agent", USER_AGENT_WEB)
            request.headers().forEach { (name, values) ->
                if (values.size > 1) {
                    builder.removeHeader(name)
                    values.forEach { builder.addHeader(name, it) }
                } else if (values.size == 1) {
                    builder.header(name, values[0])
                }
            }
            val resp = client.newCall(builder.build()).execute()
            if (resp.code == 429) {
                resp.close()
                throw ReCaptchaException("Rate limited", request.url())
            }
            val bodyText = resp.body.string()
            val finalUrl = resp.request.url.toString()
            return Response(resp.code, resp.message, resp.headers.toMultimap(), bodyText, finalUrl)
        }
    }

    private fun parseDurationMs(text: String): Long? {
        val parts = text.trim().split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        val seconds = if (numbers.size == 2) {
            numbers[0] * 60L + numbers[1]
        } else {
            numbers[0] * 3600L + numbers[1] * 60L + numbers[2]
        }
        return seconds * 1000L
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.intVal(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.longVal(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonArray.firstOrNullObj(): JsonObject? = firstOrNull() as? JsonObject
    private fun JsonObject.runsText(): String? =
        arr("runs")?.mapNotNull { (it as? JsonObject)?.str("text") }?.joinToString("")?.takeIf { it.isNotEmpty() }

    private fun JsonObject.flexColumnRuns(index: Int): List<JsonObject>? =
        arr("flexColumns")
            ?.getOrNull(index)
            ?.let { it as? JsonObject }
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")
            ?.arr("runs")
            ?.mapNotNull { it as? JsonObject }

    private fun JsonObject.flexColumnText(index: Int): String? =
        flexColumnRuns(index)?.mapNotNull { it.str("text") }?.joinToString("")?.takeIf { it.isNotEmpty() }

    private fun JsonObject.bestThumbnailUrl(): String? =
        obj("thumbnail")?.arr("thumbnails")
            ?.mapNotNull { it as? JsonObject }
            ?.maxByOrNull { it.intVal("width") ?: 0 }
            ?.str("url")

    companion object {
        private const val DEFAULT_STREAM_LIFE_MS = 5 * 60_000L
        private val CODEC_REGEX = Regex("""codecs="([^"]+)"""")
    }
}
