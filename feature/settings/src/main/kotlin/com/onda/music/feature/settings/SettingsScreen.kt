package com.onda.music.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.EqPreset
import com.onda.music.core.model.MergePolicy
import com.onda.music.core.model.PlayerStyle
import java.io.File
import java.util.Locale
import kotlin.math.round

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onSearchQueryChange = viewModel::setSearchQuery,
        onEqConfigChange = viewModel::updateEqConfig,
        onSelectEqPreset = viewModel::selectEqPreset,
        onEqBandGainChange = viewModel::updateEqBandGain,
        onNormalizeChange = viewModel::setNormalizeLoudness,
        onGaplessChange = viewModel::setGaplessEnabled,
        onPlayerStyleChange = viewModel::setPlayerStyle,
        onGlassBlurChange = viewModel::setGlassBlurEnabled,
        onDynamicMeshChange = viewModel::setDynamicMeshEnabled,
        onLyricsMotionChange = viewModel::setLyricsMotionEnabled,
        onLyricsFontScaleChange = viewModel::setLyricsFontScale,
        onHapticsChange = viewModel::setHapticsEnabled,
        onUsernameSave = viewModel::updateUsername,
        onAvatarPicked = viewModel::updateAvatarFromUri,
        onAvatarClear = viewModel::clearAvatar,
        onLanguageChange = viewModel::setLanguageCode,
        onMonochromeChange = viewModel::setMonochromeEnabled,
        onYtCookieSave = viewModel::updateYtCookie,
        onProxyUrlSave = viewModel::updateProxyUrl,
        onLinkInputChange = viewModel::updateLinkInput,
        onImportLink = viewModel::importLink,
        onPickMomiZip = viewModel::peekMomiArchive,
        onMomiPolicyChange = viewModel::setMomiMergePolicy,
        onImportMomi = viewModel::importMomiArchive,
        onExportOndaBackup = viewModel::exportOndaBackup,
        onPickOndaZip = viewModel::peekOndaBackup,
        onOndaPolicyChange = viewModel::setOndaMergePolicy,
        onImportOnda = viewModel::importOndaBackup,
        onClearTransferStatus = viewModel::clearTransferStatus,
        onClearCache = viewModel::clearCache,
        onMaxCacheMbChange = viewModel::setMaxCacheMb,
        onDownloadFolderPicked = viewModel::setDownloadTreeUri,
        onResetDefaults = viewModel::resetToDefaults,
        onClearStatusMessage = viewModel::clearStatusMessage,
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEqConfigChange: (EqConfig) -> Unit,
    onSelectEqPreset: (EqPreset) -> Unit,
    onEqBandGainChange: (Int, Float) -> Unit,
    onNormalizeChange: (Boolean) -> Unit,
    onGaplessChange: (Boolean) -> Unit,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    onGlassBlurChange: (Boolean) -> Unit,
    onDynamicMeshChange: (Boolean) -> Unit,
    onLyricsMotionChange: (Boolean) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onUsernameSave: (String) -> Unit,
    onAvatarPicked: (Uri) -> Unit,
    onAvatarClear: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
    onYtCookieSave: (String?) -> Unit,
    onProxyUrlSave: (String?) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onImportLink: (String) -> Unit,
    onPickMomiZip: (Uri) -> Unit,
    onMomiPolicyChange: (MergePolicy) -> Unit,
    onImportMomi: (Uri, MergePolicy) -> Unit,
    onExportOndaBackup: (Uri) -> Unit,
    onPickOndaZip: (Uri) -> Unit,
    onOndaPolicyChange: (MergePolicy) -> Unit,
    onImportOnda: (Uri, MergePolicy) -> Unit,
    onClearTransferStatus: () -> Unit,
    onClearCache: () -> Unit,
    onMaxCacheMbChange: (Int) -> Unit,
    onDownloadFolderPicked: (Uri?) -> Unit,
    onResetDefaults: () -> Unit,
    onClearStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    val strings = remember(settings.languageCode) { settingsStringsFor(settings.languageCode) }
    val allEntries = remember(strings) { buildSettingsSearchEntries(strings) }
    val filteredEntries = remember(allEntries, uiState.searchQuery) {
        filterSettingsEntries(allEntries, uiState.searchQuery)
    }
    val groupedEntries = remember(filteredEntries) {
        filteredEntries.groupBy { it.group }
    }
    val reducedMotion = OndaTheme.reducedMotion

    var activeGroup by rememberSaveable { mutableStateOf<SettingsGroup?>(null) }
    var showEqSheet by rememberSaveable { mutableStateOf(false) }
    var showPlayerStyleSheet by rememberSaveable { mutableStateOf(false) }
    var showTransferSheet by rememberSaveable { mutableStateOf(false) }
    var showUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var showYtCookieDialog by rememberSaveable { mutableStateOf(false) }
    var showProxyDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = activeGroup != null) {
        activeGroup = null
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onClearStatusMessage()
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onAvatarPicked(uri)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onDownloadFolderPicked(uri)
    }

    val bottomPadding = DockMetrics.rememberBottomPadding(hasMiniPlayer = true)

    val renderEntry: @Composable (SettingsSearchEntry) -> Unit = { entry ->
        SettingsEntryRow(
            entry = entry,
            uiState = uiState,
            strings = strings,
            onOpenEq = { showEqSheet = true },
            onNormalizeChange = onNormalizeChange,
            onGaplessChange = onGaplessChange,
            onOpenPlayerStyle = { showPlayerStyleSheet = true },
            onGlassBlurChange = onGlassBlurChange,
            onDynamicMeshChange = onDynamicMeshChange,
            onLyricsMotionChange = onLyricsMotionChange,
            onLyricsFontScaleChange = onLyricsFontScaleChange,
            onHapticsChange = onHapticsChange,
            onEditUsername = { showUsernameDialog = true },
            onPickAvatar = { avatarPickerLauncher.launch(arrayOf("image/*")) },
            onClearAvatar = onAvatarClear,
            onLanguageChange = onLanguageChange,
            onMonochromeChange = onMonochromeChange,
            onOpenYtCookie = { showYtCookieDialog = true },
            onOpenProxy = { showProxyDialog = true },
            onLinkInputChange = onLinkInputChange,
            onImportLink = { onImportLink(uiState.linkImport.inputUrl) },
            onOpenTransfer = { showTransferSheet = true },
            onClearCache = onClearCache,
            onMaxCacheMbChange = onMaxCacheMbChange,
            onPickDownloadFolder = { folderPickerLauncher.launch(null) },
            onClearDownloadFolder = { onDownloadFolderPicked(null) },
            onOpenReset = { showResetDialog = true }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SettingsOledColors.CanvasBlack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        AnimatedContent(
            targetState = activeGroup,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn() togetherWith fadeOut()
                } else if (targetState != null && initialState == null) {
                    (slideInHorizontally { it / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 6 } + fadeOut())
                }
            },
            label = "SettingsHubTransition",
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsOledColors.CanvasBlack)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) { currentGroup ->
            if (currentGroup == null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(bottom = bottomPadding)
                ) {
                    item(key = "hub_header") {
                        SettingsHubHeader(
                            title = strings.screenTitle,
                            avatarPath = settings.avatarPath,
                            backLabel = strings.backLabel,
                            profileLabel = strings.profileTitle,
                            onBack = onBack,
                            onAvatarClick = { activeGroup = SettingsGroup.APPEARANCE_LANGUAGE }
                        )
                    }

                    item(key = "hub_search_pill") {
                        SettingsSearchPill(
                            query = uiState.searchQuery,
                            placeholder = strings.searchPlaceholder,
                            clearLabel = strings.clearSearchLabel,
                            onQueryChange = onSearchQueryChange,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp, bottom = 16.dp)
                        )
                    }

                    if (uiState.searchQuery.isBlank()) {
                        item(key = "hub_suggested") {
                            SettingsMatteCard(
                                backgroundColor = SettingsOledColors.MatteCard,
                                cornerRadius = 24,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = strings.suggestedLabel,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SettingsOledColors.TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SettingsSuggestedRow(
                                        icon = Icons.Rounded.Equalizer,
                                        title = strings.eqTitle,
                                        breadcrumb = strings.suggestedEqBreadcrumb,
                                        onClick = { showEqSheet = true }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        thickness = 1.dp,
                                        color = SettingsOledColors.InsetDivider
                                    )
                                    SettingsSuggestedRow(
                                        icon = Icons.Rounded.Archive,
                                        title = strings.transferTitle,
                                        breadcrumb = strings.suggestedTransferBreadcrumb,
                                        onClick = { showTransferSheet = true }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        SettingsGroup.entries.forEachIndexed { index, group ->
                            item(key = "category_${group.id}") {
                                SettingsCategoryPill(
                                    icon = categoryIconFor(group),
                                    title = groupTitleFor(group, strings),
                                    onClick = { activeGroup = group },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                if (index < SettingsGroup.entries.lastIndex) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    } else if (filteredEntries.isEmpty()) {
                        item(key = "empty_search") {
                            SettingsMatteCard(
                                backgroundColor = SettingsOledColors.MatteCard,
                                cornerRadius = 24,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = strings.noSearchMatchesTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SettingsOledColors.TextPrimary
                                    )
                                    Text(
                                        text = strings.noSearchMatchesBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SettingsOledColors.TextSecondary
                                    )
                                    SettingsPillButton(
                                        text = strings.clearSearchLabel,
                                        selected = true,
                                        onClick = { onSearchQueryChange("") }
                                    )
                                }
                            }
                        }
                    } else {
                        SettingsGroup.entries.forEach { group ->
                            val groupItems = groupedEntries[group].orEmpty()
                            if (groupItems.isNotEmpty()) {
                                item(key = "search_${group.id}") {
                                    SettingsGroupCard(
                                        title = groupTitleFor(group, strings),
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp)
                                    ) {
                                        groupItems.forEachIndexed { index, entry ->
                                            if (index > 0) {
                                                SettingsRowDivider()
                                            }
                                            renderEntry(entry)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val detailEntries = remember(allEntries, currentGroup) {
                    allEntries.filter { it.group == currentGroup }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SettingsOledColors.CanvasBlack)
                ) {
                    SettingsDetailTopBar(
                        title = groupTitleFor(currentGroup, strings),
                        backLabel = strings.backLabel,
                        onBack = { activeGroup = null }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "detail_${currentGroup.id}") {
                            SettingsGroupCard {
                                detailEntries.forEachIndexed { index, entry ->
                                    if (index > 0) {
                                        SettingsRowDivider()
                                    }
                                    renderEntry(entry)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEqSheet) {
        EqEditorSheet(
            eqConfig = settings.eqConfig,
            strings = strings,
            onEqConfigChange = onEqConfigChange,
            onSelectPreset = onSelectEqPreset,
            onBandGainChange = onEqBandGainChange,
            onDismiss = { showEqSheet = false }
        )
    }

    if (showPlayerStyleSheet) {
        PlayerStylePickerSheet(
            selectedStyle = settings.playerStyle,
            glassBlurEnabled = settings.glassBlurEnabled,
            dynamicMeshEnabled = settings.dynamicMeshEnabled,
            lyricsMotionEnabled = settings.lyricsMotionEnabled,
            hapticsEnabled = settings.hapticsEnabled,
            strings = strings,
            onSelectStyle = onPlayerStyleChange,
            onGlassBlurChange = onGlassBlurChange,
            onDynamicMeshChange = onDynamicMeshChange,
            onLyricsMotionChange = onLyricsMotionChange,
            onHapticsChange = onHapticsChange,
            onDismiss = { showPlayerStyleSheet = false }
        )
    }

    if (showTransferSheet) {
        TransferSheet(
            transferState = uiState.transfer,
            strings = strings,
            onPickMomiZip = onPickMomiZip,
            onMomiPolicyChange = onMomiPolicyChange,
            onImportMomi = onImportMomi,
            onExportOndaBackup = onExportOndaBackup,
            onPickOndaZip = onPickOndaZip,
            onOndaPolicyChange = onOndaPolicyChange,
            onImportOnda = onImportOnda,
            onClearStatus = onClearTransferStatus,
            onDismiss = { showTransferSheet = false }
        )
    }

    if (showUsernameDialog) {
        SettingsInputDialog(
            title = strings.profileTitle,
            initialValue = settings.username,
            placeholder = strings.defaultListenerName,
            saveLabel = strings.saveLabel,
            cancelLabel = strings.cancelLabel,
            clearLabel = null,
            onDismiss = { showUsernameDialog = false },
            onSave = { updated ->
                onUsernameSave(updated)
                showUsernameDialog = false
            }
        )
    }

    if (showYtCookieDialog) {
        SettingsInputDialog(
            title = strings.ytCookieTitle,
            initialValue = settings.ytCookie.orEmpty(),
            placeholder = "SAPISID=...; SID=...;",
            saveLabel = strings.saveLabel,
            cancelLabel = strings.cancelLabel,
            clearLabel = strings.clearLabel,
            singleLine = false,
            onDismiss = { showYtCookieDialog = false },
            onSave = { updated ->
                onYtCookieSave(updated)
                showYtCookieDialog = false
            }
        )
    }

    if (showProxyDialog) {
        SettingsInputDialog(
            title = strings.proxyTitle,
            initialValue = settings.proxyUrl.orEmpty(),
            placeholder = "http://127.0.0.1:7890",
            saveLabel = strings.saveLabel,
            cancelLabel = strings.cancelLabel,
            clearLabel = strings.clearLabel,
            onDismiss = { showProxyDialog = false },
            onSave = { updated ->
                onProxyUrlSave(updated)
                showProxyDialog = false
            }
        )
    }

    if (showResetDialog) {
        SettingsConfirmDialog(
            title = strings.resetConfirmTitle,
            body = strings.resetConfirmBody,
            confirmLabel = strings.confirmResetLabel,
            cancelLabel = strings.cancelLabel,
            onDismiss = { showResetDialog = false },
            onConfirm = {
                onResetDefaults()
                showResetDialog = false
            }
        )
    }
}

private fun groupTitleFor(group: SettingsGroup, strings: SettingsStrings): String = when (group) {
    SettingsGroup.AUDIO_EQ -> strings.groupAudioEq
    SettingsGroup.NOW_PLAYING_MOTION -> strings.groupNowPlayingMotion
    SettingsGroup.APPEARANCE_LANGUAGE -> strings.groupAppearanceLanguage
    SettingsGroup.SOURCES_LINK_IMPORT -> strings.groupSourcesLinkImport
    SettingsGroup.STORAGE_TRANSFER -> strings.groupStorageTransfer
}

private fun categoryIconFor(group: SettingsGroup): ImageVector = when (group) {
    SettingsGroup.AUDIO_EQ -> Icons.Rounded.Equalizer
    SettingsGroup.NOW_PLAYING_MOTION -> Icons.Rounded.Palette
    SettingsGroup.APPEARANCE_LANGUAGE -> Icons.Rounded.Translate
    SettingsGroup.SOURCES_LINK_IMPORT -> Icons.Rounded.Link
    SettingsGroup.STORAGE_TRANSFER -> Icons.Rounded.Storage
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsEntryRow(
    entry: SettingsSearchEntry,
    uiState: SettingsUiState,
    strings: SettingsStrings,
    onOpenEq: () -> Unit,
    onNormalizeChange: (Boolean) -> Unit,
    onGaplessChange: (Boolean) -> Unit,
    onOpenPlayerStyle: () -> Unit,
    onGlassBlurChange: (Boolean) -> Unit,
    onDynamicMeshChange: (Boolean) -> Unit,
    onLyricsMotionChange: (Boolean) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onEditUsername: () -> Unit,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
    onOpenYtCookie: () -> Unit,
    onOpenProxy: () -> Unit,
    onLinkInputChange: (String) -> Unit,
    onImportLink: () -> Unit,
    onOpenTransfer: () -> Unit,
    onClearCache: () -> Unit,
    onMaxCacheMbChange: (Int) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onClearDownloadFolder: () -> Unit,
    onOpenReset: () -> Unit
) {
    val settings = uiState.settings
    when (entry.id) {
        SettingsEntryId.EQUALIZER_EDITOR -> {
            val presetLabel = EqConfig.builtInPresets.firstOrNull {
                it.id == settings.eqConfig.activePresetId
            }?.name ?: strings.eqCustomLabel
            SettingsActionRow(
                icon = Icons.Rounded.Equalizer,
                title = entry.title,
                subtitle = entry.subtitle,
                badgeText = if (settings.eqConfig.enabled) presetLabel else strings.offLabel,
                badgeAccent = settings.eqConfig.enabled,
                onClick = onOpenEq
            )
        }
        SettingsEntryId.LOUDNESS_NORMALIZE -> {
            SettingsToggleRow(
                icon = Icons.Rounded.VolumeUp,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.eqConfig.normalizeLoudness,
                onCheckedChange = onNormalizeChange
            )
        }
        SettingsEntryId.GAPLESS_PLAYBACK -> {
            SettingsToggleRow(
                icon = Icons.Rounded.GraphicEq,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.eqConfig.gaplessEnabled,
                onCheckedChange = onGaplessChange
            )
        }
        SettingsEntryId.CROSSFADE_DURATION -> {
            SettingsActionRow(
                icon = Icons.Rounded.Tune,
                title = entry.title,
                subtitle = entry.subtitle,
                badgeText = if (settings.eqConfig.crossfadeSeconds == 0) {
                    strings.offLabel
                } else {
                    "${settings.eqConfig.crossfadeSeconds}s"
                },
                badgeAccent = settings.eqConfig.crossfadeSeconds > 0,
                onClick = onOpenEq
            )
        }
        SettingsEntryId.PLAYER_STYLE_PICKER -> {
            SettingsActionRow(
                icon = Icons.Rounded.Palette,
                title = entry.title,
                subtitle = entry.subtitle,
                badgeText = settings.playerStyle.label,
                badgeAccent = true,
                onClick = onOpenPlayerStyle
            )
        }
        SettingsEntryId.GLASS_BLUR -> {
            SettingsToggleRow(
                icon = Icons.Rounded.BlurOn,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.glassBlurEnabled,
                onCheckedChange = onGlassBlurChange
            )
        }
        SettingsEntryId.DYNAMIC_MESH -> {
            SettingsToggleRow(
                icon = Icons.Rounded.Gradient,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.dynamicMeshEnabled,
                onCheckedChange = onDynamicMeshChange
            )
        }
        SettingsEntryId.LYRICS_MOTION -> {
            SettingsToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.lyricsMotionEnabled,
                onCheckedChange = onLyricsMotionChange
            )
        }
        SettingsEntryId.LYRICS_FONT_SCALE -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SettingsOledColors.IconBadge),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextFields,
                            contentDescription = null,
                            tint = SettingsOledColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SettingsOledColors.TextPrimary
                        )
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SettingsOledColors.TextSecondary
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.2fx", settings.lyricsFontScale),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = SettingsOledColors.TextPrimary
                    )
                }
                Slider(
                    value = settings.lyricsFontScale,
                    onValueChange = { raw ->
                        val snapped = (round(raw * 20f) / 20f).coerceIn(0.75f, 1.75f)
                        onLyricsFontScaleChange(snapped)
                    },
                    valueRange = 0.75f..1.75f,
                    colors = SliderDefaults.colors(
                        thumbColor = SettingsOledColors.TextPrimary,
                        activeTrackColor = SettingsOledColors.TextPrimary,
                        inactiveTrackColor = SettingsOledColors.InactiveTrack
                    )
                )
            }
        }
        SettingsEntryId.HAPTIC_FEEDBACK -> {
            SettingsToggleRow(
                icon = Icons.Rounded.Vibration,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.hapticsEnabled,
                onCheckedChange = onHapticsChange
            )
        }
        SettingsEntryId.PROFILE_CARD -> {
            ProfileSettingsCardRow(
                username = settings.username,
                avatarPath = settings.avatarPath,
                strings = strings,
                onEditName = onEditUsername,
                onPickAvatar = onPickAvatar,
                onClearAvatar = onClearAvatar
            )
        }
        SettingsEntryId.LANGUAGE_TOGGLE -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SettingsOledColors.IconBadge),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = SettingsOledColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SettingsOledColors.TextPrimary
                        )
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SettingsOledColors.TextSecondary
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsPillButton(
                        text = strings.languageEnglish,
                        selected = settings.languageCode != "vi",
                        onClick = { onLanguageChange("en") },
                        modifier = Modifier.weight(1f)
                    )
                    SettingsPillButton(
                        text = strings.languageVietnamese,
                        selected = settings.languageCode == "vi",
                        onClick = { onLanguageChange("vi") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        SettingsEntryId.MONOCHROME_FLAC -> {
            SettingsToggleRow(
                icon = Icons.Rounded.HighQuality,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.monochromeEnabled,
                onCheckedChange = onMonochromeChange
            )
        }
        SettingsEntryId.YT_COOKIE -> {
            val hasCookie = !settings.ytCookie.isNullOrBlank()
            SettingsActionRow(
                icon = Icons.Rounded.Key,
                title = entry.title,
                subtitle = entry.subtitle,
                badgeText = if (hasCookie) strings.configuredLabel else strings.notSetLabel,
                badgeAccent = hasCookie,
                onClick = onOpenYtCookie
            )
        }
        SettingsEntryId.PROXY_URL -> {
            val proxy = settings.proxyUrl
            SettingsActionRow(
                icon = Icons.Rounded.Language,
                title = entry.title,
                subtitle = entry.subtitle,
                badgeText = proxy?.takeIf { it.isNotBlank() } ?: strings.notSetLabel,
                badgeAccent = !proxy.isNullOrBlank(),
                onClick = onOpenProxy
            )
        }
        SettingsEntryId.LINK_IMPORTER -> {
            LinkImporterRow(
                state = uiState.linkImport,
                strings = strings,
                onInputChange = onLinkInputChange,
                onImport = onImportLink
            )
        }
        SettingsEntryId.TRANSFER_HUB -> {
            SettingsActionRow(
                icon = Icons.Rounded.Archive,
                title = entry.title,
                subtitle = entry.subtitle,
                badgeText = "ZIP",
                badgeAccent = true,
                onClick = onOpenTransfer
            )
        }
        SettingsEntryId.CACHE_CLEAR -> {
            CacheControlRow(
                cacheSizeBytes = uiState.cacheSizeBytes,
                downloadsSizeBytes = uiState.downloadsSizeBytes,
                cachedStreamCount = uiState.cachedStreamCount,
                isClearing = uiState.isClearingCache,
                strings = strings,
                onClearCache = onClearCache
            )
        }
        SettingsEntryId.CACHE_LIMIT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SettingsOledColors.IconBadge),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = SettingsOledColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SettingsOledColors.TextPrimary
                        )
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SettingsOledColors.TextSecondary
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(256, 512, 1024, 2048).forEach { mb ->
                        SettingsPillButton(
                            text = if (mb >= 1024) "${mb / 1024} GB" else "$mb MB",
                            selected = settings.maxCacheMb == mb,
                            onClick = { onMaxCacheMbChange(mb) }
                        )
                    }
                }
            }
        }
        SettingsEntryId.DOWNLOAD_FOLDER -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SettingsOledColors.IconBadge),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = SettingsOledColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SettingsOledColors.TextPrimary
                    )
                    Text(
                        text = settings.downloadTreeUri ?: entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (!settings.downloadTreeUri.isNullOrBlank()) {
                    TextButton(
                        onClick = onClearDownloadFolder,
                        modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    ) {
                        Text(text = strings.clearLabel, color = SettingsOledColors.ErrorTint)
                    }
                }
                Button(
                    onClick = onPickDownloadFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SettingsOledColors.IconBadge,
                        contentColor = SettingsOledColors.TextPrimary
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                ) {
                    Text(text = strings.chooseFolderLabel, maxLines = 1)
                }
            }
        }
        SettingsEntryId.RESET_DEFAULTS -> {
            SettingsActionRow(
                icon = Icons.Rounded.RestartAlt,
                title = entry.title,
                subtitle = entry.subtitle,
                destructive = true,
                onClick = onOpenReset
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSettingsCardRow(
    username: String,
    avatarPath: String?,
    strings: SettingsStrings,
    onEditName: () -> Unit,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit
) {
    val avatarFile = remember(avatarPath) {
        avatarPath?.let { File(it) }?.takeIf { it.exists() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SettingsOledColors.IconBadge),
                contentAlignment = Alignment.Center
            ) {
                if (avatarFile != null) {
                    AsyncImage(
                        model = avatarFile,
                        contentDescription = strings.profileTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = SettingsOledColors.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = username.ifBlank { strings.defaultListenerName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsOledColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strings.profileSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsOledColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onEditName,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SettingsOledColors.IconBadge,
                    contentColor = SettingsOledColors.TextPrimary
                ),
                elevation = null,
                modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(text = strings.editNameLabel)
            }
            Button(
                onClick = onPickAvatar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SettingsOledColors.IconBadge,
                    contentColor = SettingsOledColors.TextPrimary
                ),
                elevation = null,
                modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(text = strings.changeAvatarLabel)
            }
            if (avatarFile != null) {
                TextButton(
                    onClick = onClearAvatar,
                    modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                ) {
                    Text(text = strings.removeAvatarLabel, color = SettingsOledColors.ErrorTint)
                }
            }
        }
    }
}

@Composable
private fun LinkImporterRow(
    state: LinkImportUiState,
    strings: SettingsStrings,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SettingsOledColors.IconBadge),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = SettingsOledColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.linkImporterTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsOledColors.TextPrimary
                )
                Text(
                    text = strings.linkImporterSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsOledColors.TextSecondary
                )
            }
            state.detectedProvider?.let { provider ->
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = SettingsOledColors.TextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SettingsOledColors.IconBadge)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        OutlinedTextField(
            value = state.inputUrl,
            onValueChange = onInputChange,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = {
                Text(
                    text = strings.linkInputPlaceholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsOledColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SettingsOledColors.TextPrimary,
                unfocusedTextColor = SettingsOledColors.TextPrimary,
                focusedContainerColor = SettingsOledColors.IconBadge,
                unfocusedContainerColor = SettingsOledColors.IconBadge,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = SettingsOledColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        state.errorMessage?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsOledColors.ErrorTint
            )
        }
        state.lastImportedTitle?.let { title ->
            Text(
                text = String.format(strings.linkImportedSummary, state.lastImportedCount, title),
                style = MaterialTheme.typography.bodySmall,
                color = SettingsOledColors.SuccessTint
            )
        }

        Button(
            onClick = onImport,
            enabled = !state.isImporting,
            colors = ButtonDefaults.buttonColors(
                containerColor = SettingsOledColors.TextPrimary,
                contentColor = SettingsOledColors.CanvasBlack
            ),
            elevation = null,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
        ) {
            if (state.isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = SettingsOledColors.CanvasBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = strings.importingLabel)
            } else {
                Text(text = strings.importLinkButton)
            }
        }
    }
}

@Composable
private fun CacheControlRow(
    cacheSizeBytes: Long,
    downloadsSizeBytes: Long,
    cachedStreamCount: Int,
    isClearing: Boolean,
    strings: SettingsStrings,
    onClearCache: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SettingsOledColors.IconBadge),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = null,
                tint = SettingsOledColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = strings.cacheSizeTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SettingsOledColors.TextPrimary
            )
            Text(
                text = "${formatByteSize(cacheSizeBytes)} · $cachedStreamCount URL · " +
                    "Offline: ${formatByteSize(downloadsSizeBytes)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = SettingsOledColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onClearCache,
            enabled = !isClearing,
            colors = ButtonDefaults.buttonColors(
                containerColor = SettingsOledColors.IconBadge,
                contentColor = SettingsOledColors.TextPrimary
            ),
            elevation = null,
            modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget),
            shape = RoundedCornerShape(999.dp)
        ) {
            if (isClearing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = SettingsOledColors.TextPrimary
                )
            } else {
                Text(text = strings.clearCacheButton, maxLines = 1)
            }
        }
    }
}
