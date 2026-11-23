package com.onda.music.core.transfer

import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.PlayEvent
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import com.onda.music.core.model.TransferManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class OndaSongArtistRef(
    val songId: String,
    val artistId: String,
    val position: Int
)

@Serializable
data class OndaPlaylistEntry(
    val playlistId: String,
    val songId: String,
    val position: Int,
    val addedAtMs: Long = 0L
)

@Serializable
data class OndaSearchEntry(
    val query: String,
    val searchedAtMs: Long
)

@Serializable
data class OndaBackupLibrary(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val songArtistRefs: List<OndaSongArtistRef> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val playlistEntries: List<OndaPlaylistEntry> = emptyList(),
    val playEvents: List<PlayEvent> = emptyList(),
    val searchHistory: List<OndaSearchEntry> = emptyList(),
    val lyrics: List<LyricsDocument> = emptyList()
)

data class OndaBackupBundle(
    val manifest: TransferManifest,
    val settings: OndaUserSettings,
    val library: OndaBackupLibrary,
    val avatarBytes: ByteArray? = null
)

class OndaArchiveCodec(
    private val parser: MomiTransferParser = MomiTransferParser(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {

    fun writeArchive(
        outputStream: OutputStream,
        bundle: OndaBackupBundle
    ) {
        val manifest = bundle.manifest.copy(
            format = FORMAT_ID,
            version = FORMAT_VERSION
        )
        val sanitizedSettings = bundle.settings.copy(avatarPath = null)

        ZipOutputStream(outputStream).use { zos ->
            writeEntry(zos, MANIFEST_PATH, json.encodeToString(manifest).encodeToByteArray())
            writeEntry(zos, SETTINGS_PATH, json.encodeToString(sanitizedSettings).encodeToByteArray())
            writeEntry(zos, LIBRARY_PATH, json.encodeToString(bundle.library).encodeToByteArray())
            val avatar = bundle.avatarBytes
            if (avatar != null && avatar.isNotEmpty()) {
                writeEntry(zos, AVATAR_PATH, avatar)
            }
        }
    }

    fun peekManifest(inputStream: InputStream, scratchDir: File): TransferManifest {
        val root = scratchDir.canonicalFile
        root.mkdirs()
        var manifestText: String? = null
        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                parser.validateSafeEntry(root, entry.name)
                val normalized = entry.name.replace('\\', '/')
                if (!entry.isDirectory && normalized == MANIFEST_PATH) {
                    manifestText = readBytes(zip).decodeToString()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return validateManifest(
            manifestText ?: throw InvalidTransferArchiveException("Missing manifest.json in Onda backup")
        )
    }

    fun readArchive(inputStream: InputStream, scratchDir: File): OndaBackupBundle {
        val root = scratchDir.canonicalFile
        root.mkdirs()
        var manifestText: String? = null
        var settingsText: String? = null
        var libraryText: String? = null
        var avatarBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                parser.validateSafeEntry(root, entry.name)
                if (!entry.isDirectory) {
                    when (entry.name.replace('\\', '/')) {
                        MANIFEST_PATH -> manifestText = readBytes(zip).decodeToString()
                        SETTINGS_PATH -> settingsText = readBytes(zip).decodeToString()
                        LIBRARY_PATH -> libraryText = readBytes(zip).decodeToString()
                        AVATAR_PATH -> avatarBytes = readBytes(zip)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifest = validateManifest(
            manifestText ?: throw InvalidTransferArchiveException("Missing manifest.json in Onda backup")
        )
        val settings = settingsText?.let {
            runCatching { json.decodeFromString<OndaUserSettings>(it) }
                .getOrElse { ex -> throw InvalidTransferArchiveException("Malformed user_settings.json", ex) }
        } ?: OndaUserSettings()
        val library = libraryText?.let {
            runCatching { json.decodeFromString<OndaBackupLibrary>(it) }
                .getOrElse { ex -> throw InvalidTransferArchiveException("Malformed library_snapshot.json", ex) }
        } ?: OndaBackupLibrary()

        return OndaBackupBundle(
            manifest = manifest,
            settings = settings,
            library = library,
            avatarBytes = avatarBytes
        )
    }

    private fun validateManifest(raw: String): TransferManifest {
        val manifest = runCatching { json.decodeFromString<TransferManifest>(raw) }
            .getOrElse { ex -> throw InvalidTransferArchiveException("Malformed manifest.json", ex) }
        if (manifest.format != FORMAT_ID) {
            throw InvalidTransferArchiveException("Unsupported Onda backup format: ${manifest.format}")
        }
        if (manifest.version != FORMAT_VERSION) {
            throw InvalidTransferArchiveException("Unsupported Onda backup version: ${manifest.version}")
        }
        return manifest
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(data)
        zos.closeEntry()
    }

    private fun readBytes(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var n = zip.read(buffer)
        while (n >= 0) {
            if (n > 0) out.write(buffer, 0, n)
            n = zip.read(buffer)
        }
        return out.toByteArray()
    }

    companion object {
        const val FORMAT_ID = "onda-transfer"
        const val FORMAT_VERSION = 1
        const val MANIFEST_PATH = "manifest.json"
        const val SETTINGS_PATH = "settings/user_settings.json"
        const val LIBRARY_PATH = "library/library_snapshot.json"
        const val AVATAR_PATH = "files/avatar.jpg"
    }
}
