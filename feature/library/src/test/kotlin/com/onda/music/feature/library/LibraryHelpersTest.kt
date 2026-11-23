package com.onda.music.feature.library

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import org.junit.Assert.assertEquals
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
}
