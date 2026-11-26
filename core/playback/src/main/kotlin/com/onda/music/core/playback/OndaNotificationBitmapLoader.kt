package com.onda.music.core.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toModel
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

@UnstableApi
class OndaNotificationBitmapLoader(
    private val applicationContext: Context,
    private val sessionStore: PlaybackSessionStore,
    private val songDao: SongDao
) : BitmapLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, Bitmap>(MAX_CACHE_ENTRIES)

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/", ignoreCase = true)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return scope.future {
            val decoded = decodeScaledBitmap(data)
                ?: throw IllegalStateException("Unable to decode artwork bytes")
            normalizeBitmap(decoded)
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return scope.future {
            loadBitmapSync(uri)
        }
    }

    fun release() {
        scope.cancel()
        cache.evictAll()
    }

    private suspend fun loadBitmapSync(uri: Uri): Bitmap {
        val uriString = uri.toString()
        val cached = cache.get(uriString)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val scheme = uri.scheme?.lowercase()
        val loaded = if (scheme == "http" || scheme == "https") {
            loadHttpBitmap(uriString)
        } else {
            loadLocalBitmap(uri, uriString)
        } ?: throw IllegalStateException("Unable to load notification artwork from $uriString")

        val normalized = normalizeBitmap(loaded)
        cache.put(uriString, normalized)
        return normalized
    }

    private fun loadHttpBitmap(uriString: String): Bitmap? {
        val matchedTrack = sessionStore.findTrackByArtworkUrl(uriString)
        val originalArtworkUrl = matchedTrack?.artworkUrl
        val candidates = buildNotificationArtworkFallbackUrls(uriString, originalArtworkUrl)
        for (candidateUrl in candidates) {
            val decoded = fetchHttpBitmap(candidateUrl)
            if (decoded != null) {
                return decoded
            }
        }
        return null
    }

    private fun fetchHttpBitmap(urlString: String): Bitmap? {
        val connection = (runCatching { URL(urlString).openConnection() }.getOrNull() as? HttpURLConnection)
            ?: return null
        return try {
            connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
            connection.readTimeout = HTTP_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", DEFAULT_NOTIFICATION_ARTWORK_USER_AGENT)
            connection.setRequestProperty("Accept", "image/webp,image/png,image/jpeg")
            val code = connection.responseCode
            if (code !in 200..299) {
                return null
            }
            val bytes = connection.inputStream.use { readBoundedBytes(it, MAX_HTTP_IMAGE_BYTES) } ?: return null
            decodeScaledBitmap(bytes)
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun loadLocalBitmap(uri: Uri, uriString: String): Bitmap? {
        val direct = loadLocalUriDirect(uri)
        if (direct != null) {
            return direct
        }
        val matchedTrack = sessionStore.findTrackByArtworkUrl(uriString)
            ?: findLocalTrackByArtworkInDb(uriString)
            ?: return null
        val fallbackUriString = matchedTrack.localUri?.takeIf { it.isNotBlank() } ?: return null
        if (fallbackUriString == uriString) {
            return null
        }
        val fallbackUri = if (fallbackUriString.startsWith("/")) {
            Uri.fromFile(File(fallbackUriString))
        } else {
            Uri.parse(fallbackUriString)
        }
        return loadLocalUriDirect(fallbackUri)
    }

    private fun loadLocalUriDirect(uri: Uri): Bitmap? {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "content") {
            val thumbnail = runCatching {
                applicationContext.contentResolver.loadThumbnail(
                    uri,
                    Size(MAX_ARTWORK_DIM_PX, MAX_ARTWORK_DIM_PX),
                    null
                )
            }.getOrNull()
            if (thumbnail != null) {
                return thumbnail
            }

            val streamBitmap = runCatching {
                applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                    readBoundedBytes(stream, MAX_LOCAL_IMAGE_BYTES)
                }?.let { decodeScaledBitmap(it) }
            }.getOrNull()
            if (streamBitmap != null) {
                return streamBitmap
            }

            return extractEmbeddedBitmapFromContentUri(uri)
        }

        val path = uri.path ?: return null
        val file = File(path)
        if (file.exists() && file.isFile) {
            val fileBitmap = runCatching {
                file.inputStream().use { readBoundedBytes(it, MAX_LOCAL_IMAGE_BYTES) }
                    ?.let { decodeScaledBitmap(it) }
            }.getOrNull()
            if (fileBitmap != null) {
                return fileBitmap
            }
            return extractEmbeddedBitmapFromPath(path)
        }
        return null
    }

    private fun extractEmbeddedBitmapFromContentUri(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(applicationContext, uri)
            val embedded = retriever.embeddedPicture ?: return null
            decodeScaledBitmap(embedded)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractEmbeddedBitmapFromPath(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val embedded = retriever.embeddedPicture ?: return null
            decodeScaledBitmap(embedded)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun findLocalTrackByArtworkInDb(uriString: String): Track? {
        val target = uriString.trim()
        val entities = runCatching { songDao.getAll() }.getOrDefault(emptyList())
        return entities
            .asSequence()
            .filter { it.source == SourceKind.LOCAL }
            .map { it.toModel() }
            .firstOrNull { track ->
                val trackArtwork = track.artworkUrl?.trim()
                val upgradedArtwork = upgradeNotificationArtworkUrl(trackArtwork)?.trim()
                val trackLocalUri = track.localUri?.trim()
                trackArtwork == target || upgradedArtwork == target || trackLocalUri == target
            }
    }

    private fun decodeScaledBitmap(data: ByteArray): Bitmap? {
        if (data.isEmpty()) return null
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)
        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) {
            return BitmapFactory.decodeByteArray(data, 0, data.size)
        }
        val sampleSize = computeBitmapSampleSize(width, height, MAX_ARTWORK_DIM_PX)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
    }

    private fun normalizeBitmap(bitmap: Bitmap): Bitmap {
        var current = bitmap
        if (current.config != Bitmap.Config.ARGB_8888) {
            val copied = runCatching { current.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            if (copied != null) {
                current = copied
            }
        }
        val maxSide = max(current.width, current.height)
        if (maxSide > MAX_ARTWORK_DIM_PX && current.width > 0 && current.height > 0) {
            val scale = MAX_ARTWORK_DIM_PX.toFloat() / maxSide.toFloat()
            val targetW = (current.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (current.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(current, targetW, targetH, true)
            if (scaled !== current && current !== bitmap) {
                current.recycle()
            }
            current = scaled
        }
        return current
    }

    private fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        private const val MAX_CACHE_ENTRIES = 24
        private const val MAX_ARTWORK_DIM_PX = 512
        private const val HTTP_CONNECT_TIMEOUT_MS = 10_000
        private const val HTTP_READ_TIMEOUT_MS = 15_000
        private const val MAX_HTTP_IMAGE_BYTES = 12 * 1024 * 1024
        private const val MAX_LOCAL_IMAGE_BYTES = 16 * 1024 * 1024
    }
}
