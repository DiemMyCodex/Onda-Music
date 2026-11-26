package com.onda.music.core.lyrics

import com.onda.music.core.database.LyricsDao
import com.onda.music.core.database.LyricsEntity
import com.onda.music.core.database.toEntity
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import com.onda.music.core.sources.SourcesModule
import com.onda.music.core.sources.YtMusicClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRepositoryTest {

    @Test
    fun getLyricsCacheHitPreservesOriginalSource() {
        runTest {
            val dao = FakeLyricsDao()
            val track = Track(
                id = "track_cache_1",
                title = "Midnight",
                artist = "Onda",
                source = SourceKind.YOUTUBE
            )
            val cachedDoc = LyricsDocument(
                trackId = track.id,
                lines = listOf(LyricsLine(startTimeMs = 1000L, endTimeMs = 4000L, text = "Hello world")),
                isSynced = true,
                hasWordTiming = false,
                source = LyricsSource.BETTER_LYRICS
            )
            dao.upsert(cachedDoc.toEntity(updatedAtMs = 100L))

            val sourcesJson = SourcesModule.provideSourcesJson()
            val okHttp = SourcesModule.provideSourcesOkHttp()
            val sourcesHttpClient = SourcesModule.provideSourcesHttpClient(okHttp, sourcesJson)
            val lyricsJson = LyricsModule.provideLyricsJson()
            val lyricsHttpClient = LyricsModule.provideLyricsHttpClient(lyricsJson)
            try {
                val repo = LyricsRepository(
                    lyricsDao = dao,
                    ytMusicClient = YtMusicClient(sourcesHttpClient, okHttp, sourcesJson),
                    lyricsParser = LyricsParser(),
                    httpClient = lyricsHttpClient,
                    json = lyricsJson
                )
                val result = repo.getLyrics(
                    track = track,
                    forceRefresh = false,
                    onDemandTranslate = false
                )
                assertTrue(result.isSuccess)
                assertEquals(LyricsSource.BETTER_LYRICS, result.getOrThrow().source)
                assertEquals(1, result.getOrThrow().lines.size)
            } finally {
                sourcesHttpClient.close()
                lyricsHttpClient.close()
            }
        }
    }

    @Test
    fun translateDocumentLinesChunksByTwelveAndSkipsInterludes() {
        runTest {
            val lines = mutableListOf<LyricsLine>()
            lines.add(LyricsLine(0L, 1000L, "", isInterlude = true))
            lines.add(LyricsLine(1000L, 2000L, "Already translated", translatedText = "Da dich"))
            for (i in 1..15) {
                lines.add(LyricsLine(i * 2000L, (i + 1) * 2000L, "Line $i"))
            }
            lines.add(LyricsLine(40_000L, 45_000L, "", isInterlude = true))

            val doc = LyricsDocument(
                trackId = "t_chunk",
                lines = lines,
                isSynced = true,
                source = LyricsSource.SIMP_MUSIC
            )

            val batchLineCounts = mutableListOf<Int>()
            val translated = LyricsRepository.translateDocumentLines(doc, "vi") { text, targetLang ->
                assertEquals("vi", targetLang)
                val inputLines = text.lines()
                batchLineCounts.add(inputLines.size)
                inputLines.joinToString("\n") { "VI:$it" }
            }

            assertEquals(listOf(12, 3), batchLineCounts)
            assertEquals(LyricsSource.SIMP_MUSIC, translated.source)
            assertNull(translated.lines[0].translatedText)
            assertEquals("Da dich", translated.lines[1].translatedText)
            assertEquals("VI:Line 1", translated.lines[2].translatedText)
            assertEquals("VI:Line 15", translated.lines[16].translatedText)
            assertNull(translated.lines[17].translatedText)
        }
    }

    @Test
    fun translateDocumentLinesHandlesTrailingNewlineWithoutFallback() {
        runTest {
            val doc = LyricsDocument(
                trackId = "t_trailing",
                lines = listOf(
                    LyricsLine(0L, 1000L, "First"),
                    LyricsLine(1000L, 2000L, "Second")
                ),
                isSynced = true,
                source = LyricsSource.LRCLIB
            )
            var calls = 0
            val translated = LyricsRepository.translateDocumentLines(doc, "vi") { _, _ ->
                calls += 1
                "Dau tien\nThu hai\n"
            }
            assertEquals(1, calls)
            assertEquals("Dau tien", translated.lines[0].translatedText)
            assertEquals("Thu hai", translated.lines[1].translatedText)
        }
    }

    @Test
    fun translateDocumentLinesFallsBackPerLineOnLineCountMismatch() {
        runTest {
            val doc = LyricsDocument(
                trackId = "t_fallback",
                lines = listOf(
                    LyricsLine(0L, 1000L, "Alpha"),
                    LyricsLine(1000L, 2000L, "Beta"),
                    LyricsLine(2000L, 3000L, "Gamma"),
                    LyricsLine(3000L, 4000L, "Delta")
                ),
                isSynced = true,
                source = LyricsSource.NETEASE
            )
            val callInputs = mutableListOf<String>()
            val translated = LyricsRepository.translateDocumentLines(doc, "en") { text, targetLang ->
                assertEquals("en", targetLang)
                callInputs.add(text)
                when (text) {
                    "Alpha\nBeta\nGamma\nDelta" -> "Merged line one\nMerged line two"
                    "Alpha" -> "Alpha EN"
                    "Beta" -> null
                    "Gamma" -> "Gamma EN"
                    "Delta" -> "Delta EN"
                    else -> null
                }
            }

            assertEquals(5, callInputs.size)
            assertEquals("Alpha EN", translated.lines[0].translatedText)
            assertNull(translated.lines[1].translatedText)
            assertEquals("Gamma EN", translated.lines[2].translatedText)
            assertEquals("Delta EN", translated.lines[3].translatedText)
        }
    }

    @Test
    fun translateDocumentLinesSkipsBlankAndIdenticalTranslations() {
        runTest {
            val doc = LyricsDocument(
                trackId = "t_skip",
                lines = listOf(
                    LyricsLine(0L, 1000L, "Same"),
                    LyricsLine(1000L, 2000L, "Blank"),
                    LyricsLine(2000L, 3000L, "Valid")
                ),
                isSynced = true,
                source = LyricsSource.LRCLIB
            )
            val translated = LyricsRepository.translateDocumentLines(doc, "vi") { _, _ ->
                "same\n   \nHop le"
            }
            assertNull(translated.lines[0].translatedText)
            assertNull(translated.lines[1].translatedText)
            assertEquals("Hop le", translated.lines[2].translatedText)
        }
    }

    @Test
    fun userAgentMatchesVersion131() {
        assertEquals(
            "OndaMusic/1.3.1 (+https://github.com/DiemMyCodex/Onda-Music)",
            LyricsRepository.ONDA_LYRICS_USER_AGENT
        )
    }

    private class FakeLyricsDao : LyricsDao {
        private val items = LinkedHashMap<String, LyricsEntity>()

        override fun observeByTrackId(trackId: String): Flow<LyricsEntity?> = flowOf(items[trackId])

        override suspend fun getByTrackId(trackId: String): LyricsEntity? = items[trackId]

        override suspend fun getAll(): List<LyricsEntity> = items.values.toList()

        override suspend fun upsert(lyrics: LyricsEntity) {
            items[lyrics.trackId] = lyrics
        }

        override suspend fun upsertAll(items: List<LyricsEntity>) {
            items.forEach { this.items[it.trackId] = it }
        }

        override suspend fun deleteByTrackId(trackId: String) {
            items.remove(trackId)
        }

        override suspend fun deleteAll() {
            items.clear()
        }
    }
}
