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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.dockBottomPadding
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
        onOpenSearch = onOpenSearch,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTokens.AmoledBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion)
                .dockBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer),
            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "home_top_bar") {
                HomeTopBar(
                    username = uiState.username,
                    avatarPath = uiState.avatarPath,
                    strings = strings,
                    isRefreshing = uiState.isRefreshing,
                    accentColor = uiState.heroPalette.primary,
                    onRefresh = onRefresh,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = onOpenSettings
                )
            }

            if (uiState.isOfflineFallback) {
                item(key = "offline_banner") {
                    OfflineFallbackBanner(
                        strings = strings,
                        accentColor = uiState.heroPalette.primary,
                        onRetry = onRefresh
                    )
                }
            }

            val heroTrack = uiState.heroTrack
            if (heroTrack != null) {
                item(key = "hero_card") {
                    HeroTrackCard(
                        track = heroTrack,
                        subtitle = uiState.heroSubtitle,
                        palette = uiState.heroPalette,
                        strings = strings,
                        isPlaying = uiState.isPlaying,
                        isActiveTrack = uiState.activeTrackId == heroTrack.id,
                        dynamicMeshEnabled = uiState.dynamicMeshEnabled,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onPlayClick = onPlayHero,
                        onToggleLike = { onToggleLike(heroTrack) }
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
                        CircularProgressIndicator(color = uiState.heroPalette.primary)
                    }
                }
            }

            if (uiState.recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played_header") {
                    SectionHeader(
                        title = strings.recentlyPlayed,
                        count = uiState.recentlyPlayed.size
                    )
                }
                item(key = "recently_played_row") {
                    TrackHorizontalShelf(
                        tracks = uiState.recentlyPlayed,
                        strings = strings,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        accentColor = uiState.heroPalette.primary,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onPlayTrack = { track -> onPlayTrack(track, uiState.recentlyPlayed) }
                    )
                }
            }

            if (uiState.mostPlayed.isNotEmpty()) {
                item(key = "most_played_header") {
                    SectionHeader(
                        title = strings.mostPlayed,
                        count = uiState.mostPlayed.size
                    )
                }
                item(key = "most_played_row") {
                    TrackHorizontalShelf(
                        tracks = uiState.mostPlayed,
                        strings = strings,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        accentColor = uiState.heroPalette.primary,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onPlayTrack = { track -> onPlayTrack(track, uiState.mostPlayed) }
                    )
                }
            }

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "quick_picks_header") {
                    SectionHeader(
                        title = strings.quickPicks,
                        count = uiState.quickPicks.size
                    )
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
                        accentColor = uiState.heroPalette.primary,
                        onPlay = { onPlayTrack(track, uiState.quickPicks) },
                        onToggleLike = { onToggleLike(track) }
                    )
                }
            }

            if (uiState.newReleases.isNotEmpty()) {
                item(key = "new_releases_header") {
                    SectionHeader(
                        title = strings.newReleases,
                        count = uiState.newReleases.size
                    )
                }
                item(key = "new_releases_row") {
                    AlbumHorizontalShelf(
                        albums = uiState.newReleases,
                        strings = strings,
                        accentColor = uiState.heroPalette.primary,
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
                                accentColor = uiState.heroPalette.primary,
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
                                accentColor = uiState.heroPalette.primary,
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
                                accentColor = uiState.heroPalette.primary,
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
                                accentColor = uiState.heroPalette.primary,
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
                    SectionHeader(
                        title = strings.moodsAndGenres,
                        count = uiState.moodCategories.size
                    )
                }
                item(key = "moods_row") {
                    LazyRow(
                        contentPadding = ShelfHorizontalPadding,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    SectionHeader(
                        title = strings.offlineLibrary,
                        count = uiState.localTracks.size
                    )
                }
                item(key = "local_row") {
                    TrackHorizontalShelf(
                        tracks = uiState.localTracks,
                        strings = strings,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        accentColor = uiState.heroPalette.primary,
                        detailFraction = detailFraction,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onPlayTrack = { track -> onPlayTrack(track, uiState.localTracks) }
                    )
                }
            }

            if (!uiState.isLoading && heroTrack == null && uiState.shelves.isEmpty() && uiState.localTracks.isEmpty()) {
                item(key = "empty_home") {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        elevation = GlassElevation.CARD,
                        accentTint = uiState.heroPalette.primary
                    ) {
                        Text(
                            text = uiState.errorMessage ?: strings.emptyDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OndaTheme.colors.textSecondary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
        }

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
private fun TrackHorizontalShelf(
    tracks: List<Track>,
    strings: HomeStrings,
    activeTrackId: String?,
    isPlaying: Boolean,
    accentColor: Color,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onPlayTrack: (Track) -> Unit
) {
    LazyRow(
        contentPadding = ShelfHorizontalPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                accentColor = accentColor,
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
    accentColor: Color,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpenAlbum: (Album) -> Unit
) {
    LazyRow(
        contentPadding = ShelfHorizontalPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = albums,
            key = { album -> album.id }
        ) { album ->
            AlbumSquareCard(
                album = album,
                strings = strings,
                accentColor = accentColor,
                detailFraction = detailFraction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = { onOpenAlbum(album) }
            )
        }
    }
}
