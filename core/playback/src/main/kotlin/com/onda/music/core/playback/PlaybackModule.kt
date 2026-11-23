package com.onda.music.core.playback

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindLocalFileChecker(impl: AndroidLocalFileChecker): LocalFileChecker

    @Binds
    @Singleton
    abstract fun bindDownloadedCacheLookup(impl: RoomDownloadedCacheLookup): DownloadedCacheLookup

    @Binds
    @Singleton
    abstract fun bindMonochromeStreamSource(impl: RealMonochromeStreamSource): MonochromeStreamSource

    @Binds
    @Singleton
    abstract fun bindYtInnerTubeStreamSource(impl: RealYtInnerTubeStreamSource): YtInnerTubeStreamSource

    @Binds
    @Singleton
    abstract fun bindNewPipeStreamSource(impl: RealNewPipeStreamSource): NewPipeStreamSource

    @Binds
    @Singleton
    abstract fun bindStreamPreferencesProvider(impl: OndaStreamPreferencesProvider): StreamPreferencesProvider

    companion object {
        @Provides
        @Singleton
        fun provideStreamResolver(
            localFileChecker: LocalFileChecker,
            downloadedCacheLookup: DownloadedCacheLookup,
            monochromeSource: MonochromeStreamSource,
            ytInnerTubeSource: YtInnerTubeStreamSource,
            newPipeSource: NewPipeStreamSource,
            preferencesProvider: StreamPreferencesProvider
        ): StreamResolver {
            return StreamResolver(
                localFileChecker = localFileChecker,
                downloadedCacheLookup = downloadedCacheLookup,
                monochromeSource = monochromeSource,
                ytInnerTubeSource = ytInnerTubeSource,
                newPipeSource = newPipeSource,
                preferencesProvider = preferencesProvider
            )
        }
    }
}
