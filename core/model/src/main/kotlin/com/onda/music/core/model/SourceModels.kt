package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class StreamOption(
    val url: String,
    val mimeType: String,
    val bitrateKbps: Int,
    val codec: String,
    val qualityLabel: String,
    val expiresAtMs: Long? = null,
    val source: SourceKind = SourceKind.YOUTUBE,
    val userAgent: String? = null
)

@Serializable
enum class LinkProvider {
    SPOTIFY,
    APPLE_MUSIC,
    DEEZER,
    YOUTUBE
}

@Serializable
enum class LinkEntityKind {
    TRACK,
    ALBUM,
    PLAYLIST
}

@Immutable
@Serializable
data class ParsedLink(
    val provider: LinkProvider,
    val kind: LinkEntityKind,
    val externalId: String,
    val originalUrl: String
)

@Immutable
@Serializable
data class ImportedTrackCandidate(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val externalId: String? = null
)

@Immutable
@Serializable
data class ImportedCollection(
    val title: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val provider: LinkProvider,
    val tracks: List<ImportedTrackCandidate> = emptyList()
)

@Immutable
@Serializable
data class HomeShelf(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList()
)

@Immutable
@Serializable
data class MoodCategory(
    val id: String,
    val title: String,
    val accentColorHex: Long,
    val params: String? = null
)

@Immutable
@Serializable
data class SearchResultBundle(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)
