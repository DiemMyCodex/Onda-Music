package com.onda.music.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateTest {

    @Test
    fun `empty query returns all settings entries across 5 groups`() {
        val strings = settingsStringsFor("en")
        val entries = buildSettingsSearchEntries(strings)
        val filtered = filterSettingsEntries(entries, "   ")

        assertEquals(entries.size, filtered.size)
        assertEquals(5, filtered.map { it.group }.toSet().size)
    }

    @Test
    fun `search matches new playback and output entries across groups`() {
        val strings = settingsStringsFor("en")
        val entries = buildSettingsSearchEntries(strings)

        assertEquals(SettingsEntryId.SMART_OUTPUT_PROFILES, filterSettingsEntries(entries, "output profile").first().id)
        assertEquals(SettingsEntryId.SLEEP_TIMER, filterSettingsEntries(entries, "sleep timer").first().id)
        assertEquals(SettingsEntryId.SKIP_SILENCE, filterSettingsEntries(entries, "skip silence").first().id)
        assertEquals(SettingsEntryId.RESUME_ON_HEADSET, filterSettingsEntries(entries, "resume headset").first().id)
        assertTrue(filterSettingsEntries(entries, "bluetooth").any { it.id == SettingsEntryId.RESUME_ON_HEADSET })
        assertEquals(SettingsEntryId.KEEP_SCREEN_ON, filterSettingsEntries(entries, "keep screen").first().id)
    }

    @Test
    fun `search filters across groups by keyword and title`() {
        val strings = settingsStringsFor("en")
        val entries = buildSettingsSearchEntries(strings)

        val momiMatches = filterSettingsEntries(entries, "momi zip")
        assertEquals(1, momiMatches.size)
        assertEquals(SettingsEntryId.TRANSFER_HUB, momiMatches.first().id)

        val eqMatches = filterSettingsEntries(entries, "31hz")
        assertEquals(1, eqMatches.size)
        assertEquals(SettingsEntryId.EQUALIZER_EDITOR, eqMatches.first().id)

        val styleMatches = filterSettingsEntries(entries, "turntable")
        assertEquals(1, styleMatches.size)
        assertEquals(SettingsEntryId.PLAYER_STYLE_PICKER, styleMatches.first().id)

        val lyricsStyleMatches = filterSettingsEntries(entries, "lyrics bloom")
        assertEquals(1, lyricsStyleMatches.size)
        assertEquals(SettingsEntryId.LYRICS_STYLE_PICKER, lyricsStyleMatches.first().id)

        val accentMatches = filterSettingsEntries(entries, "adaptive accent")
        assertEquals(1, accentMatches.size)
        assertEquals(SettingsEntryId.OLED_ADAPTIVE_ACCENT, accentMatches.first().id)

        val translateMatches = filterSettingsEntries(entries, "bilingual")
        assertEquals(1, translateMatches.size)
        assertEquals(SettingsEntryId.LYRICS_TRANSLATE, translateMatches.first().id)

        val scrobbleMatches = filterSettingsEntries(entries, "librefm")
        assertEquals(1, scrobbleMatches.size)
        assertEquals(SettingsEntryId.SCROBBLE_INTEGRATION, scrobbleMatches.first().id)

        val discordMatches = filterSettingsEntries(entries, "discord rpc")
        assertEquals(1, discordMatches.size)
        assertEquals(SettingsEntryId.DISCORD_RPC, discordMatches.first().id)

        assertTrue(strings.appVersionFooter.contains("1.2.9"))
    }

    @Test
    fun `vietnamese strings localize all groups without emoji`() {
        val vi = settingsStringsFor("vi")
        val entries = buildSettingsSearchEntries(vi)

        assertTrue(entries.isNotEmpty())
        val langMatches = filterSettingsEntries(entries, "tieng viet")
        assertEquals(1, langMatches.size)
        assertEquals(SettingsEntryId.LANGUAGE_TOGGLE, langMatches.first().id)
    }

    @Test
    fun `formatting helpers output expected human readable values`() {
        assertEquals("512 B", formatByteSize(512L))
        assertEquals("2.0 KB", formatByteSize(2048L))
        assertEquals("31", formatFrequencyHz(31))
        assertEquals("16k", formatFrequencyHz(16000))
        assertEquals("+4.5 dB", formatDb(4.5f))
        assertEquals("-3.0 dB", formatDb(-3.0f))
        assertEquals("0.0 dB", formatDb(0f))
    }

    @Test
    fun `collapse latch applies hysteresis between enter and exit thresholds`() {
        assertFalse(computeCollapseLatch(false, 0, 12f, enterPx = 48f, exitPx = 12f))
        assertFalse(computeCollapseLatch(false, 0, 48f, enterPx = 48f, exitPx = 12f))
        assertTrue(computeCollapseLatch(false, 0, 49f, enterPx = 48f, exitPx = 12f))
        assertTrue(computeCollapseLatch(true, 0, 20f, enterPx = 48f, exitPx = 12f))
        assertFalse(computeCollapseLatch(true, 0, 11f, enterPx = 48f, exitPx = 12f))
        assertTrue(computeCollapseLatch(false, 1, 0f, enterPx = 48f, exitPx = 12f))
    }
}
