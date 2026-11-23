package com.onda.music.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.database.PlayEventDao
import com.onda.music.core.database.PlayEventEntity
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaPaletteExtractor
import com.onda.music.core.model.Album
import com.onda.music.core.model.HomeShelf
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.playback.PlaybackController
import com.onda.music.core.sources.LocalMediaScanner
import com.onda.music.core.sources.YtMusicClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private data class RemoteHomeSnapshot(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOfflineFallback: Boolean = false,
    val errorMessage: String? = null,
    val quickPicks: List<Track> = emptyList(),
    val shelves: List<HomeShelf> = emptyList(),
    val chartShelves: List<HomeShelf> = emptyList(),
    val newReleases: List<Album> = emptyList(),
    val moodCategories: List<MoodCategory> = emptyList(),
    val scannedLocalTracks: List<Track> = emptyList()
)

private data class HomeSourcesBundle(
    val settings: OndaUserSettings,
    val allSongs: List<Track>,
    val recentSongs: List<Track>,
    val recentEvents: List<PlayEventEntity>,
    val playbackState: PlaybackState
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ytMusicClient: YtMusicClient,
    private val localMediaScanner: LocalMediaScanner,
    private val songDao: SongDao,
    private val playEventDao: PlayEventDao,
    private val ondaPreferences: OndaPreferences,
    private val playbackController: PlaybackController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val remoteState = MutableStateFlow(RemoteHomeSnapshot())
    private val detailState = MutableStateFlow<CollectionDetailState?>(null)
    private val heroPaletteState = MutableStateFlow(OndaArtworkPalette.Default)

    private var lastExtractedArtworkUrl: String? = null
    private var paletteJob: Job? = null
    private var detailJob: Job? = null
    private var refreshJob: Job? = null

    private val localSourcesFlow = combine(
        ondaPreferences.settings,
        songDao.observeAll(),
        playEventDao.observeRecentSongs(limit = 24),
        playEventDao.observeRecent(limit = 200),
        playbackController.playbackState
    ) { settings, songEntities, recentEntities, eventEntities, playbackState ->
        HomeSourcesBundle(
            settings = settings,
            allSongs = songEntities.map { it.toModel() },
            recentSongs = recentEntities.map { it.toModel() },
            recentEvents = eventEntities,
            playbackState = playbackState
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        localSourcesFlow,
        remoteState,
        detailState,
        heroPaletteState
    ) { local, remote, detail, heroPalette ->
        buildUiState(local, remote, detail, heroPalette)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            ondaPreferences.settings.collect { settings ->
                ytMusicClient.setCookie(settings.ytCookie)
                val country = if (settings.languageCode == "vi") "VN" else "US"
                ytMusicClient.setLocale(countryCode = country, languageTag = settings.languageCode)
            }
        }
        viewModelScope.launch {
            uiState.collect { state ->
                val artworkUrl = state.heroTrack?.artworkUrl
                if (artworkUrl != lastExtractedArtworkUrl) {
                    lastExtractedArtworkUrl = artworkUrl
                    extractHeroPalette(artworkUrl)
                }
            }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            remoteState.update {
                it.copy(
                    isLoading = initial && it.shelves.isEmpty(),
                    isRefreshing = !initial,
                    errorMessage = null
                )
            }
            val loaded = withContext(Dispatchers.IO) {
                fetchRemoteAndLocalFallback()
            }
            remoteState.value = loaded
        }
    }

    fun playHero() {
        val state = uiState.value
        val hero = state.heroTrack ?: return
        if (state.activeTrackId == hero.id) {
            playbackController.togglePlayPause()
            return
        }
        val queue = buildHeroQueue(hero, state)
        playTrack(hero, queue)
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val normalizedQueue = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val startIndex = normalizedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playQueue(normalizedQueue, startIndex)
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = track.copy(liked = !track.liked)
            songDao.upsertAndMerge(updated.toEntity())
        }
    }

    fun openAlbum(album: Album) {
        detailJob?.cancel()
        detailState.value = CollectionDetailState(
            sourceId = album.id,
            title = album.title,
            subtitle = album.artist,
            artworkUrl = album.artworkUrl,
            isLoading = true,
            isAlbum = true
        )
        detailJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ytMusicClient.getAlbumTracks(album.id)
            }
            result.onSuccess { (resolvedAlbum, tracks) ->
                val finalTracks = tracks.ifEmpty {
                    localTracksForAlbum(album)
                }
                detailState.value = CollectionDetailState(
                    sourceId = resolvedAlbum.id,
                    title = resolvedAlbum.title,
                    subtitle = resolvedAlbum.artist.ifBlank { album.artist },
                    artworkUrl = resolvedAlbum.artworkUrl ?: album.artworkUrl,
                    tracks = finalTracks,
                    isLoading = false,
                    isAlbum = true
                )
            }.onFailure { err ->
                val fallbackTracks = withContext(Dispatchers.IO) {
                    localTracksForAlbum(album)
                }
                detailState.value = CollectionDetailState(
                    sourceId = album.id,
                    title = album.title,
                    subtitle = album.artist,
                    artworkUrl = album.artworkUrl,
                    tracks = fallbackTracks,
                    isLoading = false,
                    errorMessage = if (fallbackTracks.isEmpty()) err.message ?: "Unable to load album" else null,
                    isAlbum = true
                )
            }
        }
    }

    fun openPlaylist(playlist: Playlist) {
        detailJob?.cancel()
        detailState.value = CollectionDetailState(
            sourceId = playlist.id,
            title = playlist.name,
            subtitle = playlist.description.orEmpty(),
            artworkUrl = playlist.artworkUrl,
            isLoading = true,
            isAlbum = false
        )
        detailJob = viewModelScope.launch {
            val lookupId = playlist.remoteId ?: playlist.id.removePrefix("yt_pl_")
            val result = withContext(Dispatchers.IO) {
                ytMusicClient.getPlaylistTracks(lookupId)
            }
            result.onSuccess { (resolvedPlaylist, tracks) ->
                detailState.value = CollectionDetailState(
                    sourceId = resolvedPlaylist.id,
                    title = resolvedPlaylist.name,
                    subtitle = resolvedPlaylist.description ?: playlist.description.orEmpty(),
                    artworkUrl = resolvedPlaylist.artworkUrl ?: playlist.artworkUrl,
                    tracks = tracks,
                    isLoading = false,
                    isAlbum = false
                )
            }.onFailure { err ->
                detailState.value = CollectionDetailState(
                    sourceId = playlist.id,
                    title = playlist.name,
                    subtitle = playlist.description.orEmpty(),
                    artworkUrl = playlist.artworkUrl,
                    tracks = emptyList(),
                    isLoading = false,
                    errorMessage = err.message ?: "Unable to load playlist",
                    isAlbum = false
                )
            }
        }
    }

    fun openMood(mood: MoodCategory) {
        detailJob?.cancel()
        detailState.value = CollectionDetailState(
            sourceId = mood.id,
            title = mood.title,
            subtitle = "",
            artworkUrl = null,
            isLoading = true,
            isAlbum = false
        )
        detailJob = viewModelScope.launch {
            val tracksResult = withContext(Dispatchers.IO) {
                val browseTracks = ytMusicClient.getHomeShelves(params = mood.params)
                    .getOrDefault(emptyList())
                    .flatMap { it.tracks }
                if (browseTracks.isNotEmpty()) {
                    Result.success(browseTracks.distinctBy { it.id })
                } else {
                    ytMusicClient.searchTracks(mood.title)
                }
            }
            tracksResult.onSuccess { tracks ->
                detailState.value = CollectionDetailState(
                    sourceId = mood.id,
                    title = mood.title,
                    subtitle = "",
                    artworkUrl = tracks.firstOrNull()?.artworkUrl,
                    tracks = tracks,
                    isLoading = false,
                    isAlbum = false
                )
            }.onFailure { err ->
                detailState.value = CollectionDetailState(
                    sourceId = mood.id,
                    title = mood.title,
                    subtitle = "",
                    artworkUrl = null,
                    tracks = emptyList(),
                    isLoading = false,
                    errorMessage = err.message ?: "Unable to load mood tracks",
                    isAlbum = false
                )
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        detailState.value = null
    }

    fun playAllCollection() {
        val tracks = detailState.value?.tracks.orEmpty()
        if (tracks.isEmpty()) return
        playbackController.playQueue(tracks, startIndex = 0)
    }

    fun playCollectionTrack(track: Track) {
        val tracks = detailState.value?.tracks.orEmpty()
        if (tracks.isEmpty()) {
            playbackController.playTrack(track)
            return
        }
        playTrack(track, tracks)
    }

    private suspend fun fetchRemoteAndLocalFallback(): RemoteHomeSnapshot = coroutineScope {
        val shelvesDeferred = async { ytMusicClient.getHomeShelves() }
        val chartsDeferred = async { ytMusicClient.getCharts() }
        val moodsDeferred = async { ytMusicClient.getMoodCategories() }
        val releasesDeferred = async { ytMusicClient.getNewReleases() }

        val shelvesResult = shelvesDeferred.await()
        val chartsResult = chartsDeferred.await()
        val moodsResult = moodsDeferred.await()
        val releasesResult = releasesDeferred.await()

        val rawShelves = shelvesResult.getOrDefault(emptyList())
        val charts = chartsResult.getOrDefault(emptyList())
        val moods = moodsResult.getOrDefault(emptyList())
        val releases = releasesResult.getOrDefault(emptyList())

        val allRemoteFailed = shelvesResult.isFailure &&
            chartsResult.isFailure &&
            moodsResult.isFailure &&
            releasesResult.isFailure

        val chartTitles = charts.mapTo(HashSet()) { it.title.lowercase() }
        val filteredShelves = rawShelves.filterNot { shelf ->
            shelf.id == "new_releases" || shelf.title.lowercase() in chartTitles
        }

        val quickPickShelf = filteredShelves.firstOrNull { shelf ->
            shelf.tracks.isNotEmpty() &&
                (shelf.title.contains("quick", ignoreCase = true) ||
                    shelf.title.contains("pick", ignoreCase = true))
        } ?: filteredShelves.firstOrNull { it.tracks.isNotEmpty() }

        val quickPicks = quickPickShelf?.tracks.orEmpty()
        val remainingShelves = if (quickPickShelf != null) {
            filteredShelves.filterNot { it.id == quickPickShelf.id }
        } else {
            filteredShelves
        }

        val fallbackReleases = if (releases.isNotEmpty()) {
            releases
        } else {
            rawShelves.firstOrNull { it.id == "new_releases" }?.albums.orEmpty()
        }

        val scannedLocal = if (allRemoteFailed || (quickPicks.isEmpty() && remainingShelves.isEmpty())) {
            scanAndPersistLocalTracks()
        } else {
            emptyList()
        }

        val errorMsg = if (allRemoteFailed) {
            shelvesResult.exceptionOrNull()?.message
        } else {
            null
        }

        RemoteHomeSnapshot(
            isLoading = false,
            isRefreshing = false,
            isOfflineFallback = allRemoteFailed,
            errorMessage = errorMsg,
            quickPicks = quickPicks,
            shelves = remainingShelves,
            chartShelves = charts,
            newReleases = fallbackReleases,
            moodCategories = moods,
            scannedLocalTracks = scannedLocal
        )
    }

    private suspend fun scanAndPersistLocalTracks(): List<Track> {
        if (!localMediaScanner.hasPermission()) return emptyList()
        val existingUris = songDao.getAll()
            .mapNotNull { it.localUri }
            .toHashSet()
        val scanned = localMediaScanner.scan(alreadyImportedUris = existingUris).tracks
        if (scanned.isNotEmpty()) {
            songDao.upsertAndMergeAll(scanned.map { it.toEntity() })
        }
        return scanned
    }

    private suspend fun localTracksForAlbum(album: Album): List<Track> {
        return songDao.getAll()
            .map { it.toModel() }
            .filter { it.albumId == album.id || (!album.title.isBlank() && it.album.equals(album.title, ignoreCase = true)) }
    }

    private fun extractHeroPalette(artworkUrl: String?) {
        paletteJob?.cancel()
        if (artworkUrl.isNullOrBlank()) {
            heroPaletteState.value = OndaArtworkPalette.Default
            return
        }
        paletteJob = viewModelScope.launch(Dispatchers.IO) {
            val extracted = runCatching {
                val loader = SingletonImageLoader.get(appContext)
                OndaPaletteExtractor.extractFromData(appContext, loader, artworkUrl)
            }.getOrDefault(OndaArtworkPalette.Default)
            heroPaletteState.value = extracted
        }
    }

    private fun buildUiState(
        local: HomeSourcesBundle,
        remote: RemoteHomeSnapshot,
        detail: CollectionDetailState?,
        heroPalette: OndaArtworkPalette
    ): HomeUiState {
        val playCounts = local.recentEvents.groupingBy { it.trackId }.eachCount()
        val songMap = local.allSongs.associateBy { it.id }

        val recentlyPlayed = local.recentSongs.ifEmpty {
            local.recentEvents
                .mapNotNull { songMap[it.trackId] }
                .distinctBy { it.id }
                .take(20)
        }

        val mostPlayed = local.allSongs
            .filter { it.totalPlayTimeMs > 0L || (playCounts[it.id] ?: 0) > 0 }
            .sortedWith(
                compareByDescending<Track> { it.totalPlayTimeMs }
                    .thenByDescending { playCounts[it.id] ?: 0 }
            )
            .take(16)

        val localTracks = (local.allSongs.filter { it.source == SourceKind.LOCAL } + remote.scannedLocalTracks)
            .distinctBy { it.id }

        val effectiveQuickPicks = remote.quickPicks.ifEmpty {
            (mostPlayed + recentlyPlayed + localTracks + local.allSongs)
                .distinctBy { it.id }
                .take(16)
        }

        val currentTrack = local.playbackState.currentTrack
        val heroTrack = currentTrack
            ?: recentlyPlayed.firstOrNull()
            ?: effectiveQuickPicks.firstOrNull()
            ?: mostPlayed.firstOrNull()
            ?: localTracks.firstOrNull()

        val strings = resolveHomeStrings(local.settings.languageCode)
        val heroSubtitle = when {
            currentTrack != null -> currentTrack.artist.ifBlank { strings.unknownArtist }
            heroTrack != null -> heroTrack.artist.ifBlank { strings.unknownArtist }
            else -> ""
        }

        val syncedDetail = detail?.let { currentDetail ->
            if (currentDetail.tracks.isEmpty()) {
                currentDetail
            } else {
                val likedMap = songMap
                currentDetail.copy(
                    tracks = currentDetail.tracks.map { track ->
                        val localTrack = likedMap[track.id]
                        if (localTrack != null) track.copy(liked = localTrack.liked) else track
                    }
                )
            }
        }

        return HomeUiState(
            isLoading = remote.isLoading && recentlyPlayed.isEmpty() && localTracks.isEmpty(),
            isRefreshing = remote.isRefreshing,
            isOfflineFallback = remote.isOfflineFallback,
            errorMessage = remote.errorMessage,
            username = local.settings.username,
            avatarPath = local.settings.avatarPath,
            languageCode = local.settings.languageCode,
            dynamicMeshEnabled = local.settings.dynamicMeshEnabled,
            glassBlurEnabled = local.settings.glassBlurEnabled,
            hapticsEnabled = local.settings.hapticsEnabled,
            heroTrack = heroTrack,
            heroSubtitle = heroSubtitle,
            heroPalette = heroPalette,
            recentlyPlayed = recentlyPlayed,
            mostPlayed = mostPlayed,
            quickPicks = effectiveQuickPicks,
            shelves = remote.shelves,
            chartShelves = remote.chartShelves,
            newReleases = remote.newReleases,
            moodCategories = remote.moodCategories,
            localTracks = localTracks,
            activeTrackId = currentTrack?.id,
            isPlaying = local.playbackState.status == PlaybackStatus.PLAYING,
            hasMiniPlayer = currentTrack != null,
            detailSheet = syncedDetail
        )
    }

    private fun buildHeroQueue(hero: Track, state: HomeUiState): List<Track> {
        val candidates = (listOf(hero) + state.quickPicks + state.recentlyPlayed + state.mostPlayed + state.localTracks)
            .distinctBy { it.id }
        return if (candidates.isEmpty()) listOf(hero) else candidates
    }
}
