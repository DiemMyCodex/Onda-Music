package com.onda.music.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val revealSeen = rememberHomeRevealSeenState()
    val detailVisible = uiState.detailSheet != null
    var lastDetail by remember { mutableStateOf<CollectionDetailState?>(null) }
    var launchOrigin by remember { mutableStateOf(CardLaunchOrigin()) }
    LaunchedEffect(uiState.detailSheet) {
        if (uiState.detailSheet != null) {
            lastDetail = uiState.detailSheet
        }
    }

    BackHandler(enabled = detailVisible) {
        onCloseDetail()
    }

    val detailFraction by animateFloatAsState(
        targetValue = if (detailVisible) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else if (detailVisible) {
            spring(dampingRatio = 0.88f, stiffness = 260f)
        } else {
            spring(dampingRatio = 0.92f, stiffness = 340f)
        },
        label = "HomeDetailFraction"
    )
    val bottomClearance = rememberOledDockBottomClearance(hasMiniPlayer = uiState.hasMiniPlayer)
    val heroHeightDp = (LocalConfiguration.current.screenHeightDp * 0.46f).dp.coerceIn(380.dp, 420.dp)
    val heroHeightPx = with(density) { heroHeightDp.toPx() }.coerceAtLeast(1f)
    val heroScrollProgress by remember(listState, heroHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / heroHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    val heroCards = remember(
        uiState.heroTrack,
        uiState.activeTrackId,
        uiState.isPlaying,
        uiState.quickPicks,
        uiState.recentlyPlayed,
        uiState.mostPlayed,
        uiState.newReleases,
        uiState.shelves,
        uiState.localTracks,
        uiState.languageCode
    ) {
        buildHeroCarouselCards(uiState, strings)
    }

    val topGlowCovers = remember(heroCards, uiState.recentlyPlayed, uiState.quickPicks) {
        (heroCards.mapNotNull { it.artworkUrl } +
            uiState.recentlyPlayed.mapNotNull { it.artworkUrl } +
            uiState.quickPicks.mapNotNull { it.artworkUrl })
            .distinct()
            .take(2)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        if (topGlowCovers.isNotEmpty()) {
            HomeTopGlow(
                coverUrls = topGlowCovers,
                depth = 260.dp,
                visibility = { (1f - heroScrollProgress).coerceIn(0f, 1f) * (1f - detailFraction) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion),
            contentPadding = PaddingValues(top = 0.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (heroCards.isNotEmpty()) {
                item(key = "hero_card") {
                    HomeHeroCarousel(
                        cards = heroCards,
                        strings = strings,
                        activeTrackId = uiState.activeTrackId,
                        isPlaying = uiState.isPlaying,
                        heroScrollProgress = { heroScrollProgress },
                        blurEnabled = uiState.glassBlurEnabled,
                        detailVisible = detailVisible,
                        onPlayTrack = onPlayTrack,
                        onPlayHero = onPlayHero,
                        onOpenAlbum = { album, bounds ->
                            launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                            onOpenAlbum(album)
                        }
                    )
                }
            } else if (uiState.isLoading) {
                item(key = "hero_skeleton") {
                    HomeHeroSkeleton()
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
                    Box(modifier = Modifier.homeRevealOnAppear("home_offline", revealSeen, cascade = true)) {
                        OfflineFallbackBanner(
                            strings = strings,
                            onRetry = onRefresh
                        )
                    }
                }
            }

            if (uiState.isLoading && heroCards.isEmpty()) {
                items(2, key = { "home_shelf_skeleton_$it" }) {
                    HomeShelfSkeleton()
                }
            }

            if (uiState.recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("recently_played_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.recentlyPlayed)
                    }
                }
                item(key = "recently_played_row") {
                    Box(modifier = Modifier.homeRevealOnAppear("recently_played_row", revealSeen, cascade = true)) {
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
            }

            if (uiState.mostPlayed.isNotEmpty()) {
                item(key = "most_played_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("most_played_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.mostPlayed)
                    }
                }
                item(key = "most_played_row") {
                    Box(modifier = Modifier.homeRevealOnAppear("most_played_row", revealSeen, cascade = true)) {
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
            }

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "quick_picks_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("quick_picks_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.quickPicks)
                    }
                }
                items(
                    items = uiState.quickPicks.take(6),
                    key = { track -> "qp_${track.id}" }
                ) { track ->
                    Box(modifier = Modifier.homeRevealOnAppear("qp_${track.id}", revealSeen, cascade = true)) {
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
            }

            if (uiState.newReleases.isNotEmpty()) {
                item(key = "new_releases_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("new_releases_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.newReleases)
                    }
                }
                item(key = "new_releases_row") {
                    Box(modifier = Modifier.homeRevealOnAppear("new_releases_row", revealSeen, cascade = true)) {
                        AlbumHorizontalShelf(
                            albums = uiState.newReleases,
                            strings = strings,
                            detailFraction = detailFraction,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onOpenAlbum = { album, bounds ->
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                onOpenAlbum(album)
                            }
                        )
                    }
                }
            }

            uiState.chartShelves.forEach { chartShelf ->
                if (chartShelf.tracks.isNotEmpty() || chartShelf.albums.isNotEmpty()) {
                    item(key = "chart_header_${chartShelf.id}") {
                        Box(modifier = Modifier.homeRevealOnAppear("chart_header_${chartShelf.id}", revealSeen, cascade = true)) {
                            SectionHeader(
                                title = chartShelf.title.ifBlank { strings.charts },
                                subtitle = chartShelf.subtitle
                            )
                        }
                    }
                    if (chartShelf.tracks.isNotEmpty()) {
                        item(key = "chart_tracks_${chartShelf.id}") {
                            Box(modifier = Modifier.homeRevealOnAppear("chart_tracks_${chartShelf.id}", revealSeen, cascade = true)) {
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
                    }
                    if (chartShelf.albums.isNotEmpty()) {
                        item(key = "chart_albums_${chartShelf.id}") {
                            Box(modifier = Modifier.homeRevealOnAppear("chart_albums_${chartShelf.id}", revealSeen, cascade = true)) {
                                AlbumHorizontalShelf(
                                    albums = chartShelf.albums,
                                    strings = strings,
                                    detailFraction = detailFraction,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onOpenAlbum = { album, bounds ->
                                        launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                        onOpenAlbum(album)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            uiState.shelves.forEach { shelf ->
                if (shelf.tracks.isNotEmpty() || shelf.albums.isNotEmpty()) {
                    item(key = "shelf_header_${shelf.id}") {
                        Box(modifier = Modifier.homeRevealOnAppear("shelf_header_${shelf.id}", revealSeen, cascade = true)) {
                            SectionHeader(
                                title = shelf.title,
                                subtitle = shelf.subtitle
                            )
                        }
                    }
                    if (shelf.tracks.isNotEmpty()) {
                        item(key = "shelf_tracks_${shelf.id}") {
                            Box(modifier = Modifier.homeRevealOnAppear("shelf_tracks_${shelf.id}", revealSeen, cascade = true)) {
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
                    }
                    if (shelf.albums.isNotEmpty()) {
                        item(key = "shelf_albums_${shelf.id}") {
                            Box(modifier = Modifier.homeRevealOnAppear("shelf_albums_${shelf.id}", revealSeen, cascade = true)) {
                                AlbumHorizontalShelf(
                                    albums = shelf.albums,
                                    strings = strings,
                                    detailFraction = detailFraction,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onOpenAlbum = { album, bounds ->
                                        launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                        onOpenAlbum(album)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.moodCategories.isNotEmpty()) {
                item(key = "moods_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("moods_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.moodsAndGenres)
                    }
                }
                item(key = "moods_row") {
                    Box(modifier = Modifier.homeRevealOnAppear("moods_row", revealSeen, cascade = true)) {
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
                                    onClick = { bounds ->
                                        launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                        onOpenMood(mood)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.localTracks.isNotEmpty() && (uiState.isOfflineFallback || uiState.shelves.isEmpty())) {
                item(key = "local_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("local_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.offlineLibrary)
                    }
                }
                item(key = "local_row") {
                    Box(modifier = Modifier.homeRevealOnAppear("local_row", revealSeen, cascade = true)) {
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
            }

            if (!uiState.isLoading && heroCards.isEmpty() && uiState.shelves.isEmpty() && uiState.localTracks.isEmpty()) {
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
            scrollProgress = { if (heroCards.isNotEmpty()) heroScrollProgress else 0f },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion)
        )

        val shownDetail = uiState.detailSheet ?: lastDetail
        if (shownDetail != null && (detailVisible || detailFraction > 0.005f)) {
            AlbumPlaylistDetailSheet(
                detail = shownDetail,
                strings = strings,
                palette = uiState.heroPalette,
                activeTrackId = uiState.activeTrackId,
                isPlaying = uiState.isPlaying,
                hasMiniPlayer = uiState.hasMiniPlayer,
                dynamicMeshEnabled = uiState.dynamicMeshEnabled,
                glassBlurEnabled = uiState.glassBlurEnabled,
                detailFraction = detailFraction,
                launchOrigin = launchOrigin,
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
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val revealSeen = rememberHomeRevealSeenState()
    val detailVisible = uiState.detailSheet != null
    var lastDetail by remember { mutableStateOf<CollectionDetailState?>(null) }
    var launchOrigin by remember { mutableStateOf(CardLaunchOrigin()) }
    LaunchedEffect(uiState.detailSheet) {
        if (uiState.detailSheet != null) {
            lastDetail = uiState.detailSheet
        }
    }

    BackHandler(enabled = detailVisible) {
        onCloseDetail()
    }

    val detailFraction by animateFloatAsState(
        targetValue = if (detailVisible) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else if (detailVisible) {
            spring(dampingRatio = 0.88f, stiffness = 260f)
        } else {
            spring(dampingRatio = 0.92f, stiffness = 340f)
        },
        label = "DiscoverDetailFraction"
    )
    val bottomClearance = rememberOledDockBottomClearance(hasMiniPlayer = uiState.hasMiniPlayer)
    val topBarTriggerPx = with(density) { 48.dp.toPx() }.coerceAtLeast(1f)
    val scrollProgress by remember(listState, topBarTriggerPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / topBarTriggerPx).coerceIn(0f, 1f)
            }
        }
    }
    val hasDiscoverContent = uiState.chartShelves.isNotEmpty() ||
        uiState.newReleases.isNotEmpty() ||
        uiState.moodCategories.isNotEmpty() ||
        uiState.quickPicks.isNotEmpty()
    val discoverGlowCovers = remember(uiState.newReleases, uiState.chartShelves) {
        (uiState.newReleases.mapNotNull { it.artworkUrl } +
            uiState.chartShelves.flatMap { it.albums.mapNotNull { album -> album.artworkUrl } })
            .distinct()
            .take(2)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        if (discoverGlowCovers.isNotEmpty()) {
            HomeTopGlow(
                coverUrls = discoverGlowCovers,
                depth = 220.dp,
                visibility = { (1f - scrollProgress).coerceIn(0f, 1f) * (1f - detailFraction) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }

        LazyColumn(
            state = listState,
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
                    DiscoverSkeleton()
                }
            }

            uiState.chartShelves.forEach { chartShelf ->
                if (chartShelf.tracks.isNotEmpty() || chartShelf.albums.isNotEmpty()) {
                    item(key = "discover_chart_header_${chartShelf.id}") {
                        Box(modifier = Modifier.homeRevealOnAppear("discover_chart_header_${chartShelf.id}", revealSeen, cascade = true)) {
                            SectionHeader(
                                title = chartShelf.title.ifBlank { strings.charts },
                                subtitle = chartShelf.subtitle
                            )
                        }
                    }
                    if (chartShelf.tracks.isNotEmpty()) {
                        item(key = "discover_chart_tracks_${chartShelf.id}") {
                            Box(modifier = Modifier.homeRevealOnAppear("discover_chart_tracks_${chartShelf.id}", revealSeen, cascade = true)) {
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
                    }
                    if (chartShelf.albums.isNotEmpty()) {
                        item(key = "discover_chart_albums_${chartShelf.id}") {
                            Box(modifier = Modifier.homeRevealOnAppear("discover_chart_albums_${chartShelf.id}", revealSeen, cascade = true)) {
                                AlbumHorizontalShelf(
                                    albums = chartShelf.albums,
                                    strings = strings,
                                    detailFraction = detailFraction,
                                    sharedTransitionScope = null,
                                    animatedVisibilityScope = null,
                                    onOpenAlbum = { album, bounds ->
                                        launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                        onOpenAlbum(album)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.newReleases.isNotEmpty()) {
                item(key = "discover_new_releases_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("discover_new_releases_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.newReleases)
                    }
                }
                item(key = "discover_new_releases_row") {
                    Box(modifier = Modifier.homeRevealOnAppear("discover_new_releases_row", revealSeen, cascade = true)) {
                        AlbumHorizontalShelf(
                            albums = uiState.newReleases,
                            strings = strings,
                            detailFraction = detailFraction,
                            sharedTransitionScope = null,
                            animatedVisibilityScope = null,
                            onOpenAlbum = { album, bounds ->
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                onOpenAlbum(album)
                            }
                        )
                    }
                }
            }

            if (uiState.moodCategories.isNotEmpty()) {
                item(key = "discover_moods_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("discover_moods_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.moodsAndGenres)
                    }
                }
                item(key = "discover_moods_row") {
                    Box(modifier = Modifier.homeRevealOnAppear("discover_moods_row", revealSeen, cascade = true)) {
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
                                    onClick = { bounds ->
                                        launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                        onOpenMood(mood)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "discover_quick_picks_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("discover_quick_picks_header", revealSeen, cascade = true)) {
                        SectionHeader(title = strings.quickPicks)
                    }
                }
                items(
                    items = uiState.quickPicks.take(8),
                    key = { track -> "discover_qp_${track.id}" }
                ) { track ->
                    Box(modifier = Modifier.homeRevealOnAppear("discover_qp_${track.id}", revealSeen, cascade = true)) {
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
            scrollProgress = { scrollProgress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion)
        )

        val shownDetail = uiState.detailSheet ?: lastDetail
        if (shownDetail != null && (detailVisible || detailFraction > 0.005f)) {
            AlbumPlaylistDetailSheet(
                detail = shownDetail,
                strings = strings,
                palette = uiState.heroPalette,
                activeTrackId = uiState.activeTrackId,
                isPlaying = uiState.isPlaying,
                hasMiniPlayer = uiState.hasMiniPlayer,
                dynamicMeshEnabled = uiState.dynamicMeshEnabled,
                glassBlurEnabled = uiState.glassBlurEnabled,
                detailFraction = detailFraction,
                launchOrigin = launchOrigin,
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
    onOpenAlbum: (Album, Rect?) -> Unit
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
                onClick = { bounds -> onOpenAlbum(album, bounds) }
            )
        }
    }
}
