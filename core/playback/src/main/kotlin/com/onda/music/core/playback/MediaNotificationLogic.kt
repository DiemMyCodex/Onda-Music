package com.onda.music.core.playback

import com.onda.music.core.model.RepeatMode
import java.net.URI

const val ACTION_TOGGLE_LIKE = "com.onda.music.action.TOGGLE_LIKE"
const val ACTION_CYCLE_REPEAT = "com.onda.music.action.CYCLE_REPEAT"
const val ACTION_TOGGLE_SHUFFLE = "com.onda.music.action.TOGGLE_SHUFFLE"
const val ACTION_TOGGLE_SLEEP_TIMER = "com.onda.music.action.TOGGLE_SLEEP_TIMER"
const val DEFAULT_NOTIF_SLEEP_DURATION_MS = 30L * 60L * 1000L

private val NOTIFICATION_ARTWORK_HOSTS = setOf(
    "lh3.googleusercontent.com",
    "yt3.ggpht.com",
    "lh3.ggpht.com"
)

private val NOTIFICATION_ARTWORK_SIZE_REGEX = Regex("=(?:w\\d+-h\\d+[^/?#]*|s\\d+[^/?#]*)")
private const val NOTIFICATION_ARTWORK_TARGET_SIZE = "=w720-h720-l90-rj"

data class NotificationButtonsState(
    val isLiked: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val sleepTimerActive: Boolean = false
)

fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}

fun upgradeNotificationArtworkUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return rawUrl
    val trimmed = rawUrl.trim()
    val lower = trimmed.lowercase()
    if (lower.startsWith("content://") || lower.startsWith("file://")) {
        return rawUrl
    }

    val host = runCatching { URI(trimmed).host?.lowercase() }.getOrNull() ?: return rawUrl
    val matchesGoogleHost = host in NOTIFICATION_ARTWORK_HOSTS ||
        NOTIFICATION_ARTWORK_HOSTS.any { host.endsWith(".$it") }
    if (!matchesGoogleHost) {
        return rawUrl
    }

    val queryIndex = trimmed.indexOfAny(charArrayOf('?', '#'))
    val pathPart = if (queryIndex >= 0) trimmed.substring(0, queryIndex) else trimmed
    val suffixPart = if (queryIndex >= 0) trimmed.substring(queryIndex) else ""
    return if (NOTIFICATION_ARTWORK_SIZE_REGEX.containsMatchIn(pathPart)) {
        NOTIFICATION_ARTWORK_SIZE_REGEX.replace(pathPart, NOTIFICATION_ARTWORK_TARGET_SIZE) + suffixPart
    } else {
        trimmed
    }
}
