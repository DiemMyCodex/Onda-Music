package com.onda.music.core.transfer

import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.model.Artist
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.model.TransferManifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OndaArchiveCodecAndLyricsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val codec = OndaArchiveCodec()

    @Test
    fun `roundtrips onda transfer v1 backup archive`() {
        val settings = OndaUserSettings(
            playerStyle = PlayerStyle.POSTER,
            eqConfig = EqConfig(enabled = true, activePresetId = "bass_boost", preampDb = 1.5f),
            repeatMode = RepeatMode.ALL,
            shuffleEnabled = true,
            username = "Diem My",
            avatarPath = "/data/user/0/com.onda.music/files/onda_avatar.jpg"
        )
        val library = OndaBackupLibrary(
            tracks = listOf(
                Track(
                    id = "t1",
                    title = "Le Luu Ly",
                    artist = "Vu Phung Tien",
                    durationMs = 201_000L,
                    source = SourceKind.YOUTUBE,
                    ytId = "t1",
                    liked = true,
                    inLibrary = true
                )
            ),
            artists = listOf(Artist(id = "a1", name = "Vu Phung Tien")),
            songArtistRefs = listOf(OndaSongArtistRef(songId = "t1", artistId = "a1", position = 0)),
            playlists = listOf(Playlist(id = "p1", name = "Chill", trackCount = 1)),
            playlistEntries = listOf(OndaPlaylistEntry(playlistId = "p1", songId = "t1", position = 0, addedAtMs = 1000L))
        )
        val avatar = byteArrayOf(1, 2, 3, 4, 5)
        val out = ByteArrayOutputStream()
        codec.writeArchive(
            outputStream = out,
            bundle = OndaBackupBundle(
                manifest = TransferManifest(
                    format = "onda-transfer",
                    version = 1,
                    exportedAtEpochMs = 1795441766985L
                ),
                settings = settings,
                library = library,
                avatarBytes = avatar
            )
        )

        val bytes = out.toByteArray()
        val peek = codec.peekManifest(ByteArrayInputStream(bytes), tempFolder.newFolder("peek"))
        assertEquals("onda-transfer", peek.format)
        assertEquals(1, peek.version)

        val restored = codec.readArchive(ByteArrayInputStream(bytes), tempFolder.newFolder("read"))
        assertEquals(PlayerStyle.POSTER, restored.settings.playerStyle)
        assertEquals("Diem My", restored.settings.username)
        assertEquals(null, restored.settings.avatarPath)
        assertTrue(restored.settings.eqConfig.enabled)
        assertEquals(1, restored.library.tracks.size)
        assertEquals("Le Luu Ly", restored.library.tracks[0].title)
        assertArrayEquals(avatar, restored.avatarBytes)
    }

    @Test
    fun `rejects zip slip in onda archive`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write("""{"format":"onda-transfer","version":1}""".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }
        var thrown: Throwable? = null
        try {
            codec.readArchive(ByteArrayInputStream(baos.toByteArray()), tempFolder.newFolder("slip"))
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is ZipSlipException)
    }

    @Test
    fun `converts simpmusic and betterlyrics formats into LyricsDocument`() {
        val simpRow = MomiRawLyricsRow(
            trackId = "Wt3y-4GFgUY",
            rawLyrics = "[00:17.16] <00:17.16>Cat <00:17.48>doi <00:17.71>noi <00:17.94>sau",
            provider = "SimpMusic",
            translatedLyrics = "[00:17.20]Cut the sorrow in two",
            translationLanguage = "en",
            translationMode = "line"
        )
        val simpDoc = MomiLyricsConverter.convert(simpRow)
        assertTrue(simpDoc.isSynced)
        assertTrue(simpDoc.hasWordTiming)
        assertEquals(LyricsSource.CACHE, simpDoc.source)
        assertEquals(1, simpDoc.lines.size)
        assertEquals(4, simpDoc.lines[0].words.size)
        assertEquals("Cut the sorrow in two", simpDoc.lines[0].translatedText)

        val betterRow = MomiRawLyricsRow(
            trackId = "8ctIJNE6TK0",
            rawLyrics = "[00:14.08]{agent:v1}Em co gi dau\n<Em:14.089:14.448|co:14.448:14.856|gi:14.856:15.206|dau:15.206:15.54>",
            provider = "BetterLyrics",
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = ""
        )
        val betterDoc = MomiLyricsConverter.convert(betterRow)
        assertTrue(betterDoc.isSynced)
        assertTrue(betterDoc.hasWordTiming)
        assertEquals(4, betterDoc.lines[0].words.size)
        assertEquals(14089L, betterDoc.lines[0].words[0].startTimeMs)
    }
}
