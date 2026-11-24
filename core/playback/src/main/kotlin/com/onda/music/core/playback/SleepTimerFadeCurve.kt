package com.onda.music.core.playback

object SleepTimerFadeCurve {
    const val FADE_DURATION_MS: Long = 15_000L
    const val SHORT_TRACK_END_FADE_MS: Long = 3_000L

    fun computeFadeMultiplier(
        remainingMs: Long?,
        endOfTrack: Boolean,
        trackPositionMs: Long,
        trackDurationMs: Long
    ): Float {
        if (remainingMs != null) {
            if (remainingMs <= 0L) return 0f
            if (remainingMs >= FADE_DURATION_MS) return 1f
            val t = (remainingMs.toFloat() / FADE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            return t * t
        }
        if (endOfTrack && trackDurationMs > 0L) {
            val fadeWindow = if (trackDurationMs > FADE_DURATION_MS) {
                FADE_DURATION_MS
            } else {
                minOf(SHORT_TRACK_END_FADE_MS, trackDurationMs / 2L)
            }
            if (fadeWindow <= 0L) return 1f
            val trackRemaining = (trackDurationMs - trackPositionMs).coerceAtLeast(0L)
            if (trackRemaining >= fadeWindow) return 1f
            val t = (trackRemaining.toFloat() / fadeWindow.toFloat()).coerceIn(0f, 1f)
            return t * t
        }
        return 1f
    }

    fun isFading(
        remainingMs: Long?,
        endOfTrack: Boolean,
        trackPositionMs: Long,
        trackDurationMs: Long
    ): Boolean {
        return computeFadeMultiplier(
            remainingMs = remainingMs,
            endOfTrack = endOfTrack,
            trackPositionMs = trackPositionMs,
            trackDurationMs = trackDurationMs
        ) < 0.995f
    }
}
