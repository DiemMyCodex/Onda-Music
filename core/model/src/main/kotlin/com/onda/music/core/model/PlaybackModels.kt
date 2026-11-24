package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

@Serializable
enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
}

@Immutable
@Serializable
data class AudioSignalInfo(
    val codec: String = "",
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val bitrateKbps: Int = 0,
    val channelCount: Int = 0,
    val outputDeviceName: String = "",
    val outputKind: AudioOutputKind = AudioOutputKind.SPEAKER,
    val isLossless: Boolean = false
) {
    fun formatSampleRateLabel(): String {
        if (sampleRateHz <= 0) return ""
        return if (sampleRateHz % 1000 == 0) {
            "${sampleRateHz / 1000}kHz"
        } else {
            String.format(Locale.US, "%.1fkHz", sampleRateHz / 1000.0)
        }
    }

    fun formatRouteLabel(): String = when (outputKind) {
        AudioOutputKind.USB_AUDIO -> "USB DAC"
        AudioOutputKind.BLUETOOTH -> "BT"
        AudioOutputKind.WIRED_HEADSET -> "WIRED"
        AudioOutputKind.SPEAKER -> "SPEAKER"
    }

    fun formatBadgeText(): String {
        val parts = ArrayList<String>(4)
        val cleanCodec = codec.trim().uppercase(Locale.US)
        if (cleanCodec.isNotEmpty()) {
            parts += cleanCodec
        }
        val rateText = formatSampleRateLabel()
        val resolutionText = when {
            bitDepth > 0 && rateText.isNotEmpty() -> "$bitDepth-bit/$rateText"
            rateText.isNotEmpty() -> rateText
            bitDepth > 0 -> "$bitDepth-bit"
            else -> ""
        }
        if (resolutionText.isNotEmpty()) {
            parts += resolutionText
        } else if (bitrateKbps > 0) {
            parts += "${bitrateKbps}k"
        }
        if (!isLossless && resolutionText.isNotEmpty() && bitrateKbps > 0) {
            parts += "${bitrateKbps}k"
        }
        parts += formatRouteLabel()
        return parts.joinToString(" · ")
    }
}

@Immutable
@Serializable
data class PlaybackState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val activeStream: StreamOption? = null,
    val audioSignal: AudioSignalInfo? = null,
    val sleepTimerRemainingMs: Long? = null,
    val errorMessage: String? = null,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val sleepTimerEndOfTrack: Boolean = false,
    val sleepTimerFading: Boolean = false
)

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@Immutable
@Serializable
data class DownloadItem(
    val trackId: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val localFilePath: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L
)
