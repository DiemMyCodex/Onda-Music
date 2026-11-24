package com.onda.music.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.navigation.compose.hiltViewModel
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.design.homeRecede
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track

@Composable
fun SearchRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SearchScreen(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::clearQuery,
        onSubmitSearch = { viewModel.submitSearch() },
        onSelectFilter = viewModel::selectFilter,
        onSelectSuggestion = viewModel::selectSuggestion,
        onSelectHistoryItem = viewModel::selectHistoryItem,
        onDeleteHistoryItem = viewModel::deleteHistoryItem,
        onClearAllHistory = viewModel::clearAllHistory,
        onSelectMoodCategory = viewModel::selectMoodCategory,
        onRetryMoods = viewModel::loadMoodCategories,
        onPlayTrack = viewModel::playTrack,
        onToggleLike = viewModel::toggleLike,
        onOpenAlbum = viewModel::openAlbum,
        onOpenPlaylist = viewModel::openPlaylist,
        onSearchArtist = { viewModel.submitSearch(it.name) },
        onOpenLinkSheet = viewModel::openLinkSheet,
        onDismissLinkSheet = viewModel::dismissLinkSheet,
        onLinkInputChange = viewModel::onLinkInputChange,
        onStartLinkImport = viewModel::startLinkImport,
        onResetLinkImport = viewModel::resetLinkImport,
        onPlayImportedPlaylist = viewModel::playImportedPlaylist,
        onInspectImportedPlaylist = viewModel::inspectImportedPlaylist,
        onPlayDrillDownAll = { viewModel.playDrillDownTracks(startIndex = 0, shuffle = false) },
        onShuffleDrillDown = { viewModel.playDrillDownTracks(startIndex = 0, shuffle = true) },
        onPlayDrillDownTrack = { index -> viewModel.playDrillDownTracks(startIndex = index, shuffle = false) },
        onRetryDrillDown = viewModel::retryDrillDown,
        onDismissDrillDown = viewModel::dismissDrillDown,
        modifier = modifier
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onOpenSettings: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSubmitSearch: () -> Unit,
    onSelectFilter: (SearchSourceFilter) -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onSelectHistoryItem: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onSelectMoodCategory: (MoodCategory) -> Unit,
    onRetryMoods: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onSearchArtist: (Artist) -> Unit,
    onOpenLinkSheet: () -> Unit,
    onDismissLinkSheet: () -> Unit,
    onLinkInputChange: (String) -> Unit,
    onStartLinkImport: () -> Unit,
    onResetLinkImport: () -> Unit,
    onPlayImportedPlaylist: () -> Unit,
    onInspectImportedPlaylist: () -> Unit,
    onPlayDrillDownAll: () -> Unit,
    onShuffleDrillDown: () -> Unit,
    onPlayDrillDownTrack: (Int) -> Unit,
    onRetryDrillDown: () -> Unit,
    onDismissDrillDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val reducedMotion = OndaTheme.reducedMotion
    val revealSeen = rememberSearchRevealSeenState()

    var isSearchFocused by remember { mutableStateOf(false) }
    val browseListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()
    val focusedHistoryListState = rememberLazyListState()

    val isActiveSearch = isSearchFocused || uiState.query.isNotBlank()
    val activeProgressState = animateFloatAsState(
        targetValue = if (isActiveSearch) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(360, easing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)),
        label = "searchActiveFade"
    )
    val activeListState = when {
        uiState.query.isNotBlank() -> resultsListState
        isSearchFocused -> focusedHistoryListState
        else -> browseListState
    }
    val scrollCollapseState = rememberSearchCollapseProgress(activeListState)
    val barCollapseProvider = remember(scrollCollapseState, activeProgressState) {
        { maxOf(scrollCollapseState.value, activeProgressState.value) }
    }

    val drillDownVisible = uiState.drillDownState.isVisible
    var lastDrillDown by remember { mutableStateOf<DrillDownState?>(null) }
    var launchOrigin by remember { mutableStateOf(SearchLaunchOrigin()) }
    LaunchedEffect(uiState.drillDownState) {
        if (uiState.drillDownState.isVisible) {
            lastDrillDown = uiState.drillDownState
        }
    }
    val drillDownFractionState = animateFloatAsState(
        targetValue = if (drillDownVisible) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else if (drillDownVisible) {
            spring(dampingRatio = 0.88f, stiffness = 260f)
        } else {
            spring(dampingRatio = 0.92f, stiffness = 340f)
        },
        label = "searchDrillDownFraction"
    )

    BackHandler(enabled = drillDownVisible) {
        onDismissDrillDown()
    }
    BackHandler(enabled = !drillDownVisible && isSearchFocused && uiState.query.isBlank()) {
        isSearchFocused = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val showBrowse by remember { derivedStateOf { activeProgressState.value < 0.995f } }
    val showActive by remember { derivedStateOf { activeProgressState.value > 0.005f } }
    val showDrillDownOverlay by remember(drillDownVisible) {
        derivedStateOf { drillDownVisible || drillDownFractionState.value > 0.005f }
    }
    val topBarExpandedPadding = searchExpandedTopPadding(hasFilterRow = uiState.query.isNotBlank())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTokens.AmoledBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .homeRecede(
                    fractionProvider = { drillDownFractionState.value },
                    reducedMotion = reducedMotion
                )
        ) {
            if (showBrowse) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - activeProgressState.value }
                ) {
                    SearchBrowseBody(
                        uiState = uiState,
                        listState = browseListState,
                        topPadding = topBarExpandedPadding,
                        revealSeen = revealSeen,
                        onOpenLinkSheet = onOpenLinkSheet,
                        onSelectHistoryItem = {
                            focusManager.clearFocus()
                            isSearchFocused = false
                            onSelectHistoryItem(it)
                        },
                        onDeleteHistoryItem = onDeleteHistoryItem,
                        onClearAllHistory = onClearAllHistory,
                        onSelectMoodCategory = {
                            focusManager.clearFocus()
                            isSearchFocused = false
                            onSelectMoodCategory(it)
                        },
                        onRetryMoods = onRetryMoods
                    )
                }
            }

            if (showActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = activeProgressState.value }
                ) {
                    if (uiState.query.isBlank()) {
                        SearchFocusedIdleBody(
                            uiState = uiState,
                            listState = focusedHistoryListState,
                            topPadding = topBarExpandedPadding,
                            onOpenLinkSheet = {
                                focusManager.clearFocus()
                                isSearchFocused = false
                                onOpenLinkSheet()
                            },
                            onSelectHistoryItem = {
                                focusManager.clearFocus()
                                onSelectHistoryItem(it)
                            },
                            onDeleteHistoryItem = onDeleteHistoryItem,
                            onClearAllHistory = onClearAllHistory
                        )
                    } else {
                        SearchResultsBody(
                            uiState = uiState,
                            listState = resultsListState,
                            topPadding = topBarExpandedPadding,
                            revealSeen = revealSeen,
                            onSelectSuggestion = {
                                focusManager.clearFocus()
                                onSelectSuggestion(it)
                            },
                            onPlayTrack = onPlayTrack,
                            onToggleLike = onToggleLike,
                            onOpenAlbum = { album, bounds ->
                                launchOrigin = SearchLaunchOrigin(bounds = bounds, cornerRadius = GlassTokens.RadiusMd)
                                onOpenAlbum(album)
                            },
                            onOpenPlaylist = { playlist, bounds ->
                                launchOrigin = SearchLaunchOrigin(bounds = bounds, cornerRadius = GlassTokens.RadiusMd)
                                onOpenPlaylist(playlist)
                            },
                            onSearchArtist = {
                                focusManager.clearFocus()
                                onSearchArtist(it)
                            },
                            onRetrySearch = onSubmitSearch
                        )
                    }
                }
            }

            SearchGlassTopBar(
                collapseProgress = barCollapseProvider,
                onOpenSettings = onOpenSettings,
                below = if (uiState.query.isNotBlank()) {
                    {
                        SourceFilterRow(
                            activeFilter = uiState.activeFilter,
                            onSelectFilter = onSelectFilter
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                SmokedSearchBar(
                    query = uiState.query,
                    onQueryChange = onQueryChange,
                    onClear = onClearQuery,
                    onSubmit = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onSubmitSearch()
                    },
                    focusRequester = focusRequester,
                    isFocused = isSearchFocused,
                    onFocusChange = { focused -> isSearchFocused = focused },
                    onDismissFocus = {
                        if (uiState.query.isNotEmpty()) {
                            onClearQuery()
                        }
                        isSearchFocused = false
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }
        }

        if (uiState.linkSheetVisible) {
            LinkImportSheet(
                linkInput = uiState.linkInput,
                detectedProvider = uiState.detectedProvider,
                progress = uiState.linkImportProgress,
                onLinkInputChange = onLinkInputChange,
                onStartImport = onStartLinkImport,
                onReset = onResetLinkImport,
                onPlayImported = onPlayImportedPlaylist,
                onInspectImported = onInspectImportedPlaylist,
                onDismiss = onDismissLinkSheet
            )
        }

        val shownDrillDown = if (drillDownVisible) uiState.drillDownState else lastDrillDown
        if (shownDrillDown != null && shownDrillDown.isVisible && showDrillDownOverlay) {
            CollectionDrillDownSheet(
                state = shownDrillDown,
                currentTrackId = uiState.currentTrackId,
                isPlaying = uiState.isPlaying,
                hasMiniPlayer = uiState.hasMiniPlayer,
                openFractionProvider = { drillDownFractionState.value },
                launchOrigin = launchOrigin,
                onPlayAll = onPlayDrillDownAll,
                onShufflePlay = onShuffleDrillDown,
                onPlayTrack = onPlayDrillDownTrack,
                onToggleLike = onToggleLike,
                onRetry = onRetryDrillDown,
                onDismiss = onDismissDrillDown
            )
        }
    }
}

@Composable
private fun SearchBrowseBody(
    uiState: SearchUiState,
    listState: LazyListState,
    topPadding: Dp,
    revealSeen: SearchRevealSeenState,
    onOpenLinkSheet: () -> Unit,
    onSelectHistoryItem: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onSelectMoodCategory: (MoodCategory) -> Unit,
    onRetryMoods: () -> Unit
) {
    val moodRows = uiState.moodCategories.chunked(2)
    val coverPool = remember(uiState.ytResults, uiState.monochromeTracks, uiState.localTracks) {
        (uiState.ytResults.tracks.mapNotNull { it.artworkUrl } +
            uiState.monochromeTracks.mapNotNull { it.artworkUrl } +
            uiState.localTracks.mapNotNull { it.artworkUrl })
            .distinct()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
        contentPadding = PaddingValues(top = topPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "link_import_card") {
            Box(modifier = Modifier.searchRevealOnAppear("search_link_card", revealSeen, cascade = true)) {
                LinkImportPromoCard(
                    onClick = onOpenLinkSheet,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (uiState.history.isNotEmpty()) {
            item(key = "history_section") {
                Box(modifier = Modifier.searchRevealOnAppear("search_history_section", revealSeen, cascade = true)) {
                    SearchHistorySection(
                        history = uiState.history,
                        onSelect = onSelectHistoryItem,
                        onDelete = onDeleteHistoryItem,
                        onClearAll = onClearAllHistory
                    )
                }
            }
        }

        item(key = "moods_header") {
            Box(modifier = Modifier.searchRevealOnAppear("search_moods_header", revealSeen, cascade = true)) {
                SectionHeader(
                    title = "Moods & Genres",
                    subtitle = "Explore curated YouTube Music stations"
                )
            }
        }

        if (uiState.moodCategories.isEmpty()) {
            item(key = "moods_fallback") {
                MoodLoadFallbackCard(
                    isLoading = uiState.isLoadingMoods,
                    errorMessage = uiState.moodError,
                    onRetry = onRetryMoods
                )
            }
        } else {
            items(
                items = moodRows,
                key = { row -> row.joinToString("_") { it.id } }
            ) { pair ->
                val rowKey = "mood_row_${pair.joinToString("_") { it.id }}"
                Box(modifier = Modifier.searchRevealOnAppear(rowKey, revealSeen, cascade = true)) {
                    MoodCategoryRow(
                        pair = pair,
                        coverUrls = coverPool,
                        onSelect = onSelectMoodCategory
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFocusedIdleBody(
    uiState: SearchUiState,
    listState: LazyListState,
    topPadding: Dp,
    onOpenLinkSheet: () -> Unit,
    onSelectHistoryItem: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
        contentPadding = PaddingValues(top = topPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "focused_link_import") {
            LinkImportPromoCard(
                onClick = onOpenLinkSheet,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (uiState.history.isNotEmpty()) {
            item(key = "focused_history_header") {
                SectionHeader(
                    title = "Recent Searches",
                    actionLabel = "Clear",
                    onActionClick = onClearAllHistory
                )
            }
            items(
                items = uiState.history,
                key = { "focused_hist_$it" }
            ) { query ->
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .searchPressBounce(interactionSource, pressedScale = 0.98f)
                        .clip(RoundedCornerShape(GlassTokens.RadiusMd))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                            onClick = { onSelectHistoryItem(query) }
                        )
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = OndaTheme.colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onDeleteHistoryItem(query) },
                        modifier = Modifier.defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove $query",
                            tint = OndaTheme.colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            item(key = "focused_empty_hint") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type a song, album, artist or lyric to search across sources.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsBody(
    uiState: SearchUiState,
    listState: LazyListState,
    topPadding: Dp,
    revealSeen: SearchRevealSeenState,
    onSelectSuggestion: (String) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenAlbum: (Album, Rect?) -> Unit,
    onOpenPlaylist: (Playlist, Rect?) -> Unit,
    onSearchArtist: (Artist) -> Unit,
    onRetrySearch: () -> Unit
) {
    val visible = uiState.visibleResults

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
        contentPadding = PaddingValues(top = topPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.suggestions.isNotEmpty() && !uiState.isSubmitted) {
            items(
                items = uiState.suggestions.take(5),
                key = { "suggestion_$it" }
            ) { suggestion ->
                SearchSuggestionRow(
                    suggestion = suggestion,
                    onClick = { onSelectSuggestion(suggestion) }
                )
            }
        }

        if (uiState.isSearching) {
            item(key = "searching_indicator") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchLoadingDots(dotColor = OndaTheme.colors.accent)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Searching across sources",
                        style = MaterialTheme.typography.bodySmall,
                        color = OndaTheme.colors.textSecondary
                    )
                }
            }
        }

        if (uiState.searchError != null && visible.isEmpty && !uiState.isSearching) {
            item(key = "search_error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .smokedGlass(
                            shape = RoundedCornerShape(GlassTokens.RadiusMd),
                            elevation = GlassElevation.CARD,
                            accentTint = GlassTokens.ErrorCoral
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = uiState.searchError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textPrimary
                    )
                    Button(
                        onClick = onRetrySearch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OndaTheme.colors.accent,
                            contentColor = GlassTokens.AmoledBlack
                        ),
                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                        modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Retry")
                    }
                }
            }
        }

        if (visible.monochromeTracks.isNotEmpty()) {
            item(key = "mono_header") {
                SectionHeader(
                    title = "Monochrome FLAC",
                    subtitle = "${visible.monochromeTracks.size} lossless matches"
                )
            }
            items(
                items = visible.monochromeTracks,
                key = { "mono_${it.id}" }
            ) { track ->
                Box(modifier = Modifier.searchRevealOnAppear("mono_${track.id}", revealSeen, cascade = true)) {
                    SearchTrackRow(
                        track = track,
                        isCurrent = track.id == uiState.currentTrackId,
                        isPlaying = uiState.isPlaying,
                        onClick = { onPlayTrack(track, visible.monochromeTracks) },
                        onToggleLike = { onToggleLike(track) }
                    )
                }
            }
        }

        if (visible.ytTracks.isNotEmpty()) {
            item(key = "yt_tracks_header") {
                SectionHeader(
                    title = "YouTube Music Songs",
                    subtitle = "${visible.ytTracks.size} tracks"
                )
            }
            items(
                items = visible.ytTracks,
                key = { "yt_${it.id}" }
            ) { track ->
                Box(modifier = Modifier.searchRevealOnAppear("yt_${track.id}", revealSeen, cascade = true)) {
                    SearchTrackRow(
                        track = track,
                        isCurrent = track.id == uiState.currentTrackId,
                        isPlaying = uiState.isPlaying,
                        onClick = { onPlayTrack(track, visible.ytTracks) },
                        onToggleLike = { onToggleLike(track) }
                    )
                }
            }
        }

        if (visible.albums.isNotEmpty()) {
            item(key = "albums_header") {
                SectionHeader(title = "Albums")
            }
            item(key = "albums_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = visible.albums,
                        key = { "album_${it.id}" }
                    ) { album ->
                        var capturedBounds by remember { mutableStateOf<Rect?>(null) }
                        SearchAlbumCard(
                            album = album,
                            onCaptureBounds = { capturedBounds = it },
                            onClick = { onOpenAlbum(album, capturedBounds) }
                        )
                    }
                }
            }
        }

        if (visible.playlists.isNotEmpty()) {
            item(key = "playlists_header") {
                SectionHeader(title = "Playlists")
            }
            items(
                items = visible.playlists,
                key = { "playlist_${it.id}" }
            ) { playlist ->
                var capturedBounds by remember { mutableStateOf<Rect?>(null) }
                SearchPlaylistCard(
                    playlist = playlist,
                    onCaptureBounds = { capturedBounds = it },
                    onClick = { onOpenPlaylist(playlist, capturedBounds) }
                )
            }
        }

        if (visible.artists.isNotEmpty()) {
            item(key = "artists_header") {
                SectionHeader(title = "Artists")
            }
            item(key = "artists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = visible.artists,
                        key = { "artist_${it.id}" }
                    ) { artist ->
                        SearchArtistChip(
                            artist = artist,
                            onClick = { onSearchArtist(artist) }
                        )
                    }
                }
            }
        }

        if (visible.localTracks.isNotEmpty()) {
            item(key = "local_header") {
                SectionHeader(
                    title = "Local Library",
                    subtitle = "${visible.localTracks.size} on device"
                )
            }
            items(
                items = visible.localTracks,
                key = { "local_${it.id}" }
            ) { track ->
                Box(modifier = Modifier.searchRevealOnAppear("local_${track.id}", revealSeen, cascade = true)) {
                    SearchTrackRow(
                        track = track,
                        isCurrent = track.id == uiState.currentTrackId,
                        isPlaying = uiState.isPlaying,
                        onClick = { onPlayTrack(track, visible.localTracks) },
                        onToggleLike = { onToggleLike(track) }
                    )
                }
            }
        }

        if (!uiState.isSearching && uiState.searchError == null && visible.isEmpty) {
            item(key = "empty_results") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found in ${uiState.activeFilter.label} for \"${uiState.query.trim()}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textTertiary
                    )
                }
            }
        }
    }
}
