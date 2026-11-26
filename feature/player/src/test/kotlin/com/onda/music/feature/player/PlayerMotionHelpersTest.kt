package com.onda.music.feature.player

import com.onda.music.core.model.AudioOutputKind
import com.onda.music.core.model.AudioSignalInfo
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.StreamOption
import com.onda.music.feature.player.sheets.resolveActionSheetSignalBadge
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMotionHelpersTest {

    @Test
    fun formatSleepTimerBadgeLabel_formatsEotDurationAndNullStates() {
        assertEquals("EOT", formatSleepTimerBadgeLabel(remainingMs = 12_000L, endOfTrack = true))
        assertEquals("EOT", formatSleepTimerBadgeLabel(remainingMs = null, endOfTrack = true))
        assertEquals("14:52", formatSleepTimerBadgeLabel(remainingMs = 892_000L, endOfTrack = false))
        assertEquals("0:45", formatSleepTimerBadgeLabel(remainingMs = 45_000L, endOfTrack = false))
        assertEquals("0:01", formatSleepTimerBadgeLabel(remainingMs = 500L, endOfTrack = false))
        assertEquals("0:01", formatSleepTimerBadgeLabel(remainingMs = 1L, endOfTrack = false))
        assertEquals("0:01", formatSleepTimerBadgeLabel(remainingMs = 999L, endOfTrack = false))
        assertNull(formatSleepTimerBadgeLabel(remainingMs = 0L, endOfTrack = false))
        assertNull(formatSleepTimerBadgeLabel(remainingMs = -100L, endOfTrack = false))
        assertNull(formatSleepTimerBadgeLabel(remainingMs = null, endOfTrack = false))
    }

    @Test
    fun auroraHelpers_wrapPhaseAndProduceFiniteBoundedRibbonCoordinates() {
        val twoPi = 2f * PI.toFloat()
        val wrapped = auroraWavePhaseAdvance(twoPi - 0.01f, dtSeconds = 0.05f, speed = 1f)
        assertTrue(wrapped in 0f..twoPi)

        val clamped = auroraWavePhaseAdvance(0f, dtSeconds = 10f, speed = 1f)
        assertEquals(0.05f, clamped, 0.0001f)

        assertTrue(auroraWavePhaseAdvance(Float.NaN, 0.016f, 1f).isFinite())
        assertTrue(auroraWavePhaseAdvance(Float.POSITIVE_INFINITY, 0.016f, 1f).isFinite())
        assertTrue(auroraWavePhaseAdvance(1f, Float.NaN, Float.POSITIVE_INFINITY).isFinite())

        val height = 1200f
        for (ribbon in 0..2) {
            val yStart = computeAuroraRibbonY(0f, ribbon, wrapped, height)
            val yMid = computeAuroraRibbonY(0.5f, ribbon, wrapped, height)
            val yEnd = computeAuroraRibbonY(1f, ribbon, wrapped, height)
            assertTrue(yStart.isFinite() && yStart in 0f..height)
            assertTrue(yMid.isFinite() && yMid in 0f..height)
            assertTrue(yEnd.isFinite() && yEnd in 0f..height)
        }
        assertTrue(computeAuroraRibbonY(Float.NaN, 0, Float.POSITIVE_INFINITY, Float.NaN).isFinite())
    }

    @Test
    fun resolveActionSheetSignalBadge_prefersAudioSignalAndFallsBackCleanly() {
        val signal = AudioSignalInfo(
            codec = "opus",
            sampleRateHz = 48_000,
            bitrateKbps = 153,
            outputKind = AudioOutputKind.SPEAKER,
            isLossless = false
        )
        val stream = StreamOption(
            url = "https://example.com/audio.webm",
            mimeType = "audio/webm",
            bitrateKbps = 128,
            codec = "aac",
            qualityLabel = "High"
        )

        assertEquals(
            signal.formatBadgeText(),
            resolveActionSheetSignalBadge(signal, stream, "YOUTUBE")
        )
        assertEquals(
            "AAC 128 kbps",
            resolveActionSheetSignalBadge(null, stream, "YOUTUBE")
        )
        assertEquals(
            "LOCAL",
            resolveActionSheetSignalBadge(null, null, "LOCAL")
        )
        assertEquals(
            "IDLE",
            resolveActionSheetSignalBadge(null, null, null)
        )
    }

    @Test
    fun computeMiniCardTransform_applies3dSwipeAndRespectsReducedMotion() {
        val currentNormal = computeMiniCardTransform(
            offsetPx = -150f,
            widthPx = 300f,
            role = 0,
            reducedMotion = false
        )
        assertEquals(-150f, currentNormal.translationX, 0.01f)
        assertEquals(9f, currentNormal.rotationY, 0.01f)
        assertEquals(0.96f, currentNormal.scale, 0.001f)
        assertTrue(currentNormal.alpha in 0f..1f)

        val currentReduced = computeMiniCardTransform(
            offsetPx = -150f,
            widthPx = 300f,
            role = 0,
            reducedMotion = true
        )
        assertEquals(0f, currentReduced.rotationY, 0.0001f)
        assertEquals(1f, currentReduced.scale, 0.0001f)

        val hiddenPrev = computeMiniCardTransform(
            offsetPx = -20f,
            widthPx = 300f,
            role = -1,
            reducedMotion = false
        )
        assertEquals(0f, hiddenPrev.alpha, 0.0001f)

        val incomingNext = computeMiniCardTransform(
            offsetPx = -150f,
            widthPx = 300f,
            role = 1,
            reducedMotion = false
        )
        assertEquals(150f, incomingNext.translationX, 0.01f)
        assertEquals(-9f, incomingNext.rotationY, 0.01f)
        assertEquals(0.96f, incomingNext.scale, 0.001f)

        val zeroWidthSafe = computeMiniCardTransform(
            offsetPx = 10f,
            widthPx = 0f,
            role = 0,
            reducedMotion = false
        )
        assertTrue(zeroWidthSafe.rotationY.isFinite() && zeroWidthSafe.scale.isFinite())

        val nanSafe = computeMiniCardTransform(
            offsetPx = Float.NaN,
            widthPx = Float.POSITIVE_INFINITY,
            role = 0,
            reducedMotion = false
        )
        assertTrue(
            nanSafe.translationX.isFinite() &&
                nanSafe.alpha.isFinite() &&
                nanSafe.rotationY.isFinite() &&
                nanSafe.scale.isFinite()
        )
    }

    @Test
    fun carouselAndScrubCapsuleHelpers_clampBoundsAndVelocityTilt() {
        assertEquals(0, clampCarouselIndex(5, 0))
        assertEquals(0, clampCarouselIndex(-2, 4))
        assertEquals(3, clampCarouselIndex(9, 4))
        assertEquals(0, clampCarouselIndex(0, 4))

        assertEquals(0f, clampScrubCapsuleOffsetX(10f, 120f, 400f), 0.01f)
        assertEquals(280f, clampScrubCapsuleOffsetX(395f, 120f, 400f), 0.01f)
        assertEquals(140f, clampScrubCapsuleOffsetX(200f, 120f, 400f), 0.01f)
        assertEquals(0f, clampScrubCapsuleOffsetX(50f, 500f, 400f), 0.01f)
        assertTrue(clampScrubCapsuleOffsetX(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).isFinite())

        assertEquals(0f, computeScrubTiltDegrees(80f, 16L, reducedMotion = true), 0.0001f)
        val fastRight = computeScrubTiltDegrees(120f, 16L, reducedMotion = false)
        val fastLeft = computeScrubTiltDegrees(-120f, 16L, reducedMotion = false)
        assertEquals(7.5f, fastRight, 0.0001f)
        assertEquals(-7.5f, fastLeft, 0.0001f)
        assertTrue(computeScrubTiltDegrees(1f, 0L, reducedMotion = false).isFinite())
        assertEquals(0f, computeScrubTiltDegrees(Float.NaN, 16L, reducedMotion = false), 0.0001f)
        assertEquals(0f, computeScrubTiltDegrees(Float.POSITIVE_INFINITY, 16L, reducedMotion = false), 0.0001f)
    }

    @Test
    fun resolveScrubPreviewLyric_appliesOffsetAndSkipsInterludesOrUnsynced() {
        val syncedDoc = LyricsDocument(
            trackId = "t1",
            isSynced = true,
            lines = listOf(
                LyricsLine(1_000L, 4_000L, "Opening line"),
                LyricsLine(4_000L, 6_000L, "", isInterlude = true),
                LyricsLine(6_000L, 10_000L, "Chorus starts here")
            )
        )

        assertNull(resolveScrubPreviewLyric(null, 2_000L, 0L))
        assertNull(resolveScrubPreviewLyric(syncedDoc.copy(isSynced = false), 2_000L, 0L))
        assertNull(resolveScrubPreviewLyric(syncedDoc, 500L, 0L))
        assertEquals("Opening line", resolveScrubPreviewLyric(syncedDoc, 2_000L, 0L))
        assertNull(resolveScrubPreviewLyric(syncedDoc, 5_000L, 0L))
        assertEquals("Chorus starts here", resolveScrubPreviewLyric(syncedDoc, 5_000L, 1_500L))
    }

    @Test
    fun computeLiquidSpectrumBarHeightFraction_staysBoundedAndCollapsesToRail() {
        for (i in 0 until 36) {
            val active = computeLiquidSpectrumBarHeightFraction(
                barIndex = i,
                barCount = 36,
                phase = 1.2f,
                activeMotion = true,
                collapse = 0f
            )
            assertTrue(active in 0.08f..1f)

            val collapsed = computeLiquidSpectrumBarHeightFraction(
                barIndex = i,
                barCount = 36,
                phase = 1.2f,
                activeMotion = true,
                collapse = 1f
            )
            assertEquals(0.14f, collapsed, 0.001f)
        }
        val singleSafe = computeLiquidSpectrumBarHeightFraction(0, 1, 0f, false, 0f)
        assertTrue(singleSafe.isFinite() && singleSafe in 0.08f..1f)

        val nonFiniteSafe = computeLiquidSpectrumBarHeightFraction(
            barIndex = 0,
            barCount = 36,
            phase = Float.NaN,
            activeMotion = true,
            collapse = Float.POSITIVE_INFINITY
        )
        assertTrue(nonFiniteSafe.isFinite() && nonFiniteSafe in 0.08f..1f)
    }

    @Test
    fun nowPlayingSwipeHelpers_dampElasticOffsetAndResolveDirectionCleanly() {
        assertEquals(0f, computeNowPlayingSwipeOffsetPx(0f, 48f), 0.001f)
        val rightShift = computeNowPlayingSwipeOffsetPx(120f, 48f)
        val leftShift = computeNowPlayingSwipeOffsetPx(-120f, 48f)
        assertTrue(rightShift in 0f..48f)
        assertTrue(leftShift in -48f..0f)
        assertEquals(-rightShift, leftShift, 0.001f)
        assertTrue(computeNowPlayingSwipeOffsetPx(10_000f, 48f) <= 48f)
        assertEquals(0f, computeNowPlayingSwipeOffsetPx(Float.NaN, 48f), 0.001f)
        assertEquals(0f, computeNowPlayingSwipeOffsetPx(100f, Float.POSITIVE_INFINITY), 0.001f)

        assertEquals(
            PlayerSwipeDirection.NEXT,
            resolveNowPlayingSwipeAction(totalDragX = -90f, velocityX = 0f, distanceThresholdPx = 72f, velocityThresholdPx = 460f)
        )
        assertEquals(
            PlayerSwipeDirection.PREVIOUS,
            resolveNowPlayingSwipeAction(totalDragX = 85f, velocityX = 0f, distanceThresholdPx = 72f, velocityThresholdPx = 460f)
        )
        assertEquals(
            PlayerSwipeDirection.NEXT,
            resolveNowPlayingSwipeAction(totalDragX = -30f, velocityX = -600f, distanceThresholdPx = 72f, velocityThresholdPx = 460f)
        )
        assertEquals(
            PlayerSwipeDirection.PREVIOUS,
            resolveNowPlayingSwipeAction(totalDragX = 30f, velocityX = 600f, distanceThresholdPx = 72f, velocityThresholdPx = 460f)
        )
        assertEquals(
            PlayerSwipeDirection.NONE,
            resolveNowPlayingSwipeAction(totalDragX = -30f, velocityX = 0f, distanceThresholdPx = 72f, velocityThresholdPx = 460f)
        )
        assertEquals(
            PlayerSwipeDirection.NONE,
            resolveNowPlayingSwipeAction(totalDragX = -90f, velocityX = 600f, distanceThresholdPx = 72f, velocityThresholdPx = 460f)
        )
        assertEquals(
            PlayerSwipeDirection.NONE,
            resolveNowPlayingSwipeAction(totalDragX = Float.NaN, velocityX = 0f, distanceThresholdPx = 72f, velocityThresholdPx = 460f)
        )
    }
}
