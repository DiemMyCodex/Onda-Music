package com.onda.music.core.playback

import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.StreamOption
import com.onda.music.core.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverTest {

    private class FakeLocalChecker(
        private val readableUris: Set<String> = emptySet()
    ) : LocalFileChecker {
        var calls = 0
        override suspend fun isReadable(uriString: String): Boolean {
            calls++
            return uriString in readableUris
        }
    }

    private class FakeDownloadedLookup(
        private val items: Map<String, StreamOption> = emptyMap()
    ) : DownloadedCacheLookup {
        var calls = 0
        override suspend fun findDownloaded(trackId: String): StreamOption? {
            calls++
            return items[trackId]
        }
    }

    private class FakeMonochromeSource(
        private val resolver: suspend (Track) -> StreamOption? = { null }
    ) : MonochromeStreamSource {
        var calls = 0
        override suspend fun resolveStream(track: Track): StreamOption? {
            calls++
            return resolver(track)
        }
    }

    private class FakeYtInnerTubeSource(
        private val handler: suspend (videoId: String, rotationCount: Int) -> StreamOption? = { _, _ -> null }
    ) : YtInnerTubeStreamSource {
        var resolveCalls = 0
        var rotateCalls = 0

        override suspend fun resolveStream(videoId: String): StreamOption? {
            resolveCalls++
            return handler(videoId, rotateCalls)
        }

        override suspend fun rotateClient(): String {
            rotateCalls++
            return "CLIENT_$rotateCalls"
        }
    }

    private class FakeNewPipeSource(
        private val handler: suspend (String) -> StreamOption? = { null }
    ) : NewPipeStreamSource {
        var calls = 0
        override suspend fun resolveStream(videoId: String): StreamOption? {
            calls++
            return handler(videoId)
        }
    }

    private class FakePrefs(var monochromeEnabled: Boolean = true) : StreamPreferencesProvider {
        override suspend fun isMonochromeEnabled(): Boolean = monochromeEnabled
    }

    private fun option(
        url: String,
        source: SourceKind = SourceKind.YOUTUBE,
        expiresAtMs: Long? = null
    ) = StreamOption(
        url = url,
        mimeType = "audio/mp4",
        bitrateKbps = 256,
        codec = "aac",
        qualityLabel = "High",
        expiresAtMs = expiresAtMs,
        source = source
    )

    @Test
    fun deterministicPriorityLocalWinsFirst() = runTest {
        val local = FakeLocalChecker(setOf("file:///music/song.flac"))
        val downloaded = FakeDownloadedLookup(mapOf("t1" to option("file:///cache/song.m4a", SourceKind.LOCAL)))
        val monochrome = FakeMonochromeSource { option("https://mono/1.flac", SourceKind.MONOCHROME) }
        val yt = FakeYtInnerTubeSource { _, _ -> option("https://yt/1") }
        val newPipe = FakeNewPipeSource { option("https://np/1") }

        val resolver = StreamResolver(
            localFileChecker = local,
            downloadedCacheLookup = downloaded,
            monochromeSource = monochrome,
            ytInnerTubeSource = yt,
            newPipeSource = newPipe,
            preferencesProvider = FakePrefs(true)
        )

        val track = Track(
            id = "t1",
            title = "Song",
            artist = "Artist",
            source = SourceKind.MONOCHROME,
            localUri = "file:///music/song.flac",
            ytId = "yt1",
            monochromeId = "m1"
        )

        val result = resolver.resolve(track)
        assertTrue(result.isSuccess)
        assertEquals("file:///music/song.flac", result.getOrThrow().url)
        assertEquals(0, downloaded.calls)
        assertEquals(0, monochrome.calls)
        assertEquals(0, yt.resolveCalls)
        assertEquals(0, newPipe.calls)
    }

    @Test
    fun deterministicPriorityDownloadedThenMonochromeThenYtThenNewPipe() = runTest {
        val downloaded = FakeDownloadedLookup(mapOf("d1" to option("file:///dl/d1.m4a", SourceKind.LOCAL)))
        val monochrome = FakeMonochromeSource { option("https://mono/flac", SourceKind.MONOCHROME) }
        val yt = FakeYtInnerTubeSource { id, _ ->
            if (id == "yt_fail") throw IllegalStateException("yt down") else option("https://yt/$id")
        }
        val newPipe = FakeNewPipeSource { id -> option("https://np/$id") }
        val prefs = FakePrefs(monochromeEnabled = true)

        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = downloaded,
            monochromeSource = monochrome,
            ytInnerTubeSource = yt,
            newPipeSource = newPipe,
            preferencesProvider = prefs
        )

        val dlResult = resolver.resolve(
            Track(id = "d1", title = "D", artist = "A", ytId = "yt_ok", monochromeId = "m1")
        )
        assertEquals("file:///dl/d1.m4a", dlResult.getOrThrow().url)

        val monoTrack = Track(
            id = "m1",
            title = "M",
            artist = "A",
            source = SourceKind.MONOCHROME,
            monochromeId = "m1",
            ytId = "yt_ok"
        )
        val monoResult = resolver.resolve(monoTrack)
        assertEquals("https://mono/flac", monoResult.getOrThrow().url)

        prefs.monochromeEnabled = false
        val ytTrack = Track(
            id = "y1",
            title = "Y",
            artist = "A",
            source = SourceKind.MONOCHROME,
            monochromeId = "m1",
            ytId = "yt_ok"
        )
        val ytResult = resolver.resolve(ytTrack)
        assertEquals("https://yt/yt_ok", ytResult.getOrThrow().url)

        val fallbackTrack = Track(id = "y2", title = "F", artist = "A", ytId = "yt_fail")
        val npResult = resolver.resolve(fallbackTrack)
        assertEquals("https://np/yt_fail", npResult.getOrThrow().url)
    }

    @Test
    fun cacheHitAndExpiryBehavior() = runTest {
        var now = 1_000L
        val yt = FakeYtInnerTubeSource { id, _ ->
            option("https://yt/$id?t=$now", expiresAtMs = now + 5_000L)
        }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = FakeMonochromeSource(),
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(false),
            clockMs = { now }
        )

        val track = Track(id = "t_cache", title = "C", artist = "A", ytId = "vid1")

        val first = resolver.resolve(track).getOrThrow()
        assertEquals(1, yt.resolveCalls)
        assertEquals("https://yt/vid1?t=1000", first.url)

        now = 4_000L
        val second = resolver.resolve(track).getOrThrow()
        assertEquals(1, yt.resolveCalls)
        assertEquals("https://yt/vid1?t=1000", second.url)

        now = 6_500L
        val third = resolver.resolve(track).getOrThrow()
        assertEquals(2, yt.resolveCalls)
        assertEquals("https://yt/vid1?t=6500", third.url)
    }

    @Test
    fun http403DuringResolutionRotatesClientOnce() = runTest {
        val yt = FakeYtInnerTubeSource { id, rotationCount ->
            if (rotationCount == 0) {
                throw StreamHttpException(403, "Forbidden")
            }
            option("https://yt/$id?client=$rotationCount")
        }
        val newPipe = FakeNewPipeSource { option("https://np/unused") }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = FakeMonochromeSource(),
            ytInnerTubeSource = yt,
            newPipeSource = newPipe,
            preferencesProvider = FakePrefs(false)
        )

        val track = Track(id = "t403", title = "403", artist = "A", ytId = "vid403")
        val result = resolver.resolve(track)

        assertTrue(result.isSuccess)
        assertEquals("https://yt/vid403?client=1", result.getOrThrow().url)
        assertEquals(1, yt.rotateCalls)
        assertEquals(2, yt.resolveCalls)
        assertEquals(0, newPipe.calls)
    }

    @Test
    fun playbackHttp403And410InvalidatesCacheAndRotatesOnceBeforeFailingCleanly() = runTest {
        var now = 10_000L
        val yt = FakeYtInnerTubeSource { id, rotationCount ->
            when (rotationCount) {
                0 -> option("https://yt/$id/stale", expiresAtMs = now + 60_000L)
                1 -> option("https://yt/$id/rotated", expiresAtMs = now + 60_000L)
                else -> option("https://yt/$id/unexpected")
            }
        }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = FakeMonochromeSource(),
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(false),
            clockMs = { now }
        )

        val track = Track(id = "t_play_410", title = "410", artist = "A", ytId = "vid410")
        val initial = resolver.resolve(track).getOrThrow()
        assertEquals("https://yt/vid410/stale", initial.url)
        assertEquals(1, resolver.cachedSize())

        val refreshed = resolver.handlePlaybackHttpError(track, 410)
        assertTrue(refreshed.isSuccess)
        assertEquals("https://yt/vid410/rotated", refreshed.getOrThrow().url)
        assertEquals(1, yt.rotateCalls)

        val secondError = resolver.handlePlaybackHttpError(track, 403)
        assertTrue(secondError.isFailure)
        assertEquals(1, yt.rotateCalls)
        assertEquals(0, resolver.cachedSize())
    }

    @Test
    fun monochromeFailureFallsThroughToYtInnerTube() = runTest {
        val monochrome = FakeMonochromeSource { throw IllegalStateException("No FLAC match") }
        val yt = FakeYtInnerTubeSource { id, _ -> option("https://yt/$id") }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = monochrome,
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(monochromeEnabled = true)
        )

        val track = Track(id = "yt_upgrade", title = "Song", artist = "Artist", ytId = "vid_up")
        val result = resolver.resolve(track)

        assertTrue(result.isSuccess)
        assertEquals("https://yt/vid_up", result.getOrThrow().url)
        assertEquals(1, monochrome.calls)
        assertEquals(1, yt.resolveCalls)
    }

    @Test
    fun slowMonochromeDoesNotBlockYoutubePlayback() = runTest {
        val monochrome = FakeMonochromeSource {
            delay(2_500L)
            option("https://mono/too_late.flac", SourceKind.MONOCHROME)
        }
        val yt = FakeYtInnerTubeSource { id, _ ->
            delay(20L)
            option("https://yt/$id")
        }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = monochrome,
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(monochromeEnabled = true),
            monochromeUpgradeTimeoutMs = 60L
        )

        val track = Track(id = "yt_fast", title = "Song", artist = "Artist", ytId = "vid_fast")
        val result = resolver.resolve(track)

        assertTrue(result.isSuccess)
        assertEquals("https://yt/vid_fast", result.getOrThrow().url)
        assertEquals(1, yt.resolveCalls)
    }

    @Test
    fun concurrentResolveAndPrefetchSharesSingleUpstreamCall() = runTest {
        val yt = FakeYtInnerTubeSource { id, _ ->
            delay(50L)
            option("https://yt/$id")
        }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = FakeMonochromeSource(),
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(monochromeEnabled = false)
        )

        val track = Track(id = "yt_dedup", title = "Song", artist = "Artist", ytId = "vid_dedup")
        val first = async { resolver.prefetch(track) }
        val second = async { resolver.resolve(track) }
        first.await()
        val resolved = second.await()

        assertTrue(resolved.isSuccess)
        assertEquals("https://yt/vid_dedup", resolved.getOrThrow().url)
        assertEquals(1, yt.resolveCalls)
    }

    @Test
    fun fastYoutubeUsesShortGraceAndCancelsSlowMonochrome() = runTest {
        val monochrome = FakeMonochromeSource {
            delay(2_500L)
            option("https://mono/too_late.flac", SourceKind.MONOCHROME)
        }
        val yt = FakeYtInnerTubeSource { id, _ ->
            delay(20L)
            option("https://yt/$id")
        }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = monochrome,
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(monochromeEnabled = true)
        )

        val track = Track(id = "yt_fast_default", title = "Song", artist = "Artist", ytId = "vid_fast_default")
        val result = resolver.resolve(track)

        assertTrue(result.isSuccess)
        assertEquals("https://yt/vid_fast_default", result.getOrThrow().url)
        assertEquals(1, monochrome.calls)
        assertEquals(1, yt.resolveCalls)
    }

    @Test
    fun fastMonochromeWinsRaceBeforeYoutube() = runTest {
        val monochrome = FakeMonochromeSource {
            delay(20L)
            option("https://mono/fast.flac", SourceKind.MONOCHROME)
        }
        val yt = FakeYtInnerTubeSource { id, _ ->
            delay(200L)
            option("https://yt/$id")
        }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = monochrome,
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(monochromeEnabled = true)
        )

        val track = Track(id = "mono_wins", title = "Song", artist = "Artist", ytId = "vid_slow")
        val result = resolver.resolve(track)

        assertTrue(result.isSuccess)
        assertEquals("https://mono/fast.flac", result.getOrThrow().url)
        assertEquals(SourceKind.MONOCHROME, result.getOrThrow().source)
    }

    @Test
    fun monochromePlaybackFailureFallsBackToYoutubeOnce() = runTest {
        val monochrome = FakeMonochromeSource {
            option("https://mono/stalled.flac", SourceKind.MONOCHROME)
        }
        val yt = FakeYtInnerTubeSource { id, _ ->
            option("https://yt/$id")
        }
        val resolver = StreamResolver(
            localFileChecker = FakeLocalChecker(),
            downloadedCacheLookup = FakeDownloadedLookup(),
            monochromeSource = monochrome,
            ytInnerTubeSource = yt,
            newPipeSource = FakeNewPipeSource(),
            preferencesProvider = FakePrefs(monochromeEnabled = true)
        )

        val track = Track(id = "mono_failover", title = "Song", artist = "Artist", ytId = "vid_fallback")
        val initial = resolver.resolve(track)
        assertEquals("https://mono/stalled.flac", initial.getOrThrow().url)
        assertEquals(1, monochrome.calls)
        val ytCallsBeforeFallback = yt.resolveCalls

        val fallback = resolver.handleMonochromePlaybackFailure(track)
        assertTrue(fallback.isSuccess)
        assertEquals("https://yt/vid_fallback", fallback.getOrThrow().url)
        assertEquals(SourceKind.YOUTUBE, fallback.getOrThrow().source)
        assertEquals(1, monochrome.calls)
        assertEquals(ytCallsBeforeFallback + 1, yt.resolveCalls)

        val secondAttempt = resolver.handleMonochromePlaybackFailure(track)
        assertTrue(secondAttempt.isFailure)
        assertEquals(ytCallsBeforeFallback + 1, yt.resolveCalls)
    }
}
