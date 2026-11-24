package com.onda.music.core.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.model.AudioOutputDevice
import com.onda.music.core.model.AudioOutputEqProfile
import com.onda.music.core.model.AudioOutputKind
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.OutputProfileSwitchEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RawAudioOutput(
    val kind: AudioOutputKind,
    val name: String,
    val address: String = ""
)

object AudioOutputResolver {
    fun resolveDevice(candidates: List<RawAudioOutput>): AudioOutputDevice {
        val bluetooth = candidates.firstOrNull { it.kind == AudioOutputKind.BLUETOOTH }
        if (bluetooth != null) {
            val cleanName = bluetooth.name.trim()
            val cleanAddress = bluetooth.address.trim()
            val validAddress = cleanAddress.isNotBlank() && cleanAddress != "00:00:00:00:00:00"
            val id = when {
                validAddress -> cleanAddress.lowercase(Locale.ROOT)
                cleanName.isNotBlank() -> cleanName.lowercase(Locale.ROOT)
                else -> "default"
            }
            return AudioOutputDevice(
                key = "bt:$id",
                displayName = cleanName.ifBlank { "Bluetooth Audio" },
                kind = AudioOutputKind.BLUETOOTH
            )
        }

        val usb = candidates.firstOrNull { it.kind == AudioOutputKind.USB_AUDIO }
        if (usb != null) {
            val cleanName = usb.name.trim()
            val id = cleanName.lowercase(Locale.ROOT).ifBlank { "dac" }
            return AudioOutputDevice(
                key = "usb:$id",
                displayName = cleanName.ifBlank { "USB Audio" },
                kind = AudioOutputKind.USB_AUDIO
            )
        }

        val wired = candidates.firstOrNull { it.kind == AudioOutputKind.WIRED_HEADSET }
        if (wired != null) {
            return AudioOutputDevice(
                key = "wired_headset",
                displayName = "Wired Headset",
                kind = AudioOutputKind.WIRED_HEADSET
            )
        }

        return AudioOutputDevice(
            key = "speaker",
            displayName = "Phone Speaker",
            kind = AudioOutputKind.SPEAKER
        )
    }

    fun presetDisplayName(presetId: String, eqEnabled: Boolean): String {
        if (!eqEnabled) return "Flat"
        return EqConfig.builtInPresets.firstOrNull { it.id == presetId }?.name
            ?: if (presetId == "custom") "Custom EQ" else presetId
    }
}

@UnstableApi
@Singleton
class AudioOutputProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ondaPreferences: OndaPreferences,
    private val audioEffectsEngine: AudioEffectsEngine
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _activeOutputDevice = MutableStateFlow(detectCurrentOutputDevice())
    val activeOutputDevice: StateFlow<AudioOutputDevice> = _activeOutputDevice.asStateFlow()

    private val _savedProfiles = MutableStateFlow<Map<String, AudioOutputEqProfile>>(emptyMap())
    val savedProfiles: StateFlow<Map<String, AudioOutputEqProfile>> = _savedProfiles.asStateFlow()

    private val _profileSwitchEvents = MutableSharedFlow<OutputProfileSwitchEvent>(extraBufferCapacity = 4)
    val profileSwitchEvents: SharedFlow<OutputProfileSwitchEvent> = _profileSwitchEvents.asSharedFlow()

    @Volatile
    private var smartProfilesEnabled: Boolean = true

    @Volatile
    private var resumeOnHeadsetConnect: Boolean = false

    @Volatile
    private var isApplyingProfile: Boolean = false

    @Volatile
    private var isRegistered: Boolean = false

    @Volatile
    private var callbackPrimed: Boolean = false

    private var eventCounter: Long = 0L
    private var headsetConnectedListener: (() -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val hasExternalOutput = addedDevices.any { info ->
                info.isSink && mapOutputKind(info.type) != null && mapOutputKind(info.type) != AudioOutputKind.SPEAKER
            }
            if (callbackPrimed && hasExternalOutput && resumeOnHeadsetConnect) {
                headsetConnectedListener?.invoke()
            }
            handleRouteChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            handleRouteChanged()
        }
    }

    init {
        managerScope.launch {
            ondaPreferences.audioOutputProfiles.collect { profiles ->
                _savedProfiles.value = profiles
            }
        }
        managerScope.launch {
            ondaPreferences.settings.collect { settings ->
                smartProfilesEnabled = settings.smartOutputProfilesEnabled
                resumeOnHeadsetConnect = settings.resumeOnHeadsetConnect
            }
        }
    }

    fun startMonitoring(onHeadsetConnected: () -> Unit) {
        headsetConnectedListener = onHeadsetConnected
        if (isRegistered) return
        val manager = audioManager ?: return
        val mainHandler = Handler(Looper.getMainLooper())
        callbackPrimed = false
        runCatching {
            manager.registerAudioDeviceCallback(deviceCallback, mainHandler)
            isRegistered = true
            mainHandler.post { callbackPrimed = true }
        }
        handleRouteChanged()
    }

    fun stopMonitoring() {
        headsetConnectedListener = null
        callbackPrimed = false
        if (!isRegistered) return
        val manager = audioManager
        if (manager != null) {
            runCatching { manager.unregisterAudioDeviceCallback(deviceCallback) }
        }
        isRegistered = false
    }

    fun recordUserEqChange(config: EqConfig) {
        if (isApplyingProfile || !smartProfilesEnabled) return
        val device = _activeOutputDevice.value
        val profile = AudioOutputEqProfile.fromEqConfig(device = device, config = config)
        managerScope.launch(Dispatchers.IO) {
            ondaPreferences.saveAudioOutputProfile(profile)
        }
    }

    private fun handleRouteChanged() {
        val previousDevice = _activeOutputDevice.value
        val nextDevice = detectCurrentOutputDevice()
        if (nextDevice.key == previousDevice.key) return
        _activeOutputDevice.value = nextDevice
        if (!smartProfilesEnabled) return

        managerScope.launch {
            val currentSettings = ondaPreferences.currentSettings()
            val profiles = ondaPreferences.currentAudioOutputProfiles()
            if (previousDevice.key !in profiles) {
                ondaPreferences.saveAudioOutputProfile(
                    AudioOutputEqProfile.fromEqConfig(
                        device = previousDevice,
                        config = currentSettings.eqConfig
                    )
                )
            }
            val saved = profiles[nextDevice.key]
            if (saved != null) {
                val targetEq = saved.applyTo(currentSettings.eqConfig)
                isApplyingProfile = true
                try {
                    audioEffectsEngine.applyEqConfig(targetEq)
                    if (targetEq != currentSettings.eqConfig) {
                        ondaPreferences.setEqConfig(targetEq)
                    }
                } finally {
                    isApplyingProfile = false
                }
                _profileSwitchEvents.tryEmit(
                    OutputProfileSwitchEvent(
                        id = ++eventCounter,
                        device = nextDevice,
                        presetLabel = AudioOutputResolver.presetDisplayName(saved.activePresetId, saved.eqEnabled),
                        eqEnabled = saved.eqEnabled,
                        normalizeLoudness = saved.normalizeLoudness
                    )
                )
            } else {
                val baseline = AudioOutputEqProfile.fromEqConfig(
                    device = nextDevice,
                    config = currentSettings.eqConfig
                )
                ondaPreferences.saveAudioOutputProfile(baseline)
            }
        }
    }

    private fun detectCurrentOutputDevice(): AudioOutputDevice {
        val manager = audioManager ?: return AudioOutputDevice()
        val outputs = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrDefault(emptyArray())
        val candidates = outputs.mapNotNull { info ->
            val kind = mapOutputKind(info.type) ?: return@mapNotNull null
            RawAudioOutput(
                kind = kind,
                name = info.productName?.toString().orEmpty(),
                address = info.address.orEmpty()
            )
        }
        return AudioOutputResolver.resolveDevice(candidates)
    }

    private fun mapOutputKind(type: Int): AudioOutputKind? {
        return when {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioOutputKind.BLUETOOTH
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) -> {
                AudioOutputKind.BLUETOOTH
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                type == AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                AudioOutputKind.BLUETOOTH
            }
            type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE -> {
                AudioOutputKind.USB_AUDIO
            }
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                AudioOutputKind.WIRED_HEADSET
            }
            type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputKind.SPEAKER
            else -> null
        }
    }
}
