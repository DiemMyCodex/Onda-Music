package com.onda.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.PlaylistDao
import com.onda.music.core.database.PlaylistSongEntity
import com.onda.music.core.database.SearchHistoryDao
import com.onda.music.core.database.SearchHistoryEntity
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.model.Album
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.SearchResultBundle
import com.onda.music.core.model.Track
import com.onda.music.core.playback.PlaybackController
import com.onda.music.core.sources.LinkImporter
import com.onda.music.core.sources.MonochromeClient
import com.onda.music.core.sources.TrackMatcher
import com.onda.music.core.sources.YtMusicClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ytMusicClient: YtMusicClient,
    private val monochromeClient: MonochromeClient,
    private val linkImporter: LinkImporter,
    private val trackMatcher: TrackMatcher,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val ondaPreferences: OndaPreferences,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryInputFlow = MutableStateFlow("")
    private val likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    private var activeSearchJob: Job? = null
    private var linkImportJob: Job? = null
    private var drillDownJob: Job? = null

    init {
        observeSearchHistory()
        observeLikedSongs()
        observePreferences()
        observePlaybackState()
        observeDebouncedQuery()
        loadMoodCategories()
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            searchHistoryDao.observeRecent(20).collectLatest { entries ->
                _uiState.update { state ->
                    state.copy(history = entries.map { it.query })
                }
            }
        }
    }

    private fun observeLikedSongs() {
        viewModelScope.launch {
            songDao.observeLikedSongs().collectLatest { likedSongs ->
                val ids = likedSongs.mapTo(HashSet(likedSongs.size)) { it.id }
                likedTrackIds.value = ids
                _uiState.update { state -> SearchLogic.applyLikedIds(state, ids) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            ondaPreferences.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        monochromeEnabled = settings.monochromeEnabled,
                        languageCode = settings.languageCode,
                        glassBlurEnabled = settings.glassBlurEnabled
                    )
                }
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            playbackController.playbackState.collectLatest { playback ->
                _uiState.update {
                    it.copy(
                        currentTrackId = playback.currentTrack?.id,
                        isPlaying = playback.status == PlaybackStatus.PLAYING,
                        hasMiniPlayer = playback.currentTrack != null
                    )
                }
            }
        }
    }

    private fun observeDebouncedQuery() {
        viewModelScope.launch {
            queryInputFlow
                .debounce(320L)
                .distinctUntilChanged()
                .collectLatest { rawQuery ->
                    val trimmed = rawQuery.trim()
                    if (trimmed.isEmpty()) {
                        activeSearchJob?.cancel()
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                searchError = null,
                                suggestions = emptyList(),
                                ytResults = SearchResultBundle(),
                                monochromeTracks = emptyList(),
                                localTracks = emptyList()
                            )
                        }
                    } else {
                        executeSearch(trimmed, fetchSuggestions = true)
                    }
                }
        }
    }

    fun loadMoodCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoods = true, moodError = null) }
            val result = withContext(Dispatchers.IO) {
                ytMusicClient.getMoodCategories()
            }
            result.onSuccess { categories ->
                _uiState.update {
                    it.copy(
                        moodCategories = categories,
                        isLoadingMoods = false,
                        moodError = null
                    )
                }
            }.onFailure { error ->
                val strings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update {
                    it.copy(
                        isLoadingMoods = false,
                        moodError = error.message ?: strings.moodCategoriesErrorFallback
                    )
                }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update {
            it.copy(
                query = newQuery,
                isSubmitted = false,
                searchError = null
            )
        }
        queryInputFlow.value = newQuery
        if (newQuery.isBlank()) {
            activeSearchJob?.cancel()
            _uiState.update {
                it.copy(
                    isSearching = false,
                    suggestions = emptyList(),
                    ytResults = SearchResultBundle(),
                    monochromeTracks = emptyList(),
                    localTracks = emptyList()
                )
            }
        }
    }

    fun clearQuery() {
        activeSearchJob?.cancel()
        queryInputFlow.value = ""
        _uiState.update {
            it.copy(
                query = "",
                isSubmitted = false,
                isSearching = false,
                searchError = null,
                suggestions = emptyList(),
                ytResults = SearchResultBundle(),
                monochromeTracks = emptyList(),
                localTracks = emptyList()
            )
        }
    }

    fun submitSearch(explicitQuery: String? = null) {
        val target = (explicitQuery ?: _uiState.value.query).trim()
        if (target.isEmpty()) return
        _uiState.update {
            it.copy(
                query = target,
                isSubmitted = true,
                searchError = null
            )
        }
        queryInputFlow.value = target
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryDao.upsert(
                SearchHistoryEntity(
                    query = target,
                    searchedAtMs = System.currentTimeMillis()
                )
            )
        }
        executeSearch(target, fetchSuggestions = false)
    }

    fun selectSuggestion(suggestion: String) {
        submitSearch(suggestion)
    }

    fun selectHistoryItem(query: String) {
        submitSearch(query)
    }

    fun selectMoodCategory(category: MoodCategory) {
        drillDownJob?.cancel()
        val lang = _uiState.value.languageCode
        val strings = resolveSearchStrings(lang)
        val isVi = lang.startsWith("vi", ignoreCase = true)
        _uiState.update {
            it.copy(
                drillDownState = DrillDownState(
                    target = DrillDownTarget.MoodTarget(category),
                    title = category.title,
                    subtitle = strings.moodStationSubtitle,
                    artworkUrl = null,
                    isLoading = true
                )
            )
        }
        drillDownJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ytMusicClient.getMoodTracks(
                    mood = category,
                    vietnameseHint = isVi
                )
            }
            result.onSuccess { tracks ->
                val synced = SearchLogic.syncLikedFlags(tracks, likedTrackIds.value)
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update { state ->
                    if (state.drillDownState.target !is DrillDownTarget.MoodTarget) {
                        state
                    } else {
                        state.copy(
                            drillDownState = state.drillDownState.copy(
                                subtitle = if (synced.isNotEmpty()) {
                                    activeStrings.trackCountLabel(synced.size)
                                } else {
                                    activeStrings.moodStationSubtitle
                                },
                                artworkUrl = synced.firstOrNull()?.artworkUrl,
                                tracks = synced,
                                isLoading = false,
                                errorMessage = null
                            )
                        )
                    }
                }
            }.onFailure { error ->
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update { state ->
                    if (state.drillDownState.target !is DrillDownTarget.MoodTarget) {
                        state
                    } else {
                        state.copy(
                            drillDownState = state.drillDownState.copy(
                                isLoading = false,
                                errorMessage = error.message ?: activeStrings.moodTracksErrorFallback
                            )
                        )
                    }
                }
            }
        }
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryDao.deleteAll()
        }
    }

    fun selectFilter(filter: SearchSourceFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
        val currentQuery = _uiState.value.query.trim()
        if (filter == SearchSourceFilter.MONOCHROME &&
            currentQuery.isNotEmpty() &&
            _uiState.value.monochromeTracks.isEmpty() &&
            !_uiState.value.isSearching
        ) {
            executeSearch(currentQuery, fetchSuggestions = false, forceMonochrome = true)
        }
    }

    private fun executeSearch(
        query: String,
        fetchSuggestions: Boolean,
        forceMonochrome: Boolean = false
    ) {
        activeSearchJob?.cancel()
        activeSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            val currentState = _uiState.value
            val searchMonochrome = forceMonochrome ||
                currentState.monochromeEnabled ||
                currentState.activeFilter == SearchSourceFilter.MONOCHROME

            try {
                coroutineScope {
                    val suggestionsDeferred: Deferred<List<String>>? = if (fetchSuggestions) {
                        async(Dispatchers.IO) {
                            ytMusicClient.searchSuggestions(query).getOrDefault(emptyList())
                        }
                    } else {
                        null
                    }
                    val localDeferred = async(Dispatchers.IO) {
                        songDao.searchSongs(query).first().map { it.toModel() }
                    }
                    val ytDeferred = async(Dispatchers.IO) {
                        ytMusicClient.search(query)
                    }
                    val monoDeferred: Deferred<Result<List<Track>>>? = if (searchMonochrome) {
                        async(Dispatchers.IO) {
                            monochromeClient.searchTracks(query)
                        }
                    } else {
                        null
                    }

                    val localResult = runCatching { localDeferred.await() }.getOrDefault(emptyList())
                    val ytResult = ytDeferred.await()
                    val monoResult = monoDeferred?.await()
                    val suggestions = suggestionsDeferred?.await()

                    val ids = likedTrackIds.value
                    val ytBundle = SearchLogic.syncBundleLikedFlags(
                        ytResult.getOrElse { SearchResultBundle() },
                        ids
                    )
                    val monoTracks = SearchLogic.syncLikedFlags(
                        monoResult?.getOrElse { emptyList() }.orEmpty(),
                        ids
                    )
                    val syncedLocal = SearchLogic.syncLikedFlags(localResult, ids)
                    val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                    val combinedError = if (
                        ytResult.isFailure &&
                        (monoResult == null || monoResult.isFailure) &&
                        syncedLocal.isEmpty()
                    ) {
                        ytResult.exceptionOrNull()?.message
                            ?: monoResult?.exceptionOrNull()?.message
                            ?: activeStrings.searchFailedFallback
                    } else {
                        null
                    }

                    _uiState.update { state ->
                        if (state.query.trim() != query) {
                            state
                        } else {
                            state.copy(
                                isSearching = false,
                                searchError = combinedError,
                                ytResults = ytBundle,
                                monochromeTracks = monoTracks,
                                localTracks = syncedLocal,
                                suggestions = suggestions ?: if (state.isSubmitted) emptyList() else state.suggestions
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = e.message ?: activeStrings.searchFailedFallback
                    )
                }
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val safeQueue = if (queue.isEmpty()) listOf(track) else queue
        val startIndex = safeQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playQueue(safeQueue, startIndex)
    }

    fun toggleLike(track: Track) {
        val targetLiked = !track.liked
        val entity = track.toEntity().let { base ->
            if (base.addedAtMs > 0L) base else base.copy(addedAtMs = System.currentTimeMillis())
        }
        viewModelScope.launch(Dispatchers.IO) {
            songDao.toggleLikedOrInsert(entity, targetLiked)
        }
        val nextIds = if (targetLiked) likedTrackIds.value + track.id else likedTrackIds.value - track.id
        likedTrackIds.value = nextIds
        _uiState.update { state -> SearchLogic.applyLikedIds(state, nextIds) }
    }

    fun openAlbum(album: Album) {
        drillDownJob?.cancel()
        _uiState.update {
            it.copy(
                drillDownState = DrillDownState(
                    target = DrillDownTarget.AlbumTarget(album),
                    title = album.title,
                    subtitle = album.artist,
                    artworkUrl = album.artworkUrl,
                    isLoading = true
                )
            )
        }
        drillDownJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ytMusicClient.getAlbumTracks(album.id)
            }
            result.onSuccess { (resolvedAlbum, tracks) ->
                val synced = SearchLogic.syncLikedFlags(tracks, likedTrackIds.value)
                _uiState.update { state ->
                    state.copy(
                        drillDownState = state.drillDownState.copy(
                            title = resolvedAlbum.title.ifBlank { album.title },
                            subtitle = resolvedAlbum.artist.ifBlank { album.artist },
                            artworkUrl = resolvedAlbum.artworkUrl ?: album.artworkUrl,
                            tracks = synced,
                            isLoading = false,
                            errorMessage = null
                        )
                    )
                }
            }.onFailure { error ->
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update { state ->
                    state.copy(
                        drillDownState = state.drillDownState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: activeStrings.albumTracksErrorFallback
                        )
                    )
                }
            }
        }
    }

    fun openPlaylist(playlist: Playlist) {
        drillDownJob?.cancel()
        val initialStrings = resolveSearchStrings(_uiState.value.languageCode)
        _uiState.update {
            it.copy(
                drillDownState = DrillDownState(
                    target = DrillDownTarget.PlaylistTarget(playlist),
                    title = playlist.name,
                    subtitle = if (playlist.trackCount > 0) {
                        initialStrings.trackCountLabel(playlist.trackCount)
                    } else {
                        initialStrings.playlistLabel
                    },
                    artworkUrl = playlist.artworkUrl,
                    isLoading = true
                )
            )
        }
        drillDownJob = viewModelScope.launch {
            val localTracks = withContext(Dispatchers.IO) {
                playlistDao.observeSongsInPlaylist(playlist.id).first().map { it.toModel() }
            }
            if (localTracks.isNotEmpty()) {
                val syncedLocal = SearchLogic.syncLikedFlags(localTracks, likedTrackIds.value)
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update { state ->
                    state.copy(
                        drillDownState = state.drillDownState.copy(
                            subtitle = activeStrings.trackCountLabel(syncedLocal.size),
                            tracks = syncedLocal,
                            isLoading = false,
                            errorMessage = null
                        )
                    )
                }
                return@launch
            }
            val remoteId = playlist.remoteId ?: playlist.id.removePrefix("yt_pl_")
            val result = withContext(Dispatchers.IO) {
                ytMusicClient.getPlaylistTracks(remoteId)
            }
            result.onSuccess { (resolvedPlaylist, tracks) ->
                val synced = SearchLogic.syncLikedFlags(tracks, likedTrackIds.value)
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update { state ->
                    state.copy(
                        drillDownState = state.drillDownState.copy(
                            title = resolvedPlaylist.name.ifBlank { playlist.name },
                            subtitle = activeStrings.trackCountLabel(synced.size),
                            artworkUrl = resolvedPlaylist.artworkUrl ?: playlist.artworkUrl,
                            tracks = synced,
                            isLoading = false,
                            errorMessage = null
                        )
                    )
                }
            }.onFailure { error ->
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update { state ->
                    state.copy(
                        drillDownState = state.drillDownState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: activeStrings.playlistTracksErrorFallback
                        )
                    )
                }
            }
        }
    }

    fun retryDrillDown() {
        when (val target = _uiState.value.drillDownState.target) {
            is DrillDownTarget.AlbumTarget -> openAlbum(target.album)
            is DrillDownTarget.PlaylistTarget -> openPlaylist(target.playlist)
            is DrillDownTarget.MoodTarget -> selectMoodCategory(target.mood)
            null -> Unit
        }
    }

    fun dismissDrillDown() {
        drillDownJob?.cancel()
        _uiState.update { it.copy(drillDownState = DrillDownState()) }
    }

    fun playDrillDownTracks(startIndex: Int = 0, shuffle: Boolean = false) {
        val tracks = _uiState.value.drillDownState.tracks
        if (tracks.isEmpty()) return
        val queue = if (shuffle) tracks.shuffled() else tracks
        val safeStart = if (shuffle) 0 else startIndex.coerceIn(0, queue.lastIndex)
        playbackController.playQueue(queue, safeStart)
    }

    fun openLinkSheet() {
        _uiState.update { it.copy(linkSheetVisible = true) }
    }

    fun dismissLinkSheet() {
        _uiState.update { it.copy(linkSheetVisible = false) }
    }

    fun onLinkInputChange(rawValue: String) {
        val detected = linkImporter.detect(rawValue)?.provider
        _uiState.update {
            val resetProgress = if (it.linkImportProgress.stage == LinkImportStage.ERROR ||
                it.linkImportProgress.stage == LinkImportStage.SUCCESS
            ) {
                LinkImportProgress()
            } else {
                it.linkImportProgress
            }
            it.copy(
                linkInput = rawValue,
                detectedProvider = detected,
                linkImportProgress = resetProgress
            )
        }
    }

    fun resetLinkImport() {
        linkImportJob?.cancel()
        _uiState.update {
            it.copy(
                linkInput = "",
                detectedProvider = null,
                linkImportProgress = LinkImportProgress()
            )
        }
    }

    fun startLinkImport() {
        val rawUrl = _uiState.value.linkInput.trim()
        val initialStrings = resolveSearchStrings(_uiState.value.languageCode)
        if (rawUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    linkImportProgress = LinkImportProgress(
                        stage = LinkImportStage.ERROR,
                        errorMessage = initialStrings.emptyLinkError
                    )
                )
            }
            return
        }
        linkImportJob?.cancel()
        linkImportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    linkImportProgress = LinkImportProgress(stage = LinkImportStage.FETCHING)
                )
            }
            try {
                val collection = withContext(Dispatchers.IO) {
                    linkImporter.importCollection(rawUrl).getOrThrow()
                }
                _uiState.update {
                    it.copy(
                        linkImportProgress = LinkImportProgress(
                            stage = LinkImportStage.MATCHING,
                            processedCount = 0,
                            totalCount = collection.tracks.size,
                            matchedCount = 0,
                            collectionTitle = collection.title,
                            artworkUrl = collection.artworkUrl
                        )
                    )
                }

                val preferMonochrome = _uiState.value.monochromeEnabled
                val rawMatchedTracks = withContext(Dispatchers.IO) {
                    SearchLogic.matchImportedCollection(
                        collection = collection,
                        preferMonochrome = preferMonochrome,
                        trackMatcher = trackMatcher,
                        searchMonochrome = { q -> monochromeClient.searchTracks(q, 6).getOrDefault(emptyList()) },
                        searchYtMusic = { q -> ytMusicClient.searchTracks(q).getOrDefault(emptyList()) },
                        onProgress = { processed, matched ->
                            _uiState.update { state ->
                                state.copy(
                                    linkImportProgress = state.linkImportProgress.copy(
                                        processedCount = processed,
                                        matchedCount = matched
                                    )
                                )
                            }
                        }
                    )
                }
                val matchedTracks = SearchLogic.syncLikedFlags(rawMatchedTracks, likedTrackIds.value)
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)

                if (matchedTracks.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            linkImportProgress = LinkImportProgress(
                                stage = LinkImportStage.ERROR,
                                collectionTitle = collection.title,
                                artworkUrl = collection.artworkUrl,
                                errorMessage = activeStrings.noMatchingTracksFound(collection.title)
                            )
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        linkImportProgress = it.linkImportProgress.copy(stage = LinkImportStage.SAVING)
                    )
                }

                val savedPlaylist = withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val parsed = linkImporter.detect(rawUrl)
                    val playlistId = SearchLogic.buildImportedPlaylistId(parsed, collection.title, now)
                    val songEntities = matchedTracks.map { track ->
                        track.copy(
                            inLibrary = true,
                            addedAtMs = if (track.addedAtMs > 0L) track.addedAtMs else now
                        ).toEntity()
                    }
                    songDao.upsertAndMergeAll(songEntities)
                    val playlist = Playlist(
                        id = playlistId,
                        name = collection.title,
                        description = collection.description
                            ?: activeStrings.importedFromProvider(SearchLogic.providerLabel(collection.provider)),
                        artworkUrl = collection.artworkUrl ?: matchedTracks.firstOrNull()?.artworkUrl,
                        trackCount = matchedTracks.size,
                        createdAtMs = now,
                        remoteId = parsed?.externalId
                    )
                    playlistDao.upsert(playlist.toEntity())
                    val entries = matchedTracks.mapIndexed { index, track ->
                        PlaylistSongEntity(
                            playlistId = playlistId,
                            songId = track.id,
                            position = index,
                            addedAtMs = now
                        )
                    }
                    playlistDao.upsertPlaylistSongs(entries)
                    playlistDao.refreshTrackCount(playlistId)
                    playlist
                }

                _uiState.update {
                    it.copy(
                        linkImportProgress = LinkImportProgress(
                            stage = LinkImportStage.SUCCESS,
                            processedCount = collection.tracks.size,
                            totalCount = collection.tracks.size,
                            matchedCount = matchedTracks.size,
                            collectionTitle = savedPlaylist.name,
                            artworkUrl = savedPlaylist.artworkUrl,
                            savedPlaylist = savedPlaylist,
                            matchedTracks = matchedTracks
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val activeStrings = resolveSearchStrings(_uiState.value.languageCode)
                _uiState.update {
                    it.copy(
                        linkImportProgress = LinkImportProgress(
                            stage = LinkImportStage.ERROR,
                            errorMessage = e.message ?: activeStrings.importLinkFailedFallback
                        )
                    )
                }
            }
        }
    }

    fun playImportedPlaylist() {
        val tracks = _uiState.value.linkImportProgress.matchedTracks
        if (tracks.isEmpty()) return
        playbackController.playQueue(tracks, 0)
        dismissLinkSheet()
    }

    fun inspectImportedPlaylist() {
        val progress = _uiState.value.linkImportProgress
        val playlist = progress.savedPlaylist ?: return
        val tracks = SearchLogic.syncLikedFlags(progress.matchedTracks, likedTrackIds.value)
        val strings = resolveSearchStrings(_uiState.value.languageCode)
        dismissLinkSheet()
        _uiState.update {
            it.copy(
                drillDownState = DrillDownState(
                    target = DrillDownTarget.PlaylistTarget(playlist),
                    title = playlist.name,
                    subtitle = strings.trackCountLabel(tracks.size),
                    artworkUrl = playlist.artworkUrl,
                    tracks = tracks,
                    isLoading = false
                )
            )
        }
    }
}
