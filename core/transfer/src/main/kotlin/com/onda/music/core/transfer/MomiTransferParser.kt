package com.onda.music.core.transfer

import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.EqBand
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.PlayEvent
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.model.TransferFormat
import com.onda.music.core.model.TransferManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.abs

class MomiTransferParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {

    fun validateSafeEntry(extractRoot: File, entryName: String): File {
        if (entryName.isBlank() || entryName.contains('\u0000')) {
            throw ZipSlipException("Invalid empty or null-byte zip entry")
        }
        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw ZipSlipException("Absolute zip entry path rejected: $entryName")
        }
        val normalizedSegments = entryName.replace('\\', '/').split('/')
        if (normalizedSegments.any { it == ".." }) {
            throw ZipSlipException("Parent traversal segment rejected: $entryName")
        }
        val rootCanonical = extractRoot.canonicalFile
        val rootPrefix = rootCanonical.path.let { path ->
            if (path.endsWith(File.separator)) path else path + File.separator
        }
        val candidate = File(rootCanonical, entryName)
        val candidateCanonical = candidate.canonicalFile
        val candidatePath = candidateCanonical.path
        if (candidatePath != rootCanonical.path && !candidatePath.startsWith(rootPrefix)) {
            throw ZipSlipException("Zip entry escapes target directory: $entryName")
        }
        return candidateCanonical
    }

    fun peekArchive(inputStream: InputStream, scratchDir: File): MomiArchivePeek {
        val extractionRoot = scratchDir.canonicalFile
        extractionRoot.mkdirs()
        var manifestJsonText: String? = null
        var settingsJsonText: String? = null
        var hasAvatar = false
        var rawManifestObj: JsonObject? = null

        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val safeFile = validateSafeEntry(extractionRoot, entry.name)
                if (!entry.isDirectory) {
                    val normalized = entry.name.replace('\\', '/')
                    when {
                        normalized == "manifest.json" -> {
                            manifestJsonText = readEntryBytes(zip).decodeToString()
                        }
                        normalized == "settings/settings.json" -> {
                            settingsJsonText = readEntryBytes(zip).decodeToString()
                        }
                        normalized.startsWith("files/profile_pic_") -> {
                            hasAvatar = true
                        }
                        else -> {
                            safeFile.name
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifestText = manifestJsonText
            ?: throw InvalidTransferArchiveException("Missing manifest.json in backup archive")
        val manifest = parseManifest(manifestText)
        rawManifestObj = runCatching {
            json.parseToJsonElement(manifestText) as? JsonObject
        }.getOrNull()
        val settings = settingsJsonText?.let { parseTypedSettings(it) } ?: MomiParsedSettings()

        return MomiArchivePeek(
            format = TransferFormat.MOMI_TRANSFER_V1,
            manifest = manifest,
            songCount = rawManifestObj?.intField("songs") ?: 0,
            playlistCount = rawManifestObj?.intField("playlists") ?: 0,
            playEventCount = rawManifestObj?.intField("plays") ?: 0,
            localSongCount = rawManifestObj?.intField("localSongs") ?: 0,
            totalBytes = rawManifestObj?.longField("bytes") ?: 0L,
            hasAvatar = hasAvatar,
            username = settings.username,
            playerStyle = settings.playerStyle
        )
    }

    fun parseArchive(
        inputStream: InputStream,
        workingDir: File,
        sqliteOpener: SqliteOpener
    ): MomiTransferBundle {
        val extractionRoot = workingDir.canonicalFile
        extractionRoot.mkdirs()

        var manifestText: String? = null
        var settingsText: String? = null
        var audioLabText: String? = null
        var sourceModulesText: String? = null
        var libraryDbFile: File? = null
        val profileImageCandidates = linkedMapOf<String, ByteArray>()

        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val safeTarget = validateSafeEntry(extractionRoot, entry.name)
                val normalized = entry.name.replace('\\', '/')
                if (entry.isDirectory) {
                    safeTarget.mkdirs()
                } else {
                    when {
                        normalized == "manifest.json" -> {
                            manifestText = readEntryBytes(zip).decodeToString()
                        }
                        normalized == "settings/settings.json" -> {
                            settingsText = readEntryBytes(zip).decodeToString()
                        }
                        normalized == "settings/prefs/audio_lab.json" -> {
                            audioLabText = readEntryBytes(zip).decodeToString()
                        }
                        normalized == "settings/prefs/source_modules.json" -> {
                            sourceModulesText = readEntryBytes(zip).decodeToString()
                        }
                        normalized == "database/library.db" -> {
                            safeTarget.parentFile?.mkdirs()
                            safeTarget.outputStream().use { out ->
                                zip.copyTo(out)
                            }
                            libraryDbFile = safeTarget
                        }
                        normalized.startsWith("files/") &&
                            (normalized.endsWith(".jpg", ignoreCase = true) ||
                                normalized.endsWith(".jpeg", ignoreCase = true) ||
                                normalized.endsWith(".png", ignoreCase = true) ||
                                normalized.endsWith(".webp", ignoreCase = true)) -> {
                            val fileName = normalized.substringAfterLast('/')
                            profileImageCandidates[fileName] = readEntryBytes(zip)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifest = parseManifest(
            manifestText ?: throw InvalidTransferArchiveException("Missing manifest.json in archive")
        )
        val settings = settingsText?.let { parseTypedSettings(it) } ?: MomiParsedSettings()
        val audioLab = audioLabText?.let { parseAudioLab(it) } ?: MomiAudioLabSettings()
        val sourceModules = sourceModulesText?.let { parseSourceModules(it) }
            ?: MomiSourceModuleSettings()

        val selectedAvatarName = settings.profilePicFileName?.takeIf {
            profileImageCandidates.containsKey(it)
        } ?: profileImageCandidates.keys.firstOrNull { it.startsWith("profile_pic_") }
        val avatarBytes = selectedAvatarName?.let { profileImageCandidates[it] }

        val library = libraryDbFile?.let { dbFile ->
            readLibraryDatabase(dbFile, sqliteOpener)
        } ?: MomiLibrarySnapshot()

        return MomiTransferBundle(
            manifest = manifest,
            settings = settings,
            audioLab = audioLab,
            sourceModules = sourceModules,
            avatarFileName = selectedAvatarName,
            avatarBytes = avatarBytes,
            library = library
        )
    }

    fun parseManifest(manifestJson: String): TransferManifest {
        val root = try {
            json.parseToJsonElement(manifestJson) as? JsonObject
                ?: throw InvalidTransferArchiveException("manifest.json must be a JSON object")
        } catch (ex: InvalidTransferArchiveException) {
            throw ex
        } catch (ex: Exception) {
            throw InvalidTransferArchiveException("Malformed manifest.json", ex)
        }

        val format = root.stringField("format")
            ?: throw InvalidTransferArchiveException("manifest.json missing format")
        val version = root.intField("version")
            ?: throw InvalidTransferArchiveException("manifest.json missing version")

        if (format != EXPECTED_FORMAT) {
            throw InvalidTransferArchiveException("Unsupported backup format: $format")
        }
        if (version != EXPECTED_VERSION) {
            throw InvalidTransferArchiveException("Unsupported backup version: $version")
        }

        return TransferManifest(
            format = format,
            version = version,
            exportedAtEpochMs = root.longField("createdAt") ?: 0L,
            appName = root.stringField("appId") ?: "",
            appVersion = root.stringField("appVersion") ?: ""
        )
    }

    fun parseTypedSettings(settingsJson: String): MomiParsedSettings {
        val root = parseObjectOrEmpty(settingsJson)
        val entries = parseTypedEntryMap(root)

        val username = entries["username"]?.rawValue?.takeIf { it.isNotBlank() }
        val profilePicUri = entries["profilePicUri"]?.rawValue?.takeIf { it.isNotBlank() }
        val profilePicFileName = profilePicUri?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val playerStyle = entries["playerStyle"]?.rawValue?.let(::mapPlayerStyle)
        val repeatMode = entries["repeatMode"]?.intValue?.let(::mapRepeatMode)
        val shuffleEnabled = entries["shuffleMode"]?.boolValue
        val blurQuality = entries["blurQuality"]?.rawValue
        val glassBlurEnabled = blurQuality?.let {
            !it.equals("OFF", ignoreCase = true) && !it.equals("NONE", ignoreCase = true)
        }
        val lyricsMotionStyle = entries["lyricsMotionStyle"]?.rawValue
        val lyricsMotionEnabled = lyricsMotionStyle?.let {
            !it.equals("OFF", ignoreCase = true) && !it.equals("NONE", ignoreCase = true)
        }
        val maxCacheMb = entries["maxSongCacheSize"]?.intValue

        return MomiParsedSettings(
            username = username,
            profilePicFileName = profilePicFileName,
            playerStyle = playerStyle,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            glassBlurEnabled = glassBlurEnabled,
            lyricsMotionEnabled = lyricsMotionEnabled,
            maxCacheMb = maxCacheMb,
            rawEntries = entries
        )
    }

    fun parseAudioLab(audioLabJson: String): MomiAudioLabSettings {
        val root = parseObjectOrEmpty(audioLabJson)
        val entries = parseTypedEntryMap(root)

        val eqJsonString = entries["eq"]?.rawValue
        val eqObject = eqJsonString?.let(::parseObjectOrEmpty)
        val normalizeObject = entries["normalize"]?.rawValue?.let(::parseObjectOrEmpty)
        val playbackObject = entries["playback"]?.rawValue?.let(::parseObjectOrEmpty)

        val normalizeLoudness = normalizeObject?.stringField("replayGainMode")?.let { mode ->
            !mode.equals("None", ignoreCase = true) && !mode.equals("Off", ignoreCase = true)
        } ?: false

        val eqConfig = eqObject?.let { eq ->
            val enabled = eq.boolField("enabled") ?: false
            val presetId = eq.stringField("presetId")?.takeIf { it.isNotBlank() } ?: "flat"
            val preampDb = (eq.floatField("preamp") ?: 0f).coerceIn(-12f, 12f)
            val nodesArray = eq["nodes"] as? JsonArray
            val parsedBands = parseEqNodes(nodesArray)
            EqConfig(
                enabled = enabled,
                activePresetId = presetId,
                bands = parsedBands,
                preampDb = preampDb,
                normalizeLoudness = normalizeLoudness
            )
        }

        val speed = playbackObject?.floatField("speed") ?: 1f
        val preservePitch = playbackObject?.boolField("preservePitch") ?: true

        return MomiAudioLabSettings(
            eqConfig = eqConfig,
            playbackSpeed = speed,
            preservePitch = preservePitch
        )
    }

    fun parseSourceModules(sourceModulesJson: String): MomiSourceModuleSettings {
        val root = parseObjectOrEmpty(sourceModulesJson)
        val entries = parseTypedEntryMap(root)
        val moduleOrderRaw = entries["module_order"]?.rawValue
            ?: entries["provider_order"]?.rawValue
            ?: ""
        val moduleOrder = moduleOrderRaw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val monochromeFlag = entries["order_monochrome_first"]?.boolValue
            ?: entries["migrated_monochrome"]?.boolValue
            ?: if (moduleOrder.isNotEmpty()) {
                moduleOrder.any { it.equals("monochrome", ignoreCase = true) }
            } else {
                null
            }
        return MomiSourceModuleSettings(
            monochromeEnabled = monochromeFlag,
            moduleOrder = moduleOrder
        )
    }

    fun readLibraryDatabase(dbFile: File, sqliteOpener: SqliteOpener): MomiLibrarySnapshot {
        sqliteOpener.openReadOnly(dbFile).use { conn ->
            val artists = readArtists(conn)
            val artistNameById = artists.associate { it.id to it.name }
            val songArtistMap = readSongArtistMap(conn)
            val songArtistRefs = songArtistMap.flatMap { (songId, artistIds) ->
                artistIds.mapIndexed { index, artistId ->
                    MomiSongArtistRef(
                        songId = songId,
                        artistId = artistId,
                        position = index
                    )
                }
            }
            val tracks = readSongs(conn, songArtistMap, artistNameById)
            val albumArtistMap = readAlbumArtistMap(conn)
            val albums = readAlbums(conn, albumArtistMap, artistNameById)
            val playlists = readPlaylists(conn)
            val playlistTrackRefs = readPlaylistSongMap(conn)
            val playEvents = readEvents(conn)
            val searchQueries = readSearchHistory(conn)
            val lyricsRows = readLyrics(conn)

            return MomiLibrarySnapshot(
                tracks = tracks,
                artists = artists,
                songArtistRefs = songArtistRefs,
                albums = albums,
                playlists = playlists,
                playlistTrackRefs = playlistTrackRefs,
                playEvents = playEvents,
                searchQueries = searchQueries,
                lyricsRows = lyricsRows
            )
        }
    }

    private fun readArtists(conn: SqliteConnection): List<Artist> {
        val cols = conn.tableColumns("artist")
        if (cols.isEmpty()) return emptyList()
        val results = mutableListOf<Artist>()
        conn.query("SELECT * FROM artist").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString("id")?.takeIf { it.isNotBlank() } ?: continue
                val name = cursor.getString("name")?.takeIf { it.isNotBlank() } ?: continue
                val thumbnailUrl = if ("thumbnailUrl" in cols) cursor.getString("thumbnailUrl") else null
                results.add(
                    Artist(
                        id = id,
                        name = name,
                        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() && it != "NULL" }
                    )
                )
            }
        }
        return results
    }

    private fun readSongArtistMap(conn: SqliteConnection): Map<String, List<String>> {
        val cols = conn.tableColumns("song_artist_map")
        if (cols.isEmpty()) return emptyMap()
        val grouped = linkedMapOf<String, MutableList<Pair<Int, String>>>()
        val orderClause = if ("position" in cols) " ORDER BY position ASC" else ""
        conn.query("SELECT * FROM song_artist_map$orderClause").use { cursor ->
            while (cursor.moveToNext()) {
                val songId = cursor.getString("songId")?.takeIf { it.isNotBlank() } ?: continue
                val artistId = cursor.getString("artistId")?.takeIf { it.isNotBlank() } ?: continue
                val position = if ("position" in cols) cursor.getInt("position") ?: 0 else 0
                grouped.getOrPut(songId) { mutableListOf() }.add(position to artistId)
            }
        }
        return grouped.mapValues { (_, entries) ->
            entries.sortedBy { it.first }.map { it.second }.distinct()
        }
    }

    private fun readSongs(
        conn: SqliteConnection,
        songArtistMap: Map<String, List<String>>,
        artistNameById: Map<String, String>
    ): List<Track> {
        val cols = conn.tableColumns("song")
        if (cols.isEmpty()) return emptyList()
        val tracks = mutableListOf<Track>()
        conn.query("SELECT * FROM song").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString("id")?.takeIf { it.isNotBlank() } ?: continue
                val title = cursor.getString("title")?.takeIf { it.isNotBlank() } ?: continue
                val durationSec = if ("duration" in cols) cursor.getLong("duration") ?: 0L else 0L
                val durationMs = if (durationSec > 0L) durationSec * 1000L else 0L
                val thumbnailUrl = if ("thumbnailUrl" in cols) {
                    cursor.getString("thumbnailUrl")?.takeIf { it.isNotBlank() && it != "NULL" }
                } else {
                    null
                }
                val albumId = if ("albumId" in cols) {
                    cursor.getString("albumId")?.takeIf { it.isNotBlank() && it != "NULL" }
                } else {
                    null
                }
                val albumName = if ("albumName" in cols) {
                    cursor.getString("albumName")?.takeIf { it.isNotBlank() && it != "NULL" }
                } else {
                    null
                }
                val liked = if ("liked" in cols) (cursor.getInt("liked") ?: 0) != 0 else false
                val likedDate = if ("likedDate" in cols) cursor.getLong("likedDate") else null
                val totalPlayTimeMs = if ("totalPlayTime" in cols) cursor.getLong("totalPlayTime") ?: 0L else 0L
                val inLibraryVal = if ("inLibrary" in cols) cursor.getLong("inLibrary") else null
                val isLocal = if ("isLocal" in cols) (cursor.getInt("isLocal") ?: 0) != 0 else false
                val lyricsOffsetMs = if ("lyricsOffset" in cols) cursor.getLong("lyricsOffset") ?: 0L else 0L
                val rawLocalUri = if ("localUri" in cols) cursor.getString("localUri") else null
                val localUri = rawLocalUri?.takeIf {
                    it.isNotBlank() && !it.equals("NULL", ignoreCase = true)
                }

                val artistIds = songArtistMap[id].orEmpty()
                val primaryArtistId = artistIds.firstOrNull()
                val artistNames = artistIds.mapNotNull { artistNameById[it] }
                val artistDisplay = if (artistNames.isNotEmpty()) {
                    artistNames.joinToString(", ")
                } else {
                    "Unknown Artist"
                }

                val source = if (isLocal) SourceKind.LOCAL else SourceKind.YOUTUBE
                val inLibrary = (inLibraryVal != null && inLibraryVal > 0L) || liked
                val addedAtMs = when {
                    inLibraryVal != null && inLibraryVal > 0L -> inLibraryVal
                    likedDate != null && likedDate > 0L -> likedDate
                    else -> 0L
                }

                tracks.add(
                    Track(
                        id = id,
                        title = title,
                        artist = artistDisplay,
                        album = albumName,
                        albumId = albumId,
                        artistId = primaryArtistId,
                        durationMs = durationMs,
                        artworkUrl = thumbnailUrl,
                        source = source,
                        localUri = localUri,
                        ytId = if (!isLocal) id else null,
                        liked = liked,
                        inLibrary = inLibrary,
                        totalPlayTimeMs = totalPlayTimeMs,
                        lyricsOffsetMs = lyricsOffsetMs,
                        needsRelink = isLocal && localUri == null,
                        addedAtMs = addedAtMs
                    )
                )
            }
        }
        return tracks
    }

    private fun readAlbumArtistMap(conn: SqliteConnection): Map<String, String> {
        val cols = conn.tableColumns("album_artist_map")
        if (cols.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        conn.query("SELECT * FROM album_artist_map").use { cursor ->
            while (cursor.moveToNext()) {
                val albumId = cursor.getString("albumId")?.takeIf { it.isNotBlank() } ?: continue
                val artistId = cursor.getString("artistId")?.takeIf { it.isNotBlank() } ?: continue
                if (!result.containsKey(albumId)) {
                    result[albumId] = artistId
                }
            }
        }
        return result
    }

    private fun readAlbums(
        conn: SqliteConnection,
        albumArtistMap: Map<String, String>,
        artistNameById: Map<String, String>
    ): List<Album> {
        val cols = conn.tableColumns("album")
        if (cols.isEmpty()) return emptyList()
        val albums = mutableListOf<Album>()
        conn.query("SELECT * FROM album").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString("id")?.takeIf { it.isNotBlank() } ?: continue
                val title = cursor.getString("title")?.takeIf { it.isNotBlank() } ?: continue
                val artistId = albumArtistMap[id]
                val artistName = artistId?.let { artistNameById[it] } ?: "Unknown Artist"
                val year = if ("year" in cols) cursor.getInt("year") else null
                val thumbnailUrl = if ("thumbnailUrl" in cols) {
                    cursor.getString("thumbnailUrl")?.takeIf { it.isNotBlank() && it != "NULL" }
                } else {
                    null
                }
                val songCount = if ("songCount" in cols) cursor.getInt("songCount") ?: 0 else 0
                val isLocal = if ("isLocal" in cols) (cursor.getInt("isLocal") ?: 0) != 0 else false
                albums.add(
                    Album(
                        id = id,
                        title = title,
                        artist = artistName,
                        artistId = artistId,
                        year = year,
                        artworkUrl = thumbnailUrl,
                        trackCount = songCount,
                        source = if (isLocal) SourceKind.LOCAL else SourceKind.YOUTUBE
                    )
                )
            }
        }
        return albums
    }

    private fun readPlaylists(conn: SqliteConnection): List<Playlist> {
        val cols = conn.tableColumns("playlist")
        if (cols.isEmpty()) return emptyList()
        val playlists = mutableListOf<Playlist>()
        conn.query("SELECT * FROM playlist").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString("id")?.takeIf { it.isNotBlank() } ?: continue
                val name = cursor.getString("name")?.takeIf { it.isNotBlank() } ?: continue
                val description = if ("description" in cols) {
                    cursor.getString("description")?.takeIf { it.isNotBlank() && it != "NULL" }
                } else {
                    null
                }
                val thumbnailUrl = if ("thumbnailUrl" in cols) {
                    cursor.getString("thumbnailUrl")?.takeIf { it.isNotBlank() && it != "NULL" }
                } else {
                    null
                }
                val remoteSongCount = if ("remoteSongCount" in cols) cursor.getInt("remoteSongCount") ?: 0 else 0
                val createdAt = if ("createdAt" in cols) cursor.getLong("createdAt") ?: 0L else 0L
                val browseId = if ("browseId" in cols) {
                    cursor.getString("browseId")?.takeIf { it.isNotBlank() && it != "NULL" }
                } else {
                    null
                }
                playlists.add(
                    Playlist(
                        id = id,
                        name = name,
                        description = description,
                        artworkUrl = thumbnailUrl,
                        trackCount = remoteSongCount,
                        createdAtMs = createdAt,
                        remoteId = browseId
                    )
                )
            }
        }
        return playlists
    }

    private fun readPlaylistSongMap(conn: SqliteConnection): List<MomiPlaylistTrackRef> {
        val cols = conn.tableColumns("playlist_song_map")
        if (cols.isEmpty()) return emptyList()
        val refs = mutableListOf<MomiPlaylistTrackRef>()
        val orderClause = if ("position" in cols) " ORDER BY playlistId ASC, position ASC" else ""
        conn.query("SELECT * FROM playlist_song_map$orderClause").use { cursor ->
            while (cursor.moveToNext()) {
                val playlistId = cursor.getString("playlistId")?.takeIf { it.isNotBlank() } ?: continue
                val songId = cursor.getString("songId")?.takeIf { it.isNotBlank() } ?: continue
                val position = if ("position" in cols) cursor.getInt("position") ?: 0 else 0
                refs.add(
                    MomiPlaylistTrackRef(
                        playlistId = playlistId,
                        songId = songId,
                        position = position
                    )
                )
            }
        }
        return refs
    }

    private fun readEvents(conn: SqliteConnection): List<PlayEvent> {
        val cols = conn.tableColumns("event")
        if (cols.isEmpty()) return emptyList()
        val events = mutableListOf<PlayEvent>()
        conn.query("SELECT * FROM event ORDER BY timestamp ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val songId = cursor.getString("songId")?.takeIf { it.isNotBlank() } ?: continue
                val timestamp = cursor.getLong("timestamp") ?: 0L
                val playTime = cursor.getLong("playTime") ?: 0L
                events.add(
                    PlayEvent(
                        id = 0L,
                        trackId = songId,
                        timestampMs = timestamp,
                        durationPlayedMs = playTime
                    )
                )
            }
        }
        return events
    }

    private fun readSearchHistory(conn: SqliteConnection): List<String> {
        val cols = conn.tableColumns("search_history")
        if (cols.isEmpty()) return emptyList()
        val queries = mutableListOf<String>()
        conn.query("SELECT * FROM search_history ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val query = cursor.getString("query")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                queries.add(query)
            }
        }
        return queries.distinct()
    }

    private fun readLyrics(conn: SqliteConnection): List<MomiRawLyricsRow> {
        val cols = conn.tableColumns("lyrics")
        if (cols.isEmpty()) return emptyList()
        val rows = mutableListOf<MomiRawLyricsRow>()
        conn.query("SELECT * FROM lyrics").use { cursor ->
            while (cursor.moveToNext()) {
                val trackId = cursor.getString("id")?.takeIf { it.isNotBlank() } ?: continue
                val rawLyrics = cursor.getString("lyrics")?.takeIf { it.isNotBlank() } ?: continue
                val provider = if ("provider" in cols) cursor.getString("provider") ?: "Unknown" else "Unknown"
                val translated = if ("translatedLyrics" in cols) cursor.getString("translatedLyrics") ?: "" else ""
                val language = if ("translationLanguage" in cols) cursor.getString("translationLanguage") ?: "" else ""
                val mode = if ("translationMode" in cols) cursor.getString("translationMode") ?: "" else ""
                rows.add(
                    MomiRawLyricsRow(
                        trackId = trackId,
                        rawLyrics = rawLyrics,
                        provider = provider,
                        translatedLyrics = translated,
                        translationLanguage = language,
                        translationMode = mode
                    )
                )
            }
        }
        return rows
    }

    private fun parseEqNodes(nodesArray: JsonArray?): List<EqBand> {
        val standardFreqs = EqConfig.standardFrequenciesHz
        if (nodesArray == null || nodesArray.isEmpty()) {
            return EqConfig.defaultBands()
        }
        val parsedNodes = nodesArray.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val freq = obj.floatField("frequency")?.toInt() ?: return@mapNotNull null
            val gain = (obj.floatField("gain") ?: 0f).coerceIn(-12f, 12f)
            val q = (obj.floatField("q") ?: 1.41f).coerceIn(0.3f, 8f)
            val enabled = obj.boolField("enabled") ?: true
            Triple(freq, if (enabled) gain else 0f, q)
        }
        if (parsedNodes.isEmpty()) {
            return EqConfig.defaultBands()
        }
        return standardFreqs.mapIndexed { index, targetFreq ->
            val matched = parsedNodes.minByOrNull { abs(it.first - targetFreq) }
            val gainDb = matched?.second ?: 0f
            val q = matched?.third ?: 1.41f
            EqBand(
                index = index,
                frequencyHz = targetFreq,
                gainDb = gainDb,
                q = q
            )
        }
    }

    private fun parseTypedEntryMap(root: JsonObject): Map<String, MomiTypedSetting> {
        val result = linkedMapOf<String, MomiTypedSetting>()
        for ((key, value) in root) {
            val obj = value as? JsonObject ?: continue
            val type = obj.stringField("t") ?: continue
            val vEl = obj["v"]
            val prim = vEl as? JsonPrimitive
            val setting = when (type) {
                "b" -> MomiTypedSetting(
                    type = type,
                    rawValue = prim?.contentOrNull,
                    boolValue = prim?.booleanOrNull ?: prim?.contentOrNull?.toBooleanStrictOrNull()
                )
                "i" -> MomiTypedSetting(
                    type = type,
                    rawValue = prim?.contentOrNull,
                    intValue = prim?.intOrNull ?: prim?.contentOrNull?.toIntOrNull()
                )
                "l" -> MomiTypedSetting(
                    type = type,
                    rawValue = prim?.contentOrNull,
                    longValue = prim?.longOrNull ?: prim?.contentOrNull?.toLongOrNull()
                )
                "f" -> MomiTypedSetting(
                    type = type,
                    rawValue = prim?.contentOrNull,
                    floatValue = prim?.floatOrNull ?: prim?.contentOrNull?.toFloatOrNull()
                )
                else -> MomiTypedSetting(
                    type = type,
                    rawValue = prim?.contentOrNull
                )
            }
            result[key] = setting
        }
        return result
    }

    private fun parseObjectOrEmpty(rawJson: String): JsonObject {
        return runCatching {
            json.parseToJsonElement(rawJson) as? JsonObject ?: JsonObject(emptyMap())
        }.getOrDefault(JsonObject(emptyMap()))
    }

    private fun mapPlayerStyle(raw: String): PlayerStyle {
        return when (raw.trim().uppercase()) {
            "FLAGSHIP", "MOCHYY", "MODERN", "DEFAULT" -> PlayerStyle.FLAGSHIP
            "CLASSIC" -> PlayerStyle.CLASSIC
            "POSTER" -> PlayerStyle.POSTER
            "TURNTABLE", "VINYL" -> PlayerStyle.TURNTABLE
            "VIDEO", "FULL_VIDEO" -> PlayerStyle.VIDEO
            "IPOD", "RETRO" -> PlayerStyle.IPOD
            "TIMELINE" -> PlayerStyle.TIMELINE
            "LYRICS", "LYRICS_STAGE" -> PlayerStyle.LYRICS
            "BEDSIDE", "BEDSIDE_CLOCK" -> PlayerStyle.BEDSIDE
            "BEDSIDE_TILES" -> PlayerStyle.BEDSIDE_TILES
            else -> PlayerStyle.FLAGSHIP
        }
    }

    private fun mapRepeatMode(raw: Int): RepeatMode {
        return when (raw) {
            1 -> RepeatMode.ALL
            2 -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
    }

    private fun readEntryBytes(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var read = zip.read(buffer)
        while (read >= 0) {
            if (read > 0) {
                out.write(buffer, 0, read)
            }
            read = zip.read(buffer)
        }
        return out.toByteArray()
    }

    private fun JsonObject.stringField(key: String): String? {
        return (this[key] as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.intField(key: String): Int? {
        return (this[key] as? JsonPrimitive)?.intOrNull
    }

    private fun JsonObject.longField(key: String): Long? {
        return (this[key] as? JsonPrimitive)?.longOrNull
    }

    private fun JsonObject.floatField(key: String): Float? {
        return (this[key] as? JsonPrimitive)?.floatOrNull
    }

    private fun JsonObject.boolField(key: String): Boolean? {
        return (this[key] as? JsonPrimitive)?.booleanOrNull
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

    companion object {
        const val EXPECTED_FORMAT = "momi-transfer"
        const val EXPECTED_VERSION = 1
    }
}
