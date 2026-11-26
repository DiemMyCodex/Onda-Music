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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
        assertFalse(updatedFlags.lyricsOnDemandTranslateEnabled)
        assertFalse(updatedFlags.scrobbleEnabled)
        assertFalse(updatedFlags.discordRpcEnabled)

        preferences.setLyricsOnDemandTranslateEnabled(true)
        preferences.setScrobbleEnabled(true)
        preferences.setScrobbleConfig("lastfm", "onda_user", "sk_999", "key_1", "sec_1")
        preferences.setDiscordRpcEnabled(true)
        preferences.setDiscordUserToken("discord_tok_abc")

        val optInFlags = preferences.currentSettings()
        assertTrue(optInFlags.lyricsOnDemandTranslateEnabled)
        assertTrue(optInFlags.scrobbleEnabled)
        assertEquals("lastfm", optInFlags.scrobbleService)
        assertEquals("onda_user", optInFlags.scrobbleUsername)
        assertEquals("sk_999", optInFlags.scrobbleSessionKey)
        assertTrue(optInFlags.discordRpcEnabled)
        assertEquals("discord_tok_abc", optInFlags.discordUserToken)

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

    @Test
    fun resetToDefaultsClearsAudioOutputProfilesAndPlaybackCheckpoint() {
        runTest {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempFolder.newFile("onda_reset_test.preferences_pb") }
            )
            val preferences = OndaPreferences(dataStore)
            val device = AudioOutputDevice("bt:xm5", "Sony XM5", AudioOutputKind.BLUETOOTH)
            val profile = AudioOutputEqProfile.fromEqConfig(
                device = device,
                eqConfig = EqConfig(enabled = true, activePresetId = "rock"),
                updatedAtMs = 1_234L
            )
            val checkpoint = PlaybackCheckpoint(
                queueTrackIds = listOf("s1", "s2"),
                currentIndex = 1,
                positionMs = 18_500L,
                updatedAtMs = 5_678L
            )

            preferences.setUsername("Tester")
            preferences.saveAudioOutputProfile(profile)
            preferences.savePlaybackCheckpoint(checkpoint)

            assertEquals(profile, preferences.currentAudioOutputProfiles()[device.key])
            assertEquals(checkpoint, preferences.playbackCheckpoint.first())

            preferences.resetToDefaults()

            assertEquals(OndaUserSettings(), preferences.currentSettings())
            assertTrue(preferences.currentAudioOutputProfiles().isEmpty())
            assertEquals(PlaybackCheckpoint(), preferences.playbackCheckpoint.first())
        }
    }

    @Test
    fun mergeSongPrefersFreshNonEmptyMetadataAndPreservesDbState() {
        val existing = SongEntity(
            id = "s1",
            title = "Old Title",
            artist = "Old Artist",
            album = "Old Album",
            albumId = "alb_old",
            artistId = "art_old",
            durationMs = 180_000L,
            artworkUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=old",
            source = SourceKind.YOUTUBE,
            localUri = "content://old/1",
            ytId = "yt_old",
            monochromeId = "mono_old",
            liked = true,
            inLibrary = true,
            totalPlayTimeMs = 45_000L,
            lyricsOffsetMs = -250L,
            needsRelink = true,
            addedAtMs = 1_000L
        )
        val freshIncoming = SongEntity(
            id = "s1",
            title = "New Title",
            artist = "New Artist",
            album = "New Album",
            albumId = "alb_new",
            artistId = "art_new",
            durationMs = 210_000L,
            artworkUrl = "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            source = SourceKind.YOUTUBE,
            localUri = "content://new/1",
            ytId = "yt_new",
            monochromeId = "mono_new",
            liked = false,
            inLibrary = false,
            totalPlayTimeMs = 12_000L,
            lyricsOffsetMs = 0L,
            needsRelink = false,
            addedAtMs = 5_000L
        )

        val mergedFresh = mergeSong(existing, freshIncoming)
        assertEquals("New Title", mergedFresh.title)
        assertEquals("New Artist", mergedFresh.artist)
        assertEquals("New Album", mergedFresh.album)
        assertEquals("alb_new", mergedFresh.albumId)
        assertEquals("art_new", mergedFresh.artistId)
        assertEquals(210_000L, mergedFresh.durationMs)
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", mergedFresh.artworkUrl)
        assertEquals("content://new/1", mergedFresh.localUri)
        assertEquals("yt_new", mergedFresh.ytId)
        assertEquals("mono_new", mergedFresh.monochromeId)
        assertTrue(mergedFresh.liked)
        assertTrue(mergedFresh.inLibrary)
        assertEquals(45_000L, mergedFresh.totalPlayTimeMs)
        assertEquals(-250L, mergedFresh.lyricsOffsetMs)
        assertFalse(mergedFresh.needsRelink)
        assertEquals(1_000L, mergedFresh.addedAtMs)

        val blankIncoming = SongEntity(
            id = "s1",
            title = "   ",
            artist = "",
            album = " ",
            albumId = null,
            artistId = "",
            durationMs = 0L,
            artworkUrl = "",
            localUri = null,
            ytId = " ",
            monochromeId = null,
            liked = false,
            inLibrary = false,
            totalPlayTimeMs = 90_000L,
            lyricsOffsetMs = 100L,
            needsRelink = false,
            addedAtMs = 500L
        )
        val mergedBlank = mergeSong(existing, blankIncoming)
        assertEquals("Old Title", mergedBlank.title)
        assertEquals("Old Artist", mergedBlank.artist)
        assertEquals("Old Album", mergedBlank.album)
        assertEquals("alb_old", mergedBlank.albumId)
        assertEquals("art_old", mergedBlank.artistId)
        assertEquals(180_000L, mergedBlank.durationMs)
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=old", mergedBlank.artworkUrl)
        assertEquals("content://old/1", mergedBlank.localUri)
        assertEquals("yt_old", mergedBlank.ytId)
        assertEquals("mono_old", mergedBlank.monochromeId)
        assertEquals(90_000L, mergedBlank.totalPlayTimeMs)
        assertEquals(-250L, mergedBlank.lyricsOffsetMs)
        assertTrue(mergedBlank.needsRelink)
        assertEquals(500L, mergedBlank.addedAtMs)

        val deadBackupIncoming = existing.copy(
            localUri = "content://dead/99",
            needsRelink = true,
            liked = false,
            inLibrary = false
        )
        val mergedDeadBackup = mergeSong(existing, deadBackupIncoming)
        assertEquals("content://dead/99", mergedDeadBackup.localUri)
        assertTrue(mergedDeadBackup.needsRelink)
        assertTrue(mergedDeadBackup.liked)
        assertTrue(mergedDeadBackup.inLibrary)
    }

    @Test
    fun songDaoUpsertAndMergeAllChunksQueriesAndToggleLikedOrInsertSetsExactLiked() {
        runTest {
            val dao = FakeSongDao()
            dao.upsertAndMergeAll(emptyList())
            assertEquals(0, dao.getAllCalls)
            assertTrue(dao.getByIdsChunkSizes.isEmpty())

            dao.upsert(
                SongEntity(
                    id = "song_0",
                    title = "Old 0",
                    artist = "Artist 0",
                    liked = true,
                    totalPlayTimeMs = 7_500L,
                    lyricsOffsetMs = -300L
                )
            )
            val batch = (0 until 2050).map { idx ->
                SongEntity(
                    id = "song_$idx",
                    title = "Title $idx",
                    artist = "Artist $idx",
                    durationMs = 120_000L + idx
                )
            } + SongEntity(
                id = "song_0",
                title = "Updated 0",
                artist = "Artist 0",
                artworkUrl = "https://example.com/0.jpg"
            )

            dao.upsertAndMergeAll(batch)
            assertEquals(0, dao.getAllCalls)
            assertEquals(listOf(900, 900, 250), dao.getByIdsChunkSizes)
            val mergedZero = dao.getById("song_0")
            assertEquals("Updated 0", mergedZero?.title)
            assertEquals("https://example.com/0.jpg", mergedZero?.artworkUrl)
            assertTrue(mergedZero?.liked == true)
            assertEquals(7_500L, mergedZero?.totalPlayTimeMs)
            assertEquals(-300L, mergedZero?.lyricsOffsetMs)

            val missingSong = SongEntity(id = "missing_1", title = "Missing", artist = "Artist")
            dao.toggleLikedOrInsert(missingSong, targetLiked = true)
            assertTrue(dao.getById("missing_1")?.liked == true)

            dao.toggleLikedOrInsert(missingSong, targetLiked = false)
            assertFalse(dao.getById("missing_1")?.liked ?: true)

            dao.toggleLikedOrInsert(
                SongEntity(id = "song_0", title = "Wipe Attempt", artist = "Other", totalPlayTimeMs = 0L),
                targetLiked = false
            )
            val unlikedZero = dao.getById("song_0")
            assertFalse(unlikedZero?.liked ?: true)
            assertEquals("Updated 0", unlikedZero?.title)
            assertEquals(7_500L, unlikedZero?.totalPlayTimeMs)
            assertEquals(-300L, unlikedZero?.lyricsOffsetMs)
        }
    }

    @Test
    fun playlistDaoUpsertAndMergeAllDeduplicatesAndAppendsSequentialPositions() {
        runTest {
            val dao = FakePlaylistDao()
            val newPlaylist = PlaylistEntity(id = "pl_new", name = "Fresh")
            dao.upsertAndMergeAll(
                playlists = listOf(newPlaylist),
                entries = listOf(
                    PlaylistSongEntity("pl_new", "a", position = 10),
                    PlaylistSongEntity("pl_new", "b", position = 20),
                    PlaylistSongEntity("pl_new", "a", position = 30),
                    PlaylistSongEntity("pl_new", "c", position = 40)
                )
            )
            val newEntries = dao.getPlaylistSongs("pl_new")
            assertEquals(listOf("a" to 0, "b" to 1, "c" to 2), newEntries.map { it.songId to it.position })
            assertEquals(3, dao.getById("pl_new")?.trackCount)

            val existingPlaylist = PlaylistEntity(id = "pl_exist", name = "Existing", trackCount = 2)
            dao.upsert(existingPlaylist)
            dao.upsertPlaylistSongs(
                listOf(
                    PlaylistSongEntity("pl_exist", "s1", position = 0),
                    PlaylistSongEntity("pl_exist", "s2", position = 1)
                )
            )
            dao.upsertAndMergeAll(
                playlists = listOf(existingPlaylist.copy(name = "Renamed Ignored")),
                entries = listOf(
                    PlaylistSongEntity("pl_exist", "s2", position = 0),
                    PlaylistSongEntity("pl_exist", "s3", position = 0),
                    PlaylistSongEntity("pl_exist", "s4", position = 1),
                    PlaylistSongEntity("pl_exist", "s3", position = 2)
                )
            )
            val existingEntries = dao.getPlaylistSongs("pl_exist")
            assertEquals(
                listOf("s1" to 0, "s2" to 1, "s3" to 2, "s4" to 3),
                existingEntries.map { it.songId to it.position }
            )
            assertEquals(4, dao.getById("pl_exist")?.trackCount)
            assertEquals("Existing", dao.getById("pl_exist")?.name)
        }
    }

    private class FakeSongDao : SongDao {
        private val songs = LinkedHashMap<String, SongEntity>()
        var getAllCalls = 0
        val getByIdsChunkSizes = mutableListOf<Int>()

        override fun observeAll(): Flow<List<SongEntity>> = flowOf(songs.values.toList())

        override fun observeLibrarySongs(): Flow<List<SongEntity>> = flowOf(songs.values.filter { it.inLibrary })

        override fun observeLikedSongs(): Flow<List<SongEntity>> = flowOf(songs.values.filter { it.liked })

        override fun observeLocalSongs(): Flow<List<SongEntity>> = flowOf(songs.values.filter { it.source == SourceKind.LOCAL })

        override fun observeSongsNeedingRelink(): Flow<List<SongEntity>> = flowOf(songs.values.filter { it.needsRelink })

        override fun observeById(id: String): Flow<SongEntity?> = flowOf(songs[id])

        override fun searchSongs(query: String): Flow<List<SongEntity>> = flowOf(
            songs.values.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            }
        )

        override suspend fun getById(id: String): SongEntity? = songs[id]

        override suspend fun getByIds(ids: List<String>): List<SongEntity> {
            getByIdsChunkSizes.add(ids.size)
            return ids.mapNotNull { songs[it] }
        }

        override suspend fun getByYtId(ytId: String): SongEntity? = songs.values.firstOrNull { it.ytId == ytId }

        override suspend fun getByMonochromeId(monochromeId: String): SongEntity? =
            songs.values.firstOrNull { it.monochromeId == monochromeId }

        override suspend fun getAll(): List<SongEntity> {
            getAllCalls += 1
            return songs.values.toList()
        }

        override suspend fun upsert(song: SongEntity) {
            songs[song.id] = song
        }

        override suspend fun upsertAll(songs: List<SongEntity>) {
            songs.forEach { this.songs[it.id] = it }
        }

        override suspend fun setLiked(id: String, liked: Boolean) {
            songs[id]?.let { songs[id] = it.copy(liked = liked) }
        }

        override suspend fun setInLibrary(id: String, inLibrary: Boolean) {
            songs[id]?.let { songs[id] = it.copy(inLibrary = inLibrary) }
        }

        override suspend fun addPlayTime(id: String, deltaMs: Long) {
            songs[id]?.let { songs[id] = it.copy(totalPlayTimeMs = it.totalPlayTimeMs + deltaMs) }
        }

        override suspend fun setLyricsOffset(id: String, offsetMs: Long) {
            songs[id]?.let { songs[id] = it.copy(lyricsOffsetMs = offsetMs) }
        }

        override suspend fun relinkLocalUri(id: String, localUri: String) {
            songs[id]?.let { songs[id] = it.copy(localUri = localUri, needsRelink = false) }
        }

        override suspend fun updateTrackMetadata(
            id: String,
            title: String,
            artist: String,
            album: String?,
            artworkUrl: String?
        ) {
            songs[id]?.let {
                songs[id] = it.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    artworkUrl = artworkUrl ?: it.artworkUrl
                )
            }
        }

        override suspend fun deleteById(id: String) {
            songs.remove(id)
        }

        override suspend fun deleteAll() {
            songs.clear()
        }
    }

    private class FakePlaylistDao : PlaylistDao {
        private val playlists = LinkedHashMap<String, PlaylistEntity>()
        private val entries = LinkedHashMap<Pair<String, String>, PlaylistSongEntity>()

        override fun observeAll(): Flow<List<PlaylistEntity>> = flowOf(playlists.values.toList())

        override fun observeById(id: String): Flow<PlaylistEntity?> = flowOf(playlists[id])

        override fun observeSongsInPlaylist(playlistId: String): Flow<List<SongEntity>> = flowOf(emptyList())

        override fun observePlaylistEntries(playlistId: String): Flow<List<PlaylistSongEntity>> =
            flowOf(entries.values.filter { it.playlistId == playlistId }.sortedBy { it.position })

        override suspend fun getById(id: String): PlaylistEntity? = playlists[id]

        override suspend fun getAll(): List<PlaylistEntity> = playlists.values.toList()

        override suspend fun getPlaylistSongs(playlistId: String): List<PlaylistSongEntity> =
            entries.values.filter { it.playlistId == playlistId }.sortedBy { it.position }

        override suspend fun getAllPlaylistSongs(): List<PlaylistSongEntity> =
            entries.values.sortedBy { it.position }

        override suspend fun getMaxPosition(playlistId: String): Int =
            entries.values.filter { it.playlistId == playlistId }.maxOfOrNull { it.position } ?: -1

        override suspend fun upsert(playlist: PlaylistEntity) {
            playlists[playlist.id] = playlist
        }

        override suspend fun upsertAll(playlists: List<PlaylistEntity>) {
            playlists.forEach { this.playlists[it.id] = it }
        }

        override suspend fun upsertPlaylistSongs(entries: List<PlaylistSongEntity>) {
            entries.forEach { this.entries[it.playlistId to it.songId] = it }
        }

        override suspend fun deletePlaylistSong(playlistId: String, songId: String) {
            entries.remove(playlistId to songId)
        }

        override suspend fun deleteSongsForPlaylist(playlistId: String) {
            entries.keys.removeAll { it.first == playlistId }
        }

        override suspend fun deletePlaylistEntity(playlistId: String) {
            playlists.remove(playlistId)
        }

        override suspend fun refreshTrackCount(playlistId: String) {
            playlists[playlistId]?.let { pl ->
                playlists[playlistId] = pl.copy(
                    trackCount = entries.values.count { it.playlistId == playlistId }
                )
            }
        }

        override suspend fun deleteAllPlaylistSongs() {
            entries.clear()
        }

        override suspend fun deleteAllPlaylists() {
            playlists.clear()
        }
    }
}
