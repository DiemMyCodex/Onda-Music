package com.onda.music.core.playback

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

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
    private val defaultCacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val monochromeUpgradeTimeoutMs: Long = DEFAULT_MONOCHROME_UPGRADE_TIMEOUT_MS
) {
    private data class CacheEntry(
        val option: StreamOption,
        val expiresAtMs: Long
    )

    private val urlCache = ConcurrentHashMap<String, CacheEntry>()
    private val rotatedTrackIds = ConcurrentHashMap.newKeySet<String>()
    private val monochromeFallbackTrackIds = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Result<StreamOption>>>()

    suspend fun prefetch(track: Track) {
        runCatching { resolve(track, bypassCache = false) }
    }

    suspend fun resolve(
        track: Track,
        bypassCache: Boolean = false,
        skipMonochrome: Boolean = false
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
            val mine = CompletableDeferred<Result<StreamOption>>()
            val existing = inFlight.putIfAbsent(track.id, mine)
            if (existing != null) {
                return existing.await()
            }
            return try {
                val resolved = resolveNetwork(track, skipMonochrome)
                mine.complete(resolved)
                resolved
            } catch (ce: CancellationException) {
                inFlight.remove(track.id, mine)
                mine.cancel(ce)
                throw ce
            } catch (t: Throwable) {
                val failure = Result.failure<StreamOption>(t)
                mine.complete(failure)
                failure
            } finally {
                inFlight.remove(track.id, mine)
            }
        }

        urlCache.remove(track.id)
        return resolveNetwork(track, skipMonochrome)
    }

    private suspend fun resolveNetwork(
        track: Track,
        skipMonochrome: Boolean = false
    ): Result<StreamOption> {
        val monochromeEnabled = !skipMonochrome &&
            preferencesProvider.isMonochromeEnabled() &&
            shouldTryMonochrome(track)
        val isExplicitMonochrome = !track.monochromeId.isNullOrBlank() || track.source == SourceKind.MONOCHROME
        val videoId = extractYtVideoId(track)

        if (monochromeEnabled && !isExplicitMonochrome && videoId != null) {
            return coroutineScope {
                val monoDeferred = async {
                    try {
                        monochromeSource.resolveStream(track)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Throwable) {
                        null
                    }
                }
                val ytDeferred = async {
                    resolveYtWithSingleRotation(track.id, videoId)
                }

                val firstIsMono = select<Boolean> {
                    monoDeferred.onAwait { true }
                    ytDeferred.onAwait { false }
                }

                if (firstIsMono) {
                    val fastMono = monoDeferred.await()
                    if (fastMono != null) {
                        ytDeferred.cancel()
                        cacheOption(track.id, fastMono)
                        return@coroutineScope Result.success(fastMono)
                    }
                } else {
                    val ytResultFirst = ytDeferred.await()
                    if (ytResultFirst.isSuccess) {
                        val graceMs = minOf(monochromeUpgradeTimeoutMs, MONOCHROME_POST_YT_GRACE_MS)
                        val fastMono = withTimeoutOrNull(graceMs) {
                            monoDeferred.await()
                        }
                        if (fastMono != null) {
                            cacheOption(track.id, fastMono)
                            return@coroutineScope Result.success(fastMono)
                        }
                        monoDeferred.cancel()
                        val ytOption = ytResultFirst.getOrThrow()
                        cacheOption(track.id, ytOption)
                        return@coroutineScope Result.success(ytOption)
                    }
                    val graceMono = withTimeoutOrNull(monochromeUpgradeTimeoutMs) {
                        monoDeferred.await()
                    }
                    if (graceMono != null) {
                        cacheOption(track.id, graceMono)
                        return@coroutineScope Result.success(graceMono)
                    }
                    monoDeferred.cancel()
                    return@coroutineScope resolveWithNewPipeFallback(
                        trackId = track.id,
                        videoId = videoId,
                        initialError = ytResultFirst.exceptionOrNull()
                    )
                }

                monoDeferred.cancel()

                val ytResult = ytDeferred.await()
                if (ytResult.isSuccess) {
                    val option = ytResult.getOrThrow()
                    cacheOption(track.id, option)
                    return@coroutineScope Result.success(option)
                }
                resolveWithNewPipeFallback(
                    trackId = track.id,
                    videoId = videoId,
                    initialError = ytResult.exceptionOrNull()
                )
            }
        }

        var lastError: Throwable? = null
        if (monochromeEnabled) {
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

    suspend fun handleMonochromePlaybackFailure(track: Track): Result<StreamOption> {
        extractYtVideoId(track)
            ?: return Result.failure(IllegalStateException("No YouTube fallback for track ${track.id}"))
        urlCache.remove(track.id)
        if (!monochromeFallbackTrackIds.add(track.id)) {
            return Result.failure(
                IllegalStateException("Monochrome fallback already attempted for track ${track.id}")
            )
        }
        return resolve(track = track, bypassCache = true, skipMonochrome = true)
    }

    fun invalidateCache(trackId: String) {
        urlCache.remove(trackId)
    }

    fun clearCache() {
        urlCache.clear()
        rotatedTrackIds.clear()
        monochromeFallbackTrackIds.clear()
    }

    fun resetRotationState(trackId: String) {
        rotatedTrackIds.remove(trackId)
        monochromeFallbackTrackIds.remove(trackId)
    }

    fun cachedSize(): Int = urlCache.size

    private suspend fun resolveWithNewPipeFallback(
        trackId: String,
        videoId: String,
        initialError: Throwable?
    ): Result<StreamOption> {
        var lastError = initialError
        try {
            val fallbackOption = newPipeSource.resolveStream(videoId)
            if (fallbackOption != null) {
                cacheOption(trackId, fallbackOption)
                return Result.success(fallbackOption)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            lastError = t
        }
        return Result.failure(
            lastError ?: IllegalStateException("No stream available for track $trackId")
        )
    }

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
        const val DEFAULT_MONOCHROME_UPGRADE_TIMEOUT_MS: Long = 500L
        private const val MONOCHROME_POST_YT_GRACE_MS: Long = 160L
    }
}
