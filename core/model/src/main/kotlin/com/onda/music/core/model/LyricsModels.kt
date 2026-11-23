package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LyricsWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

@Immutable
@Serializable
data class LyricsLine(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
    val translatedText: String? = null,
    val romanizedText: String? = null,
    val isInterlude: Boolean = false
)

@Serializable
enum class LyricsSource {
    LRCLIB,
    YOUTUBE_MUSIC,
    EMBEDDED,
    CACHE
}

@Immutable
@Serializable
data class LyricsDocument(
    val trackId: String,
    val lines: List<LyricsLine> = emptyList(),
    val isSynced: Boolean = false,
    val hasWordTiming: Boolean = false,
    val source: LyricsSource = LyricsSource.LRCLIB
)
