package com.onda.music.core.lyrics

import com.onda.music.core.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    private val parser = LyricsParser()

    @Test
    fun parsesSyncedLinesAndMultipleTimestampsAndOffset() {
        val lrc = """
            [ar:Daft Punk]
            [ti:Around the World]
            [offset:500]
            [00:02.00][00:12.00]Around the world
            [00:06.50]Around the world
        """.trimIndent()

        val doc = parser.parse(trackId = "t1", rawContent = lrc, source = LyricsSource.LRCLIB)
        assertTrue(doc.isSynced)
        assertFalse(doc.hasWordTiming)
        assertEquals(3, doc.lines.size)
        assertEquals(1_500L, doc.lines[0].startTimeMs)
        assertEquals(6_000L, doc.lines[0].endTimeMs)
        assertEquals("Around the world", doc.lines[0].text)
        assertEquals(6_000L, doc.lines[1].startTimeMs)
        assertEquals(11_500L, doc.lines[1].endTimeMs)
        assertEquals(11_500L, doc.lines[2].startTimeMs)
    }

    @Test
    fun parsesSyncedLinesWithoutSubseconds() {
        val lrc = """
            [00:05]First line
            [00:09]Second line
        """.trimIndent()

        val doc = parser.parse(trackId = "t_sec", rawContent = lrc, source = LyricsSource.LRCLIB)
        assertTrue(doc.isSynced)
        assertFalse(doc.hasWordTiming)
        assertEquals(2, doc.lines.size)
        assertEquals(5_000L, doc.lines[0].startTimeMs)
        assertEquals(9_000L, doc.lines[0].endTimeMs)
        assertEquals(9_000L, doc.lines[1].startTimeMs)
    }

    @Test
    fun parsesEnhancedWordLevelTimings() {
        val enhancedLrc = """
            [00:10.00]<00:10.00>We <00:10.40>are <00:10.80>the <00:11.20>music <00:11.80>makers
            [00:13.00]Next line
        """.trimIndent()

        val doc = parser.parse(
            trackId = "t2",
            rawContent = enhancedLrc,
            source = LyricsSource.LRCLIB,
            interludeGapThresholdMs = 5_000L
        )
        assertTrue(doc.isSynced)
        assertTrue(doc.hasWordTiming)
        assertTrue(doc.lines[0].isInterlude)
        assertEquals(0L, doc.lines[0].startTimeMs)
        assertEquals(10_000L, doc.lines[0].endTimeMs)

        val wordLine = doc.lines[1]
        assertEquals("We are the music makers", wordLine.text)
        assertEquals(5, wordLine.words.size)
        assertEquals("We", wordLine.words[0].text)
        assertEquals(10_000L, wordLine.words[0].startTimeMs)
        assertEquals(10_400L, wordLine.words[0].endTimeMs)
        assertEquals("makers", wordLine.words[4].text)
        assertEquals(11_800L, wordLine.words[4].startTimeMs)
    }

    @Test
    fun detectsMidSongInterludesAndInstrumentalMarkers() {
        val lrcWithBreak = """
            [00:01.00]Opening verse
            [00:04.00]♪
            [00:14.00]Chorus returns
            [00:30.00]Final verse
        """.trimIndent()

        val doc = parser.parse(
            trackId = "t3",
            rawContent = lrcWithBreak,
            source = LyricsSource.LRCLIB,
            interludeGapThresholdMs = 5_000L
        )
        assertTrue(doc.isSynced)
        val interludes = doc.lines.filter { it.isInterlude }
        assertEquals(2, interludes.size)
        assertEquals(4_000L, interludes[0].startTimeMs)
        assertEquals(14_000L, interludes[0].endTimeMs)
        assertTrue(interludes[1].startTimeMs >= 18_000L)
        assertEquals(30_000L, interludes[1].endTimeMs)
    }

    @Test
    fun parsesPlainUnsyncedLyricsCleanly() {
        val plain = """
            [ti:Plain Song]
            First line of song
            Second line of song
        """.trimIndent()

        val doc = parser.parse(trackId = "t4", rawContent = plain, source = LyricsSource.YOUTUBE_MUSIC)
        assertFalse(doc.isSynced)
        assertFalse(doc.hasWordTiming)
        assertEquals(LyricsSource.YOUTUBE_MUSIC, doc.source)
        assertEquals(2, doc.lines.size)
        assertEquals("First line of song", doc.lines[0].text)
        assertEquals(0L, doc.lines[0].startTimeMs)
    }

    @Test
    fun parsesBetterLyricsTtmlWithWordTimingsAndTranslations() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="00:01.000" end="00:03.500">
                  <span begin="00:01.000" end="00:01.800">Hello</span>
                  <span begin="00:01.800" end="00:03.500">world &amp; sky</span>
                  <span ttm:role="x-translation">Xin chao the gioi</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val doc = parser.parse(trackId = "t5", rawContent = ttml, source = LyricsSource.BETTER_LYRICS)
        assertTrue(doc.isSynced)
        assertTrue(doc.hasWordTiming)
        assertEquals(LyricsSource.BETTER_LYRICS, doc.source)
        assertEquals(1, doc.lines.size)
        assertEquals("Hello world & sky", doc.lines[0].text)
        assertEquals("Xin chao the gioi", doc.lines[0].translatedText)
        assertEquals(2, doc.lines[0].words.size)
        assertEquals(1_000L, doc.lines[0].words[0].startTimeMs)
    }

    @Test
    fun mergesNetEaseTranslationAndPrioritizesWordTimedCandidates() {
        val primary = parser.parse(
            trackId = "t6",
            rawContent = "[00:01.00]<00:01.00>Night <00:01.50>wave\n[00:04.00]Pure black",
            source = LyricsSource.SIMP_MUSIC
        )
        val merged = parser.mergeTranslation(
            primary,
            "[00:01.10]Song dem\n[00:04.05]Den thuan"
        )
        assertEquals("Song dem", merged.lines[0].translatedText)
        assertEquals("Den thuan", merged.lines[1].translatedText)

        val lrcOnly = parser.parse(
            trackId = "t6",
            rawContent = "[00:01.00]Night wave\n[00:04.00]Pure black",
            source = LyricsSource.LRCLIB
        )
        val picked = LyricsRepository.selectBestLyricsCandidate(listOf(lrcOnly, merged))
        assertEquals(LyricsSource.SIMP_MUSIC, picked?.source)
        assertTrue(picked?.hasWordTiming == true)
    }
}
