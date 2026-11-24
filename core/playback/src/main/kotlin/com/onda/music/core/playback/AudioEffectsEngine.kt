package com.onda.music.core.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.onda.music.core.model.EqBand
import com.onda.music.core.model.EqConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
) {
    companion object {
        fun peaking(
            frequencyHz: Double,
            sampleRate: Int,
            gainDb: Double,
            q: Double
        ): BiquadCoefficients? {
            if (sampleRate <= 0 || frequencyHz <= 0.0 || frequencyHz >= sampleRate * 0.495) {
                return null
            }
            if (abs(gainDb) < 0.05) {
                return null
            }
            val safeQ = q.coerceIn(0.2, 12.0)
            val a = 10.0.pow(gainDb / 40.0)
            val omega = 2.0 * PI * frequencyHz / sampleRate.toDouble()
            val sinOmega = sin(omega)
            val cosOmega = cos(omega)
            val alpha = sinOmega / (2.0 * safeQ)

            val a0 = 1.0 + alpha / a
            val b0 = (1.0 + alpha * a) / a0
            val b1 = (-2.0 * cosOmega) / a0
            val b2 = (1.0 - alpha * a) / a0
            val a1 = (-2.0 * cosOmega) / a0
            val a2 = (1.0 - alpha / a) / a0
            return BiquadCoefficients(b0 = b0, b1 = b1, b2 = b2, a1 = a1, a2 = a2)
        }
    }
}

@UnstableApi
@Suppress("DEPRECATION")
class EqAudioProcessor : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    @Volatile
    private var activeConfig = EqConfig()

    @Volatile
    private var configVersion = 0
    private var builtVersion = -1

    private var filters: List<BiquadCoefficients> = emptyList()
    private var filterStates = DoubleArray(0)
    private var preampLinear = 1.0f
    private var normalizeEnabled = false
    private var loudnessEnvelope = 0.0f
    private var loudnessGain = 1.0f

    private var workBuffer = FloatArray(0)
    private var pcm16Buffer = ShortArray(0)
    private var outputBuffer = EMPTY_BUFFER
    private var internalBuffer = EMPTY_BUFFER
    private var inputEnded = false

    fun updateConfig(config: EqConfig) {
        activeConfig = config
        configVersion++
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported || inputAudioFormat.channelCount !in 1..8 || inputAudioFormat.sampleRate <= 0) {
            pendingFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (channelCount == 0 || sampleRate == 0 || encoding == C.ENCODING_INVALID) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        syncFilterBank()

        val config = activeConfig
        val passThrough = !config.enabled && !config.normalizeLoudness && abs(config.preampDb) < 0.05f
        val out = replaceOutputBuffer(bytes)
        if (passThrough) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val bytesPerSample = bytesPerSampleFor(encoding)
        val frameBytes = bytesPerSample * channelCount
        if (frameBytes <= 0 || bytes < frameBytes) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frameCount = bytes / frameBytes
        val sampleCount = frameCount * channelCount
        val consumedBytes = sampleCount * bytesPerSample
        ensureCapacity(sampleCount)

        decodeToFloat(inputBuffer, sampleCount, encoding)

        if (config.enabled && filters.isNotEmpty()) {
            applyBiquads(frameCount)
        }

        if (abs(preampLinear - 1.0f) > 1e-4f) {
            for (i in 0 until sampleCount) {
                workBuffer[i] *= preampLinear
            }
        }

        if (normalizeEnabled) {
            applyLoudnessNormalization(frameCount)
        }

        encodeFromFloat(out, sampleCount, encoding)
        if (bytes > consumedBytes) {
            val remainder = bytes - consumedBytes
            for (r in 0 until remainder) {
                out.put(inputBuffer.get())
            }
        }
        out.flip()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val current = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return current
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        val format = pendingFormat
        if (format != AudioProcessor.AudioFormat.NOT_SET &&
            (format.sampleRate != sampleRate || format.channelCount != channelCount || format.encoding != encoding)
        ) {
            sampleRate = format.sampleRate
            channelCount = format.channelCount
            encoding = format.encoding
            builtVersion = -1
        }
        filterStates.fill(0.0)
        loudnessEnvelope = 0.0f
        loudnessGain = 1.0f
    }

    override fun reset() {
        flush()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        filters = emptyList()
        filterStates = DoubleArray(0)
        workBuffer = FloatArray(0)
        pcm16Buffer = ShortArray(0)
        internalBuffer = EMPTY_BUFFER
        builtVersion = -1
    }

    private fun decodeToFloat(inputBuffer: ByteBuffer, sampleCount: Int, pcmEncoding: Int) {
        when (pcmEncoding) {
            C.ENCODING_PCM_16BIT -> {
                inputBuffer.asShortBuffer().get(pcm16Buffer, 0, sampleCount)
                inputBuffer.position(inputBuffer.position() + sampleCount * 2)
                val scale = 1.0f / 32768.0f
                for (i in 0 until sampleCount) {
                    workBuffer[i] = pcm16Buffer[i] * scale
                }
            }
            C.ENCODING_PCM_24BIT -> {
                val scale = 1.0f / 8388608.0f
                for (i in 0 until sampleCount) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt()
                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                    workBuffer[i] = sample24 * scale
                }
            }
            C.ENCODING_PCM_32BIT -> {
                val scale = 1.0f / 2147483648.0f
                for (i in 0 until sampleCount) {
                    workBuffer[i] = inputBuffer.getInt() * scale
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                inputBuffer.asFloatBuffer().get(workBuffer, 0, sampleCount)
                inputBuffer.position(inputBuffer.position() + sampleCount * 4)
            }
        }
    }

    private fun encodeFromFloat(out: ByteBuffer, sampleCount: Int, pcmEncoding: Int) {
        when (pcmEncoding) {
            C.ENCODING_PCM_16BIT -> {
                for (i in 0 until sampleCount) {
                    val limited = softLimit(workBuffer[i])
                    pcm16Buffer[i] = (limited * 32768.0f).roundToInt().coerceIn(-32768, 32767).toShort()
                }
                out.asShortBuffer().put(pcm16Buffer, 0, sampleCount)
                out.position(sampleCount * 2)
            }
            C.ENCODING_PCM_24BIT -> {
                for (i in 0 until sampleCount) {
                    val limited = softLimit(workBuffer[i])
                    val q = (limited * 8388608.0f).roundToInt().coerceIn(-8388608, 8388607)
                    out.put((q and 0xFF).toByte())
                    out.put(((q shr 8) and 0xFF).toByte())
                    out.put(((q shr 16) and 0xFF).toByte())
                }
            }
            C.ENCODING_PCM_32BIT -> {
                for (i in 0 until sampleCount) {
                    val limited = softLimit(workBuffer[i])
                    val q = (limited.toDouble() * 2147483648.0).roundToLong()
                        .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                        .toInt()
                    out.putInt(q)
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                for (i in 0 until sampleCount) {
                    workBuffer[i] = softLimit(workBuffer[i])
                }
                out.asFloatBuffer().put(workBuffer, 0, sampleCount)
                out.position(sampleCount * 4)
            }
        }
    }

    private fun bytesPerSampleFor(pcmEncoding: Int): Int = when (pcmEncoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private fun syncFilterBank() {
        val version = configVersion
        if (version == builtVersion) return
        builtVersion = version
        val config = activeConfig
        preampLinear = 10.0.pow(config.preampDb.coerceIn(-12f, 12f) / 20.0).toFloat()
        normalizeEnabled = config.normalizeLoudness
        val built = if (config.enabled) {
            config.bands.take(10).mapNotNull { band: EqBand ->
                BiquadCoefficients.peaking(
                    frequencyHz = band.frequencyHz.toDouble(),
                    sampleRate = sampleRate,
                    gainDb = band.gainDb.coerceIn(-12f, 12f).toDouble(),
                    q = band.q.toDouble()
                )
            }
        } else {
            emptyList()
        }
        filters = built
        val neededState = built.size * channelCount * 4
        if (filterStates.size != neededState) {
            filterStates = DoubleArray(neededState)
        } else {
            filterStates.fill(0.0)
        }
    }

    private fun applyBiquads(frameCount: Int) {
        val ch = channelCount
        filters.forEachIndexed { bandIndex, coeff ->
            for (c in 0 until ch) {
                val base = (bandIndex * ch + c) * 4
                var x1 = filterStates[base]
                var x2 = filterStates[base + 1]
                var y1 = filterStates[base + 2]
                var y2 = filterStates[base + 3]
                var idx = c
                for (f in 0 until frameCount) {
                    val x = workBuffer[idx].toDouble()
                    val y = coeff.b0 * x + coeff.b1 * x1 + coeff.b2 * x2 - coeff.a1 * y1 - coeff.a2 * y2
                    x2 = x1
                    x1 = x
                    y2 = y1
                    y1 = y
                    workBuffer[idx] = y.toFloat()
                    idx += ch
                }
                filterStates[base] = x1
                filterStates[base + 1] = x2
                filterStates[base + 2] = y1
                filterStates[base + 3] = y2
            }
        }
    }

    private fun applyLoudnessNormalization(frameCount: Int) {
        val ch = channelCount
        val targetRms = 0.22f
        val attackCoeff = 0.015f
        val releaseCoeff = 0.0012f
        for (f in 0 until frameCount) {
            var framePeak = 0.0f
            val offset = f * ch
            for (c in 0 until ch) {
                val sampleAbs = abs(workBuffer[offset + c])
                if (sampleAbs > framePeak) framePeak = sampleAbs
            }
            val envCoeff = if (framePeak > loudnessEnvelope) attackCoeff else releaseCoeff
            loudnessEnvelope += (framePeak - loudnessEnvelope) * envCoeff
            val desiredGain = (targetRms / max(loudnessEnvelope, 0.02f)).coerceIn(0.45f, 2.2f)
            loudnessGain += (desiredGain - loudnessGain) * 0.002f
            for (c in 0 until ch) {
                workBuffer[offset + c] *= loudnessGain
            }
        }
    }

    private fun softLimit(sample: Float): Float {
        return if (abs(sample) <= 0.92f) {
            sample
        } else {
            val sign = if (sample >= 0.0f) 1.0f else -1.0f
            val excess = (abs(sample) - 0.92f).toDouble()
            (sign * (0.92 + 0.08 * tanh(excess / 0.08))).toFloat()
        }
    }

    private fun ensureCapacity(sampleCount: Int) {
        if (workBuffer.size < sampleCount) {
            workBuffer = FloatArray(sampleCount)
        }
        if (encoding == C.ENCODING_PCM_16BIT && pcm16Buffer.size < sampleCount) {
            pcm16Buffer = ShortArray(sampleCount)
        }
    }

    private fun replaceOutputBuffer(count: Int): ByteBuffer {
        if (internalBuffer.capacity() < count) {
            internalBuffer = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder())
        } else {
            internalBuffer.clear()
        }
        outputBuffer = internalBuffer
        return internalBuffer
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}

class CrossfadeVolumeController {
    @Volatile
    var crossfadeSeconds: Int = 0
        private set

    @Volatile
    var gaplessEnabled: Boolean = true
        private set

    private var trackFadeStartMs: Long = 0L

    fun configure(crossfadeSeconds: Int, gaplessEnabled: Boolean) {
        this.crossfadeSeconds = crossfadeSeconds.coerceIn(0, 12)
        this.gaplessEnabled = gaplessEnabled
    }

    fun onTrackStarted(nowMs: Long = System.currentTimeMillis()) {
        trackFadeStartMs = nowMs
    }

    fun computeEffectiveVolume(
        baseVolume: Float,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Float {
        val clampedBase = baseVolume.coerceIn(0f, 1f)
        val fadeDurationMs = crossfadeSeconds * 1000L
        if (fadeDurationMs <= 0L || durationMs <= fadeDurationMs * 2L) {
            return clampedBase
        }
        val elapsedSinceStart = (nowMs - trackFadeStartMs).coerceAtLeast(0L)
        val fadeInFactor = if (elapsedSinceStart < fadeDurationMs) {
            (elapsedSinceStart.toFloat() / fadeDurationMs.toFloat()).coerceIn(0.05f, 1f)
        } else {
            1f
        }
        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
        val fadeOutFactor = if (remainingMs < fadeDurationMs) {
            (remainingMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0.05f, 1f)
        } else {
            1f
        }
        return (clampedBase * minOf(fadeInFactor, fadeOutFactor)).coerceIn(0f, 1f)
    }
}

@UnstableApi
@Singleton
class AudioEffectsEngine @Inject constructor() {
    val eqProcessor = EqAudioProcessor()
    val crossfadeController = CrossfadeVolumeController()

    private val _eqConfig = MutableStateFlow(EqConfig())
    val eqConfig: StateFlow<EqConfig> = _eqConfig.asStateFlow()

    fun applyEqConfig(config: EqConfig) {
        _eqConfig.value = config
        eqProcessor.updateConfig(config)
        crossfadeController.configure(
            crossfadeSeconds = config.crossfadeSeconds,
            gaplessEnabled = config.gaplessEnabled
        )
    }
}
