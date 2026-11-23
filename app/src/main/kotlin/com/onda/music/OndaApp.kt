package com.onda.music

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.onda.music.core.database.OndaPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class OndaApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var ondaPreferences: OndaPreferences

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val maxCacheMb = if (::ondaPreferences.isInitialized) {
            runCatching {
                runBlocking { ondaPreferences.currentSettings().maxCacheMb }
            }.getOrDefault(512)
        } else {
            512
        }

        return ImageLoader.Builder(this).apply {
            components {
                add(OkHttpNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            crossfade(250)
            allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            if (maxCacheMb <= 0) {
                diskCachePolicy(CachePolicy.DISABLED)
            } else {
                diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(maxCacheMb.toLong() * 1024L * 1024L)
                        .build()
                }
            }
        }.build()
    }
}
