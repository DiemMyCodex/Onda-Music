package com.onda.music.core.playback

import android.content.Context
import android.net.Uri
import com.onda.music.core.database.DownloadDao
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import com.onda.music.core.sources.MonochromeClient
import com.onda.music.core.sources.YtClientKind
import com.onda.music.core.sources.YtMusicClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class AndroidLocalFileChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalFileChecker {
    override suspend fun isReadable(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        if (uriString.startsWith("/")) {
            val file = File(uriString)
            return file.exists() && file.canRead()
        }
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (parsed.scheme == "file") {
            val path = parsed.path ?: return false
            val file = File(path)
            return file.exists() && file.canRead()
        }
        if (parsed.scheme == "content") {
            return runCatching {
                context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { true } ?: false
            }.getOrDefault(false)
        }
        return false
    }
}

@Singleton
class RoomDownloadedCacheLookup @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadedCacheLookup {
    override suspend fun findDownloaded(trackId: String): StreamOption? {
        val entity = downloadDao.getByTrackId(trackId) ?: return null
        if (entity.status != DownloadStatus.COMPLETED) return null
        val path = entity.localFilePath ?: return null
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        val mimeType = when (file.extension.lowercase()) {
            "flac" -> "audio/flac"
            "webm" -> "audio/webm"
            "mp3" -> "audio/mpeg"
            else -> "audio/mp4"
        }
        return StreamOption(
            url = Uri.fromFile(file).toString(),
            mimeType = mimeType,
            bitrateKbps = 0,
            codec = file.extension.ifBlank { "offline" },
            qualityLabel = "Offline",
            expiresAtMs = null,
            source = SourceKind.LOCAL
        )
    }
}

@Singleton
class RealMonochromeStreamSource @Inject constructor(
    private val monochromeClient: MonochromeClient
) : MonochromeStreamSource {
    override suspend fun resolveStream(track: Track): StreamOption? {
        return monochromeClient.resolveTrackStream(track).getOrNull()
    }
}

@Singleton
class RealYtInnerTubeStreamSource @Inject constructor(
    private val ytMusicClient: YtMusicClient,
    private val ondaPreferences: OndaPreferences
) : YtInnerTubeStreamSource {
    private val clientIndex = AtomicInteger(0)

    override suspend fun resolveStream(videoId: String): StreamOption? {
        val settings = ondaPreferences.currentSettings()
        ytMusicClient.setCookie(settings.ytCookie)

        val clients = YtClientKind.defaultOrder
        val currentIdx = Math.floorMod(clientIndex.get(), clients.size)
        val rotatedOrder = clients.drop(currentIdx) + clients.take(currentIdx)

        val result = ytMusicClient.resolveStreams(
            videoId = videoId,
            clientOrder = listOf(rotatedOrder.first())
        )
        if (result.isSuccess) {
            return result.getOrNull()?.maxByOrNull { it.bitrateKbps }
        }
        val error = result.exceptionOrNull()
        if (error is CancellationException) throw error
        val message = error?.message.orEmpty()
        if ("HTTP 403" in message) {
            throw StreamHttpException(403, message)
        }
        if ("HTTP 410" in message) {
            throw StreamHttpException(410, message)
        }
        val fallbackRemaining = rotatedOrder.drop(1)
        if (fallbackRemaining.isNotEmpty()) {
            val secondary = ytMusicClient.resolveStreams(videoId, fallbackRemaining)
            if (secondary.isSuccess) {
                return secondary.getOrNull()?.maxByOrNull { it.bitrateKbps }
            }
            val secondaryError = secondary.exceptionOrNull()
            if (secondaryError is CancellationException) throw secondaryError
            val secondaryMsg = secondaryError?.message.orEmpty()
            if ("HTTP 403" in secondaryMsg) throw StreamHttpException(403, secondaryMsg)
            if ("HTTP 410" in secondaryMsg) throw StreamHttpException(410, secondaryMsg)
        }
        if (error != null) throw error
        return null
    }

    override suspend fun rotateClient(): String {
        val clients = YtClientKind.defaultOrder
        val next = Math.floorMod(clientIndex.incrementAndGet(), clients.size)
        return clients[next].clientName
    }
}

@Singleton
class RealNewPipeStreamSource @Inject constructor(
    private val ytMusicClient: YtMusicClient
) : NewPipeStreamSource {
    override suspend fun resolveStream(videoId: String): StreamOption? {
        return ytMusicClient.extractWithNewPipe(videoId).getOrNull()?.firstOrNull()
    }
}

@Singleton
class OndaStreamPreferencesProvider @Inject constructor(
    private val ondaPreferences: OndaPreferences
) : StreamPreferencesProvider {
    override suspend fun isMonochromeEnabled(): Boolean {
        return ondaPreferences.currentSettings().monochromeEnabled
    }
}
