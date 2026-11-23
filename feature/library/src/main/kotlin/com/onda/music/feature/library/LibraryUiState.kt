package com.onda.music.feature.library

import androidx.compose.runtime.Immutable
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import com.onda.music.core.sources.LocalMediaFolder

enum class LibraryTab(val label: String) {
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
    val selectedTab: LibraryTab = LibraryTab.PLAYLISTS,
    val playlists: List<Playlist> = emptyList(),
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
    val totalPlayCount: Int = 0,
    val totalPlayDurationMs: Long = 0L,
    val activeTrackId: String? = null,
    val hasMiniPlayer: Boolean = false
)
