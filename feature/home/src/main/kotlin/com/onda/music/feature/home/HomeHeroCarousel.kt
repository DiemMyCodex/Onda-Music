package com.onda.music.feature.home

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.model.Album
import com.onda.music.core.model.Track
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

private const val HeroAdvanceDelayMs = 5500L
private const val HeroAutoSlideMs = 950
private val HeroAutoSlideEasing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f)
private const val WipeMediaTravel = 0.16f
private const val WipeTextTravel = 0.42f
private const val WipeFeatherShare = 0.52f
private const val WipeTrailShare = 0.24f
private val WipeFeatherEase = floatArrayOf(0f, 0.104f, 0.352f, 0.648f, 0.896f, 1f)

@Immutable
internal sealed interface HeroCarouselCard {
    val id: String
    val eyebrow: String
    val seedTitle: String?
    val seedArtworkUrl: String?
    val headline: String
    val artworkUrl: String?

    @Immutable
    data class TrackCard(
        val track: Track,
        override val eyebrow: String,
        override val seedTitle: String? = null,
        override val seedArtworkUrl: String? = null,
        val queue: List<Track>
    ) : HeroCarouselCard {
        override val id: String = "hero_track_${track.id}"
        override val headline: String = if (track.artist.isNotBlank()) {
            "${track.title} - ${track.artist}"
        } else {
            track.title
        }
        override val artworkUrl: String? = track.artworkUrl
    }

    @Immutable
    data class AlbumCard(
        val album: Album,
        override val eyebrow: String
    ) : HeroCarouselCard {
        override val id: String = "hero_album_${album.id}"
        override val seedTitle: String? = null
        override val seedArtworkUrl: String? = null
        override val headline: String = if (album.artist.isNotBlank()) {
            "${album.title} - ${album.artist}"
        } else {
            album.title
        }
        override val artworkUrl: String? = album.artworkUrl
    }
}

internal fun buildHeroCarouselCards(
    uiState: HomeUiState,
    strings: HomeStrings
): List<HeroCarouselCard> {
    val cards = mutableListOf<HeroCarouselCard>()
    val usedTrackIds = HashSet<String>()
    val seedTrack = uiState.recentlyPlayed.firstOrNull()

    uiState.heroTrack?.let { hero ->
        usedTrackIds.add(hero.id)
        val isActive = uiState.activeTrackId == hero.id
        val eyebrow = when {
            isActive && uiState.isPlaying -> if (uiState.languageCode == "vi") "Đang phát" else "Now Playing"
            else -> strings.continueListening
        }
        val queue = (listOf(hero) + uiState.quickPicks + uiState.recentlyPlayed + uiState.mostPlayed + uiState.localTracks)
            .distinctBy { it.id }
        cards += HeroCarouselCard.TrackCard(
            track = hero,
            eyebrow = eyebrow,
            queue = queue
        )
    }

    uiState.quickPicks
        .filter { it.id !in usedTrackIds && !it.artworkUrl.isNullOrBlank() }
        .take(2)
        .forEachIndexed { idx, pick ->
            usedTrackIds.add(pick.id)
            val useSeed = idx == 0 && seedTrack != null && seedTrack.id != pick.id
            cards += HeroCarouselCard.TrackCard(
                track = pick,
                eyebrow = if (useSeed) {
                    if (uiState.languageCode == "vi") "Từ lần nghe" else "Inspired by"
                } else {
                    strings.quickPicks
                },
                seedTitle = if (useSeed) seedTrack?.title else null,
                seedArtworkUrl = if (useSeed) seedTrack?.artworkUrl else null,
                queue = uiState.quickPicks
            )
        }

    uiState.newReleases
        .firstOrNull { !it.artworkUrl.isNullOrBlank() }
        ?.let { release ->
            cards += HeroCarouselCard.AlbumCard(
                album = release,
                eyebrow = strings.newReleases
            )
        }

    uiState.shelves
        .asSequence()
        .flatMap { shelf -> shelf.tracks.asSequence().map { shelf.title to it } }
        .filter { (_, track) -> track.id !in usedTrackIds && !track.artworkUrl.isNullOrBlank() }
        .take(2)
        .forEach { (shelfTitle, track) ->
            usedTrackIds.add(track.id)
            cards += HeroCarouselCard.TrackCard(
                track = track,
                eyebrow = shelfTitle.ifBlank { strings.charts },
                queue = listOf(track) + uiState.quickPicks
            )
        }

    if (cards.size < 3) {
        uiState.mostPlayed
            .filter { it.id !in usedTrackIds }
            .take(3 - cards.size)
            .forEach { track ->
                usedTrackIds.add(track.id)
                cards += HeroCarouselCard.TrackCard(
                    track = track,
                    eyebrow = strings.mostPlayed,
                    queue = uiState.mostPlayed
                )
            }
    }

    return cards.take(6)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeHeroCarousel(
    cards: List<HeroCarouselCard>,
    strings: HomeStrings,
    activeTrackId: String?,
    isPlaying: Boolean,
    heroScrollProgress: () -> Float,
    blurEnabled: Boolean,
    detailVisible: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayHero: () -> Unit,
    onOpenAlbum: (Album, Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = OndaTheme.reducedMotion
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.46f).dp.coerceIn(380.dp, 420.dp)
    val isSingle = cards.size <= 1
    val loopMultiplier = 200
    val initialPage = if (isSingle) 0 else (loopMultiplier / 2) * cards.size
    val pageCount = if (isSingle) cards.size.coerceAtLeast(1) else cards.size * loopMultiplier
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    var userSwipe by remember { mutableStateOf(false) }
    LaunchedEffect(isDragged) {
        if (isDragged) userSwipe = true
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) userSwipe = false
        }
    }
    val wipeCurve by animateFloatAsState(
        targetValue = if (userSwipe) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(240),
        label = "heroWipeCurve"
    )

    LaunchedEffect(pagerState, cards.size, isDragged, reducedMotion, detailVisible) {
        if (isSingle || isDragged || reducedMotion || detailVisible) return@LaunchedEffect
        while (true) {
            delay(HeroAdvanceDelayMs)
            if (heroScrollProgress() > 0.08f || detailVisible) continue
            val next = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
            pagerState.animateScrollToPage(
                page = next,
                animationSpec = tween(HeroAutoSlideMs, easing = HeroAutoSlideEasing)
            )
        }
    }

    LaunchedEffect(pagerState.currentPage, cards) {
        if (cards.size > 1) {
            val nextCard = cards[(pagerState.currentPage + 1) % cards.size]
            val nextUrl = nextCard.artworkUrl
            if (!nextUrl.isNullOrBlank()) {
                SingletonImageLoader.get(context).enqueue(
                    ImageRequest.Builder(context).data(nextUrl).build()
                )
            }
        }
    }

    ScrollAwayCover(
        progress = heroScrollProgress,
        blurEnabled = blurEnabled,
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(OledFallbackSurface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isSingle,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val card = cards[page % cards.size]
                var cardBounds by remember { mutableStateOf<Rect?>(null) }
                val pageOffsetProvider = {
                    (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                }
                val isCardPlaying = card is HeroCarouselCard.TrackCard &&
                    card.track.id == activeTrackId &&
                    isPlaying

                HeroCarouselPage(
                    card = card,
                    strings = strings,
                    isThisPlaying = isCardPlaying,
                    pageOffset = pageOffsetProvider,
                    wipeCurve = { wipeCurve },
                    blurEnabled = blurEnabled,
                    onPrimaryClick = {
                        when (card) {
                            is HeroCarouselCard.TrackCard -> {
                                if (card.track.id == activeTrackId) {
                                    onPlayHero()
                                } else {
                                    onPlayTrack(card.track, card.queue)
                                }
                            }
                            is HeroCarouselCard.AlbumCard -> {
                                onOpenAlbum(card.album, cardBounds)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { cardBounds = it.boundsInRoot() }
                )
            }

            if (cards.size > 1) {
                val activeIndex = pagerState.currentPage % cards.size
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cards.forEachIndexed { idx, _ ->
                        val selected = idx == activeIndex
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(if (selected) 16.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color.White else Color.White.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCarouselPage(
    card: HeroCarouselCard,
    strings: HomeStrings,
    isThisPlaying: Boolean,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    blurEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = OndaTheme.reducedMotion

    HeroWipeContainer(
        pageOffset = pageOffset,
        curve = wipeCurve,
        blurEnabled = blurEnabled,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reducedMotion) {
                        val d = pageOffset().coerceIn(-1f, 1f)
                        val scale = 1f + 2f * WipeMediaTravel * abs(d)
                        scaleX = scale
                        scaleY = scale
                        translationX = d * size.width * WipeMediaTravel
                    }
                }
                .background(OledFallbackSurface)
        ) {
            if (!card.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(card.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = card.headline,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = card.headline,
                    tint = Color(0x2EFFFFFF),
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0x66000000),
                            0.25f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.78f to Color(0xCC000000),
                            1.00f to Color(0xFF000000)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 88.dp, bottom = 20.dp, top = 16.dp)
                .graphicsLayer {
                    val d = pageOffset()
                    if (!reducedMotion) {
                        translationX = d * size.width * (WipeTextTravel + WipeMediaTravel)
                    }
                    alpha = (1f - abs(d) * 1.9f).coerceIn(0f, 1f)
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = card.eyebrow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFD0D0D0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!card.seedArtworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(card.seedArtworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                if (!card.seedTitle.isNullOrBlank()) {
                    Text(
                        text = card.seedTitle!!,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            AnimatedContent(
                targetState = card.headline,
                transitionSpec = { heroTitleSwap(reducedMotion) },
                label = "heroHeadline"
            ) { headlineText ->
                Text(
                    text = headlineText,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val pillKind = when {
                card is HeroCarouselCard.AlbumCard -> HeroActionKind.VIEW
                isThisPlaying -> HeroActionKind.PAUSE
                else -> HeroActionKind.PLAY
            }
            HeroActionPill(
                kind = pillKind,
                playLabel = strings.playAction,
                pauseLabel = strings.pauseAction,
                viewLabel = if (strings.playAction == "Phát") "Xem" else "View",
                onClick = onPrimaryClick
            )
        }
    }
}

private enum class HeroActionKind { PLAY, PAUSE, VIEW }

@Composable
private fun HeroActionPill(
    kind: HeroActionKind,
    playLabel: String,
    pauseLabel: String,
    viewLabel: String,
    onClick: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 44.dp)
            .homePressBounce(interactionSource, pressedScale = 0.92f)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB3C4C4C4))
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 999.dp),
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { heroGlyphSwap(reducedMotion) },
            label = "heroPillAction"
        ) { current ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (current) {
                    HeroActionKind.PLAY, HeroActionKind.PAUSE -> {
                        Icon(
                            imageVector = if (current == HeroActionKind.PAUSE) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF121212),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (current == HeroActionKind.PAUSE) pauseLabel else playLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF121212),
                            maxLines = 1
                        )
                    }
                    HeroActionKind.VIEW -> {
                        Text(
                            text = viewLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF121212),
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF121212),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.heroGlyphSwap(
    reducedMotion: Boolean
): ContentTransform {
    if (reducedMotion) {
        return fadeIn(snap()) togetherWith fadeOut(snap())
    }
    return (scaleIn(initialScale = 0.65f, animationSpec = spring(dampingRatio = 0.58f, stiffness = 620f)) + fadeIn(tween(120)))
        .togetherWith(scaleOut(targetScale = 0.65f, animationSpec = tween(100)) + fadeOut(tween(80)))
        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.78f, stiffness = 500f) })
}

private fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.heroTitleSwap(
    reducedMotion: Boolean
): ContentTransform {
    if (reducedMotion) {
        return fadeIn(snap()) togetherWith fadeOut(snap())
    }
    return (slideInVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) { it / 3 } + fadeIn(tween(200)))
        .togetherWith(slideOutVertically(tween(150)) { -it / 3 } + fadeOut(tween(130)))
        .using(SizeTransform(clip = false))
}

@Composable
private fun HeroWipeContainer(
    pageOffset: () -> Float,
    curve: () -> Float,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    if (reducedMotion) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    val d = pageOffset().coerceIn(-1f, 1f)
                    alpha = (1f - abs(d)).coerceIn(0f, 1f)
                },
            content = content
        )
        return
    }

    val blurPx = if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        with(LocalDensity.current) { 16.dp.toPx() }
    } else {
        0f
    }
    val pageLayer = rememberGraphicsLayer()
    val sourceEdge = remember { WipeEdgeGeometry() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val d = pageOffset()
                translationX = -d * size.width
                alpha = if (abs(d) >= 0.999f) 0f else 1f
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .drawWithContent {
                    if (sourceEdge.update(pageOffset(), size, curve(), blurPx)) {
                        pageLayer.record { this@drawWithContent.drawContent() }
                    } else {
                        drawContent()
                    }
                },
            content = content
        )
        WipeMaskedCopy(
            pageLayer = pageLayer,
            pageOffset = pageOffset,
            curve = curve,
            blurPx = blurPx
        )
    }
}

@Composable
private fun WipeMaskedCopy(
    pageLayer: GraphicsLayer,
    pageOffset: () -> Float,
    curve: () -> Float,
    blurPx: Float
) {
    val edge = remember { WipeEdgeGeometry() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val a = abs(pageOffset())
                compositingStrategy = if (a in 0.001f..0.999f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0f && a in 0.001f..0.999f) {
                    renderEffect = HomeBlurCache.blur(a * blurPx)
                } else {
                    renderEffect = null
                }
            }
            .drawWithContent {
                if (!edge.update(pageOffset(), size, curve(), blurPx)) return@drawWithContent
                val w = size.width
                val h = size.height
                val halfH = h * 0.5f
                fun fullyInside(radius: Float): Float {
                    return if (radius > halfH) {
                        min(w, edge.centerX - sqrt(radius * radius - halfH * halfH))
                    } else {
                        w
                    }
                }
                val left: Float
                val right: Float
                val maskLeft: Float
                val maskRight: Float
                if (edge.incoming) {
                    left = (edge.centerX - edge.outer).coerceAtLeast(0f)
                    right = w
                    maskLeft = left
                    maskRight = fullyInside(edge.inner)
                } else {
                    left = 0f
                    right = fullyInside(edge.inner)
                    maskLeft = (edge.centerX - edge.outer).coerceAtLeast(0f)
                    maskRight = right
                }
                if (right <= left) return@drawWithContent
                clipRect(left, 0f, right, h) {
                    drawLayer(pageLayer)
                }
                if (maskRight > maskLeft) {
                    val center = Offset(edge.centerX, edge.centerY)
                    val stops = if (edge.incoming) {
                        buildFeatherStops(edge.inner / edge.outer, inside = Color.Transparent, outside = Color.Black)
                    } else {
                        buildFeatherStops(edge.inner / edge.outer, inside = Color.Black, outside = Color.Transparent)
                    }
                    drawRect(
                        brush = Brush.radialGradient(*stops, center = center, radius = edge.outer),
                        topLeft = Offset(maskLeft, 0f),
                        size = Size(maskRight - maskLeft, h),
                        blendMode = BlendMode.DstOut
                    )
                }
            }
    )
}

private class WipeEdgeGeometry {
    var incoming = false
    var centerX = 0f
    var centerY = 0f
    var inner = 0f
    var outer = 0f

    fun update(d: Float, size: Size, curve: Float, blurPx: Float): Boolean {
        val a = abs(d)
        if (a < 0.001f || a >= 0.999f) return false
        val w = size.width
        val feather = w * WipeFeatherShare
        val progress = if (d > 0f) 1f - d else -d
        val off = w * 0.6f / curve.coerceIn(0.04f, 1f)
        centerX = w + off
        centerY = size.height * 0.5f
        val from = off - feather
        val to = hypot(centerX, centerY)
        val r = from + (to - from) * progress
        incoming = d > 0f
        if (incoming) {
            inner = r
            outer = r + feather
        } else {
            inner = r - feather * WipeTrailShare
            outer = inner + feather
        }
        val erases = if (incoming) inner < to else outer > off
        return erases || (a * blurPx) > 0.5f
    }
}

private fun buildFeatherStops(start: Float, inside: Color, outside: Color): Array<Pair<Float, Color>> {
    val s = start.coerceIn(0f, 0.999f)
    return Array(WipeFeatherEase.size) { i ->
        val t = i / (WipeFeatherEase.size - 1f)
        (s + (1f - s) * t) to lerp(inside, outside, WipeFeatherEase[i])
    }
}
