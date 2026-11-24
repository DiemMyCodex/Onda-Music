package com.onda.music.core.database

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.AudioOutputDevice
import com.onda.music.core.model.AudioOutputEqProfile
import com.onda.music.core.model.AudioOutputKind
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsMotionStyle
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.LyricsWord
import com.onda.music.core.model.PlayEvent
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseMappersAndPrefsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun songArtistAlbumPlaylistAndPlayEventRoundTrip() {
        val track = Track(
            id = "track_1",
            title = "Smoked Glass",
            artist = "Onda",
            album = "Night Mesh",
            albumId = "alb_1",
            artistId = "art_1",
            durationMs = 245_000L,
            artworkUrl = "https://example.com/art.jpg",
            source = SourceKind.MONOCHROME,
            localUri = "content://media/external/audio/1",
            ytId = "yt_abc",
            monochromeId = "mono_xyz",
            liked = true,
            inLibrary = true,
            totalPlayTimeMs = 980_000L,
            lyricsOffsetMs = -150L,
            needsRelink = true,
            addedAtMs = 1_700_000_000_000L
        )
        assertEquals(track, track.toEntity().toModel())

        val artist = Artist(
            id = "art_1",
            name = "Onda",
            thumbnailUrl = "https://example.com/artist.jpg",
            trackCount = 12
        )
        assertEquals(artist, artist.toEntity().toModel())

        val album = Album(
            id = "alb_1",
            title = "Night Mesh",
            artist = "Onda",
            artistId = "art_1",
            year = 2026,
            artworkUrl = "https://example.com/album.jpg",
            trackCount = 10,
            source = SourceKind.YOUTUBE
        )
        assertEquals(album, album.toEntity().toModel())

        val playlist = Playlist(
            id = "pl_1",
            name = "Late Night",
            description = "120fps vibes",
            artworkUrl = "https://example.com/pl.jpg",
            trackCount = 24,
            createdAtMs = 1_700_000_123_000L,
            remoteId = "PL123"
        )
        assertEquals(playlist, playlist.toEntity().toModel())

        val playEvent = PlayEvent(
            id = 42L,
            trackId = "track_1",
            timestampMs = 1_700_000_555_000L,
            durationPlayedMs = 180_000L
        )
        assertEquals(playEvent, playEvent.toEntity().toModel())
    }

    @Test
    fun lyricsAndDownloadRoundTrip() {
        val document = LyricsDocument(
            trackId = "track_1",
            lines = listOf(
                LyricsLine(
                    startTimeMs = 1000L,
                    endTimeMs = 4500L,
                    text = "Feel the wave",
                    words = listOf(
                        LyricsWord("Feel", 1000L, 1800L),
                        LyricsWord("the", 1850L, 2300L),
                        LyricsWord("wave", 2350L, 4500L)
                    ),
                    translatedText = "Cam nhan con song",
                    romanizedText = "Feel the wave",
                    isInterlude = false
                ),
                LyricsLine(
                    startTimeMs = 4500L,
                    endTimeMs = 12000L,
                    text = "",
                    isInterlude = true
                )
            ),
            isSynced = true,
            hasWordTiming = true,
            source = LyricsSource.YOUTUBE_MUSIC
        )
        val entity = document.toEntity(updatedAtMs = 9999L)
        assertEquals(document, entity.toModel())

        val malformedEntity = entity.copy(linesJson = "{invalid-json")
        assertTrue(malformedEntity.toModel().lines.isEmpty())

        val download = DownloadItem(
            trackId = "track_1",
            status = DownloadStatus.COMPLETED,
            progress = 1.0f,
            localFilePath = "/data/user/0/com.onda.music/files/track_1.flac",
            bytesDownloaded = 24_000_000L,
            totalBytes = 24_000_000L
        )
        assertEquals(download, download.toEntity(updatedAtMs = 1234L).toModel())
    }

    @Test
    fun eqConfigSerializationRoundTripAndFallbacks() {
        val customEq = EqConfig(
            enabled = true,
            activePresetId = "custom_flagship",
            bands = EqConfig.defaultBands(listOf(4.5f, 3.0f, 1.5f, 0f, -1f, 0.5f, 2f, 3.5f, 4f, 5f)),
            preampDb = -2.5f,
            normalizeLoudness = true,
            crossfadeSeconds = 6,
            gaplessEnabled = false
        )
        val encoded = encodeEqConfig(customEq)
        val decoded = decodeEqConfig(encoded)
        assertEquals(customEq, decoded)

        assertEquals(EqConfig(), decodeEqConfig(null))
        assertEquals(EqConfig(), decodeEqConfig("   "))
        assertEquals(EqConfig(), decodeEqConfig("{corrupt"))
    }

    @Test
    fun typeConvertersHandleUnknownValuesSafely() {
        val converters = DatabaseConverters()
        assertEquals(SourceKind.LOCAL, converters.toSourceKind(converters.fromSourceKind(SourceKind.LOCAL)))
        assertEquals(SourceKind.YOUTUBE, converters.toSourceKind("UNKNOWN_SOURCE"))
        assertEquals(LyricsSource.EMBEDDED, converters.toLyricsSource(converters.fromLyricsSource(LyricsSource.EMBEDDED)))
        assertEquals(LyricsSource.LRCLIB, converters.toLyricsSource("UNKNOWN_LYRICS"))
        assertEquals(DownloadStatus.FAILED, converters.toDownloadStatus(converters.fromDownloadStatus(DownloadStatus.FAILED)))
        assertEquals(DownloadStatus.QUEUED, converters.toDownloadStatus("UNKNOWN_STATUS"))
    }

    @Test
    fun ondaPreferencesPersistsUserSettingsAndCheckpoint() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFolder.newFile("onda_test.preferences_pb") }
        )
        val preferences = OndaPreferences(dataStore)

        val defaults = preferences.currentSettings()
        assertEquals(PlayerStyle.POSTER, defaults.playerStyle)
        assertEquals(LyricsMotionStyle.KINETIC_GLOW, defaults.lyricsMotionStyle)
        assertEquals(RepeatMode.OFF, defaults.repeatMode)
        assertFalse(defaults.shuffleEnabled)
        assertEquals("en", defaults.languageCode)
        assertFalse(defaults.onboardingCompleted)

        val eq = EqConfig(
            enabled = true,
            activePresetId = "bass_boost",
            bands = EqConfig.defaultBands(listOf(5f, 4.5f, 3.5f)),
            preampDb = -1.5f,
            normalizeLoudness = true,
            crossfadeSeconds = 4,
            gaplessEnabled = true
        )

        preferences.setPlayerStyle(PlayerStyle.TURNTABLE)
        preferences.setLyricsMotionStyle(LyricsMotionStyle.WAVE_GLOW)
        preferences.setEqConfig(eq)
        preferences.setRepeatMode(RepeatMode.ALL)
        preferences.setShuffleEnabled(true)
        preferences.setLanguageCode("VI")
        preferences.setLyricsFontScale(2.5f)
        preferences.setUsername("DiemMy")
        preferences.setAvatarPath("/tmp/avatar.jpg")
        preferences.setYtCookie("SAPISID=abc")
        preferences.setProxyUrl("http://127.0.0.1:7890")

        val updated = preferences.settings.first()
        assertEquals(PlayerStyle.TURNTABLE, updated.playerStyle)
        assertEquals(LyricsMotionStyle.WAVE_GLOW, updated.lyricsMotionStyle)
        assertEquals(eq, updated.eqConfig)
        assertEquals(RepeatMode.ALL, updated.repeatMode)
        assertTrue(updated.shuffleEnabled)
        assertEquals("vi", updated.languageCode)
        assertEquals(1.75f, updated.lyricsFontScale)
        assertEquals("DiemMy", updated.username)
        assertEquals("/tmp/avatar.jpg", updated.avatarPath)
        assertEquals("SAPISID=abc", updated.ytCookie)
        assertEquals("http://127.0.0.1:7890", updated.proxyUrl)

        val checkpoint = PlaybackCheckpoint(
            queueTrackIds = listOf("t1", "t2", "t3"),
            currentIndex = 1,
            positionMs = 42_500L,
            updatedAtMs = 1_700_000_999_000L
        )
        preferences.savePlaybackCheckpoint(checkpoint)
        assertEquals(checkpoint, preferences.playbackCheckpoint.first())

        preferences.setYtCookie("")
        assertNull(preferences.currentSettings().ytCookie)

        preferences.setSmartOutputProfilesEnabled(false)
        preferences.setResumeOnHeadsetConnect(true)
        preferences.setSkipSilenceEnabled(true)
        preferences.setKeepPlayingOnTaskRemoved(false)
        preferences.setKeepScreenOn(true)
        preferences.setOledAdaptiveAccentEnabled(true)
        preferences.setPlaybackSpeedAndPitch(1.25f, 1.10f)
        preferences.setPersistPlaybackSpeedGlobally(false)

        val updatedFlags = preferences.currentSettings()
        assertFalse(updatedFlags.smartOutputProfilesEnabled)
        assertTrue(updatedFlags.resumeOnHeadsetConnect)
        assertTrue(updatedFlags.skipSilenceEnabled)
        assertFalse(updatedFlags.keepPlayingOnTaskRemoved)
        assertTrue(updatedFlags.keepScreenOn)
        assertTrue(updatedFlags.oledAdaptiveAccentEnabled)
        assertFalse(updatedFlags.persistPlaybackSpeedGlobally)
        assertEquals(1.0f, updatedFlags.playbackSpeed, 0.001f)
        assertEquals(1.0f, updatedFlags.playbackPitch, 0.001f)

        val btDevice = AudioOutputDevice("bt:xm5", "Sony XM5", AudioOutputKind.BLUETOOTH)
        val btProfile = AudioOutputEqProfile.fromEqConfig(btDevice, eq, updatedAtMs = 5_000L)
        preferences.saveAudioOutputProfile(btProfile)
        assertEquals(btProfile, preferences.currentAudioOutputProfiles()[btDevice.key])
        preferences.clearAudioOutputProfiles()
        assertTrue(preferences.currentAudioOutputProfiles().isEmpty())

        preferences.completeOnboarding(
            languageCode = "VI",
            playerStyle = PlayerStyle.FLAGSHIP,
            lyricsMotionEnabled = false,
            monochromeEnabled = false,
            hapticsEnabled = false
        )
        val onboarded = preferences.currentSettings()
        assertTrue(onboarded.onboardingCompleted)
        assertEquals("vi", onboarded.languageCode)
        assertEquals(PlayerStyle.FLAGSHIP, onboarded.playerStyle)
        assertFalse(onboarded.lyricsMotionEnabled)
        assertFalse(onboarded.monochromeEnabled)
        assertFalse(onboarded.hapticsEnabled)

        preferences.resetToDefaults()
        assertEquals(OndaUserSettings(), preferences.currentSettings())
    }
}
