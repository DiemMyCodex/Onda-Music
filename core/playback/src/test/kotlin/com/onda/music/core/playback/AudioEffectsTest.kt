package com.onda.music.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.onda.music.core.model.AudioOutputEqProfile
import com.onda.music.core.model.AudioOutputKind
import com.onda.music.core.model.EqConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
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

    @OptIn(UnstableApi::class)
    @Test
    fun eqAudioProcessorProcesses16Bit24BitAndFloatPcmWithoutBypass() {
        val boostConfig = EqConfig(
            enabled = true,
            activePresetId = "custom",
            bands = EqConfig.defaultBands(listOf(0f, 0f, 0f, 0f, 0f, 6.0f, 0f, 0f, 0f, 0f)),
            preampDb = 0f
        )

        val encodings = listOf(
            C.ENCODING_PCM_16BIT to 2,
            C.ENCODING_PCM_24BIT to 3,
            C.ENCODING_PCM_FLOAT to 4
        )
        val frames = 128
        val channels = 2

        encodings.forEach { (encoding, bytesPerSample) ->
            val processor = EqAudioProcessor()
            processor.updateConfig(boostConfig)
            val configured = processor.configure(AudioProcessor.AudioFormat(48_000, channels, encoding))
            assertEquals(encoding, configured.encoding)
            processor.flush()

            val input = ByteBuffer.allocateDirect(frames * channels * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
            for (frame in 0 until frames) {
                val s = (sin(2.0 * PI * 1000.0 * frame / 48_000.0) * 0.25).toFloat()
                repeat(channels) {
                    when (encoding) {
                        C.ENCODING_PCM_16BIT -> input.putShort((s * 32767f).toInt().toShort())
                        C.ENCODING_PCM_24BIT -> {
                            val v = (s * 8_388_607f).toInt()
                            input.put((v and 0xFF).toByte())
                            input.put(((v shr 8) and 0xFF).toByte())
                            input.put(((v shr 16) and 0xFF).toByte())
                        }
                        C.ENCODING_PCM_FLOAT -> input.putFloat(s)
                    }
                }
            }
            input.flip()
            val inputCopy = ByteArray(input.remaining())
            input.duplicate().get(inputCopy)

            processor.queueInput(input)
            val output = processor.output
            assertEquals(inputCopy.size, output.remaining())
            val outputCopy = ByteArray(output.remaining())
            output.get(outputCopy)
            assertFalse("Expected EQ to modify PCM for encoding $encoding", inputCopy.contentEquals(outputCopy))

            if (encoding == C.ENCODING_PCM_FLOAT) {
                val outFloats = ByteBuffer.wrap(outputCopy).order(ByteOrder.LITTLE_ENDIAN)
                var maxAbs = 0f
                while (outFloats.remaining() >= 4) {
                    val sample = outFloats.float
                    assertTrue(sample.isFinite())
                    maxAbs = maxOf(maxAbs, abs(sample))
                }
                assertTrue(maxAbs > 0.25f && maxAbs <= 1.0f)
            }
        }
    }

    @Test
    fun bundledAutoEqGzipIndexLoadsOverThousandProfilesAndMatchesHeadphoneQueries() {
        val rawFile = File("src/main/res/raw/autoeq_index.gz").takeIf { it.exists() }
            ?: File("core/playback/src/main/res/raw/autoeq_index.gz")
        assertTrue("Expected bundled AutoEq index at ${rawFile.absolutePath}", rawFile.exists())

        val profiles = rawFile.inputStream().use { AutoEqRepository.loadFromStream(it) }
        assertTrue("Expected >= 1000 AutoEq profiles, got ${profiles.size}", profiles.size >= 1000)

        val sonyMatches = AutoEqRepository.searchProfiles(profiles, "sony xm5", limit = 10)
        assertTrue("Expected Sony XM5 match", sonyMatches.any { it.displayName.contains("XM5", ignoreCase = true) })

        val sennMatches = AutoEqRepository.searchProfiles(profiles, "hd 600", limit = 10)
        assertTrue("Expected HD 600 match", sennMatches.any { it.displayName.contains("600", ignoreCase = true) })

        val moondropMatches = AutoEqRepository.searchProfiles(profiles, "moondrop", limit = 10)
        assertTrue("Expected Moondrop match", moondropMatches.isNotEmpty())
        assertEquals(10, moondropMatches.first().gainsDb.size)
    }

    private fun assertEqualsFloat(expected: Float, actual: Float) {
        assertTrue("Expected $expected but got $actual", kotlin.math.abs(expected - actual) < 0.01f)
    }
}
