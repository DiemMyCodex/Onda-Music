package com.onda.music.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaLoadingDots
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.ondaBlurSource
import com.onda.music.core.design.pressBounce
import com.onda.music.core.design.rememberOndaBackdropState
import com.onda.music.core.design.rememberRevealSeenState
import com.onda.music.core.design.revealOnAppear
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
    onReplayOnboarding: () -> Unit = {},
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
        onSmartOutputProfilesChange = viewModel::setSmartOutputProfilesEnabled,
        onSleepTimerSelectMs = viewModel::startSleepTimer,
        onSleepTimerEndOfTrack = viewModel::startSleepTimerEndOfTrack,
        onSleepTimerCancel = viewModel::cancelSleepTimer,
        onSkipSilenceChange = viewModel::setSkipSilenceEnabled,
        onResumeHeadsetChange = viewModel::setResumeOnHeadsetConnect,
        onKeepPlayingChange = viewModel::setKeepPlayingOnTaskRemoved,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
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
        onReplayOnboarding = onReplayOnboarding,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
    onSmartOutputProfilesChange: (Boolean) -> Unit,
    onSleepTimerSelectMs: (Long) -> Unit,
    onSleepTimerEndOfTrack: () -> Unit,
    onSleepTimerCancel: () -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onResumeHeadsetChange: (Boolean) -> Unit,
    onKeepPlayingChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
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
    modifier: Modifier = Modifier,
    onReplayOnboarding: () -> Unit = {}
) {
    val settings = uiState.settings
    val strings = remember(settings.languageCode) { settingsStringsFor(settings.languageCode) }
    val allEntries = remember(strings) { buildSettingsSearchEntries(strings) }
    val filteredEntries = remember(allEntries, uiState.searchQuery) {
        filterSettingsEntries(allEntries, uiState.searchQuery)
    }
    val reducedMotion = OndaTheme.reducedMotion
    val focusManager = LocalFocusManager.current

    var activeGroup by rememberSaveable { mutableStateOf<SettingsGroup?>(null) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showEqSheet by rememberSaveable { mutableStateOf(false) }
    var showPlayerStyleSheet by rememberSaveable { mutableStateOf(false) }
    var showTransferSheet by rememberSaveable { mutableStateOf(false) }
    var showUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var showYtCookieDialog by rememberSaveable { mutableStateOf(false) }
    var showProxyDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    val isSearching = isSearchFocused || uiState.searchQuery.isNotBlank()

    BackHandler(enabled = activeGroup != null || isSearching) {
        if (activeGroup != null) {
            activeGroup = null
        } else {
            onSearchQueryChange("")
            isSearchFocused = false
            focusManager.clearFocus()
        }
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
    val expandedTopBarHeight = rememberSettingsTopBarExpandedHeight()
    val collapsedTopBarHeight = rememberSettingsTopBarCollapsedHeight()
    val detailTopBarHeight = rememberSettingsDetailTopBarHeight()

    val renderEntry: @Composable (SettingsSearchEntry) -> Unit = { entry ->
        SettingsEntryRow(
            entry = entry,
            uiState = uiState,
            strings = strings,
            onOpenEq = { showEqSheet = true },
            onNormalizeChange = onNormalizeChange,
            onGaplessChange = onGaplessChange,
            onSmartOutputProfilesChange = onSmartOutputProfilesChange,
            onSleepTimerSelectMs = onSleepTimerSelectMs,
            onSleepTimerEndOfTrack = onSleepTimerEndOfTrack,
            onSleepTimerCancel = onSleepTimerCancel,
            onSkipSilenceChange = onSkipSilenceChange,
            onResumeHeadsetChange = onResumeHeadsetChange,
            onKeepPlayingChange = onKeepPlayingChange,
            onKeepScreenOnChange = onKeepScreenOnChange,
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
            onOpenReset = { showResetDialog = true },
            onReplayOnboarding = onReplayOnboarding
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SettingsOledColors.CanvasBlack,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomPadding)
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = activeGroup,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                } else if (targetState != null && initialState == null) {
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f)
                    ) { it / 5 } + fadeIn(tween(190))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 6 } +
                            scaleOut(tween(220), targetScale = 0.96f) +
                            fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f)
                    ) { -it / 6 } +
                        scaleIn(
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
                            initialScale = 0.96f
                        ) +
                        fadeIn(tween(190))) togetherWith
                        (slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(160)))
                }
            },
            label = "SettingsHubTransition",
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsOledColors.CanvasBlack)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) { currentGroup ->
            if (currentGroup == null) {
                val listState = rememberLazyListState()
                val backdropState = rememberOndaBackdropState()
                val revealSeen = rememberRevealSeenState()
                val density = LocalDensity.current
                val enterPx = with(density) { 24.dp.toPx() }
                val exitPx = with(density) { 6.dp.toPx() }
                val latch = remember(listState) { booleanArrayOf(false) }

                val scrolledPastLatch by remember(listState, enterPx, exitPx) {
                    derivedStateOf {
                        val next = computeCollapseLatch(
                            currentCollapsed = latch[0],
                            firstVisibleIndex = listState.firstVisibleItemIndex,
                            firstVisibleOffsetPx = listState.firstVisibleItemScrollOffset.toFloat(),
                            enterPx = enterPx,
                            exitPx = exitPx
                        )
                        latch[0] = next
                        next
                    }
                }

                val collapseTarget = scrolledPastLatch || isSearching
                val collapseProgress by animateFloatAsState(
                    targetValue = if (collapseTarget) 1f else 0f,
                    animationSpec = if (reducedMotion) {
                        snap()
                    } else {
                        spring(dampingRatio = 0.85f, stiffness = 420f)
                    },
                    label = "SettingsHeaderCollapse"
                )

                val hubAlpha by animateFloatAsState(
                    targetValue = if (isSearching) 0f else 1f,
                    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 180),
                    label = "SettingsHubAlpha"
                )
                val showHubContent by remember { derivedStateOf { hubAlpha > 0.001f } }

                LaunchedEffect(isSearching) {
                    if (isSearching && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                        listState.scrollToItem(0)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .ondaBlurSource(
                                state = backdropState,
                                isActive = { collapseProgress > 0.01f || isSearching }
                            )
                    ) {
                        if (showHubContent) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding()
                                    .graphicsLayer { alpha = hubAlpha },
                                contentPadding = PaddingValues(bottom = bottomPadding)
                            ) {
                                item(key = "hub_top_spacer") {
                                    Spacer(modifier = Modifier.height(expandedTopBarHeight))
                                }

                                item(key = "hub_suggested") {
                                    SettingsMatteCard(
                                        backgroundColor = SettingsOledColors.MatteCard,
                                        cornerRadius = 24,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .revealOnAppear(
                                                key = "hub_suggested",
                                                seenState = revealSeen,
                                                cascade = true
                                            )
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
                                        val sleepRemainingMs = uiState.sleepTimerRemainingMs
                                        val categoryBadge = if (group == SettingsGroup.AUDIO_EQ) {
                                            when {
                                                uiState.sleepTimerEndOfTrack -> strings.sleepTimerEndOfTrackLabel
                                                sleepRemainingMs != null && sleepRemainingMs > 0L -> {
                                                    formatSettingsDurationMs(sleepRemainingMs)
                                                }
                                                else -> uiState.activeOutputDevice.displayName
                                            }
                                        } else {
                                            null
                                        }
                                        SettingsCategoryPill(
                                            icon = categoryIconFor(group),
                                            title = groupTitleFor(group, strings),
                                            trailingBadge = categoryBadge,
                                            onClick = { activeGroup = group },
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)
                                                .revealOnAppear(
                                                    key = "hub_cat_${group.id}",
                                                    seenState = revealSeen,
                                                    cascade = true
                                                )
                                        )
                                        if (index < SettingsGroup.entries.lastIndex) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isSearching,
                            enter = if (reducedMotion) {
                                fadeIn(tween(80))
                            } else {
                                fadeIn(tween(180)) + slideInVertically(tween(200)) { 18 }
                            },
                            exit = fadeOut(tween(if (reducedMotion) 60 else 130))
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = collapsedTopBarHeight + 8.dp,
                                    bottom = bottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (uiState.searchQuery.isBlank()) {
                                    item(key = "search_quick_tags") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = strings.suggestedLabel,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = SettingsOledColors.TextSecondary,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf("EQ", "Player", "Momi", "FLAC", "Cache", "Proxy").forEach { tag ->
                                                    SettingsPillButton(
                                                        text = tag,
                                                        selected = false,
                                                        onClick = { onSearchQueryChange(tag) },
                                                        unselectedColor = SettingsOledColors.MatteCard
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (filteredEntries.isEmpty()) {
                                    item(key = "empty_search") {
                                        SettingsMatteCard(
                                            backgroundColor = SettingsOledColors.MatteCard,
                                            cornerRadius = 24
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
                                    items(
                                        items = filteredEntries,
                                        key = { it.id.name }
                                    ) { entry ->
                                        SettingsSearchResultRow(
                                            icon = entryIconFor(entry.id),
                                            title = entry.title,
                                            breadcrumb = groupTitleFor(entry.group, strings),
                                            onClick = {
                                                SettingsHighlight.request(entry.id.name)
                                                isSearchFocused = false
                                                focusManager.clearFocus()
                                                activeGroup = entry.group
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsCollapsingTopBar(
                        title = strings.screenTitle,
                        avatarPath = settings.avatarPath,
                        backLabel = strings.backLabel,
                        profileLabel = strings.profileTitle,
                        searchQuery = uiState.searchQuery,
                        searchPlaceholder = strings.searchPlaceholder,
                        clearSearchLabel = strings.clearSearchLabel,
                        isSearchFocused = isSearchFocused,
                        collapseProgressProvider = { collapseProgress },
                        backdropState = backdropState,
                        glassBlurEnabled = settings.glassBlurEnabled,
                        onBack = onBack,
                        onAvatarClick = { activeGroup = SettingsGroup.APPEARANCE_LANGUAGE },
                        onSearchQueryChange = onSearchQueryChange,
                        onSearchFocusChanged = { isSearchFocused = it }
                    )
                }
            } else {
                val detailEntries = remember(allEntries, currentGroup) {
                    allEntries.filter { it.group == currentGroup }
                }
                val detailScrollState = rememberScrollState()
                LaunchedEffect(currentGroup) {
                    if (SettingsHighlight.pending == null) {
                        detailScrollState.scrollTo(0)
                    }
                }
                val settingsScroll = remember(detailScrollState, detailTopBarHeight) {
                    SettingsScroll(
                        state = detailScrollState,
                        clearTop = detailTopBarHeight + 8.dp
                    )
                }

                CompositionLocalProvider(LocalSettingsScroll provides settingsScroll) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SettingsOledColors.CanvasBlack)
                    ) {
                        SettingsDetailTopBar(
                            title = groupTitleFor(currentGroup, strings),
                            backLabel = strings.backLabel,
                            onBack = { activeGroup = null },
                            scrollOffsetPx = { detailScrollState.value.toFloat() }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(detailScrollState)
                                .imePadding()
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = bottomPadding
                                ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingsGroupCard {
                                detailEntries.forEachIndexed { index, entry ->
                                    if (index > 0) {
                                        SettingsRowDivider()
                                    }
                                    Box(modifier = Modifier.settingsHighlightTarget(entry.id.name)) {
                                        renderEntry(entry)
                                    }
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

private fun entryIconFor(id: SettingsEntryId): ImageVector = when (id) {
    SettingsEntryId.EQUALIZER_EDITOR -> Icons.Rounded.Equalizer
    SettingsEntryId.SMART_OUTPUT_PROFILES -> Icons.Rounded.Headphones
    SettingsEntryId.LOUDNESS_NORMALIZE -> Icons.Rounded.VolumeUp
    SettingsEntryId.GAPLESS_PLAYBACK -> Icons.Rounded.GraphicEq
    SettingsEntryId.CROSSFADE_DURATION -> Icons.Rounded.Tune
    SettingsEntryId.SLEEP_TIMER -> Icons.Rounded.Bedtime
    SettingsEntryId.SKIP_SILENCE -> Icons.Rounded.FastForward
    SettingsEntryId.RESUME_ON_HEADSET -> Icons.Rounded.Bluetooth
    SettingsEntryId.KEEP_PLAYING_ON_SWIPE -> Icons.Rounded.PlayCircle
    SettingsEntryId.PLAYER_STYLE_PICKER -> Icons.Rounded.Palette
    SettingsEntryId.GLASS_BLUR -> Icons.Rounded.BlurOn
    SettingsEntryId.DYNAMIC_MESH -> Icons.Rounded.Gradient
    SettingsEntryId.LYRICS_MOTION -> Icons.Rounded.MusicNote
    SettingsEntryId.LYRICS_FONT_SCALE -> Icons.Rounded.TextFields
    SettingsEntryId.HAPTIC_FEEDBACK -> Icons.Rounded.Vibration
    SettingsEntryId.KEEP_SCREEN_ON -> Icons.Rounded.BrightnessHigh
    SettingsEntryId.PROFILE_CARD -> Icons.Rounded.Person
    SettingsEntryId.LANGUAGE_TOGGLE -> Icons.Rounded.Translate
    SettingsEntryId.REPLAY_ONBOARDING -> Icons.Rounded.GraphicEq
    SettingsEntryId.MONOCHROME_FLAC -> Icons.Rounded.HighQuality
    SettingsEntryId.YT_COOKIE -> Icons.Rounded.Key
    SettingsEntryId.PROXY_URL -> Icons.Rounded.Language
    SettingsEntryId.LINK_IMPORTER -> Icons.Rounded.Link
    SettingsEntryId.TRANSFER_HUB -> Icons.Rounded.Archive
    SettingsEntryId.CACHE_CLEAR -> Icons.Rounded.DeleteSweep
    SettingsEntryId.CACHE_LIMIT -> Icons.Rounded.Storage
    SettingsEntryId.DOWNLOAD_FOLDER -> Icons.Rounded.FolderOpen
    SettingsEntryId.RESET_DEFAULTS -> Icons.Rounded.RestartAlt
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
    onSmartOutputProfilesChange: (Boolean) -> Unit,
    onSleepTimerSelectMs: (Long) -> Unit,
    onSleepTimerEndOfTrack: () -> Unit,
    onSleepTimerCancel: () -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onResumeHeadsetChange: (Boolean) -> Unit,
    onKeepPlayingChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
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
    onOpenReset: () -> Unit,
    onReplayOnboarding: () -> Unit = {}
) {
    val settings = uiState.settings
    val haptics = settings.hapticsEnabled
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
        SettingsEntryId.SMART_OUTPUT_PROFILES -> {
            val deviceSubtitle = String.format(
                Locale.US,
                strings.smartOutputActiveFormat,
                uiState.activeOutputDevice.displayName,
                uiState.savedOutputProfileCount
            )
            SettingsToggleRow(
                icon = Icons.Rounded.Headphones,
                title = entry.title,
                subtitle = deviceSubtitle,
                checked = settings.smartOutputProfilesEnabled,
                onCheckedChange = onSmartOutputProfilesChange,
                hapticsEnabled = haptics
            )
        }
        SettingsEntryId.LOUDNESS_NORMALIZE -> {
            SettingsToggleRow(
                icon = Icons.Rounded.VolumeUp,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.eqConfig.normalizeLoudness,
                onCheckedChange = onNormalizeChange,
                hapticsEnabled = haptics
            )
        }
        SettingsEntryId.GAPLESS_PLAYBACK -> {
            SettingsToggleRow(
                icon = Icons.Rounded.GraphicEq,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.eqConfig.gaplessEnabled,
                onCheckedChange = onGaplessChange,
                hapticsEnabled = haptics
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
        SettingsEntryId.SLEEP_TIMER -> {
            val remainingMs = uiState.sleepTimerRemainingMs
            val hasCountdown = remainingMs != null && remainingMs > 0L
            val isActive = hasCountdown || uiState.sleepTimerEndOfTrack
            val statusBadge = when {
                uiState.sleepTimerFading -> strings.sleepTimerFadingLabel
                uiState.sleepTimerEndOfTrack -> strings.sleepTimerEndOfTrackLabel
                hasCountdown -> formatSettingsDurationMs(remainingMs ?: 0L)
                else -> strings.offLabel
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            imageVector = Icons.Rounded.Bedtime,
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
                            text = strings.sleepTimerFadeNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = SettingsOledColors.TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = if (isActive) SettingsOledColors.TextPrimary else SettingsOledColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SettingsOledColors.IconBadge)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        val durationMs = minutes * 60_000L
                        val selected = hasCountdown && remainingMs != null &&
                            remainingMs in (durationMs - 59_000L)..durationMs
                        SettingsPillButton(
                            text = "${minutes}m",
                            selected = selected,
                            onClick = { onSleepTimerSelectMs(durationMs) }
                        )
                    }
                    SettingsPillButton(
                        text = strings.sleepTimerEndOfTrackLabel,
                        selected = uiState.sleepTimerEndOfTrack,
                        onClick = onSleepTimerEndOfTrack
                    )
                    if (isActive) {
                        SettingsPillButton(
                            text = strings.cancelLabel,
                            selected = false,
                            onClick = onSleepTimerCancel
                        )
                    }
                }
            }
        }
        SettingsEntryId.SKIP_SILENCE -> {
            SettingsToggleRow(
                icon = Icons.Rounded.FastForward,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.skipSilenceEnabled,
                onCheckedChange = onSkipSilenceChange,
                hapticsEnabled = haptics
            )
        }
        SettingsEntryId.RESUME_ON_HEADSET -> {
            SettingsToggleRow(
                icon = Icons.Rounded.Bluetooth,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.resumeOnHeadsetConnect,
                onCheckedChange = onResumeHeadsetChange,
                hapticsEnabled = haptics
            )
        }
        SettingsEntryId.KEEP_PLAYING_ON_SWIPE -> {
            SettingsToggleRow(
                icon = Icons.Rounded.PlayCircle,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.keepPlayingOnTaskRemoved,
                onCheckedChange = onKeepPlayingChange,
                hapticsEnabled = haptics
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
                onCheckedChange = onGlassBlurChange,
                hapticsEnabled = haptics
            )
        }
        SettingsEntryId.DYNAMIC_MESH -> {
            SettingsToggleRow(
                icon = Icons.Rounded.Gradient,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.dynamicMeshEnabled,
                onCheckedChange = onDynamicMeshChange,
                hapticsEnabled = haptics
            )
        }
        SettingsEntryId.LYRICS_MOTION -> {
            SettingsToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.lyricsMotionEnabled,
                onCheckedChange = onLyricsMotionChange,
                hapticsEnabled = haptics
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
                onCheckedChange = onHapticsChange,
                hapticsEnabled = haptics
            )
        }
        SettingsEntryId.KEEP_SCREEN_ON -> {
            SettingsToggleRow(
                icon = Icons.Rounded.BrightnessHigh,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
                hapticsEnabled = haptics
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
        SettingsEntryId.REPLAY_ONBOARDING -> {
            SettingsActionRow(
                icon = Icons.Rounded.GraphicEq,
                title = entry.title,
                subtitle = entry.subtitle,
                onClick = onReplayOnboarding
            )
        }
        SettingsEntryId.MONOCHROME_FLAC -> {
            SettingsToggleRow(
                icon = Icons.Rounded.HighQuality,
                title = entry.title,
                subtitle = entry.subtitle,
                checked = settings.monochromeEnabled,
                onCheckedChange = onMonochromeChange,
                hapticsEnabled = haptics
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
                    SettingsInlineActionButton(
                        text = strings.clearLabel,
                        textColor = SettingsOledColors.ErrorTint,
                        backgroundColor = Color.Transparent,
                        onClick = onClearDownloadFolder
                    )
                }
                SettingsInlineActionButton(
                    text = strings.chooseFolderLabel,
                    textColor = SettingsOledColors.TextPrimary,
                    backgroundColor = SettingsOledColors.IconBadge,
                    onClick = onPickDownloadFolder
                )
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

@Composable
private fun SettingsInlineActionButton(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyText: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedBg = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.45f)
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .pressBounce(interactionSource, pressedScale = 0.96f, enabled = enabled)
            .clip(RoundedCornerShape(999.dp))
            .background(resolvedBg)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 999.dp),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            OndaLoadingDots(
                dotSize = 5.dp,
                spacing = 4.dp,
                dotColor = textColor
            )
            if (!busyText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = busyText,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            SettingsInlineActionButton(
                text = strings.editNameLabel,
                textColor = SettingsOledColors.TextPrimary,
                backgroundColor = SettingsOledColors.IconBadge,
                onClick = onEditName
            )
            SettingsInlineActionButton(
                text = strings.changeAvatarLabel,
                textColor = SettingsOledColors.TextPrimary,
                backgroundColor = SettingsOledColors.IconBadge,
                onClick = onPickAvatar
            )
            if (avatarFile != null) {
                SettingsInlineActionButton(
                    text = strings.removeAvatarLabel,
                    textColor = SettingsOledColors.ErrorTint,
                    backgroundColor = Color.Transparent,
                    onClick = onClearAvatar
                )
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

        SettingsInlineActionButton(
            text = strings.importLinkButton,
            textColor = SettingsOledColors.CanvasBlack,
            backgroundColor = SettingsOledColors.TextPrimary,
            onClick = onImport,
            enabled = !state.isImporting,
            busy = state.isImporting,
            busyText = strings.importingLabel,
            modifier = Modifier.fillMaxWidth()
        )
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
        SettingsInlineActionButton(
            text = strings.clearCacheButton,
            textColor = SettingsOledColors.TextPrimary,
            backgroundColor = SettingsOledColors.IconBadge,
            onClick = onClearCache,
            enabled = !isClearing,
            busy = isClearing
        )
    }
}
