package com.onda.music.feature.search

import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.ImportedCollection
import com.onda.music.core.model.ImportedTrackCandidate
import com.onda.music.core.model.LinkEntityKind
import com.onda.music.core.model.LinkProvider
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.ParsedLink
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SearchResultBundle
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.sources.TrackMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFeatureTest {

    @Test
    fun filterResultsIsolatesEachSourceCleanly() {
        val ytTrack = Track(id = "yt_1", title = "Midnight City", artist = "M83", source = SourceKind.YOUTUBE, ytId = "1")
        val monoTrack = Track(id = "mono_1", title = "Midnight City", artist = "M83", source = SourceKind.MONOCHROME, monochromeId = "1")
        val localTrack = Track(id = "local_1", title = "Midnight City", artist = "M83", source = SourceKind.LOCAL, localUri = "content://1")
        val album = Album(id = "MPREb_1", title = "Hurry Up, We're Dreaming", artist = "M83")
        val playlist = Playlist(id = "yt_pl_1", name = "Night Drive", remoteId = "1")
        val artist = Artist(id = "UC1", name = "M83")
        val bundle = SearchResultBundle(
            tracks = listOf(ytTrack),
            albums = listOf(album),
            artists = listOf(artist),
            playlists = listOf(playlist)
        )

        val all = SearchLogic.filterResults(SearchSourceFilter.ALL, bundle, listOf(monoTrack), listOf(localTrack))
        assertEquals(1, all.ytTracks.size)
        assertEquals(1, all.monochromeTracks.size)
        assertEquals(1, all.localTracks.size)
        assertEquals(1, all.albums.size)
        assertEquals(1, all.playlists.size)
        assertEquals(1, all.artists.size)

        val monoOnly = SearchLogic.filterResults(SearchSourceFilter.MONOCHROME, bundle, listOf(monoTrack), listOf(localTrack))
        assertEquals(listOf(monoTrack), monoOnly.monochromeTracks)
        assertTrue(monoOnly.ytTracks.isEmpty())
        assertTrue(monoOnly.localTracks.isEmpty())
        assertTrue(monoOnly.albums.isEmpty())

        val localOnly = SearchLogic.filterResults(SearchSourceFilter.LOCAL, bundle, listOf(monoTrack), listOf(localTrack))
        assertEquals(listOf(localTrack), localOnly.localTracks)
        assertTrue(localOnly.monochromeTracks.isEmpty())
        assertTrue(localOnly.ytTracks.isEmpty())
    }

    @Test
    fun matchImportedCollectionReportsProgressAndMatchesBestTracks() = runTest {
        val collection = ImportedCollection(
            title = "Synth Favorites",
            artworkUrl = "https://cdn/cover.jpg",
            provider = LinkProvider.SPOTIFY,
            tracks = listOf(
                ImportedTrackCandidate(
                    title = "Resonance",
                    artist = "HOME",
                    durationMs = 212_000L
                ),
                ImportedTrackCandidate(
                    title = "Midnight City (Official Audio)",
                    artist = "M83",
                    durationMs = 243_000L
                )
            )
        )
        val monoResonance = Track(
            id = "mono_res",
            title = "Resonance",
            artist = "HOME",
            durationMs = 212_000L,
            source = SourceKind.MONOCHROME,
            monochromeId = "res"
        )
        val ytMidnight = Track(
            id = "yt_mid",
            title = "Midnight City",
            artist = "M83",
            durationMs = 243_000L,
            source = SourceKind.YOUTUBE,
            ytId = "mid"
        )
        val progressEvents = mutableListOf<Pair<Int, Int>>()

        val matched = SearchLogic.matchImportedCollection(
            collection = collection,
            preferMonochrome = true,
            trackMatcher = TrackMatcher(),
            searchMonochrome = { q -> if (q.contains("resonance", ignoreCase = true)) listOf(monoResonance) else emptyList() },
            searchYtMusic = { q -> if (q.contains("midnight", ignoreCase = true)) listOf(ytMidnight) else emptyList() },
            onProgress = { processed, count -> progressEvents += processed to count }
        )

        assertEquals(2, matched.size)
        assertEquals("mono_res", matched[0].id)
        assertEquals("yt_mid", matched[1].id)
        assertEquals("https://cdn/cover.jpg", matched[0].artworkUrl)
        assertEquals(listOf(1 to 1, 2 to 2), progressEvents)
    }

    @Test
    fun buildImportedPlaylistIdAndDurationFormattingAreStable() {
        val parsed = ParsedLink(
            provider = LinkProvider.SPOTIFY,
            kind = LinkEntityKind.PLAYLIST,
            externalId = "37i9dQZF1DXcBWIGoYBM5M",
            originalUrl = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        )
        assertEquals(
            "import_spotify_37i9dQZF1DXcBWIGoYBM5M",
            SearchLogic.buildImportedPlaylistId(parsed, "Today's Top Hits", 1000L)
        )
        assertEquals("3:32", SearchLogic.formatDuration(212_000L))
        assertEquals("1:01:05", SearchLogic.formatDuration(3_665_000L))
        assertEquals("", SearchLogic.formatDuration(0L))
    }

    @Test
    fun applyLikedIdsSyncsAndUnlikesAcrossAllSearchAndDrillDownLists() {
        val yt1 = Track(id = "yt_1", title = "Song 1", artist = "A", source = SourceKind.YOUTUBE, ytId = "1", liked = false)
        val yt2 = Track(id = "yt_2", title = "Song 2", artist = "B", source = SourceKind.YOUTUBE, ytId = "2", liked = true)
        val mono1 = Track(id = "mono_1", title = "Song 3", artist = "C", source = SourceKind.MONOCHROME, monochromeId = "1", liked = false)
        val local1 = Track(id = "local_1", title = "Song 4", artist = "D", source = SourceKind.LOCAL, localUri = "content://1", liked = true)
        val drill1 = Track(id = "yt_3", title = "Song 5", artist = "E", source = SourceKind.YOUTUBE, ytId = "3", liked = false)
        val imported1 = Track(id = "mono_2", title = "Song 6", artist = "F", source = SourceKind.MONOCHROME, monochromeId = "2", liked = true)

        val initialState = SearchUiState(
            ytResults = SearchResultBundle(tracks = listOf(yt1, yt2)),
            monochromeTracks = listOf(mono1),
            localTracks = listOf(local1),
            drillDownState = DrillDownState(
                target = DrillDownTarget.MoodTarget(MoodCategory(id = "m1", title = "Chill", accentColorHex = 0xFF3A3A44L, params = "p1")),
                tracks = listOf(drill1, yt2)
            ),
            linkImportProgress = LinkImportProgress(
                stage = LinkImportStage.SUCCESS,
                matchedTracks = listOf(imported1, yt1)
            )
        )

        val likedState = SearchLogic.applyLikedIds(initialState, setOf("yt_1", "mono_1", "yt_3"))
        assertEquals(listOf(true, false), likedState.ytResults.tracks.map { it.liked })
        assertEquals(listOf(true), likedState.monochromeTracks.map { it.liked })
        assertEquals(listOf(false), likedState.localTracks.map { it.liked })
        assertEquals(listOf(true, false), likedState.drillDownState.tracks.map { it.liked })
        assertEquals(listOf(false, true), likedState.linkImportProgress.matchedTracks.map { it.liked })

        val unlikedState = SearchLogic.applyLikedIds(likedState, emptySet())
        assertTrue(unlikedState.ytResults.tracks.none { it.liked })
        assertTrue(unlikedState.monochromeTracks.none { it.liked })
        assertTrue(unlikedState.localTracks.none { it.liked })
        assertTrue(unlikedState.drillDownState.tracks.none { it.liked })
        assertTrue(unlikedState.linkImportProgress.matchedTracks.none { it.liked })
    }

    @Test
    fun resolveSearchStringsProvidesVietnameseAndEnglishTranslations() {
        val en = resolveSearchStrings("en")
        val vi = resolveSearchStrings("vi")

        assertFalse(en.isVietnamese)
        assertTrue(vi.isVietnamese)
        assertEquals("Search", en.screenTitle)
        assertEquals("Tìm kiếm", vi.screenTitle)
        assertNotEquals(en.importLinkTitle, vi.importLinkTitle)
        assertNotEquals(en.playAllAction, vi.playAllAction)
        assertNotEquals(en.shuffleAction, vi.shuffleAction)
        assertNotEquals(en.fetchAndSavePlaylist, vi.fetchAndSavePlaylist)
        assertTrue(vi.filterLabel(SearchSourceFilter.ALL).isNotBlank())
        assertTrue(vi.filterLabel(SearchSourceFilter.LOCAL).isNotBlank())
        assertTrue(en.trackCountLabel(4).contains("4"))
        assertTrue(vi.trackCountLabel(4).contains("4"))
    }

    @Test
    fun moodTargetDrillDownStateReportsVisibleWhenSet() {
        val hidden = DrillDownState()
        assertFalse(hidden.isVisible)

        val mood = MoodCategory(id = "mood_focus", title = "Focus", accentColorHex = 0xFF2D4A53L, params = "ggMPOg1uX1lMbVZ1")
        val visible = DrillDownState(
            target = DrillDownTarget.MoodTarget(mood),
            title = mood.title,
            isLoading = true
        )
        assertTrue(visible.isVisible)
    }
}
