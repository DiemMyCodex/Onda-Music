package com.onda.music.core.sources

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTagCodecTest {

    @Test
    fun mp3Id3v2RoundTripsTagsAndArtworkWhilePreservingAudioFrames() {
        val mpegAudio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64, 1, 2, 3, 4, 5)
        val art = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 8, 7, 6)
        val meta = AudioTagMetadata(
            title = "Midnight OLED",
            artist = "Onda Studio",
            album = "Pure Black",
            trackNumber = 4,
            artworkBytes = art,
            artworkMimeType = "image/jpeg"
        )

        val written = AudioTagCodec.writeTags(mpegAudio, meta, "track.mp3")
        assertEquals(AudioTagFormat.MP3, AudioTagCodec.detectFormat(written))
        assertTrue(written.size > mpegAudio.size)

        val readBack = AudioTagCodec.readTags(written, "track.mp3")
        assertEquals("Midnight OLED", readBack.title)
        assertEquals("Onda Studio", readBack.artist)
        assertEquals("Pure Black", readBack.album)
        assertEquals(4, readBack.trackNumber)
        assertEquals("image/jpeg", readBack.artworkMimeType)
        assertArrayEquals(art, readBack.artworkBytes)
        assertArrayEquals(mpegAudio, written.copyOfRange(written.size - mpegAudio.size, written.size))
    }

    @Test
    fun flacVorbisAndPictureRoundTripPreservesStreamInfoAndAudio() {
        val streamInfo = ByteArray(34) { (it + 1).toByte() }
        val audioPayload = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x69, 0x02, 11, 22, 33, 44)
        val rawFlac = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.ISO_8859_1))
            write(0x80)
            write(0)
            write(0)
            write(34)
            write(streamInfo)
            write(audioPayload)
        }.toByteArray()

        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 42)
        val meta = AudioTagMetadata(
            title = "Hi-Res Wave",
            artist = "DiemMy",
            album = "24-Bit Sessions",
            trackNumber = 7,
            artworkBytes = pngBytes,
            artworkMimeType = "image/png"
        )

        val written = AudioTagCodec.writeTags(rawFlac, meta, "lossless.flac")
        assertEquals(AudioTagFormat.FLAC, AudioTagCodec.detectFormat(written))
        val readBack = AudioTagCodec.readTags(written, "lossless.flac")
        assertEquals("Hi-Res Wave", readBack.title)
        assertEquals("DiemMy", readBack.artist)
        assertEquals("24-Bit Sessions", readBack.album)
        assertEquals(7, readBack.trackNumber)
        assertEquals("image/png", readBack.artworkMimeType)
        assertArrayEquals(pngBytes, readBack.artworkBytes)
        assertArrayEquals(audioPayload, written.copyOfRange(written.size - audioPayload.size, written.size))
    }

    @Test
    fun mp4AtomsRoundTripAndAdjustStcoChunkOffsets() {
        val ftyp = box("ftyp", "M4A ".toByteArray(Charsets.ISO_8859_1) + ByteArray(8))
        val stcoPlaceholder = ByteArray(16).apply {
            this[7] = 1
        }
        val stbl = box("stbl", box("stco", stcoPlaceholder))
        val moovBefore = box("moov", box("trak", box("mdia", box("minf", stbl))))
        val initialMdatOffset = ftyp.size + moovBefore.size + 8
        val stcoFilled = ByteArray(16).apply {
            this[7] = 1
            this[8] = ((initialMdatOffset ushr 24) and 0xFF).toByte()
            this[9] = ((initialMdatOffset ushr 16) and 0xFF).toByte()
            this[10] = ((initialMdatOffset ushr 8) and 0xFF).toByte()
            this[11] = (initialMdatOffset and 0xFF).toByte()
        }
        val moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stco", stcoFilled))))))
        val mdat = box("mdat", byteArrayOf(10, 20, 30, 40, 50, 60))
        val rawM4a = ByteArrayOutputStream().apply {
            write(ftyp)
            write(moov)
            write(mdat)
        }.toByteArray()

        val jpegArt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)
        val meta = AudioTagMetadata(
            title = "Smoked Glass",
            artist = "Onda",
            album = "Acoustic",
            trackNumber = 2,
            artworkBytes = jpegArt,
            artworkMimeType = "image/jpeg"
        )

        val written = AudioTagCodec.writeTags(rawM4a, meta, "song.m4a")
        assertEquals(AudioTagFormat.MP4, AudioTagCodec.detectFormat(written))
        val readBack = AudioTagCodec.readTags(written, "song.m4a")
        assertEquals("Smoked Glass", readBack.title)
        assertEquals("Onda", readBack.artist)
        assertEquals("Acoustic", readBack.album)
        assertEquals(2, readBack.trackNumber)
        assertEquals("image/jpeg", readBack.artworkMimeType)
        assertArrayEquals(jpegArt, readBack.artworkBytes)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return ByteArrayOutputStream(size).apply {
            write((size ushr 24) and 0xFF)
            write((size ushr 16) and 0xFF)
            write((size ushr 8) and 0xFF)
            write(size and 0xFF)
            write(type.toByteArray(Charsets.ISO_8859_1))
            write(payload)
        }.toByteArray()
    }
}
