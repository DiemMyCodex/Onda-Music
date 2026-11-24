package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class AudioOutputKind {
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
    USB_AUDIO
}

@Immutable
@Serializable
data class AudioOutputDevice(
    val key: String = "speaker",
    val displayName: String = "Phone Speaker",
    val kind: AudioOutputKind = AudioOutputKind.SPEAKER
)

@Immutable
@Serializable
data class AudioOutputEqProfile(
    val deviceKey: String,
    val deviceName: String,
    val kind: AudioOutputKind,
    val eqEnabled: Boolean = false,
    val activePresetId: String = "flat",
    val bands: List<EqBand> = EqConfig.defaultBands(),
    val preampDb: Float = 0f,
    val normalizeLoudness: Boolean = false,
    val updatedAtMs: Long = 0L
) {
    fun applyTo(base: EqConfig): EqConfig {
        val safeBands = if (bands.size == EqConfig.standardFrequenciesHz.size) {
            bands
        } else {
            EqConfig.defaultBands()
        }
        return base.copy(
            enabled = eqEnabled,
            activePresetId = activePresetId,
            bands = safeBands,
            preampDb = preampDb.coerceIn(-12f, 12f),
            normalizeLoudness = normalizeLoudness
        )
    }

    companion object {
        fun fromEqConfig(
            device: AudioOutputDevice,
            config: EqConfig,
            updatedAtMs: Long = System.currentTimeMillis()
        ): AudioOutputEqProfile {
            return AudioOutputEqProfile(
                deviceKey = device.key,
                deviceName = device.displayName,
                kind = device.kind,
                eqEnabled = config.enabled,
                activePresetId = config.activePresetId,
                bands = config.bands,
                preampDb = config.preampDb.coerceIn(-12f, 12f),
                normalizeLoudness = config.normalizeLoudness,
                updatedAtMs = updatedAtMs
            )
        }
    }
}

@Immutable
data class OutputProfileSwitchEvent(
    val id: Long,
    val device: AudioOutputDevice,
    val presetLabel: String,
    val eqEnabled: Boolean,
    val normalizeLoudness: Boolean
)
