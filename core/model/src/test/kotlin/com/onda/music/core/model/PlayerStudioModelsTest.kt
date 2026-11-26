package com.onda.music.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStudioModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun withSwappedQuickActionSwapsExistingAndReplacesMissing() {
        val initial = PlayerStudioConfig()
        assertEquals(
            listOf(
                StudioQuickAction.LYRICS,
                StudioQuickAction.QUEUE,
                StudioQuickAction.SHARE,
                StudioQuickAction.SPEED
            ),
            initial.quickActions
        )

        val swapped = initial.withSwappedQuickAction(0, StudioQuickAction.SPEED)
        assertEquals(
            listOf(
                StudioQuickAction.SPEED,
                StudioQuickAction.QUEUE,
                StudioQuickAction.SHARE,
                StudioQuickAction.LYRICS
            ),
            swapped.quickActions
        )

        val replaced = swapped.withSwappedQuickAction(2, StudioQuickAction.EQUALIZER)
        assertEquals(
            listOf(
                StudioQuickAction.SPEED,
                StudioQuickAction.QUEUE,
                StudioQuickAction.EQUALIZER,
                StudioQuickAction.LYRICS
            ),
            replaced.quickActions
        )

        val sameSlot = replaced.withSwappedQuickAction(1, StudioQuickAction.QUEUE)
        assertEquals(replaced.quickActions, sameSlot.quickActions)

        val outOfBoundsLow = replaced.withSwappedQuickAction(-1, StudioQuickAction.LIKE)
        val outOfBoundsHigh = replaced.withSwappedQuickAction(4, StudioQuickAction.LIKE)
        assertEquals(replaced, outOfBoundsLow)
        assertEquals(replaced, outOfBoundsHigh)
    }

    @Test
    fun sanitizedClampsShiftAndRestoresFourUniqueQuickActions() {
        val highShift = PlayerStudioConfig(
            controlsShiftDp = 96,
            quickActions = emptyList()
        ).sanitized()
        assertEquals(24, highShift.controlsShiftDp)
        assertEquals(
            listOf(
                StudioQuickAction.LYRICS,
                StudioQuickAction.QUEUE,
                StudioQuickAction.SHARE,
                StudioQuickAction.SPEED
            ),
            highShift.quickActions
        )

        val lowShiftWithDuplicates = PlayerStudioConfig(
            controlsShiftDp = -48,
            quickActions = listOf(
                StudioQuickAction.LIKE,
                StudioQuickAction.LIKE,
                StudioQuickAction.DOWNLOAD
            )
        ).sanitized()
        assertEquals(-24, lowShiftWithDuplicates.controlsShiftDp)
        assertEquals(
            listOf(
                StudioQuickAction.LIKE,
                StudioQuickAction.DOWNLOAD,
                StudioQuickAction.LYRICS,
                StudioQuickAction.QUEUE
            ),
            lowShiftWithDuplicates.quickActions
        )

        val oversized = PlayerStudioConfig(
            quickActions = listOf(
                StudioQuickAction.LIKE,
                StudioQuickAction.DOWNLOAD,
                StudioQuickAction.EQUALIZER,
                StudioQuickAction.SLEEP_TIMER,
                StudioQuickAction.STREAM_INFO
            )
        ).sanitized()
        assertEquals(4, oversized.quickActions.size)
        assertEquals(
            listOf(
                StudioQuickAction.LIKE,
                StudioQuickAction.DOWNLOAD,
                StudioQuickAction.EQUALIZER,
                StudioQuickAction.SLEEP_TIMER
            ),
            oversized.quickActions
        )
    }

    @Test
    fun playerStudioConfigRoundTripsThroughJson() {
        val custom = PlayerStudioConfig(
            backdropMode = StudioBackdropMode.AGSL_MESH,
            artworkFrame = StudioArtworkFrame.VINYL_DISC,
            seekbarStyle = StudioSeekbarStyle.SEGMENTED_PULSE,
            bottomDeckMode = StudioBottomDeckMode.QUICK_ACTIONS,
            controlsShiftDp = -12,
            quickActions = listOf(
                StudioQuickAction.LIKE,
                StudioQuickAction.LYRICS_OFFSET,
                StudioQuickAction.STREAM_INFO,
                StudioQuickAction.SLEEP_TIMER
            ),
            ipodFinish = IpodFinish.CRIMSON,
            bedsideTint = BedsideTint.ARTWORK,
            bedsideBurnInGuard = false,
            videoSmartFill = false,
            videoAmbientGlow = true
        )
        val encoded = json.encodeToString(custom)
        val decoded = json.decodeFromString<PlayerStudioConfig>(encoded)
        assertEquals(custom, decoded)
    }

    @Test
    fun studioBackdropModeIncludesAuroraRibbonsAndSeekbarStyleIncludesLiquidSpectrum() {
        assertEquals(
            listOf(
                StudioBackdropMode.OLED_PURE,
                StudioBackdropMode.ACOUSTIC_MIST,
                StudioBackdropMode.AGSL_MESH,
                StudioBackdropMode.VINYL_DARK,
                StudioBackdropMode.AURORA_RIBBONS
            ),
            StudioBackdropMode.entries
        )
        assertEquals(5, StudioBackdropMode.entries.size)
        assertEquals("Aurora Ribbons", StudioBackdropMode.AURORA_RIBBONS.label)

        assertEquals(
            listOf(
                StudioSeekbarStyle.HARMONIC_WAVE,
                StudioSeekbarStyle.PRECISION_BAR,
                StudioSeekbarStyle.SEGMENTED_PULSE,
                StudioSeekbarStyle.LIQUID_SPECTRUM
            ),
            StudioSeekbarStyle.entries
        )
        assertEquals(4, StudioSeekbarStyle.entries.size)
        assertEquals("Liquid Spectrum", StudioSeekbarStyle.LIQUID_SPECTRUM.label)
    }

    @Test
    fun auroraRibbonsAndLiquidSpectrumSurviveSanitizeAndJsonRoundTrip() {
        val config = PlayerStudioConfig(
            backdropMode = StudioBackdropMode.AURORA_RIBBONS,
            seekbarStyle = StudioSeekbarStyle.LIQUID_SPECTRUM,
            controlsShiftDp = 12,
            quickActions = listOf(
                StudioQuickAction.LIKE,
                StudioQuickAction.LYRICS,
                StudioQuickAction.QUEUE,
                StudioQuickAction.SLEEP_TIMER
            )
        )
        val sanitized = config.sanitized()
        assertEquals(config, sanitized)
        assertEquals(StudioBackdropMode.AURORA_RIBBONS, sanitized.backdropMode)
        assertEquals(StudioSeekbarStyle.LIQUID_SPECTRUM, sanitized.seekbarStyle)

        val encoded = json.encodeToString(config)
        assertTrue(encoded.contains("AURORA_RIBBONS"))
        assertTrue(encoded.contains("LIQUID_SPECTRUM"))
        val decoded = json.decodeFromString<PlayerStudioConfig>(encoded)
        assertEquals(config, decoded)
    }
}
