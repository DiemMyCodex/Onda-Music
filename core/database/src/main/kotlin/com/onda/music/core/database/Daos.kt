package com.onda.music.core.database

import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.onda.music.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY addedAtMs DESC, title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE inLibrary = 1 ORDER BY addedAtMs DESC, title ASC")
    fun observeLibrarySongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY addedAtMs DESC, title ASC")
    fun observeLikedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE source = 'LOCAL' ORDER BY title ASC")
    fun observeLocalSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE needsRelink = 1 ORDER BY title ASC")
    fun observeSongsNeedingRelink(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeById(id: String): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE ytId = :ytId LIMIT 1")
    suspend fun getByYtId(ytId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE monochromeId = :monochromeId LIMIT 1")
    suspend fun getByMonochromeId(monochromeId: String): SongEntity?

    @Query("SELECT * FROM songs ORDER BY addedAtMs DESC, title ASC")
    suspend fun getAll(): List<SongEntity>

    @Upsert
    suspend fun upsert(song: SongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Transaction
    suspend fun upsertAndMerge(song: SongEntity) {
        val existing = getById(song.id)
        if (existing == null) {
            upsert(song)
        } else {
            upsert(mergeSong(existing, song))
        }
    }

    @Transaction
    suspend fun upsertAndMergeAll(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val existingById = getAll().associateBy { it.id }
        val merged = songs.map { incoming ->
            val existing = existingById[incoming.id]
            if (existing == null) incoming else mergeSong(existing, incoming)
        }
        upsertAll(merged)
    }

    @Transaction
    suspend fun replaceAll(songs: List<SongEntity>) {
        deleteAll()
        if (songs.isNotEmpty()) {
            upsertAll(songs)
        }
    }

    @Query("UPDATE songs SET liked = :liked WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean)

    @Query("UPDATE songs SET inLibrary = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(id: String, inLibrary: Boolean)

    @Query("UPDATE songs SET totalPlayTimeMs = totalPlayTimeMs + :deltaMs WHERE id = :id")
    suspend fun addPlayTime(id: String, deltaMs: Long)

    @Query("UPDATE songs SET lyricsOffsetMs = :offsetMs WHERE id = :id")
    suspend fun setLyricsOffset(id: String, offsetMs: Long)

    @Query("UPDATE songs SET localUri = :localUri, needsRelink = 0 WHERE id = :id")
    suspend fun relinkLocalUri(id: String, localUri: String)

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, artworkUrl = COALESCE(:artworkUrl, artworkUrl) WHERE id = :id")
    suspend fun updateTrackMetadata(
        id: String,
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?
    )

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}

private fun mergeSong(existing: SongEntity, incoming: SongEntity): SongEntity {
    val mergedLocalUri = existing.localUri ?: incoming.localUri
    val mergedAddedAt = when {
        existing.addedAtMs > 0L && incoming.addedAtMs > 0L -> minOf(existing.addedAtMs, incoming.addedAtMs)
        existing.addedAtMs > 0L -> existing.addedAtMs
        else -> incoming.addedAtMs
    }
    return existing.copy(
        title = existing.title.ifBlank { incoming.title },
        artist = existing.artist.ifBlank { incoming.artist },
        album = existing.album ?: incoming.album,
        albumId = existing.albumId ?: incoming.albumId,
        artistId = existing.artistId ?: incoming.artistId,
        durationMs = if (existing.durationMs > 0L) existing.durationMs else incoming.durationMs,
        artworkUrl = existing.artworkUrl ?: incoming.artworkUrl,
        localUri = mergedLocalUri,
        ytId = existing.ytId ?: incoming.ytId,
        monochromeId = existing.monochromeId ?: incoming.monochromeId,
        liked = existing.liked || incoming.liked,
        inLibrary = existing.inLibrary || incoming.inLibrary,
        totalPlayTimeMs = maxOf(existing.totalPlayTimeMs, incoming.totalPlayTimeMs),
        lyricsOffsetMs = if (existing.lyricsOffsetMs != 0L) existing.lyricsOffsetMs else incoming.lyricsOffsetMs,
        needsRelink = existing.needsRelink && mergedLocalUri == null,
        addedAtMs = mergedAddedAt
    )
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeById(id: String): Flow<ArtistEntity?>

    @Query("SELECT s.* FROM songs s INNER JOIN song_artist_cross_ref r ON s.id = r.songId WHERE r.artistId = :artistId ORDER BY r.position ASC, s.title ASC")
    fun observeSongsForArtist(artistId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists ORDER BY name ASC")
    suspend fun getAll(): List<ArtistEntity>

    @Query("SELECT * FROM song_artist_cross_ref WHERE songId = :songId ORDER BY position ASC")
    suspend fun getCrossRefsForSong(songId: String): List<SongArtistCrossRef>

    @Query("SELECT * FROM song_artist_cross_ref")
    suspend fun getAllCrossRefs(): List<SongArtistCrossRef>

    @Upsert
    suspend fun upsert(artist: ArtistEntity)

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Upsert
    suspend fun upsertCrossRefs(crossRefs: List<SongArtistCrossRef>)

    @Transaction
    suspend fun upsertAndMergeAll(artists: List<ArtistEntity>, crossRefs: List<SongArtistCrossRef> = emptyList()) {
        if (artists.isNotEmpty()) {
            val existingById = getAll().associateBy { it.id }
            val merged = artists.map { incoming ->
                val existing = existingById[incoming.id]
                if (existing == null) {
                    incoming
                } else {
                    existing.copy(
                        name = existing.name.ifBlank { incoming.name },
                        thumbnailUrl = existing.thumbnailUrl ?: incoming.thumbnailUrl,
                        trackCount = maxOf(existing.trackCount, incoming.trackCount)
                    )
                }
            }
            upsertAll(merged)
        }
        if (crossRefs.isNotEmpty()) {
            upsertCrossRefs(crossRefs)
        }
    }

    @Transaction
    suspend fun replaceAll(artists: List<ArtistEntity>, crossRefs: List<SongArtistCrossRef> = emptyList()) {
        deleteAllCrossRefs()
        deleteAll()
        if (artists.isNotEmpty()) {
            upsertAll(artists)
        }
        if (crossRefs.isNotEmpty()) {
            upsertCrossRefs(crossRefs)
        }
    }

    @Query("DELETE FROM song_artist_cross_ref")
    suspend fun deleteAllCrossRefs()

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeById(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY title ASC")
    fun observeSongsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums ORDER BY title ASC")
    suspend fun getAll(): List<AlbumEntity>

    @Upsert
    suspend fun upsert(album: AlbumEntity)

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Transaction
    suspend fun upsertAndMergeAll(albums: List<AlbumEntity>) {
        if (albums.isEmpty()) return
        val existingById = getAll().associateBy { it.id }
        val merged = albums.map { incoming ->
            val existing = existingById[incoming.id]
            if (existing == null) {
                incoming
            } else {
                existing.copy(
                    title = existing.title.ifBlank { incoming.title },
                    artist = existing.artist.ifBlank { incoming.artist },
                    artistId = existing.artistId ?: incoming.artistId,
                    year = existing.year ?: incoming.year,
                    artworkUrl = existing.artworkUrl ?: incoming.artworkUrl,
                    trackCount = maxOf(existing.trackCount, incoming.trackCount)
                )
            }
        }
        upsertAll(merged)
    }

    @Transaction
    suspend fun replaceAll(albums: List<AlbumEntity>) {
        deleteAll()
        if (albums.isNotEmpty()) {
            upsertAll(albums)
        }
    }

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAtMs DESC, name ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: String): Flow<PlaylistEntity?>

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC, ps.addedAtMs ASC")
    fun observeSongsInPlaylist(playlistId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observePlaylistEntries(playlistId: String): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAtMs DESC, name ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistSongs(playlistId: String): List<PlaylistSongEntity>

    @Query("SELECT * FROM playlist_songs")
    suspend fun getAllPlaylistSongs(): List<PlaylistSongEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Upsert
    suspend fun upsertPlaylistSongs(entries: List<PlaylistSongEntity>)

    @Transaction
    suspend fun addSongToPlaylist(
        playlistId: String,
        songId: String,
        addedAtMs: Long = System.currentTimeMillis()
    ) {
        val current = getPlaylistSongs(playlistId)
        if (current.any { it.songId == songId }) return
        val nextPos = (current.maxOfOrNull { it.position } ?: -1) + 1
        upsertPlaylistSongs(
            listOf(
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = songId,
                    position = nextPos,
                    addedAtMs = addedAtMs
                )
            )
        )
        refreshTrackCount(playlistId)
    }

    @Transaction
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        deletePlaylistSong(playlistId, songId)
        refreshTrackCount(playlistId)
    }

    @Transaction
    suspend fun upsertAndMergeAll(playlists: List<PlaylistEntity>, entries: List<PlaylistSongEntity>) {
        if (playlists.isNotEmpty()) {
            val existingById = getAll().associateBy { it.id }
            val merged = playlists.map { incoming ->
                val existing = existingById[incoming.id]
                if (existing == null) {
                    incoming
                } else {
                    existing.copy(
                        name = existing.name.ifBlank { incoming.name },
                        description = existing.description ?: incoming.description,
                        artworkUrl = existing.artworkUrl ?: incoming.artworkUrl,
                        trackCount = maxOf(existing.trackCount, incoming.trackCount),
                        remoteId = existing.remoteId ?: incoming.remoteId
                    )
                }
            }
            upsertAll(merged)
        }
        if (entries.isNotEmpty()) {
            upsertPlaylistSongs(entries)
        }
        playlists.forEach { refreshTrackCount(it.id) }
    }

    @Transaction
    suspend fun replaceAll(playlists: List<PlaylistEntity>, entries: List<PlaylistSongEntity>) {
        deleteAllPlaylistSongs()
        deleteAllPlaylists()
        if (playlists.isNotEmpty()) {
            upsertAll(playlists)
        }
        if (entries.isNotEmpty()) {
            upsertPlaylistSongs(entries)
        }
        playlists.forEach { refreshTrackCount(it.id) }
    }

    @Transaction
    suspend fun deletePlaylistById(playlistId: String) {
        deleteSongsForPlaylist(playlistId)
        deletePlaylistEntity(playlistId)
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: String, songId: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongsForPlaylist(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistEntity(playlistId: String)

    @Query("UPDATE playlists SET trackCount = (SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId) WHERE id = :playlistId")
    suspend fun refreshTrackCount(playlistId: String)

    @Query("DELETE FROM playlist_songs")
    suspend fun deleteAllPlaylistSongs()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}

@Immutable
data class TrackPlayStatRow(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val playCount: Int,
    val totalDurationMs: Long
)

@Immutable
data class ArtistPlayStatRow(
    val artist: String,
    val playCount: Int,
    val totalDurationMs: Long
)

@Dao
interface PlayEventDao {
    @Query("SELECT * FROM play_events ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<PlayEventEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN play_events pe ON s.id = pe.trackId GROUP BY s.id ORDER BY MAX(pe.timestampMs) DESC LIMIT :limit")
    fun observeRecentSongs(limit: Int = 30): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM play_events")
    fun observeTotalPlayCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationPlayedMs), 0) FROM play_events")
    fun observeTotalPlayDurationMs(): Flow<Long>

    @Query("SELECT COUNT(*) FROM play_events WHERE timestampMs >= :sinceMs")
    fun observePlayCountSince(sinceMs: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationPlayedMs), 0) FROM play_events WHERE timestampMs >= :sinceMs")
    fun observePlayDurationSince(sinceMs: Long): Flow<Long>

    @Query(
        "SELECT s.id AS trackId, s.title AS title, s.artist AS artist, s.artworkUrl AS artworkUrl, " +
            "COUNT(pe.id) AS playCount, COALESCE(SUM(pe.durationPlayedMs), 0) AS totalDurationMs " +
            "FROM play_events pe INNER JOIN songs s ON s.id = pe.trackId " +
            "WHERE pe.timestampMs >= :sinceMs " +
            "GROUP BY s.id ORDER BY totalDurationMs DESC, playCount DESC LIMIT :limit"
    )
    fun observeTopTracksSince(sinceMs: Long, limit: Int = 10): Flow<List<TrackPlayStatRow>>

    @Query(
        "SELECT s.artist AS artist, COUNT(pe.id) AS playCount, COALESCE(SUM(pe.durationPlayedMs), 0) AS totalDurationMs " +
            "FROM play_events pe INNER JOIN songs s ON s.id = pe.trackId " +
            "WHERE pe.timestampMs >= :sinceMs AND s.artist != '' " +
            "GROUP BY s.artist ORDER BY totalDurationMs DESC, playCount DESC LIMIT :limit"
    )
    fun observeTopArtistsSince(sinceMs: Long, limit: Int = 10): Flow<List<ArtistPlayStatRow>>

    @Query("SELECT * FROM play_events ORDER BY timestampMs DESC")
    suspend fun getAll(): List<PlayEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PlayEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PlayEventEntity>)

    @Transaction
    suspend fun upsertAndMergeAll(events: List<PlayEventEntity>) {
        if (events.isEmpty()) return
        val existingKeys = getAll().mapTo(HashSet()) { it.trackId to it.timestampMs }
        val toInsert = events
            .filter { (it.trackId to it.timestampMs) !in existingKeys }
            .map { it.copy(id = 0L) }
        if (toInsert.isNotEmpty()) {
            insertAll(toInsert)
        }
    }

    @Transaction
    suspend fun replaceAll(events: List<PlayEventEntity>) {
        deleteAll()
        if (events.isNotEmpty()) {
            insertAll(events.map { it.copy(id = 0L) })
        }
    }

    @Query("DELETE FROM play_events")
    suspend fun deleteAll()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searchedAtMs DESC")
    suspend fun getAll(): List<SearchHistoryEntity>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<SearchHistoryEntity>)

    @Transaction
    suspend fun upsertAndMergeAll(entries: List<SearchHistoryEntity>) {
        if (entries.isEmpty()) return
        val existingByQuery = getAll().associateBy { it.query }
        val merged = entries.map { incoming ->
            val existing = existingByQuery[incoming.query]
            if (existing == null) {
                incoming
            } else {
                existing.copy(searchedAtMs = maxOf(existing.searchedAtMs, incoming.searchedAtMs))
            }
        }
        upsertAll(merged)
    }

    @Transaction
    suspend fun replaceAll(entries: List<SearchHistoryEntity>) {
        deleteAll()
        if (entries.isNotEmpty()) {
            upsertAll(entries)
        }
    }

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    fun observeByTrackId(trackId: String): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: String): LyricsEntity?

    @Query("SELECT * FROM lyrics")
    suspend fun getAll(): List<LyricsEntity>

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    @Upsert
    suspend fun upsertAll(items: List<LyricsEntity>)

    @Transaction
    suspend fun upsertAndMergeAll(items: List<LyricsEntity>) {
        if (items.isEmpty()) return
        val existingById = getAll().associateBy { it.trackId }
        val merged = items.map { incoming ->
            val existing = existingById[incoming.trackId]
            when {
                existing == null -> incoming
                existing.linesJson.isBlank() || existing.linesJson == "[]" -> incoming
                incoming.hasWordTiming && !existing.hasWordTiming -> incoming
                else -> existing
            }
        }
        upsertAll(merged)
    }

    @Transaction
    suspend fun replaceAll(items: List<LyricsEntity>) {
        deleteAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)

    @Query("DELETE FROM lyrics")
    suspend fun deleteAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    fun observeByTrackId(trackId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY updatedAtMs DESC")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: String): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY updatedAtMs DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(item: DownloadEntity)

    @Upsert
    suspend fun upsertAll(items: List<DownloadEntity>)

    @Query("UPDATE downloads SET status = :status, progress = :progress, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, localFilePath = :localFilePath, updatedAtMs = :updatedAtMs WHERE trackId = :trackId")
    suspend fun updateProgress(
        trackId: String,
        status: DownloadStatus,
        progress: Float,
        bytesDownloaded: Long,
        totalBytes: Long,
        localFilePath: String?,
        updatedAtMs: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
