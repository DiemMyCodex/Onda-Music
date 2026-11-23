package com.onda.music.core.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonochromeClientTest {
    private val json = SourcesModule.provideSourcesJson()
    private val okHttp = SourcesModule.provideSourcesOkHttp()
    private val httpClient = SourcesModule.provideSourcesHttpClient(okHttp, json)
    private val client = MonochromeClient(httpClient, json, TrackMatcher())

    @Test
    fun parsesFlacHeaderForStandardAndHiRes() {
        val hiResHeader = ByteArray(26)
        hiResHeader[0] = 'f'.code.toByte()
        hiResHeader[1] = 'L'.code.toByte()
        hiResHeader[2] = 'a'.code.toByte()
        hiResHeader[3] = 'C'.code.toByte()
        val sampleRate = 96_000
        val bitsPerSample = 24
        val bitsMinusOne = bitsPerSample - 1
        hiResHeader[18] = ((sampleRate shr 12) and 0xFF).toByte()
        hiResHeader[19] = ((sampleRate shr 4) and 0xFF).toByte()
        hiResHeader[20] = (((sampleRate and 0x0F) shl 4) or ((bitsMinusOne shr 4) and 0x01)).toByte()
        hiResHeader[21] = ((bitsMinusOne and 0x0F) shl 4).toByte()

        val info = client.parseFlacHeader(hiResHeader)
        assertNotNull(info)
        assertEquals(96_000, info?.sampleRateHz)
        assertEquals(24, info?.bitsPerSample)
        assertTrue(info?.isHiRes == true)
        assertEquals("Hi-Res FLAC · 24-bit / 96 kHz", info?.qualityLabel)

        assertNull(client.parseFlacHeader("RIFF".toByteArray()))
    }

    @Test
    fun parsesStreamerAndRelaySearchResponses() {
        val streamerJson = """
            {
              "tracks": [
                {
                  "trackId": "tr_1",
                  "title": "Nights",
                  "artistNames": ["Frank Ocean"],
                  "albumTitle": "Blonde",
                  "duration": 307000,
                  "playable": true
                },
                {
                  "trackId": "tr_2",
                  "title": "Blocked",
                  "artistNames": ["Someone"],
                  "duration": 180,
                  "playable": false
                }
              ]
            }
        """.trimIndent()

        val streamerTracks = client.parseStreamerTracks(streamerJson)
        assertEquals(1, streamerTracks.size)
        assertEquals("tr_1", streamerTracks[0].monochromeId)
        assertEquals("Frank Ocean", streamerTracks[0].artist)
        assertEquals(307_000L, streamerTracks[0].durationMs)

        val relayJson = """
            {
              "data": [
                {
                  "id": "r_99",
                  "title": "Bridge Over Troubled Water",
                  "artist": "Simon & Garfunkel&Art Garfunkel",
                  "album": "Bridge Over Troubled Water",
                  "duration": 292
                }
              ]
            }
        """.trimIndent()
        val relayTracks = client.parseRelayTracks(relayJson)
        assertEquals(1, relayTracks.size)
        assertEquals("Simon & Garfunkel, Art Garfunkel", relayTracks[0].artist)
        assertEquals(292_000L, relayTracks[0].durationMs)
        assertFalse(relayTracks[0].id.isBlank())
    }
}
