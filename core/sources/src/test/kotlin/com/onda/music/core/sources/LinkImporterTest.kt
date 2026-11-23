package com.onda.music.core.sources

import com.onda.music.core.model.LinkEntityKind
import com.onda.music.core.model.LinkProvider
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LinkImporterTest {
    private val json = SourcesModule.provideSourcesJson()
    private val okHttp = SourcesModule.provideSourcesOkHttp()
    private val httpClient = SourcesModule.provideSourcesHttpClient(okHttp, json)
    private val trackMatcher = TrackMatcher()
    private val monochromeClient = MonochromeClient(httpClient, json, trackMatcher)
    private val ytMusicClient = YtMusicClient(httpClient, okHttp, json)
    private val importer = LinkImporter(httpClient, json, ytMusicClient, monochromeClient, trackMatcher)

    @Test
    fun detectsSpotifyAppleDeezerAndYouTubeUrls() {
        val spotifyPlaylist = importer.detect("Check this out https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc")
        assertNotNull(spotifyPlaylist)
        assertEquals(LinkProvider.SPOTIFY, spotifyPlaylist?.provider)
        assertEquals(LinkEntityKind.PLAYLIST, spotifyPlaylist?.kind)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", spotifyPlaylist?.externalId)

        val spotifyUri = importer.detect("spotify:album:4aawyAB9vmqN3uQ7FjRGTy")
        assertEquals(LinkProvider.SPOTIFY, spotifyUri?.provider)
        assertEquals(LinkEntityKind.ALBUM, spotifyUri?.kind)
        assertEquals("4aawyAB9vmqN3uQ7FjRGTy", spotifyUri?.externalId)

        val appleAlbum = importer.detect("https://music.apple.com/us/album/after-hours/1499378108")
        assertEquals(LinkProvider.APPLE_MUSIC, appleAlbum?.provider)
        assertEquals(LinkEntityKind.ALBUM, appleAlbum?.kind)
        assertEquals("1499378108", appleAlbum?.externalId)

        val appleTrack = importer.detect("https://music.apple.com/us/album/blinding-lights/1499378108?i=1499378615")
        assertEquals(LinkProvider.APPLE_MUSIC, appleTrack?.provider)
        assertEquals(LinkEntityKind.TRACK, appleTrack?.kind)
        assertEquals("1499378615", appleTrack?.externalId)

        val deezerPlaylist = importer.detect("https://www.deezer.com/en/playlist/908622995")
        assertEquals(LinkProvider.DEEZER, deezerPlaylist?.provider)
        assertEquals(LinkEntityKind.PLAYLIST, deezerPlaylist?.kind)
        assertEquals("908622995", deezerPlaylist?.externalId)

        val ytPlaylist = importer.detect("https://music.youtube.com/playlist?list=PL1234567890abcdef")
        assertEquals(LinkProvider.YOUTUBE, ytPlaylist?.provider)
        assertEquals(LinkEntityKind.PLAYLIST, ytPlaylist?.kind)
        assertEquals("PL1234567890abcdef", ytPlaylist?.externalId)

        val ytAlbum = importer.detect("https://music.youtube.com/browse/MPREb_abc123XYZ")
        assertEquals(LinkProvider.YOUTUBE, ytAlbum?.provider)
        assertEquals(LinkEntityKind.ALBUM, ytAlbum?.kind)
        assertEquals("MPREb_abc123XYZ", ytAlbum?.externalId)

        val ytTrack = importer.detect("https://youtu.be/dQw4w9WgXcQ")
        assertEquals(LinkProvider.YOUTUBE, ytTrack?.provider)
        assertEquals(LinkEntityKind.TRACK, ytTrack?.kind)
        assertEquals("dQw4w9WgXcQ", ytTrack?.externalId)

        assertNull(importer.detect("https://example.com/not-a-music-link"))
    }

    @Test
    fun parsesSpotifyEmbedHtmlPayload() {
        val html = """
            <html><head>
            <script id="__NEXT_DATA__" type="application/json">{
              "props": {
                "pageProps": {
                  "state": {
                    "data": {
                      "entity": {
                        "name": "Night Drive",
                        "coverArt": {
                          "sources": [
                            {"url": "https://cdn.example/small.jpg", "width": 300},
                            {"url": "https://cdn.example/large.jpg", "width": 640}
                          ]
                        },
                        "trackList": [
                          {
                            "title": "Midnight City",
                            "subtitle": "M83",
                            "duration": 243000,
                            "uri": "spotify:track:12345"
                          },
                          {
                            "title": "Resonance",
                            "subtitle": "HOME",
                            "duration": 212000,
                            "uri": "spotify:track:67890"
                          }
                        ]
                      }
                    }
                  }
                }
              }
            }</script>
            </head></html>
        """.trimIndent()

        val collection = importer.parseSpotifyEmbedHtml(html, "playlist")
        assertEquals("Night Drive", collection.title)
        assertEquals("https://cdn.example/large.jpg", collection.artworkUrl)
        assertEquals(LinkProvider.SPOTIFY, collection.provider)
        assertEquals(2, collection.tracks.size)
        assertEquals("Midnight City", collection.tracks[0].title)
        assertEquals("M83", collection.tracks[0].artist)
        assertEquals(243000L, collection.tracks[0].durationMs)
    }

    @Test
    fun parsesAppleMusicHtmlPayload() {
        val html = """
            <html><head>
            <script id="serialized-server-data" type="application/json">{
              "data": [
                {
                  "data": {
                    "sections": [
                      {
                        "itemKind": "containerDetailHeaderLockup",
                        "items": [
                          {
                            "title": "After Hours",
                            "subtitleLinks": [{"title": "The Weeknd"}],
                            "artwork": {"dictionary": {"url": "https://is1-ssl.mzstatic.com/image/{w}x{h}{c}.{f}"}}
                          }
                        ]
                      },
                      {
                        "itemKind": "trackLockup",
                        "items": [
                          {
                            "id": "1499378615",
                            "title": "Blinding Lights",
                            "artistName": "The Weeknd",
                            "duration": 200040
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }</script>
            </head></html>
        """.trimIndent()

        val collection = importer.parseAppleMusicHtml(html, "album")
        assertEquals("After Hours", collection.title)
        assertEquals("https://is1-ssl.mzstatic.com/image/600x600bb.jpg", collection.artworkUrl)
        assertEquals(LinkProvider.APPLE_MUSIC, collection.provider)
        assertEquals(1, collection.tracks.size)
        assertEquals("Blinding Lights", collection.tracks[0].title)
        assertEquals("The Weeknd", collection.tracks[0].artist)
        assertEquals("After Hours", collection.tracks[0].album)
        assertEquals(200040L, collection.tracks[0].durationMs)
    }

    @Test
    fun parsesDeezerTrackAndPagePayloads() {
        val singleJson = """
            {
              "id": "3135556",
              "title": "Harder, Better, Faster, Stronger",
              "duration": 224,
              "artist": {"name": "Daft Punk"},
              "album": {"title": "Discovery", "cover_xl": "https://cdn-images.dzcdn.net/cover.jpg"}
            }
        """.trimIndent()
        val single = importer.parseDeezerTrackJson(singleJson)
        assertEquals(LinkProvider.DEEZER, single.provider)
        assertEquals(1, single.tracks.size)
        assertEquals("Daft Punk", single.tracks[0].artist)
        assertEquals(224_000L, single.tracks[0].durationMs)

        val pageObj = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": "101",
                  "title": "Veridis Quo",
                  "duration": 345,
                  "artist": {"name": "Daft Punk"}
                }
              ]
            }
            """.trimIndent()
        ) as JsonObject
        val pageTracks = importer.parseDeezerTrackPage(pageObj, forcedAlbumTitle = "Discovery", fallbackCover = "https://cdn/cover.jpg")
        assertEquals(1, pageTracks.size)
        assertEquals("Discovery", pageTracks[0].album)
        assertEquals(345_000L, pageTracks[0].durationMs)
    }
}
