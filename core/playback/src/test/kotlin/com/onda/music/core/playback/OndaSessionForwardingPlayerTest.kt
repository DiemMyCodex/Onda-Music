package com.onda.music.core.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class OndaSessionForwardingPlayerTest {

    @Test
    fun forwardingPlayerDeliversAllCoreListenerCallbacks() {
        val delegate = FakeDelegatePlayer()
        val forwarding = OndaSessionForwardingPlayer(delegate.asPlayer())

        val events = mutableListOf<String>()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                events.add("isPlaying:$isPlaying")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                events.add("playWhenReady:$playWhenReady:$reason")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                events.add("state:$playbackState")
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                events.add("transition:$reason")
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                events.add("repeat:$repeatMode")
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                events.add("shuffle:$shuffleModeEnabled")
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                events.add("loading:$isLoading")
            }

            override fun onVolumeChanged(volume: Float) {
                events.add("volume:$volume")
            }
        }

        forwarding.addListener(listener)
        delegate.listeners.forEach { registered ->
            registered.onIsPlayingChanged(true)
            registered.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            registered.onPlaybackStateChanged(Player.STATE_READY)
            registered.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
            registered.onRepeatModeChanged(Player.REPEAT_MODE_ALL)
            registered.onShuffleModeEnabledChanged(true)
            registered.onIsLoadingChanged(false)
            registered.onVolumeChanged(0.75f)
        }

        assertEquals(
            listOf(
                "isPlaying:true",
                "playWhenReady:true:${Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST}",
                "state:${Player.STATE_READY}",
                "transition:${Player.MEDIA_ITEM_TRANSITION_REASON_SEEK}",
                "repeat:${Player.REPEAT_MODE_ALL}",
                "shuffle:true",
                "loading:false",
                "volume:0.75"
            ),
            events
        )

        forwarding.removeListener(listener)
        assertTrue(delegate.listeners.isEmpty())
    }

    @Test
    fun forwardingPlayerAugmentsSkipCommandsAndHandlesNextPrevious() {
        val delegate = FakeDelegatePlayer(
            mediaItemCount = 3,
            hasNextMediaItem = true,
            hasPreviousMediaItem = true,
            currentPosition = 1_500L
        )
        val forwarding = OndaSessionForwardingPlayer(delegate.asPlayer())

        assertTrue(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

        forwarding.seekToNext()
        forwarding.seekToNextMediaItem()
        forwarding.seekToPrevious()
        assertEquals(listOf("seekToNext", "seekToNextMediaItem", "seekToPreviousMediaItem"), delegate.calls)

        delegate.calls.clear()
        delegate.hasNextMediaItem = false
        delegate.playbackState = Player.STATE_IDLE
        forwarding.seekToNextMediaItem()
        assertEquals(listOf("seekTo:0:0", "prepare", "play"), delegate.calls)

        delegate.calls.clear()
        delegate.currentPosition = 5_000L
        forwarding.seekToPrevious()
        assertEquals(listOf("seekTo:0"), delegate.calls)
    }

    @Test
    fun forwardingPlayerDoesNotReportSkipCommandsWhenQueueIsEmpty() {
        val delegate = FakeDelegatePlayer(mediaItemCount = 0)
        val forwarding = OndaSessionForwardingPlayer(delegate.asPlayer())

        assertFalse(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertFalse(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertFalse(forwarding.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

        forwarding.seekToNext()
        forwarding.seekToPrevious()
        assertTrue(delegate.calls.isEmpty())
    }

    @Test
    fun shouldPauseOnToggleCoversPlayingBufferingAndIdleStates() {
        assertTrue(shouldPauseOnToggle(isPlaying = true, playWhenReady = true, playbackState = Player.STATE_READY))
        assertTrue(shouldPauseOnToggle(isPlaying = false, playWhenReady = true, playbackState = Player.STATE_BUFFERING))
        assertFalse(shouldPauseOnToggle(isPlaying = false, playWhenReady = false, playbackState = Player.STATE_BUFFERING))
        assertFalse(shouldPauseOnToggle(isPlaying = false, playWhenReady = false, playbackState = Player.STATE_READY))
        assertFalse(shouldPauseOnToggle(isPlaying = false, playWhenReady = true, playbackState = Player.STATE_ENDED))
        assertFalse(shouldPauseOnToggle(isPlaying = false, playWhenReady = false, playbackState = Player.STATE_IDLE))
    }

    private class FakeDelegatePlayer(
        var mediaItemCount: Int = 1,
        var hasNextMediaItem: Boolean = false,
        var hasPreviousMediaItem: Boolean = false,
        var currentPosition: Long = 0L,
        var playbackState: Int = Player.STATE_READY
    ) {
        val listeners = mutableListOf<Player.Listener>()
        val calls = mutableListOf<String>()

        fun asPlayer(): Player {
            return Proxy.newProxyInstance(
                Player::class.java.classLoader,
                arrayOf(Player::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "addListener" -> {
                        val listener = args?.get(0) as Player.Listener
                        if (!listeners.contains(listener)) {
                            listeners.add(listener)
                        }
                        null
                    }
                    "removeListener" -> {
                        val listener = args?.get(0) as Player.Listener
                        listeners.remove(listener)
                        null
                    }
                    "getMediaItemCount" -> mediaItemCount
                    "hasNextMediaItem" -> hasNextMediaItem
                    "hasPreviousMediaItem" -> hasPreviousMediaItem
                    "getCurrentPosition" -> currentPosition
                    "getPlaybackState" -> playbackState
                    "isCommandAvailable" -> false
                    "seekToNext" -> {
                        calls.add("seekToNext")
                        null
                    }
                    "seekToNextMediaItem" -> {
                        calls.add("seekToNextMediaItem")
                        null
                    }
                    "seekToPreviousMediaItem" -> {
                        calls.add("seekToPreviousMediaItem")
                        null
                    }
                    "seekTo" -> {
                        if (args != null && args.size == 2) {
                            calls.add("seekTo:${args[0]}:${args[1]}")
                        } else if (args != null && args.size == 1) {
                            calls.add("seekTo:${args[0]}")
                        }
                        null
                    }
                    "prepare" -> {
                        calls.add("prepare")
                        null
                    }
                    "play" -> {
                        calls.add("play")
                        null
                    }
                    "pause" -> {
                        calls.add("pause")
                        null
                    }
                    "equals" -> this === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(this)
                    "toString" -> "FakeDelegatePlayer"
                    else -> when (method.returnType) {
                        Boolean::class.javaPrimitiveType -> false
                        Int::class.javaPrimitiveType -> 0
                        Long::class.javaPrimitiveType -> 0L
                        Float::class.javaPrimitiveType -> 0f
                        Double::class.javaPrimitiveType -> 0.0
                        else -> null
                    }
                }
            } as Player
        }
    }
}
