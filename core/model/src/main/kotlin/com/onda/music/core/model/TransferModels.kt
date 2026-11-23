package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class TransferFormat {
    MOMI_TRANSFER_V1,
    ONDA_TRANSFER_V1
}

@Serializable
enum class MergePolicy {
    IDEMPOTENT_UPSERT,
    REPLACE_EXISTING
}

@Immutable
@Serializable
data class TransferManifest(
    val format: String,
    val version: Int,
    val exportedAtEpochMs: Long = 0L,
    val appName: String = "",
    val appVersion: String = ""
)

@Immutable
@Serializable
data class TransferProgress(
    val stage: String,
    val fraction: Float,
    val processedCount: Int = 0,
    val totalCount: Int = 0
)

@Immutable
@Serializable
data class TransferSummary(
    val format: TransferFormat,
    val importedTracks: Int = 0,
    val relinkRequiredTracks: Int = 0,
    val importedArtists: Int = 0,
    val importedAlbums: Int = 0,
    val importedPlaylists: Int = 0,
    val importedPlayEvents: Int = 0,
    val importedSearchQueries: Int = 0,
    val importedLyrics: Int = 0,
    val importedProfile: Boolean = false,
    val importedEqConfig: Boolean = false,
    val importedPlayerStyle: PlayerStyle? = null
)
