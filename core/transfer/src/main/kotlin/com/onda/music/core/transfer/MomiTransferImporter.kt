package com.onda.music.core.transfer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.onda.music.core.database.OndaDatabase
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.database.PlaylistSongEntity
import com.onda.music.core.database.SearchHistoryEntity
import com.onda.music.core.database.SongArtistCrossRef
import com.onda.music.core.database.toEntity
import com.onda.music.core.model.MergePolicy
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.model.TransferFormat
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
class MomiTransferImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OndaDatabase,
    private val preferences: OndaPreferences,
    private val parser: MomiTransferParser,
    private val sqliteOpener: SqliteOpener
) {

    suspend fun peek(uri: Uri): Result<MomiArchivePeek> = withContext(Dispatchers.IO) {
        val scratchDir = File(context.cacheDir, "momi_peek_${UUID.randomUUID()}")
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw InvalidTransferArchiveException("Unable to open backup stream")
            stream.use { input ->
                Result.success(parser.peekArchive(input, scratchDir))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            scratchDir.deleteRecursively()
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        policy: MergePolicy,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<TransferSummary> = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "momi_import_${UUID.randomUUID()}")
        try {
            onProgress(TransferProgress(stage = "Reading backup archive", fraction = 0.08f))
            val bundle = context.contentResolver.openInputStream(uri)?.use { input ->
                parser.parseArchive(input, workDir, sqliteOpener)
            } ?: throw InvalidTransferArchiveException("Unable to open backup stream")

            onProgress(TransferProgress(stage = "Validating tracks", fraction = 0.28f))
            val resolvedTracks = bundle.library.tracks.map { track ->
                resolveLocalAccess(track)
            }
            val relinkCount = resolvedTracks.count { it.needsRelink }

            val totalItems = resolvedTracks.size +
                bundle.library.artists.size +
                bundle.library.albums.size +
                bundle.library.playlists.size +
                bundle.library.playEvents.size +
                bundle.library.lyricsRows.size

            onProgress(
                TransferProgress(
                    stage = "Importing library",
                    fraction = 0.45f,
                    processedCount = 0,
                    totalCount = totalItems
                )
            )

            val songEntities = resolvedTracks.map { it.toEntity() }
            val artistEntities = bundle.library.artists.map { it.toEntity() }
            val crossRefs = bundle.library.songArtistRefs.map { ref ->
                SongArtistCrossRef(
                    songId = ref.songId,
                    artistId = ref.artistId,
                    position = ref.position
                )
            }
            val albumEntities = bundle.library.albums.map { it.toEntity() }
            val playlistEntities = bundle.library.playlists.map { it.toEntity() }
            val playlistSongEntities = bundle.library.playlistTrackRefs.map { ref ->
                PlaylistSongEntity(
                    playlistId = ref.playlistId,
                    songId = ref.songId,
                    position = ref.position,
                    addedAtMs = bundle.manifest.exportedAtEpochMs
                )
            }
            val playEventEntities = bundle.library.playEvents.map { it.toEntity() }
            val baseTime = bundle.manifest.exportedAtEpochMs.takeIf { it > 0L }
                ?: System.currentTimeMillis()
            val searchEntities = bundle.library.searchQueries.mapIndexed { idx, query ->
                SearchHistoryEntity(
                    query = query,
                    searchedAtMs = baseTime + idx
                )
            }
            val lyricsEntities = bundle.library.lyricsRows.map { row ->
                MomiLyricsConverter.convert(row).toEntity(updatedAtMs = baseTime)
            }

            database.withTransaction {
                if (policy == MergePolicy.REPLACE_EXISTING) {
                    database.clearLibraryData()
                }
                database.songDao().upsertAndMergeAll(songEntities)
                database.artistDao().upsertAndMergeAll(artistEntities, crossRefs)
                database.albumDao().upsertAndMergeAll(albumEntities)
                database.playlistDao().upsertAndMergeAll(playlistEntities, playlistSongEntities)
                database.playEventDao().upsertAndMergeAll(playEventEntities)
                database.searchHistoryDao().upsertAndMergeAll(searchEntities)
                database.lyricsDao().upsertAndMergeAll(lyricsEntities)
            }

            onProgress(
                TransferProgress(
                    stage = "Applying settings",
                    fraction = 0.85f,
                    processedCount = totalItems,
                    totalCount = totalItems
                )
            )

            val savedAvatarPath = bundle.avatarBytes?.let { bytes ->
                saveAvatarBytes(bytes)
            }

            preferences.updateSettings { current ->
                val base = if (policy == MergePolicy.REPLACE_EXISTING) {
                    OndaUserSettings()
                } else {
                    current
                }
                base.copy(
                    username = bundle.settings.username ?: base.username,
                    avatarPath = savedAvatarPath ?: base.avatarPath,
                    playerStyle = bundle.settings.playerStyle ?: base.playerStyle,
                    repeatMode = bundle.settings.repeatMode ?: base.repeatMode,
                    shuffleEnabled = bundle.settings.shuffleEnabled ?: base.shuffleEnabled,
                    glassBlurEnabled = bundle.settings.glassBlurEnabled ?: base.glassBlurEnabled,
                    lyricsMotionEnabled = bundle.settings.lyricsMotionEnabled ?: base.lyricsMotionEnabled,
                    maxCacheMb = bundle.settings.maxCacheMb?.coerceAtLeast(64) ?: base.maxCacheMb,
                    eqConfig = bundle.audioLab.eqConfig ?: base.eqConfig,
                    monochromeEnabled = bundle.sourceModules.monochromeEnabled ?: base.monochromeEnabled
                )
            }

            onProgress(
                TransferProgress(
                    stage = "Import complete",
                    fraction = 1.0f,
                    processedCount = totalItems,
                    totalCount = totalItems
                )
            )

            Result.success(
                TransferSummary(
                    format = TransferFormat.MOMI_TRANSFER_V1,
                    importedTracks = songEntities.size,
                    relinkRequiredTracks = relinkCount,
                    importedArtists = artistEntities.size,
                    importedAlbums = albumEntities.size,
                    importedPlaylists = playlistEntities.size,
                    importedPlayEvents = playEventEntities.size,
                    importedSearchQueries = searchEntities.size,
                    importedLyrics = lyricsEntities.size,
                    importedProfile = bundle.settings.username != null || savedAvatarPath != null,
                    importedEqConfig = bundle.audioLab.eqConfig != null,
                    importedPlayerStyle = bundle.settings.playerStyle
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            workDir.deleteRecursively()
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

        return if (accessible) {
            track.copy(needsRelink = false)
        } else {
            track.copy(needsRelink = true)
        }
    }

    private fun saveAvatarBytes(bytes: ByteArray): String? {
        return OndaBackupManager.saveOndaAvatarBytes(context.filesDir, bytes)
    }
}
