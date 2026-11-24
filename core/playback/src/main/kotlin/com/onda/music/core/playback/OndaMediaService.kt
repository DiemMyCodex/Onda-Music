package com.onda.music.core.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.PlayEventDao
import com.onda.music.core.database.PlayEventEntity
import com.onda.music.core.database.PlaybackCheckpoint
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toModel
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.Track
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

@UnstableApi
@AndroidEntryPoint
class OndaMediaService : MediaLibraryService() {

    @Inject lateinit var streamResolver: StreamResolver
    @Inject lateinit var audioEffectsEngine: AudioEffectsEngine
    @Inject lateinit var ondaPreferences: OndaPreferences
    @Inject lateinit var songDao: SongDao
    @Inject lateinit var playEventDao: PlayEventDao
    @Inject lateinit var sessionStore: PlaybackSessionStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var checkpointJob: Job? = null
    private var positionTickerJob: Job? = null

    private var currentTrackStartTimeMs: Long = 0L
    private var lastTrackedId: String? = null

    override fun onCreate() {
        super.onCreate()
        initLocks()
        initPlayer()
        initMediaSession()
        observeSettings()
        restoreCheckpointIfNeeded()
        startPositionTicker()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistCheckpointNow()
        if (!::player.isInitialized || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistCheckpointNow()
        releaseLocks()
        checkpointJob?.cancel()
        positionTickerJob?.cancel()
        sessionStore.setActivePlayer(null)
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        if (::player.isInitialized) {
            player.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    fun attachVideoSurface(surface: Surface?) {
        if (::player.isInitialized) {
            player.setVideoSurface(surface)
        }
    }

    fun attachVideoSurfaceView(surfaceView: SurfaceView?) {
        if (::player.isInitialized) {
            if (surfaceView == null) {
                player.clearVideoSurface()
            } else {
                player.setVideoSurfaceView(surfaceView)
            }
        }
    }

    fun attachVideoTextureView(textureView: TextureView?) {
        if (::player.isInitialized) {
            if (textureView == null) {
                player.clearVideoSurface()
            } else {
                player.setVideoTextureView(textureView)
            }
        }
    }

    private fun initLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "onda:playback_wake_lock"
        )?.apply {
            setReferenceCounted(false)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiLockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager?.createWifiLock(wifiLockMode, "onda:playback_wifi_lock")?.apply {
            setReferenceCounted(false)
        }
    }

    private fun initPlayer() {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)

        val upstreamFactory: DataSource.Factory = DefaultDataSource.Factory(this, httpFactory)

        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            resolveDataSpec(dataSpec)
        }

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(audioEffectsEngine.eqProcessor))
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(playerListener)
        sessionStore.setActivePlayer(player)
    }

    private fun initMediaSession() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                NOTIFICATION_CHANNEL_ID,
                R.string.onda_playback_channel_name
            )
        )

        val builder = MediaLibrarySession.Builder(this, player, librarySessionCallback)
        if (sessionActivity != null) {
            builder.setSessionActivity(sessionActivity)
        }
        mediaLibrarySession = builder.build()
    }

    private fun observeSettings() {
        serviceScope.launch {
            ondaPreferences.settings.collectLatest { userSettings ->
                audioEffectsEngine.applyEqConfig(userSettings.eqConfig)
                val targetRepeatMode = when (userSettings.repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
                if (player.repeatMode != targetRepeatMode) {
                    player.repeatMode = targetRepeatMode
                }
                if (player.shuffleModeEnabled != userSettings.shuffleEnabled) {
                    player.shuffleModeEnabled = userSettings.shuffleEnabled
                }
                val targetSpeed = userSettings.playbackSpeed.coerceIn(0.5f, 2.0f)
                val targetPitch = userSettings.playbackPitch.coerceIn(0.5f, 2.0f)
                if (abs(player.playbackParameters.speed - targetSpeed) > 0.005f ||
                    abs(player.playbackParameters.pitch - targetPitch) > 0.005f
                ) {
                    player.playbackParameters = PlaybackParameters(targetSpeed, targetPitch)
                }
            }
        }
    }

    private fun restoreCheckpointIfNeeded() {
        serviceScope.launch(Dispatchers.IO) {
            val checkpoint = ondaPreferences.playbackCheckpoint.first()
            if (checkpoint.queueTrackIds.isEmpty()) return@launch
            val restoredTracks = checkpoint.queueTrackIds.mapNotNull { id ->
                songDao.getById(id)?.toModel()
            }
            if (restoredTracks.isEmpty()) return@launch
            sessionStore.registerTracks(restoredTracks)
            val safeIndex = checkpoint.currentIndex.coerceIn(0, restoredTracks.lastIndex)
            val safePos = checkpoint.positionMs.coerceAtLeast(0L)
            serviceScope.launch(Dispatchers.Main.immediate) {
                if (player.mediaItemCount == 0) {
                    val items = restoredTracks.map(::trackToMediaItem)
                    player.setMediaItems(items, safeIndex, safePos)
                    player.prepare()
                    player.playWhenReady = false
                    syncPlayerStateToStore()
                }
            }
        }
    }

    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val rawUri = dataSpec.uri.toString()
        if (!rawUri.startsWith(ONDA_TRACK_SCHEME)) {
            return dataSpec
        }
        val trackId = rawUri.removePrefix(ONDA_TRACK_SCHEME)
        val track = runBlocking(Dispatchers.IO) {
            sessionStore.getTrack(trackId) ?: songDao.getById(trackId)?.toModel()
        } ?: throw IOException("Unknown track id: $trackId")

        val resolved = runBlocking(Dispatchers.IO) {
            streamResolver.resolve(track)
        }.getOrElse { error ->
            throw IOException("Unable to resolve stream for $trackId", error)
        }

        sessionStore.recordResolvedStream(trackId, resolved)
        val parsedUri = if (resolved.url.startsWith("/")) {
            Uri.fromFile(java.io.File(resolved.url))
        } else {
            Uri.parse(resolved.url)
        }
        return dataSpec.withUri(parsedUri)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                acquireLocks()
                startCheckpointLoop()
                if (currentTrackStartTimeMs == 0L) {
                    currentTrackStartTimeMs = System.currentTimeMillis()
                }
            } else {
                releaseLocks()
                stopCheckpointLoop()
                persistCheckpointNow()
                recordElapsedPlayTime()
            }
            syncPlayerStateToStore()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                recordElapsedPlayTime()
                persistCheckpointNow()
                pauseAndClearEndOfTrackTimer()
            }
            syncPlayerStateToStore()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recordElapsedPlayTime()
            currentTrackStartTimeMs = System.currentTimeMillis()
            lastTrackedId = mediaItem?.mediaId
            audioEffectsEngine.crossfadeController.onTrackStarted()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                pauseAndClearEndOfTrackTimer()
            }
            syncPlayerStateToStore()
            persistCheckpointNow()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncPlayerStateToStore()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncPlayerStateToStore()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncPlayerStateToStore()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            syncPlayerStateToStore()
        }

        private fun pauseAndClearEndOfTrackTimer() {
            if (!sessionStore.playbackState.value.sleepTimerEndOfTrack) return
            player.pause()
            sessionStore.updateState {
                it.copy(
                    sleepTimerEndOfTrack = false,
                    sleepTimerRemainingMs = null
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val httpCode = extractHttpStatusCode(error)
            val activeTrackId = player.currentMediaItem?.mediaId
            val activeTrack = activeTrackId?.let { sessionStore.getTrack(it) }
            if (httpCode != null && (httpCode == 403 || httpCode == 410) && activeTrack != null) {
                val resumePosition = player.currentPosition.coerceAtLeast(0L)
                serviceScope.launch(Dispatchers.IO) {
                    val refreshed = streamResolver.handlePlaybackHttpError(activeTrack, httpCode)
                    if (refreshed.isSuccess) {
                        val option = refreshed.getOrThrow()
                        sessionStore.recordResolvedStream(activeTrack.id, option)
                        serviceScope.launch(Dispatchers.Main.immediate) {
                            player.seekTo(player.currentMediaItemIndex, resumePosition)
                            player.prepare()
                            player.play()
                        }
                    } else {
                        val message = refreshed.exceptionOrNull()?.message ?: "Playback error ($httpCode)"
                        sessionStore.updateState {
                            it.copy(status = PlaybackStatus.ERROR, errorMessage = message)
                        }
                    }
                }
                return
            }

            sessionStore.updateState {
                it.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = error.message ?: "Playback error"
                )
            }
        }
    }

    private fun extractHttpStatusCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            if (cause is StreamHttpException) {
                return cause.statusCode
            }
            cause = cause.cause
        }
        return null
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = serviceScope.launch {
            while (isActive) {
                if (::player.isInitialized && player.mediaItemCount > 0) {
                    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                    val position = player.currentPosition.coerceAtLeast(0L)
                    if (player.isPlaying) {
                        val rampedVolume = audioEffectsEngine.crossfadeController.computeEffectiveVolume(
                            baseVolume = 1f,
                            positionMs = position,
                            durationMs = duration
                        )
                        if (kotlin.math.abs(player.volume - rampedVolume) > 0.01f) {
                            player.volume = rampedVolume
                        }
                    }
                    syncPlayerStateToStore()
                }
                delay(250L)
            }
        }
    }

    private fun startCheckpointLoop() {
        if (checkpointJob?.isActive == true) return
        checkpointJob = serviceScope.launch {
            while (isActive) {
                delay(CHECKPOINT_INTERVAL_MS)
                persistCheckpointNow()
            }
        }
    }

    private fun stopCheckpointLoop() {
        checkpointJob?.cancel()
        checkpointJob = null
    }

    private fun persistCheckpointNow() {
        if (!::player.isInitialized) return
        val ids = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.takeIf { it.isNotBlank() }
        }
        val currentIndex = player.currentMediaItemIndex
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        serviceScope.launch(Dispatchers.IO) {
            ondaPreferences.savePlaybackCheckpoint(
                PlaybackCheckpoint(
                    queueTrackIds = ids,
                    currentIndex = currentIndex,
                    positionMs = positionMs,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun recordElapsedPlayTime() {
        val trackId = lastTrackedId ?: return
        val startMs = currentTrackStartTimeMs
        currentTrackStartTimeMs = 0L
        if (startMs <= 0L) return
        val playedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        if (playedMs < 1_000L) return
        serviceScope.launch(Dispatchers.IO) {
            songDao.addPlayTime(trackId, playedMs)
            playEventDao.insert(
                PlayEventEntity(
                    trackId = trackId,
                    timestampMs = startMs,
                    durationPlayedMs = playedMs
                )
            )
        }
    }

    private fun syncPlayerStateToStore() {
        if (!::player.isInitialized) return
        val queue = (0 until player.mediaItemCount).mapNotNull { index ->
            val mediaId = player.getMediaItemAt(index).mediaId
            sessionStore.getTrack(mediaId)
        }
        val currentIndex = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val currentTrack = queue.getOrNull(currentIndex)
        val status = when {
            player.playerError != null -> PlaybackStatus.ERROR
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            player.isPlaying -> PlaybackStatus.PLAYING
            player.playbackState == Player.STATE_READY || player.mediaItemCount > 0 -> PlaybackStatus.PAUSED
            else -> PlaybackStatus.IDLE
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: currentTrack?.durationMs
            ?: 0L
        val repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        val activeStream = currentTrack?.id?.let { sessionStore.getResolvedStream(it) }
        sessionStore.updateState { existing ->
            existing.copy(
                currentTrack = currentTrack,
                queue = queue,
                currentIndex = currentIndex,
                status = status,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                repeatMode = repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                activeStream = activeStream,
                errorMessage = if (status == PlaybackStatus.ERROR) existing.errorMessage else null,
                playbackSpeed = player.playbackParameters.speed,
                playbackPitch = player.playbackParameters.pitch
            )
        }
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10L * 60L * 1000L)
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    private val librarySessionCallback = object : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val resolvedItems = mediaItems.map { item ->
                val track = sessionStore.getTrack(item.mediaId)
                if (track != null) trackToMediaItem(track) else item
            }
            return Futures.immediateFuture(resolvedItems)
        }
    }

    companion object {
        const val ONDA_TRACK_SCHEME = "onda://track/"
        private const val CHECKPOINT_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_ID = 7001
        private const val NOTIFICATION_CHANNEL_ID = "onda_playback"

        fun trackToMediaItem(track: Track): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                .build()
            return MediaItem.Builder()
                .setMediaId(track.id)
                .setUri("$ONDA_TRACK_SCHEME${track.id}")
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
