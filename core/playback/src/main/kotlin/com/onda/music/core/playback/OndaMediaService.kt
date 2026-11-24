package com.onda.music.core.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.PlayEventDao
import com.onda.music.core.database.PlayEventEntity
import com.onda.music.core.database.PlaybackCheckpoint
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.model.AudioOutputDevice
import com.onda.music.core.model.AudioSignalInfo
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.abs

@UnstableApi
@AndroidEntryPoint
class OndaMediaService : MediaLibraryService() {

    @Inject lateinit var streamResolver: StreamResolver
    @Inject lateinit var audioEffectsEngine: AudioEffectsEngine
    @Inject lateinit var audioOutputProfileManager: AudioOutputProfileManager
    @Inject lateinit var ondaPreferences: OndaPreferences
    @Inject lateinit var songDao: SongDao
    @Inject lateinit var playEventDao: PlayEventDao
    @Inject lateinit var sessionStore: PlaybackSessionStore
    @Inject lateinit var playbackController: PlaybackController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var checkpointJob: Job? = null
    private var positionTickerJob: Job? = null
    private var notificationButtonsJob: Job? = null
    private var lastPublishedButtonsState: NotificationButtonsState? = null

    private var currentTrackStartTimeMs: Long = 0L
    private var lastTrackedId: String? = null
    private var lastPrefetchedTrackId: String? = null

    @Volatile
    private var keepPlayingOnTaskRemoved: Boolean = true

    @Volatile
    private var resumeOnHeadsetConnect: Boolean = false

    override fun onCreate() {
        super.onCreate()
        initLocks()
        initPlayer()
        initMediaSession()
        audioOutputProfileManager.startMonitoring {
            handleHeadsetConnectedAutoResume()
        }
        observeOutputDevice()
        observeSettings()
        observeNotificationControls()
        restoreCheckpointIfNeeded()
        startPositionTicker()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistCheckpointNow()
        if (!keepPlayingOnTaskRemoved) {
            if (::player.isInitialized) {
                player.pause()
            }
            stopSelf()
        } else if (!::player.isInitialized || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistCheckpointNow()
        audioOutputProfileManager.stopMonitoring()
        releaseLocks()
        checkpointJob?.cancel()
        positionTickerJob?.cancel()
        notificationButtonsJob?.cancel()
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
            .setConnectTimeoutMs(6_000)
            .setReadTimeoutMs(10_000)

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
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(audioEffectsEngine.eqProcessor))
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 45_000, 300, 1_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setLoadControl(loadControl)
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
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.onda_playback_channel_name)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_onda_notif_mark)
                }
        )

        val initialState = currentButtonsState()
        val initialButtons = buildNotificationButtons(initialState)
        lastPublishedButtonsState = initialState
        val builder = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setMediaButtonPreferences(initialButtons)
        if (sessionActivity != null) {
            builder.setSessionActivity(sessionActivity)
        }
        mediaLibrarySession = builder.build()
    }

    private fun handleHeadsetConnectedAutoResume() {
        if (!resumeOnHeadsetConnect || !::player.isInitialized) return
        if (player.mediaItemCount > 0 && !player.isPlaying) {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.volume = 1.0f
            player.play()
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            ondaPreferences.settings.collectLatest { userSettings ->
                keepPlayingOnTaskRemoved = userSettings.keepPlayingOnTaskRemoved
                resumeOnHeadsetConnect = userSettings.resumeOnHeadsetConnect
                audioEffectsEngine.applyEqConfig(userSettings.eqConfig)
                if (player.skipSilenceEnabled != userSettings.skipSilenceEnabled) {
                    player.skipSilenceEnabled = userSettings.skipSilenceEnabled
                }
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
                if (userSettings.persistPlaybackSpeedGlobally) {
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
    }

    private fun observeOutputDevice() {
        serviceScope.launch {
            audioOutputProfileManager.activeOutputDevice.collect {
                syncPlayerStateToStore()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNotificationControls() {
        notificationButtonsJob?.cancel()
        notificationButtonsJob = serviceScope.launch {
            sessionStore.playbackState
                .map { state ->
                    NotificationControlSnapshot(
                        trackId = state.currentTrack?.id,
                        repeatMode = state.repeatMode,
                        shuffleEnabled = state.shuffleEnabled,
                        sleepTimerActive = state.sleepTimerRemainingMs != null || state.sleepTimerEndOfTrack
                    )
                }
                .distinctUntilChanged()
                .flatMapLatest { snapshot ->
                    val trackFlow = if (snapshot.trackId != null) {
                        songDao.observeById(snapshot.trackId)
                    } else {
                        flowOf(null)
                    }
                    trackFlow.map { songEntity ->
                        val liked = songEntity?.liked
                            ?: snapshot.trackId?.let { sessionStore.getTrack(it)?.liked }
                            ?: false
                        snapshot.trackId to NotificationButtonsState(
                            isLiked = liked,
                            repeatMode = snapshot.repeatMode,
                            shuffleEnabled = snapshot.shuffleEnabled,
                            sleepTimerActive = snapshot.sleepTimerActive
                        )
                    }
                }
                .distinctUntilChanged()
                .collectLatest { (trackId, buttonsState) ->
                    if (trackId != null) {
                        syncTrackLikedCache(trackId, buttonsState.isLiked)
                        syncCurrentMediaItemRating(trackId, buttonsState.isLiked)
                    }
                    if (lastPublishedButtonsState != buttonsState) {
                        lastPublishedButtonsState = buttonsState
                        mediaLibrarySession?.setMediaButtonPreferences(buildNotificationButtons(buttonsState))
                    }
                }
        }
    }

    private fun buildNotificationButtons(state: NotificationButtonsState): List<CommandButton> {
        val likeButton = CommandButton.Builder(
            if (state.isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setCustomIconResId(
                if (state.isLiked) {
                    R.drawable.ic_onda_notif_heart_filled
                } else {
                    R.drawable.ic_onda_notif_heart_outline
                }
            )
            .setDisplayName(getString(if (state.isLiked) R.string.onda_notif_unlike else R.string.onda_notif_like))
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
            .setEnabled(true)
            .build()

        val (repeatIcon, repeatResId, repeatLabelRes) = when (state.repeatMode) {
            RepeatMode.OFF -> Triple(
                CommandButton.ICON_REPEAT_OFF,
                R.drawable.ic_onda_notif_repeat_off,
                R.string.onda_notif_repeat_off
            )
            RepeatMode.ALL -> Triple(
                CommandButton.ICON_REPEAT_ALL,
                R.drawable.ic_onda_notif_repeat_all,
                R.string.onda_notif_repeat_all
            )
            RepeatMode.ONE -> Triple(
                CommandButton.ICON_REPEAT_ONE,
                R.drawable.ic_onda_notif_repeat_one,
                R.string.onda_notif_repeat_one
            )
        }
        val repeatButton = CommandButton.Builder(repeatIcon)
            .setCustomIconResId(repeatResId)
            .setDisplayName(getString(repeatLabelRes))
            .setSessionCommand(SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY))
            .setEnabled(true)
            .build()

        val shuffleButton = CommandButton.Builder(
            if (state.shuffleEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setCustomIconResId(
                if (state.shuffleEnabled) {
                    R.drawable.ic_onda_notif_shuffle_on
                } else {
                    R.drawable.ic_onda_notif_shuffle_off
                }
            )
            .setDisplayName(
                getString(
                    if (state.shuffleEnabled) {
                        R.string.onda_notif_shuffle_on
                    } else {
                        R.string.onda_notif_shuffle_off
                    }
                )
            )
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .setEnabled(true)
            .build()

        val sleepButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(
                if (state.sleepTimerActive) {
                    R.drawable.ic_onda_notif_sleep_on
                } else {
                    R.drawable.ic_onda_notif_sleep_off
                }
            )
            .setDisplayName(
                getString(
                    if (state.sleepTimerActive) {
                        R.string.onda_notif_sleep_on
                    } else {
                        R.string.onda_notif_sleep_off
                    }
                )
            )
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_SLEEP_TIMER, Bundle.EMPTY))
            .setEnabled(true)
            .build()

        return listOf(likeButton, repeatButton, shuffleButton, sleepButton)
    }

    private fun currentButtonsState(): NotificationButtonsState {
        val state = sessionStore.playbackState.value
        val repeatMode = if (::player.isInitialized) {
            when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        } else {
            state.repeatMode
        }
        val shuffleEnabled = if (::player.isInitialized) {
            player.shuffleModeEnabled
        } else {
            state.shuffleEnabled
        }
        return NotificationButtonsState(
            isLiked = state.currentTrack?.liked ?: false,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            sleepTimerActive = state.sleepTimerRemainingMs != null || state.sleepTimerEndOfTrack
        )
    }

    private fun currentActiveTrackId(): String? {
        val playerTrackId = if (::player.isInitialized) {
            player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return playerTrackId ?: sessionStore.playbackState.value.currentTrack?.id
    }

    private fun syncTrackLikedCache(trackId: String, isLiked: Boolean) {
        val cached = sessionStore.getTrack(trackId) ?: return
        if (cached.liked == isLiked) return
        val updated = cached.copy(liked = isLiked)
        sessionStore.registerTracks(listOf(updated))
        sessionStore.updateState { existing ->
            val updatedCurrent = if (existing.currentTrack?.id == trackId) {
                existing.currentTrack?.copy(liked = isLiked)
            } else {
                existing.currentTrack
            }
            val updatedQueue = existing.queue.map { item ->
                if (item.id == trackId) item.copy(liked = isLiked) else item
            }
            existing.copy(currentTrack = updatedCurrent, queue = updatedQueue)
        }
    }

    private fun syncCurrentMediaItemRating(trackId: String, isLiked: Boolean) {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until player.mediaItemCount) return
        val currentItem = player.getMediaItemAt(currentIndex)
        if (currentItem.mediaId != trackId) return
        val existingRating = currentItem.mediaMetadata.userRating as? HeartRating
        if (existingRating != null && existingRating.isRated && existingRating.isHeart == isLiked) {
            return
        }
        val updatedMetadata = currentItem.mediaMetadata.buildUpon()
            .setUserRating(HeartRating(isLiked))
            .build()
        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(updatedMetadata)
            .build()
        player.replaceMediaItem(currentIndex, updatedItem)
    }

    private suspend fun updateTrackLikeInDb(trackId: String, resolveLiked: (Boolean) -> Boolean) {
        withContext(Dispatchers.IO) {
            val existing = songDao.getById(trackId)
            val currentlyLiked = existing?.liked ?: sessionStore.getTrack(trackId)?.liked ?: false
            val targetLiked = resolveLiked(currentlyLiked)
            if (existing == null) {
                val track = sessionStore.getTrack(trackId) ?: return@withContext
                songDao.upsertAndMerge(track.toEntity())
            }
            songDao.setLiked(trackId, targetLiked)
        }
    }

    private fun cycleRepeatMode() {
        val current = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        val next = nextRepeatMode(current)
        player.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        serviceScope.launch(Dispatchers.IO) {
            ondaPreferences.setRepeatMode(next)
        }
    }

    private fun toggleShuffleMode() {
        val next = !player.shuffleModeEnabled
        player.shuffleModeEnabled = next
        serviceScope.launch(Dispatchers.IO) {
            ondaPreferences.setShuffleEnabled(next)
        }
    }

    private fun toggleSleepTimer() {
        val state = sessionStore.playbackState.value
        if (state.sleepTimerRemainingMs != null || state.sleepTimerEndOfTrack) {
            playbackController.cancelSleepTimer()
        } else {
            playbackController.startSleepTimer(DEFAULT_NOTIF_SLEEP_DURATION_MS)
        }
    }

    private fun handleRatingUpdate(
        mediaId: String?,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        if (rating !is HeartRating) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        val targetId = mediaId?.takeIf { it.isNotBlank() }
            ?: currentActiveTrackId()
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
        val liked = rating.isRated && rating.isHeart
        return serviceScope.future {
            updateTrackLikeInDb(targetId) { liked }
            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }

    private data class NotificationControlSnapshot(
        val trackId: String?,
        val repeatMode: RepeatMode,
        val shuffleEnabled: Boolean,
        val sleepTimerActive: Boolean
    )

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
        val streamUserAgent = resolved.userAgent
        return if (!streamUserAgent.isNullOrBlank()) {
            dataSpec.buildUpon()
                .setUri(parsedUri)
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + ("User-Agent" to streamUserAgent))
                .build()
        } else {
            dataSpec.withUri(parsedUri)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                acquireLocks()
                startCheckpointLoop()
                prefetchNextTrackInQueue()
                if (!sessionStore.playbackState.value.sleepTimerFading &&
                    audioEffectsEngine.crossfadeController.crossfadeSeconds == 0 &&
                    player.volume < 0.99f
                ) {
                    player.volume = 1.0f
                }
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
            prefetchNextTrackInQueue()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncPlayerStateToStore()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncPlayerStateToStore()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncPlayerStateToStore()
            prefetchNextTrackInQueue()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            syncPlayerStateToStore()
        }

        override fun onTracksChanged(tracks: Tracks) {
            syncPlayerStateToStore()
        }

        private fun pauseAndClearEndOfTrackTimer() {
            if (!sessionStore.playbackState.value.sleepTimerEndOfTrack) return
            player.pause()
            player.volume = 1.0f
            sessionStore.updateState {
                it.copy(
                    sleepTimerEndOfTrack = false,
                    sleepTimerRemainingMs = null,
                    sleepTimerFading = false
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

            if (activeTrack != null && sessionStore.getResolvedStream(activeTrack.id)?.source == SourceKind.MONOCHROME) {
                val resumePosition = player.currentPosition.coerceAtLeast(0L)
                serviceScope.launch(Dispatchers.IO) {
                    val fallback = streamResolver.handleMonochromePlaybackFailure(activeTrack)
                    if (fallback.isSuccess) {
                        val option = fallback.getOrThrow()
                        sessionStore.recordResolvedStream(activeTrack.id, option)
                        serviceScope.launch(Dispatchers.Main.immediate) {
                            player.seekTo(player.currentMediaItemIndex, resumePosition)
                            player.prepare()
                            player.play()
                        }
                    } else {
                        val message = error.message ?: "Playback error"
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
                        val currentState = sessionStore.playbackState.value
                        val sleepMultiplier = SleepTimerFadeCurve.computeFadeMultiplier(
                            remainingMs = currentState.sleepTimerRemainingMs,
                            endOfTrack = currentState.sleepTimerEndOfTrack,
                            trackPositionMs = position,
                            trackDurationMs = duration
                        )
                        val fadingNow = sleepMultiplier < 0.995f
                        if (currentState.sleepTimerFading != fadingNow) {
                            sessionStore.updateState { it.copy(sleepTimerFading = fadingNow) }
                        }
                        val rampedVolume = audioEffectsEngine.crossfadeController.computeEffectiveVolume(
                            baseVolume = sleepMultiplier,
                            positionMs = position,
                            durationMs = duration
                        )
                        if (kotlin.math.abs(player.volume - rampedVolume) > 0.005f) {
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
        val audioSignal = if (currentTrack != null) {
            buildAudioSignalInfo(
                format = player.audioFormat,
                activeStream = activeStream,
                outputDevice = audioOutputProfileManager.activeOutputDevice.value
            )
        } else {
            null
        }
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
                audioSignal = audioSignal,
                errorMessage = if (status == PlaybackStatus.ERROR) existing.errorMessage else null,
                playbackSpeed = player.playbackParameters.speed,
                playbackPitch = player.playbackParameters.pitch
            )
        }
    }

    private fun buildAudioSignalInfo(
        format: Format?,
        activeStream: StreamOption?,
        outputDevice: AudioOutputDevice
    ): AudioSignalInfo {
        val codec = resolveCodecLabel(format, activeStream)
        val sampleRateHz = format?.sampleRate?.takeIf { it != Format.NO_VALUE && it > 0 } ?: 0
        val bitDepth = resolveBitDepth(format, activeStream, codec)
        val bitrateKbps = when {
            format != null && format.bitrate != Format.NO_VALUE && format.bitrate > 0 -> format.bitrate / 1000
            activeStream != null && activeStream.bitrateKbps > 0 -> activeStream.bitrateKbps
            else -> 0
        }
        val channelCount = format?.channelCount?.takeIf { it != Format.NO_VALUE && it > 0 } ?: 0
        val isLossless = codec in LOSSLESS_CODECS ||
            (activeStream?.qualityLabel?.contains("lossless", ignoreCase = true) == true) ||
            (activeStream?.qualityLabel?.contains("hi-res", ignoreCase = true) == true)
        return AudioSignalInfo(
            codec = codec,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            bitrateKbps = bitrateKbps,
            channelCount = channelCount,
            outputDeviceName = outputDevice.displayName,
            outputKind = outputDevice.kind,
            isLossless = isLossless
        )
    }

    private fun resolveCodecLabel(format: Format?, activeStream: StreamOption?): String {
        val mime = format?.sampleMimeType?.lowercase(Locale.ROOT).orEmpty()
        val fromMime = when {
            mime.contains("flac") -> "FLAC"
            mime.contains("alac") -> "ALAC"
            mime.contains("opus") -> "OPUS"
            mime.contains("vorbis") -> "VORBIS"
            mime.contains("mp4a") || mime.contains("aac") -> "AAC"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
            mime.contains("ac3") -> "AC3"
            mime.contains("eac3") -> "EAC3"
            mime.contains("raw") || mime.contains("pcm") -> "PCM"
            else -> ""
        }
        if (fromMime.isNotEmpty()) return fromMime
        val fromCodecs = format?.codecs?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val mappedCodec = when {
            fromCodecs.startsWith("fLaC", ignoreCase = true) -> "FLAC"
            fromCodecs.startsWith("mp4a") -> "AAC"
            fromCodecs.startsWith("opus") -> "OPUS"
            fromCodecs.startsWith("alac") -> "ALAC"
            else -> ""
        }
        if (mappedCodec.isNotEmpty()) return mappedCodec
        return activeStream?.codec?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: "PCM"
    }

    private fun resolveBitDepth(format: Format?, activeStream: StreamOption?, codec: String): Int {
        val fromPcm = when (format?.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
        if (fromPcm > 0) return fromPcm
        val qualityLabel = activeStream?.qualityLabel.orEmpty()
        if (qualityLabel.contains("24-bit", ignoreCase = true) || qualityLabel.contains("hi-res", ignoreCase = true)) {
            return 24
        }
        if (qualityLabel.contains("16-bit", ignoreCase = true) || codec in LOSSLESS_CODECS) {
            return 16
        }
        return 0
    }

    private fun prefetchNextTrackInQueue() {
        if (!::player.isInitialized) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex !in 0 until player.mediaItemCount) return
        val nextMediaId = player.getMediaItemAt(nextIndex).mediaId
        if (nextMediaId.isBlank() || nextMediaId == lastPrefetchedTrackId) return
        lastPrefetchedTrackId = nextMediaId
        serviceScope.launch(Dispatchers.IO) {
            val nextTrack = sessionStore.getTrack(nextMediaId) ?: songDao.getById(nextMediaId)?.toModel() ?: return@launch
            streamResolver.prefetch(nextTrack)
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
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            val buttons = buildNotificationButtons(currentButtonsState())
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .setMediaButtonPreferences(buttons)
                .setCustomLayout(buttons)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                ACTION_TOGGLE_LIKE -> {
                    val trackId = currentActiveTrackId()
                        ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                    serviceScope.future {
                        updateTrackLikeInDb(trackId) { !it }
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }
                }
                ACTION_CYCLE_REPEAT -> {
                    cycleRepeatMode()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    toggleShuffleMode()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_TOGGLE_SLEEP_TIMER -> {
                    toggleSleepTimer()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaId: String,
            rating: Rating
        ): ListenableFuture<SessionResult> {
            return handleRatingUpdate(mediaId, rating)
        }

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating
        ): ListenableFuture<SessionResult> {
            return handleRatingUpdate(currentActiveTrackId(), rating)
        }

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
        private val LOSSLESS_CODECS = setOf("FLAC", "ALAC", "WAV", "PCM")

        fun trackToMediaItem(track: Track): MediaItem {
            val upgradedArtworkUrl = upgradeNotificationArtworkUrl(track.artworkUrl)
                ?.takeIf { it.isNotBlank() }
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(upgradedArtworkUrl?.let { Uri.parse(it) })
                .setUserRating(HeartRating(track.liked))
                .build()
            return MediaItem.Builder()
                .setMediaId(track.id)
                .setUri("$ONDA_TRACK_SCHEME${track.id}")
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
