package com.onda.music.feature.home

import com.onda.music.core.model.ContextualShelf
import com.onda.music.core.model.HomeShelf
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverAndHomeSyncTest {

    private fun ytTrack(id: String, liked: Boolean = false) = Track(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        source = SourceKind.YOUTUBE,
        ytId = id.removePrefix("yt_"),
        liked = liked
    )

    @Test
    fun `syncLikedFlags sets and clears liked flags across all four feed containers`() {
        val qpLiked = ytTrack("yt_qp1", liked = false)
        val qpUnliked = ytTrack("yt_qp2", liked = true)
        val ctxLiked = ytTrack("yt_ctx1", liked = false)
        val ctxUnliked = ytTrack("yt_ctx2", liked = true)
        val shelfLiked = ytTrack("yt_sh1", liked = false)
        val shelfUnliked = ytTrack("yt_sh2", liked = true)
        val chartLiked = ytTrack("yt_ch1", liked = false)
        val chartUnliked = ytTrack("yt_ch2", liked = true)

        val feed = DiscoverFeedResult(
            quickPicks = listOf(qpLiked, qpUnliked),
            contextualShelf = ContextualShelf(
                seedTrackTitle = "Seed",
                seedArtist = "Seed Artist",
                useArtistHeading = false,
                tracks = listOf(ctxLiked, ctxUnliked)
            ),
            shelves = listOf(
                HomeShelf(
                    id = "shelf_1",
                    title = "Shelf 1",
                    tracks = listOf(shelfLiked, shelfUnliked)
                )
            ),
            chartShelves = listOf(
                HomeShelf(
                    id = "chart_1",
                    title = "Chart 1",
                    tracks = listOf(chartLiked, chartUnliked)
                )
            ),
            newReleases = emptyList(),
            moodCategories = emptyList(),
            allRemoteFailed = false,
            errorMessage = null
        )

        val likedIds = setOf("yt_qp1", "yt_ctx1", "yt_sh1", "yt_ch1")
        val synced = DiscoverRecommendationEngine.syncLikedFlags(feed, likedIds)

        assertTrue(synced.quickPicks[0].liked)
        assertFalse(synced.quickPicks[1].liked)

        val syncedCtx = synced.contextualShelf!!
        assertTrue(syncedCtx.tracks[0].liked)
        assertFalse(syncedCtx.tracks[1].liked)

        assertTrue(synced.shelves[0].tracks[0].liked)
        assertFalse(synced.shelves[0].tracks[1].liked)

        assertTrue(synced.chartShelves[0].tracks[0].liked)
        assertFalse(synced.chartShelves[0].tracks[1].liked)

        val cleared = DiscoverRecommendationEngine.syncLikedFlags(synced, emptySet())
        assertTrue(cleared.quickPicks.none { it.liked })
        assertTrue(cleared.contextualShelf!!.tracks.none { it.liked })
        assertTrue(cleared.shelves.flatMap { it.tracks }.none { it.liked })
        assertTrue(cleared.chartShelves.flatMap { it.tracks }.none { it.liked })
    }

    @Test
    fun `syncTracksWithSongMap and syncShelvesWithSongMap enforce Room truth and clear stale liked flags`() {
        val t1 = ytTrack("yt_1", liked = false)
        val t2 = ytTrack("yt_2", liked = true)
        val t3 = ytTrack("yt_3", liked = true)

        val songMap = mapOf(
            "yt_1" to ytTrack("yt_1", liked = true),
            "yt_2" to ytTrack("yt_2", liked = false)
        )

        val syncedTracks = syncTracksWithSongMap(listOf(t1, t2, t3), songMap)
        assertEquals(listOf(true, false, false), syncedTracks.map { it.liked })

        val shelves = listOf(
            HomeShelf(id = "s1", title = "Top", tracks = listOf(t1, t2, t3))
        )
        val syncedShelves = syncShelvesWithSongMap(shelves, songMap)
        assertEquals(listOf(true, false, false), syncedShelves[0].tracks.map { it.liked })
    }
}
