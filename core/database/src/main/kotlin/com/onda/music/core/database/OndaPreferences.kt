package com.onda.music.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onda.music.core.model.AudioOutputEqProfile
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.PlayerStudioConfig
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OndaUserSettings(
    val playerStyle: PlayerStyle = PlayerStyle.POSTER,
    val eqConfig: EqConfig = EqConfig(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val glassBlurEnabled: Boolean = true,
    val dynamicMeshEnabled: Boolean = true,
    val lyricsMotionEnabled: Boolean = true,
    val lyricsFontScale: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
    val monochromeEnabled: Boolean = true,
    val ytCookie: String? = null,
    val proxyUrl: String? = null,
    val languageCode: String = "en",
    val maxCacheMb: Int = 512,
    val downloadTreeUri: String? = null,
    val username: String = "",
    val avatarPath: String? = null,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val studioConfig: PlayerStudioConfig = PlayerStudioConfig(),
    val smartOutputProfilesEnabled: Boolean = true,
    val resumeOnHeadsetConnect: Boolean = false,
    val skipSilenceEnabled: Boolean = false,
    val keepPlayingOnTaskRemoved: Boolean = true,
    val keepScreenOn: Boolean = false,
    val onboardingCompleted: Boolean = false
)

@Serializable
data class PlaybackCheckpoint(
    val queueTrackIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

@Singleton
class OndaPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val playerStyle = stringPreferencesKey("player_style")
        val eqConfigJson = stringPreferencesKey("eq_config_json")
        val repeatMode = stringPreferencesKey("repeat_mode")
        val shuffleEnabled = booleanPreferencesKey("shuffle_enabled")
        val glassBlurEnabled = booleanPreferencesKey("glass_blur_enabled")
        val dynamicMeshEnabled = booleanPreferencesKey("dynamic_mesh_enabled")
        val lyricsMotionEnabled = booleanPreferencesKey("lyrics_motion_enabled")
        val lyricsFontScale = floatPreferencesKey("lyrics_font_scale")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val monochromeEnabled = booleanPreferencesKey("monochrome_enabled")
        val ytCookie = stringPreferencesKey("yt_cookie")
        val proxyUrl = stringPreferencesKey("proxy_url")
        val languageCode = stringPreferencesKey("language_code")
        val maxCacheMb = intPreferencesKey("max_cache_mb")
        val downloadTreeUri = stringPreferencesKey("download_tree_uri")
        val username = stringPreferencesKey("username")
        val avatarPath = stringPreferencesKey("avatar_path")
        val playbackCheckpointJson = stringPreferencesKey("playback_checkpoint_json")
        val playbackSpeed = floatPreferencesKey("playback_speed")
        val playbackPitch = floatPreferencesKey("playback_pitch")
        val studioConfigJson = stringPreferencesKey("studio_config_json")
        val smartOutputProfilesEnabled = booleanPreferencesKey("smart_output_profiles_enabled")
        val audioOutputProfilesJson = stringPreferencesKey("audio_output_profiles_json")
        val resumeOnHeadsetConnect = booleanPreferencesKey("resume_on_headset_connect")
        val skipSilenceEnabled = booleanPreferencesKey("skip_silence_enabled")
        val keepPlayingOnTaskRemoved = booleanPreferencesKey("keep_playing_on_task_removed")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }

    private val safePreferences: Flow<Preferences> = dataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val settings: Flow<OndaUserSettings> = safePreferences.map(::mapSettings)

    val audioOutputProfiles: Flow<Map<String, AudioOutputEqProfile>> = safePreferences.map { prefs ->
        decodeAudioOutputProfiles(prefs[Keys.audioOutputProfilesJson])
    }

    val playbackCheckpoint: Flow<PlaybackCheckpoint> = safePreferences.map { prefs ->
        val raw = prefs[Keys.playbackCheckpointJson]
        if (raw.isNullOrBlank()) {
            PlaybackCheckpoint()
        } else {
            runCatching { databaseJson.decodeFromString<PlaybackCheckpoint>(raw) }
                .getOrDefault(PlaybackCheckpoint())
        }
    }

    suspend fun currentSettings(): OndaUserSettings = settings.first()

    suspend fun updateSettings(transform: (OndaUserSettings) -> OndaUserSettings) {
        dataStore.edit { prefs ->
            val current = mapSettings(prefs)
            val updated = transform(current)
            writeSettings(prefs, updated)
        }
    }

    suspend fun setPlayerStyle(style: PlayerStyle) {
        dataStore.edit { it[Keys.playerStyle] = style.name }
    }

    suspend fun setEqConfig(eqConfig: EqConfig) {
        dataStore.edit { it[Keys.eqConfigJson] = encodeEqConfig(eqConfig) }
    }

    suspend fun setRepeatMode(repeatMode: RepeatMode) {
        dataStore.edit { it[Keys.repeatMode] = repeatMode.name }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.shuffleEnabled] = enabled }
    }

    suspend fun setGlassBlurEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.glassBlurEnabled] = enabled }
    }

    suspend fun setDynamicMeshEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.dynamicMeshEnabled] = enabled }
    }

    suspend fun setLyricsMotionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.lyricsMotionEnabled] = enabled }
    }

    suspend fun setLyricsFontScale(scale: Float) {
        dataStore.edit { it[Keys.lyricsFontScale] = scale.coerceIn(0.75f, 1.75f) }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.hapticsEnabled] = enabled }
    }

    suspend fun setMonochromeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.monochromeEnabled] = enabled }
    }

    suspend fun setYtCookie(cookie: String?) {
        dataStore.edit { prefs ->
            if (cookie.isNullOrBlank()) prefs.remove(Keys.ytCookie) else prefs[Keys.ytCookie] = cookie
        }
    }

    suspend fun setProxyUrl(proxyUrl: String?) {
        dataStore.edit { prefs ->
            if (proxyUrl.isNullOrBlank()) prefs.remove(Keys.proxyUrl) else prefs[Keys.proxyUrl] = proxyUrl
        }
    }

    suspend fun setLanguageCode(languageCode: String) {
        val normalized = normalizeLanguageCode(languageCode)
        dataStore.edit { it[Keys.languageCode] = normalized }
    }

    suspend fun setMaxCacheMb(maxCacheMb: Int) {
        dataStore.edit { it[Keys.maxCacheMb] = maxCacheMb.coerceAtLeast(64) }
    }

    suspend fun setDownloadTreeUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.downloadTreeUri) else prefs[Keys.downloadTreeUri] = uri
        }
    }

    suspend fun setUsername(username: String) {
        dataStore.edit { it[Keys.username] = username }
    }

    suspend fun setAvatarPath(avatarPath: String?) {
        dataStore.edit { prefs ->
            if (avatarPath.isNullOrBlank()) prefs.remove(Keys.avatarPath) else prefs[Keys.avatarPath] = avatarPath
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[Keys.playbackSpeed] = clampPlaybackFactor(speed) }
    }

    suspend fun setPlaybackPitch(pitch: Float) {
        dataStore.edit { it[Keys.playbackPitch] = clampPlaybackFactor(pitch) }
    }

    suspend fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        val safeSpeed = clampPlaybackFactor(speed)
        val safePitch = clampPlaybackFactor(pitch)
        dataStore.edit { prefs ->
            prefs[Keys.playbackSpeed] = safeSpeed
            prefs[Keys.playbackPitch] = safePitch
        }
    }

    suspend fun setStudioConfig(config: PlayerStudioConfig) {
        dataStore.edit { it[Keys.studioConfigJson] = databaseJson.encodeToString(config.sanitized()) }
    }

    suspend fun setSmartOutputProfilesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.smartOutputProfilesEnabled] = enabled }
    }

    suspend fun setResumeOnHeadsetConnect(enabled: Boolean) {
        dataStore.edit { it[Keys.resumeOnHeadsetConnect] = enabled }
    }

    suspend fun setSkipSilenceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.skipSilenceEnabled] = enabled }
    }

    suspend fun setKeepPlayingOnTaskRemoved(enabled: Boolean) {
        dataStore.edit { it[Keys.keepPlayingOnTaskRemoved] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.keepScreenOn] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun completeOnboarding(
        languageCode: String,
        playerStyle: PlayerStyle,
        lyricsMotionEnabled: Boolean,
        monochromeEnabled: Boolean,
        hapticsEnabled: Boolean
    ) {
        val normalizedLang = normalizeLanguageCode(languageCode)
        dataStore.edit { prefs ->
            prefs[Keys.languageCode] = normalizedLang
            prefs[Keys.playerStyle] = playerStyle.name
            prefs[Keys.lyricsMotionEnabled] = lyricsMotionEnabled
            prefs[Keys.monochromeEnabled] = monochromeEnabled
            prefs[Keys.hapticsEnabled] = hapticsEnabled
            prefs[Keys.onboardingCompleted] = true
        }
    }

    suspend fun currentAudioOutputProfiles(): Map<String, AudioOutputEqProfile> {
        return audioOutputProfiles.first()
    }

    suspend fun saveAudioOutputProfile(profile: AudioOutputEqProfile) {
        dataStore.edit { prefs ->
            val current = decodeAudioOutputProfiles(prefs[Keys.audioOutputProfilesJson]).toMutableMap()
            current[profile.deviceKey] = profile
            prefs[Keys.audioOutputProfilesJson] = databaseJson.encodeToString(current)
        }
    }

    suspend fun clearAudioOutputProfiles() {
        dataStore.edit { it.remove(Keys.audioOutputProfilesJson) }
    }

    suspend fun savePlaybackCheckpoint(checkpoint: PlaybackCheckpoint) {
        dataStore.edit { it[Keys.playbackCheckpointJson] = databaseJson.encodeToString(checkpoint) }
    }

    suspend fun clearPlaybackCheckpoint() {
        dataStore.edit { it.remove(Keys.playbackCheckpointJson) }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            writeSettings(prefs, OndaUserSettings())
        }
    }

    private fun mapSettings(prefs: Preferences): OndaUserSettings {
        val style = prefs[Keys.playerStyle]?.let { raw ->
            runCatching { PlayerStyle.valueOf(raw) }.getOrNull()
        } ?: PlayerStyle.POSTER
        val repeat = prefs[Keys.repeatMode]?.let { raw ->
            runCatching { RepeatMode.valueOf(raw) }.getOrNull()
        } ?: RepeatMode.OFF
        return OndaUserSettings(
            playerStyle = style,
            eqConfig = decodeEqConfig(prefs[Keys.eqConfigJson]),
            repeatMode = repeat,
            shuffleEnabled = prefs[Keys.shuffleEnabled] ?: false,
            glassBlurEnabled = prefs[Keys.glassBlurEnabled] ?: true,
            dynamicMeshEnabled = prefs[Keys.dynamicMeshEnabled] ?: true,
            lyricsMotionEnabled = prefs[Keys.lyricsMotionEnabled] ?: true,
            lyricsFontScale = (prefs[Keys.lyricsFontScale] ?: 1.0f).coerceIn(0.75f, 1.75f),
            hapticsEnabled = prefs[Keys.hapticsEnabled] ?: true,
            monochromeEnabled = prefs[Keys.monochromeEnabled] ?: true,
            ytCookie = prefs[Keys.ytCookie],
            proxyUrl = prefs[Keys.proxyUrl],
            languageCode = normalizeLanguageCode(prefs[Keys.languageCode]),
            maxCacheMb = (prefs[Keys.maxCacheMb] ?: 512).coerceAtLeast(64),
            downloadTreeUri = prefs[Keys.downloadTreeUri],
            username = prefs[Keys.username] ?: "",
            avatarPath = prefs[Keys.avatarPath],
            playbackSpeed = clampPlaybackFactor(prefs[Keys.playbackSpeed] ?: 1.0f),
            playbackPitch = clampPlaybackFactor(prefs[Keys.playbackPitch] ?: 1.0f),
            studioConfig = decodeStudioConfig(prefs[Keys.studioConfigJson]),
            smartOutputProfilesEnabled = prefs[Keys.smartOutputProfilesEnabled] ?: true,
            resumeOnHeadsetConnect = prefs[Keys.resumeOnHeadsetConnect] ?: false,
            skipSilenceEnabled = prefs[Keys.skipSilenceEnabled] ?: false,
            keepPlayingOnTaskRemoved = prefs[Keys.keepPlayingOnTaskRemoved] ?: true,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: false,
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false
        )
    }

    private fun writeSettings(prefs: MutablePreferences, settings: OndaUserSettings) {
        prefs[Keys.playerStyle] = settings.playerStyle.name
        prefs[Keys.eqConfigJson] = encodeEqConfig(settings.eqConfig)
        prefs[Keys.repeatMode] = settings.repeatMode.name
        prefs[Keys.shuffleEnabled] = settings.shuffleEnabled
        prefs[Keys.glassBlurEnabled] = settings.glassBlurEnabled
        prefs[Keys.dynamicMeshEnabled] = settings.dynamicMeshEnabled
        prefs[Keys.lyricsMotionEnabled] = settings.lyricsMotionEnabled
        prefs[Keys.lyricsFontScale] = settings.lyricsFontScale.coerceIn(0.75f, 1.75f)
        prefs[Keys.hapticsEnabled] = settings.hapticsEnabled
        prefs[Keys.monochromeEnabled] = settings.monochromeEnabled
        if (settings.ytCookie.isNullOrBlank()) {
            prefs.remove(Keys.ytCookie)
        } else {
            prefs[Keys.ytCookie] = settings.ytCookie
        }
        if (settings.proxyUrl.isNullOrBlank()) {
            prefs.remove(Keys.proxyUrl)
        } else {
            prefs[Keys.proxyUrl] = settings.proxyUrl
        }
        prefs[Keys.languageCode] = normalizeLanguageCode(settings.languageCode)
        prefs[Keys.maxCacheMb] = settings.maxCacheMb.coerceAtLeast(64)
        if (settings.downloadTreeUri.isNullOrBlank()) {
            prefs.remove(Keys.downloadTreeUri)
        } else {
            prefs[Keys.downloadTreeUri] = settings.downloadTreeUri
        }
        prefs[Keys.username] = settings.username
        if (settings.avatarPath.isNullOrBlank()) {
            prefs.remove(Keys.avatarPath)
        } else {
            prefs[Keys.avatarPath] = settings.avatarPath
        }
        prefs[Keys.playbackSpeed] = clampPlaybackFactor(settings.playbackSpeed)
        prefs[Keys.playbackPitch] = clampPlaybackFactor(settings.playbackPitch)
        prefs[Keys.studioConfigJson] = databaseJson.encodeToString(settings.studioConfig.sanitized())
        prefs[Keys.smartOutputProfilesEnabled] = settings.smartOutputProfilesEnabled
        prefs[Keys.resumeOnHeadsetConnect] = settings.resumeOnHeadsetConnect
        prefs[Keys.skipSilenceEnabled] = settings.skipSilenceEnabled
        prefs[Keys.keepPlayingOnTaskRemoved] = settings.keepPlayingOnTaskRemoved
        prefs[Keys.keepScreenOn] = settings.keepScreenOn
        prefs[Keys.onboardingCompleted] = settings.onboardingCompleted
    }

    private fun decodeAudioOutputProfiles(raw: String?): Map<String, AudioOutputEqProfile> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            databaseJson.decodeFromString<Map<String, AudioOutputEqProfile>>(raw)
        }.getOrDefault(emptyMap())
    }

    private fun clampPlaybackFactor(value: Float): Float {
        return (value.takeIf { it.isFinite() } ?: 1.0f).coerceIn(0.5f, 2.0f)
    }

    private fun decodeStudioConfig(raw: String?): PlayerStudioConfig {
        if (raw.isNullOrBlank()) return PlayerStudioConfig()
        return runCatching {
            databaseJson.decodeFromString<PlayerStudioConfig>(raw).sanitized()
        }.getOrDefault(PlayerStudioConfig())
    }

    private fun normalizeLanguageCode(code: String?): String {
        return when (code?.lowercase()) {
            "vi" -> "vi"
            else -> "en"
        }
    }
}
