package com.onda.music.feature.library

import androidx.compose.runtime.Immutable
import com.onda.music.core.database.ArtistPlayStatRow
import com.onda.music.core.database.TrackPlayStatRow
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import com.onda.music.core.sources.LocalMediaFolder

enum class StatsRange(val label: String, val days: Int?) {
    WEEK("7 Days", 7),
    MONTH("30 Days", 30),
    ALL_TIME("All Time", null)
}

enum class LibraryTab(val label: String) {
    OVERVIEW("Overview"),
    PLAYLISTS("Playlists"),
    LIKED("Liked"),
    LOCAL("Local"),
    DOWNLOADS("Downloads"),
    HISTORY("History & Stats")
}

@Immutable
data class DownloadEntryUi(
    val item: DownloadItem,
    val track: Track?
)

@Immutable
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.OVERVIEW,
    val searchQuery: String = "",
    val playlists: List<Playlist> = emptyList(),
    val playlistCoversById: Map<String, List<String>> = emptyMap(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val likedSongs: List<Track> = emptyList(),
    val librarySongs: List<Track> = emptyList(),
    val localSongs: List<Track> = emptyList(),
    val songsNeedingRelink: List<Track> = emptyList(),
    val localFolders: List<LocalMediaFolder> = emptyList(),
    val selectedLocalFolderPath: String? = null,
    val hasLocalPermission: Boolean = false,
    val isScanningLocal: Boolean = false,
    val lastScanMessage: String? = null,
    val downloads: List<DownloadEntryUi> = emptyList(),
    val recentSongs: List<Track> = emptyList(),
    val statsRange: StatsRange = StatsRange.MONTH,
    val periodPlayCount: Int = 0,
    val periodPlayDurationMs: Long = 0L,
    val topTracks: List<TrackPlayStatRow> = emptyList(),
    val topArtists: List<ArtistPlayStatRow> = emptyList(),
    val totalPlayCount: Int = 0,
    val totalPlayDurationMs: Long = 0L,
    val activeTrackId: String? = null,
    val hasMiniPlayer: Boolean = false,
    val isPlaying: Boolean = false
)
