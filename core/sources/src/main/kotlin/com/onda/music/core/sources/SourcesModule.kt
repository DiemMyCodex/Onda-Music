package com.onda.music.core.sources

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SourcesJson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SourcesOkHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SourcesHttpClient

@Module
@InstallIn(SingletonComponent::class)
object SourcesModule {

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    @SourcesJson
    fun provideSourcesJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @SourcesOkHttp
    fun provideSourcesOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @SourcesHttpClient
    fun provideSourcesHttpClient(
        @SourcesOkHttp okHttpClient: OkHttpClient,
        @SourcesJson json: Json
    ): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        install(ContentEncoding) {
            gzip(0.9f)
            deflate(0.8f)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        engine {
            preconfigured = okHttpClient
        }
    }
}
