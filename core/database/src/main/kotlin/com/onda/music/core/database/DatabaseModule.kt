package com.onda.music.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.ondaPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onda_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideOndaDatabase(@ApplicationContext context: Context): OndaDatabase {
        return Room.databaseBuilder(
            context,
            OndaDatabase::class.java,
            "onda_music.db"
        ).build()
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.ondaPreferencesDataStore
    }

    @Provides
    fun provideSongDao(database: OndaDatabase): SongDao = database.songDao()

    @Provides
    fun provideArtistDao(database: OndaDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideAlbumDao(database: OndaDatabase): AlbumDao = database.albumDao()

    @Provides
    fun providePlaylistDao(database: OndaDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePlayEventDao(database: OndaDatabase): PlayEventDao = database.playEventDao()

    @Provides
    fun provideSearchHistoryDao(database: OndaDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideLyricsDao(database: OndaDatabase): LyricsDao = database.lyricsDao()

    @Provides
    fun provideDownloadDao(database: OndaDatabase): DownloadDao = database.downloadDao()
}
