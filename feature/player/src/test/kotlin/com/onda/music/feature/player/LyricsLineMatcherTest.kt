package com.onda.music.feature.player

import com.onda.music.core.model.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLineMatcherTest {

    @Test
    fun findActiveLyricsLineIndex_returnsLatestReachedLine() {
        val lines = listOf(
            LyricsLine(startTimeMs = 1_000L, endTimeMs = 4_000L, text = "First"),
            LyricsLine(startTimeMs = 5_000L, endTimeMs = 9_000L, text = "Second"),
            LyricsLine(startTimeMs = 10_000L, endTimeMs = 15_000L, text = "Third")
        )

        assertEquals(-1, findActiveLyricsLineIndex(lines, 500L, isSynced = true))
        assertEquals(0, findActiveLyricsLineIndex(lines, 1_000L, isSynced = true))
        assertEquals(0, findActiveLyricsLineIndex(lines, 4_500L, isSynced = true))
        assertEquals(1, findActiveLyricsLineIndex(lines, 7_200L, isSynced = true))
        assertEquals(2, findActiveLyricsLineIndex(lines, 20_000L, isSynced = true))
    }

    @Test
    fun findActiveLyricsLineIndex_returnsMinusOneWhenUnsynced() {
        val lines = listOf(
            LyricsLine(startTimeMs = 0L, endTimeMs = 1_000L, text = "Plain")
        )
        assertEquals(-1, findActiveLyricsLineIndex(lines, 500L, isSynced = false))
    }

    @Test
    fun formatDurationMs_formatsMinutesAndHoursCleanly() {
        assertEquals("0:00", formatDurationMs(0L))
        assertEquals("3:05", formatDurationMs(185_000L))
        assertEquals("1:02:03", formatDurationMs(3_723_000L))
    }
}
