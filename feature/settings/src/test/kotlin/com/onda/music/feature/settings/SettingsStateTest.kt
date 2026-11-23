package com.onda.music.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateTest {

    @Test
    fun `empty query returns all 21 settings entries across 5 groups`() {
        val strings = settingsStringsFor("en")
        val entries = buildSettingsSearchEntries(strings)
        val filtered = filterSettingsEntries(entries, "   ")

        assertEquals(entries.size, filtered.size)
        assertEquals(5, filtered.map { it.group }.toSet().size)
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
}
