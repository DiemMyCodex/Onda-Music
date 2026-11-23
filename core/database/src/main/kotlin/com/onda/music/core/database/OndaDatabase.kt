package com.onda.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        SongArtistCrossRef::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayEventEntity::class,
        SearchHistoryEntity::class,
        LyricsEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class OndaDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun downloadDao(): DownloadDao

    @Transaction
    open suspend fun clearLibraryData() {
        playlistDao().deleteAllPlaylistSongs()
        playlistDao().deleteAllPlaylists()
        artistDao().deleteAllCrossRefs()
        artistDao().deleteAll()
        albumDao().deleteAll()
        playEventDao().deleteAll()
        searchHistoryDao().deleteAll()
        lyricsDao().deleteAll()
        downloadDao().deleteAll()
        songDao().deleteAll()
    }
}
