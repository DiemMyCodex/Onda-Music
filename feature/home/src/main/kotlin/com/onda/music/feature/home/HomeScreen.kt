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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.homeRecede
import com.onda.music.core.design.ondaBlurSource
import com.onda.music.core.design.ondaLaunchRise
import com.onda.music.core.design.rememberOndaBackdropState
import com.onda.music.core.model.Album
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Track
import java.util.Locale

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
    val homeTitle = if (uiState.languageCode.startsWith("vi", ignoreCase = true)) {
        "Trang chủ"
    } else {
        "Home"
    }
    val showcaseTitle = if (uiState.languageCode.startsWith("vi", ignoreCase = true)) {
        "Tuyển chọn Onda"
    } else {
        "Onda Selections"
    }
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val homeBackdropState = rememberOndaBackdropState()
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
    val bottomClearance = DockMetrics.rememberBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer)
    val heroHeightDp = rememberHomeHeroHeight()
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

    val quickJumpPool = remember(
        uiState.recentlyPlayed,
        uiState.quickPicks,
        uiState.mostPlayed,
        uiState.localTracks
    ) {
        (uiState.recentlyPlayed + uiState.quickPicks + uiState.mostPlayed + uiState.localTracks)
            .distinctBy { it.id }
    }
    val quickJumpTracks = remember(quickJumpPool) {
        quickJumpPool.take(4)
    }
    val editorialShowcaseItems = remember(
        uiState.chartShelves,
        uiState.shelves,
        uiState.languageCode
    ) {
        buildEditorialShowcaseItems(uiState, strings)
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
                .ondaBlurSource(
                    state = homeBackdropState,
                    isActive = { heroScrollProgress > 0.05f }
                )
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion),
            contentPadding = PaddingValues(top = 0.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (heroCards.isNotEmpty()) {
                item(key = "hero_card") {
                    Box(modifier = Modifier.ondaLaunchRise(order = 1, distance = 44.dp)) {
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
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 22.dp)
                                onOpenAlbum(album)
                            },
                            onToggleLike = onToggleLike
                        )
                    }
                }
            } else if (uiState.isLoading) {
                item(key = "hero_skeleton") {
                    Box(modifier = Modifier.ondaLaunchRise(order = 1, distance = 44.dp)) {
                        HomeHeroSkeleton()
                    }
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
                    Box(modifier = Modifier.ondaLaunchRise(order = 2, distance = 48.dp)) {
                        HomeShelfSkeleton()
                    }
                }
            }

            if (quickJumpTracks.isNotEmpty()) {
                item(key = "quick_jump_grid") {
                    Box(
                        modifier = Modifier
                            .ondaLaunchRise(order = 2, distance = 48.dp)
                            .homeRevealOnAppear("quick_jump_grid", revealSeen, cascade = true)
                    ) {
                        QuickJumpGrid(
                            tracks = quickJumpTracks,
                            strings = strings,
                            activeTrackId = uiState.activeTrackId,
                            isPlaying = uiState.isPlaying,
                            onPlayTrack = { track -> onPlayTrack(track, quickJumpPool) }
                        )
                    }
                }
            }

            if (uiState.recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("recently_played_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.recentlyPlayed)
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

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "quick_picks_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("quick_picks_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.quickPicks)
                        SnappingTrackColumnGrid(
                            tracks = uiState.quickPicks,
                            strings = strings,
                            activeTrackId = uiState.activeTrackId,
                            isPlaying = uiState.isPlaying,
                            showRanks = false,
                            onPlayTrack = { track -> onPlayTrack(track, uiState.quickPicks) },
                            onToggleLike = onToggleLike
                        )
                    }
                }
            }

            if (uiState.newReleases.isNotEmpty()) {
                item(key = "new_releases_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("new_releases_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.newReleases)
                        AlbumHorizontalShelf(
                            albums = uiState.newReleases,
                            strings = strings,
                            detailFraction = detailFraction,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onOpenAlbum = { album, bounds ->
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 22.dp)
                                onOpenAlbum(album)
                            }
                        )
                    }
                }
            }

            if (editorialShowcaseItems.isNotEmpty()) {
                item(key = "editorial_showcase_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("editorial_showcase_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = showcaseTitle)
                        EditorialShowcaseShelf(
                            items = editorialShowcaseItems,
                            onOpenAlbum = { album, bounds ->
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                onOpenAlbum(album)
                            },
                            onPlayShelf = { shelf ->
                                val firstTrack = shelf.tracks.firstOrNull() ?: return@EditorialShowcaseShelf
                                onPlayTrack(firstTrack, shelf.tracks)
                            }
                        )
                    }
                }
            }

            if (uiState.mostPlayed.isNotEmpty()) {
                item(key = "most_played_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("most_played_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.mostPlayed)
                        SnappingTrackColumnGrid(
                            tracks = uiState.mostPlayed,
                            strings = strings,
                            activeTrackId = uiState.activeTrackId,
                            isPlaying = uiState.isPlaying,
                            showRanks = true,
                            onPlayTrack = { track -> onPlayTrack(track, uiState.mostPlayed) }
                        )
                    }
                }
            }

            if (uiState.moodCategories.isNotEmpty()) {
                item(key = "moods_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("moods_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.moodsAndGenres)
                        MoodTwoRowGrid(
                            moods = uiState.moodCategories,
                            onOpenMood = { mood, bounds ->
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                onOpenMood(mood)
                            }
                        )
                    }
                }
            }

            uiState.shelves.forEach { shelf ->
                if (shelf.tracks.isNotEmpty() || shelf.albums.isNotEmpty()) {
                    item(key = "shelf_section_${shelf.id}") {
                        Column(
                            modifier = Modifier.homeRevealOnAppear("shelf_section_${shelf.id}", revealSeen, cascade = true),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeader(
                                title = shelf.title,
                                subtitle = shelf.subtitle
                            )
                            if (shelf.tracks.isNotEmpty()) {
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
                            if (shelf.albums.isNotEmpty()) {
                                AlbumHorizontalShelf(
                                    albums = shelf.albums,
                                    strings = strings,
                                    detailFraction = detailFraction,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onOpenAlbum = { album, bounds ->
                                        launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 22.dp)
                                        onOpenAlbum(album)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            uiState.chartShelves.forEach { chartShelf ->
                if (chartShelf.tracks.isNotEmpty()) {
                    item(key = "chart_section_${chartShelf.id}") {
                        Column(
                            modifier = Modifier.homeRevealOnAppear("chart_section_${chartShelf.id}", revealSeen, cascade = true),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeader(
                                title = chartShelf.title.ifBlank { strings.charts },
                                subtitle = chartShelf.subtitle
                            )
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
            }

            if (uiState.localTracks.isNotEmpty() && (uiState.isOfflineFallback || uiState.shelves.isEmpty())) {
                item(key = "local_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("local_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.offlineLibrary)
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
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }

        MinimalFloatingTopBar(
            username = uiState.username,
            avatarPath = uiState.avatarPath,
            settingsDescription = strings.settingsDescription,
            onOpenSettings = onOpenSettings,
            scrollProgress = { heroScrollProgress },
            backdropState = homeBackdropState,
            blurEnabled = uiState.glassBlurEnabled,
            title = homeTitle,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .ondaLaunchRise(order = 0, distance = 16.dp)
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
    val discoverBackdropState = rememberOndaBackdropState()
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
    val bottomClearance = DockMetrics.rememberBottomPadding(hasMiniPlayer = uiState.hasMiniPlayer)
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
    val contextualShelf = uiState.contextualShelf
    val contextualTracks = contextualShelf?.tracks.orEmpty()
    val hasDiscoverContent = uiState.quickPicks.isNotEmpty() ||
        contextualTracks.isNotEmpty() ||
        uiState.chartShelves.isNotEmpty() ||
        uiState.newReleases.isNotEmpty() ||
        uiState.moodCategories.isNotEmpty()
    val discoverGlowCovers = remember(
        uiState.quickPicks,
        contextualTracks,
        uiState.newReleases,
        uiState.chartShelves
    ) {
        (uiState.quickPicks.mapNotNull { it.artworkUrl } +
            contextualTracks.mapNotNull { it.artworkUrl } +
            uiState.newReleases.mapNotNull { it.artworkUrl } +
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
                .ondaBlurSource(
                    state = discoverBackdropState,
                    isActive = { scrollProgress > 0.01f }
                )
                .homeRecede(fraction = detailFraction, reducedMotion = reducedMotion),
            contentPadding = PaddingValues(top = 0.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "discover_top_clearance") {
                Spacer(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(58.dp)
                )
            }

            item(key = "discover_heading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = discoverHeading,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = strings.refreshDescription,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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

            if (uiState.quickPicks.isNotEmpty()) {
                item(key = "discover_quick_picks_header") {
                    Box(modifier = Modifier.homeRevealOnAppear("discover_quick_picks_header", revealSeen, cascade = true)) {
                        SectionHeader(
                            title = strings.quickPicks,
                            subtitle = if (contextualShelf != null) strings.forYourTasteSubtitle else null
                        )
                    }
                }
                items(
                    items = uiState.quickPicks.take(10),
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

            if (contextualShelf != null && contextualTracks.isNotEmpty()) {
                item(key = "discover_contextual_shelf") {
                    val seedLabel = if (contextualShelf.useArtistHeading) {
                        contextualShelf.seedArtist
                    } else {
                        contextualShelf.seedTrackTitle
                    }
                    val shelfTitle = String.format(Locale.getDefault(), strings.becauseYouListenedFormat, seedLabel)
                    val shelfSubtitle = if (!contextualShelf.useArtistHeading && contextualShelf.seedArtist.isNotBlank()) {
                        contextualShelf.seedArtist
                    } else {
                        null
                    }
                    Column(
                        modifier = Modifier.homeRevealOnAppear("discover_contextual_shelf", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = shelfTitle,
                            subtitle = shelfSubtitle
                        )
                        TrackHorizontalShelf(
                            tracks = contextualTracks,
                            strings = strings,
                            activeTrackId = uiState.activeTrackId,
                            isPlaying = uiState.isPlaying,
                            detailFraction = detailFraction,
                            sharedTransitionScope = null,
                            animatedVisibilityScope = null,
                            onPlayTrack = { track -> onPlayTrack(track, contextualTracks) }
                        )
                    }
                }
            }

            if (uiState.moodCategories.isNotEmpty()) {
                item(key = "discover_moods_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("discover_moods_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.moodsAndGenres)
                        MoodTwoRowGrid(
                            moods = uiState.moodCategories,
                            onOpenMood = { mood, bounds ->
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 24.dp)
                                onOpenMood(mood)
                            }
                        )
                    }
                }
            }

            if (uiState.newReleases.isNotEmpty()) {
                item(key = "discover_new_releases_section") {
                    Column(
                        modifier = Modifier.homeRevealOnAppear("discover_new_releases_section", revealSeen, cascade = true),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(title = strings.newReleases)
                        AlbumHorizontalShelf(
                            albums = uiState.newReleases,
                            strings = strings,
                            detailFraction = detailFraction,
                            sharedTransitionScope = null,
                            animatedVisibilityScope = null,
                            onOpenAlbum = { album, bounds ->
                                launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 22.dp)
                                onOpenAlbum(album)
                            }
                        )
                    }
                }
            }

            uiState.chartShelves.forEach { chartShelf ->
                if (chartShelf.tracks.isNotEmpty() || chartShelf.albums.isNotEmpty()) {
                    item(key = "discover_chart_section_${chartShelf.id}") {
                        Column(
                            modifier = Modifier.homeRevealOnAppear("discover_chart_section_${chartShelf.id}", revealSeen, cascade = true),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeader(
                                title = chartShelf.title.ifBlank { strings.charts },
                                subtitle = chartShelf.subtitle
                            )
                            if (chartShelf.tracks.isNotEmpty()) {
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
                            if (chartShelf.albums.isNotEmpty()) {
                                AlbumHorizontalShelf(
                                    albums = chartShelf.albums,
                                    strings = strings,
                                    detailFraction = detailFraction,
                                    sharedTransitionScope = null,
                                    animatedVisibilityScope = null,
                                    onOpenAlbum = { album, bounds ->
                                        launchOrigin = CardLaunchOrigin(bounds = bounds, cornerRadius = 22.dp)
                                        onOpenAlbum(album)
                                    }
                                )
                            }
                        }
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
                            .padding(horizontal = 24.dp, vertical = 12.dp)
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
            backdropState = discoverBackdropState,
            blurEnabled = uiState.glassBlurEnabled,
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
