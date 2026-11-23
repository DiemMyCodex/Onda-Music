package com.onda.music.core.sources

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LocalMediaFolder(
    val path: String,
    val name: String,
    val trackCount: Int
)

data class LocalMediaScanResult(
    val tracks: List<Track> = emptyList(),
    val folders: List<LocalMediaFolder> = emptyList()
)

@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(requiredPermission()) == PackageManager.PERMISSION_GRANTED

    suspend fun scan(alreadyImportedUris: Set<String> = emptySet()): LocalMediaScanResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext LocalMediaScanResult()
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DATE_ADDED
            )
            val tracks = mutableListOf<Track>()
            val folderCounts = LinkedHashMap<String, Int>()

            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(collection, mediaId).toString()
                        if (contentUri in alreadyImportedUris) continue

                        val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown Track"
                        val rawArtist = cursor.getString(artistCol)
                        val artist = rawArtist
                            ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                            ?: "Unknown Artist"
                        val album = cursor.getString(albumCol)
                            ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        val albumId = cursor.getLong(albumIdCol)
                        val artworkUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString()
                        val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                        val relativePath = if (pathCol >= 0) {
                            cursor.getString(pathCol)?.trimEnd('/').orEmpty()
                        } else {
                            ""
                        }
                        val folderPath = relativePath.ifBlank { "Internal Storage" }
                        folderCounts[folderPath] = (folderCounts[folderPath] ?: 0) + 1
                        val addedAtMs = if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else 0L

                        tracks += Track(
                            id = "local_$mediaId",
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId.toString(),
                            durationMs = durationMs,
                            artworkUrl = artworkUri,
                            source = SourceKind.LOCAL,
                            localUri = contentUri,
                            inLibrary = true,
                            addedAtMs = addedAtMs
                        )
                    }
                }
            }

            val folders = folderCounts.entries.map { (path, count) ->
                LocalMediaFolder(
                    path = path,
                    name = path.substringAfterLast('/').ifBlank { path },
                    trackCount = count
                )
            }
            LocalMediaScanResult(tracks = tracks, folders = folders)
        }

    companion object {
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
