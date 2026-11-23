package com.onda.music.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.homeRecede
import com.onda.music.core.model.Album
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Track

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onOpenSettings = onOpenSettings,
        onRefresh = { viewModel.refresh(initial = false) },
        onPlayHero = viewModel::playHero,
        onPlayTrack = viewModel::playTrack,
        onToggleLike = viewModel::toggleLike,
        onOpenAlbum = viewModel::openAlbum,
        onOpenMood = viewModel::openMood,
        onCloseDetail = viewModel::closeDetail,
        onPlayAllCollection = viewModel::playAllCollection,
        onPlayCollectionTrack = viewModel::playCollectionTrack,
        modifier = modifier
    )
}

@Composable
fun DiscoverRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    DiscoverScreen(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onRefresh = { viewModel.refresh(initial = false) },
        onPlayTrack = viewModel::playTrack,
        onToggleLike = viewModel::toggleLike,
        onOpenAlbum = viewModel::openAlbum,
        onOpenMood = viewModel::openMood,
        onCloseDetail = viewModel::closeDetail,
        onPlayAllCollection = viewModel::playAllCollection,
        onPlayCollectionTrack = viewModel::playCollectionTrack,
        modifier = modifier
    )
}

@Composable
private fun rememberOledDockBottomClearance(hasMiniPlayer: Boolean): Dp {
    val extraClearance = if (hasMiniPlayer) 32.dp else 96.dp
    return DockMetrics.rememberBottomPadding(
        hasMiniPlayer = hasMiniPlayer,
        extraClearance = extraClearance
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlayHero: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenMood: (MoodCategory) -> Unit,
    onCloseDetail: () -> Unit,
    onPlayAllCollection: () -> Unit,
    onPlayCollectionTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = resolveHomeStrings(uiState.languageCode)
    val reducedMotion = OndaTheme.reducedMotion
    val detailVisible = uiState.detailSheet != null
    val detailFraction by animateFloatAsState(
        targetValue = if (detailVisible) 1f else 0f,
        animationSpec = AppMotion.adaptiveSpring(reducedMotion = reducedMotion),
        label = "HomeDetailFraction"
    )
    val bottomClearance = rememberOledDockBottomClearance(hasMiniPlayer = uiState.hasMiniPlayer)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion),
            contentPadding = PaddingValues(top = 0.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val heroTrack = uiState.heroTrack
            if (heroTrack != null) {
                item(key = "hero_card") {
                    FullBleedHeroSection(
                        track = heroTrack,
                        subtitle = uiState.heroSubtitle,
                        strings = strings,
                        isPlaying = uiState.isPlaying,
                        isActiveTrack = uiState.activeTrackId == heroTrack.id,
                        onPlayClick = onPlayHero
                    )
                }
            } else {
                item(key = "top_bar_clearance") {
                    Spacer(
                        modifier = Modifier
                            .statusBarsPadding()
                            .height(60.dp)
                    )
                }
            }

            if (uiState.isOfflineFallback) {
                item(key = "offline_banner") {
                    OfflineFallbackBanner(
                        strings = strings,
                        onRetry = onRefresh
                    )
                }
            }

            if (uiState.isLoading && heroTrack == null) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            if (uiState.recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played_header") {
                    SectionHeader(title = strings.recentlyPlayed)
                }
                item(key = "recently_played_row") {
                    TrackHorizontalShelf(
                        tracks = uiState.recentlyPlayed,
                        strings = strings,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onPlayTrack = { track -> onPlayTrack(track, uiState.recentlyPlayed) }
                    )
                }
            }

            if (uiState.mostPlayed.isNotEmpty()) {
                item(key = "most_played_header") {
                    SectionHeader(title = strings.mostPlayed)
                }
                item(key = "most_played_row") {
                    TrackHorizontalShelf(
                        tracks = uiState.mostPlayed,
                        strings = strings,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onPlayTrack = { track -> onPlayTrack(track, uiState.mostPlayed) }
                    )
                }
            }

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "quick_picks_header") {
                    SectionHeader(title = strings.quickPicks)
                }
                items(
                    items = uiState.quickPicks.take(6),
                    key = { track -> "qp_${track.id}" }
                ) { track ->
                    QuickPickCompactRow(
                        track = track,
                        strings = strings,
                        isActive = track.id == uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        onPlay = { onPlayTrack(track, uiState.quickPicks) },
                        onToggleLike = { onToggleLike(track) }
                    )
                }
            }

            if (uiState.newReleases.isNotEmpty()) {
                item(key = "new_releases_header") {
                    SectionHeader(title = strings.newReleases)
                }
                item(key = "new_releases_row") {
                    AlbumHorizontalShelf(
                        albums = uiState.newReleases,
                        strings = strings,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }

            uiState.chartShelves.forEach { chartShelf ->
                if (chartShelf.tracks.isNotEmpty() || chartShelf.albums.isNotEmpty()) {
                    item(key = "chart_header_${chartShelf.id}") {
                        SectionHeader(
                            title = chartShelf.title.ifBlank { strings.charts },
                            subtitle = chartShelf.subtitle
                        )
                    }
                    if (chartShelf.tracks.isNotEmpty()) {
                        item(key = "chart_tracks_${chartShelf.id}") {
                            TrackHorizontalShelf(
                                tracks = chartShelf.tracks,
                                strings = strings,
                                activeTrackId = uiState.activeTrackId,
                                isPlaying = uiState.isPlaying,
                                detailFraction = detailFraction,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onPlayTrack = { track -> onPlayTrack(track, chartShelf.tracks) }
                            )
                        }
                    }
                    if (chartShelf.albums.isNotEmpty()) {
                        item(key = "chart_albums_${chartShelf.id}") {
                            AlbumHorizontalShelf(
                                albums = chartShelf.albums,
                                strings = strings,
                                detailFraction = detailFraction,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onOpenAlbum = onOpenAlbum
                            )
                        }
                    }
                }
            }

            uiState.shelves.forEach { shelf ->
                if (shelf.tracks.isNotEmpty() || shelf.albums.isNotEmpty()) {
                    item(key = "shelf_header_${shelf.id}") {
                        SectionHeader(
                            title = shelf.title,
                            subtitle = shelf.subtitle
                        )
                    }
                    if (shelf.tracks.isNotEmpty()) {
                        item(key = "shelf_tracks_${shelf.id}") {
                            TrackHorizontalShelf(
                                tracks = shelf.tracks,
                                strings = strings,
                                activeTrackId = uiState.activeTrackId,
                                isPlaying = uiState.isPlaying,
                                detailFraction = detailFraction,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onPlayTrack = { track -> onPlayTrack(track, shelf.tracks) }
                            )
                        }
                    }
                    if (shelf.albums.isNotEmpty()) {
                        item(key = "shelf_albums_${shelf.id}") {
                            AlbumHorizontalShelf(
                                albums = shelf.albums,
                                strings = strings,
                                detailFraction = detailFraction,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onOpenAlbum = onOpenAlbum
                            )
                        }
                    }
                }
            }

            if (uiState.moodCategories.isNotEmpty()) {
                item(key = "moods_header") {
                    SectionHeader(title = strings.moodsAndGenres)
                }
                item(key = "moods_row") {
                    LazyRow(
                        contentPadding = ShelfHorizontalPadding,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = uiState.moodCategories,
                            key = { mood -> mood.id }
                        ) { mood ->
                            MoodPillCard(
                                mood = mood,
                                onClick = { onOpenMood(mood) }
                            )
                        }
                    }
                }
            }

            if (uiState.localTracks.isNotEmpty() && (uiState.isOfflineFallback || uiState.shelves.isEmpty())) {
                item(key = "local_header") {
                    SectionHeader(title = strings.offlineLibrary)
                }
                item(key = "local_row") {
                    TrackHorizontalShelf(
                        tracks = uiState.localTracks,
                        strings = strings,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onPlayTrack = { track -> onPlayTrack(track, uiState.localTracks) }
                    )
                }
            }

            if (!uiState.isLoading && heroTrack == null && uiState.shelves.isEmpty() && uiState.localTracks.isEmpty()) {
                item(key = "empty_home") {
                    Text(
                        text = uiState.errorMessage ?: strings.emptyDetail,
                        fontSize = 14.sp,
                        color = OledMutedText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        MinimalFloatingTopBar(
            username = uiState.username,
            avatarPath = uiState.avatarPath,
            settingsDescription = strings.settingsDescription,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion)
        )

        AnimatedVisibility(
            visible = detailVisible,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            val detail = uiState.detailSheet ?: return@AnimatedVisibility
            AlbumPlaylistDetailSheet(
                detail = detail,
                strings = strings,
                palette = uiState.heroPalette,
                activeTrackId = uiState.activeTrackId,
                hasMiniPlayer = uiState.hasMiniPlayer,
                dynamicMeshEnabled = uiState.dynamicMeshEnabled,
                detailFraction = detailFraction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClose = onCloseDetail,
                onPlayAll = onPlayAllCollection,
                onPlayTrack = onPlayCollectionTrack,
                onToggleLike = onToggleLike
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DiscoverScreen(
    uiState: HomeUiState,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenMood: (MoodCategory) -> Unit,
    onCloseDetail: () -> Unit,
    onPlayAllCollection: () -> Unit,
    onPlayCollectionTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = resolveHomeStrings(uiState.languageCode)
    val discoverHeading = if (uiState.languageCode.startsWith("vi", ignoreCase = true)) {
        "Khám phá"
    } else {
        "Discover"
    }
    val reducedMotion = OndaTheme.reducedMotion
    val detailVisible = uiState.detailSheet != null
    val detailFraction by animateFloatAsState(
        targetValue = if (detailVisible) 1f else 0f,
        animationSpec = AppMotion.adaptiveSpring(reducedMotion = reducedMotion),
        label = "DiscoverDetailFraction"
    )
    val bottomClearance = rememberOledDockBottomClearance(hasMiniPlayer = uiState.hasMiniPlayer)
    val hasDiscoverContent = uiState.chartShelves.isNotEmpty() ||
        uiState.newReleases.isNotEmpty() ||
        uiState.moodCategories.isNotEmpty() ||
        uiState.quickPicks.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion),
            contentPadding = PaddingValues(top = 0.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "discover_top_clearance") {
                Spacer(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(58.dp)
                )
            }

            item(key = "discover_heading") {
                Text(
                    text = discoverHeading,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (uiState.isOfflineFallback) {
                item(key = "discover_offline_banner") {
                    OfflineFallbackBanner(
                        strings = strings,
                        onRetry = onRefresh
                    )
                }
            }

            if (uiState.isLoading && !hasDiscoverContent) {
                item(key = "discover_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            uiState.chartShelves.forEach { chartShelf ->
                if (chartShelf.tracks.isNotEmpty() || chartShelf.albums.isNotEmpty()) {
                    item(key = "discover_chart_header_${chartShelf.id}") {
                        SectionHeader(
                            title = chartShelf.title.ifBlank { strings.charts },
                            subtitle = chartShelf.subtitle
                        )
                    }
                    if (chartShelf.tracks.isNotEmpty()) {
                        item(key = "discover_chart_tracks_${chartShelf.id}") {
                            TrackHorizontalShelf(
                                tracks = chartShelf.tracks,
                                strings = strings,
                                activeTrackId = uiState.activeTrackId,
                                isPlaying = uiState.isPlaying,
                                detailFraction = detailFraction,
                                sharedTransitionScope = null,
                                animatedVisibilityScope = null,
                                onPlayTrack = { track -> onPlayTrack(track, chartShelf.tracks) }
                            )
                        }
                    }
                    if (chartShelf.albums.isNotEmpty()) {
                        item(key = "discover_chart_albums_${chartShelf.id}") {
                            AlbumHorizontalShelf(
                                albums = chartShelf.albums,
                                strings = strings,
                                detailFraction = detailFraction,
                                sharedTransitionScope = null,
                                animatedVisibilityScope = null,
                                onOpenAlbum = onOpenAlbum
                            )
                        }
                    }
                }
            }

            if (uiState.newReleases.isNotEmpty()) {
                item(key = "discover_new_releases_header") {
                    SectionHeader(title = strings.newReleases)
                }
                item(key = "discover_new_releases_row") {
                    AlbumHorizontalShelf(
                        albums = uiState.newReleases,
                        strings = strings,
                        detailFraction = detailFraction,
                        sharedTransitionScope = null,
                        animatedVisibilityScope = null,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }

            if (uiState.moodCategories.isNotEmpty()) {
                item(key = "discover_moods_header") {
                    SectionHeader(title = strings.moodsAndGenres)
                }
                item(key = "discover_moods_row") {
                    LazyRow(
                        contentPadding = ShelfHorizontalPadding,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = uiState.moodCategories,
                            key = { mood -> mood.id }
                        ) { mood ->
                            MoodPillCard(
                                mood = mood,
                                onClick = { onOpenMood(mood) }
                            )
                        }
                    }
                }
            }

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "discover_quick_picks_header") {
                    SectionHeader(title = strings.quickPicks)
                }
                items(
                    items = uiState.quickPicks.take(8),
                    key = { track -> "discover_qp_${track.id}" }
                ) { track ->
                    QuickPickCompactRow(
                        track = track,
                        strings = strings,
                        isActive = track.id == uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        onPlay = { onPlayTrack(track, uiState.quickPicks) },
                        onToggleLike = { onToggleLike(track) }
                    )
                }
            }

            if (!uiState.isLoading && !hasDiscoverContent) {
                item(key = "discover_empty") {
                    Text(
                        text = uiState.errorMessage ?: strings.emptyDetail,
                        fontSize = 14.sp,
                        color = OledMutedText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        MinimalFloatingTopBar(
            username = uiState.username,
            avatarPath = uiState.avatarPath,
            settingsDescription = strings.settingsDescription,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion)
        )

        AnimatedVisibility(
            visible = detailVisible,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            val detail = uiState.detailSheet ?: return@AnimatedVisibility
            AlbumPlaylistDetailSheet(
                detail = detail,
                strings = strings,
                palette = uiState.heroPalette,
                activeTrackId = uiState.activeTrackId,
                hasMiniPlayer = uiState.hasMiniPlayer,
                dynamicMeshEnabled = uiState.dynamicMeshEnabled,
                detailFraction = detailFraction,
                sharedTransitionScope = null,
                animatedVisibilityScope = null,
                onClose = onCloseDetail,
                onPlayAll = onPlayAllCollection,
                onPlayTrack = onPlayCollectionTrack,
                onToggleLike = onToggleLike
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TrackHorizontalShelf(
    tracks: List<Track>,
    strings: HomeStrings,
    activeTrackId: String?,
    isPlaying: Boolean,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onPlayTrack: (Track) -> Unit
) {
    LazyRow(
        contentPadding = ShelfHorizontalPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            items = tracks,
            key = { track -> track.id }
        ) { track ->
            TrackSquareCard(
                track = track,
                strings = strings,
                isActive = track.id == activeTrackId,
                isPlaying = isPlaying,
                detailFraction = detailFraction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = { onPlayTrack(track) }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumHorizontalShelf(
    albums: List<Album>,
    strings: HomeStrings,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpenAlbum: (Album) -> Unit
) {
    LazyRow(
        contentPadding = ShelfHorizontalPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            items = albums,
            key = { album -> album.id }
        ) { album ->
            AlbumSquareCard(
                album = album,
                strings = strings,
                detailFraction = detailFraction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = { onOpenAlbum(album) }
            )
        }
    }
}
