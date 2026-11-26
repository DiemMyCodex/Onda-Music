package com.onda.music.core.sources

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaScannerTest {

    @Test
    fun localMediaScanResultDefaultsToEmptyTrackFolders() {
        val result = LocalMediaScanResult()
        assertTrue(result.tracks.isEmpty())
        assertTrue(result.folders.isEmpty())
        assertTrue(result.trackFolders.isEmpty())
    }

    @Test
    fun localMediaScanResultPreservesTrackFoldersMapping() {
        val track = Track(
            id = "local_42",
            title = "Wave",
            artist = "Onda",
            source = SourceKind.LOCAL,
            localUri = "content://media/external/audio/media/42"
        )
        val folder = LocalMediaFolder(
            path = "Music/Lossless",
            name = "Lossless",
            trackCount = 1
        )
        val result = LocalMediaScanResult(
            tracks = listOf(track),
            folders = listOf(folder),
            trackFolders = mapOf("local_42" to "Music/Lossless")
        )
        assertEquals("Music/Lossless", result.trackFolders[track.id])
        assertEquals(folder.path, result.trackFolders[track.id])
    }

    @Test
    fun resolveLocalFolderPathNormalizesRelativePathsAndFallbacks() {
        assertEquals("Music/Onda", resolveLocalFolderPath("Music/Onda/"))
        assertEquals("Music/Onda", resolveLocalFolderPath("Music/Onda///"))
        assertEquals("Download", resolveLocalFolderPath("Download"))
        assertEquals("Internal Storage", resolveLocalFolderPath(""))
        assertEquals("Internal Storage", resolveLocalFolderPath("/"))
        assertEquals("Internal Storage", resolveLocalFolderPath("   "))
        assertEquals("Internal Storage", resolveLocalFolderPath(null))
    }
}
