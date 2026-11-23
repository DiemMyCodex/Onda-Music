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
}
