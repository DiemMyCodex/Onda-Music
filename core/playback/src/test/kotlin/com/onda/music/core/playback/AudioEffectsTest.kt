package com.onda.music.core.playback

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEffectsTest {

    @Test
    fun peakingBiquadReturnsNullForNearZeroGainAndValidCoefficientsForBoost() {
        val flat = BiquadCoefficients.peaking(
            frequencyHz = 1000.0,
            sampleRate = 48000,
            gainDb = 0.0,
            q = 1.41
        )
        assertNull(flat)

        val boosted = BiquadCoefficients.peaking(
            frequencyHz = 1000.0,
            sampleRate = 48000,
            gainDb = 6.0,
            q = 1.41
        )
        assertNotNull(boosted)
        val c = boosted!!
        assertTrue(c.b0.isFinite() && c.b0 > 1.0)
        assertTrue(c.b1.isFinite())
        assertTrue(c.b2.isFinite())
        assertTrue(c.a1.isFinite())
        assertTrue(c.a2.isFinite())
    }

    @Test
    fun crossfadeControllerRampsVolumeAtTrackEdges() {
        val controller = CrossfadeVolumeController()
        controller.configure(crossfadeSeconds = 4, gaplessEnabled = true)
        controller.onTrackStarted(nowMs = 1_000L)

        val startVol = controller.computeEffectiveVolume(
            baseVolume = 1.0f,
            positionMs = 1_000L,
            durationMs = 30_000L,
            nowMs = 2_000L
        )
        assertTrue(startVol in 0.2f..0.3f)

        val midVol = controller.computeEffectiveVolume(
            baseVolume = 1.0f,
            positionMs = 15_000L,
            durationMs = 30_000L,
            nowMs = 15_000L
        )
        assertEqualsFloat(1.0f, midVol)

        val endVol = controller.computeEffectiveVolume(
            baseVolume = 1.0f,
            positionMs = 29_000L,
            durationMs = 30_000L,
            nowMs = 29_000L
        )
        assertTrue(endVol in 0.2f..0.3f)
    }

    private fun assertEqualsFloat(expected: Float, actual: Float) {
        assertTrue("Expected $expected but got $actual", kotlin.math.abs(expected - actual) < 0.01f)
    }
}
