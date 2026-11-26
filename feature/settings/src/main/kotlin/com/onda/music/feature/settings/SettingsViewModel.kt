package com.onda.music.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.database.PlaylistDao
import com.onda.music.core.database.PlaylistSongEntity
import com.onda.music.core.database.SongDao
import com.onda.music.core.database.toEntity
import com.onda.music.core.model.AudioOutputDevice
import com.onda.music.core.model.AutoEqProfile
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.EqPreset
import com.onda.music.core.model.LyricsMotionStyle
import com.onda.music.core.model.MergePolicy
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.Playlist
import com.onda.music.core.playback.AutoEqRepository
import com.onda.music.core.playback.DownloadManager
import com.onda.music.core.playback.PlaybackController
import com.onda.music.core.playback.StreamResolver
import com.onda.music.core.sources.LinkImporter
import com.onda.music.core.sources.YtMusicClient
import com.onda.music.core.transfer.MomiTransferImporter
import com.onda.music.core.transfer.OndaBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

@Suppress("OPT_IN_USAGE_ERROR", "OPT_IN_USAGE", "UnsafeOptInUsageError", "UnstableApiUsage")
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: OndaPreferences,
    private val playbackController: PlaybackController,
    private val autoEqRepository: AutoEqRepository,
    private val streamResolver: StreamResolver,
    private val downloadManager: DownloadManager,
    private val ytMusicClient: YtMusicClient,
    private val linkImporter: LinkImporter,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val momiTransferImporter: MomiTransferImporter,
    private val ondaBackupManager: OndaBackupManager
) : ViewModel() {

    private val localState = MutableStateFlow(SettingsUiState())

    val settings: StateFlow<OndaUserSettings> = preferences.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OndaUserSettings()
        )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private data class PlaybackRuntimeSnapshot(
        val activeOutputDevice: AudioOutputDevice,
        val savedOutputProfileCount: Int,
        val hasMiniPlayer: Boolean,
        val sleepTimerRemainingMs: Long?,
        val sleepTimerEndOfTrack: Boolean,
        val sleepTimerFading: Boolean
    )

    init {
        viewModelScope.launch {
            val baseFlow = combine(
                preferences.settings,
                downloadManager.completedDownloads,
                localState
            ) { userSettings, completedDownloads, local ->
                local.copy(
                    settings = userSettings,
                    completedDownloadsCount = completedDownloads.size
                )
            }
            val runtimeFlow = combine(
                playbackController.activeOutputDevice,
                playbackController.savedOutputProfiles,
                playbackController.playbackState
            ) { device, profiles, playback ->
                PlaybackRuntimeSnapshot(
                    activeOutputDevice = device,
                    savedOutputProfileCount = profiles.size,
                    hasMiniPlayer = playback.currentTrack != null,
                    sleepTimerRemainingMs = playback.sleepTimerRemainingMs,
                    sleepTimerEndOfTrack = playback.sleepTimerEndOfTrack,
                    sleepTimerFading = playback.sleepTimerFading
                )
            }
            combine(baseFlow, runtimeFlow) { base, runtime ->
                base.copy(
                    activeOutputDevice = runtime.activeOutputDevice,
                    savedOutputProfileCount = runtime.savedOutputProfileCount,
                    hasMiniPlayer = runtime.hasMiniPlayer,
                    sleepTimerRemainingMs = runtime.sleepTimerRemainingMs,
                    sleepTimerEndOfTrack = runtime.sleepTimerEndOfTrack,
                    sleepTimerFading = runtime.sleepTimerFading
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
        refreshStorageStats()
        viewModelScope.launch(Dispatchers.IO) {
            val total = autoEqRepository.getAllProfiles().size
            val initial = autoEqRepository.search("", limit = 24)
            localState.update {
                it.copy(
                    autoEqTotalCount = total,
                    autoEqResults = initial
                )
            }
        }
    }

    fun setAutoEqQuery(query: String) {
        localState.update { it.copy(autoEqQuery = query) }
        viewModelScope.launch(Dispatchers.IO) {
            val matches = autoEqRepository.search(query, limit = 40)
            localState.update { state ->
                if (state.autoEqQuery == query) {
                    state.copy(autoEqResults = matches)
                } else {
                    state
                }
            }
        }
    }

    fun applyAutoEqProfile(profile: AutoEqProfile) {
        val current = _uiState.value.settings.eqConfig
        val updated = profile.toEqConfig(current)
        updateEqConfig(updated)
        val strings = settingsStringsFor(_uiState.value.settings.languageCode)
        val deviceName = _uiState.value.activeOutputDevice.displayName
        localState.update {
            it.copy(
                statusMessage = String.format(
                    Locale.US,
                    strings.eqAutoEqAppliedFormat,
                    profile.displayName,
                    deviceName
                )
            )
        }
    }

    fun setOledAdaptiveAccentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setOledAdaptiveAccentEnabled(enabled)
        }
    }

    fun setLyricsOnDemandTranslateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setLyricsOnDemandTranslateEnabled(enabled)
        }
    }

    fun setScrobbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setScrobbleEnabled(enabled)
        }
    }

    fun saveScrobbleConfig(
        service: String,
        username: String,
        sessionKey: String,
        apiKey: String,
        apiSecret: String
    ) {
        viewModelScope.launch {
            preferences.setScrobbleConfig(service, username, sessionKey, apiKey, apiSecret)
        }
    }

    fun setDiscordRpcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDiscordRpcEnabled(enabled)
        }
    }

    fun saveDiscordUserToken(token: String?) {
        viewModelScope.launch {
            preferences.setDiscordUserToken(token)
        }
    }

    fun setSearchQuery(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }

    fun updateEqConfig(config: EqConfig) {
        playbackController.updateEqConfig(config)
        viewModelScope.launch {
            preferences.setEqConfig(config)
        }
    }

    fun selectEqPreset(preset: EqPreset) {
        val current = _uiState.value.settings.eqConfig
        val updated = current.copy(
            enabled = true,
            activePresetId = preset.id,
            bands = EqConfig.defaultBands(preset.gainsDb)
        )
        updateEqConfig(updated)
    }

    fun updateEqBandGain(bandIndex: Int, gainDb: Float) {
        val current = _uiState.value.settings.eqConfig
        val clamped = gainDb.coerceIn(-12f, 12f)
        val updatedBands = current.bands.map { band ->
            if (band.index == bandIndex) band.copy(gainDb = clamped) else band
        }
        val matchedPreset = EqConfig.builtInPresets.firstOrNull { preset ->
            preset.gainsDb.size == updatedBands.size &&
                preset.gainsDb.indices.all { idx ->
                    kotlin.math.abs(preset.gainsDb[idx] - updatedBands[idx].gainDb) < 0.05f
                }
        }
        updateEqConfig(
            current.copy(
                activePresetId = matchedPreset?.id ?: "custom",
                bands = updatedBands
            )
        )
    }

    fun setEqEnabled(enabled: Boolean) {
        val current = _uiState.value.settings.eqConfig
        updateEqConfig(current.copy(enabled = enabled))
    }

    fun setEqPreampDb(preampDb: Float) {
        val current = _uiState.value.settings.eqConfig
        updateEqConfig(current.copy(preampDb = preampDb.coerceIn(-12f, 12f)))
    }

    fun setNormalizeLoudness(enabled: Boolean) {
        val current = _uiState.value.settings.eqConfig
        updateEqConfig(current.copy(normalizeLoudness = enabled))
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val current = _uiState.value.settings.eqConfig
        updateEqConfig(current.copy(crossfadeSeconds = seconds.coerceIn(0, 12)))
    }

    fun setGaplessEnabled(enabled: Boolean) {
        val current = _uiState.value.settings.eqConfig
        updateEqConfig(current.copy(gaplessEnabled = enabled))
    }

    fun setSmartOutputProfilesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSmartOutputProfilesEnabled(enabled)
        }
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSkipSilenceEnabled(enabled)
        }
    }

    fun setResumeOnHeadsetConnect(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setResumeOnHeadsetConnect(enabled)
        }
    }

    fun setKeepPlayingOnTaskRemoved(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setKeepPlayingOnTaskRemoved(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setKeepScreenOn(enabled)
        }
    }

    fun startSleepTimer(durationMs: Long) {
        playbackController.startSleepTimer(durationMs)
    }

    fun startSleepTimerEndOfTrack() {
        playbackController.startSleepTimerEndOfTrack()
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
    }

    fun setPlayerStyle(style: PlayerStyle) {
        viewModelScope.launch {
            preferences.setPlayerStyle(style)
        }
    }

    fun setGlassBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setGlassBlurEnabled(enabled)
        }
    }

    fun setDynamicMeshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDynamicMeshEnabled(enabled)
        }
    }

    fun setLyricsMotionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setLyricsMotionEnabled(enabled)
        }
    }

    fun setLyricsMotionStyle(style: LyricsMotionStyle) {
        viewModelScope.launch {
            preferences.setLyricsMotionStyle(style)
        }
    }

    fun setLyricsFontScale(scale: Float) {
        viewModelScope.launch {
            preferences.setLyricsFontScale(scale.coerceIn(0.75f, 1.75f))
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHapticsEnabled(enabled)
        }
    }

    fun setMonochromeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMonochromeEnabled(enabled)
        }
    }

    fun setLanguageCode(code: String) {
        val normalized = if (code.equals("vi", ignoreCase = true)) "vi" else "en"
        viewModelScope.launch {
            preferences.setLanguageCode(normalized)
            ytMusicClient.setLocale(
                countryCode = if (normalized == "vi") "VN" else "US",
                languageTag = normalized
            )
        }
    }

    fun updateUsername(username: String) {
        viewModelScope.launch {
            preferences.setUsername(username.trim())
        }
    }

    fun updateAvatarFromUri(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    if (bytes.isEmpty()) return@runCatching null
                    context.filesDir.listFiles { file ->
                        file.name.startsWith("onda_avatar")
                    }?.forEach { it.delete() }
                    val target = File(context.filesDir, "onda_avatar_${System.currentTimeMillis()}.jpg")
                    target.writeBytes(bytes)
                    target.absolutePath
                }.getOrNull()
            }
            if (savedPath != null) {
                preferences.setAvatarPath(savedPath)
                val strings = settingsStringsFor(_uiState.value.settings.languageCode)
                localState.update { it.copy(statusMessage = strings.avatarUpdatedMsg) }
            }
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.filesDir.listFiles { file ->
                    file.name.startsWith("onda_avatar")
                }?.forEach { it.delete() }
            }
            preferences.setAvatarPath(null)
        }
    }

    fun updateYtCookie(cookie: String?) {
        val cleaned = cookie?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            preferences.setYtCookie(cleaned)
            ytMusicClient.setCookie(cleaned)
        }
    }

    fun updateProxyUrl(proxyUrl: String?) {
        val cleaned = proxyUrl?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            preferences.setProxyUrl(cleaned)
        }
    }

    fun updateLinkInput(input: String) {
        val parsed = if (input.isBlank()) null else linkImporter.detect(input)
        localState.update {
            it.copy(
                linkImport = it.linkImport.copy(
                    inputUrl = input,
                    detectedProvider = parsed?.provider,
                    detectedKind = parsed?.kind,
                    errorMessage = null
                )
            )
        }
    }

    fun importLink(rawUrl: String) {
        val trimmed = rawUrl.trim()
        val strings = settingsStringsFor(_uiState.value.settings.languageCode)
        if (trimmed.isEmpty()) {
            localState.update {
                it.copy(
                    linkImport = it.linkImport.copy(errorMessage = strings.linkImportEmptyError)
                )
            }
            return
        }
        viewModelScope.launch {
            localState.update {
                it.copy(
                    linkImport = it.linkImport.copy(
                        isImporting = true,
                        errorMessage = null,
                        lastImportedTitle = null
                    )
                )
            }
            val preferMono = _uiState.value.settings.monochromeEnabled
            val result = withContext(Dispatchers.IO) {
                linkImporter.importAndMatch(trimmed, preferMonochrome = preferMono)
            }
            result.onSuccess { (collection, tracks) ->
                withContext(Dispatchers.IO) {
                    if (tracks.isNotEmpty()) {
                        songDao.upsertAndMergeAll(tracks.map { it.copy(inLibrary = true).toEntity() })
                        if (tracks.size > 1) {
                            val parsed = linkImporter.detect(trimmed)
                            val playlistId = buildSettingsImportedPlaylistId(
                                provider = collection.provider,
                                parsed = parsed,
                                collectionTitle = collection.title,
                                rawUrl = trimmed
                            )
                            val now = System.currentTimeMillis()
                            val playlist = Playlist(
                                id = playlistId,
                                name = collection.title,
                                description = collection.description,
                                artworkUrl = collection.artworkUrl ?: tracks.firstOrNull()?.artworkUrl,
                                trackCount = tracks.size,
                                createdAtMs = now,
                                remoteId = parsed?.externalId
                            )
                            val entries = tracks.mapIndexed { idx, track ->
                                PlaylistSongEntity(
                                    playlistId = playlistId,
                                    songId = track.id,
                                    position = idx,
                                    addedAtMs = now
                                )
                            }
                            playlistDao.upsertAndMergeAll(listOf(playlist.toEntity()), entries)
                        }
                    }
                }
                localState.update {
                    it.copy(
                        linkImport = it.linkImport.copy(
                            inputUrl = "",
                            detectedProvider = null,
                            detectedKind = null,
                            isImporting = false,
                            lastImportedTitle = collection.title,
                            lastImportedCount = tracks.size,
                            errorMessage = null
                        ),
                        statusMessage = String.format(
                            strings.linkImportedSummary,
                            tracks.size,
                            collection.title
                        )
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        linkImport = it.linkImport.copy(
                            isImporting = false,
                            errorMessage = err.message ?: "Link import failed"
                        )
                    )
                }
            }
        }
    }

    fun setMaxCacheMb(maxCacheMb: Int) {
        viewModelScope.launch {
            preferences.setMaxCacheMb(maxCacheMb.coerceAtLeast(64))
        }
    }

    fun setDownloadTreeUri(uri: Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                runCatching {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                }
            }
            preferences.setDownloadTreeUri(uri?.toString())
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val (cacheBytes, downloadsBytes) = withContext(Dispatchers.IO) {
                val appCache = directorySize(context.cacheDir) +
                    (context.externalCacheDir?.let { directorySize(it) } ?: 0L)
                val downloads = directorySize(File(context.filesDir, "onda_downloads"))
                appCache to downloads
            }
            localState.update {
                it.copy(
                    cacheSizeBytes = cacheBytes,
                    downloadsSizeBytes = downloadsBytes,
                    cachedStreamCount = streamResolver.cachedSize()
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            localState.update { it.copy(isClearingCache = true) }
            withContext(Dispatchers.IO) {
                deleteChildren(context.cacheDir)
                context.externalCacheDir?.let { deleteChildren(it) }
                streamResolver.clearCache()
            }
            refreshStorageStats()
            val strings = settingsStringsFor(_uiState.value.settings.languageCode)
            localState.update {
                it.copy(
                    isClearingCache = false,
                    statusMessage = strings.cacheClearedMsg
                )
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            preferences.resetToDefaults()
            val defaults = OndaUserSettings()
            playbackController.updateEqConfig(defaults.eqConfig)
            ytMusicClient.setCookie(null)
            ytMusicClient.setLocale("US", "en")
            val strings = settingsStringsFor(defaults.languageCode)
            localState.update { it.copy(statusMessage = strings.settingsResetMsg) }
        }
    }

    fun peekMomiArchive(uri: Uri) {
        viewModelScope.launch {
            val displayName = resolveDisplayName(uri)
            localState.update {
                it.copy(
                    transfer = it.transfer.copy(
                        selectedMomiUri = uri,
                        selectedMomiFileName = displayName,
                        momiPeek = null,
                        momiHasAudioLab = false,
                        isPeeking = true,
                        errorMessage = null,
                        lastSummary = null
                    )
                )
            }
            val peekResult = momiTransferImporter.peek(uri)
            val hasAudioLab = withContext(Dispatchers.IO) {
                detectArchiveEntry(uri, "settings/prefs/audio_lab.json")
            }
            peekResult.onSuccess { peek ->
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            momiPeek = peek,
                            momiHasAudioLab = hasAudioLab,
                            isPeeking = false,
                            errorMessage = null
                        )
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            momiPeek = null,
                            momiHasAudioLab = false,
                            isPeeking = false,
                            errorMessage = err.message ?: "Invalid Momi backup ZIP"
                        )
                    )
                }
            }
        }
    }

    fun setMomiMergePolicy(policy: MergePolicy) {
        localState.update {
            it.copy(transfer = it.transfer.copy(momiMergePolicy = policy))
        }
    }

    fun importMomiArchive(uri: Uri, policy: MergePolicy) {
        viewModelScope.launch {
            localState.update {
                it.copy(
                    transfer = it.transfer.copy(
                        isRunning = true,
                        progress = null,
                        errorMessage = null,
                        lastSummary = null
                    )
                )
            }
            val result = momiTransferImporter.importFromUri(uri, policy) { progress ->
                localState.update {
                    it.copy(transfer = it.transfer.copy(progress = progress))
                }
            }
            result.onSuccess { summary ->
                val updated = preferences.currentSettings()
                playbackController.updateEqConfig(updated.eqConfig)
                val strings = settingsStringsFor(updated.languageCode)
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            isRunning = false,
                            lastSummary = summary,
                            errorMessage = null
                        ),
                        statusMessage = strings.backupCompletedMsg
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            isRunning = false,
                            errorMessage = err.message ?: "Momi import failed"
                        )
                    )
                }
            }
        }
    }

    fun peekOndaBackup(uri: Uri) {
        viewModelScope.launch {
            val displayName = resolveDisplayName(uri)
            localState.update {
                it.copy(
                    transfer = it.transfer.copy(
                        selectedOndaUri = uri,
                        selectedOndaFileName = displayName,
                        ondaManifest = null,
                        isPeeking = true,
                        errorMessage = null,
                        lastSummary = null
                    )
                )
            }
            ondaBackupManager.peekBackup(uri)
                .onSuccess { manifest ->
                    localState.update {
                        it.copy(
                            transfer = it.transfer.copy(
                                ondaManifest = manifest,
                                isPeeking = false,
                                errorMessage = null
                            )
                        )
                    }
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            transfer = it.transfer.copy(
                                ondaManifest = null,
                                isPeeking = false,
                                errorMessage = err.message ?: "Invalid Onda backup ZIP"
                            )
                        )
                    }
                }
        }
    }

    fun setOndaMergePolicy(policy: MergePolicy) {
        localState.update {
            it.copy(transfer = it.transfer.copy(ondaMergePolicy = policy))
        }
    }

    fun exportOndaBackup(uri: Uri) {
        viewModelScope.launch {
            localState.update {
                it.copy(
                    transfer = it.transfer.copy(
                        isRunning = true,
                        progress = null,
                        errorMessage = null,
                        lastSummary = null
                    )
                )
            }
            val result = ondaBackupManager.exportBackup(uri) { progress ->
                localState.update {
                    it.copy(transfer = it.transfer.copy(progress = progress))
                }
            }
            val strings = settingsStringsFor(_uiState.value.settings.languageCode)
            result.onSuccess { summary ->
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            isRunning = false,
                            lastSummary = summary,
                            errorMessage = null
                        ),
                        statusMessage = strings.backupCompletedMsg
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            isRunning = false,
                            errorMessage = err.message ?: "Onda export failed"
                        )
                    )
                }
            }
        }
    }

    fun importOndaBackup(uri: Uri, policy: MergePolicy) {
        viewModelScope.launch {
            localState.update {
                it.copy(
                    transfer = it.transfer.copy(
                        isRunning = true,
                        progress = null,
                        errorMessage = null,
                        lastSummary = null
                    )
                )
            }
            val result = ondaBackupManager.importBackup(uri, policy) { progress ->
                localState.update {
                    it.copy(transfer = it.transfer.copy(progress = progress))
                }
            }
            result.onSuccess { summary ->
                val updated = preferences.currentSettings()
                playbackController.updateEqConfig(updated.eqConfig)
                ytMusicClient.setLocale(
                    countryCode = if (updated.languageCode == "vi") "VN" else "US",
                    languageTag = updated.languageCode
                )
                val strings = settingsStringsFor(updated.languageCode)
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            isRunning = false,
                            lastSummary = summary,
                            errorMessage = null
                        ),
                        statusMessage = strings.backupCompletedMsg
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        transfer = it.transfer.copy(
                            isRunning = false,
                            errorMessage = err.message ?: "Onda restore failed"
                        )
                    )
                }
            }
        }
    }

    fun clearTransferStatus() {
        localState.update {
            it.copy(
                transfer = it.transfer.copy(
                    progress = null,
                    lastSummary = null,
                    errorMessage = null
                )
            )
        }
    }

    fun clearStatusMessage() {
        localState.update { it.copy(statusMessage = null) }
    }

    private suspend fun resolveDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun detectArchiveEntry(uri: Uri, targetPath: String): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val normalized = entry.name.replace('\\', '/')
                        if (normalized == targetPath) return@use true
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    false
                }
            } ?: false
        }.getOrDefault(false)
    }

    private fun directorySize(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    total += child.length()
                }
            }
        }
        return total
    }

    private fun deleteChildren(root: File) {
        if (!root.exists() || !root.isDirectory) return
        root.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }
}
