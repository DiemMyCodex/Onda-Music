package com.onda.music.core.playback

import androidx.media3.common.Player
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class PlaybackSessionStore @Inject constructor() {
    private val tracksById = ConcurrentHashMap<String, Track>()
    private val resolvedStreamsById = ConcurrentHashMap<String, StreamOption>()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _activePlayer = MutableStateFlow<Player?>(null)
    val activePlayer: StateFlow<Player?> = _activePlayer.asStateFlow()

    fun registerTracks(tracks: List<Track>) {
        tracks.forEach { track ->
            tracksById[track.id] = track
        }
    }

    fun getTrack(trackId: String): Track? = tracksById[trackId]

    fun findTrackByArtworkUrl(artworkUrl: String): Track? {
        val target = artworkUrl.trim()
        if (target.isEmpty()) return null
        val state = _playbackState.value
        val candidates = buildList {
            state.currentTrack?.let { add(it) }
            addAll(state.queue)
            addAll(tracksById.values)
        }
        return candidates.firstOrNull { track ->
            val trackArtwork = track.artworkUrl?.trim()
            val upgradedArtwork = upgradeNotificationArtworkUrl(trackArtwork)?.trim()
            val trackLocalUri = track.localUri?.trim()
            trackArtwork == target || upgradedArtwork == target || trackLocalUri == target
        }
    }

    fun recordResolvedStream(trackId: String, option: StreamOption) {
        resolvedStreamsById[trackId] = option
        _playbackState.update { state ->
            if (state.currentTrack?.id == trackId) {
                state.copy(activeStream = option, errorMessage = null)
            } else {
                state
            }
        }
    }

    fun getResolvedStream(trackId: String): StreamOption? = resolvedStreamsById[trackId]

    fun setActivePlayer(player: Player?) {
        _activePlayer.value = player
    }

    fun updateState(transform: (PlaybackState) -> PlaybackState) {
        _playbackState.update(transform)
    }
}
