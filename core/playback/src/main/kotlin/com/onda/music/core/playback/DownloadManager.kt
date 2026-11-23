package com.onda.music.core.playback

import android.content.Context
import com.onda.music.core.database.DownloadDao
import com.onda.music.core.database.DownloadEntity
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.database.toModel
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val songDao: SongDao,
    private val streamResolver: StreamResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val concurrencyGate = Semaphore(2)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val downloadsDir: File
        get() = File(context.filesDir, "onda_downloads").apply {
            if (!exists()) mkdirs()
        }

    val downloads: Flow<List<DownloadItem>> = downloadDao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }

    val completedDownloads: Flow<List<DownloadItem>> = downloadDao.observeCompleted().map { entities ->
        entities.map { it.toModel() }
    }

    fun observeDownload(trackId: String): Flow<DownloadItem?> {
        return downloadDao.observeByTrackId(trackId).map { it?.toModel() }
    }

    suspend fun queueDownload(track: Track) {
        val existing = downloadDao.getByTrackId(track.id)
        if (existing != null && existing.status == DownloadStatus.COMPLETED) {
            val path = existing.localFilePath
            if (!path.isNullOrBlank() && File(path).exists()) {
                return
            }
        }
        if (activeJobs[track.id]?.isActive == true) {
            return
        }

        songDao.upsertAndMerge(track.toEntity())
        downloadDao.upsert(
            DownloadEntity(
                trackId = track.id,
                status = DownloadStatus.QUEUED,
                progress = 0f,
                localFilePath = null,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                updatedAtMs = System.currentTimeMillis()
            )
        )

        val job = scope.launch {
            concurrencyGate.withPermit {
                executeDownload(track)
            }
        }
        activeJobs[track.id] = job
        job.invokeOnCompletion {
            activeJobs.remove(track.id)
        }
    }

    fun enqueueDownload(track: Track) {
        scope.launch {
            queueDownload(track)
        }
    }

    suspend fun cancelDownload(trackId: String) {
        activeJobs.remove(trackId)?.cancel()
        tempFileFor(trackId).delete()
        downloadDao.deleteByTrackId(trackId)
    }

    suspend fun deleteDownload(trackId: String) {
        activeJobs.remove(trackId)?.cancel()
        val existing = downloadDao.getByTrackId(trackId)
        existing?.localFilePath?.let { path ->
            File(path).delete()
        }
        tempFileFor(trackId).delete()
        downloadsDir.listFiles { file ->
            file.name.startsWith("${sanitizeId(trackId)}.")
        }?.forEach { it.delete() }
        downloadDao.deleteByTrackId(trackId)
    }

    suspend fun getDownloadedFilePath(trackId: String): String? {
        val entity = downloadDao.getByTrackId(trackId) ?: return null
        if (entity.status != DownloadStatus.COMPLETED) return null
        val path = entity.localFilePath ?: return null
        return path.takeIf { File(it).exists() }
    }

    private suspend fun executeDownload(track: Track) = withContext(Dispatchers.IO) {
        val tempFile = tempFileFor(track.id)
        try {
            downloadDao.updateProgress(
                trackId = track.id,
                status = DownloadStatus.DOWNLOADING,
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                localFilePath = null,
                updatedAtMs = System.currentTimeMillis()
            )

            val resolved = streamResolver.resolve(track, bypassCache = true).getOrThrow()
            val extension = extensionFor(resolved.mimeType, resolved.codec)
            val targetFile = File(downloadsDir, "${sanitizeId(track.id)}.$extension")

            if (resolved.url.startsWith("file://")) {
                val sourceFile = File(resolved.url.removePrefix("file://"))
                if (!sourceFile.exists()) {
                    throw IllegalStateException("Local file missing: ${resolved.url}")
                }
                sourceFile.copyTo(tempFile, overwrite = true)
                val size = tempFile.length()
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                downloadDao.updateProgress(
                    trackId = track.id,
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    bytesDownloaded = size,
                    totalBytes = size,
                    localFilePath = targetFile.absolutePath,
                    updatedAtMs = System.currentTimeMillis()
                )
                return@withContext
            }

            val connection = (URL(resolved.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw StreamHttpException(code, "Download failed with HTTP $code")
                }
                val totalBytes = connection.contentLengthLong.coerceAtLeast(0L)
                var downloadedBytes = 0L
                var lastPersistBytes = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (downloadedBytes - lastPersistBytes >= 65_536L) {
                                lastPersistBytes = downloadedBytes
                                val progress = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                } else {
                                    0f
                                }
                                downloadDao.updateProgress(
                                    trackId = track.id,
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = progress,
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes,
                                    localFilePath = null,
                                    updatedAtMs = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }

                if (!isActive) {
                    tempFile.delete()
                    return@withContext
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                val finalSize = targetFile.length()
                downloadDao.updateProgress(
                    trackId = track.id,
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    bytesDownloaded = finalSize,
                    totalBytes = if (totalBytes > 0L) totalBytes else finalSize,
                    localFilePath = targetFile.absolutePath,
                    updatedAtMs = System.currentTimeMillis()
                )
            } finally {
                connection.disconnect()
            }
        } catch (ce: CancellationException) {
            tempFile.delete()
            throw ce
        } catch (_: Throwable) {
            tempFile.delete()
            downloadDao.updateProgress(
                trackId = track.id,
                status = DownloadStatus.FAILED,
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                localFilePath = null,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun tempFileFor(trackId: String): File {
        return File(downloadsDir, "${sanitizeId(trackId)}.tmp")
    }

    private fun sanitizeId(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun extensionFor(mimeType: String, codec: String): String {
        val lowerMime = mimeType.lowercase()
        val lowerCodec = codec.lowercase()
        return when {
            "flac" in lowerMime || "flac" in lowerCodec -> "flac"
            "webm" in lowerMime || "opus" in lowerCodec -> "webm"
            "mp4" in lowerMime || "aac" in lowerCodec || "m4a" in lowerMime -> "m4a"
            "mpeg" in lowerMime || "mp3" in lowerCodec -> "mp3"
            else -> "m4a"
        }
    }
}
