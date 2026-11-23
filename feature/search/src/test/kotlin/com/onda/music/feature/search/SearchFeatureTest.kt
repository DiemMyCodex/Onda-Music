package com.onda.music.feature.search

import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.ImportedCollection
import com.onda.music.core.model.ImportedTrackCandidate
import com.onda.music.core.model.LinkEntityKind
import com.onda.music.core.model.LinkProvider
import com.onda.music.core.model.ParsedLink
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SearchResultBundle
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.sources.TrackMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
