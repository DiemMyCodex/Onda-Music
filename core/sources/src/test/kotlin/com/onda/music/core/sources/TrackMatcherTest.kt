package com.onda.music.core.sources

import com.onda.music.core.model.ImportedTrackCandidate
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {
    private val matcher = TrackMatcher()

    @Test
    fun coreTitleStripsDecorations() {
        assertEquals(
            "blinding lights",
            matcher.coreTitle("Blinding Lights (Official Video)")
        )
        assertEquals(
            "starboy",
            matcher.coreTitle("Starboy (feat. Daft Punk) [Remastered]")
        )
        assertEquals(
            "midnight city",
            matcher.coreTitle("Midnight City - Official Audio")
        )
    }

    @Test
    fun ranksExactAndDecoratedMatchAboveWrongVersionOrDuration() {
        val candidate = ImportedTrackCandidate(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 200_000L
        )
        val exact = Track(
            id = "yt_exact",
            title = "Blinding Lights (Official Audio)",
            artist = "The Weeknd",
            durationMs = 201_000L,
            source = SourceKind.YOUTUBE
        )
        val liveVersion = Track(
            id = "yt_live",
            title = "Blinding Lights (Live)",
            artist = "The Weeknd",
            durationMs = 200_000L,
            source = SourceKind.YOUTUBE
        )
        val wrongArtist = Track(
            id = "yt_wrong",
            title = "Blinding Lights",
            artist = "Cover Band",
            durationMs = 200_000L,
            source = SourceKind.YOUTUBE
        )

        val ranked = matcher.rank(candidate, listOf(wrongArtist, liveVersion, exact))
        assertEquals(1, ranked.size)
        assertEquals("yt_exact", ranked.first().track.id)
        assertTrue(ranked.first().score >= 0.95f)
        assertTrue(matcher.score(candidate, liveVersion) < 0.7f)
        assertEquals(0f, matcher.score(candidate, wrongArtist))
    }

    @Test
    fun respectsWordBoundariesForAliveVsLive() {
        val candidate = ImportedTrackCandidate(
            title = "Stayin Alive",
            artist = "Bee Gees",
            durationMs = 245_000L
        )
        val target = Track(
            id = "yt_alive",
            title = "Stayin Alive",
            artist = "Bee Gees",
            durationMs = 245_000L
        )
        val match = matcher.bestMatch(candidate, listOf(target))
        assertNotNull(match)
        assertEquals(1f, match!!.score, 0.001f)
    }

    @Test
    fun rejectsLargeDurationMismatch() {
        val candidate = ImportedTrackCandidate(
            title = "Intro",
            artist = "The xx",
            durationMs = 127_000L
        )
        val extendedMix = Track(
            id = "yt_ext",
            title = "Intro",
            artist = "The xx",
            durationMs = 350_000L
        )
        val score = matcher.score(candidate, extendedMix)
        assertTrue(score >= 0.79f)

        val mismatchArtist = Track(
            id = "yt_other",
            title = "Intro",
            artist = "M83",
            durationMs = 127_000L
        )
        assertNull(matcher.bestMatch(candidate, listOf(mismatchArtist)))
    }
}
