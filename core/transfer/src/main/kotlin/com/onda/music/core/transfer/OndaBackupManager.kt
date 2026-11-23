package com.onda.music.core.transfer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.onda.music.core.database.OndaDatabase
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.PlaylistSongEntity
import com.onda.music.core.database.SearchHistoryEntity
import com.onda.music.core.database.SongArtistCrossRef
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.model.MergePolicy
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.model.TransferFormat
import com.onda.music.core.model.TransferManifest
import com.onda.music.core.model.TransferProgress
import com.onda.music.core.model.TransferSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OndaBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OndaDatabase,
    private val preferences: OndaPreferences,
    private val codec: OndaArchiveCodec
) {

    suspend fun peekBackup(uri: Uri): Result<TransferManifest> = withContext(Dispatchers.IO) {
        val scratchDir = File(context.cacheDir, "onda_peek_${UUID.randomUUID()}")
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw InvalidTransferArchiveException("Unable to open Onda backup stream")
            stream.use { input ->
                Result.success(codec.peekManifest(input, scratchDir))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            scratchDir.deleteRecursively()
        }
    }

    suspend fun exportBackup(
        uri: Uri,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<TransferSummary> = withContext(Dispatchers.IO) {
        try {
            onProgress(TransferProgress(stage = "Collecting library", fraction = 0.15f))
            val settings = preferences.currentSettings()
            val tracks = database.songDao().getAll().map { it.toModel() }
            val artists = database.artistDao().getAll().map { it.toModel() }
            val songArtistRefs = database.artistDao().getAllCrossRefs().map {
                OndaSongArtistRef(
                    songId = it.songId,
                    artistId = it.artistId,
                    position = it.position
                )
            }
            val albums = database.albumDao().getAll().map { it.toModel() }
            val playlists = database.playlistDao().getAll().map { it.toModel() }
            val playlistEntries = database.playlistDao().getAllPlaylistSongs().map {
                OndaPlaylistEntry(
                    playlistId = it.playlistId,
                    songId = it.songId,
                    position = it.position,
                    addedAtMs = it.addedAtMs
                )
            }
            val playEvents = database.playEventDao().getAll().map { it.toModel() }
            val searchHistory = database.searchHistoryDao().getAll().map {
                OndaSearchEntry(
                    query = it.query,
                    searchedAtMs = it.searchedAtMs
                )
            }
            val lyrics = database.lyricsDao().getAll().map { it.toModel() }

            val avatarBytes = settings.avatarPath
                ?.let { path -> File(path) }
                ?.takeIf { it.exists() && it.canRead() }
                ?.readBytes()

            val library = OndaBackupLibrary(
                tracks = tracks,
                artists = artists,
                songArtistRefs = songArtistRefs,
                albums = albums,
                playlists = playlists,
                playlistEntries = playlistEntries,
                playEvents = playEvents,
                searchHistory = searchHistory,
                lyrics = lyrics
            )

            val manifest = TransferManifest(
                format = OndaArchiveCodec.FORMAT_ID,
                version = OndaArchiveCodec.FORMAT_VERSION,
                exportedAtEpochMs = System.currentTimeMillis(),
                appName = context.packageName,
                appVersion = "1.0"
            )

            onProgress(TransferProgress(stage = "Writing backup archive", fraction = 0.65f))
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw InvalidTransferArchiveException("Unable to open backup destination")
            outputStream.use { out ->
                codec.writeArchive(
                    outputStream = out,
                    bundle = OndaBackupBundle(
                        manifest = manifest,
                        settings = settings,
                        library = library,
                        avatarBytes = avatarBytes
                    )
                )
            }

            onProgress(TransferProgress(stage = "Export complete", fraction = 1.0f))
            Result.success(
                TransferSummary(
                    format = TransferFormat.ONDA_TRANSFER_V1,
                    importedTracks = tracks.size,
                    relinkRequiredTracks = tracks.count { it.needsRelink },
                    importedArtists = artists.size,
                    importedAlbums = albums.size,
                    importedPlaylists = playlists.size,
                    importedPlayEvents = playEvents.size,
                    importedSearchQueries = searchHistory.size,
                    importedLyrics = lyrics.size,
                    importedProfile = settings.username.isNotBlank() || avatarBytes != null,
                    importedEqConfig = true,
                    importedPlayerStyle = settings.playerStyle
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun importBackup(
        uri: Uri,
        policy: MergePolicy,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<TransferSummary> = withContext(Dispatchers.IO) {
        val scratchDir = File(context.cacheDir, "onda_import_${UUID.randomUUID()}")
        try {
            onProgress(TransferProgress(stage = "Reading backup archive", fraction = 0.12f))
            val bundle = context.contentResolver.openInputStream(uri)?.use { input ->
                codec.readArchive(input, scratchDir)
            } ?: throw InvalidTransferArchiveException("Unable to open Onda backup stream")

            onProgress(TransferProgress(stage = "Validating tracks", fraction = 0.35f))
            val resolvedTracks = bundle.library.tracks.map { resolveLocalAccess(it) }
            val relinkCount = resolvedTracks.count { it.needsRelink }

            val songEntities = resolvedTracks.map { it.toEntity() }
            val artistEntities = bundle.library.artists.map { it.toEntity() }
            val crossRefs = bundle.library.songArtistRefs.map {
                SongArtistCrossRef(
                    songId = it.songId,
                    artistId = it.artistId,
                    position = it.position
                )
            }
            val albumEntities = bundle.library.albums.map { it.toEntity() }
            val playlistEntities = bundle.library.playlists.map { it.toEntity() }
            val playlistEntries = bundle.library.playlistEntries.map {
                PlaylistSongEntity(
                    playlistId = it.playlistId,
                    songId = it.songId,
                    position = it.position,
                    addedAtMs = it.addedAtMs
                )
            }
            val playEvents = bundle.library.playEvents.map { it.toEntity() }
            val searchHistory = bundle.library.searchHistory.map {
                SearchHistoryEntity(
                    query = it.query,
                    searchedAtMs = it.searchedAtMs
                )
            }
            val lyricsEntities = bundle.library.lyrics.map { it.toEntity() }

            onProgress(TransferProgress(stage = "Restoring library", fraction = 0.6f))
            database.withTransaction {
                if (policy == MergePolicy.REPLACE_EXISTING) {
                    database.clearLibraryData()
                }
                database.songDao().upsertAndMergeAll(songEntities)
                database.artistDao().upsertAndMergeAll(artistEntities, crossRefs)
                database.albumDao().upsertAndMergeAll(albumEntities)
                database.playlistDao().upsertAndMergeAll(playlistEntities, playlistEntries)
                database.playEventDao().upsertAndMergeAll(playEvents)
                database.searchHistoryDao().upsertAndMergeAll(searchHistory)
                database.lyricsDao().upsertAndMergeAll(lyricsEntities)
            }

            onProgress(TransferProgress(stage = "Restoring settings", fraction = 0.85f))
            val savedAvatarPath = bundle.avatarBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
                val avatarFile = File(context.filesDir, "onda_avatar.jpg")
                avatarFile.writeBytes(bytes)
                avatarFile.absolutePath
            }

            preferences.updateSettings { current ->
                if (policy == MergePolicy.REPLACE_EXISTING) {
                    bundle.settings.copy(avatarPath = savedAvatarPath ?: current.avatarPath)
                } else {
                    current.copy(
                        username = bundle.settings.username.ifBlank { current.username },
                        avatarPath = savedAvatarPath ?: current.avatarPath,
                        playerStyle = bundle.settings.playerStyle,
                        eqConfig = bundle.settings.eqConfig,
                        repeatMode = bundle.settings.repeatMode,
                        shuffleEnabled = bundle.settings.shuffleEnabled,
                        glassBlurEnabled = bundle.settings.glassBlurEnabled,
                        dynamicMeshEnabled = bundle.settings.dynamicMeshEnabled,
                        lyricsMotionEnabled = bundle.settings.lyricsMotionEnabled,
                        lyricsFontScale = bundle.settings.lyricsFontScale,
                        hapticsEnabled = bundle.settings.hapticsEnabled,
                        monochromeEnabled = bundle.settings.monochromeEnabled,
                        languageCode = bundle.settings.languageCode,
                        maxCacheMb = bundle.settings.maxCacheMb
                    )
                }
            }

            onProgress(TransferProgress(stage = "Restore complete", fraction = 1.0f))
            Result.success(
                TransferSummary(
                    format = TransferFormat.ONDA_TRANSFER_V1,
                    importedTracks = songEntities.size,
                    relinkRequiredTracks = relinkCount,
                    importedArtists = artistEntities.size,
                    importedAlbums = albumEntities.size,
                    importedPlaylists = playlistEntities.size,
                    importedPlayEvents = playEvents.size,
                    importedSearchQueries = searchHistory.size,
                    importedLyrics = lyricsEntities.size,
                    importedProfile = bundle.settings.username.isNotBlank() || savedAvatarPath != null,
                    importedEqConfig = true,
                    importedPlayerStyle = bundle.settings.playerStyle
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            scratchDir.deleteRecursively()
        }
    }

    private fun resolveLocalAccess(track: Track): Track {
        if (track.source != SourceKind.LOCAL) return track
        val uriString = track.localUri
        if (uriString.isNullOrBlank()) {
            return track.copy(needsRelink = true)
        }
        val accessible = runCatching {
            when {
                uriString.startsWith("content://") -> {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(uriString), "r")?.use {
                        true
                    } ?: false
                }
                uriString.startsWith("file://") -> {
                    val path = Uri.parse(uriString).path ?: return@runCatching false
                    val file = File(path)
                    file.exists() && file.canRead()
                }
                else -> {
                    val file = File(uriString)
                    file.exists() && file.canRead()
                }
            }
        }.getOrDefault(false)
        return track.copy(needsRelink = !accessible)
    }
}
