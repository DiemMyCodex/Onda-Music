package com.onda.music.core.transfer

import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.TransferFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MomiTransferParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = MomiTransferParser()

    private val jdbcOpener = SqliteOpener { dbFile ->
        val connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        JdbcSqliteConnection(connection)
    }

    @Test
    fun `rejects zip slip traversal entry`() {
        val evilZip = buildZipBytes(
            mapOf(
                "manifest.json" to """{"format":"momi-transfer","version":1}""".toByteArray(),
                "../../etc/passwd" to "root:x:0:0".toByteArray()
            )
        )
        val scratchDir = tempFolder.newFolder("zip-slip")
        var thrown: Throwable? = null
        try {
            parser.parseArchive(ByteArrayInputStream(evilZip), scratchDir, jdbcOpener)
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is ZipSlipException)
    }

    @Test
    fun `rejects invalid manifest format and version`() {
        val wrongFormatZip = buildZipBytes(
            mapOf(
                "manifest.json" to """{"format":"other-app","version":1}""".toByteArray()
            )
        )
        var formatEx: Throwable? = null
        try {
            parser.peekArchive(ByteArrayInputStream(wrongFormatZip), tempFolder.newFolder("bad-fmt"))
        } catch (t: Throwable) {
            formatEx = t
        }
        assertTrue(formatEx is InvalidTransferArchiveException)

        val wrongVersionZip = buildZipBytes(
            mapOf(
                "manifest.json" to """{"format":"momi-transfer","version":2}""".toByteArray()
            )
        )
        var versionEx: Throwable? = null
        try {
            parser.peekArchive(ByteArrayInputStream(wrongVersionZip), tempFolder.newFolder("bad-ver"))
        } catch (t: Throwable) {
            versionEx = t
        }
        assertTrue(versionEx is InvalidTransferArchiveException)
    }

    @Test
    fun `parses full synthetic momi transfer v1 archive`() {
        val dbFile = tempFolder.newFile("synthetic_library.db")
        createSyntheticLibraryDb(dbFile)
        val avatarPayload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x42)

        val audioLabJson = """
            {
              "normalize":{"t":"s","v":"{\"replayGainMode\":\"Track\",\"preampDb\":3.0}"},
              "playback":{"t":"s","v":"{\"speed\":1.15,\"preservePitch\":true,\"pitchSemitones\":0.0}"},
              "eq":{"t":"s","v":"{\"enabled\":true,\"nodes\":[{\"frequency\":31.0,\"gain\":4.5,\"q\":1.2,\"enabled\":true},{\"frequency\":62.0,\"gain\":3.0,\"q\":1.41,\"enabled\":true},{\"frequency\":125.0,\"gain\":1.5,\"q\":1.41,\"enabled\":true},{\"frequency\":250.0,\"gain\":0.0,\"q\":1.41,\"enabled\":true},{\"frequency\":500.0,\"gain\":-1.0,\"q\":1.41,\"enabled\":true},{\"frequency\":1000.0,\"gain\":1.0,\"q\":1.41,\"enabled\":true},{\"frequency\":2000.0,\"gain\":2.5,\"q\":1.41,\"enabled\":true},{\"frequency\":4000.0,\"gain\":3.5,\"q\":1.41,\"enabled\":true},{\"frequency\":8000.0,\"gain\":2.0,\"q\":1.41,\"enabled\":true},{\"frequency\":16000.0,\"gain\":1.5,\"q\":1.41,\"enabled\":true}],\"preamp\":-1.5,\"presetId\":\"custom\"}"}
            }
        """.trimIndent()

        val settingsJson = """
            {
              "username":{"t":"s","v":"Diem My"},
              "profilePicUri":{"t":"s","v":"/data/user/0/com.momi.music/files/profile_pic_12345.jpg"},
              "playerStyle":{"t":"s","v":"POSTER"},
              "repeatMode":{"t":"i","v":1},
              "shuffleMode":{"t":"b","v":true},
              "blurQuality":{"t":"s","v":"HIGH"},
              "lyricsMotionStyle":{"t":"s","v":"LEGACY"},
              "maxSongCacheSize":{"t":"i","v":4096}
            }
        """.trimIndent()

        val manifestJson = """
            {"format":"momi-transfer","version":1,"createdAt":1795441766985,"appId":"com.momi.music","appVersion":"8.4.2","songs":2,"playlists":1,"plays":2,"localSongs":1,"bytes":12345}
        """.trimIndent()

        val sourceModulesJson = """
            {"order_monochrome_first":{"t":"b","v":true},"module_order":{"t":"s","v":"monochrome,youtube"}}
        """.trimIndent()

        val zipBytes = buildZipBytes(
            mapOf(
                "manifest.json" to manifestJson.toByteArray(),
                "settings/settings.json" to settingsJson.toByteArray(),
                "settings/prefs/audio_lab.json" to audioLabJson.toByteArray(),
                "settings/prefs/source_modules.json" to sourceModulesJson.toByteArray(),
                "files/profile_pic_12345.jpg" to avatarPayload,
                "database/library.db" to dbFile.readBytes()
            )
        )

        val peek = parser.peekArchive(ByteArrayInputStream(zipBytes), tempFolder.newFolder("peek"))
        assertEquals(TransferFormat.MOMI_TRANSFER_V1, peek.format)
        assertEquals("Diem My", peek.username)
        assertEquals(PlayerStyle.POSTER, peek.playerStyle)
        assertEquals(2, peek.songCount)
        assertEquals(1, peek.playlistCount)
        assertTrue(peek.hasAvatar)

        val bundle = parser.parseArchive(
            ByteArrayInputStream(zipBytes),
            tempFolder.newFolder("parse"),
            jdbcOpener
        )

        assertEquals("momi-transfer", bundle.manifest.format)
        assertEquals(1, bundle.manifest.version)
        assertEquals("Diem My", bundle.settings.username)
        assertEquals(PlayerStyle.POSTER, bundle.settings.playerStyle)
        assertEquals(RepeatMode.ALL, bundle.settings.repeatMode)
        assertEquals(true, bundle.settings.shuffleEnabled)
        assertEquals(true, bundle.settings.glassBlurEnabled)
        assertEquals(4096, bundle.settings.maxCacheMb)
        assertEquals("profile_pic_12345.jpg", bundle.avatarFileName)
        assertArrayEquals(avatarPayload, bundle.avatarBytes)

        val eq = bundle.audioLab.eqConfig
        assertNotNull(eq)
        assertTrue(eq!!.enabled)
        assertEquals("custom", eq.activePresetId)
        assertEquals(-1.5f, eq.preampDb, 0.001f)
        assertTrue(eq.normalizeLoudness)
        assertEquals(10, eq.bands.size)
        assertEquals(31, eq.bands[0].frequencyHz)
        assertEquals(4.5f, eq.bands[0].gainDb, 0.001f)
        assertEquals(16000, eq.bands[9].frequencyHz)
        assertEquals(1.5f, eq.bands[9].gainDb, 0.001f)
        assertEquals(true, bundle.sourceModules.monochromeEnabled)

        assertEquals(2, bundle.library.artists.size)
        assertEquals(2, bundle.library.tracks.size)
        val ytTrack = bundle.library.tracks.first { it.id == "Wt3y-4GFgUY" }
        assertEquals("Cat Doi Noi Sau", ytTrack.title)
        assertEquals("Tang Duy Tan", ytTrack.artist)
        assertEquals(182_000L, ytTrack.durationMs)
        assertEquals(SourceKind.YOUTUBE, ytTrack.source)
        assertTrue(ytTrack.liked)
        assertTrue(ytTrack.inLibrary)
        assertFalse(ytTrack.needsRelink)

        val localTrack = bundle.library.tracks.first { it.id == "local_1" }
        assertEquals(SourceKind.LOCAL, localTrack.source)
        assertTrue(localTrack.needsRelink)

        assertEquals(1, bundle.library.albums.size)
        assertEquals("Tang Duy Tan", bundle.library.albums[0].artist)
        assertEquals(1, bundle.library.playlists.size)
        assertEquals("Favorites", bundle.library.playlists[0].name)
        assertEquals(2, bundle.library.playlistTrackRefs.size)
        assertEquals(2, bundle.library.playEvents.size)
        assertEquals(2, bundle.library.searchQueries.size)
        assertEquals(1, bundle.library.lyricsRows.size)
        assertEquals("BetterLyrics", bundle.library.lyricsRows[0].provider)
    }

    private fun createSyntheticLibraryDb(dbFile: File) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE song (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        thumbnailUrl TEXT,
                        albumId TEXT,
                        albumName TEXT,
                        liked INTEGER NOT NULL,
                        likedDate INTEGER,
                        totalPlayTime INTEGER NOT NULL,
                        inLibrary INTEGER,
                        isLocal INTEGER NOT NULL,
                        lyricsOffset INTEGER NOT NULL,
                        localUri TEXT
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE artist (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        thumbnailUrl TEXT,
                        channelId TEXT,
                        lastUpdateTime INTEGER NOT NULL,
                        bookmarkedAt INTEGER,
                        isLocal INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE song_artist_map (
                        songId TEXT NOT NULL,
                        artistId TEXT NOT NULL,
                        position INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE album (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        year INTEGER,
                        thumbnailUrl TEXT,
                        songCount INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        lastUpdateTime INTEGER NOT NULL,
                        isLocal INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE album_artist_map (
                        albumId TEXT NOT NULL,
                        artistId TEXT NOT NULL,
                        "order" INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE playlist (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        browseId TEXT,
                        createdAt INTEGER,
                        remoteSongCount INTEGER,
                        thumbnailUrl TEXT,
                        description TEXT
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE playlist_song_map (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistId TEXT NOT NULL,
                        songId TEXT NOT NULL,
                        position INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE event (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        playTime INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE lyrics (
                        id TEXT PRIMARY KEY NOT NULL,
                        lyrics TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        translatedLyrics TEXT NOT NULL,
                        translationLanguage TEXT NOT NULL,
                        translationMode TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                st.execute("INSERT INTO artist VALUES ('a1', 'Tang Duy Tan', 'https://img/a1.jpg', 'a1', 1000, NULL, 0)")
                st.execute("INSERT INTO artist VALUES ('a2', 'Local Artist', NULL, NULL, 1000, NULL, 1)")

                st.execute("INSERT INTO album VALUES ('alb1', 'Cat Doi', 2023, 'https://img/alb1.jpg', 1, 182, 1000, 0)")
                st.execute("INSERT INTO album_artist_map VALUES ('alb1', 'a1', 0)")

                st.execute("INSERT INTO song VALUES ('Wt3y-4GFgUY', 'Cat Doi Noi Sau', 182, 'https://img/s1.jpg', 'alb1', 'Cat Doi', 1, 1794967495911, 1239317, 1794967495911, 0, 0, NULL)")
                st.execute("INSERT INTO song VALUES ('local_1', 'Offline Demo', 200, NULL, NULL, NULL, 0, NULL, 5000, 1794967496000, 1, 0, NULL)")

                st.execute("INSERT INTO song_artist_map VALUES ('Wt3y-4GFgUY', 'a1', 0)")
                st.execute("INSERT INTO song_artist_map VALUES ('local_1', 'a2', 0)")

                st.execute("INSERT INTO playlist VALUES ('pl1', 'Favorites', NULL, 1794967490000, 2, 'https://img/pl1.jpg', 'Best tracks')")
                st.execute("INSERT INTO playlist_song_map (playlistId, songId, position) VALUES ('pl1', 'Wt3y-4GFgUY', 0)")
                st.execute("INSERT INTO playlist_song_map (playlistId, songId, position) VALUES ('pl1', 'local_1', 1)")

                st.execute("INSERT INTO event (songId, timestamp, playTime) VALUES ('Wt3y-4GFgUY', 1794967797986, 182000)")
                st.execute("INSERT INTO event (songId, timestamp, playTime) VALUES ('local_1', 1794967988293, 95000)")

                st.execute("INSERT INTO search_history (query) VALUES ('tang duy tan')")
                st.execute("INSERT INTO search_history (query) VALUES ('le luu ly')")

                st.execute("INSERT INTO lyrics VALUES ('Wt3y-4GFgUY', '[00:17.16]Line 1', 'BetterLyrics', '', '', '')")
            }
        }
    }

    private fun buildZipBytes(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private class JdbcSqliteConnection(
        private val connection: Connection
    ) : SqliteConnection {
        override fun tableColumns(tableName: String): Set<String> {
            val cols = linkedSetOf<String>()
            connection.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                    while (rs.next()) {
                        rs.getString("name")?.let { cols.add(it) }
                    }
                }
            }
            return cols
        }

        override fun query(sql: String): SqliteCursor {
            val st = connection.createStatement()
            val rs = st.executeQuery(sql)
            return JdbcSqliteCursor(st, rs)
        }

        override fun close() {
            connection.close()
        }
    }

    private class JdbcSqliteCursor(
        private val statement: java.sql.Statement,
        private val resultSet: ResultSet
    ) : SqliteCursor {
        override fun moveToNext(): Boolean = resultSet.next()

        override fun getString(column: String): String? = resultSet.getString(column)

        override fun getLong(column: String): Long? {
            val value = resultSet.getLong(column)
            return if (resultSet.wasNull()) null else value
        }

        override fun getInt(column: String): Int? {
            val value = resultSet.getInt(column)
            return if (resultSet.wasNull()) null else value
        }

        override fun close() {
            resultSet.close()
            statement.close()
        }
    }
}
