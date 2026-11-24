package com.onda.music.feature.player

import androidx.compose.ui.geometry.Offset
import com.onda.music.core.model.LyricsLine
import com.onda.music.feature.player.styles.closestPosterArcHit
import com.onda.music.feature.player.styles.evaluatePosterArcPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun rubberBandOffset_isBoundedByReachAndPreservesSign() {
        val reach = 26f
        val positive = rubberBandOffset(500f, reach)
        val negative = rubberBandOffset(-500f, reach)
        assertTrue(positive > 0f && positive < reach)
        assertTrue(negative < 0f && negative > -reach)
        assertEquals(0f, rubberBandOffset(0f, reach), 0.0001f)
    }

    @Test
    fun seekFillFollower_convergesCriticallyDampedWithoutOvershoot() {
        val follower = SeekFillFollower().apply { widthPx = 400f }
        repeat(20) {
            follower.step(0.6f, 1f / 60f)
        }
        assertTrue(follower.fraction <= 0.6005f)
        assertTrue(follower.fraction > 0.58f)
    }

    @Test
    fun closestPosterArcHit_matchesBezierEndpointsAndMidpoint() {
        val w = 1080f
        val h = 1000f
        val midPoint = evaluatePosterArcPoint(0.5f, w, h)
        val hit = closestPosterArcHit(midPoint, w, h)
        assertEquals(0.5f, hit.fraction, 0.03f)
        assertTrue(hit.distancePx < 2f)
    }

    @Test
    fun interpolateLyricPositionMs_advancesSmoothlyAndSuppressesMinorJitter() {
        val advanced = interpolateLyricPositionMs(
            anchorPositionMs = 10_000L,
            anchorFrameNanos = 1_000_000_000L,
            nowFrameNanos = 1_120_000_000L,
            isPlaying = true,
            playbackSpeed = 1.0f,
            currentInterpolatedMs = 10_050L
        )
        assertEquals(10_120L, advanced)

        val antiJitter = interpolateLyricPositionMs(
            anchorPositionMs = 10_000L,
            anchorFrameNanos = 1_000_000_000L,
            nowFrameNanos = 1_040_000_000L,
            isPlaying = true,
            playbackSpeed = 1.0f,
            currentInterpolatedMs = 10_120L
        )
        assertEquals(10_120L, antiJitter)

        val seekSnap = interpolateLyricPositionMs(
            anchorPositionMs = 45_000L,
            anchorFrameNanos = 2_000_000_000L,
            nowFrameNanos = 2_016_000_000L,
            isPlaying = true,
            playbackSpeed = 1.0f,
            currentInterpolatedMs = 10_120L
        )
        assertEquals(45_016L, seekSnap)

        val paused = interpolateLyricPositionMs(
            anchorPositionMs = 12_345L,
            anchorFrameNanos = 1_000_000_000L,
            nowFrameNanos = 1_500_000_000L,
            isPlaying = false,
            playbackSpeed = 1.0f,
            currentInterpolatedMs = 12_345L
        )
        assertEquals(12_345L, paused)
    }

    @Test
    fun smoothWordProgressAndEnvelope_areMonotonicAndZeroAtEdges() {
        assertEquals(0f, computeSmoothWordProgress(900L, 1_000L, 2_000L), 0.0001f)
        assertEquals(1f, computeSmoothWordProgress(2_000L, 1_000L, 2_000L), 0.0001f)
        val pEarly = computeSmoothWordProgress(1_250L, 1_000L, 2_000L)
        val pMid = computeSmoothWordProgress(1_500L, 1_000L, 2_000L)
        val pLate = computeSmoothWordProgress(1_750L, 1_000L, 2_000L)
        assertTrue(pEarly > 0f && pEarly < pMid && pMid < pLate && pLate < 1f)
        assertEquals(0f, computeWordActiveEnvelope(0f), 0.0001f)
        assertEquals(0f, computeWordActiveEnvelope(1f), 0.0001f)
        assertTrue(computeWordActiveEnvelope(0.5f) > 0.99f)
    }
}
