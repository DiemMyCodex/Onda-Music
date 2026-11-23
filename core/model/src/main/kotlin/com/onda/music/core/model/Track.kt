package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class SourceKind {
    YOUTUBE,
    MONOCHROME,
    LOCAL
}

@Immutable
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val source: SourceKind = SourceKind.YOUTUBE,
    val localUri: String? = null,
    val ytId: String? = null,
    val monochromeId: String? = null,
    val liked: Boolean = false,
    val inLibrary: Boolean = false,
    val totalPlayTimeMs: Long = 0L,
    val lyricsOffsetMs: Long = 0L,
    val needsRelink: Boolean = false,
    val addedAtMs: Long = 0L
)

@Immutable
@Serializable
data class Artist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0
)

@Immutable
@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val year: Int? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val source: SourceKind = SourceKind.YOUTUBE
)

@Immutable
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val createdAtMs: Long = 0L,
    val remoteId: String? = null
)

@Immutable
@Serializable
data class PlayEvent(
    val id: Long = 0L,
    val trackId: String,
    val timestampMs: Long,
    val durationPlayedMs: Long
)
