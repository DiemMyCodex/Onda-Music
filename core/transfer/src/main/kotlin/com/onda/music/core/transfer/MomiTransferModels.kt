package com.onda.music.core.transfer

import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.PlayEvent
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.Track
import com.onda.music.core.model.TransferFormat
import com.onda.music.core.model.TransferManifest
import java.io.Closeable
import java.io.File

class ZipSlipException(message: String) : SecurityException(message)

class InvalidTransferArchiveException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

interface SqliteCursor : Closeable {
    fun moveToNext(): Boolean
    fun getString(column: String): String?
    fun getLong(column: String): Long?
    fun getInt(column: String): Int?
}

interface SqliteConnection : Closeable {
    fun tableColumns(tableName: String): Set<String>
    fun query(sql: String): SqliteCursor
}

fun interface SqliteOpener {
    fun openReadOnly(dbFile: File): SqliteConnection
}

data class MomiTypedSetting(
    val type: String,
    val rawValue: String? = null,
    val boolValue: Boolean? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val floatValue: Float? = null
)

data class MomiParsedSettings(
    val username: String? = null,
    val profilePicFileName: String? = null,
    val playerStyle: PlayerStyle? = null,
    val repeatMode: RepeatMode? = null,
    val shuffleEnabled: Boolean? = null,
    val glassBlurEnabled: Boolean? = null,
    val lyricsMotionEnabled: Boolean? = null,
    val maxCacheMb: Int? = null,
    val rawEntries: Map<String, MomiTypedSetting> = emptyMap()
)

data class MomiAudioLabSettings(
    val eqConfig: EqConfig? = null,
    val playbackSpeed: Float = 1f,
    val preservePitch: Boolean = true
)

data class MomiSourceModuleSettings(
    val monochromeEnabled: Boolean? = null,
    val moduleOrder: List<String> = emptyList()
)

data class MomiSongArtistRef(
    val songId: String,
    val artistId: String,
    val position: Int
)

data class MomiPlaylistTrackRef(
    val playlistId: String,
    val songId: String,
    val position: Int
)

data class MomiRawLyricsRow(
    val trackId: String,
    val rawLyrics: String,
    val provider: String,
    val translatedLyrics: String,
    val translationLanguage: String,
    val translationMode: String
)

data class MomiLibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val songArtistRefs: List<MomiSongArtistRef> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val playlistTrackRefs: List<MomiPlaylistTrackRef> = emptyList(),
    val playEvents: List<PlayEvent> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val lyricsRows: List<MomiRawLyricsRow> = emptyList()
)

data class MomiArchivePeek(
    val format: TransferFormat,
    val manifest: TransferManifest,
    val songCount: Int,
    val playlistCount: Int,
    val playEventCount: Int,
    val localSongCount: Int,
    val totalBytes: Long,
    val hasAvatar: Boolean,
    val username: String?,
    val playerStyle: PlayerStyle?
)

data class MomiTransferBundle(
    val manifest: TransferManifest,
    val settings: MomiParsedSettings,
    val audioLab: MomiAudioLabSettings,
    val sourceModules: MomiSourceModuleSettings,
    val avatarFileName: String?,
    val avatarBytes: ByteArray?,
    val library: MomiLibrarySnapshot
)
