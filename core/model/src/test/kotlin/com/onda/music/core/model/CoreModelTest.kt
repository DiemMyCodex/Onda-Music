package com.onda.music.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun playerStyleContainsAllTenBuiltInModes() {
        val expected = listOf(
            PlayerStyle.FLAGSHIP,
            PlayerStyle.CLASSIC,
            PlayerStyle.POSTER,
            PlayerStyle.TURNTABLE,
            PlayerStyle.VIDEO,
            PlayerStyle.IPOD,
            PlayerStyle.TIMELINE,
            PlayerStyle.LYRICS,
            PlayerStyle.BEDSIDE,
            PlayerStyle.BEDSIDE_TILES
        )
        assertEquals(10, PlayerStyle.entries.size)
        assertEquals(expected, PlayerStyle.entries)
    }

    @Test
    fun eqConfigClampsBandGainAndExposesTenStandardFrequencies() {
        val bands = EqConfig.defaultBands(listOf(24f, -20f, 3.5f))
        assertEquals(10, bands.size)
        assertEquals(12f, bands[0].gainDb)
        assertEquals(-12f, bands[1].gainDb)
        assertEquals(3.5f, bands[2].gainDb)
        assertEquals(0f, bands[9].gainDb)
        assertEquals(EqConfig.standardFrequenciesHz, bands.map { it.frequencyHz })
    }

    @Test
    fun trackAndTransferSummaryRoundTripThroughJson() {
        val track = Track(
            id = "yt_123",
            title = "Midnight Wave",
            artist = "Onda",
            durationMs = 210_000L,
            source = SourceKind.MONOCHROME,
            monochromeId = "mono_99",
            liked = true,
            inLibrary = true
        )
        val encoded = json.encodeToString(track)
        val decoded = json.decodeFromString<Track>(encoded)
        assertEquals(track, decoded)

        val summary = TransferSummary(
            format = TransferFormat.MOMI_TRANSFER_V1,
            importedTracks = 334,
            importedPlayerStyle = PlayerStyle.POSTER
        )
        assertTrue(summary.importedTracks == 334)
        assertEquals(PlayerStyle.POSTER, summary.importedPlayerStyle)
    }

    @Test
    fun lyricsMotionStyleExposesAllFourModesAndBehaviorMatrix() {
        assertEquals(
            listOf(
                LyricsMotionStyle.KINETIC_GLOW,
                LyricsMotionStyle.WAVE_GLOW,
                LyricsMotionStyle.KINETIC_CLEAN,
                LyricsMotionStyle.PURE_SWEEP
            ),
            LyricsMotionStyle.entries
        )
        assertTrue(LyricsMotionStyle.KINETIC_GLOW.risesLetters && LyricsMotionStyle.KINETIC_GLOW.glowEnabled && LyricsMotionStyle.KINETIC_GLOW.cascadesRows)
        assertTrue(!LyricsMotionStyle.WAVE_GLOW.risesLetters && LyricsMotionStyle.WAVE_GLOW.glowEnabled && !LyricsMotionStyle.WAVE_GLOW.cascadesRows)
        assertTrue(LyricsMotionStyle.KINETIC_CLEAN.risesLetters && !LyricsMotionStyle.KINETIC_CLEAN.glowEnabled && LyricsMotionStyle.KINETIC_CLEAN.cascadesRows)
        assertTrue(!LyricsMotionStyle.PURE_SWEEP.risesLetters && !LyricsMotionStyle.PURE_SWEEP.glowEnabled && !LyricsMotionStyle.PURE_SWEEP.cascadesRows)
    }

    @Test
    fun autoEqProfileMapsToTenBandEqConfigAndAudioSignalFormatsBadge() {
        val profile = AutoEqProfile(
            id = "sony_wh_1000xm5",
            brand = "Sony",
            model = "WH-1000XM5",
            formFactor = "O",
            preampDb = -6.2f,
            gainsDb = listOf(-4.5f, -3.2f, -1.8f, 0.5f, 1.2f, 2.0f, 3.1f, 2.4f, 1.0f, 0.0f),
            qValues = listOf(1.2f, 1.4f, 1.4f, 1.4f, 1.4f, 1.6f, 1.8f, 1.4f, 1.4f, 1.2f)
        )
        assertEquals("Sony WH-1000XM5", profile.displayName)
        val base = EqConfig(crossfadeSeconds = 5, gaplessEnabled = true)
        val applied = profile.toEqConfig(base)
        assertTrue(applied.enabled)
        assertEquals("autoeq:sony_wh_1000xm5", applied.activePresetId)
        assertEquals(-6.2f, applied.preampDb, 0.001f)
        assertEquals(10, applied.bands.size)
        assertEquals(-4.5f, applied.bands[0].gainDb, 0.001f)
        assertEquals(1.8f, applied.bands[6].q, 0.001f)
        assertEquals(5, applied.crossfadeSeconds)

        val flacSignal = AudioSignalInfo(
            codec = "FLAC",
            sampleRateHz = 96_000,
            bitDepth = 24,
            bitrateKbps = 2800,
            channelCount = 2,
            outputDeviceName = "FiiO KA13",
            outputKind = AudioOutputKind.USB_AUDIO,
            isLossless = true
        )
        assertEquals("FLAC · 24-bit/96kHz · USB DAC", flacSignal.formatBadgeText())

        val opusSignal = AudioSignalInfo(
            codec = "OPUS",
            sampleRateHz = 48_000,
            bitDepth = 0,
            bitrateKbps = 128,
            channelCount = 2,
            outputDeviceName = "Sony XM5",
            outputKind = AudioOutputKind.BLUETOOTH,
            isLossless = false
        )
        assertEquals("OPUS · 48kHz · 128k · BT", opusSignal.formatBadgeText())
    }
}
