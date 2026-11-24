package com.onda.music.core.sources

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AudioTagFormat {
    MP3,
    FLAC,
    MP4,
    UNSUPPORTED
}

data class AudioTagMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val trackNumber: Int? = null,
    val artworkBytes: ByteArray? = null,
    val artworkMimeType: String = "image/jpeg"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioTagMetadata) return false
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            trackNumber == other.trackNumber &&
            artworkMimeType == other.artworkMimeType &&
            artworkBytes.contentEquals(other.artworkBytes)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + artworkMimeType.hashCode()
        return result
    }
}

object AudioTagCodec {

    fun detectFormat(bytes: ByteArray, fileNameHint: String? = null): AudioTagFormat {
        if (bytes.size >= 4 &&
            bytes[0] == 'f'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() &&
            bytes[3] == 'C'.code.toByte()
        ) {
            return AudioTagFormat.FLAC
        }
        if (bytes.size >= 8 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
        ) {
            return AudioTagFormat.MP4
        }
        if (bytes.size >= 3 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()
        ) {
            return AudioTagFormat.MP3
        }
        if (bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xE0) == 0xE0
        ) {
            return AudioTagFormat.MP3
        }
        val lower = fileNameHint?.lowercase().orEmpty()
        return when {
            lower.endsWith(".flac") -> AudioTagFormat.FLAC
            lower.endsWith(".m4a") || lower.endsWith(".mp4") || lower.endsWith(".aac") -> AudioTagFormat.MP4
            lower.endsWith(".mp3") -> AudioTagFormat.MP3
            else -> AudioTagFormat.UNSUPPORTED
        }
    }

    fun readTags(bytes: ByteArray, fileNameHint: String? = null): AudioTagMetadata {
        return when (detectFormat(bytes, fileNameHint)) {
            AudioTagFormat.MP3 -> readMp3Tags(bytes)
            AudioTagFormat.FLAC -> readFlacTags(bytes)
            AudioTagFormat.MP4 -> readMp4Tags(bytes)
            AudioTagFormat.UNSUPPORTED -> AudioTagMetadata()
        }
    }

    fun writeTags(bytes: ByteArray, metadata: AudioTagMetadata, fileNameHint: String? = null): ByteArray {
        return when (detectFormat(bytes, fileNameHint)) {
            AudioTagFormat.MP3 -> writeMp3Tags(bytes, metadata)
            AudioTagFormat.FLAC -> writeFlacTags(bytes, metadata)
            AudioTagFormat.MP4 -> writeMp4Tags(bytes, metadata)
            AudioTagFormat.UNSUPPORTED -> throw IOException("Unsupported audio format for tag editing")
        }
    }

    private fun readMp3Tags(bytes: ByteArray): AudioTagMetadata {
        if (bytes.size < 10 ||
            bytes[0] != 'I'.code.toByte() ||
            bytes[1] != 'D'.code.toByte() ||
            bytes[2] != '3'.code.toByte()
        ) {
            return AudioTagMetadata()
        }
        val majorVersion = bytes[3].toInt() and 0xFF
        val tagSize = decodeSyncSafeInt(bytes, 6)
        val tagEnd = (10 + tagSize).coerceAtMost(bytes.size)
        var title = ""
        var artist = ""
        var album = ""
        var trackNumber: Int? = null
        var artworkBytes: ByteArray? = null
        var artworkMime = "image/jpeg"

        var pos = 10
        while (pos + 10 <= tagEnd) {
            if (bytes[pos] == 0.toByte()) break
            val frameId = String(bytes, pos, 4, Charsets.ISO_8859_1)
            val frameSize = if (majorVersion >= 4) {
                decodeSyncSafeInt(bytes, pos + 4)
            } else {
                readBeInt(bytes, pos + 4)
            }
            if (frameSize <= 0 || pos + 10 + frameSize > tagEnd) break
            val payloadStart = pos + 10
            when (frameId) {
                "TIT2" -> title = decodeId3Text(bytes, payloadStart, frameSize)
                "TPE1" -> artist = decodeId3Text(bytes, payloadStart, frameSize)
                "TALB" -> album = decodeId3Text(bytes, payloadStart, frameSize)
                "TRCK" -> {
                    val raw = decodeId3Text(bytes, payloadStart, frameSize)
                    trackNumber = raw.substringBefore('/').trim().toIntOrNull()
                }
                "APIC" -> {
                    val parsed = decodeId3Apic(bytes, payloadStart, frameSize)
                    if (parsed != null) {
                        artworkMime = parsed.first
                        artworkBytes = parsed.second
                    }
                }
            }
            pos += 10 + frameSize
        }
        return AudioTagMetadata(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            artworkBytes = artworkBytes,
            artworkMimeType = artworkMime
        )
    }

    private fun writeMp3Tags(bytes: ByteArray, metadata: AudioTagMetadata): ByteArray {
        val audioStart = if (bytes.size >= 10 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()
        ) {
            (10 + decodeSyncSafeInt(bytes, 6)).coerceAtMost(bytes.size)
        } else {
            0
        }

        val frames = ByteArrayOutputStream()
        if (metadata.title.isNotBlank()) {
            frames.write(buildId3v23TextFrame("TIT2", metadata.title))
        }
        if (metadata.artist.isNotBlank()) {
            frames.write(buildId3v23TextFrame("TPE1", metadata.artist))
        }
        if (metadata.album.isNotBlank()) {
            frames.write(buildId3v23TextFrame("TALB", metadata.album))
        }
        if (metadata.trackNumber != null && metadata.trackNumber > 0) {
            frames.write(buildId3v23TextFrame("TRCK", metadata.trackNumber.toString()))
        }
        val art = metadata.artworkBytes
        if (art != null && art.isNotEmpty()) {
            frames.write(buildId3v23ApicFrame(metadata.artworkMimeType, art))
        }

        val frameBytes = frames.toByteArray()
        val out = ByteArrayOutputStream(10 + frameBytes.size + (bytes.size - audioStart))
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        out.write(encodeSyncSafeInt(frameBytes.size))
        out.write(frameBytes)
        out.write(bytes, audioStart, bytes.size - audioStart)
        return out.toByteArray()
    }

    private fun buildId3v23TextFrame(frameId: String, value: String): ByteArray {
        val utf16 = value.toByteArray(Charsets.UTF_16LE)
        val payloadSize = 1 + 2 + utf16.size
        val out = ByteArrayOutputStream(10 + payloadSize)
        out.write(frameId.toByteArray(Charsets.ISO_8859_1))
        out.write(encodeBeInt(payloadSize))
        out.write(byteArrayOf(0, 0))
        out.write(1)
        out.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        out.write(utf16)
        return out.toByteArray()
    }

    private fun buildId3v23ApicFrame(mimeType: String, imageBytes: ByteArray): ByteArray {
        val mime = mimeType.ifBlank { "image/jpeg" }.toByteArray(Charsets.ISO_8859_1)
        val payloadSize = 1 + mime.size + 1 + 1 + 1 + imageBytes.size
        val out = ByteArrayOutputStream(10 + payloadSize)
        out.write("APIC".toByteArray(Charsets.ISO_8859_1))
        out.write(encodeBeInt(payloadSize))
        out.write(byteArrayOf(0, 0))
        out.write(0)
        out.write(mime)
        out.write(0)
        out.write(3)
        out.write(0)
        out.write(imageBytes)
        return out.toByteArray()
    }

    private fun decodeId3Text(bytes: ByteArray, offset: Int, length: Int): String {
        if (length <= 1) return ""
        val encoding = bytes[offset].toInt() and 0xFF
        val dataStart = offset + 1
        val dataLen = length - 1
        val raw = when (encoding) {
            0 -> String(bytes, dataStart, dataLen, Charsets.ISO_8859_1)
            1 -> {
                if (dataLen >= 2 && (bytes[dataStart].toInt() and 0xFF) == 0xFF && (bytes[dataStart + 1].toInt() and 0xFF) == 0xFE) {
                    String(bytes, dataStart + 2, dataLen - 2, Charsets.UTF_16LE)
                } else if (dataLen >= 2 && (bytes[dataStart].toInt() and 0xFF) == 0xFE && (bytes[dataStart + 1].toInt() and 0xFF) == 0xFF) {
                    String(bytes, dataStart + 2, dataLen - 2, Charsets.UTF_16BE)
                } else {
                    String(bytes, dataStart, dataLen, Charsets.UTF_16)
                }
            }
            2 -> String(bytes, dataStart, dataLen, Charsets.UTF_16BE)
            else -> String(bytes, dataStart, dataLen, Charsets.UTF_8)
        }
        return raw.trimEnd('\u0000').trim()
    }

    private fun decodeId3Apic(bytes: ByteArray, offset: Int, length: Int): Pair<String, ByteArray>? {
        if (length <= 4) return null
        val encoding = bytes[offset].toInt() and 0xFF
        val end = offset + length
        var p = offset + 1
        val mimeStart = p
        while (p < end && bytes[p] != 0.toByte()) p++
        val mime = String(bytes, mimeStart, p - mimeStart, Charsets.ISO_8859_1).ifBlank { "image/jpeg" }
        p += 1
        if (p >= end) return null
        p += 1
        if (encoding == 1 || encoding == 2) {
            while (p + 1 < end && !(bytes[p] == 0.toByte() && bytes[p + 1] == 0.toByte())) p += 2
            p += 2
        } else {
            while (p < end && bytes[p] != 0.toByte()) p++
            p += 1
        }
        if (p >= end) return null
        return mime to bytes.copyOfRange(p, end)
    }

    private fun readFlacTags(bytes: ByteArray): AudioTagMetadata {
        if (bytes.size < 8) return AudioTagMetadata()
        var pos = 4
        var title = ""
        var artist = ""
        var album = ""
        var trackNumber: Int? = null
        var artworkBytes: ByteArray? = null
        var artworkMime = "image/jpeg"

        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val blockLen = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + blockLen > bytes.size) break

            if (blockType == 4) {
                val comments = parseVorbisCommentBlock(bytes, pos, blockLen)
                comments.forEach { (key, value) ->
                    when (key.uppercase()) {
                        "TITLE" -> if (title.isEmpty()) title = value
                        "ARTIST" -> if (artist.isEmpty()) artist = value
                        "ALBUM" -> if (album.isEmpty()) album = value
                        "TRACKNUMBER" -> if (trackNumber == null) {
                            trackNumber = value.substringBefore('/').trim().toIntOrNull()
                        }
                    }
                }
            } else if (blockType == 6 && artworkBytes == null) {
                val pic = parseFlacPictureBlock(bytes, pos, blockLen)
                if (pic != null) {
                    artworkMime = pic.first
                    artworkBytes = pic.second
                }
            }

            pos += blockLen
            if (isLast) break
        }

        return AudioTagMetadata(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            artworkBytes = artworkBytes,
            artworkMimeType = artworkMime
        )
    }

    private fun writeFlacTags(bytes: ByteArray, metadata: AudioTagMetadata): ByteArray {
        if (bytes.size < 8) throw IOException("Invalid FLAC stream")
        var pos = 4
        val preservedBlocks = mutableListOf<Pair<Int, ByteArray>>()
        var vendor = "OndaMusic"
        val extraComments = mutableListOf<Pair<String, String>>()
        val managedKeys = setOf("TITLE", "ARTIST", "ALBUM", "TRACKNUMBER")
        var existingPictureBlock: ByteArray? = null

        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val blockLen = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + blockLen > bytes.size) throw IOException("Truncated FLAC metadata block")
            val blockData = bytes.copyOfRange(pos, pos + blockLen)
            when (blockType) {
                4 -> {
                    val parsedVendor = readVorbisVendor(blockData)
                    if (!parsedVendor.isNullOrBlank()) vendor = parsedVendor
                    parseVorbisCommentBlock(blockData, 0, blockData.size).forEach { (k, v) ->
                        if (k.uppercase() !in managedKeys) {
                            extraComments += k to v
                        }
                    }
                }
                6 -> {
                    if (existingPictureBlock == null) existingPictureBlock = blockData
                }
                else -> preservedBlocks += blockType to blockData
            }
            pos += blockLen
            if (isLast) break
        }
        val audioOffset = pos

        val finalComments = mutableListOf<Pair<String, String>>()
        if (metadata.title.isNotBlank()) finalComments += "TITLE" to metadata.title
        if (metadata.artist.isNotBlank()) finalComments += "ARTIST" to metadata.artist
        if (metadata.album.isNotBlank()) finalComments += "ALBUM" to metadata.album
        if (metadata.trackNumber != null && metadata.trackNumber > 0) {
            finalComments += "TRACKNUMBER" to metadata.trackNumber.toString()
        }
        finalComments += extraComments

        val allBlocks = mutableListOf<Pair<Int, ByteArray>>()
        val streamInfo = preservedBlocks.firstOrNull { it.first == 0 }
        if (streamInfo != null) {
            allBlocks += streamInfo
        }
        preservedBlocks.filter { it.first != 0 && it.first != 1 }.forEach { allBlocks += it }
        allBlocks += 4 to buildVorbisCommentBlock(vendor, finalComments)

        val art = metadata.artworkBytes
        if (art != null && art.isNotEmpty()) {
            allBlocks += 6 to buildFlacPictureBlock(metadata.artworkMimeType, art)
        } else if (existingPictureBlock != null) {
            allBlocks += 6 to existingPictureBlock
        }

        val out = ByteArrayOutputStream(bytes.size + 4096)
        out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        allBlocks.forEachIndexed { idx, (type, data) ->
            val lastFlag = if (idx == allBlocks.lastIndex) 0x80 else 0
            out.write(lastFlag or (type and 0x7F))
            out.write((data.size ushr 16) and 0xFF)
            out.write((data.size ushr 8) and 0xFF)
            out.write(data.size and 0xFF)
            out.write(data)
        }
        out.write(bytes, audioOffset, bytes.size - audioOffset)
        return out.toByteArray()
    }

    private fun readVorbisVendor(block: ByteArray): String? {
        if (block.size < 4) return null
        val len = readLeInt(block, 0)
        if (len < 0 || 4 + len > block.size) return null
        return String(block, 4, len, Charsets.UTF_8)
    }

    private fun parseVorbisCommentBlock(bytes: ByteArray, offset: Int, length: Int): List<Pair<String, String>> {
        val end = offset + length
        if (offset + 8 > end) return emptyList()
        var p = offset
        val vendorLen = readLeInt(bytes, p)
        p += 4 + vendorLen
        if (p + 4 > end) return emptyList()
        val count = readLeInt(bytes, p)
        p += 4
        val result = ArrayList<Pair<String, String>>()
        for (i in 0 until count.coerceIn(0, 1024)) {
            if (p + 4 > end) break
            val itemLen = readLeInt(bytes, p)
            p += 4
            if (itemLen < 0 || p + itemLen > end) break
            val entry = String(bytes, p, itemLen, Charsets.UTF_8)
            p += itemLen
            val eqIdx = entry.indexOf('=')
            if (eqIdx > 0) {
                result += entry.substring(0, eqIdx) to entry.substring(eqIdx + 1)
            }
        }
        return result
    }

    private fun buildVorbisCommentBlock(vendor: String, comments: List<Pair<String, String>>): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(encodeLeInt(vendorBytes.size))
        out.write(vendorBytes)
        out.write(encodeLeInt(comments.size))
        comments.forEach { (k, v) ->
            val raw = "$k=$v".toByteArray(Charsets.UTF_8)
            out.write(encodeLeInt(raw.size))
            out.write(raw)
        }
        return out.toByteArray()
    }

    private fun parseFlacPictureBlock(bytes: ByteArray, offset: Int, length: Int): Pair<String, ByteArray>? {
        val end = offset + length
        var p = offset
        if (p + 8 > end) return null
        p += 4
        val mimeLen = readBeInt(bytes, p)
        p += 4
        if (mimeLen < 0 || p + mimeLen + 4 > end) return null
        val mime = String(bytes, p, mimeLen, Charsets.ISO_8859_1).ifBlank { "image/jpeg" }
        p += mimeLen
        val descLen = readBeInt(bytes, p)
        p += 4 + descLen
        if (p + 20 > end) return null
        p += 16
        val dataLen = readBeInt(bytes, p)
        p += 4
        if (dataLen <= 0 || p + dataLen > end) return null
        return mime to bytes.copyOfRange(p, p + dataLen)
    }

    private fun buildFlacPictureBlock(mimeType: String, imageBytes: ByteArray): ByteArray {
        val mime = mimeType.ifBlank { "image/jpeg" }.toByteArray(Charsets.ISO_8859_1)
        val out = ByteArrayOutputStream(32 + mime.size + imageBytes.size)
        out.write(encodeBeInt(3))
        out.write(encodeBeInt(mime.size))
        out.write(mime)
        out.write(encodeBeInt(0))
        out.write(encodeBeInt(0))
        out.write(encodeBeInt(0))
        out.write(encodeBeInt(0))
        out.write(encodeBeInt(0))
        out.write(encodeBeInt(imageBytes.size))
        out.write(imageBytes)
        return out.toByteArray()
    }

    private data class Mp4Box(
        val type: String,
        val start: Int,
        val headerSize: Int,
        val size: Int
    ) {
        val payloadStart: Int get() = start + headerSize
        val payloadSize: Int get() = (size - headerSize).coerceAtLeast(0)
        val end: Int get() = start + size
    }

    private fun listMp4Boxes(bytes: ByteArray, rangeStart: Int, rangeEnd: Int): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var p = rangeStart
        while (p + 8 <= rangeEnd) {
            val rawSize = readBeInt(bytes, p)
            val type = String(bytes, p + 4, 4, Charsets.ISO_8859_1)
            val boxSize: Int
            val headerSize: Int
            if (rawSize == 1 && p + 16 <= rangeEnd) {
                val longSize = readBeLong(bytes, p + 8)
                if (longSize < 16L || p + longSize > rangeEnd) break
                boxSize = longSize.toInt()
                headerSize = 16
            } else if (rawSize == 0) {
                boxSize = rangeEnd - p
                headerSize = 8
            } else if (rawSize >= 8 && p + rawSize <= rangeEnd) {
                boxSize = rawSize
                headerSize = 8
            } else {
                break
            }
            boxes += Mp4Box(type = type, start = p, headerSize = headerSize, size = boxSize)
            p += boxSize
        }
        return boxes
    }

    private fun readMp4Tags(bytes: ByteArray): AudioTagMetadata {
        val top = listMp4Boxes(bytes, 0, bytes.size)
        val moov = top.firstOrNull { it.type == "moov" } ?: return AudioTagMetadata()
        val udta = listMp4Boxes(bytes, moov.payloadStart, moov.end).firstOrNull { it.type == "udta" }
            ?: return AudioTagMetadata()
        val meta = listMp4Boxes(bytes, udta.payloadStart, udta.end).firstOrNull { it.type == "meta" }
            ?: return AudioTagMetadata()
        val metaChildrenStart = meta.payloadStart + 4
        if (metaChildrenStart >= meta.end) return AudioTagMetadata()
        val ilst = listMp4Boxes(bytes, metaChildrenStart, meta.end).firstOrNull { it.type == "ilst" }
            ?: return AudioTagMetadata()

        var title = ""
        var artist = ""
        var album = ""
        var trackNumber: Int? = null
        var artworkBytes: ByteArray? = null
        var artworkMime = "image/jpeg"

        listMp4Boxes(bytes, ilst.payloadStart, ilst.end).forEach { atom ->
            val dataBox = listMp4Boxes(bytes, atom.payloadStart, atom.end).firstOrNull { it.type == "data" }
                ?: return@forEach
            if (dataBox.payloadSize <= 8) return@forEach
            val typeIndicator = readBeInt(bytes, dataBox.payloadStart)
            val valStart = dataBox.payloadStart + 8
            val valLen = dataBox.payloadSize - 8
            when (atom.type) {
                MP4_ATOM_TITLE -> title = String(bytes, valStart, valLen, Charsets.UTF_8)
                MP4_ATOM_ARTIST -> artist = String(bytes, valStart, valLen, Charsets.UTF_8)
                MP4_ATOM_ALBUM -> album = String(bytes, valStart, valLen, Charsets.UTF_8)
                "trkn" -> if (valLen >= 4) {
                    val num = ((bytes[valStart + 2].toInt() and 0xFF) shl 8) or (bytes[valStart + 3].toInt() and 0xFF)
                    if (num > 0) trackNumber = num
                }
                "covr" -> {
                    artworkMime = if (typeIndicator == 14) "image/png" else "image/jpeg"
                    artworkBytes = bytes.copyOfRange(valStart, valStart + valLen)
                }
            }
        }

        return AudioTagMetadata(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            artworkBytes = artworkBytes,
            artworkMimeType = artworkMime
        )
    }

    private fun writeMp4Tags(bytes: ByteArray, metadata: AudioTagMetadata): ByteArray {
        val topBoxes = listMp4Boxes(bytes, 0, bytes.size)
        val moovBox = topBoxes.firstOrNull { it.type == "moov" }
            ?: throw IOException("MP4 stream missing moov atom")
        val mdatBox = topBoxes.firstOrNull { it.type == "mdat" }

        val existingIlstItems = mutableListOf<Pair<String, ByteArray>>()
        val managedAtoms = setOf(MP4_ATOM_TITLE, MP4_ATOM_ARTIST, MP4_ATOM_ALBUM, "trkn", "covr")
        var existingCovr: ByteArray? = null

        val moovChildren = listMp4Boxes(bytes, moovBox.payloadStart, moovBox.end)
        val udtaBox = moovChildren.firstOrNull { it.type == "udta" }
        val otherUdtaChildren = mutableListOf<ByteArray>()
        if (udtaBox != null) {
            listMp4Boxes(bytes, udtaBox.payloadStart, udtaBox.end).forEach { child ->
                if (child.type == "meta" && child.payloadSize >= 4) {
                    val ilst = listMp4Boxes(bytes, child.payloadStart + 4, child.end)
                        .firstOrNull { it.type == "ilst" }
                    if (ilst != null) {
                        listMp4Boxes(bytes, ilst.payloadStart, ilst.end).forEach { atom ->
                            val rawAtom = bytes.copyOfRange(atom.start, atom.end)
                            if (atom.type == "covr") {
                                existingCovr = rawAtom
                            } else if (atom.type !in managedAtoms) {
                                existingIlstItems += atom.type to rawAtom
                            }
                        }
                    }
                } else {
                    otherUdtaChildren += bytes.copyOfRange(child.start, child.end)
                }
            }
        }

        val ilstPayload = ByteArrayOutputStream()
        if (metadata.title.isNotBlank()) ilstPayload.write(buildMp4TextAtom(MP4_ATOM_TITLE, metadata.title))
        if (metadata.artist.isNotBlank()) ilstPayload.write(buildMp4TextAtom(MP4_ATOM_ARTIST, metadata.artist))
        if (metadata.album.isNotBlank()) ilstPayload.write(buildMp4TextAtom(MP4_ATOM_ALBUM, metadata.album))
        if (metadata.trackNumber != null && metadata.trackNumber > 0) {
            ilstPayload.write(buildMp4TrackNumberAtom(metadata.trackNumber))
        }
        val art = metadata.artworkBytes
        if (art != null && art.isNotEmpty()) {
            val typeCode = if (metadata.artworkMimeType.contains("png", ignoreCase = true)) 14 else 13
            ilstPayload.write(buildMp4DataAtom("covr", typeCode, art))
        } else if (existingCovr != null) {
            ilstPayload.write(existingCovr)
        }
        existingIlstItems.forEach { ilstPayload.write(it.second) }

        val ilstBox = wrapMp4Box("ilst", ilstPayload.toByteArray())
        val hdlrPayload = ByteArrayOutputStream().apply {
            write(ByteArray(4))
            write(ByteArray(4))
            write("mdir".toByteArray(Charsets.ISO_8859_1))
            write("appl".toByteArray(Charsets.ISO_8859_1))
            write(ByteArray(12))
            write(0)
        }.toByteArray()
        val hdlrBox = wrapMp4Box("hdlr", hdlrPayload)
        val metaPayload = ByteArrayOutputStream().apply {
            write(ByteArray(4))
            write(hdlrBox)
            write(ilstBox)
        }.toByteArray()
        val metaBox = wrapMp4Box("meta", metaPayload)

        val udtaPayload = ByteArrayOutputStream().apply {
            otherUdtaChildren.forEach { write(it) }
            write(metaBox)
        }.toByteArray()
        val newUdtaBox = wrapMp4Box("udta", udtaPayload)

        val newMoovPayload = ByteArrayOutputStream()
        moovChildren.forEach { child ->
            if (child.type != "udta") {
                newMoovPayload.write(bytes, child.start, child.size)
            }
        }
        newMoovPayload.write(newUdtaBox)
        val newMoovBytes = wrapMp4Box("moov", newMoovPayload.toByteArray())

        val sizeDelta = newMoovBytes.size - moovBox.size
        if (sizeDelta != 0 && mdatBox != null && moovBox.start < mdatBox.start) {
            shiftMp4ChunkOffsets(newMoovBytes, 8, newMoovBytes.size, sizeDelta.toLong())
        }

        val out = ByteArrayOutputStream(bytes.size + sizeDelta.coerceAtLeast(0))
        topBoxes.forEach { box ->
            if (box.type == "moov") {
                out.write(newMoovBytes)
            } else {
                out.write(bytes, box.start, box.size)
            }
        }
        return out.toByteArray()
    }

    private fun shiftMp4ChunkOffsets(buf: ByteArray, start: Int, end: Int, delta: Long) {
        val boxes = listMp4Boxes(buf, start, end)
        for (box in boxes) {
            when (box.type) {
                "trak", "mdia", "minf", "stbl" -> shiftMp4ChunkOffsets(buf, box.payloadStart, box.end, delta)
                "stco" -> {
                    if (box.payloadSize >= 8) {
                        val count = readBeInt(buf, box.payloadStart + 4)
                        var p = box.payloadStart + 8
                        for (i in 0 until count) {
                            if (p + 4 > box.end) break
                            val updated = (readBeInt(buf, p).toLong() + delta).toInt()
                            writeBeInt(buf, p, updated)
                            p += 4
                        }
                    }
                }
                "co64" -> {
                    if (box.payloadSize >= 8) {
                        val count = readBeInt(buf, box.payloadStart + 4)
                        var p = box.payloadStart + 8
                        for (i in 0 until count) {
                            if (p + 8 > box.end) break
                            val updated = readBeLong(buf, p) + delta
                            writeBeLong(buf, p, updated)
                            p += 8
                        }
                    }
                }
            }
        }
    }

    private fun buildMp4TextAtom(atomType: String, text: String): ByteArray {
        return buildMp4DataAtom(atomType, 1, text.toByteArray(Charsets.UTF_8))
    }

    private fun buildMp4TrackNumberAtom(trackNumber: Int): ByteArray {
        val payload = ByteArray(8)
        payload[2] = ((trackNumber ushr 8) and 0xFF).toByte()
        payload[3] = (trackNumber and 0xFF).toByte()
        return buildMp4DataAtom("trkn", 0, payload)
    }

    private fun buildMp4DataAtom(atomType: String, typeIndicator: Int, valueBytes: ByteArray): ByteArray {
        val dataPayload = ByteArrayOutputStream(8 + valueBytes.size).apply {
            write(encodeBeInt(typeIndicator))
            write(ByteArray(4))
            write(valueBytes)
        }.toByteArray()
        val dataBox = wrapMp4Box("data", dataPayload)
        return wrapMp4Box(atomType, dataBox)
    }

    private fun wrapMp4Box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(8 + payload.size)
        out.write(encodeBeInt(8 + payload.size))
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private fun decodeSyncSafeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun encodeSyncSafeInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte()
    )

    private fun readBeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun encodeBeInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun writeBeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }

    private fun readBeLong(bytes: ByteArray, offset: Int): Long {
        return ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long
    }

    private fun writeBeLong(bytes: ByteArray, offset: Int, value: Long) {
        val bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array()
        System.arraycopy(bb, 0, bytes, offset, 8)
    }

    private fun readLeInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun encodeLeInt(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )

    private const val MP4_ATOM_TITLE = "\u00A9nam"
    private const val MP4_ATOM_ARTIST = "\u00A9ART"
    private const val MP4_ATOM_ALBUM = "\u00A9alb"
}

@Singleton
class LocalAudioTagEditor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun readFromUri(uri: Uri): Result<AudioTagMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Unable to open local audio file")
            AudioTagCodec.readTags(bytes, uri.lastPathSegment)
        }
    }

    suspend fun writeToUriAtomic(
        uri: Uri,
        metadata: AudioTagMetadata
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val originalBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Unable to read audio source")
            val updatedBytes = AudioTagCodec.writeTags(originalBytes, metadata, uri.lastPathSegment)
            if (updatedBytes.isEmpty()) {
                throw IOException("Tag serialization produced empty output")
            }

            val tempFile = File(context.cacheDir, "onda_tag_tmp_${System.currentTimeMillis()}.bin")
            try {
                tempFile.writeBytes(updatedBytes)
                val verified = tempFile.readBytes()
                if (verified.size != updatedBytes.size) {
                    throw IOException("Temp file verification mismatch")
                }
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(verified)
                    out.flush()
                } ?: throw IOException("Write permission denied for audio URI")
            } finally {
                tempFile.delete()
            }

            val artBytes = metadata.artworkBytes
            if (artBytes != null && artBytes.isNotEmpty()) {
                val artDir = File(context.filesDir, "edited_artwork").apply { mkdirs() }
                val ext = if (metadata.artworkMimeType.contains("png", ignoreCase = true)) "png" else "jpg"
                val artFile = File(artDir, "art_${uri.toString().hashCode().toUInt()}_${System.currentTimeMillis()}.$ext")
                artFile.writeBytes(artBytes)
                Uri.fromFile(artFile).toString()
            } else {
                null
            }
        }
    }
}
