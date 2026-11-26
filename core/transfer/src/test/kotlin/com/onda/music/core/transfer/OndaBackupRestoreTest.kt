package com.onda.music.core.transfer

import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.model.BedsideTint
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.IpodFinish
import com.onda.music.core.model.LyricsMotionStyle
import com.onda.music.core.model.MergePolicy
import com.onda.music.core.model.PlayerStudioConfig
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.StudioArtworkFrame
import com.onda.music.core.model.StudioBackdropMode
import com.onda.music.core.model.StudioBottomDeckMode
import com.onda.music.core.model.StudioQuickAction
import com.onda.music.core.model.StudioSeekbarStyle
import com.onda.music.core.model.TransferManifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class OndaBackupRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val codec = OndaArchiveCodec()

    private fun populatedSettings(): OndaUserSettings = OndaUserSettings(
        playerStyle = PlayerStyle.TURNTABLE,
        eqConfig = EqConfig(
            enabled = true,
            activePresetId = "studio_flat",
            bands = EqConfig.defaultBands(listOf(2.0f, -1.0f, 3.0f)),
            preampDb = -2.0f,
            normalizeLoudness = true,
            crossfadeSeconds = 5,
            gaplessEnabled = false
        ),
        repeatMode = RepeatMode.ONE,
        shuffleEnabled = true,
        glassBlurEnabled = false,
        dynamicMeshEnabled = false,
        lyricsMotionEnabled = false,
        lyricsMotionStyle = LyricsMotionStyle.PURE_SWEEP,
        lyricsFontScale = 1.35f,
        lyricsOnDemandTranslateEnabled = true,
        hapticsEnabled = false,
        monochromeEnabled = false,
        ytCookie = "SAPISID=restored_cookie",
        proxyUrl = "http://127.0.0.1:8888",
        languageCode = "vi",
        maxCacheMb = 1024,
        downloadTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
        username = "OndaListener",
        avatarPath = "/data/user/0/com.onda.music/files/onda_avatar_old.jpg",
        playbackSpeed = 1.25f,
        playbackPitch = 0.9f,
        persistPlaybackSpeedGlobally = false,
        oledAdaptiveAccentEnabled = true,
        studioConfig = PlayerStudioConfig(
            backdropMode = StudioBackdropMode.AGSL_MESH,
            artworkFrame = StudioArtworkFrame.EDGE_BLEED,
            seekbarStyle = StudioSeekbarStyle.PRECISION_BAR,
            bottomDeckMode = StudioBottomDeckMode.QUICK_ACTIONS,
            controlsShiftDp = 12,
            quickActions = listOf(
                StudioQuickAction.DOWNLOAD,
                StudioQuickAction.EQUALIZER,
                StudioQuickAction.SLEEP_TIMER,
                StudioQuickAction.LIKE
            ),
            ipodFinish = IpodFinish.CRIMSON,
            bedsideTint = BedsideTint.CRIMSON,
            bedsideBurnInGuard = false,
            videoSmartFill = false,
            videoAmbientGlow = false
        ),
        smartOutputProfilesEnabled = false,
        resumeOnHeadsetConnect = true,
        skipSilenceEnabled = true,
        keepPlayingOnTaskRemoved = false,
        keepScreenOn = true,
        onboardingCompleted = true,
        scrobbleEnabled = true,
        scrobbleService = "lastfm",
        scrobbleUsername = "scrob_user",
        scrobbleSessionKey = "scrob_sk",
        scrobbleApiKey = "scrob_ak",
        scrobbleApiSecret = "scrob_sec",
        discordRpcEnabled = true,
        discordUserToken = "discord_tok_123"
    )

    @Test
    fun archiveRoundTripPreservesAll38UserSettingsFields() {
        val settings = populatedSettings()
        val out = ByteArrayOutputStream()
        codec.writeArchive(
            outputStream = out,
            bundle = OndaBackupBundle(
                manifest = TransferManifest(
                    format = OndaArchiveCodec.FORMAT_ID,
                    version = OndaArchiveCodec.FORMAT_VERSION,
                    exportedAtEpochMs = 1_795_441_766_985L,
                    appName = "com.onda.music",
                    appVersion = OndaBackupManager.ONDA_TRANSFER_APP_VERSION
                ),
                settings = settings,
                library = OndaBackupLibrary(),
                avatarBytes = null
            )
        )

        val restored = codec.readArchive(
            ByteArrayInputStream(out.toByteArray()),
            tempFolder.newFolder("roundtrip_38")
        )
        assertEquals(settings.copy(avatarPath = null), restored.settings)
        assertEquals(OndaBackupManager.ONDA_TRANSFER_APP_VERSION, restored.manifest.appVersion)
    }

    @Test
    fun mergeRestoredSettingsIdempotentUpsertRestoresAllFieldsAndPreservesAvatarWhenMissing() {
        val current = OndaUserSettings(
            avatarPath = "/data/files/onda_avatar_100.jpg"
        )
        val restored = populatedSettings().copy(avatarPath = null)

        val merged = OndaBackupManager.mergeRestoredSettings(
            current = current,
            restored = restored,
            policy = MergePolicy.IDEMPOTENT_UPSERT,
            savedAvatarPath = null
        )

        assertEquals(restored.copy(avatarPath = "/data/files/onda_avatar_100.jpg"), merged)
    }

    @Test
    fun mergeRestoredSettingsProtectsLocalSecretsWhenBackupBlankAndUpdatesAvatarWhenProvided() {
        val current = OndaUserSettings(
            username = "LocalUser",
            avatarPath = "/data/files/onda_avatar_100.jpg",
            ytCookie = "LOCAL_COOKIE",
            proxyUrl = "http://127.0.0.1:7890",
            downloadTreeUri = "content://local/tree",
            onboardingCompleted = true,
            scrobbleService = "lastfm",
            scrobbleUsername = "local_scrob",
            scrobbleSessionKey = "local_sk",
            scrobbleApiKey = "local_ak",
            scrobbleApiSecret = "local_sec",
            discordUserToken = "local_discord"
        )
        val blankBackup = OndaUserSettings(
            playerStyle = PlayerStyle.FLAGSHIP,
            languageCode = "vi",
            username = "   ",
            ytCookie = "",
            proxyUrl = null,
            downloadTreeUri = " ",
            onboardingCompleted = false,
            scrobbleService = "",
            scrobbleUsername = "",
            scrobbleSessionKey = "",
            scrobbleApiKey = "",
            scrobbleApiSecret = "",
            discordUserToken = ""
        )

        val mergedUpsert = OndaBackupManager.mergeRestoredSettings(
            current = current,
            restored = blankBackup,
            policy = MergePolicy.IDEMPOTENT_UPSERT,
            savedAvatarPath = "/data/files/onda_avatar_200.jpg"
        )

        assertEquals(PlayerStyle.FLAGSHIP, mergedUpsert.playerStyle)
        assertEquals("vi", mergedUpsert.languageCode)
        assertEquals("LocalUser", mergedUpsert.username)
        assertEquals("/data/files/onda_avatar_200.jpg", mergedUpsert.avatarPath)
        assertEquals("LOCAL_COOKIE", mergedUpsert.ytCookie)
        assertEquals("http://127.0.0.1:7890", mergedUpsert.proxyUrl)
        assertEquals("content://local/tree", mergedUpsert.downloadTreeUri)
        assertTrue(mergedUpsert.onboardingCompleted)
        assertEquals("lastfm", mergedUpsert.scrobbleService)
        assertEquals("local_scrob", mergedUpsert.scrobbleUsername)
        assertEquals("local_sk", mergedUpsert.scrobbleSessionKey)
        assertEquals("local_ak", mergedUpsert.scrobbleApiKey)
        assertEquals("local_sec", mergedUpsert.scrobbleApiSecret)
        assertEquals("local_discord", mergedUpsert.discordUserToken)

        val replaced = OndaBackupManager.mergeRestoredSettings(
            current = current,
            restored = blankBackup,
            policy = MergePolicy.REPLACE_EXISTING,
            savedAvatarPath = null
        )
        assertEquals("/data/files/onda_avatar_100.jpg", replaced.avatarPath)
        assertFalse(replaced.onboardingCompleted)
        assertNull(replaced.proxyUrl)
    }

    @Test
    fun saveOndaAvatarBytesRotatesTimestampAndDeletesStaleAvatarFiles() {
        val filesDir = tempFolder.newFolder("files")
        val staleStatic = File(filesDir, "onda_avatar.jpg").apply { writeBytes(byteArrayOf(1)) }
        val staleTimed = File(filesDir, "onda_avatar_111.jpg").apply { writeBytes(byteArrayOf(2)) }
        val unrelated = File(filesDir, "keep_me.txt").apply { writeText("keep") }

        val emptyResult = OndaBackupManager.saveOndaAvatarBytes(filesDir, byteArrayOf(), timestampMs = 500L)
        assertNull(emptyResult)
        assertTrue(staleStatic.exists())
        assertTrue(staleTimed.exists())

        val savedPath = OndaBackupManager.saveOndaAvatarBytes(
            filesDir = filesDir,
            bytes = byteArrayOf(7, 8, 9),
            timestampMs = 999L
        )
        val expectedFile = File(filesDir, "onda_avatar_999.jpg")
        assertEquals(expectedFile.absolutePath, savedPath)
        assertTrue(expectedFile.exists())
        assertArrayEquals(byteArrayOf(7, 8, 9), expectedFile.readBytes())
        assertFalse(staleStatic.exists())
        assertFalse(staleTimed.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun backupManifestVersionIs131() {
        assertEquals("1.3.1", OndaBackupManager.ONDA_TRANSFER_APP_VERSION)
    }
}
