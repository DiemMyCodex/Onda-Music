package com.onda.music.feature.home

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerTasteAnalyzerTest {

    private fun ytTrack(
        videoId: String,
        title: String,
        artist: String,
        liked: Boolean = false,
        playTimeMs: Long = 0L
    ) = Track(
        id = "yt_$videoId",
        title = title,
        artist = artist,
        source = SourceKind.YOUTUBE,
        ytId = videoId,
        liked = liked,
        totalPlayTimeMs = playTimeMs
    )

    @Test
    fun `vietnamese tracks yield dominant VN affinity and vi language`() {
        val now = 1_700_000_000_000L
        val t1 = ytTrack("vn1", "Chúng Ta Của Tương Lai", "Sơn Tùng M-TP", liked = true, playTimeMs = 600_000L)
        val t2 = ytTrack("vn2", "Đừng Làm Trái Tim Anh Đau", "Sơn Tùng M-TP")
        val t3 = ytTrack("vn3", "Mang Tiền Về Cho Mẹ", "Đen Vâu")

        val profile = ListenerTasteAnalyzer.analyze(
            currentTrack = t1,
            recentSongs = listOf(t1, t2, t3),
            playSignals = listOf(
                PlaySignal(t1.id, now - 300_000L, 180_000L),
                PlaySignal(t2.id, now - 900_000L, 190_000L),
                PlaySignal(t3.id, now - 1_500_000L, 210_000L)
            ),
            allSongs = listOf(t1, t2, t3),
            appLanguageCode = "en",
            nowMs = now
        )

        assertTrue(profile.hasListeningHistory)
        assertTrue(profile.vietnameseAffinityScore >= 0.7f)
        assertEquals("VN", profile.dominantRegionCode)
        assertEquals("vi", profile.dominantLanguageTag)
        assertEquals("vn1", profile.topSeedVideoIds.first())
    }

    @Test
    fun `international listening yields low vn affinity and US region when app is en`() {
        val now = 1_700_000_000_000L
        val t1 = ytTrack("en1", "Blinding Lights", "The Weeknd", liked = true, playTimeMs = 900_000L)
        val t2 = ytTrack("en2", "Starboy", "The Weeknd")
        val t3 = ytTrack("en3", "Midnight City", "M83")

        val profile = ListenerTasteAnalyzer.analyze(
            currentTrack = t1,
            recentSongs = listOf(t1, t2, t3),
            playSignals = listOf(
                PlaySignal(t1.id, now - 100_000L, 200_000L),
                PlaySignal(t3.id, now - 400_000L, 220_000L)
            ),
            allSongs = listOf(t1, t2, t3),
            appLanguageCode = "en",
            nowMs = now
        )

        assertTrue(profile.vietnameseAffinityScore <= 0.15f)
        assertEquals("US", profile.dominantRegionCode)
        assertEquals("en", profile.dominantLanguageTag)
    }

    @Test
    fun `mixed history blends scores and selects seeds from both tastes`() {
        val now = 1_700_000_000_000L
        val vn1 = ytTrack("vn_mix", "Chuyện Đôi Ta", "Emcee L", liked = true)
        val en1 = ytTrack("en_mix1", "Get Lucky", "Daft Punk", liked = true, playTimeMs = 1_200_000L)
        val en2 = ytTrack("en_mix2", "Instant Crush", "Daft Punk", playTimeMs = 900_000L)
        val en3 = ytTrack("en_mix3", "After Hours", "The Weeknd", playTimeMs = 800_000L)

        val profile = ListenerTasteAnalyzer.analyze(
            currentTrack = en1,
            recentSongs = listOf(en1, en2, vn1, en3),
            playSignals = listOf(
                PlaySignal(en1.id, now - 60_000L, 200_000L),
                PlaySignal(vn1.id, now - 120_000L, 180_000L),
                PlaySignal(en2.id, now - 180_000L, 200_000L),
                PlaySignal(en3.id, now - 240_000L, 200_000L)
            ),
            allSongs = listOf(vn1, en1, en2, en3),
            appLanguageCode = "en",
            nowMs = now
        )

        assertTrue(profile.vietnameseAffinityScore in 0.10f..0.70f)
        val subset = ListenerTasteAnalyzer.selectSeedSubset(profile, maxSeeds = 3)
        assertTrue(subset.any { it.videoId == "vn_mix" })
        assertTrue(subset.any { it.videoId == "en_mix1" })
    }

    @Test
    fun `recency decay and liked boost beat stale skipped plays`() {
        val now = 1_700_000_000_000L
        val staleSkipped = ytTrack("stale1", "Old Song", "Artist A")
        val freshLiked = ytTrack("fresh1", "Fresh Song", "Artist B", liked = true)

        val profile = ListenerTasteAnalyzer.analyze(
            currentTrack = null,
            recentSongs = listOf(freshLiked, staleSkipped),
            playSignals = listOf(
                PlaySignal(staleSkipped.id, now - 14L * 24L * 3_600_000L, 2_000L),
                PlaySignal(freshLiked.id, now - 600_000L, 180_000L)
            ),
            allSongs = listOf(staleSkipped, freshLiked),
            appLanguageCode = "en",
            nowMs = now
        )

        assertEquals("fresh1", profile.topSeedVideoIds.first())
    }

    @Test
    fun `rankCandidateTracks excludes current recent deduplicates and caps artist count`() {
        val now = 1_700_000_000_000L
        val current = ytTrack("cur", "Đom Đóm", "Jack")
        val a1 = ytTrack("a1", "Sóng Gió", "Jack")
        val a2 = ytTrack("a2", "Bạc Phận", "Jack")
        val a3 = ytTrack("a3", "Hồng Nhan", "Jack")
        val a3Dup = ytTrack("a3", "Hồng Nhan", "Jack")
        val b1 = ytTrack("b1", "Nàng Thơ", "Hoàng Dũng")
        val b2 = ytTrack("b2", "Chờ Anh Nhé", "Hoàng Dũng")

        val profile = ListenerTasteAnalyzer.analyze(
            currentTrack = current,
            recentSongs = listOf(current),
            playSignals = listOf(PlaySignal(current.id, now - 10_000L, 120_000L)),
            allSongs = listOf(current),
            appLanguageCode = "vi",
            nowMs = now
        )

        val ranked = ListenerTasteAnalyzer.rankCandidateTracks(
            candidates = listOf(
                current to 0,
                a1 to 0,
                a2 to 0,
                a3 to 0,
                a3Dup to 0,
                b1 to 1,
                b2 to 1
            ),
            profile = profile,
            likedTrackIds = setOf(b1.id),
            maxPerArtist = 2,
            limit = 10
        )

        assertFalse(ranked.any { it.ytId == "cur" })
        assertEquals(2, ranked.count { it.artist == "Jack" })
        assertTrue(ranked.any { it.ytId == "b1" && it.liked })
    }

    @Test
    fun `cold start without history falls back to VN region cleanly`() {
        val profile = ListenerTasteAnalyzer.analyze(
            currentTrack = null,
            recentSongs = emptyList(),
            playSignals = emptyList(),
            allSongs = emptyList(),
            appLanguageCode = "en"
        )

        assertFalse(profile.hasListeningHistory)
        assertTrue(profile.topSeeds.isEmpty())
        assertEquals("VN", profile.dominantRegionCode)
    }

    @Test
    fun `keyword matcher does not false positive on english word mono`() {
        assertFalse(ListenerTasteAnalyzer.isVietnameseTrack("Mono Audio Test", "Test Artist"))
        assertTrue(ListenerTasteAnalyzer.isVietnameseTrack("Waiting For You", "MTP & Sơn Tùng"))
    }
}
