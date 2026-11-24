package com.onda.music.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class TasteSeed(
    val trackId: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val score: Double
)

@Immutable
data class ListenerTasteProfile(
    val topSeeds: List<TasteSeed> = emptyList(),
    val topArtists: List<String> = emptyList(),
    val vietnameseAffinityScore: Float = 0f,
    val dominantRegionCode: String = "VN",
    val dominantLanguageTag: String = "vi",
    val excludedTrackIds: Set<String> = emptySet(),
    val recentTrackIds: Set<String> = emptySet(),
    val hasListeningHistory: Boolean = false,
    val tasteSignature: String = "cold_start_VN_vi"
) {
    val topSeedVideoIds: List<String>
        get() = topSeeds.map { it.videoId }
}

@Immutable
data class ContextualShelf(
    val seedTrackTitle: String,
    val seedArtist: String,
    val useArtistHeading: Boolean,
    val tracks: List<Track> = emptyList()
)
