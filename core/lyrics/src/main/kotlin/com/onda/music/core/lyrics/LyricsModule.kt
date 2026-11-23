package com.onda.music.core.lyrics

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LyricsJson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LyricsHttpClient

@Module
@InstallIn(SingletonComponent::class)
object LyricsModule {

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    @LyricsJson
    fun provideLyricsJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    @LyricsHttpClient
    fun provideLyricsHttpClient(
        @LyricsJson json: Json
    ): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 12_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }
}
