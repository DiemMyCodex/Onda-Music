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
