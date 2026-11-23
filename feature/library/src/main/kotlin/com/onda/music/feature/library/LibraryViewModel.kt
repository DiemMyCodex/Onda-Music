package com.onda.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onda.music.core.database.PlayEventDao
import com.onda.music.core.database.PlaylistDao
import com.onda.music.core.database.PlaylistEntity
import com.onda.music.core.database.PlaylistSongEntity
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import com.onda.music.core.playback.DownloadManager
import com.onda.music.core.playback.PlaybackController
import com.onda.music.core.sources.LocalMediaFolder
import com.onda.music.core.sources.LocalMediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val playEventDao: PlayEventDao,
    private val localMediaScanner: LocalMediaScanner,
    private val downloadManager: DownloadManager,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val selectedTab = MutableStateFlow(LibraryTab.PLAYLISTS)
    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    private val localFolders = MutableStateFlow<List<LocalMediaFolder>>(emptyList())
    private val selectedLocalFolderPath = MutableStateFlow<String?>(null)
    private val hasLocalPermission = MutableStateFlow(localMediaScanner.hasPermission())
    private val isScanningLocal = MutableStateFlow(false)
    private val lastScanMessage = MutableStateFlow<String?>(null)

    private val allSongsById: Flow<Map<String, Track>> = songDao.observeAll().map { entities ->
        entities.associate { entity -> entity.id to entity.toModel() }
    }

    private val likedSongsFlow: Flow<List<Track>> = songDao.observeLikedSongs().map { list ->
        list.map { it.toModel() }
    }

    private val librarySongsFlow: Flow<List<Track>> = songDao.observeLibrarySongs().map { list ->
        list.map { it.toModel() }
    }

    private val localSongsFlow: Flow<List<Track>> = songDao.observeLocalSongs().map { list ->
        list.map { it.toModel() }
    }

    private val relinkSongsFlow: Flow<List<Track>> = songDao.observeSongsNeedingRelink().map { list ->
        list.map { it.toModel() }
    }

    private val playlistsFlow: Flow<List<Playlist>> = playlistDao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    private val selectedPlaylistFlow: Flow<Playlist?> = selectedPlaylistId.flatMapLatest { id ->
        if (id == null) flowOf(null) else playlistDao.observeById(id).map { it?.toModel() }
    }

    private val selectedPlaylistTracksFlow: Flow<List<Track>> = selectedPlaylistId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            playlistDao.observeSongsInPlaylist(id).map { list -> list.map { it.toModel() } }
        }
    }

    private val downloadsFlow: Flow<List<DownloadEntryUi>> = combine(
        downloadManager.downloads,
        allSongsById
    ) { downloads, songMap ->
        downloads.map { item ->
            DownloadEntryUi(item = item, track = songMap[item.trackId])
        }
    }

    private val recentSongsFlow: Flow<List<Track>> = playEventDao.observeRecentSongs(30).map { list ->
        list.map { it.toModel() }
    }

    private data class SongsSlice(
        val liked: List<Track>,
        val library: List<Track>,
        val local: List<Track>,
        val relink: List<Track>
    )

    private data class PlaylistSlice(
        val playlists: List<Playlist>,
        val selectedId: String?,
        val selectedPlaylist: Playlist?,
        val selectedTracks: List<Track>
    )

    private data class LocalScanSlice(
        val folders: List<LocalMediaFolder>,
        val selectedFolderPath: String?,
        val hasPermission: Boolean,
        val isScanning: Boolean,
        val scanMessage: String?
    )

    private data class StatsAndPlaybackSlice(
        val downloads: List<DownloadEntryUi>,
        val recentSongs: List<Track>,
        val totalPlayCount: Int,
        val totalPlayDurationMs: Long,
        val activeTrackId: String?,
        val hasMiniPlayer: Boolean
    )

    private val songsSliceFlow = combine(
        likedSongsFlow,
        librarySongsFlow,
        localSongsFlow,
        relinkSongsFlow
    ) { liked, library, local, relink ->
        SongsSlice(liked = liked, library = library, local = local, relink = relink)
    }

    private val playlistSliceFlow = combine(
        playlistsFlow,
        selectedPlaylistId,
        selectedPlaylistFlow,
        selectedPlaylistTracksFlow
    ) { playlists, selectedId, selected, tracks ->
        PlaylistSlice(
            playlists = playlists,
            selectedId = selectedId,
            selectedPlaylist = selected,
            selectedTracks = tracks
        )
    }

    private val localScanSliceFlow = combine(
        localFolders,
        selectedLocalFolderPath,
        hasLocalPermission,
        isScanningLocal,
        lastScanMessage
    ) { folders, folderPath, permission, scanning, message ->
        LocalScanSlice(
            folders = folders,
            selectedFolderPath = folderPath,
            hasPermission = permission,
            isScanning = scanning,
            scanMessage = message
        )
    }

    private val statsAndPlaybackFlow = combine(
        downloadsFlow,
        recentSongsFlow,
        playEventDao.observeTotalPlayCount(),
        playEventDao.observeTotalPlayDurationMs(),
        playbackController.playbackState
    ) { downloads, recentSongs, playCount, playDurationMs, playbackState ->
        StatsAndPlaybackSlice(
            downloads = downloads,
            recentSongs = recentSongs,
            totalPlayCount = playCount,
            totalPlayDurationMs = playDurationMs,
            activeTrackId = playbackState.currentTrack?.id,
            hasMiniPlayer = playbackState.currentTrack != null
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        selectedTab,
        songsSliceFlow,
        playlistSliceFlow,
        localScanSliceFlow,
        statsAndPlaybackFlow
    ) { tab, songs, playlists, localScan, stats ->
        LibraryUiState(
            selectedTab = tab,
            playlists = playlists.playlists,
            selectedPlaylistId = playlists.selectedId,
            selectedPlaylist = playlists.selectedPlaylist,
            selectedPlaylistTracks = playlists.selectedTracks,
            likedSongs = songs.liked,
            librarySongs = songs.library,
            localSongs = songs.local,
            songsNeedingRelink = songs.relink,
            localFolders = localScan.folders,
            selectedLocalFolderPath = localScan.selectedFolderPath,
            hasLocalPermission = localScan.hasPermission,
            isScanningLocal = localScan.isScanning,
            lastScanMessage = localScan.scanMessage,
            downloads = stats.downloads,
            recentSongs = stats.recentSongs,
            totalPlayCount = stats.totalPlayCount,
            totalPlayDurationMs = stats.totalPlayDurationMs,
            activeTrackId = stats.activeTrackId,
            hasMiniPlayer = stats.hasMiniPlayer
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LibraryUiState(hasLocalPermission = localMediaScanner.hasPermission())
    )

    fun requiredLocalPermission(): String = localMediaScanner.requiredPermission()

    fun refreshLocalPermission() {
        val granted = localMediaScanner.hasPermission()
        hasLocalPermission.value = granted
        if (granted && uiState.value.localSongs.isEmpty() && !isScanningLocal.value) {
            scanLocalMedia()
        }
    }

    fun selectTab(tab: LibraryTab) {
        selectedTab.value = tab
        if (tab != LibraryTab.PLAYLISTS) {
            selectedPlaylistId.value = null
        }
    }

    fun openPlaylist(playlistId: String) {
        selectedTab.value = LibraryTab.PLAYLISTS
        selectedPlaylistId.value = playlistId
    }

    fun closePlaylist() {
        selectedPlaylistId.value = null
    }

    fun selectLocalFolder(folderPath: String?) {
        selectedLocalFolderPath.value = if (selectedLocalFolderPath.value == folderPath) null else folderPath
    }

    fun scanLocalMedia() {
        val granted = localMediaScanner.hasPermission()
        hasLocalPermission.value = granted
        if (!granted || isScanningLocal.value) {
            if (!granted) {
                lastScanMessage.value = "Audio storage permission is required to scan local files"
            }
            return
        }
        viewModelScope.launch {
            isScanningLocal.value = true
            lastScanMessage.value = null
            runCatching {
                val result = localMediaScanner.scan()
                localFolders.value = result.folders
                if (result.tracks.isNotEmpty()) {
                    songDao.upsertAndMergeAll(result.tracks.map { it.toEntity() })
                }
                val needingRelink = songDao.getAll()
                    .filter { it.needsRelink }
                    .map { it.toModel() }
                val relinkedMatches = findAutoRelinkMatches(needingRelink, result.tracks)
                relinkedMatches.forEach { (missingTrackId, matchedUri) ->
                    songDao.relinkLocalUri(missingTrackId, matchedUri)
                }
                val relinkSuffix = if (relinkedMatches.isNotEmpty()) {
                    ", relinked ${relinkedMatches.size} tracks"
                } else {
                    ""
                }
                lastScanMessage.value = "Scanned ${result.tracks.size} tracks across ${result.folders.size} folders$relinkSuffix"
            }.onFailure { error ->
                lastScanMessage.value = error.message ?: "Local media scan failed"
            }
            isScanningLocal.value = false
        }
    }

    fun relinkTrack(trackId: String, localUri: String) {
        if (localUri.isBlank()) return
        viewModelScope.launch {
            songDao.relinkLocalUri(trackId, localUri)
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val cleanDesc = description?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            val playlist = PlaylistEntity(
                id = "pl_${UUID.randomUUID()}",
                name = trimmed,
                description = cleanDesc,
                artworkUrl = null,
                trackCount = 0,
                createdAtMs = System.currentTimeMillis(),
                remoteId = null
            )
            playlistDao.upsert(playlist)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String, newDescription: String? = null) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = playlistDao.getById(playlistId) ?: return@launch
            val cleanDesc = newDescription?.trim()?.takeIf { it.isNotEmpty() }
            playlistDao.upsert(
                existing.copy(
                    name = trimmed,
                    description = cleanDesc
                )
            )
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistDao.deletePlaylistById(playlistId)
            if (selectedPlaylistId.value == playlistId) {
                selectedPlaylistId.value = null
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            songDao.upsertAndMerge(track.toEntity())
            playlistDao.addSongToPlaylist(playlistId, track.id)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            playlistDao.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun reorderPlaylistTracks(playlistId: String, orderedTracks: List<Track>) {
        if (orderedTracks.isEmpty()) return
        viewModelScope.launch {
            val existingBySongId = playlistDao.getPlaylistSongs(playlistId).associateBy { it.songId }
            val now = System.currentTimeMillis()
            val updatedEntries = orderedTracks.mapIndexed { index, track ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = track.id,
                    position = index,
                    addedAtMs = existingBySongId[track.id]?.addedAtMs ?: now
                )
            }
            playlistDao.upsertPlaylistSongs(updatedEntries)
            playlistDao.refreshTrackCount(playlistId)
        }
    }

    fun playTrack(track: Track, queue: List<Track>) {
        val resolvedQueue = if (queue.isEmpty()) listOf(track) else queue
        val index = resolvedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playQueue(resolvedQueue, startIndex = index)
    }

    fun playAll(tracks: List<Track>, shuffled: Boolean = false) {
        if (tracks.isEmpty()) return
        val queue = if (shuffled) tracks.shuffled() else tracks
        playbackController.playQueue(queue, startIndex = 0)
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            songDao.upsertAndMerge(track.toEntity().copy(liked = !track.liked))
            songDao.setLiked(track.id, !track.liked)
        }
    }

    fun toggleDownload(track: Track) {
        val existing = uiState.value.downloads.firstOrNull { it.item.trackId == track.id }?.item
        when (existing?.status) {
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> cancelOrDeleteDownload(track.id, isActive = true)
            DownloadStatus.COMPLETED -> cancelOrDeleteDownload(track.id, isActive = false)
            DownloadStatus.FAILED, null -> downloadManager.enqueueDownload(track)
        }
    }

    fun retryDownload(track: Track) {
        downloadManager.enqueueDownload(track)
    }

    fun cancelOrDeleteDownload(trackId: String, isActive: Boolean) {
        viewModelScope.launch {
            if (isActive) {
                downloadManager.cancelDownload(trackId)
            } else {
                downloadManager.deleteDownload(trackId)
            }
        }
    }
}

internal fun reorderTrackList(tracks: List<Track>, fromIndex: Int, toIndex: Int): List<Track> {
    if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) {
        return tracks
    }
    return tracks.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal fun findAutoRelinkMatches(
    missingTracks: List<Track>,
    scannedLocalTracks: List<Track>
): Map<String, String> {
    if (missingTracks.isEmpty() || scannedLocalTracks.isEmpty()) return emptyMap()
    val byTitleAndArtist = LinkedHashMap<Pair<String, String>, String>()
    val byTitleOnly = LinkedHashMap<String, String>()
    scannedLocalTracks.forEach { local ->
        val uri = local.localUri ?: return@forEach
        val normTitle = normalizeMatchToken(local.title)
        val normArtist = normalizeMatchToken(local.artist)
        if (normTitle.isNotEmpty()) {
            byTitleAndArtist.putIfAbsent(normTitle to normArtist, uri)
            byTitleOnly.putIfAbsent(normTitle, uri)
        }
    }
    val matches = LinkedHashMap<String, String>()
    missingTracks.forEach { missing ->
        val normTitle = normalizeMatchToken(missing.title)
        val normArtist = normalizeMatchToken(missing.artist)
        val matchedUri = byTitleAndArtist[normTitle to normArtist]
            ?: byTitleOnly[normTitle]
        if (!matchedUri.isNullOrBlank()) {
            matches[missing.id] = matchedUri
        }
    }
    return matches
}

private fun normalizeMatchToken(raw: String): String {
    return raw.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
