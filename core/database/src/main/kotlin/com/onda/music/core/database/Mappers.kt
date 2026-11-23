package com.onda.music.core.database

import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.PlayEvent
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val databaseJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun SongEntity.toModel(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    source = source,
    localUri = localUri,
    ytId = ytId,
    monochromeId = monochromeId,
    liked = liked,
    inLibrary = inLibrary,
    totalPlayTimeMs = totalPlayTimeMs,
    lyricsOffsetMs = lyricsOffsetMs,
    needsRelink = needsRelink,
    addedAtMs = addedAtMs
)

fun Track.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    source = source,
    localUri = localUri,
    ytId = ytId,
    monochromeId = monochromeId,
    liked = liked,
    inLibrary = inLibrary,
    totalPlayTimeMs = totalPlayTimeMs,
    lyricsOffsetMs = lyricsOffsetMs,
    needsRelink = needsRelink,
    addedAtMs = addedAtMs
)

fun ArtistEntity.toModel(): Artist = Artist(
    id = id,
    name = name,
    thumbnailUrl = thumbnailUrl,
    trackCount = trackCount
)

fun Artist.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    thumbnailUrl = thumbnailUrl,
    trackCount = trackCount
)

fun AlbumEntity.toModel(): Album = Album(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    year = year,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    source = source
)

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    year = year,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    source = source
)

fun PlaylistEntity.toModel(): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    createdAtMs = createdAtMs,
    remoteId = remoteId
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    createdAtMs = createdAtMs,
    remoteId = remoteId
)

fun PlayEventEntity.toModel(): PlayEvent = PlayEvent(
    id = id,
    trackId = trackId,
    timestampMs = timestampMs,
    durationPlayedMs = durationPlayedMs
)

fun PlayEvent.toEntity(): PlayEventEntity = PlayEventEntity(
    id = id,
    trackId = trackId,
    timestampMs = timestampMs,
    durationPlayedMs = durationPlayedMs
)

fun LyricsEntity.toModel(): LyricsDocument {
    val decodedLines = if (linesJson.isBlank()) {
        emptyList()
    } else {
        runCatching { databaseJson.decodeFromString<List<LyricsLine>>(linesJson) }.getOrDefault(emptyList())
    }
    return LyricsDocument(
        trackId = trackId,
        lines = decodedLines,
        isSynced = isSynced,
        hasWordTiming = hasWordTiming,
        source = source
    )
}

fun LyricsDocument.toEntity(updatedAtMs: Long = System.currentTimeMillis()): LyricsEntity = LyricsEntity(
    trackId = trackId,
    linesJson = databaseJson.encodeToString(lines),
    isSynced = isSynced,
    hasWordTiming = hasWordTiming,
    source = source,
    updatedAtMs = updatedAtMs
)

fun DownloadEntity.toModel(): DownloadItem = DownloadItem(
    trackId = trackId,
    status = status,
    progress = progress,
    localFilePath = localFilePath,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes
)

fun DownloadItem.toEntity(updatedAtMs: Long = System.currentTimeMillis()): DownloadEntity = DownloadEntity(
    trackId = trackId,
    status = status,
    progress = progress.coerceIn(0f, 1f),
    localFilePath = localFilePath,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    updatedAtMs = updatedAtMs
)

fun encodeEqConfig(eqConfig: EqConfig): String = databaseJson.encodeToString(eqConfig)

fun decodeEqConfig(raw: String?): EqConfig {
    if (raw.isNullOrBlank()) return EqConfig()
    return runCatching { databaseJson.decodeFromString<EqConfig>(raw) }.getOrDefault(EqConfig())
}
