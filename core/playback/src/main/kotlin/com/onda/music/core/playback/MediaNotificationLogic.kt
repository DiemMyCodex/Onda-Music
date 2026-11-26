package com.onda.music.core.playback

import com.onda.music.core.model.RepeatMode
import java.net.URI

const val ACTION_TOGGLE_LIKE = "com.onda.music.action.TOGGLE_LIKE"
const val ACTION_CYCLE_REPEAT = "com.onda.music.action.CYCLE_REPEAT"
const val ACTION_TOGGLE_SHUFFLE = "com.onda.music.action.TOGGLE_SHUFFLE"
const val ACTION_TOGGLE_SLEEP_TIMER = "com.onda.music.action.TOGGLE_SLEEP_TIMER"
const val DEFAULT_NOTIF_SLEEP_DURATION_MS = 30L * 60L * 1000L
const val DEFAULT_NOTIFICATION_ARTWORK_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

private val NOTIFICATION_ARTWORK_HOSTS = setOf(
    "lh3.googleusercontent.com",
    "yt3.ggpht.com",
    "lh3.ggpht.com"
)

private val YOUTUBE_THUMB_HOSTS = setOf(
    "i.ytimg.com",
    "img.youtube.com"
)
private val YOUTUBE_SHARD_HOST_REGEX = Regex("^i\\d*\\.ytimg\\.com$")

private val NOTIFICATION_ARTWORK_SIZE_REGEX = Regex("=(?:w\\d+-h\\d+[^/?#]*|s\\d+[^/?#]*)")
private val YOUTUBE_THUMB_REGEX = Regex(
    "/(?:default|mqdefault|hqdefault|sddefault)\\.(jpg|webp)(?=[?#]|$)",
    RegexOption.IGNORE_CASE
)
private val YOUTUBE_FALLBACK_SOURCE_REGEX = Regex(
    "/(?:maxresdefault|sddefault)\\.(jpg|webp)(?=[?#]|$)",
    RegexOption.IGNORE_CASE
)
private const val NOTIFICATION_ARTWORK_TARGET_SIZE = "=w720-h720-l90-rj"

data class NotificationButtonsState(
    val isLiked: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val sleepTimerActive: Boolean = false
)

enum class NotificationButtonSlot {
    BACK_SECONDARY,
    FORWARD_SECONDARY,
    OVERFLOW
}

data class NotificationButtonSpec(
    val action: String,
    val slot: NotificationButtonSlot,
    val active: Boolean
)

enum class SkipNextBehavior {
    SEEK_NEXT,
    WRAP_TO_FIRST,
    NO_OP
}

enum class SkipPreviousBehavior {
    SEEK_TO_START,
    SEEK_PREVIOUS,
    NO_OP
}

fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}

fun buildNotificationButtonSpecs(state: NotificationButtonsState): List<NotificationButtonSpec> = listOf(
    NotificationButtonSpec(
        action = ACTION_TOGGLE_LIKE,
        slot = NotificationButtonSlot.BACK_SECONDARY,
        active = state.isLiked
    ),
    NotificationButtonSpec(
        action = ACTION_CYCLE_REPEAT,
        slot = NotificationButtonSlot.FORWARD_SECONDARY,
        active = state.repeatMode != RepeatMode.OFF
    ),
    NotificationButtonSpec(
        action = ACTION_TOGGLE_SHUFFLE,
        slot = NotificationButtonSlot.OVERFLOW,
        active = state.shuffleEnabled
    ),
    NotificationButtonSpec(
        action = ACTION_TOGGLE_SLEEP_TIMER,
        slot = NotificationButtonSlot.OVERFLOW,
        active = state.sleepTimerActive
    )
)

fun resolveSkipNextBehavior(hasNextMediaItem: Boolean, mediaItemCount: Int): SkipNextBehavior = when {
    mediaItemCount <= 0 -> SkipNextBehavior.NO_OP
    hasNextMediaItem -> SkipNextBehavior.SEEK_NEXT
    else -> SkipNextBehavior.WRAP_TO_FIRST
}

fun resolveSkipPreviousBehavior(
    hasPreviousMediaItem: Boolean,
    mediaItemCount: Int,
    currentPositionMs: Long,
    thresholdMs: Long = 3_000L
): SkipPreviousBehavior = when {
    mediaItemCount <= 0 -> SkipPreviousBehavior.NO_OP
    currentPositionMs > thresholdMs -> SkipPreviousBehavior.SEEK_TO_START
    hasPreviousMediaItem -> SkipPreviousBehavior.SEEK_PREVIOUS
    else -> SkipPreviousBehavior.SEEK_TO_START
}

fun computeBitmapSampleSize(srcWidth: Int, srcHeight: Int, maxDim: Int = 512): Int {
    if (srcWidth <= 0 || srcHeight <= 0 || maxDim <= 0) return 1
    var sampleSize = 1
    while (srcWidth / sampleSize > maxDim || srcHeight / sampleSize > maxDim) {
        sampleSize *= 2
    }
    return sampleSize
}

fun upgradeNotificationArtworkUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return rawUrl
    val trimmed = rawUrl.trim()
    val lower = trimmed.lowercase()
    if (lower.startsWith("content://") || lower.startsWith("file://")) {
        return rawUrl
    }

    val host = runCatching { URI(trimmed).host?.lowercase() }.getOrNull() ?: return rawUrl
    return when {
        isGoogleArtworkHost(host) -> {
            val queryIndex = trimmed.indexOfAny(charArrayOf('?', '#'))
            val pathPart = if (queryIndex >= 0) trimmed.substring(0, queryIndex) else trimmed
            val suffixPart = if (queryIndex >= 0) trimmed.substring(queryIndex) else ""
            if (NOTIFICATION_ARTWORK_SIZE_REGEX.containsMatchIn(pathPart)) {
                NOTIFICATION_ARTWORK_SIZE_REGEX.replace(pathPart, NOTIFICATION_ARTWORK_TARGET_SIZE) + suffixPart
            } else {
                trimmed
            }
        }
        isYoutubeThumbHost(host) -> {
            val queryIndex = trimmed.indexOfAny(charArrayOf('?', '#'))
            val pathPart = if (queryIndex >= 0) trimmed.substring(0, queryIndex) else trimmed
            if (YOUTUBE_THUMB_REGEX.containsMatchIn(pathPart)) {
                YOUTUBE_THUMB_REGEX.replace(pathPart) { match ->
                    "/maxresdefault.${match.groupValues[1].lowercase()}"
                }
            } else {
                trimmed
            }
        }
        else -> rawUrl
    }
}

fun buildNotificationArtworkFallbackUrls(
    requestedUrl: String,
    originalUrl: String? = null
): List<String> {
    val trimmedRequested = requestedUrl.trim()
    val upgradedOrRequested = upgradeNotificationArtworkUrl(trimmedRequested)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: trimmedRequested.takeIf { it.isNotBlank() }

    val candidates = mutableListOf<String>()
    if (upgradedOrRequested != null) {
        candidates.add(upgradedOrRequested)
        val host = runCatching { URI(upgradedOrRequested).host?.lowercase() }.getOrNull()
        if (host != null && isYoutubeThumbHost(host)) {
            val queryIndex = upgradedOrRequested.indexOfAny(charArrayOf('?', '#'))
            val pathPart = if (queryIndex >= 0) upgradedOrRequested.substring(0, queryIndex) else upgradedOrRequested
            val match = YOUTUBE_FALLBACK_SOURCE_REGEX.find(pathPart)
            if (match != null) {
                val ext = match.groupValues[1].lowercase()
                candidates.add(YOUTUBE_FALLBACK_SOURCE_REGEX.replace(pathPart, "/hqdefault.jpg"))
                if (ext == "webp") {
                    candidates.add(YOUTUBE_FALLBACK_SOURCE_REGEX.replace(pathPart, "/hqdefault.webp"))
                }
            }
        }
    }

    originalUrl?.trim()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
    trimmedRequested.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
    return candidates.distinct()
}

private fun isGoogleArtworkHost(host: String): Boolean {
    return host in NOTIFICATION_ARTWORK_HOSTS ||
        NOTIFICATION_ARTWORK_HOSTS.any { host.endsWith(".$it") }
}

private fun isYoutubeThumbHost(host: String): Boolean {
    return host in YOUTUBE_THUMB_HOSTS ||
        YOUTUBE_THUMB_HOSTS.any { host.endsWith(".$it") } ||
        YOUTUBE_SHARD_HOST_REGEX.matches(host)
}
