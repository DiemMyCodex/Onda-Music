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
    BETTER_LYRICS,
    SIMP_MUSIC,
    LRCLIB,
    NETEASE,
    YOUTUBE_MUSIC,
    EMBEDDED,
    CACHE
}

@Serializable
enum class LyricsMotionStyle(
    val risesLetters: Boolean,
    val glowEnabled: Boolean,
    val cascadesRows: Boolean
) {
    KINETIC_GLOW(risesLetters = true, glowEnabled = true, cascadesRows = true),
    WAVE_GLOW(risesLetters = false, glowEnabled = true, cascadesRows = false),
    KINETIC_CLEAN(risesLetters = true, glowEnabled = false, cascadesRows = true),
    PURE_SWEEP(risesLetters = false, glowEnabled = false, cascadesRows = false)
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
