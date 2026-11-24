package com.onda.music.feature.player

import android.content.Context
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.design.ArtworkUrlResolver
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaPaletteExtractor
import com.onda.music.core.lyrics.LyricsRepository
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.PlayerStudioConfig
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.Track
import com.onda.music.core.playback.DownloadManager
import com.onda.music.core.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackController: PlaybackController,
    private val downloadManager: DownloadManager,
    private val lyricsRepository: LyricsRepository,
    private val ondaPreferences: OndaPreferences,
    private val songDao: SongDao
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    val activePlayerFlow: StateFlow<Player?> = playbackController.activePlayerFlow

    val eqConfig: StateFlow<EqConfig> = playbackController.eqConfig

    val userSettings: StateFlow<OndaUserSettings> = ondaPreferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = OndaUserSettings()
    )

    private val _artworkPalette = MutableStateFlow(OndaArtworkPalette.Default)
    val artworkPalette: StateFlow<OndaArtworkPalette> = _artworkPalette.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError.asStateFlow()

    private val _likeBurstTrigger = MutableStateFlow(0L)
    val likeBurstTrigger: StateFlow<Long> = _likeBurstTrigger.asStateFlow()

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val lyricsDocument: StateFlow<LyricsDocument?> = playbackState
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId.isNullOrBlank()) {
                flowOf(null)
            } else {
                lyricsRepository.observeLyrics(trackId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val currentTrackLiked: StateFlow<Boolean> = playbackState
        .map { it.currentTrack }
        .distinctUntilChanged { old, new -> old?.id == new?.id && old?.liked == new?.liked }
        .flatMapLatest { track ->
            if (track == null) {
                flowOf(false)
            } else {
                songDao.observeById(track.id).map { entity -> entity?.liked ?: track.liked }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val currentTrackLyricsOffsetMs: StateFlow<Long> = playbackState
        .map { it.currentTrack }
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { track ->
            if (track == null) {
                flowOf(0L)
            } else {
                songDao.observeById(track.id).map { entity -> entity?.lyricsOffsetMs ?: track.lyricsOffsetMs }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0L
        )

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val currentDownload: StateFlow<DownloadItem?> = playbackState
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId.isNullOrBlank()) {
                flowOf(null)
            } else {
                downloadManager.observeDownload(trackId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    private data class CachedPaletteEntry(
        val artworkUrl: String,
        val palette: OndaArtworkPalette
    )

    private val paletteCache = object : LinkedHashMap<String, CachedPaletteEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedPaletteEntry>?): Boolean {
            return size > 64
        }
    }

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack?.id to it.currentTrack?.artworkUrl }
                .distinctUntilChanged()
                .collectLatest { (trackId, artworkUrl) ->
                    if (trackId.isNullOrBlank() || artworkUrl.isNullOrBlank()) {
                        _artworkPalette.value = OndaArtworkPalette.Default
                    } else {
                        val cached = synchronized(paletteCache) {
                            paletteCache[trackId]?.takeIf { it.artworkUrl == artworkUrl }?.palette
                        }
                        if (cached != null) {
                            _artworkPalette.value = cached
                        } else {
                            val extracted = withContext(Dispatchers.IO) {
                                val loader = SingletonImageLoader.get(context)
                                val upgradedUrl = ArtworkUrlResolver.upgradeHighRes(artworkUrl) ?: artworkUrl
                                val primaryResult = OndaPaletteExtractor.extractFromData(
                                    context = context,
                                    imageLoader = loader,
                                    data = upgradedUrl
                                )
                                if (primaryResult != OndaArtworkPalette.Default || upgradedUrl == artworkUrl) {
                                    primaryResult
                                } else {
                                    OndaPaletteExtractor.extractFromData(
                                        context = context,
                                        imageLoader = loader,
                                        data = artworkUrl
                                    )
                                }
                            }
                            synchronized(paletteCache) {
                                paletteCache[trackId] = CachedPaletteEntry(artworkUrl, extracted)
                            }
                            _artworkPalette.value = extracted
                        }
                    }
                }
        }

        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    if (track == null) {
                        _isLyricsLoading.value = false
                        _lyricsError.value = null
                    } else {
                        loadLyricsForTrack(track, forceRefresh = false)
                    }
                }
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun seekToFraction(fraction: Float) {
        val duration = playbackState.value.durationMs
        if (duration > 0L) {
            val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
            playbackController.seekTo(target)
        }
    }

    fun skipNext() {
        playbackController.skipNext()
    }

    fun skipPrevious() {
        playbackController.skipPrevious()
    }

    fun skipToQueueIndex(index: Int) {
        val queue = playbackState.value.queue
        if (index !in queue.indices) return
        val activePlayer = playbackController.getActivePlayer()
        if (activePlayer != null && index < activePlayer.mediaItemCount) {
            activePlayer.seekTo(index, 0L)
            activePlayer.play()
        } else {
            playbackController.playQueue(queue, startIndex = index)
        }
    }

    fun toggleLike() {
        val track = playbackState.value.currentTrack ?: return
        val nextLiked = !currentTrackLiked.value
        if (nextLiked) {
            _likeBurstTrigger.value += 1L
        }
        viewModelScope.launch {
            val existing = songDao.getById(track.id)
            if (existing == null) {
                songDao.upsertAndMerge(track.copy(liked = nextLiked).toEntity())
            } else {
                songDao.setLiked(track.id, nextLiked)
            }
        }
    }

    fun downloadCurrentTrack() {
        val track = playbackState.value.currentTrack ?: return
        val current = currentDownload.value
        viewModelScope.launch {
            when (current?.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    downloadManager.cancelDownload(track.id)
                }
                DownloadStatus.COMPLETED -> {
                    downloadManager.deleteDownload(track.id)
                }
                else -> {
                    downloadManager.queueDownload(track)
                }
            }
        }
    }

    fun switchPlayerStyle(style: PlayerStyle) {
        viewModelScope.launch {
            ondaPreferences.setPlayerStyle(style)
        }
    }

    fun toggleRepeatMode() {
        val next = when (playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackController.setRepeatMode(next)
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackController.moveQueueItem(fromIndex, toIndex)
    }

    fun removeQueueItem(index: Int) {
        playbackController.removeQueueItem(index)
    }

    fun startSleepTimer(durationMs: Long) {
        playbackController.startSleepTimer(durationMs)
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
    }

    fun refreshLyrics() {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            loadLyricsForTrack(track, forceRefresh = true)
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        val track = playbackState.value.currentTrack ?: return
        val nextOffset = (currentTrackLyricsOffsetMs.value + deltaMs).coerceIn(-15_000L, 15_000L)
        viewModelScope.launch {
            val existing = songDao.getById(track.id)
            if (existing == null) {
                songDao.upsertAndMerge(track.copy(lyricsOffsetMs = nextOffset).toEntity())
            } else {
                songDao.setLyricsOffset(track.id, nextOffset)
            }
        }
    }

    fun setLyricsOffsetMs(offsetMs: Long) {
        val track = playbackState.value.currentTrack ?: return
        val nextOffset = offsetMs.coerceIn(-15_000L, 15_000L)
        viewModelScope.launch {
            val existing = songDao.getById(track.id)
            if (existing == null) {
                songDao.upsertAndMerge(track.copy(lyricsOffsetMs = nextOffset).toEntity())
            } else {
                songDao.setLyricsOffset(track.id, nextOffset)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
    }

    fun setPlaybackPitch(pitch: Float) {
        playbackController.setPlaybackPitch(pitch)
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        playbackController.setPlaybackSpeedAndPitch(speed, pitch)
    }

    fun setPersistPlaybackSpeedGlobally(enabled: Boolean) {
        playbackController.setPersistPlaybackSpeedGlobally(enabled)
    }

    fun resetPlaybackSpeedAndPitch() {
        playbackController.resetPlaybackSpeedAndPitch()
    }

    fun updateStudioConfig(config: PlayerStudioConfig) {
        viewModelScope.launch {
            ondaPreferences.setStudioConfig(config)
        }
    }

    fun playNextTrack(track: Track) {
        playbackController.playNext(track)
    }

    fun addTrackToQueue(track: Track) {
        playbackController.addToQueue(track)
    }

    fun clearUpNext() {
        playbackController.clearUpNext()
    }

    fun startSleepTimerEndOfTrack() {
        playbackController.startSleepTimerEndOfTrack()
    }

    fun adjustSleepTimer(deltaMs: Long) {
        playbackController.adjustSleepTimer(deltaMs)
    }

    fun toggleLikeForTrack(track: Track) {
        if (track.id == playbackState.value.currentTrack?.id) {
            toggleLike()
            return
        }
        viewModelScope.launch {
            val existing = songDao.getById(track.id)
            val nextLiked = !(existing?.liked ?: track.liked)
            if (existing == null) {
                songDao.upsertAndMerge(track.copy(liked = nextLiked).toEntity())
            } else {
                songDao.setLiked(track.id, nextLiked)
            }
        }
    }

    fun downloadTrack(track: Track) {
        if (track.id == playbackState.value.currentTrack?.id) {
            downloadCurrentTrack()
            return
        }
        viewModelScope.launch {
            downloadManager.queueDownload(track)
        }
    }

    fun getActivePlayer(): Player? = playbackController.getActivePlayer()

    fun attachVideoSurfaceView(surfaceView: SurfaceView?) {
        playbackController.attachVideoSurfaceView(surfaceView)
    }

    fun attachVideoTextureView(textureView: TextureView?) {
        playbackController.attachVideoTextureView(textureView)
    }

    private suspend fun loadLyricsForTrack(track: Track, forceRefresh: Boolean) {
        _isLyricsLoading.value = true
        _lyricsError.value = null
        val result = lyricsRepository.getLyrics(track, forceRefresh = forceRefresh)
        if (result.isFailure) {
            _lyricsError.value = result.exceptionOrNull()?.message ?: "Lyrics unavailable"
        }
        _isLyricsLoading.value = false
    }
}
