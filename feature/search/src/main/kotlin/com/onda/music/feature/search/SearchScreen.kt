package com.onda.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.Album
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
    onSearchArtist: (com.onda.music.core.model.Artist) -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTokens.AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SearchTopHeader(onOpenSettings = onOpenSettings)

            SmokedSearchBar(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                onSubmit = {
                    focusManager.clearFocus()
                    onSubmitSearch()
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.query.isNotBlank()) {
                SourceFilterRow(
                    activeFilter = uiState.activeFilter,
                    onSelectFilter = onSelectFilter
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.query.isBlank()) {
                SearchBrowseBody(
                    uiState = uiState,
                    onOpenLinkSheet = onOpenLinkSheet,
                    onSelectHistoryItem = {
                        focusManager.clearFocus()
                        onSelectHistoryItem(it)
                    },
                    onDeleteHistoryItem = onDeleteHistoryItem,
                    onClearAllHistory = onClearAllHistory,
                    onSelectMoodCategory = {
                        focusManager.clearFocus()
                        onSelectMoodCategory(it)
                    },
                    onRetryMoods = onRetryMoods
                )
            } else {
                SearchResultsBody(
                    uiState = uiState,
                    onSelectSuggestion = {
                        focusManager.clearFocus()
                        onSelectSuggestion(it)
                    },
                    onPlayTrack = onPlayTrack,
                    onToggleLike = onToggleLike,
                    onOpenAlbum = onOpenAlbum,
                    onOpenPlaylist = onOpenPlaylist,
                    onSearchArtist = {
                        focusManager.clearFocus()
                        onSearchArtist(it)
                    },
                    onRetrySearch = onSubmitSearch
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

        if (uiState.drillDownState.isVisible) {
            CollectionDrillDownSheet(
                state = uiState.drillDownState,
                currentTrackId = uiState.currentTrackId,
                isPlaying = uiState.isPlaying,
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
    onOpenLinkSheet: () -> Unit,
    onSelectHistoryItem: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onSelectMoodCategory: (MoodCategory) -> Unit,
    onRetryMoods: () -> Unit
) {
    val moodRows = uiState.moodCategories.chunked(2)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "link_import_card") {
            LinkImportPromoCard(
                onClick = onOpenLinkSheet,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (uiState.history.isNotEmpty()) {
            item(key = "history_section") {
                SearchHistorySection(
                    history = uiState.history,
                    onSelect = onSelectHistoryItem,
                    onDelete = onDeleteHistoryItem,
                    onClearAll = onClearAllHistory
                )
            }
        }

        item(key = "moods_header") {
            SectionHeader(
                title = "Moods & Genres",
                subtitle = "Explore curated YouTube Music stations"
            )
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
                MoodCategoryRow(
                    pair = pair,
                    onSelect = onSelectMoodCategory
                )
            }
        }
    }
}

@Composable
private fun SearchResultsBody(
    uiState: SearchUiState,
    onSelectSuggestion: (String) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onSearchArtist: (com.onda.music.core.model.Artist) -> Unit,
    onRetrySearch: () -> Unit
) {
    val visible = uiState.visibleResults

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OndaTheme.colors.accent
                    )
                    Spacer(modifier = Modifier.width(10.dp))
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
                SearchTrackRow(
                    track = track,
                    isCurrent = track.id == uiState.currentTrackId,
                    isPlaying = uiState.isPlaying,
                    onClick = { onPlayTrack(track, visible.monochromeTracks) },
                    onToggleLike = { onToggleLike(track) }
                )
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
                SearchTrackRow(
                    track = track,
                    isCurrent = track.id == uiState.currentTrackId,
                    isPlaying = uiState.isPlaying,
                    onClick = { onPlayTrack(track, visible.ytTracks) },
                    onToggleLike = { onToggleLike(track) }
                )
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
                        SearchAlbumCard(
                            album = album,
                            onClick = { onOpenAlbum(album) }
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
                SearchPlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) }
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
                SearchTrackRow(
                    track = track,
                    isCurrent = track.id == uiState.currentTrackId,
                    isPlaying = uiState.isPlaying,
                    onClick = { onPlayTrack(track, visible.localTracks) },
                    onToggleLike = { onToggleLike(track) }
                )
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
