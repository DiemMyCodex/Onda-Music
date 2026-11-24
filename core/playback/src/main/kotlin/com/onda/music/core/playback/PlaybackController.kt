package com.onda.music.core.playback

import android.content.ComponentName
import android.content.Context
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.model.AudioOutputDevice
import com.onda.music.core.model.AudioOutputEqProfile
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.OutputProfileSwitchEvent
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@UnstableApi
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: PlaybackSessionStore,
    private val streamResolver: StreamResolver,
    private val audioEffectsEngine: AudioEffectsEngine,
    private val audioOutputProfileManager: AudioOutputProfileManager,
    private val ondaPreferences: OndaPreferences,
    private val songDao: SongDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerMutex = Mutex()
    private var mediaController: MediaController? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerDeadlineMs: Long = 0L

    val playbackState: StateFlow<PlaybackState> = sessionStore.playbackState
    val activePlayerFlow: StateFlow<Player?> = sessionStore.activePlayer
    val eqConfig: StateFlow<EqConfig> = audioEffectsEngine.eqConfig
    val activeOutputDevice: StateFlow<AudioOutputDevice> = audioOutputProfileManager.activeOutputDevice
    val savedOutputProfiles: StateFlow<Map<String, AudioOutputEqProfile>> = audioOutputProfileManager.savedProfiles
    val outputProfileSwitchEvents: SharedFlow<OutputProfileSwitchEvent> = audioOutputProfileManager.profileSwitchEvents

    fun getActivePlayer(): Player? {
        return sessionStore.activePlayer.value ?: mediaController
    }

    fun attachVideoSurface(surface: Surface?) {
        val player = getActivePlayer() ?: return
        if (surface == null) {
            player.clearVideoSurface()
        } else {
            player.setVideoSurface(surface)
        }
    }

    fun attachVideoSurfaceView(surfaceView: SurfaceView?) {
        val player = getActivePlayer() ?: return
        if (surfaceView == null) {
            player.clearVideoSurface()
        } else {
            player.setVideoSurfaceView(surfaceView)
        }
    }

    fun attachVideoTextureView(textureView: TextureView?) {
        val player = getActivePlayer() ?: return
        if (textureView == null) {
            player.clearVideoSurface()
        } else {
            player.setVideoTextureView(textureView)
        }
    }

    fun warmUp() {
        scope.launch {
            ensureController()
        }
    }

    fun playTrack(track: Track) {
        playQueue(listOf(track), startIndex = 0)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val targetTrack = tracks[safeIndex]
        sessionStore.registerTracks(tracks)
        tracks.forEach { streamResolver.resetRotationState(it.id) }
        sessionStore.updateState { existing ->
            existing.copy(
                currentTrack = targetTrack,
                queue = tracks,
                currentIndex = safeIndex,
                status = PlaybackStatus.BUFFERING,
                positionMs = 0L,
                durationMs = targetTrack.durationMs,
                errorMessage = null
            )
        }
        scope.launch(Dispatchers.IO) {
            streamResolver.prefetch(targetTrack)
        }
        scope.launch(Dispatchers.IO) {
            songDao.upsertAndMergeAll(tracks.map { it.toEntity() })
        }
        scope.launch {
            val controller = ensureController() ?: return@launch
            val mediaItems = tracks.map { OndaMediaService.trackToMediaItem(it) }
            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val controller = ensureController() ?: return@launch
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                    controller.prepare()
                }
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            val controller = ensureController() ?: return@launch
            controller.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    fun skipNext() {
        scope.launch {
            val controller = ensureController() ?: return@launch
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else if (controller.mediaItemCount > 0) {
                controller.seekTo(0, 0L)
            }
            controller.play()
        }
    }

    fun skipPrevious() {
        scope.launch {
            val controller = ensureController() ?: return@launch
            if (controller.currentPosition > 3_000L || !controller.hasPreviousMediaItem()) {
                controller.seekTo(0L)
            } else {
                controller.seekToPreviousMediaItem()
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        scope.launch {
            val controller = ensureController() ?: return@launch
            val count = controller.mediaItemCount
            if (fromIndex !in 0 until count || toIndex !in 0 until count || fromIndex == toIndex) {
                return@launch
            }
            controller.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun removeQueueItem(index: Int) {
        scope.launch {
            val controller = ensureController() ?: return@launch
            if (index !in 0 until controller.mediaItemCount) return@launch
            controller.removeMediaItem(index)
        }
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        scope.launch {
            ondaPreferences.setRepeatMode(repeatMode)
            val controller = ensureController() ?: return@launch
            controller.repeatMode = when (repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
        }
    }

    fun toggleShuffle() {
        scope.launch {
            val controller = ensureController() ?: return@launch
            val next = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = next
            ondaPreferences.setShuffleEnabled(next)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        setPlaybackSpeedAndPitch(speed, playbackState.value.playbackPitch)
    }

    fun setPlaybackPitch(pitch: Float) {
        setPlaybackSpeedAndPitch(playbackState.value.playbackSpeed, pitch)
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        val safeSpeed = (speed.takeIf { it.isFinite() } ?: 1.0f).coerceIn(0.5f, 2.0f)
        val safePitch = (pitch.takeIf { it.isFinite() } ?: 1.0f).coerceIn(0.5f, 2.0f)
        sessionStore.updateState {
            it.copy(playbackSpeed = safeSpeed, playbackPitch = safePitch)
        }
        scope.launch {
            ondaPreferences.setPlaybackSpeedAndPitch(safeSpeed, safePitch)
            ensureController()?.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
        }
    }

    fun resetPlaybackSpeedAndPitch() {
        setPlaybackSpeedAndPitch(1.0f, 1.0f)
    }

    fun playNext(track: Track) {
        enqueueTrack(track, playNext = true)
    }

    fun addToQueue(track: Track) {
        enqueueTrack(track, playNext = false)
    }

    private fun enqueueTrack(track: Track, playNext: Boolean) {
        sessionStore.registerTracks(listOf(track))
        streamResolver.resetRotationState(track.id)
        scope.launch(Dispatchers.IO) {
            songDao.upsertAndMerge(track.toEntity())
        }
        scope.launch {
            val controller = ensureController() ?: return@launch
            if (controller.mediaItemCount == 0) {
                playTrack(track)
            } else if (playNext) {
                val insertIndex = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
                controller.addMediaItem(insertIndex, OndaMediaService.trackToMediaItem(track))
            } else {
                controller.addMediaItem(OndaMediaService.trackToMediaItem(track))
            }
        }
    }

    fun clearUpNext() {
        scope.launch {
            val controller = ensureController() ?: return@launch
            val startRemoveIndex = controller.currentMediaItemIndex.coerceAtLeast(0) + 1
            for (index in (controller.mediaItemCount - 1) downTo startRemoveIndex) {
                controller.removeMediaItem(index)
            }
        }
    }

    fun startSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        if (durationMs <= 0L) {
            cancelSleepTimer()
            return
        }
        val deadlineMs = System.currentTimeMillis() + durationMs
        sleepTimerDeadlineMs = deadlineMs
        sessionStore.updateState {
            it.copy(
                sleepTimerRemainingMs = durationMs,
                sleepTimerEndOfTrack = false,
                sleepTimerFading = durationMs <= SleepTimerFadeCurve.FADE_DURATION_MS
            )
        }
        sleepTimerJob = scope.launch {
            while (isActive) {
                val remaining = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
                val fading = remaining in 1L..SleepTimerFadeCurve.FADE_DURATION_MS
                sessionStore.updateState {
                    it.copy(
                        sleepTimerRemainingMs = remaining,
                        sleepTimerFading = fading
                    )
                }
                if (remaining == 0L) {
                    sleepTimerDeadlineMs = 0L
                    val player = getActivePlayer()
                    player?.pause()
                    player?.volume = 1.0f
                    sessionStore.updateState {
                        it.copy(
                            sleepTimerRemainingMs = null,
                            sleepTimerEndOfTrack = false,
                            sleepTimerFading = false
                        )
                    }
                    break
                }
                val tickDelay = if (remaining <= SleepTimerFadeCurve.FADE_DURATION_MS + 1_000L) 200L else 1_000L
                delay(tickDelay)
            }
        }
    }

    fun startSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerDeadlineMs = 0L
        sessionStore.updateState {
            it.copy(
                sleepTimerRemainingMs = null,
                sleepTimerEndOfTrack = true,
                sleepTimerFading = false
            )
        }
    }

    fun adjustSleepTimer(deltaMs: Long) {
        val currentRemaining = playbackState.value.sleepTimerRemainingMs
        if (sleepTimerDeadlineMs > 0L || currentRemaining != null) {
            val baseRemaining = currentRemaining
                ?: (sleepTimerDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val newRemaining = baseRemaining + deltaMs
            if (newRemaining <= 0L) {
                cancelSleepTimer()
            } else {
                startSleepTimer(newRemaining)
            }
        } else if (deltaMs > 0L) {
            startSleepTimer(deltaMs)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerDeadlineMs = 0L
        getActivePlayer()?.volume = 1.0f
        sessionStore.updateState {
            it.copy(
                sleepTimerRemainingMs = null,
                sleepTimerEndOfTrack = false,
                sleepTimerFading = false
            )
        }
    }

    fun updateEqConfig(config: EqConfig) {
        audioEffectsEngine.applyEqConfig(config)
        audioOutputProfileManager.recordUserEqChange(config)
        scope.launch(Dispatchers.IO) {
            ondaPreferences.setEqConfig(config)
        }
    }

    private suspend fun ensureController(): MediaController? {
        mediaController?.let { existing ->
            if (existing.isConnected) return existing
        }
        return controllerMutex.withLock {
            mediaController?.let { existing ->
                if (existing.isConnected) return@withLock existing
            }
            val token = SessionToken(
                context,
                ComponentName(context, OndaMediaService::class.java)
            )
            runCatching {
                MediaController.Builder(context, token).buildAsync().await()
            }.onSuccess { built ->
                mediaController = built
            }.getOrNull()
        }
    }
}
