package com.onda.music.core.database

import androidx.compose.runtime.Immutable
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
import com.onda.music.core.model.LyricsMotionStyle
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

@Immutable
@Serializable
data class OndaUserSettings(
    val playerStyle: PlayerStyle = PlayerStyle.POSTER,
    val eqConfig: EqConfig = EqConfig(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val glassBlurEnabled: Boolean = true,
    val dynamicMeshEnabled: Boolean = true,
    val lyricsMotionEnabled: Boolean = true,
    val lyricsMotionStyle: LyricsMotionStyle = LyricsMotionStyle.KINETIC_GLOW,
    val lyricsFontScale: Float = 1.0f,
    val lyricsOnDemandTranslateEnabled: Boolean = false,
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
    val persistPlaybackSpeedGlobally: Boolean = true,
    val oledAdaptiveAccentEnabled: Boolean = false,
    val studioConfig: PlayerStudioConfig = PlayerStudioConfig(),
    val smartOutputProfilesEnabled: Boolean = true,
    val resumeOnHeadsetConnect: Boolean = false,
    val skipSilenceEnabled: Boolean = false,
    val keepPlayingOnTaskRemoved: Boolean = true,
    val keepScreenOn: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val scrobbleEnabled: Boolean = false,
    val scrobbleService: String = "librefm",
    val scrobbleUsername: String = "",
    val scrobbleSessionKey: String = "",
    val scrobbleApiKey: String = "",
    val scrobbleApiSecret: String = "",
    val discordRpcEnabled: Boolean = false,
    val discordUserToken: String? = null
)

@Immutable
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
        val lyricsMotionStyle = stringPreferencesKey("lyrics_motion_style")
        val lyricsFontScale = floatPreferencesKey("lyrics_font_scale")
        val lyricsOnDemandTranslateEnabled = booleanPreferencesKey("lyrics_on_demand_translate_enabled")
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
        val persistPlaybackSpeedGlobally = booleanPreferencesKey("persist_playback_speed_globally")
        val oledAdaptiveAccentEnabled = booleanPreferencesKey("oled_adaptive_accent_enabled")
        val studioConfigJson = stringPreferencesKey("studio_config_json")
        val smartOutputProfilesEnabled = booleanPreferencesKey("smart_output_profiles_enabled")
        val audioOutputProfilesJson = stringPreferencesKey("audio_output_profiles_json")
        val resumeOnHeadsetConnect = booleanPreferencesKey("resume_on_headset_connect")
        val skipSilenceEnabled = booleanPreferencesKey("skip_silence_enabled")
        val keepPlayingOnTaskRemoved = booleanPreferencesKey("keep_playing_on_task_removed")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val scrobbleEnabled = booleanPreferencesKey("scrobble_enabled")
        val scrobbleService = stringPreferencesKey("scrobble_service")
        val scrobbleUsername = stringPreferencesKey("scrobble_username")
        val scrobbleSessionKey = stringPreferencesKey("scrobble_session_key")
        val scrobbleApiKey = stringPreferencesKey("scrobble_api_key")
        val scrobbleApiSecret = stringPreferencesKey("scrobble_api_secret")
        val discordRpcEnabled = booleanPreferencesKey("discord_rpc_enabled")
        val discordUserToken = stringPreferencesKey("discord_user_token")
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

    suspend fun setLyricsMotionStyle(style: LyricsMotionStyle) {
        dataStore.edit { it[Keys.lyricsMotionStyle] = style.name }
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

    suspend fun setPersistPlaybackSpeedGlobally(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.persistPlaybackSpeedGlobally] = enabled
            if (!enabled) {
                prefs[Keys.playbackSpeed] = 1.0f
                prefs[Keys.playbackPitch] = 1.0f
            }
        }
    }

    suspend fun setOledAdaptiveAccentEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.oledAdaptiveAccentEnabled] = enabled }
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

    suspend fun setLyricsOnDemandTranslateEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.lyricsOnDemandTranslateEnabled] = enabled }
    }

    suspend fun setScrobbleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.scrobbleEnabled] = enabled }
    }

    suspend fun setScrobbleConfig(
        service: String,
        username: String,
        sessionKey: String,
        apiKey: String,
        apiSecret: String
    ) {
        val cleanService = if (service.equals("lastfm", ignoreCase = true)) "lastfm" else "librefm"
        dataStore.edit { prefs ->
            prefs[Keys.scrobbleService] = cleanService
            prefs[Keys.scrobbleUsername] = username.trim()
            prefs[Keys.scrobbleSessionKey] = sessionKey.trim()
            prefs[Keys.scrobbleApiKey] = apiKey.trim()
            prefs[Keys.scrobbleApiSecret] = apiSecret.trim()
        }
    }

    suspend fun setDiscordRpcEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.discordRpcEnabled] = enabled }
    }

    suspend fun setDiscordUserToken(token: String?) {
        val cleaned = token?.trim()?.takeIf { it.isNotEmpty() }
        dataStore.edit { prefs ->
            if (cleaned == null) {
                prefs.remove(Keys.discordUserToken)
            } else {
                prefs[Keys.discordUserToken] = cleaned
            }
        }
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
            prefs.remove(Keys.audioOutputProfilesJson)
            prefs.remove(Keys.playbackCheckpointJson)
        }
    }

    private fun mapSettings(prefs: Preferences): OndaUserSettings {
        val style = prefs[Keys.playerStyle]?.let { raw ->
            runCatching { PlayerStyle.valueOf(raw) }.getOrNull()
        } ?: PlayerStyle.POSTER
        val repeat = prefs[Keys.repeatMode]?.let { raw ->
            runCatching { RepeatMode.valueOf(raw) }.getOrNull()
        } ?: RepeatMode.OFF
        val lyricsStyle = prefs[Keys.lyricsMotionStyle]?.let { raw ->
            runCatching { LyricsMotionStyle.valueOf(raw) }.getOrNull()
        } ?: LyricsMotionStyle.KINETIC_GLOW
        return OndaUserSettings(
            playerStyle = style,
            eqConfig = decodeEqConfig(prefs[Keys.eqConfigJson]),
            repeatMode = repeat,
            shuffleEnabled = prefs[Keys.shuffleEnabled] ?: false,
            glassBlurEnabled = prefs[Keys.glassBlurEnabled] ?: true,
            dynamicMeshEnabled = prefs[Keys.dynamicMeshEnabled] ?: true,
            lyricsMotionEnabled = prefs[Keys.lyricsMotionEnabled] ?: true,
            lyricsMotionStyle = lyricsStyle,
            lyricsFontScale = (prefs[Keys.lyricsFontScale] ?: 1.0f).coerceIn(0.75f, 1.75f),
            lyricsOnDemandTranslateEnabled = prefs[Keys.lyricsOnDemandTranslateEnabled] ?: false,
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
            persistPlaybackSpeedGlobally = prefs[Keys.persistPlaybackSpeedGlobally] ?: true,
            oledAdaptiveAccentEnabled = prefs[Keys.oledAdaptiveAccentEnabled] ?: false,
            studioConfig = decodeStudioConfig(prefs[Keys.studioConfigJson]),
            smartOutputProfilesEnabled = prefs[Keys.smartOutputProfilesEnabled] ?: true,
            resumeOnHeadsetConnect = prefs[Keys.resumeOnHeadsetConnect] ?: false,
            skipSilenceEnabled = prefs[Keys.skipSilenceEnabled] ?: false,
            keepPlayingOnTaskRemoved = prefs[Keys.keepPlayingOnTaskRemoved] ?: true,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: false,
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false,
            scrobbleEnabled = prefs[Keys.scrobbleEnabled] ?: false,
            scrobbleService = if (prefs[Keys.scrobbleService].equals("lastfm", ignoreCase = true)) "lastfm" else "librefm",
            scrobbleUsername = prefs[Keys.scrobbleUsername] ?: "",
            scrobbleSessionKey = prefs[Keys.scrobbleSessionKey] ?: "",
            scrobbleApiKey = prefs[Keys.scrobbleApiKey] ?: "",
            scrobbleApiSecret = prefs[Keys.scrobbleApiSecret] ?: "",
            discordRpcEnabled = prefs[Keys.discordRpcEnabled] ?: false,
            discordUserToken = prefs[Keys.discordUserToken]
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
        prefs[Keys.lyricsMotionStyle] = settings.lyricsMotionStyle.name
        prefs[Keys.lyricsFontScale] = settings.lyricsFontScale.coerceIn(0.75f, 1.75f)
        prefs[Keys.lyricsOnDemandTranslateEnabled] = settings.lyricsOnDemandTranslateEnabled
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
        prefs[Keys.persistPlaybackSpeedGlobally] = settings.persistPlaybackSpeedGlobally
        prefs[Keys.oledAdaptiveAccentEnabled] = settings.oledAdaptiveAccentEnabled
        prefs[Keys.studioConfigJson] = databaseJson.encodeToString(settings.studioConfig.sanitized())
        prefs[Keys.smartOutputProfilesEnabled] = settings.smartOutputProfilesEnabled
        prefs[Keys.resumeOnHeadsetConnect] = settings.resumeOnHeadsetConnect
        prefs[Keys.skipSilenceEnabled] = settings.skipSilenceEnabled
        prefs[Keys.keepPlayingOnTaskRemoved] = settings.keepPlayingOnTaskRemoved
        prefs[Keys.keepScreenOn] = settings.keepScreenOn
        prefs[Keys.onboardingCompleted] = settings.onboardingCompleted
        prefs[Keys.scrobbleEnabled] = settings.scrobbleEnabled
        prefs[Keys.scrobbleService] = if (settings.scrobbleService.equals("lastfm", ignoreCase = true)) "lastfm" else "librefm"
        prefs[Keys.scrobbleUsername] = settings.scrobbleUsername
        prefs[Keys.scrobbleSessionKey] = settings.scrobbleSessionKey
        prefs[Keys.scrobbleApiKey] = settings.scrobbleApiKey
        prefs[Keys.scrobbleApiSecret] = settings.scrobbleApiSecret
        prefs[Keys.discordRpcEnabled] = settings.discordRpcEnabled
        if (settings.discordUserToken.isNullOrBlank()) {
            prefs.remove(Keys.discordUserToken)
        } else {
            prefs[Keys.discordUserToken] = settings.discordUserToken
        }
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
