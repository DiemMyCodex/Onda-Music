package com.onda.music.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.SourceKind

@Entity(
    tableName = "songs",
    indices = [
        Index("albumId"),
        Index("artistId"),
        Index("ytId"),
        Index("monochromeId"),
        Index("liked"),
        Index("inLibrary")
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
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

@Entity(
    tableName = "artists",
    indices = [Index("name")]
)
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0
)

@Entity(
    tableName = "song_artist_cross_ref",
    primaryKeys = ["songId", "artistId"],
    indices = [Index("artistId")]
)
data class SongArtistCrossRef(
    val songId: String,
    val artistId: String,
    val position: Int = 0
)

@Entity(
    tableName = "albums",
    indices = [Index("artistId"), Index("title")]
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val year: Int? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val source: SourceKind = SourceKind.YOUTUBE
)

@Entity(
    tableName = "playlists",
    indices = [Index("remoteId"), Index("createdAtMs")]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val createdAtMs: Long = 0L,
    val remoteId: String? = null
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId"), Index("playlistId", "position")]
)
data class PlaylistSongEntity(
    val playlistId: String,
    val songId: String,
    val position: Int = 0,
    val addedAtMs: Long = 0L
)

@Entity(
    tableName = "play_events",
    indices = [Index("trackId"), Index("timestampMs")]
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val timestampMs: Long,
    val durationPlayedMs: Long
)

@Entity(
    tableName = "search_history",
    indices = [Index("searchedAtMs")]
)
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAtMs: Long
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val linesJson: String,
    val isSynced: Boolean = false,
    val hasWordTiming: Boolean = false,
    val source: LyricsSource = LyricsSource.LRCLIB,
    val updatedAtMs: Long = 0L
)

@Entity(
    tableName = "downloads",
    indices = [Index("status")]
)
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val localFilePath: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val updatedAtMs: Long = 0L
)
