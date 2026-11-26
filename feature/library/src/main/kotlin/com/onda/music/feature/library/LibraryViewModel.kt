package com.onda.music.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onda.music.core.database.ArtistPlayStatRow
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.PlayEventDao
import com.onda.music.core.database.PlaylistDao
import com.onda.music.core.database.PlaylistEntity
import com.onda.music.core.database.PlaylistSongEntity
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.TrackPlayStatRow
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import com.onda.music.core.playback.DownloadManager
import com.onda.music.core.playback.PlaybackController
import com.onda.music.core.sources.AudioTagMetadata
import com.onda.music.core.sources.LocalAudioTagEditor
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val localAudioTagEditor: LocalAudioTagEditor,
    private val downloadManager: DownloadManager,
    private val playbackController: PlaybackController,
    private val ondaPreferences: OndaPreferences
) : ViewModel() {

    private val selectedTab = MutableStateFlow(LibraryTab.OVERVIEW)
    private val searchQuery = MutableStateFlow("")
    private val statsRange = MutableStateFlow(StatsRange.MONTH)
    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    private val localFolders = MutableStateFlow<List<LocalMediaFolder>>(emptyList())
    private val localTrackFolders = MutableStateFlow<Map<String, String>>(emptyMap())
    private val selectedLocalFolderPath = MutableStateFlow<String?>(null)
    private val hasLocalPermission = MutableStateFlow(localMediaScanner.hasPermission())
    private val isScanningLocal = MutableStateFlow(false)
    private val lastScanMessage = MutableStateFlow<String?>(null)
    private val currentLanguageCode = MutableStateFlow("en")
    private var hasCompletedLocalScan = false

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

    private val playlistCoversByIdFlow: Flow<Map<String, List<String>>> = playlistsFlow.flatMapLatest { playlists ->
        if (playlists.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                playlists.map { playlist ->
                    playlistDao.observeSongsInPlaylist(playlist.id).map { songs ->
                        val urls = (listOfNotNull(playlist.artworkUrl) + songs.mapNotNull { it.artworkUrl })
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(4)
                        playlist.id to urls
                    }
                }
            ) { entries ->
                entries.toMap()
            }
        }
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

    private data class HeaderSlice(
        val selectedTab: LibraryTab,
        val searchQuery: String,
        val languageCode: String,
        val glassBlurEnabled: Boolean
    )

    private data class SongsSlice(
        val liked: List<Track>,
        val library: List<Track>,
        val local: List<Track>,
        val relink: List<Track>
    )

    private data class PlaylistSlice(
        val playlists: List<Playlist>,
        val coversById: Map<String, List<String>>,
        val selectedId: String?,
        val selectedPlaylist: Playlist?,
        val selectedTracks: List<Track>
    )

    private data class LocalScanSlice(
        val folders: List<LocalMediaFolder>,
        val trackFolders: Map<String, String>,
        val selectedFolderPath: String?,
        val hasPermission: Boolean,
        val isScanning: Boolean,
        val scanMessage: String?
    )

    private data class PeriodStatsSlice(
        val range: StatsRange,
        val playCount: Int,
        val durationMs: Long,
        val topTracks: List<TrackPlayStatRow>,
        val topArtists: List<ArtistPlayStatRow>
    )

    private val periodStatsFlow: Flow<PeriodStatsSlice> = statsRange.flatMapLatest { range ->
        val sinceMs = range.days?.let { days ->
            System.currentTimeMillis() - days.toLong() * 86_400_000L
        } ?: 0L
        combine(
            playEventDao.observePlayCountSince(sinceMs),
            playEventDao.observePlayDurationSince(sinceMs),
            playEventDao.observeTopTracksSince(sinceMs, 10),
            playEventDao.observeTopArtistsSince(sinceMs, 8)
        ) { count, durationMs, topTracks, topArtists ->
            PeriodStatsSlice(
                range = range,
                playCount = count,
                durationMs = durationMs,
                topTracks = topTracks,
                topArtists = topArtists
            )
        }
    }

    private data class StatsAndPlaybackSlice(
        val downloads: List<DownloadEntryUi>,
        val recentSongs: List<Track>,
        val periodStats: PeriodStatsSlice,
        val totalPlayCount: Int,
        val totalPlayDurationMs: Long,
        val activeTrackId: String?,
        val hasMiniPlayer: Boolean,
        val isPlaying: Boolean
    )

    private val prefSliceFlow = ondaPreferences.settings
        .map { it.languageCode to it.glassBlurEnabled }
        .distinctUntilChanged()

    private val headerSliceFlow = combine(
        selectedTab,
        searchQuery,
        prefSliceFlow
    ) { tab, query, (languageCode, glassBlurEnabled) ->
        HeaderSlice(
            selectedTab = tab,
            searchQuery = query,
            languageCode = languageCode,
            glassBlurEnabled = glassBlurEnabled
        )
    }

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
        playlistCoversByIdFlow,
        selectedPlaylistId,
        selectedPlaylistFlow,
        selectedPlaylistTracksFlow
    ) { playlists, coversById, selectedId, selected, tracks ->
        PlaylistSlice(
            playlists = playlists,
            coversById = coversById,
            selectedId = selectedId,
            selectedPlaylist = selected,
            selectedTracks = tracks
        )
    }

    private val localIndexFlow = combine(
        localFolders,
        localTrackFolders
    ) { folders, trackFolders ->
        folders to trackFolders
    }

    private val localScanSliceFlow = combine(
        localIndexFlow,
        selectedLocalFolderPath,
        hasLocalPermission,
        isScanningLocal,
        lastScanMessage
    ) { (folders, trackFolders), folderPath, permission, scanning, message ->
        LocalScanSlice(
            folders = folders,
            trackFolders = trackFolders,
            selectedFolderPath = folderPath,
            hasPermission = permission,
            isScanning = scanning,
            scanMessage = message
        )
    }

    private val statsCoreFlow = combine(
        downloadsFlow,
        recentSongsFlow,
        periodStatsFlow,
        playEventDao.observeTotalPlayCount(),
        playEventDao.observeTotalPlayDurationMs()
    ) { downloads, recentSongs, periodStats, playCount, playDurationMs ->
        StatsAndPlaybackSlice(
            downloads = downloads,
            recentSongs = recentSongs,
            periodStats = periodStats,
            totalPlayCount = playCount,
            totalPlayDurationMs = playDurationMs,
            activeTrackId = null,
            hasMiniPlayer = false,
            isPlaying = false
        )
    }

    private val statsAndPlaybackFlow = combine(
        statsCoreFlow,
        playbackController.playbackState
    ) { core, playbackState ->
        core.copy(
            activeTrackId = playbackState.currentTrack?.id,
            hasMiniPlayer = playbackState.currentTrack != null,
            isPlaying = playbackState.status == PlaybackStatus.PLAYING
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        headerSliceFlow,
        songsSliceFlow,
        playlistSliceFlow,
        localScanSliceFlow,
        statsAndPlaybackFlow
    ) { header, songs, playlists, localScan, stats ->
        LibraryUiState(
            selectedTab = header.selectedTab,
            searchQuery = header.searchQuery,
            playlists = playlists.playlists,
            playlistCoversById = playlists.coversById,
            selectedPlaylistId = playlists.selectedId,
            selectedPlaylist = playlists.selectedPlaylist,
            selectedPlaylistTracks = playlists.selectedTracks,
            likedSongs = songs.liked,
            librarySongs = songs.library,
            localSongs = songs.local,
            songsNeedingRelink = songs.relink,
            localFolders = localScan.folders,
            localTrackFolders = localScan.trackFolders,
            selectedLocalFolderPath = localScan.selectedFolderPath,
            hasLocalPermission = localScan.hasPermission,
            isScanningLocal = localScan.isScanning,
            lastScanMessage = localScan.scanMessage,
            downloads = stats.downloads,
            recentSongs = stats.recentSongs,
            statsRange = stats.periodStats.range,
            periodPlayCount = stats.periodStats.playCount,
            periodPlayDurationMs = stats.periodStats.durationMs,
            topTracks = stats.periodStats.topTracks,
            topArtists = stats.periodStats.topArtists,
            totalPlayCount = stats.totalPlayCount,
            totalPlayDurationMs = stats.totalPlayDurationMs,
            activeTrackId = stats.activeTrackId,
            hasMiniPlayer = stats.hasMiniPlayer,
            isPlaying = stats.isPlaying,
            languageCode = header.languageCode,
            glassBlurEnabled = header.glassBlurEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LibraryUiState(hasLocalPermission = localMediaScanner.hasPermission())
    )

    init {
        viewModelScope.launch {
            ondaPreferences.settings
                .map { it.languageCode }
                .distinctUntilChanged()
                .collect { code ->
                    currentLanguageCode.value = code
                }
        }
        viewModelScope.launch {
            localSongsFlow
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { hasSongs ->
                    if (hasSongs &&
                        localMediaScanner.hasPermission() &&
                        localTrackFolders.value.isEmpty() &&
                        !hasCompletedLocalScan &&
                        !isScanningLocal.value
                    ) {
                        scanLocalMedia()
                    }
                }
        }
    }

    fun requiredLocalPermission(): String = localMediaScanner.requiredPermission()

    fun refreshLocalPermission() {
        val granted = localMediaScanner.hasPermission()
        hasLocalPermission.value = granted
        val needsFolderIndex = localTrackFolders.value.isEmpty() && !hasCompletedLocalScan
        if (granted && !isScanningLocal.value && (uiState.value.localSongs.isEmpty() || needsFolderIndex)) {
            scanLocalMedia()
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectStatsRange(range: StatsRange) {
        statsRange.value = range
    }

    suspend fun loadLocalTrackTags(track: Track): AudioTagMetadata? {
        val rawUri = track.localUri?.takeIf { it.isNotBlank() } ?: return null
        return localAudioTagEditor.readFromUri(Uri.parse(rawUri)).getOrNull()
    }

    suspend fun saveLocalTrackTags(track: Track, metadata: AudioTagMetadata): Result<Unit> {
        val strings = resolveLibraryStrings(currentLanguageCode.value)
        val rawUri = track.localUri?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException(strings.tagNoLocalUriError))
        val writeResult = localAudioTagEditor.writeToUriAtomic(Uri.parse(rawUri), metadata)
        return writeResult.map { savedArtworkUri ->
            songDao.updateTrackMetadata(
                id = track.id,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album.takeIf { it.isNotBlank() },
                artworkUrl = savedArtworkUri
            )
            lastScanMessage.value = strings.updatedTagsMessage(metadata.title)
        }
    }

    fun playTrackById(trackId: String) {
        viewModelScope.launch {
            val state = uiState.value
            val topIds = state.topTracks.map { it.trackId }
            val queryIds = (topIds + trackId).distinct()
            val tracksById = songDao.getByIds(queryIds)
                .associate { entity -> entity.id to entity.toModel() }
            val fallbackTrack = if (trackId !in tracksById) {
                songDao.getById(trackId)?.toModel()
            } else {
                null
            }
            val resolved = resolveStatsPlaybackQueue(
                clickedTrackId = trackId,
                topTrackIds = topIds,
                tracksById = tracksById,
                recentSongs = state.recentSongs,
                fallbackTrack = fallbackTrack
            ) ?: return@launch
            playbackController.playQueue(resolved.tracks, startIndex = resolved.startIndex)
        }
    }

    fun selectTab(tab: LibraryTab) {
        if (selectedTab.value != tab) {
            selectedPlaylistId.value = null
        }
        selectedTab.value = tab
    }

    fun openPlaylist(playlistId: String) {
        if (selectedTab.value != LibraryTab.OVERVIEW && selectedTab.value != LibraryTab.PLAYLISTS) {
            selectedTab.value = LibraryTab.PLAYLISTS
        }
        selectedPlaylistId.value = playlistId
    }

    fun closePlaylist() {
        selectedPlaylistId.value = null
    }

    fun selectLocalFolder(folderPath: String?) {
        selectedLocalFolderPath.value = if (selectedLocalFolderPath.value == folderPath) null else folderPath
    }

    fun scanLocalMedia() {
        val strings = resolveLibraryStrings(currentLanguageCode.value)
        val granted = localMediaScanner.hasPermission()
        hasLocalPermission.value = granted
        if (!granted || isScanningLocal.value) {
            if (!granted) {
                lastScanMessage.value = strings.audioPermissionRequiredMsg
            }
            return
        }
        viewModelScope.launch {
            isScanningLocal.value = true
            lastScanMessage.value = null
            runCatching {
                val result = localMediaScanner.scan()
                localFolders.value = result.folders
                localTrackFolders.value = result.trackFolders
                hasCompletedLocalScan = true
                val activeFolder = selectedLocalFolderPath.value
                if (activeFolder != null && result.folders.none { it.path == activeFolder }) {
                    selectedLocalFolderPath.value = null
                }
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
                val currentStrings = resolveLibraryStrings(currentLanguageCode.value)
                lastScanMessage.value = currentStrings.scanCompletedMessage(
                    trackCount = result.tracks.size,
                    folderCount = result.folders.size,
                    relinkedCount = relinkedMatches.size
                )
            }.onFailure { error ->
                val currentStrings = resolveLibraryStrings(currentLanguageCode.value)
                lastScanMessage.value = error.message ?: currentStrings.localScanFailedMsg
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
            val targetLiked = !track.liked
            songDao.toggleLikedOrInsert(track.toEntity(), targetLiked)
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

internal data class StatsPlaybackQueue(
    val tracks: List<Track>,
    val startIndex: Int
)

internal fun resolveStatsPlaybackQueue(
    clickedTrackId: String,
    topTrackIds: List<String>,
    tracksById: Map<String, Track>,
    recentSongs: List<Track>,
    fallbackTrack: Track? = null
): StatsPlaybackQueue? {
    val queue = ArrayList<Track>()
    val seenIds = HashSet<String>()

    topTrackIds.forEach { id ->
        val track = tracksById[id]
        if (track != null && seenIds.add(track.id)) {
            queue.add(track)
        }
    }

    recentSongs.forEach { track ->
        if (seenIds.add(track.id)) {
            queue.add(track)
        }
    }

    if (clickedTrackId !in seenIds) {
        val clicked = tracksById[clickedTrackId] ?: fallbackTrack
        if (clicked != null) {
            queue.add(0, clicked)
            seenIds.add(clicked.id)
        }
    }

    if (queue.isEmpty()) return null
    val startIndex = queue.indexOfFirst { it.id == clickedTrackId }
    if (startIndex < 0) return null
    return StatsPlaybackQueue(tracks = queue, startIndex = startIndex)
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
