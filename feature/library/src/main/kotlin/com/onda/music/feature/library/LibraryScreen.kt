package com.onda.music.feature.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
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
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var showAddTracksSheetForPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val downloadsByTrackId = remember(uiState.downloads) {
        uiState.downloads.associate { it.item.trackId to it.item.status }
    }

    val allCandidateTracks = remember(
        uiState.librarySongs,
        uiState.likedSongs,
        uiState.localSongs,
        uiState.recentSongs
    ) {
        (uiState.librarySongs + uiState.likedSongs + uiState.localSongs + uiState.recentSongs)
            .distinctBy { it.id }
    }

    val coverPool = remember(allCandidateTracks) {
        allCandidateTracks.mapNotNull { it.artworkUrl }.distinct()
    }

    val playlistsListState = rememberLazyListState()
    val likedListState = rememberLazyListState()
    val localListState = rememberLazyListState()
    val downloadsListState = rememberLazyListState()
    val historyListState = rememberLazyListState()

    val activeListState = when (uiState.selectedTab) {
        LibraryTab.PLAYLISTS -> playlistsListState
        LibraryTab.LIKED -> likedListState
        LibraryTab.LOCAL -> localListState
        LibraryTab.DOWNLOADS -> downloadsListState
        LibraryTab.HISTORY -> historyListState
    }
    val collapseProgress by rememberLibraryCollapseProgress(activeListState)
    val revealSeen = rememberLibraryRevealSeenState()
    val expandedTopPadding = libraryExpandedTopPadding()
    val collapsedTopPadding = libraryCollapsedTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTokens.AmoledBlack)
    ) {
        val activePlaylist = uiState.selectedPlaylist
        if (uiState.selectedPlaylistId != null && activePlaylist != null) {
            PlaylistDetailView(
                playlist = activePlaylist,
                tracks = uiState.selectedPlaylistTracks,
                activeTrackId = uiState.activeTrackId,
                isPlaying = uiState.isPlaying,
                hasMiniPlayer = uiState.hasMiniPlayer,
                onBack = onClosePlaylist,
                onPlayAll = { shuffled -> onPlayAll(uiState.selectedPlaylistTracks, shuffled) },
                onPlayTrack = { track -> onPlayTrack(track, uiState.selectedPlaylistTracks) },
                onToggleLike = onToggleLike,
                onAddTracksClick = { showAddTracksSheetForPlaylist = activePlaylist },
                onRenameClick = { renameTarget = activePlaylist },
                onDeleteClick = { deleteTarget = activePlaylist },
                onRemoveTrack = { songId -> onRemoveTrackFromPlaylist(activePlaylist.id, songId) },
                onReorderTracks = { reordered -> onReorderPlaylistTracks(activePlaylist.id, reordered) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
        } else {
            val subtitle = "${uiState.playlists.size} playlists  |  ${uiState.likedSongs.size} liked  |  ${uiState.localSongs.size} local"
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.selectedTab) {
                    LibraryTab.PLAYLISTS -> PlaylistsTabContent(
                        listState = playlistsListState,
                        topPadding = expandedTopPadding,
                        collapsedTopPadding = collapsedTopPadding,
                        uiState = uiState,
                        coverPool = coverPool,
                        revealSeen = revealSeen,
                        onOpenHistory = { onSelectTab(LibraryTab.HISTORY) },
                        onOpenPlaylist = onOpenPlaylist,
                        onCreatePlaylist = { showCreateDialog = true },
                        onRenamePlaylist = { renameTarget = it },
                        onDeletePlaylist = { deleteTarget = it }
                    )
                    LibraryTab.LIKED -> TracksTabContent(
                        listState = likedListState,
                        topPadding = expandedTopPadding,
                        collapsedTopPadding = collapsedTopPadding,
                        tracks = uiState.likedSongs,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        downloadsByTrackId = downloadsByTrackId,
                        hasMiniPlayer = uiState.hasMiniPlayer,
                        revealSeen = revealSeen,
                        emptyTitle = "No liked songs yet",
                        emptySubtitle = "Tap the heart on any track to save it here.",
                        onPlayAll = { shuffled -> onPlayAll(uiState.likedSongs, shuffled) },
                        onPlayTrack = { track -> onPlayTrack(track, uiState.likedSongs) },
                        onToggleLike = onToggleLike,
                        onAddToPlaylist = { addToPlaylistTrack = it },
                        onToggleDownload = onToggleDownload
                    )
                    LibraryTab.LOCAL -> LocalTabContent(
                        listState = localListState,
                        topPadding = expandedTopPadding,
                        collapsedTopPadding = collapsedTopPadding,
                        uiState = uiState,
                        downloadsByTrackId = downloadsByTrackId,
                        revealSeen = revealSeen,
                        onRequestPermission = onRequestLocalPermission,
                        onScan = onScanLocalMedia,
                        onSelectFolder = onSelectLocalFolder,
                        onRelinkTrack = onRelinkTrack,
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
                        recentSongs = uiState.recentSongs,
                        totalPlayCount = uiState.totalPlayCount,
                        totalPlayDurationMs = uiState.totalPlayDurationMs,
                        downloadedCount = uiState.downloads.count { it.item.status == DownloadStatus.COMPLETED },
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        downloadsByTrackId = downloadsByTrackId,
                        hasMiniPlayer = uiState.hasMiniPlayer,
                        revealSeen = revealSeen,
                        onPlayAll = { shuffled -> onPlayAll(uiState.recentSongs, shuffled) },
                        onPlayTrack = { track -> onPlayTrack(track, uiState.recentSongs) },
                        onToggleLike = onToggleLike,
                        onAddToPlaylist = { addToPlaylistTrack = it },
                        onToggleDownload = onToggleDownload
                    )
                }

                LibraryGlassTopBar(
                    subtitle = subtitle,
                    collapseProgress = { collapseProgress },
                    selectedTab = uiState.selectedTab,
                    onSelectTab = onSelectTab,
                    onCreatePlaylist = { showCreateDialog = true },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.align(Alignment.TopCenter)
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
}

@Composable
private fun PlaylistsTabContent(
    listState: LazyListState,
    topPadding: Dp,
    collapsedTopPadding: Dp,
    uiState: LibraryUiState,
    coverPool: List<String?>,
    revealSeen: LibraryRevealSeenState,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "playlists_recap") {
                Box(modifier = Modifier.libraryRevealOnAppear("lib_recap", revealSeen)) {
                    LibraryRecapCard(
                        totalPlayCount = uiState.totalPlayCount,
                        totalPlayDurationMs = uiState.totalPlayDurationMs,
                        libraryCount = uiState.librarySongs.size,
                        onClick = onOpenHistory
                    )
                }
            }
            item(key = "playlists_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Collections (${uiState.playlists.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = OndaTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onCreatePlaylist,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "New Playlist", maxLines = 1)
                    }
                }
            }
            if (uiState.playlists.isEmpty()) {
                item(key = "playlists_empty") {
                    EmptyLibrarySection(
                        title = "No playlists yet",
                        subtitle = "Create a playlist and drag tracks into any order.",
                        actionLabel = "Create Playlist",
                        onAction = onCreatePlaylist
                    )
                }
            } else {
                itemsIndexed(uiState.playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    val playlistCovers = remember(coverPool, index) {
                        if (coverPool.isEmpty()) emptyList()
                        else listOf(coverPool[index % coverPool.size], coverPool[(index + 1) % coverPool.size])
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
            headerItems = 2,
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
            modifier = Modifier
                .fillMaxSize()
                .dockBottomPadding(hasMiniPlayer = hasMiniPlayer),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 12.dp),
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
    uiState: LibraryUiState,
    downloadsByTrackId: Map<String, DownloadStatus>,
    revealSeen: LibraryRevealSeenState,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onRelinkTrack: (Track) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit
) {
    val displayedLocalTracks = remember(uiState.localSongs, uiState.selectedLocalFolderPath) {
        val folderPath = uiState.selectedLocalFolderPath
        if (folderPath.isNullOrBlank()) {
            uiState.localSongs
        } else {
            uiState.localSongs.filter { track ->
                track.localUri?.contains(folderPath, ignoreCase = true) == true
            }.ifEmpty { uiState.localSongs }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "local_scanner_banner") {
                LocalScannerBanner(
                    hasPermission = uiState.hasLocalPermission,
                    isScanning = uiState.isScanningLocal,
                    lastScanMessage = uiState.lastScanMessage,
                    onRequestPermission = onRequestPermission,
                    onScan = onScan
                )
            }
            if (uiState.localFolders.isNotEmpty()) {
                item(key = "local_folders_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.localFolders, key = { it.path }) { folder ->
                            val selected = uiState.selectedLocalFolderPath == folder.path
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .libraryPressBounce(interactionSource, pressedScale = 0.95f)
                                    .smokedGlass(
                                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                                        elevation = if (selected) GlassElevation.CARD else GlassElevation.SUBTLE,
                                        accentTint = if (selected) OndaTheme.colors.accent else OndaTheme.colors.accentSecondary
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = LibraryHighlightIndication(cornerRadius = GlassTokens.RadiusPill),
                                        onClick = { onSelectFolder(folder.path) }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${folder.name} (${folder.trackCount})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) OndaTheme.colors.textPrimary else OndaTheme.colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.songsNeedingRelink.isNotEmpty()) {
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
                        title = "No local audio indexed",
                        subtitle = "Tap Scan above after granting audio storage permission."
                    )
                }
            } else {
                items(displayedLocalTracks, key = { it.id }) { track ->
                    val status = downloadsByTrackId[track.id]
                    Box(modifier = Modifier.libraryRevealOnAppear("local_${track.id}", revealSeen)) {
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
                }
            }
        }

        val localHeaderItems = 2 +
            (if (uiState.localFolders.isNotEmpty()) 1 else 0) +
            (if (uiState.songsNeedingRelink.isNotEmpty()) 1 else 0)

        DraggableScrollbar(
            scrollState = listState,
            headerItems = localHeaderItems,
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
    downloads: List<DownloadEntryUi>,
    hasMiniPlayer: Boolean,
    revealSeen: LibraryRevealSeenState,
    onPlayTrack: (Track) -> Unit,
    onRetry: (Track) -> Unit,
    onCancelOrDelete: (String, Boolean) -> Unit
) {
    val completedCount = remember(downloads) {
        downloads.count { it.item.status == DownloadStatus.COMPLETED }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .dockBottomPadding(hasMiniPlayer = hasMiniPlayer),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "downloads_header") {
                Text(
                    text = "$completedCount offline ready  |  ${downloads.size} total",
                    style = MaterialTheme.typography.labelMedium,
                    color = OndaTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (downloads.isEmpty()) {
                item(key = "downloads_empty") {
                    EmptyLibrarySection(
                        title = "No downloads yet",
                        subtitle = "Download tracks from Search, Home, or Liked songs for offline playback."
                    )
                }
            } else {
                items(downloads, key = { it.item.trackId }) { entry ->
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
    recentSongs: List<Track>,
    totalPlayCount: Int,
    totalPlayDurationMs: Long,
    downloadedCount: Int,
    activeTrackId: String?,
    isPlaying: Boolean,
    downloadsByTrackId: Map<String, DownloadStatus>,
    hasMiniPlayer: Boolean,
    revealSeen: LibraryRevealSeenState,
    onPlayAll: (Boolean) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .dockBottomPadding(hasMiniPlayer = hasMiniPlayer),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "history_summary") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .smokedGlass(
                            shape = RoundedCornerShape(GlassTokens.RadiusMd),
                            elevation = GlassElevation.CARD,
                            accentTint = OndaTheme.colors.accentSoft
                        )
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Plays: $totalPlayCount",
                            style = MaterialTheme.typography.titleSmall,
                            color = OndaTheme.colors.textPrimary
                        )
                        Text(
                            text = "Hours listened: ${formatHoursListened(totalPlayDurationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OndaTheme.colors.textSecondary
                        )
                    }
                    Text(
                        text = "$downloadedCount offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = OndaTheme.colors.accent
                    )
                }
            }
            item(key = "history_actions") {
                QuickPlayActionRow(
                    countLabel = "Recently played (${recentSongs.size})",
                    hasTracks = recentSongs.isNotEmpty(),
                    onPlayAll = { onPlayAll(false) },
                    onShuffleAll = { onPlayAll(true) }
                )
            }
            if (recentSongs.isEmpty()) {
                item(key = "history_empty") {
                    EmptyLibrarySection(
                        title = "No playback history yet",
                        subtitle = "Play any song and your recent listening recap will appear here."
                    )
                }
            } else {
                items(recentSongs, key = { it.id }) { track ->
                    val status = downloadsByTrackId[track.id]
                    Box(modifier = Modifier.libraryRevealOnAppear("hist_${track.id}", revealSeen)) {
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
            headerItems = 2,
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to playlists",
                    tint = OndaTheme.colors.textPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${localTracks.size} tracks  |  Swipe or drag handle",
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
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
                    tint = OndaTheme.colors.textSecondary
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
                modifier = Modifier
                    .fillMaxSize()
                    .dockBottomPadding(hasMiniPlayer = hasMiniPlayer),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                                        .heightIn(min = 68.dp)
                                        .libraryPressBounce(rowInteraction, pressedScale = 0.98f)
                                        .smokedGlass(
                                            shape = RoundedCornerShape(GlassTokens.RadiusMd),
                                            elevation = if (isDragging || isActive) GlassElevation.SHEET else GlassElevation.CARD,
                                            accentTint = if (isDragging || isActive) OndaTheme.colors.accent else OndaTheme.colors.accentSecondary
                                        )
                                        .clickable(
                                            interactionSource = rowInteraction,
                                            indication = LibraryHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
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
                                            color = if (isActive) OndaTheme.colors.accent else OndaTheme.colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist}  |  ${formatDurationMs(track.durationMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OndaTheme.colors.textSecondary,
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
                                            tint = OndaTheme.colors.textSecondary
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
                                            tint = if (isDragging) OndaTheme.colors.accent else OndaTheme.colors.textSecondary
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
