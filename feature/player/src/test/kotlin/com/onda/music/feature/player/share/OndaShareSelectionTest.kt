package com.onda.music.feature.player.share

import com.onda.music.core.model.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OndaShareSelectionTest {

    private val sampleLines = listOf(
        LyricsLine(0L, 1_000L, "First verse line here"),
        LyricsLine(1_000L, 2_000L, "Second verse line here"),
        LyricsLine(2_000L, 3_000L, "", isInterlude = true),
        LyricsLine(3_000L, 4_000L, "Third verse line here"),
        LyricsLine(4_000L, 5_000L, "Fourth verse line here"),
        LyricsLine(5_000L, 6_000L, "Fifth verse line here"),
        LyricsLine(6_000L, 7_000L, "   ")
    )

    @Test
    fun singleTap_selectsAndDeselectsSingleLine() {
        val selected = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = emptyList(),
            tappedIndex = 0,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0), selected)

        val deselected = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = selected,
            tappedIndex = 0,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertTrue(deselected.isEmpty())
    }

    @Test
    fun contiguousFill_expandsForwardAndBackwardSkippingInterludes() {
        val forward = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = listOf(0),
            tappedIndex = 4,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0, 1, 3, 4), forward)

        val backward = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = listOf(4),
            tappedIndex = 0,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0, 1, 3, 4), backward)
    }

    @Test
    fun edgeTap_shrinksContiguousSelectionFromBoundaries() {
        val current = listOf(0, 1, 3, 4)

        val droppedFirst = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = current,
            tappedIndex = 0,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(1, 3, 4), droppedFirst)

        val droppedLast = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = current,
            tappedIndex = 4,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0, 1, 3), droppedLast)
    }

    @Test
    fun interiorTap_retainsLongerSideAndTieBreaksToBefore() {
        val current = listOf(0, 1, 3, 4, 5)

        val keepAfter = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = current,
            tappedIndex = 1,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(3, 4, 5), keepAfter)

        val keepBefore = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = current,
            tappedIndex = 4,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0, 1, 3), keepBefore)

        val equalTie = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = listOf(0, 1, 3),
            tappedIndex = 1,
            lines = sampleLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0), equalTie)
    }

    @Test
    fun wordLimit_rejectsExpansionWhenExceedingMaxWords() {
        val denseLines = listOf(
            LyricsLine(0L, 1_000L, "one two three four five six seven eight nine ten"),
            LyricsLine(1_000L, 2_000L, "eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty"),
            LyricsLine(2_000L, 3_000L, "twentyone twentytwo twentythree twentyfour twentyfive twentysix twentyseven")
        )

        val initial = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = emptyList(),
            tappedIndex = 0,
            lines = denseLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0), initial)

        val second = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = initial,
            tappedIndex = 1,
            lines = denseLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0, 1), second)
        assertEquals(20, OndaLyricSelectionLogic.countWords(second, denseLines))

        val rejected = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = second,
            tappedIndex = 2,
            lines = denseLines,
            maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
        )
        assertEquals(listOf(0, 1), rejected)

        val allowedPoster = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = second,
            tappedIndex = 2,
            lines = denseLines,
            maxWords = OndaLyricSelectionLogic.MAX_POSTER_WORDS
        )
        assertEquals(listOf(0, 1, 2), allowedPoster)
        assertEquals(27, OndaLyricSelectionLogic.countWords(allowedPoster, denseLines))
    }

    @Test
    fun invalidTargets_areIgnoredWithoutMutatingSelection() {
        val initial = listOf(0, 1)

        assertEquals(
            initial,
            OndaLyricSelectionLogic.toggleContiguousLine(initial, -1, sampleLines)
        )
        assertEquals(
            initial,
            OndaLyricSelectionLogic.toggleContiguousLine(initial, 99, sampleLines)
        )
        assertEquals(
            initial,
            OndaLyricSelectionLogic.toggleContiguousLine(initial, 2, sampleLines)
        )
        assertEquals(
            initial,
            OndaLyricSelectionLogic.toggleContiguousLine(initial, 6, sampleLines)
        )
    }

    @Test
    fun countWords_deduplicatesAndSkipsInvalidOrInterludeIndices() {
        assertEquals(
            8,
            OndaLyricSelectionLogic.countWords(listOf(0, 0, 1, 2, 6, -1, 42), sampleLines)
        )
    }
}
