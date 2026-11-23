package com.onda.music.core.transfer

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransferModule {

    @Provides
    @Singleton
    fun provideMomiTransferParser(): MomiTransferParser = MomiTransferParser()

    @Provides
    @Singleton
    fun provideSqliteOpener(): SqliteOpener = AndroidSqliteOpener()

    @Provides
    @Singleton
    fun provideOndaArchiveCodec(parser: MomiTransferParser): OndaArchiveCodec =
        OndaArchiveCodec(parser = parser)
}
