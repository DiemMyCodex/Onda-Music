package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.GZIPInputStream

@Immutable
@Serializable
data class EqBand(
    val index: Int,
    val frequencyHz: Int,
    val gainDb: Float = 0f,
    val q: Float = 1.41f
)

@Immutable
@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val gainsDb: List<Float>,
    val isBuiltIn: Boolean = false
)

@Immutable
@Serializable
data class AutoEqProfile(
    val id: String,
    @SerialName("b") val brand: String,
    @SerialName("m") val model: String,
    @SerialName("t") val formFactor: String = "Over-Ear",
    @SerialName("p") val preampDb: Float = 0f,
    @SerialName("g") val gainsDb: List<Float> = emptyList(),
    @SerialName("q") val qValues: List<Float> = emptyList()
) {
    val displayName: String
        get() = "$brand $model".trim()

    fun toEqConfig(base: EqConfig = EqConfig()): EqConfig {
        val bands = EqConfig.standardFrequenciesHz.mapIndexed { index, freq ->
            EqBand(
                index = index,
                frequencyHz = freq,
                gainDb = gainsDb.getOrElse(index) { 0f }.coerceIn(-12f, 12f),
                q = qValues.getOrElse(index) { 1.41f }.coerceIn(0.2f, 12f)
            )
        }
        return base.copy(
            enabled = true,
            activePresetId = "$AUTO_EQ_PREFIX$id",
            bands = bands,
            preampDb = preampDb.coerceIn(-12f, 12f)
        )
    }

    companion object {
        const val AUTO_EQ_PREFIX = "autoeq:"
    }
}

object AutoEqCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun decodeGzip(inputStream: InputStream): List<AutoEqProfile> {
        return GZIPInputStream(inputStream).bufferedReader(Charsets.UTF_8).use { reader ->
            json.decodeFromString<List<AutoEqProfile>>(reader.readText())
        }
    }
}

@Immutable
@Serializable
data class EqConfig(
    val enabled: Boolean = false,
    val activePresetId: String = "flat",
    val bands: List<EqBand> = defaultBands(),
    val preampDb: Float = 0f,
    val normalizeLoudness: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val gaplessEnabled: Boolean = true
) {
    companion object {
        val standardFrequenciesHz: List<Int> = listOf(
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
        )

        fun defaultBands(gainsDb: List<Float> = List(10) { 0f }): List<EqBand> {
            return standardFrequenciesHz.mapIndexed { index, freq ->
                EqBand(
                    index = index,
                    frequencyHz = freq,
                    gainDb = gainsDb.getOrElse(index) { 0f }.coerceIn(-12f, 12f)
                )
            }
        }

        val builtInPresets: List<EqPreset> = listOf(
            EqPreset("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), true),
            EqPreset("bass_boost", "Bass Boost", listOf(5f, 4.5f, 3.5f, 1.5f, 0f, 0f, 0f, 0.5f, 1f, 1f), true),
            EqPreset("vocal", "Vocal Clarity", listOf(-2f, -1.5f, 0f, 1.5f, 3.5f, 4f, 3f, 1.5f, 0f, -1f), true),
            EqPreset("rock", "Rock", listOf(4f, 3f, 1.5f, -0.5f, -1f, 0.5f, 2f, 3.5f, 4f, 3.5f), true),
            EqPreset("electronic", "Electronic", listOf(4.5f, 4f, 1.5f, 0f, -1.5f, 1f, 2.5f, 3f, 4.5f, 5f), true),
            EqPreset("acoustic", "Acoustic", listOf(2f, 1.5f, 1f, 0.5f, 1.5f, 1f, 2f, 2.5f, 2f, 1.5f), true)
        )
    }
}
