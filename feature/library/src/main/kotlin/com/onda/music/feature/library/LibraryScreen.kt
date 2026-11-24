package com.onda.music.feature.library

import android.content.Intent
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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.design.homeRecede
import com.onda.music.core.design.ondaBlurSource
import com.onda.music.core.design.rememberOndaBackdropState
import com.onda.music.core.database.ArtistPlayStatRow
import com.onda.music.core.database.TrackPlayStatRow
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import com.onda.music.core.sources.AudioTagMetadata
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val SwipeLikeColor = Color(0xFFFF375F)
private val SwipeDownloadColor = Color(0xFF0A84FF)
private val SwipeRemoveColor = Color(0xFFFF453A)

@Composable
fun LibraryRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingRelinkTrackId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshLocalPermission()
        if (granted) {
            viewModel.scanLocalMedia()
        }
    }

    val relinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val trackId = pendingRelinkTrackId
        pendingRelinkTrackId = null
        if (uri != null && trackId != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.relinkTrack(trackId, uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLocalPermission()
    }

    BackHandler(enabled = uiState.selectedPlaylistId != null) {
        viewModel.closePlaylist()
    }

    LibraryScreen(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onSelectTab = viewModel::selectTab,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onOpenPlaylist = viewModel::openPlaylist,
        onClosePlaylist = viewModel::closePlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onRenamePlaylist = viewModel::renamePlaylist,
        onDeletePlaylist = viewModel::deletePlaylist,
        onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
        onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
        onReorderPlaylistTracks = viewModel::reorderPlaylistTracks,
        onPlayTrack = viewModel::playTrack,
        onPlayAll = viewModel::playAll,
        onToggleLike = viewModel::toggleLike,
        onToggleDownload = viewModel::toggleDownload,
        onRetryDownload = viewModel::retryDownload,
        onCancelOrDeleteDownload = viewModel::cancelOrDeleteDownload,
        onRequestLocalPermission = {
            permissionLauncher.launch(viewModel.requiredLocalPermission())
        },
        onScanLocalMedia = viewModel::scanLocalMedia,
        onSelectLocalFolder = viewModel::selectLocalFolder,
        onRelinkTrack = { track ->
            pendingRelinkTrackId = track.id
            relinkLauncher.launch(arrayOf("audio/*"))
        },
        onSelectStatsRange = viewModel::selectStatsRange,
        onPlayTrackById = viewModel::playTrackById,
        onShareOledRecap = {
            scope.launch {
                OndaRecapCardRenderer.renderAndShare(
                    context = context,
                    rangeLabel = uiState.statsRange.label,
                    totalPlayCount = uiState.periodPlayCount,
                    totalDurationMs = uiState.periodPlayDurationMs,
                    topTracks = uiState.topTracks,
                    topArtists = uiState.topArtists
                )
            }
        },
        onLoadLocalTrackTags = viewModel::loadLocalTrackTags,
        onSaveLocalTrackTags = viewModel::saveLocalTrackTags,
        modifier = modifier
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onOpenSettings: () -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onCreatePlaylist: (String, String?) -> Unit,
    onRenamePlaylist: (String, String, String?) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (String, Track) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onReorderPlaylistTracks: (String, List<Track>) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onRetryDownload: (Track) -> Unit,
    onCancelOrDeleteDownload: (String, Boolean) -> Unit,
    onRequestLocalPermission: () -> Unit,
    onScanLocalMedia: () -> Unit,
    onSelectLocalFolder: (String?) -> Unit,
    onRelinkTrack: (Track) -> Unit,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = {},
    onSelectStatsRange: (StatsRange) -> Unit = {},
    onPlayTrackById: (String) -> Unit = {},
    onShareOledRecap: () -> Unit = {},
    onLoadLocalTrackTags: suspend (Track) -> AudioTagMetadata? = { null },
    onSaveLocalTrackTags: suspend (Track, AudioTagMetadata) -> Result<Unit> = { _, _ -> Result.success(Unit) }
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var editTagsTrack by remember { mutableStateOf<Track?>(null) }
    var showAddTracksSheetForPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val downloadsByTrackId = remember(uiState.downloads) {
        uiState.downloads.associate { it.item.trackId to it.item.status }
    }

    val allCandidateTracks = remember(
        uiState.librarySongs,
        uiState.likedSongs,
        uiState.localSongs,
        uiState.recentSongs,
        uiState.downloads
    ) {
        (uiState.librarySongs +
            uiState.likedSongs +
            uiState.localSongs +
            uiState.recentSongs +
            uiState.downloads.mapNotNull { it.track }).distinctBy { it.id }
    }

    val coverPool = remember(allCandidateTracks) {
        allCandidateTracks.mapNotNull { it.artworkUrl }.filter { it.isNotBlank() }.distinct()
    }

    val overviewListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()
    val likedListState = rememberLazyListState()
    val localListState = rememberLazyListState()
    val downloadsListState = rememberLazyListState()
    val historyListState = rememberLazyListState()

    val activeListState = when (uiState.selectedTab) {
        LibraryTab.OVERVIEW -> overviewListState
        LibraryTab.PLAYLISTS -> playlistsListState
        LibraryTab.LIKED -> likedListState
        LibraryTab.LOCAL -> localListState
        LibraryTab.DOWNLOADS -> downloadsListState
        LibraryTab.HISTORY -> historyListState
    }

    var lastQuery by remember { mutableStateOf(uiState.searchQuery) }
    LaunchedEffect(uiState.searchQuery) {
        if (lastQuery != uiState.searchQuery) {
            lastQuery = uiState.searchQuery
            if (activeListState.firstVisibleItemIndex > 0 || activeListState.firstVisibleItemScrollOffset > 0) {
                activeListState.scrollToItem(0)
            }
        }
    }

    val isCollapsed by rememberLibraryCollapsed(activeListState)
    val collapseProgress by rememberLibraryCollapseProgress(isCollapsed)
    val revealSeen = rememberLibraryRevealSeenState()
    val expandedTopPadding = libraryExpandedTopPadding()
    val collapsedTopPadding = libraryCollapsedTopPadding()
    val bottomDockClearance = DockMetrics.rememberBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer)
    val libraryBackdropState = rememberOndaBackdropState()
    val reducedMotion = OndaTheme.reducedMotion

    val activePlaylist = uiState.selectedPlaylist
    val playlistVisible = uiState.selectedPlaylistId != null && activePlaylist != null
    var lastPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var lastPlaylistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(activePlaylist, uiState.selectedPlaylistTracks) {
        if (activePlaylist != null) {
            lastPlaylist = activePlaylist
            lastPlaylistTracks = uiState.selectedPlaylistTracks
        }
    }
    val playlistOpenFractionState = animateFloatAsState(
        targetValue = if (playlistVisible) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.88f, stiffness = 420f),
        label = "LibraryPlaylistOpen"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .homeRecede(
                    fractionProvider = { playlistOpenFractionState.value },
                    reducedMotion = reducedMotion
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .ondaBlurSource(libraryBackdropState)
            ) {
                    AnimatedContent(
                        targetState = uiState.selectedTab,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                            } else {
                                val forward = targetState.ordinal >= initialState.ordinal
                                val enterOffset: (Int) -> Int = { fullWidth ->
                                    if (forward) (fullWidth * 0.12f).toInt() else -(fullWidth * 0.12f).toInt()
                                }
                                val exitOffset: (Int) -> Int = { fullWidth ->
                                    if (forward) -(fullWidth * 0.08f).toInt() else (fullWidth * 0.08f).toInt()
                                }
                                (slideInHorizontally(
                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
                                    initialOffsetX = enterOffset
                                ) + fadeIn(tween(200))) togetherWith (slideOutHorizontally(
                                    animationSpec = tween(180),
                                    targetOffsetX = exitOffset
                                ) + fadeOut(tween(150)))
                            }
                        },
                        label = "libraryTabContent"
                    ) { tab ->
                        when (tab) {
                            LibraryTab.OVERVIEW -> OverviewTabContent(
                                listState = overviewListState,
                                topPadding = expandedTopPadding,
                                collapsedTopPadding = collapsedTopPadding,
                                bottomDockClearance = bottomDockClearance,
                                uiState = uiState,
                                allCandidateTracks = allCandidateTracks,
                                coverPool = coverPool,
                                downloadsByTrackId = downloadsByTrackId,
                                revealSeen = revealSeen,
                                onSelectTab = onSelectTab,
                                onOpenPlaylist = onOpenPlaylist,
                                onCreatePlaylist = { showCreateDialog = true },
                                onRenamePlaylist = { renameTarget = it },
                                onDeletePlaylist = { deleteTarget = it },
                                onPlayAll = onPlayAll,
                                onPlayTrack = onPlayTrack,
                                onToggleLike = onToggleLike,
                                onAddToPlaylist = { addToPlaylistTrack = it },
                                onToggleDownload = onToggleDownload
                            )
                            LibraryTab.PLAYLISTS -> PlaylistsTabContent(
                                listState = playlistsListState,
                                topPadding = expandedTopPadding,
                                collapsedTopPadding = collapsedTopPadding,
                                bottomDockClearance = bottomDockClearance,
                                uiState = uiState,
                                coverPool = coverPool,
                                revealSeen = revealSeen,
                                onOpenPlaylist = onOpenPlaylist,
                                onCreatePlaylist = { showCreateDialog = true },
                                onRenamePlaylist = { renameTarget = it },
                                onDeletePlaylist = { deleteTarget = it }
                            )
                            LibraryTab.LIKED -> {
                                val query = uiState.searchQuery.trim()
                                val filteredLiked = remember(uiState.likedSongs, query) {
                                    filterTracksByQuery(uiState.likedSongs, query)
                                }
                                TracksTabContent(
                                    listState = likedListState,
                                    topPadding = expandedTopPadding,
                                    collapsedTopPadding = collapsedTopPadding,
                                    bottomDockClearance = bottomDockClearance,
                                    tracks = filteredLiked,
                                    activeTrackId = uiState.activeTrackId,
                                    isPlaying = uiState.isPlaying,
                                    downloadsByTrackId = downloadsByTrackId,
                                    hasMiniPlayer = uiState.hasMiniPlayer,
                                    revealSeen = revealSeen,
                                    emptyTitle = if (query.isNotEmpty()) "No matching liked songs" else "No liked songs yet",
                                    emptySubtitle = if (query.isNotEmpty()) {
                                        "No liked tracks match \"$query\"."
                                    } else {
                                        "Tap the heart on any track to save it here."
                                    },
                                    onPlayAll = { shuffled -> onPlayAll(filteredLiked, shuffled) },
                                    onPlayTrack = { track -> onPlayTrack(track, filteredLiked) },
                                    onToggleLike = onToggleLike,
                                    onAddToPlaylist = { addToPlaylistTrack = it },
                                    onToggleDownload = onToggleDownload
                                )
                            }
                            LibraryTab.LOCAL -> LocalTabContent(
                                listState = localListState,
                                topPadding = expandedTopPadding,
                                collapsedTopPadding = collapsedTopPadding,
                                bottomDockClearance = bottomDockClearance,
                                uiState = uiState,
                                downloadsByTrackId = downloadsByTrackId,
                                revealSeen = revealSeen,
                                onRequestPermission = onRequestLocalPermission,
                                onScan = onScanLocalMedia,
                                onSelectFolder = onSelectLocalFolder,
                                onRelinkTrack = onRelinkTrack,
                                onEditTrackTags = { editTagsTrack = it },
                                onPlayAll = { list, shuffled -> onPlayAll(list, shuffled) },
                                onPlayTrack = onPlayTrack,
                                onToggleLike = onToggleLike,
                                onAddToPlaylist = { addToPlaylistTrack = it },
                                onToggleDownload = onToggleDownload
                            )
                            LibraryTab.DOWNLOADS -> DownloadsTabContent(
                                listState = downloadsListState,
                                topPadding = expandedTopPadding,
                                collapsedTopPadding = collapsedTopPadding,
                                bottomDockClearance = bottomDockClearance,
                                searchQuery = uiState.searchQuery,
                                downloads = uiState.downloads,
                                hasMiniPlayer = uiState.hasMiniPlayer,
                                revealSeen = revealSeen,
                                onPlayTrack = { track ->
                                    val downloadedQueue = uiState.downloads
                                        .filter { it.item.status == DownloadStatus.COMPLETED }
                                        .mapNotNull { it.track }
                                    onPlayTrack(track, downloadedQueue)
                                },
                                onRetry = onRetryDownload,
                                onCancelOrDelete = onCancelOrDeleteDownload
                            )
                            LibraryTab.HISTORY -> HistoryTabContent(
                                listState = historyListState,
                                topPadding = expandedTopPadding,
                                collapsedTopPadding = collapsedTopPadding,
                                bottomDockClearance = bottomDockClearance,
                                searchQuery = uiState.searchQuery,
                                recentSongs = uiState.recentSongs,
                                statsRange = uiState.statsRange,
                                periodPlayCount = uiState.periodPlayCount,
                                periodPlayDurationMs = uiState.periodPlayDurationMs,
                                topTracks = uiState.topTracks,
                                topArtists = uiState.topArtists,
                                totalPlayCount = uiState.totalPlayCount,
                                totalPlayDurationMs = uiState.totalPlayDurationMs,
                                downloadedCount = uiState.downloads.count { it.item.status == DownloadStatus.COMPLETED },
                                activeTrackId = uiState.activeTrackId,
                                isPlaying = uiState.isPlaying,
                                downloadsByTrackId = downloadsByTrackId,
                                hasMiniPlayer = uiState.hasMiniPlayer,
                                revealSeen = revealSeen,
                                onSelectStatsRange = onSelectStatsRange,
                                onPlayTrackById = onPlayTrackById,
                                onShareOledRecap = onShareOledRecap,
                                onPlayAll = { list, shuffled -> onPlayAll(list, shuffled) },
                                onPlayTrack = onPlayTrack,
                                onToggleLike = onToggleLike,
                                onAddToPlaylist = { addToPlaylistTrack = it },
                                onToggleDownload = onToggleDownload
                            )
                        }
                    }
                }

                LibraryGlassTopBar(
                    backdropState = libraryBackdropState,
                    collapseProgress = { collapseProgress },
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    selectedTab = uiState.selectedTab,
                    onSelectTab = onSelectTab,
                    onCreatePlaylist = { showCreateDialog = true },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
        }

        AnimatedVisibility(
            visible = playlistVisible,
            enter = if (reducedMotion) {
                fadeIn(tween(100))
            } else {
                slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f)
                ) { it / 5 } + fadeIn(tween(190))
            },
            exit = if (reducedMotion) {
                fadeOut(tween(90))
            } else {
                slideOutHorizontally(
                    animationSpec = tween(200)
                ) { it / 5 } + fadeOut(tween(150))
            }
        ) {
            val shownPlaylist = activePlaylist ?: lastPlaylist
            if (shownPlaylist != null) {
                val shownTracks = if (activePlaylist != null) uiState.selectedPlaylistTracks else lastPlaylistTracks
                PlaylistDetailView(
                    playlist = shownPlaylist,
                    tracks = shownTracks,
                    activeTrackId = uiState.activeTrackId,
                    isPlaying = uiState.isPlaying,
                    hasMiniPlayer = uiState.hasMiniPlayer,
                    bottomDockClearance = bottomDockClearance,
                    onBack = onClosePlaylist,
                    onPlayAll = { shuffled -> onPlayAll(shownTracks, shuffled) },
                    onPlayTrack = { track -> onPlayTrack(track, shownTracks) },
                    onToggleLike = onToggleLike,
                    onAddTracksClick = { showAddTracksSheetForPlaylist = shownPlaylist },
                    onRenameClick = { renameTarget = shownPlaylist },
                    onDeleteClick = { deleteTarget = shownPlaylist },
                    onRemoveTrack = { songId -> onRemoveTrackFromPlaylist(shownPlaylist.id, songId) },
                    onReorderTracks = { reordered -> onReorderPlaylistTracks(shownPlaylist.id, reordered) },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .statusBarsPadding()
                )
            }
        }
    }

    if (showCreateDialog) {
        PlaylistEditorDialog(
            title = "Create Playlist",
            confirmLabel = "Create",
            initialName = "",
            initialDescription = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreateDialog = false
            }
        )
    }

    renameTarget?.let { target ->
        PlaylistEditorDialog(
            title = "Rename Playlist",
            confirmLabel = "Save",
            initialName = target.name,
            initialDescription = target.description.orEmpty(),
            onDismiss = { renameTarget = null },
            onConfirm = { name, desc ->
                onRenamePlaylist(target.id, name, desc)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        DeletePlaylistDialog(
            playlistName = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeletePlaylist(target.id)
                deleteTarget = null
            }
        )
    }

    addToPlaylistTrack?.let { track ->
        PickPlaylistSheet(
            track = track,
            playlists = uiState.playlists,
            onSelectPlaylist = { playlist ->
                onAddTrackToPlaylist(playlist.id, track)
                addToPlaylistTrack = null
            },
            onCreatePlaylist = {
                addToPlaylistTrack = null
                showCreateDialog = true
            },
            onDismiss = { addToPlaylistTrack = null }
        )
    }

    showAddTracksSheetForPlaylist?.let { playlist ->
        AddTracksToPlaylistSheet(
            playlistName = playlist.name,
            candidateTracks = allCandidateTracks,
            existingTrackIds = uiState.selectedPlaylistTracks.mapTo(HashSet()) { it.id },
            onAddTrack = { track -> onAddTrackToPlaylist(playlist.id, track) },
            onDismiss = { showAddTracksSheetForPlaylist = null }
        )
    }

    editTagsTrack?.let { target ->
        LocalTagEditorSheet(
            track = target,
            onLoadMetadata = onLoadLocalTrackTags,
            onSaveMetadata = onSaveLocalTrackTags,
            onDismiss = { editTagsTrack = null }
        )
    }
}

@Composable
private fun OverviewTabContent(
    listState: LazyListState,
    topPadding: Dp,
    collapsedTopPadding: Dp,
    bottomDockClearance: Dp,
    uiState: LibraryUiState,
    allCandidateTracks: List<Track>,
    coverPool: List<String>,
    downloadsByTrackId: Map<String, DownloadStatus>,
    revealSeen: LibraryRevealSeenState,
    onSelectTab: (LibraryTab) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit
) {
    val query = uiState.searchQuery.trim()
    val matchingPlaylists = remember(uiState.playlists, query) {
        if (query.isEmpty()) emptyList() else filterPlaylistsByQuery(uiState.playlists, query)
    }
    val matchingTracks = remember(allCandidateTracks, query) {
        if (query.isEmpty()) emptyList() else filterTracksByQuery(allCandidateTracks, query)
    }
    val playlistRows = remember(uiState.playlists) {
        uiState.playlists.chunked(2)
    }

    val playlistsCovers = remember(uiState.playlists, uiState.playlistCoversById, coverPool) {
        if (uiState.playlists.isEmpty()) {
            emptyList()
        } else {
            val own = uiState.playlists.flatMap { playlist ->
                listOfNotNull(playlist.artworkUrl) + uiState.playlistCoversById[playlist.id].orEmpty()
            }.filter { it.isNotBlank() }.distinct()
            (own + coverPool).distinct().take(3)
        }
    }
    val localCovers = remember(uiState.localSongs) {
        uiState.localSongs.mapNotNull { it.artworkUrl }.filter { it.isNotBlank() }.distinct().take(3)
    }
    val downloadsCovers = remember(uiState.downloads) {
        uiState.downloads.mapNotNull { it.track?.artworkUrl }.filter { it.isNotBlank() }.distinct().take(3)
    }
    val recentCovers = remember(uiState.recentSongs) {
        uiState.recentSongs.mapNotNull { it.artworkUrl }.filter { it.isNotBlank() }.distinct().take(3)
    }
    val completedDownloads = remember(uiState.downloads) {
        uiState.downloads.count { it.item.status == DownloadStatus.COMPLETED }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = bottomDockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (query.isNotEmpty()) {
                if (matchingPlaylists.isEmpty() && matchingTracks.isEmpty()) {
                    item(key = "overview_search_empty") {
                        EmptyLibrarySection(
                            title = "No matches found",
                            subtitle = "No playlists or tracks match \"$query\"."
                        )
                    }
                } else {
                    if (matchingPlaylists.isNotEmpty()) {
                        item(key = "overview_match_playlists_hdr") {
                            LibrarySectionHeader(
                                title = "Matching Playlists",
                                trailingCount = "${matchingPlaylists.size}"
                            )
                        }
                        items(matchingPlaylists, key = { "ov_pl_${it.id}" }) { playlist ->
                            val plCovers = uiState.playlistCoversById[playlist.id].orEmpty()
                            PlaylistRowCard(
                                playlist = playlist,
                                coverPool = plCovers,
                                onOpen = { onOpenPlaylist(playlist.id) },
                                onRename = { onRenamePlaylist(playlist) },
                                onDelete = { onDeletePlaylist(playlist) }
                            )
                        }
                    }
                    if (matchingTracks.isNotEmpty()) {
                        item(key = "overview_match_tracks_hdr") {
                            LibrarySectionHeader(
                                title = "Matching Tracks",
                                trailingCount = "${matchingTracks.size}"
                            )
                        }
                        items(matchingTracks, key = { "ov_tr_${it.id}" }) { track ->
                            SwipeableLibraryTrackRow(
                                track = track,
                                isActive = track.id == uiState.activeTrackId,
                                isPlaying = uiState.isPlaying,
                                downloadStatus = downloadsByTrackId[track.id],
                                onPlay = { onPlayTrack(track, matchingTracks) },
                                onToggleLike = { onToggleLike(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onToggleDownload = { onToggleDownload(track) }
                            )
                        }
                    }
                }
            } else {
                item(key = "overview_hub_grid") {
                    Box(modifier = Modifier.libraryRevealOnAppear("ov_hub", revealSeen)) {
                        LibraryOverviewHubGrid(
                            playlistsCount = uiState.playlists.size,
                            playlistsCovers = playlistsCovers,
                            localCount = uiState.localSongs.size,
                            localCovers = localCovers,
                            downloadsCount = completedDownloads,
                            downloadsCovers = downloadsCovers,
                            recentCount = uiState.recentSongs.size,
                            recentCovers = recentCovers,
                            onOpenPlaylists = { onSelectTab(LibraryTab.PLAYLISTS) },
                            onOpenLocal = { onSelectTab(LibraryTab.LOCAL) },
                            onOpenDownloads = { onSelectTab(LibraryTab.DOWNLOADS) },
                            onOpenHistory = { onSelectTab(LibraryTab.HISTORY) }
                        )
                    }
                }

                item(key = "overview_liked_card") {
                    Box(modifier = Modifier.libraryRevealOnAppear("ov_liked", revealSeen)) {
                        LikedSongsSignatureCard(
                            count = uiState.likedSongs.size,
                            onOpen = { onSelectTab(LibraryTab.LIKED) },
                            onQuickShuffle = { onPlayAll(uiState.likedSongs, true) }
                        )
                    }
                }

                item(key = "overview_recent_card") {
                    Box(modifier = Modifier.libraryRevealOnAppear("ov_recent", revealSeen)) {
                        RecentlyPlayedTelemetryCard(
                            recentCovers = recentCovers,
                            totalPlayCount = uiState.totalPlayCount,
                            totalPlayDurationMs = uiState.totalPlayDurationMs,
                            onOpen = { onSelectTab(LibraryTab.HISTORY) }
                        )
                    }
                }

                item(key = "overview_playlists_hdr") {
                    LibrarySectionHeader(
                        title = "Your Playlists",
                        actionLabel = "New",
                        onAction = onCreatePlaylist
                    )
                }

                if (uiState.playlists.isEmpty()) {
                    item(key = "overview_playlists_empty") {
                        EmptyLibrarySection(
                            title = "No playlists yet",
                            subtitle = "Create a playlist to organize your favorite tracks.",
                            actionLabel = "Create Playlist",
                            onAction = onCreatePlaylist
                        )
                    }
                } else {
                    items(
                        items = playlistRows,
                        key = { row -> "ov_row_${row.joinToString("_") { it.id }}" }
                    ) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { playlist ->
                                val covers = uiState.playlistCoversById[playlist.id].orEmpty()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .libraryRevealOnAppear("ov_grid_${playlist.id}", revealSeen)
                                ) {
                                    PlaylistGridCard(
                                        playlist = playlist,
                                        covers = covers,
                                        onOpen = { onOpenPlaylist(playlist.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        DraggableScrollbar(
            scrollState = listState,
            headerItems = if (query.isNotEmpty()) 1 else 4,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = collapsedTopPadding, end = 2.dp)
                .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer)
        )
    }
}

@Composable
private fun PlaylistsTabContent(
    listState: LazyListState,
    topPadding: Dp,
    collapsedTopPadding: Dp,
    bottomDockClearance: Dp,
    uiState: LibraryUiState,
    coverPool: List<String>,
    revealSeen: LibraryRevealSeenState,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    val query = uiState.searchQuery.trim()
    val displayedPlaylists = remember(uiState.playlists, query) {
        filterPlaylistsByQuery(uiState.playlists, query)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = bottomDockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "playlists_header") {
                LibrarySectionHeader(
                    title = if (query.isNotEmpty()) {
                        "Playlists (${displayedPlaylists.size})"
                    } else {
                        "Collections (${displayedPlaylists.size})"
                    },
                    actionLabel = "New",
                    onAction = onCreatePlaylist
                )
            }
            if (displayedPlaylists.isEmpty()) {
                item(key = "playlists_empty") {
                    EmptyLibrarySection(
                        title = if (query.isNotEmpty()) "No matching playlists" else "No playlists yet",
                        subtitle = if (query.isNotEmpty()) {
                            "No playlists match \"$query\"."
                        } else {
                            "Create a playlist and drag tracks into any order."
                        },
                        actionLabel = if (query.isEmpty()) "Create Playlist" else null,
                        onAction = if (query.isEmpty()) onCreatePlaylist else null
                    )
                }
            } else {
                itemsIndexed(displayedPlaylists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    val realCovers = uiState.playlistCoversById[playlist.id].orEmpty()
                    val playlistCovers = remember(realCovers, coverPool, index) {
                        if (realCovers.isNotEmpty()) {
                            realCovers
                        } else if (coverPool.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(coverPool[index % coverPool.size])
                        }
                    }
                    Box(modifier = Modifier.libraryRevealOnAppear("pl_${playlist.id}", revealSeen)) {
                        PlaylistRowCard(
                            playlist = playlist,
                            coverPool = playlistCovers,
                            onOpen = { onOpenPlaylist(playlist.id) },
                            onRename = { onRenamePlaylist(playlist) },
                            onDelete = { onDeletePlaylist(playlist) }
                        )
                    }
                }
            }
        }

        DraggableScrollbar(
            scrollState = listState,
            headerItems = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = collapsedTopPadding, end = 2.dp)
                .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer)
        )
    }
}

@Composable
private fun TracksTabContent(
    listState: LazyListState,
    topPadding: Dp,
    collapsedTopPadding: Dp,
    bottomDockClearance: Dp,
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    downloadsByTrackId: Map<String, DownloadStatus>,
    hasMiniPlayer: Boolean,
    revealSeen: LibraryRevealSeenState,
    emptyTitle: String,
    emptySubtitle: String,
    onPlayAll: (Boolean) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = bottomDockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "tracks_actions") {
                QuickPlayActionRow(
                    countLabel = "${tracks.size} tracks",
                    hasTracks = tracks.isNotEmpty(),
                    onPlayAll = { onPlayAll(false) },
                    onShuffleAll = { onPlayAll(true) }
                )
            }
            if (tracks.isEmpty()) {
                item(key = "tracks_empty") {
                    EmptyLibrarySection(
                        title = emptyTitle,
                        subtitle = emptySubtitle
                    )
                }
            } else {
                items(tracks, key = { it.id }) { track ->
                    val status = downloadsByTrackId[track.id]
                    Box(modifier = Modifier.libraryRevealOnAppear("liked_${track.id}", revealSeen)) {
                        SwipeableLibraryTrackRow(
                            track = track,
                            isActive = track.id == activeTrackId,
                            isPlaying = isPlaying,
                            downloadStatus = status,
                            onPlay = { onPlayTrack(track) },
                            onToggleLike = { onToggleLike(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onToggleDownload = { onToggleDownload(track) }
                        )
                    }
                }
            }
        }

        DraggableScrollbar(
            scrollState = listState,
            headerItems = 1,
            indexLabelForIndex = { idx -> tracks.getOrNull(idx)?.let(::trackIndexLabel) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = collapsedTopPadding, end = 2.dp)
                .dockBottomPadding(hasMiniPlayer = hasMiniPlayer)
        )
    }
}

@Composable
private fun LocalTabContent(
    listState: LazyListState,
    topPadding: Dp,
    collapsedTopPadding: Dp,
    bottomDockClearance: Dp,
    uiState: LibraryUiState,
    downloadsByTrackId: Map<String, DownloadStatus>,
    revealSeen: LibraryRevealSeenState,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onRelinkTrack: (Track) -> Unit,
    onEditTrackTags: (Track) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit
) {
    val query = uiState.searchQuery.trim()
    val folderFiltered = remember(uiState.localSongs, uiState.selectedLocalFolderPath) {
        val folderPath = uiState.selectedLocalFolderPath
        if (folderPath.isNullOrBlank()) {
            uiState.localSongs
        } else {
            uiState.localSongs.filter { track ->
                track.localUri?.contains(folderPath, ignoreCase = true) == true
            }.ifEmpty { uiState.localSongs }
        }
    }
    val displayedLocalTracks = remember(folderFiltered, query) {
        filterTracksByQuery(folderFiltered, query)
    }

    val showFolders = uiState.localFolders.isNotEmpty()
    val showRelink = query.isEmpty() && uiState.songsNeedingRelink.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = bottomDockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (query.isEmpty()) {
                item(key = "local_scanner_banner") {
                    LocalScannerBanner(
                        hasPermission = uiState.hasLocalPermission,
                        isScanning = uiState.isScanningLocal,
                        lastScanMessage = uiState.lastScanMessage,
                        onRequestPermission = onRequestPermission,
                        onScan = onScan
                    )
                }
            }
            if (showFolders) {
                item(key = "local_folders_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.localFolders, key = { it.path }) { folder ->
                            val selected = uiState.selectedLocalFolderPath == folder.path
                            val interactionSource = remember { MutableInteractionSource() }
                            val pillShape = RoundedCornerShape(50)
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .libraryPressBounce(interactionSource, pressedScale = 0.95f)
                                    .clip(pillShape)
                                    .background(if (selected) Color.White else Color(0xFF141416))
                                    .then(
                                        if (selected) {
                                            Modifier
                                        } else {
                                            Modifier.border(0.5.dp, Color(0x16FFFFFF), pillShape)
                                        }
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = LibraryHighlightIndication(cornerRadius = 19.dp),
                                        onClick = { onSelectFolder(folder.path) }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${folder.name} (${folder.trackCount})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = if (selected) Color.Black else Color(0xFF9A9AA0),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            if (showRelink) {
                item(key = "relink_card") {
                    RelinkTracksCard(
                        tracks = uiState.songsNeedingRelink,
                        onRelinkTrack = onRelinkTrack
                    )
                }
            }
            item(key = "local_actions") {
                QuickPlayActionRow(
                    countLabel = "${displayedLocalTracks.size} local tracks",
                    hasTracks = displayedLocalTracks.isNotEmpty(),
                    onPlayAll = { onPlayAll(displayedLocalTracks, false) },
                    onShuffleAll = { onPlayAll(displayedLocalTracks, true) }
                )
            }
            if (displayedLocalTracks.isEmpty()) {
                item(key = "local_empty") {
                    EmptyLibrarySection(
                        title = if (query.isNotEmpty()) "No matching local tracks" else "No local audio indexed",
                        subtitle = if (query.isNotEmpty()) {
                            "No local tracks match \"$query\"."
                        } else {
                            "Tap Scan above after granting audio storage permission."
                        }
                    )
                }
            } else {
                items(displayedLocalTracks, key = { it.id }) { track ->
                    val status = downloadsByTrackId[track.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .libraryRevealOnAppear("local_${track.id}", revealSeen),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SwipeableLibraryTrackRow(
                                track = track,
                                isActive = track.id == uiState.activeTrackId,
                                isPlaying = uiState.isPlaying,
                                downloadStatus = status,
                                onPlay = { onPlayTrack(track, displayedLocalTracks) },
                                onToggleLike = { onToggleLike(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onToggleDownload = { onToggleDownload(track) }
                            )
                        }
                        if (!track.localUri.isNullOrBlank()) {
                            IconButton(
                                onClick = { onEditTrackTags(track) },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = DockMetrics.MinTapTarget,
                                    minHeight = DockMetrics.MinTapTarget
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tag,
                                    contentDescription = "Edit local tags",
                                    tint = Color(0xFF9A9AA0),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        val localHeaderItems = 1 +
            (if (query.isEmpty()) 1 else 0) +
            (if (showFolders) 1 else 0) +
            (if (showRelink) 1 else 0)

        DraggableScrollbar(
            scrollState = listState,
            headerItems = localHeaderItems,
            indexLabelForIndex = { idx -> displayedLocalTracks.getOrNull(idx)?.let(::trackIndexLabel) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = collapsedTopPadding, end = 2.dp)
                .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer)
        )
    }
}

@Composable
private fun DownloadsTabContent(
    listState: LazyListState,
    topPadding: Dp,
    collapsedTopPadding: Dp,
    bottomDockClearance: Dp,
    searchQuery: String,
    downloads: List<DownloadEntryUi>,
    hasMiniPlayer: Boolean,
    revealSeen: LibraryRevealSeenState,
    onPlayTrack: (Track) -> Unit,
    onRetry: (Track) -> Unit,
    onCancelOrDelete: (String, Boolean) -> Unit
) {
    val query = searchQuery.trim()
    val displayedDownloads = remember(downloads, query) {
        if (query.isEmpty()) {
            downloads
        } else {
            downloads.filter { entry ->
                val title = entry.track?.title ?: entry.item.trackId
                val artist = entry.track?.artist.orEmpty()
                val album = entry.track?.album.orEmpty()
                title.contains(query, ignoreCase = true) ||
                    artist.contains(query, ignoreCase = true) ||
                    album.contains(query, ignoreCase = true)
            }
        }
    }
    val completedCount = remember(displayedDownloads) {
        displayedDownloads.count { it.item.status == DownloadStatus.COMPLETED }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = bottomDockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "downloads_header") {
                Text(
                    text = "$completedCount offline ready  |  ${displayedDownloads.size} total",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = Color(0xFF9A9AA0),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (displayedDownloads.isEmpty()) {
                item(key = "downloads_empty") {
                    EmptyLibrarySection(
                        title = if (query.isNotEmpty()) "No matching downloads" else "No downloads yet",
                        subtitle = if (query.isNotEmpty()) {
                            "No downloads match \"$query\"."
                        } else {
                            "Download tracks from Search, Home, or Liked songs for offline playback."
                        }
                    )
                }
            } else {
                items(displayedDownloads, key = { it.item.trackId }) { entry ->
                    val isActive = entry.item.status == DownloadStatus.QUEUED ||
                        entry.item.status == DownloadStatus.DOWNLOADING
                    Box(modifier = Modifier.libraryRevealOnAppear("dl_${entry.item.trackId}", revealSeen)) {
                        DownloadRowCard(
                            entry = entry,
                            onPlay = { entry.track?.let(onPlayTrack) },
                            onRetry = { entry.track?.let(onRetry) },
                            onDeleteOrCancel = { onCancelOrDelete(entry.item.trackId, isActive) }
                        )
                    }
                }
            }
        }

        DraggableScrollbar(
            scrollState = listState,
            headerItems = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = collapsedTopPadding, end = 2.dp)
                .dockBottomPadding(hasMiniPlayer = hasMiniPlayer)
        )
    }
}

@Composable
private fun HistoryTabContent(
    listState: LazyListState,
    topPadding: Dp,
    collapsedTopPadding: Dp,
    bottomDockClearance: Dp,
    searchQuery: String,
    recentSongs: List<Track>,
    statsRange: StatsRange,
    periodPlayCount: Int,
    periodPlayDurationMs: Long,
    topTracks: List<TrackPlayStatRow>,
    topArtists: List<ArtistPlayStatRow>,
    totalPlayCount: Int,
    totalPlayDurationMs: Long,
    downloadedCount: Int,
    activeTrackId: String?,
    isPlaying: Boolean,
    downloadsByTrackId: Map<String, DownloadStatus>,
    hasMiniPlayer: Boolean,
    revealSeen: LibraryRevealSeenState,
    onSelectStatsRange: (StatsRange) -> Unit,
    onPlayTrackById: (String) -> Unit,
    onShareOledRecap: () -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit
) {
    val query = searchQuery.trim()
    val displayedRecent = remember(recentSongs, query) {
        filterTracksByQuery(recentSongs, query)
    }
    val showSummary = query.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = bottomDockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showSummary) {
                item(key = "stats_range_selector") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatsRange.entries.forEach { range ->
                            val selected = range == statsRange
                            val pillShape = RoundedCornerShape(999.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                    .clip(pillShape)
                                    .background(if (selected) Color.White else Color(0xFF141414))
                                    .border(0.75.dp, Color(0x22FFFFFF), pillShape)
                                    .clickable { onSelectStatsRange(range) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = range.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.Black else Color(0xFF9A9AA0),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                item(key = "history_summary") {
                    val summaryShape = remember { SmoothCornerShape(20.dp) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(summaryShape)
                            .background(Color(0xFF000000))
                            .border(0.75.dp, Color(0x26FFFFFF), summaryShape)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "OLED RECAP · ${statsRange.label.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8E8E93)
                                )
                                Text(
                                    text = "${formatHoursListened(periodPlayDurationMs)} listened",
                                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                    color = Color.White
                                )
                                Text(
                                    text = "$periodPlayCount plays in period · $totalPlayCount lifetime ($downloadedCount offline)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = Color(0xFF9A9AA0)
                                )
                            }
                        }
                        Button(
                            onClick = onShareOledRecap,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Share Pure-Black OLED Recap", maxLines = 1)
                        }
                    }
                }
                if (topTracks.isNotEmpty()) {
                    item(key = "stats_top_tracks") {
                        val cardShape = remember { SmoothCornerShape(20.dp) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(Color(0xFF0A0A0A))
                                .border(0.75.dp, Color(0x1EFFFFFF), cardShape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TOP TRACKS (${statsRange.label})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8E8E93)
                            )
                            topTracks.take(5).forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onPlayTrackById(row.trackId) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                        color = Color(0xFF8E8E93)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = row.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${row.artist} · ${row.playCount} plays",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                            color = Color(0xFF9A9AA0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = formatHoursListened(row.totalDurationMs),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                if (topArtists.isNotEmpty()) {
                    item(key = "stats_top_artists") {
                        val cardShape = remember { SmoothCornerShape(20.dp) }
                        val maxMs = topArtists.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(Color(0xFF0A0A0A))
                                .border(0.75.dp, Color(0x1EFFFFFF), cardShape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TOP ARTISTS (${statsRange.label})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8E8E93)
                            )
                            topArtists.take(5).forEach { artistRow ->
                                val frac = (artistRow.totalDurationMs.toFloat() / maxMs.toFloat()).coerceIn(0.08f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = artistRow.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${artistRow.playCount} · ${formatHoursListened(artistRow.totalDurationMs)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            color = Color(0xFF9A9AA0)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color(0xFF1F1F1F))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(frac)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(OndaTheme.colors.accent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "history_actions") {
                QuickPlayActionRow(
                    countLabel = "Recently played (${displayedRecent.size})",
                    hasTracks = displayedRecent.isNotEmpty(),
                    onPlayAll = { onPlayAll(displayedRecent, false) },
                    onShuffleAll = { onPlayAll(displayedRecent, true) }
                )
            }
            if (displayedRecent.isEmpty()) {
                item(key = "history_empty") {
                    EmptyLibrarySection(
                        title = if (query.isNotEmpty()) "No matching history" else "No playback history yet",
                        subtitle = if (query.isNotEmpty()) {
                            "No recent tracks match \"$query\"."
                        } else {
                            "Play any song and your recent listening recap will appear here."
                        }
                    )
                }
            } else {
                items(displayedRecent, key = { it.id }) { track ->
                    val status = downloadsByTrackId[track.id]
                    Box(modifier = Modifier.libraryRevealOnAppear("hist_${track.id}", revealSeen)) {
                        SwipeableLibraryTrackRow(
                            track = track,
                            isActive = track.id == activeTrackId,
                            isPlaying = isPlaying,
                            downloadStatus = status,
                            onPlay = { onPlayTrack(track, displayedRecent) },
                            onToggleLike = { onToggleLike(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onToggleDownload = { onToggleDownload(track) }
                        )
                    }
                }
            }
        }

        DraggableScrollbar(
            scrollState = listState,
            headerItems = if (showSummary) 2 else 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = collapsedTopPadding, end = 2.dp)
                .dockBottomPadding(hasMiniPlayer = hasMiniPlayer)
        )
    }
}

@Composable
private fun SwipeableLibraryTrackRow(
    track: Track,
    isActive: Boolean,
    isPlaying: Boolean,
    downloadStatus: DownloadStatus?,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleDownload: () -> Unit
) {
    LibrarySwipeActionsBox(
        start = {
            LibrarySwipeAction(
                icon = if (track.liked) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite,
                color = SwipeLikeColor,
                label = if (track.liked) "Unlike" else "Like",
                onTrigger = onToggleLike
            )
        },
        end = {
            val isDownloaded = downloadStatus == DownloadStatus.COMPLETED ||
                downloadStatus == DownloadStatus.DOWNLOADING ||
                downloadStatus == DownloadStatus.QUEUED
            LibrarySwipeAction(
                icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download,
                color = if (isDownloaded) SwipeRemoveColor else SwipeDownloadColor,
                label = if (isDownloaded) "Remove" else "Download",
                onTrigger = onToggleDownload
            )
        }
    ) {
        LibraryTrackRow(
            track = track,
            isActive = isActive,
            isPlaying = isPlaying,
            downloadStatus = downloadStatus,
            onPlay = onPlay,
            onToggleLike = onToggleLike,
            onAddToPlaylist = onAddToPlaylist,
            onDownloadClick = onToggleDownload
        )
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    hasMiniPlayer: Boolean,
    bottomDockClearance: Dp,
    onBack: () -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddTracksClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRemoveTrack: (String) -> Unit,
    onReorderTracks: (List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDraggingTracks by remember { mutableStateOf(false) }
    var localTracks by remember(playlist.id) { mutableStateOf(tracks) }

    LaunchedEffect(tracks, isDraggingTracks) {
        if (!isDraggingTracks) {
            localTracks = tracks
        }
    }

    val lazyListState = rememberLazyListState()
    val headerCount = 1
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromTrackIdx = from.index - headerCount
        val toTrackIdx = to.index - headerCount
        if (fromTrackIdx in localTracks.indices && toTrackIdx in localTracks.indices) {
            localTracks = reorderTrackList(localTracks, fromTrackIdx, toTrackIdx)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to playlists",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${localTracks.size} tracks  |  Swipe or drag handle",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Color(0xFF9A9AA0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRenameClick,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Rename playlist",
                    tint = Color(0xFF9A9AA0)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete playlist",
                    tint = OndaTheme.colors.error
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 8.dp,
                    bottom = bottomDockClearance
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "playlist_detail_controls") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onPlayAll(false) },
                            enabled = localTracks.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OndaTheme.colors.accent,
                                contentColor = GlassTokens.AmoledBlack
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Play", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onPlayAll(true) },
                            enabled = localTracks.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Shuffle", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onAddTracksClick,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Add", maxLines = 1)
                        }
                    }
                }

                if (localTracks.isEmpty()) {
                    item(key = "playlist_detail_empty") {
                        EmptyLibrarySection(
                            title = "Playlist is empty",
                            subtitle = "Add tracks from your saved, liked, or local songs.",
                            actionLabel = "Add Tracks",
                            onAction = onAddTracksClick
                        )
                    }
                } else {
                    items(localTracks, key = { it.id }) { track ->
                        ReorderableItem(reorderableState, key = track.id) { isDragging ->
                            val isActive = track.id == activeTrackId
                            val rowInteraction = remember { MutableInteractionSource() }
                            val rowShape = remember { SmoothCornerShape(16.dp) }
                            val bgColor = if (isDragging || isActive) Color(0xFF1C1C22) else Color(0xFF141416)
                            val borderColor = if (isDragging || isActive) Color(0x30FFFFFF) else Color(0x16FFFFFF)
                            LibrarySwipeActionsBox(
                                enabled = !isDraggingTracks && !isDragging,
                                start = {
                                    LibrarySwipeAction(
                                        icon = if (track.liked) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite,
                                        color = SwipeLikeColor,
                                        label = if (track.liked) "Unlike" else "Like",
                                        onTrigger = { onToggleLike(track) }
                                    )
                                },
                                end = {
                                    LibrarySwipeAction(
                                        icon = Icons.Rounded.Close,
                                        color = SwipeRemoveColor,
                                        label = "Remove",
                                        onTrigger = { onRemoveTrack(track.id) }
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 66.dp)
                                        .libraryPressBounce(rowInteraction, pressedScale = 0.98f)
                                        .clip(rowShape)
                                        .background(bgColor)
                                        .border(0.5.dp, borderColor, rowShape)
                                        .clickable(
                                            interactionSource = rowInteraction,
                                            indication = LibraryHighlightIndication(cornerRadius = 16.dp),
                                            onClick = { onPlayTrack(track) }
                                        )
                                        .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackArtworkBox(
                                        artworkUrl = track.artworkUrl,
                                        title = track.title,
                                        isActive = isActive,
                                        isPlaying = isPlaying
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isActive) OndaTheme.colors.accent else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist}  |  ${formatDurationMs(track.durationMs)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                            color = Color(0xFF9A9AA0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveTrack(track.id) },
                                        modifier = Modifier.defaultMinSize(
                                            minWidth = DockMetrics.MinTapTarget,
                                            minHeight = DockMetrics.MinTapTarget
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove from playlist",
                                            tint = Color(0xFF9A9AA0)
                                        )
                                    }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    isDraggingTracks = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    isDraggingTracks = false
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onReorderTracks(localTracks)
                                                }
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = "Reorder track",
                                            tint = if (isDragging) OndaTheme.colors.accent else Color(0xFF9A9AA0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DraggableScrollbar(
                scrollState = lazyListState,
                headerItems = 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .dockBottomPadding(hasMiniPlayer = hasMiniPlayer)
            )
        }
    }
}

private fun filterPlaylistsByQuery(playlists: List<Playlist>, query: String): List<Playlist> {
    if (query.isEmpty()) return playlists
    return playlists.filter { playlist ->
        playlist.name.contains(query, ignoreCase = true) ||
            playlist.description.orEmpty().contains(query, ignoreCase = true)
    }
}

private fun filterTracksByQuery(tracks: List<Track>, query: String): List<Track> {
    if (query.isEmpty()) return tracks
    return tracks.filter { track ->
        track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.orEmpty().contains(query, ignoreCase = true)
    }
}
