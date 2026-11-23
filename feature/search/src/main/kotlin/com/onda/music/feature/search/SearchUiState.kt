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

object SearchLogic {
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
