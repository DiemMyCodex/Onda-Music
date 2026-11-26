package com.onda.music.feature.library

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHelpersTest {

    @Test
    fun reorderTrackListMovesItemCleanly() {
        val tracks = listOf(
            Track(id = "t1", title = "One", artist = "Artist A"),
            Track(id = "t2", title = "Two", artist = "Artist B"),
            Track(id = "t3", title = "Three", artist = "Artist C")
        )
        val reordered = reorderTrackList(tracks, fromIndex = 0, toIndex = 2)
        assertEquals(listOf("t2", "t3", "t1"), reordered.map { it.id })
    }

    @Test
    fun reorderTrackListIgnoresOutOfBounds() {
        val tracks = listOf(
            Track(id = "t1", title = "One", artist = "Artist A"),
            Track(id = "t2", title = "Two", artist = "Artist B")
        )
        assertEquals(tracks, reorderTrackList(tracks, fromIndex = -1, toIndex = 1))
        assertEquals(tracks, reorderTrackList(tracks, fromIndex = 0, toIndex = 5))
    }

    @Test
    fun findAutoRelinkMatchesByTitleAndArtist() {
        val missing = listOf(
            Track(
                id = "momi_1",
                title = "Midnight City!",
                artist = "M83",
                source = SourceKind.LOCAL,
                needsRelink = true
            ),
            Track(
                id = "momi_2",
                title = "Unmatched Song",
                artist = "Unknown",
                source = SourceKind.LOCAL,
                needsRelink = true
            )
        )
        val scanned = listOf(
            Track(
                id = "local_42",
                title = "Midnight City",
                artist = "M83",
                source = SourceKind.LOCAL,
                localUri = "content://media/external/audio/media/42"
            )
        )
        val matches = findAutoRelinkMatches(missing, scanned)
        assertEquals(
            mapOf("momi_1" to "content://media/external/audio/media/42"),
            matches
        )
    }

    @Test
    fun formatHelpersProduceExpectedStrings() {
        assertEquals("0:00", formatDurationMs(0L))
        assertEquals("3:05", formatDurationMs(185_000L))
        assertEquals("1:01:05", formatDurationMs(3_665_000L))
        assertEquals("1.5h", formatHoursListened(5_400_000L))
        assertEquals("12h", formatHoursListened(43_200_000L))
        assertEquals("0 MB", formatBytes(0L))
        assertEquals("2.0 MB", formatBytes(2L * 1024L * 1024L))
    }

    @Test
    fun filterLocalTracksByFolderMatchesExactFolderWithoutFallback() {
        val tracks = listOf(
            Track(id = "local_1", title = "A", artist = "X", source = SourceKind.LOCAL, localUri = "content://media/external/audio/media/1"),
            Track(id = "local_2", title = "B", artist = "Y", source = SourceKind.LOCAL, localUri = "content://media/external/audio/media/2"),
            Track(id = "local_3", title = "C", artist = "Z", source = SourceKind.LOCAL, localUri = "content://media/external/audio/media/3")
        )
        val trackFolders = mapOf(
            "local_1" to "Music/Lossless",
            "local_2" to "Downloads"
        )

        assertEquals(tracks, filterLocalTracksByFolder(tracks, trackFolders, null))
        assertEquals(tracks, filterLocalTracksByFolder(tracks, trackFolders, "   "))
        assertEquals(listOf("local_1"), filterLocalTracksByFolder(tracks, trackFolders, "Music/Lossless").map { it.id })
        assertEquals(listOf("local_2"), filterLocalTracksByFolder(tracks, trackFolders, "Downloads").map { it.id })
        assertTrue(filterLocalTracksByFolder(tracks, trackFolders, "Music/Other").isEmpty())
        assertTrue(filterLocalTracksByFolder(tracks, emptyMap(), "Music/Lossless").isEmpty())
    }

    @Test
    fun resolveLibraryStringsLocalizesAllTabsAndRanges() {
        val en = resolveLibraryStrings("en")
        val vi = resolveLibraryStrings("vi")
        val viRegion = resolveLibraryStrings("vi-VN")

        assertEquals(vi, viRegion)
        LibraryTab.entries.forEach { tab ->
            assertTrue(en.tabLabel(tab).isNotBlank())
            assertTrue(vi.tabLabel(tab).isNotBlank())
        }
        StatsRange.entries.forEach { range ->
            assertTrue(en.statsRangeLabel(range).isNotBlank())
            assertTrue(vi.statsRangeLabel(range).isNotBlank())
        }
        assertNotEquals(en.libraryTitle, vi.libraryTitle)
        assertNotEquals(en.tabLabel(LibraryTab.OVERVIEW), vi.tabLabel(LibraryTab.OVERVIEW))
        assertNotEquals(en.statsRangeLabel(StatsRange.WEEK), vi.statsRangeLabel(StatsRange.WEEK))
    }

    @Test
    fun libraryUiStateHasExpectedDefaults() {
        val state = LibraryUiState()
        assertEquals("en", state.languageCode)
        assertTrue(state.glassBlurEnabled)
        assertTrue(state.localTrackFolders.isEmpty())
    }
}
