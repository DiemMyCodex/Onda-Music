package com.onda.music.feature.home

import com.onda.music.core.model.Album
import com.onda.music.core.model.ContextualShelf
import com.onda.music.core.model.HomeShelf
import com.onda.music.core.model.ListenerTasteProfile
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Track
import com.onda.music.core.sources.YtMusicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoverFeedResult(
    val quickPicks: List<Track>,
    val contextualShelf: ContextualShelf?,
    val shelves: List<HomeShelf>,
    val chartShelves: List<HomeShelf>,
    val newReleases: List<Album>,
    val moodCategories: List<MoodCategory>,
    val allRemoteFailed: Boolean,
    val errorMessage: String?
)

@Singleton
class DiscoverRecommendationEngine @Inject constructor(
    private val ytMusicClient: YtMusicClient
) {
    private data class CachedFeed(
        val fetchedAtMs: Long,
        val result: DiscoverFeedResult
    )

    private val cache = ConcurrentHashMap<String, CachedFeed>()

    suspend fun fetchFeed(
        profile: ListenerTasteProfile,
        likedTrackIds: Set<String>,
        forceRefresh: Boolean = false
    ): DiscoverFeedResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cache[profile.tasteSignature]?.let { cached ->
                if (now - cached.fetchedAtMs < CACHE_TTL_MS) {
                    return@withContext syncLikedFlags(cached.result, likedTrackIds)
                }
            }
        }

        val region = profile.dominantRegionCode
        val language = profile.dominantLanguageTag
        val chosenSeeds = ListenerTasteAnalyzer.selectSeedSubset(profile, maxSeeds = 3)

        val computed = coroutineScope {
            val seedDeferreds = chosenSeeds.map { seed ->
                async {
                    ytMusicClient.getRelatedTracksForSeed(
                        videoId = seed.videoId,
                        countryOverride = region,
                        languageOverride = language
                    )
                }
            }
            val shelvesDeferred = async {
                ytMusicClient.getHomeShelves(
                    countryOverride = region,
                    languageOverride = language
                )
            }
            val chartsDeferred = async {
                ytMusicClient.getCharts(
                    countryOverride = region,
                    languageOverride = language
                )
            }
            val moodsDeferred = async {
                ytMusicClient.getMoodCategories(
                    countryOverride = region,
                    languageOverride = language
                )
            }
            val releasesDeferred = async {
                ytMusicClient.getNewReleases(
                    countryOverride = region,
                    languageOverride = language
                )
            }

            val seedResults = seedDeferreds.awaitAll()
            val shelvesResult = shelvesDeferred.await()
            val chartsResult = chartsDeferred.await()
            val moodsResult = moodsDeferred.await()
            val releasesResult = releasesDeferred.await()

            val rawShelves = shelvesResult.getOrDefault(emptyList())
            val charts = chartsResult.getOrDefault(emptyList())
            val moods = moodsResult.getOrDefault(emptyList())
            val releases = releasesResult.getOrDefault(emptyList())

            val allRemoteFailed = (seedResults.isEmpty() || seedResults.all { it.isFailure }) &&
                shelvesResult.isFailure &&
                chartsResult.isFailure &&
                moodsResult.isFailure &&
                releasesResult.isFailure

            val chartTitles = charts.mapTo(HashSet()) { it.title.lowercase() }
            val filteredShelves = rawShelves.filterNot { shelf ->
                shelf.id == "new_releases" || shelf.title.lowercase() in chartTitles
            }

            val regionalQuickShelf = filteredShelves.firstOrNull { shelf ->
                shelf.tracks.isNotEmpty() &&
                    (shelf.title.contains("quick", ignoreCase = true) ||
                        shelf.title.contains("pick", ignoreCase = true) ||
                        shelf.title.contains("nhanh", ignoreCase = true))
            } ?: filteredShelves.firstOrNull { it.tracks.isNotEmpty() }

            val seedTrackLists = seedResults.map { it.getOrDefault(emptyList()) }
            val candidatePool = mutableListOf<Pair<Track, Int>>()
            seedTrackLists.forEachIndexed { seedIdx, tracks ->
                tracks.forEach { track ->
                    candidatePool += track to seedIdx
                }
            }
            regionalQuickShelf?.tracks.orEmpty().forEach { track ->
                candidatePool += track to 3
            }
            charts.flatMap { it.tracks }.forEach { track ->
                candidatePool += track to 3
            }

            val rankedQuickPicks = ListenerTasteAnalyzer.rankCandidateTracks(
                candidates = candidatePool,
                profile = profile,
                likedTrackIds = likedTrackIds,
                maxPerArtist = 2,
                limit = 16
            )

            val primarySeed = chosenSeeds.firstOrNull()
            val primaryRelated = seedTrackLists.firstOrNull().orEmpty()
            val contextualShelf = if (profile.hasListeningHistory && primarySeed != null && primaryRelated.isNotEmpty()) {
                val seedExcluded = setOf(
                    primarySeed.trackId,
                    primarySeed.videoId,
                    "yt_${primarySeed.videoId}"
                )
                val rankedContextual = ListenerTasteAnalyzer.rankCandidateTracks(
                    candidates = primaryRelated.map { it to 0 },
                    profile = profile,
                    likedTrackIds = likedTrackIds,
                    excludedIds = seedExcluded,
                    maxPerArtist = 2,
                    limit = 12
                )
                if (rankedContextual.isNotEmpty()) {
                    val useArtist = primarySeed.artist.isNotBlank() &&
                        profile.topArtists.firstOrNull()?.equals(primarySeed.artist, ignoreCase = true) == true
                    ContextualShelf(
                        seedTrackTitle = primarySeed.title,
                        seedArtist = primarySeed.artist,
                        useArtistHeading = useArtist,
                        tracks = rankedContextual
                    )
                } else {
                    null
                }
            } else {
                null
            }

            val remainingShelves = if (regionalQuickShelf != null) {
                filteredShelves.filterNot { it.id == regionalQuickShelf.id }
            } else {
                filteredShelves
            }
            val fallbackReleases = if (releases.isNotEmpty()) {
                releases
            } else {
                rawShelves.firstOrNull { it.id == "new_releases" }?.albums.orEmpty()
            }

            val errorMsg = if (allRemoteFailed) {
                shelvesResult.exceptionOrNull()?.message
                    ?: seedResults.firstOrNull { it.isFailure }?.exceptionOrNull()?.message
            } else {
                null
            }

            DiscoverFeedResult(
                quickPicks = rankedQuickPicks,
                contextualShelf = contextualShelf,
                shelves = remainingShelves,
                chartShelves = charts,
                newReleases = fallbackReleases,
                moodCategories = moods,
                allRemoteFailed = allRemoteFailed,
                errorMessage = errorMsg
            )
        }

        val synced = syncLikedFlags(computed, likedTrackIds)
        if (!synced.allRemoteFailed) {
            cache[profile.tasteSignature] = CachedFeed(fetchedAtMs = now, result = synced)
        }
        synced
    }

    fun clearCache() {
        cache.clear()
    }

    internal companion object {
        private const val CACHE_TTL_MS = 5L * 60_000L

        internal fun syncLikedFlags(
            result: DiscoverFeedResult,
            likedTrackIds: Set<String>
        ): DiscoverFeedResult {
            fun syncTrack(track: Track): Track {
                val isLiked = track.id in likedTrackIds
                return if (track.liked == isLiked) track else track.copy(liked = isLiked)
            }
            val updatedQuick = result.quickPicks.map(::syncTrack)
            val updatedContextual = result.contextualShelf?.let { shelf ->
                shelf.copy(tracks = shelf.tracks.map(::syncTrack))
            }
            val updatedShelves = result.shelves.map { shelf ->
                shelf.copy(tracks = shelf.tracks.map(::syncTrack))
            }
            val updatedChartShelves = result.chartShelves.map { shelf ->
                shelf.copy(tracks = shelf.tracks.map(::syncTrack))
            }
            return result.copy(
                quickPicks = updatedQuick,
                contextualShelf = updatedContextual,
                shelves = updatedShelves,
                chartShelves = updatedChartShelves
            )
        }
    }
}
