package com.onda.music.core.playback

import com.onda.music.core.model.AudioOutputEqProfile
import com.onda.music.core.model.AudioOutputKind
import com.onda.music.core.model.EqConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun sleepTimerFadeCurveAppliesSmooth15SecondAttenuation() {
        assertEqualsFloat(1.0f, SleepTimerFadeCurve.computeFadeMultiplier(null, false, 0L, 0L))
        assertEqualsFloat(1.0f, SleepTimerFadeCurve.computeFadeMultiplier(20_000L, false, 0L, 0L))
        assertEqualsFloat(1.0f, SleepTimerFadeCurve.computeFadeMultiplier(15_000L, false, 0L, 0L))
        assertEqualsFloat(0.25f, SleepTimerFadeCurve.computeFadeMultiplier(7_500L, false, 0L, 0L))
        assertEqualsFloat(0.0f, SleepTimerFadeCurve.computeFadeMultiplier(0L, false, 0L, 0L))

        assertTrue(SleepTimerFadeCurve.isFading(7_500L, false, 0L, 0L))
        assertFalse(SleepTimerFadeCurve.isFading(20_000L, false, 0L, 0L))

        assertEqualsFloat(
            0.25f,
            SleepTimerFadeCurve.computeFadeMultiplier(
                remainingMs = null,
                endOfTrack = true,
                trackPositionMs = 192_500L,
                trackDurationMs = 200_000L
            )
        )
    }

    @Test
    fun audioOutputResolverPrioritizesExternalRoutesAndPreservesCrossfadeGapless() {
        val speakerOnly = AudioOutputResolver.resolveDevice(
            listOf(RawAudioOutput(AudioOutputKind.SPEAKER, "Speaker"))
        )
        assertEquals("speaker", speakerOnly.key)
        assertEquals(AudioOutputKind.SPEAKER, speakerOnly.kind)

        val resolvedBt = AudioOutputResolver.resolveDevice(
            listOf(
                RawAudioOutput(AudioOutputKind.SPEAKER, "Speaker"),
                RawAudioOutput(AudioOutputKind.WIRED_HEADSET, "Headset"),
                RawAudioOutput(AudioOutputKind.BLUETOOTH, "Sony XM5", "AA:BB:CC:DD:EE:FF")
            )
        )
        assertEquals("bt:aa:bb:cc:dd:ee:ff", resolvedBt.key)
        assertEquals("Sony XM5", resolvedBt.displayName)
        assertEquals(AudioOutputKind.BLUETOOTH, resolvedBt.kind)

        val zeroMacBt = AudioOutputResolver.resolveDevice(
            listOf(RawAudioOutput(AudioOutputKind.BLUETOOTH, "AirPods Pro", "00:00:00:00:00:00"))
        )
        assertEquals("bt:airpods pro", zeroMacBt.key)

        val savedEq = EqConfig(
            enabled = true,
            activePresetId = "rock",
            bands = EqConfig.defaultBands(listOf(4f, 3f, 1.5f)),
            preampDb = -2.0f,
            normalizeLoudness = true,
            crossfadeSeconds = 0,
            gaplessEnabled = false
        )
        val profile = AudioOutputEqProfile.fromEqConfig(resolvedBt, savedEq, updatedAtMs = 100L)
        val baseWithCrossfade = EqConfig(crossfadeSeconds = 6, gaplessEnabled = true)
        val applied = profile.applyTo(baseWithCrossfade)

        assertTrue(applied.enabled)
        assertEquals("rock", applied.activePresetId)
        assertEqualsFloat(-2.0f, applied.preampDb)
        assertTrue(applied.normalizeLoudness)
        assertEquals(6, applied.crossfadeSeconds)
        assertTrue(applied.gaplessEnabled)
    }

    private fun assertEqualsFloat(expected: Float, actual: Float) {
        assertTrue("Expected $expected but got $actual", kotlin.math.abs(expected - actual) < 0.01f)
    }
}
