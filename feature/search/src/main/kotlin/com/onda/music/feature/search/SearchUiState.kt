package com.onda.music.feature.search

import androidx.compose.runtime.Immutable
import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.ImportedCollection
import com.onda.music.core.model.ImportedTrackCandidate
import com.onda.music.core.model.LinkProvider
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.ParsedLink
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SearchResultBundle
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.sources.TrackMatcher
import java.util.Locale

enum class SearchSourceFilter(val label: String) {
    ALL("All"),
    YOUTUBE("YT Music"),
    MONOCHROME("Monochrome FLAC"),
    LOCAL("Local")
}

enum class LinkImportStage {
    IDLE,
    FETCHING,
    MATCHING,
    SAVING,
    SUCCESS,
    ERROR
}

@Immutable
data class LinkImportProgress(
    val stage: LinkImportStage = LinkImportStage.IDLE,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val matchedCount: Int = 0,
    val collectionTitle: String? = null,
    val artworkUrl: String? = null,
    val savedPlaylist: Playlist? = null,
    val matchedTracks: List<Track> = emptyList(),
    val errorMessage: String? = null
) {
    val fraction: Float
        get() = when (stage) {
            LinkImportStage.IDLE -> 0f
            LinkImportStage.FETCHING -> 0.1f
            LinkImportStage.MATCHING -> if (totalCount <= 0) 0.5f else 0.1f + 0.8f * (processedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
            LinkImportStage.SAVING -> 0.95f
            LinkImportStage.SUCCESS -> 1f
            LinkImportStage.ERROR -> 0f
        }

    val isBusy: Boolean
        get() = stage == LinkImportStage.FETCHING ||
            stage == LinkImportStage.MATCHING ||
            stage == LinkImportStage.SAVING
}

sealed interface DrillDownTarget {
    data class AlbumTarget(val album: Album) : DrillDownTarget
    data class PlaylistTarget(val playlist: Playlist) : DrillDownTarget
    data class MoodTarget(val mood: MoodCategory) : DrillDownTarget
}

@Immutable
data class DrillDownState(
    val target: DrillDownTarget? = null,
    val title: String = "",
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isVisible: Boolean get() = target != null
}

@Immutable
data class VisibleSearchResults(
    val monochromeTracks: List<Track> = emptyList(),
    val ytTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<Artist> = emptyList()
) {
    val isEmpty: Boolean
        get() = monochromeTracks.isEmpty() &&
            ytTracks.isEmpty() &&
            localTracks.isEmpty() &&
            albums.isEmpty() &&
            playlists.isEmpty() &&
            artists.isEmpty()
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val isSubmitted: Boolean = false,
    val activeFilter: SearchSourceFilter = SearchSourceFilter.ALL,
    val monochromeEnabled: Boolean = true,
    val languageCode: String = "en",
    val glassBlurEnabled: Boolean = true,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val ytResults: SearchResultBundle = SearchResultBundle(),
    val monochromeTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val moodCategories: List<MoodCategory> = emptyList(),
    val isLoadingMoods: Boolean = false,
    val moodError: String? = null,
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val hasMiniPlayer: Boolean = false,
    val linkSheetVisible: Boolean = false,
    val linkInput: String = "",
    val detectedProvider: LinkProvider? = null,
    val linkImportProgress: LinkImportProgress = LinkImportProgress(),
    val drillDownState: DrillDownState = DrillDownState()
) {
    val visibleResults: VisibleSearchResults
        get() = SearchLogic.filterResults(
            filter = activeFilter,
            ytResults = ytResults,
            monochromeTracks = monochromeTracks,
            localTracks = localTracks
        )
}

@Immutable
data class SearchStrings(
    val isVietnamese: Boolean,
    val screenTitle: String,
    val screenSubtitle: String,
    val settingsDescription: String,
    val backDescription: String,
    val searchPlaceholder: String,
    val clearSearchDescription: String,
    val filterAll: String,
    val filterYoutube: String,
    val filterMonochrome: String,
    val filterLocal: String,
    val importLinkTitle: String,
    val importLinkSubtitle: String,
    val openLinkImporterDescription: String,
    val recentSearchesTitle: String,
    val clearAction: String,
    val removePrefix: String,
    val moodsAndGenresTitle: String,
    val moodsAndGenresSubtitle: String,
    val loadingMoods: String,
    val noMoodsAvailable: String,
    val retryMoodsDescription: String,
    val trackFallbackLabel: String,
    val likeDescription: String,
    val unlikeDescription: String,
    val badgeFlac: String,
    val badgeLocal: String,
    val badgeYt: String,
    val albumFallbackLabel: String,
    val ytMusicPlaylistSubtitle: String,
    val inspectPlaylistDescription: String,
    val pausedDescription: String,
    val focusedEmptyHint: String,
    val searchingAcrossSources: String,
    val retryAction: String,
    val monochromeHeader: String,
    val ytTracksHeader: String,
    val albumsHeader: String,
    val playlistsHeader: String,
    val artistsHeader: String,
    val localLibraryHeader: String,
    val playAction: String,
    val playAllAction: String,
    val shuffleAction: String,
    val closeCollectionDescription: String,
    val loadingTracks: String,
    val emptyCollectionMessage: String,
    val playlistLabel: String,
    val moodStationSubtitle: String,
    val moodCategoriesErrorFallback: String,
    val moodTracksErrorFallback: String,
    val searchFailedFallback: String,
    val albumTracksErrorFallback: String,
    val playlistTracksErrorFallback: String,
    val importSheetSubtitle: String,
    val closeDescription: String,
    val clearLinkDescription: String,
    val pasteLinkDescription: String,
    val fetchAndSavePlaylist: String,
    val importFailedFallback: String,
    val readingLink: String,
    val fetchingMetadata: String,
    val savingToLibrary: String,
    val importedPlaylistFallback: String,
    val inspectAction: String,
    val importAnotherLink: String,
    val tryAgainAction: String,
    val emptyLinkError: String,
    val importLinkFailedFallback: String
) {
    fun filterLabel(filter: SearchSourceFilter): String = when (filter) {
        SearchSourceFilter.ALL -> filterAll
        SearchSourceFilter.YOUTUBE -> filterYoutube
        SearchSourceFilter.MONOCHROME -> filterMonochrome
        SearchSourceFilter.LOCAL -> filterLocal
    }

    fun trackCountLabel(count: Int): String = if (isVietnamese) "$count bài hát" else "$count tracks"

    fun losslessMatchCountLabel(count: Int): String = if (isVietnamese) {
        "$count bản khớp lossless"
    } else {
        "$count lossless matches"
    }

    fun localCountLabel(count: Int): String = if (isVietnamese) "$count trên thiết bị" else "$count on device"

    fun removeHistoryDescription(query: String): String = "$removePrefix $query"

    fun emptyResultsLabel(filterLabel: String, query: String): String = if (isVietnamese) {
        "Không tìm thấy kết quả trong $filterLabel cho \"$query\""
    } else {
        "No results found in $filterLabel for \"$query\""
    }

    fun matchingProgressLabel(processed: Int, total: Int, matched: Int): String = if (isVietnamese) {
        "Đang khớp $processed / $total (khớp $matched)"
    } else {
        "Matching $processed / $total ($matched matched)"
    }

    fun savedTracksSummary(matched: Int, total: Int): String = if (isVietnamese) {
        "Đã lưu $matched / $total bài hát"
    } else {
        "Saved $matched of $total tracks"
    }

    fun importedFromProvider(providerName: String): String = if (isVietnamese) {
        "Nhập từ $providerName"
    } else {
        "Imported from $providerName"
    }

    fun noMatchingTracksFound(title: String): String = if (isVietnamese) {
        "Không tìm thấy bài hát khớp trên YT Music hoặc Monochrome cho \"$title\"."
    } else {
        "No matching tracks found on YT Music or Monochrome for \"$title\"."
    }
}

internal fun resolveSearchStrings(languageCode: String): SearchStrings {
    val isVi = languageCode.startsWith("vi", ignoreCase = true)
    return if (isVi) {
        SearchStrings(
            isVietnamese = true,
            screenTitle = "Tìm kiếm",
            screenSubtitle = "YT Music, Monochrome FLAC & Nội bộ",
            settingsDescription = "Cài đặt",
            backDescription = "Quay lại",
            searchPlaceholder = "Bài hát, album, nghệ sĩ hoặc lời bài hát",
            clearSearchDescription = "Xóa tìm kiếm",
            filterAll = "Tất cả",
            filterYoutube = "YT Music",
            filterMonochrome = "Monochrome FLAC",
            filterLocal = "Nội bộ",
            importLinkTitle = "Nhập liên kết playlist",
            importLinkSubtitle = "Spotify, Apple Music, Deezer & YouTube Music",
            openLinkImporterDescription = "Mở trình nhập liên kết",
            recentSearchesTitle = "Tìm kiếm gần đây",
            clearAction = "Xóa",
            removePrefix = "Xóa",
            moodsAndGenresTitle = "Tâm trạng & Thể loại",
            moodsAndGenresSubtitle = "Khám phá các đài YouTube Music tuyển chọn",
            loadingMoods = "Đang tải tâm trạng và thể loại",
            noMoodsAvailable = "Hiện không có danh mục tâm trạng nào.",
            retryMoodsDescription = "Thử tải lại tâm trạng",
            trackFallbackLabel = "Bài hát",
            likeDescription = "Thích",
            unlikeDescription = "Bỏ thích",
            badgeFlac = "FLAC",
            badgeLocal = "NỘI BỘ",
            badgeYt = "YT",
            albumFallbackLabel = "Album",
            ytMusicPlaylistSubtitle = "Playlist YouTube Music",
            inspectPlaylistDescription = "Xem playlist",
            pausedDescription = "Đã tạm dừng",
            focusedEmptyHint = "Nhập tên bài hát, album, nghệ sĩ hoặc lời để tìm kiếm.",
            searchingAcrossSources = "Đang tìm trên các nguồn",
            retryAction = "Thử lại",
            monochromeHeader = "Monochrome FLAC",
            ytTracksHeader = "Bài hát YouTube Music",
            albumsHeader = "Album",
            playlistsHeader = "Playlist",
            artistsHeader = "Nghệ sĩ",
            localLibraryHeader = "Thư viện nội bộ",
            playAction = "Phát",
            playAllAction = "Phát tất cả",
            shuffleAction = "Trộn bài",
            closeCollectionDescription = "Đóng danh sách",
            loadingTracks = "Đang tải danh sách bài hát",
            emptyCollectionMessage = "Không tìm thấy bài hát nào phát được trong danh sách này.",
            playlistLabel = "Playlist",
            moodStationSubtitle = "Đài tuyển chọn YouTube Music",
            moodCategoriesErrorFallback = "Không thể tải danh mục tâm trạng và thể loại.",
            moodTracksErrorFallback = "Không thể tải bài hát cho tâm trạng này.",
            searchFailedFallback = "Tìm kiếm thất bại.",
            albumTracksErrorFallback = "Không thể tải danh sách bài hát của album.",
            playlistTracksErrorFallback = "Không thể tải danh sách bài hát của playlist.",
            importSheetSubtitle = "Khớp bài hát với YT Music & Monochrome FLAC",
            closeDescription = "Đóng",
            clearLinkDescription = "Xóa liên kết",
            pasteLinkDescription = "Dán liên kết",
            fetchAndSavePlaylist = "Lấy & Lưu Playlist",
            importFailedFallback = "Nhập playlist thất bại.",
            readingLink = "Đang đọc liên kết...",
            fetchingMetadata = "Đang lấy thông tin playlist",
            savingToLibrary = "Đang lưu playlist vào Thư viện",
            importedPlaylistFallback = "Playlist đã nhập",
            inspectAction = "Xem chi tiết",
            importAnotherLink = "Nhập liên kết khác",
            tryAgainAction = "Thử lại",
            emptyLinkError = "Hãy dán liên kết Spotify, Apple Music, Deezer hoặc YouTube Music.",
            importLinkFailedFallback = "Không thể nhập liên kết playlist."
        )
    } else {
        SearchStrings(
            isVietnamese = false,
            screenTitle = "Search",
            screenSubtitle = "YT Music, Monochrome FLAC & Local",
            settingsDescription = "Settings",
            backDescription = "Back",
            searchPlaceholder = "Songs, albums, artists or lyrics",
            clearSearchDescription = "Clear search",
            filterAll = "All",
            filterYoutube = "YT Music",
            filterMonochrome = "Monochrome FLAC",
            filterLocal = "Local",
            importLinkTitle = "Import Playlist Link",
            importLinkSubtitle = "Spotify, Apple Music, Deezer & YouTube Music",
            openLinkImporterDescription = "Open link importer",
            recentSearchesTitle = "Recent Searches",
            clearAction = "Clear",
            removePrefix = "Remove",
            moodsAndGenresTitle = "Moods & Genres",
            moodsAndGenresSubtitle = "Explore curated YouTube Music stations",
            loadingMoods = "Loading moods and genres",
            noMoodsAvailable = "No moods available right now.",
            retryMoodsDescription = "Retry moods",
            trackFallbackLabel = "Track",
            likeDescription = "Like",
            unlikeDescription = "Unlike",
            badgeFlac = "FLAC",
            badgeLocal = "LOCAL",
            badgeYt = "YT",
            albumFallbackLabel = "Album",
            ytMusicPlaylistSubtitle = "YouTube Music Playlist",
            inspectPlaylistDescription = "Inspect playlist",
            pausedDescription = "Paused",
            focusedEmptyHint = "Type a song, album, artist or lyric to search across sources.",
            searchingAcrossSources = "Searching across sources",
            retryAction = "Retry",
            monochromeHeader = "Monochrome FLAC",
            ytTracksHeader = "YouTube Music Songs",
            albumsHeader = "Albums",
            playlistsHeader = "Playlists",
            artistsHeader = "Artists",
            localLibraryHeader = "Local Library",
            playAction = "Play",
            playAllAction = "Play All",
            shuffleAction = "Shuffle",
            closeCollectionDescription = "Close collection",
            loadingTracks = "Loading tracks",
            emptyCollectionMessage = "No playable tracks found in this collection.",
            playlistLabel = "Playlist",
            moodStationSubtitle = "YouTube Music Station",
            moodCategoriesErrorFallback = "Unable to load moods and genres.",
            moodTracksErrorFallback = "Failed to load mood tracks.",
            searchFailedFallback = "Search failed.",
            albumTracksErrorFallback = "Failed to load album tracks.",
            playlistTracksErrorFallback = "Failed to load playlist tracks.",
            importSheetSubtitle = "Matches tracks to YT Music & Monochrome FLAC",
            closeDescription = "Close",
            clearLinkDescription = "Clear link",
            pasteLinkDescription = "Paste link",
            fetchAndSavePlaylist = "Fetch & Save Playlist",
            importFailedFallback = "Import failed.",
            readingLink = "Reading link...",
            fetchingMetadata = "Fetching playlist metadata",
            savingToLibrary = "Saving playlist to Library",
            importedPlaylistFallback = "Imported Playlist",
            inspectAction = "Inspect",
            importAnotherLink = "Import Another Link",
            tryAgainAction = "Try Again",
            emptyLinkError = "Paste a Spotify, Apple Music, Deezer, or YouTube Music link.",
            importLinkFailedFallback = "Failed to import playlist link."
        )
    }
}

object SearchLogic {
    fun syncLikedFlags(tracks: List<Track>, likedTrackIds: Set<String>): List<Track> {
        if (tracks.isEmpty()) return tracks
        return tracks.map { track ->
            val isLiked = track.id in likedTrackIds
            if (track.liked == isLiked) track else track.copy(liked = isLiked)
        }
    }

    fun syncBundleLikedFlags(bundle: SearchResultBundle, likedTrackIds: Set<String>): SearchResultBundle {
        if (bundle.tracks.isEmpty()) return bundle
        return bundle.copy(tracks = syncLikedFlags(bundle.tracks, likedTrackIds))
    }

    fun applyLikedIds(state: SearchUiState, likedTrackIds: Set<String>): SearchUiState {
        return state.copy(
            ytResults = syncBundleLikedFlags(state.ytResults, likedTrackIds),
            monochromeTracks = syncLikedFlags(state.monochromeTracks, likedTrackIds),
            localTracks = syncLikedFlags(state.localTracks, likedTrackIds),
            drillDownState = state.drillDownState.copy(
                tracks = syncLikedFlags(state.drillDownState.tracks, likedTrackIds)
            ),
            linkImportProgress = state.linkImportProgress.copy(
                matchedTracks = syncLikedFlags(state.linkImportProgress.matchedTracks, likedTrackIds)
            )
        )
    }

    fun filterResults(
        filter: SearchSourceFilter,
        ytResults: SearchResultBundle,
        monochromeTracks: List<Track>,
        localTracks: List<Track>
    ): VisibleSearchResults {
        return when (filter) {
            SearchSourceFilter.ALL -> VisibleSearchResults(
                monochromeTracks = monochromeTracks,
                ytTracks = ytResults.tracks,
                localTracks = localTracks,
                albums = ytResults.albums,
                playlists = ytResults.playlists,
                artists = ytResults.artists
            )
            SearchSourceFilter.YOUTUBE -> VisibleSearchResults(
                ytTracks = ytResults.tracks,
                albums = ytResults.albums,
                playlists = ytResults.playlists,
                artists = ytResults.artists
            )
            SearchSourceFilter.MONOCHROME -> VisibleSearchResults(
                monochromeTracks = monochromeTracks
            )
            SearchSourceFilter.LOCAL -> VisibleSearchResults(
                localTracks = localTracks
            )
        }
    }

    fun buildImportedPlaylistId(
        parsed: ParsedLink?,
        collectionTitle: String,
        nowMs: Long
    ): String {
        if (parsed != null) {
            val prefix = parsed.provider.name.lowercase(Locale.US)
            val cleanExternal = parsed.externalId.replace(Regex("""[^A-Za-z0-9_-]"""), "")
            if (cleanExternal.isNotEmpty()) {
                return "import_${prefix}_$cleanExternal"
            }
        }
        val slug = collectionTitle
            .lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9]+"""), "_")
            .trim('_')
            .take(24)
            .ifEmpty { "playlist" }
        return "import_${slug}_$nowMs"
    }

    fun providerLabel(provider: LinkProvider): String {
        return when (provider) {
            LinkProvider.SPOTIFY -> "Spotify"
            LinkProvider.APPLE_MUSIC -> "Apple Music"
            LinkProvider.DEEZER -> "Deezer"
            LinkProvider.YOUTUBE -> "YouTube Music"
        }
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return ""
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    suspend fun matchImportedCollection(
        collection: ImportedCollection,
        preferMonochrome: Boolean,
        trackMatcher: TrackMatcher,
        searchMonochrome: suspend (String) -> List<Track>,
        searchYtMusic: suspend (String) -> List<Track>,
        onProgress: (processed: Int, matched: Int) -> Unit
    ): List<Track> {
        val total = collection.tracks.size
        if (collection.provider == LinkProvider.YOUTUBE) {
            val direct = collection.tracks.mapIndexedNotNull { index, candidate ->
                val vid = candidate.externalId?.takeIf { it.isNotBlank() }
                val track = vid?.let {
                    Track(
                        id = "yt_$it",
                        title = candidate.title,
                        artist = candidate.artist,
                        album = candidate.album,
                        durationMs = candidate.durationMs,
                        artworkUrl = candidate.artworkUrl ?: collection.artworkUrl,
                        source = SourceKind.YOUTUBE,
                        ytId = it
                    )
                }
                onProgress(index + 1, if (track != null) index + 1 else index)
                track
            }
            if (direct.isNotEmpty() || total == 0) {
                return direct
            }
        }

        val matched = mutableListOf<Track>()
        val seenIds = HashSet<String>()
        collection.tracks.forEachIndexed { index, candidate ->
            val candidateTrack = matchSingleCandidate(
                candidate = candidate,
                fallbackArtwork = collection.artworkUrl,
                preferMonochrome = preferMonochrome,
                trackMatcher = trackMatcher,
                searchMonochrome = searchMonochrome,
                searchYtMusic = searchYtMusic
            )
            if (candidateTrack != null && seenIds.add(candidateTrack.id)) {
                matched += candidateTrack
            }
            onProgress(index + 1, matched.size)
        }
        return matched
    }

    private suspend fun matchSingleCandidate(
        candidate: ImportedTrackCandidate,
        fallbackArtwork: String?,
        preferMonochrome: Boolean,
        trackMatcher: TrackMatcher,
        searchMonochrome: suspend (String) -> List<Track>,
        searchYtMusic: suspend (String) -> List<Track>
    ): Track? {
        val query = "${candidate.artist.substringBefore(',').trim()} ${trackMatcher.coreTitle(candidate.title)}".trim()
        if (query.isBlank()) return null
        if (preferMonochrome) {
            val monoCandidates = runCatching { searchMonochrome(query) }.getOrDefault(emptyList())
            trackMatcher.bestMatch(candidate, monoCandidates)?.let { hit ->
                return hit.track.copy(
                    artworkUrl = hit.track.artworkUrl ?: candidate.artworkUrl ?: fallbackArtwork,
                    album = hit.track.album ?: candidate.album
                )
            }
        }
        val ytCandidates = runCatching { searchYtMusic(query) }.getOrDefault(emptyList())
        return trackMatcher.bestMatch(candidate, ytCandidates)?.let { hit ->
            hit.track.copy(
                artworkUrl = hit.track.artworkUrl ?: candidate.artworkUrl ?: fallbackArtwork,
                album = hit.track.album ?: candidate.album
            )
        }
    }
}
