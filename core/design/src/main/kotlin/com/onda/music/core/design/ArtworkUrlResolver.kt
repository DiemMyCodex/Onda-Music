package com.onda.music.core.design

import java.net.URI

object ArtworkUrlResolver {
    private val googleHosts = setOf(
        "lh3.googleusercontent.com",
        "yt3.ggpht.com",
        "lh3.ggpht.com"
    )

    private val youtubeThumbHosts = setOf(
        "i.ytimg.com",
        "img.youtube.com"
    )

    private val googleSizeRegex = Regex("=(?:w\\d+-h\\d+[^/?#]*|s\\d+[^/?#]*)")
    private val youtubeThumbRegex = Regex(
        "/(?:default|mqdefault|hqdefault|sddefault)\\.(jpg|webp)(?=[?#]|$)",
        RegexOption.IGNORE_CASE
    )

    fun upgradeHighRes(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val trimmed = url.trim()
        val lower = trimmed.lowercase()
        if (lower.startsWith("content://") || lower.startsWith("file://")) {
            return url
        }

        val host = runCatching { URI(trimmed).host?.lowercase() }.getOrNull() ?: return url

        return when {
            host in googleHosts || googleHosts.any { host.endsWith(".$it") } -> {
                val queryIndex = trimmed.indexOfAny(charArrayOf('?', '#'))
                val pathPart = if (queryIndex >= 0) trimmed.substring(0, queryIndex) else trimmed
                val suffixPart = if (queryIndex >= 0) trimmed.substring(queryIndex) else ""
                if (googleSizeRegex.containsMatchIn(pathPart)) {
                    googleSizeRegex.replace(pathPart, "=w1080-h1080-l90-rj") + suffixPart
                } else {
                    trimmed
                }
            }
            host in youtubeThumbHosts || youtubeThumbHosts.any { host.endsWith(".$it") } -> {
                youtubeThumbRegex.replace(trimmed) { match ->
                    "/maxresdefault.${match.groupValues[1].lowercase()}"
                }
            }
            else -> url
        }
    }
}
