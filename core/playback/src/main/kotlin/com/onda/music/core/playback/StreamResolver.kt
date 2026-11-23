package com.onda.music.core.playback

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

class StreamHttpException(
    val statusCode: Int,
    message: String = "HTTP $statusCode"
) : Exception(message)

interface LocalFileChecker {
    suspend fun isReadable(uriString: String): Boolean
}

interface DownloadedCacheLookup {
    suspend fun findDownloaded(trackId: String): StreamOption?
}

interface MonochromeStreamSource {
    suspend fun resolveStream(track: Track): StreamOption?
}

interface YtInnerTubeStreamSource {
    suspend fun resolveStream(videoId: String): StreamOption?
    suspend fun rotateClient(): String
}

interface NewPipeStreamSource {
    suspend fun resolveStream(videoId: String): StreamOption?
}

interface StreamPreferencesProvider {
    suspend fun isMonochromeEnabled(): Boolean
}

class StreamResolver(
    private val localFileChecker: LocalFileChecker,
    private val downloadedCacheLookup: DownloadedCacheLookup,
    private val monochromeSource: MonochromeStreamSource,
    private val ytInnerTubeSource: YtInnerTubeStreamSource,
    private val newPipeSource: NewPipeStreamSource,
    private val preferencesProvider: StreamPreferencesProvider,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val defaultCacheTtlMs: Long = DEFAULT_CACHE_TTL_MS
) {
    private data class CacheEntry(
        val option: StreamOption,
        val expiresAtMs: Long
    )

    private val urlCache = ConcurrentHashMap<String, CacheEntry>()
    private val rotatedTrackIds = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolve(
        track: Track,
        bypassCache: Boolean = false
    ): Result<StreamOption> {
        val localUri = track.localUri
        if (!localUri.isNullOrBlank() && localFileChecker.isReadable(localUri)) {
            return Result.success(
                StreamOption(
                    url = localUri,
                    mimeType = "audio/*",
                    bitrateKbps = 0,
                    codec = "local",
                    qualityLabel = "Local",
                    expiresAtMs = null,
                    source = SourceKind.LOCAL
                )
            )
        }

        val downloaded = downloadedCacheLookup.findDownloaded(track.id)
        if (downloaded != null) {
            return Result.success(downloaded)
        }

        val now = clockMs()
        if (!bypassCache) {
            val cached = urlCache[track.id]
            if (cached != null) {
                if (now < cached.expiresAtMs) {
                    return Result.success(cached.option)
                }
                urlCache.remove(track.id)
            }
        } else {
            urlCache.remove(track.id)
        }

        var lastError: Throwable? = null

        if (preferencesProvider.isMonochromeEnabled() && shouldTryMonochrome(track)) {
            try {
                val monochromeOption = monochromeSource.resolveStream(track)
                if (monochromeOption != null) {
                    cacheOption(track.id, monochromeOption)
                    return Result.success(monochromeOption)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
            }
        }

        val videoId = extractYtVideoId(track)
        if (videoId != null) {
            val ytResult = resolveYtWithSingleRotation(track.id, videoId)
            if (ytResult.isSuccess) {
                val option = ytResult.getOrThrow()
                cacheOption(track.id, option)
                return Result.success(option)
            }
            lastError = ytResult.exceptionOrNull()

            try {
                val fallbackOption = newPipeSource.resolveStream(videoId)
                if (fallbackOption != null) {
                    cacheOption(track.id, fallbackOption)
                    return Result.success(fallbackOption)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
            }
        }

        return Result.failure(
            lastError ?: IllegalStateException("No stream available for track ${track.id}")
        )
    }

    suspend fun handlePlaybackHttpError(
        track: Track,
        statusCode: Int
    ): Result<StreamOption> {
        if (!isInvalidUrlStatus(statusCode)) {
            return Result.failure(StreamHttpException(statusCode))
        }
        urlCache.remove(track.id)
        if (!rotatedTrackIds.add(track.id)) {
            return Result.failure(
                StreamHttpException(
                    statusCode = statusCode,
                    message = "Stream failed with HTTP $statusCode after client rotation"
                )
            )
        }
        ytInnerTubeSource.rotateClient()
        return resolve(track = track, bypassCache = true)
    }

    fun invalidateCache(trackId: String) {
        urlCache.remove(trackId)
    }

    fun clearCache() {
        urlCache.clear()
        rotatedTrackIds.clear()
    }

    fun resetRotationState(trackId: String) {
        rotatedTrackIds.remove(trackId)
    }

    fun cachedSize(): Int = urlCache.size

    private suspend fun resolveYtWithSingleRotation(
        trackId: String,
        videoId: String
    ): Result<StreamOption> {
        try {
            val option = ytInnerTubeSource.resolveStream(videoId)
            if (option != null) {
                return Result.success(option)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (http: StreamHttpException) {
            if (!isInvalidUrlStatus(http.statusCode)) {
                return Result.failure(http)
            }
            urlCache.remove(trackId)
            if (!rotatedTrackIds.add(trackId)) {
                return Result.failure(http)
            }
            ytInnerTubeSource.rotateClient()
            return try {
                val retried = ytInnerTubeSource.resolveStream(videoId)
                if (retried != null) {
                    Result.success(retried)
                } else {
                    Result.failure(http)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (retryError: Throwable) {
                Result.failure(retryError)
            }
        } catch (t: Throwable) {
            return Result.failure(t)
        }
        return Result.failure(IllegalStateException("InnerTube returned null stream for $videoId"))
    }

    private fun shouldTryMonochrome(track: Track): Boolean {
        return !track.monochromeId.isNullOrBlank() ||
            track.source == SourceKind.MONOCHROME ||
            track.title.isNotBlank()
    }

    private fun extractYtVideoId(track: Track): String? {
        if (!track.ytId.isNullOrBlank()) return track.ytId
        if (track.source == SourceKind.YOUTUBE && track.id.isNotBlank()) return track.id
        return null
    }

    private fun cacheOption(trackId: String, option: StreamOption) {
        val now = clockMs()
        val expiresAt = option.expiresAtMs ?: (now + defaultCacheTtlMs)
        if (expiresAt > now) {
            urlCache[trackId] = CacheEntry(option = option, expiresAtMs = expiresAt)
        }
    }

    private fun isInvalidUrlStatus(statusCode: Int): Boolean {
        return statusCode == 403 || statusCode == 410
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS: Long = 30L * 60L * 1000L
    }
}
