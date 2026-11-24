package com.onda.music.feature.home

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.BlurDirection
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaBackdropState
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ProgressiveBlurBackground
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.damageBoundary
import com.onda.music.core.model.Album
import com.onda.music.core.model.HomeShelf
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Track

internal val OledBlack = Color(0xFF000000)
internal val OledFallbackSurface = Color(0xFF0D0D0D)
internal val OledSubtleSurface = Color(0xFF141414)
internal val OledCardSurface = Color(0xFF141416)
internal val OledCardBorder = Color(0x14FFFFFF)
internal val OledMutedText = Color(0xFF9A9A9A)
internal val OledChevronTint = Color(0xFF9E9E9E)
internal val ShelfHorizontalPadding = PaddingValues(horizontal = 24.dp)

@Composable
internal fun rememberHomeHeroHeight(): Dp {
    return (LocalConfiguration.current.screenHeightDp * 0.58f).dp.coerceIn(430.dp, 530.dp)
}

@Composable
internal fun MinimalFloatingTopBar(
    username: String,
    avatarPath: String?,
    settingsDescription: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollProgress: () -> Float = { 0f },
    backdropState: OndaBackdropState? = null,
    blurEnabled: Boolean = true,
    title: String = ""
) {
    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val scrollAlpha = { homeSmoothstep(0.15f, 0.65f, scrollProgress().coerceIn(0f, 1f)) }
    val showBackdrop by remember {
        derivedStateOf { scrollProgress() > 0.01f }
    }
    val isMorphedTitle by remember(title) {
        derivedStateOf { title.isNotBlank() && scrollProgress() > 0.52f }
    }
    val morph by animateFloatAsState(
        targetValue = if (isMorphedTitle) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 280, easing = FastOutSlowInEasing)
        },
        label = "MinimalTopBarBrandMorph"
    )
    val brandTranslatePx = with(density) { 6.dp.toPx() }
    val plateShape = remember { SmoothCornerShape(9.dp) }

    Box(modifier = modifier.fillMaxWidth()) {
        if (showBackdrop) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = scrollAlpha()
                    }
            ) {
                ProgressiveBlurBackground(
                    backdropState = backdropState,
                    direction = BlurDirection.BOTTOM_TO_TOP,
                    maxBlurRadius = { 28f * scrollAlpha() },
                    foundationColor = Color.Black,
                    blurEnabled = blurEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer {
                    alpha = homeSmoothstep(0.30f, 0.80f, scrollProgress()) * 0.45f
                }
                .background(Color.White.copy(alpha = 0.14f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                val brandAlpha = (1f - morph * 1.25f).coerceIn(0f, 1f)
                if (brandAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer {
                                val s = lerp(1f, 0.72f, morph)
                                scaleX = s
                                scaleY = s
                                alpha = brandAlpha
                                if (!reducedMotion) {
                                    translationY = -morph * brandTranslatePx
                                    if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        renderEffect = HomeBlurCache.blur(morph * 12f)
                                    } else {
                                        renderEffect = null
                                    }
                                }
                            }
                            .clip(plateShape)
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        OndaMonogramMark(
                            modifier = Modifier.size(30.dp),
                            tint = Color.White,
                            cutoutColor = Color(0xFF1A1A1A),
                            filled = true
                        )
                    }
                }

                if (title.isNotBlank() && morph > 0.01f) {
                    Text(
                        text = title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            val s = lerp(0.88f, 1f, morph)
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            alpha = ((morph - 0.10f) / 0.90f).coerceIn(0f, 1f)
                            if (!reducedMotion) {
                                translationY = (1f - morph) * brandTranslatePx
                                if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = HomeBlurCache.blur((1f - morph) * 12f)
                                } else {
                                    renderEffect = null
                                }
                            }
                        }
                    )
                }
            }

            UserAvatarSettingsButton(
                avatarPath = avatarPath,
                username = username,
                contentDescription = settingsDescription,
                visualScale = { lerp(1f, 0.90f, morph) },
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun UserAvatarSettingsButton(
    avatarPath: String?,
    username: String,
    contentDescription: String,
    visualScale: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .size(48.dp)
            .homePressBounce(interactionSource, pressedScale = 0.90f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 24.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    val s = visualScale()
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(Color(0x88000000))
                .border(width = 1.dp, color = Color(0x33FFFFFF), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else if (username.isNotBlank()) {
                Text(
                    text = username.trim().take(1).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun FullBleedHeroSection(
    track: Track,
    subtitle: String,
    strings: HomeStrings,
    isPlaying: Boolean,
    isActiveTrack: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heroHeight = rememberHomeHeroHeight()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(OledFallbackSurface)
    ) {
        if (!track.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = track.title,
                tint = Color(0x2EFFFFFF),
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0x52000000),
                            0.24f to Color.Transparent,
                            0.56f to Color(0x38000000),
                            0.82f to Color(0xCC000000),
                            1.00f to Color(0xFF000000)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = strings.continueListening.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = track.title,
                fontSize = 28.sp,
                lineHeight = 33.sp,
                letterSpacing = (-0.6).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(onClick = onPlayClick)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isActiveTrack && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isActiveTrack && isPlaying) strings.pauseAction else strings.playAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun OfflineFallbackBanner(
    strings: HomeStrings,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerShape = remember { SmoothCornerShape(16.dp) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(bannerShape)
            .background(OledCardSurface, bannerShape)
            .border(0.5.dp, OledCardBorder, bannerShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = GlassTokens.WarningAmber,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = strings.offlineNotice,
            fontSize = 12.sp,
            color = OledMutedText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0x26FFFFFF))
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.retryAction,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.44f),
                modifier = Modifier.size(16.dp)
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = OledMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun QuickJumpGrid(
    tracks: List<Track>,
    strings: HomeStrings,
    activeTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(tracks) { tracks.take(4).chunked(2) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { track ->
                    QuickJumpCard(
                        track = track,
                        isActive = track.id == activeTrackId,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickJumpCard(
    track: Track,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = remember { SmoothCornerShape(14.dp) }
    val artShape = remember { SmoothCornerShape(9.dp) }

    Row(
        modifier = modifier
            .height(58.dp)
            .homePressBounce(interactionSource, pressedScale = 0.96f)
            .clip(cardShape)
            .background(OledCardSurface, cardShape)
            .border(0.5.dp, OledCardBorder, cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 14.dp),
                onClick = onClick
            )
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(artShape)
                .background(OledFallbackSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = track.title,
                    tint = OledMutedText,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .damageBoundary(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        HomePlayingIndicator(
                            color = Color.White,
                            isPlaying = true,
                            barWidth = 2.dp,
                            spacing = 1.5.dp,
                            modifier = Modifier.height(13.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = track.title,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun TrackSquareCard(
    track: Track,
    strings: HomeStrings,
    isActive: Boolean,
    isPlaying: Boolean,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp,
    cornerRadius: Dp = 22.dp
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = remember(cornerRadius) { SmoothCornerShape(cornerRadius) }

    Column(
        modifier = modifier
            .width(cardSize)
            .homePressBounce(interactionSource, pressedScale = 0.96f)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = cornerRadius, spillX = 4.dp, spillY = 4.dp),
                onClick = onClick
            )
    ) {
        val artModifier = Modifier
            .size(cardSize)
            .clip(cardShape)
            .let { base ->
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        base.sharedElement(
                            rememberSharedContentState(key = "track_art_${track.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    base
                }
            }

        Box(
            modifier = artModifier.background(OledFallbackSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = track.title,
                    tint = OledMutedText,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .damageBoundary(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        HomePlayingIndicator(
                            color = Color.White,
                            isPlaying = true,
                            barWidth = 2.dp,
                            spacing = 1.5.dp,
                            modifier = Modifier.height(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist.ifBlank { strings.unknownArtist },
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = OledMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlbumSquareCard(
    album: Album,
    strings: HomeStrings,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
    cardSize: Dp = 152.dp,
    cornerRadius: Dp = 22.dp
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    val cardShape = remember(cornerRadius) { SmoothCornerShape(cornerRadius) }

    Column(
        modifier = modifier
            .width(cardSize)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .homePressBounce(interactionSource, pressedScale = 0.96f)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = cornerRadius, spillX = 4.dp, spillY = 4.dp),
                onClick = { onClick(cardBounds) }
            )
    ) {
        val artModifier = Modifier
            .size(cardSize)
            .clip(cardShape)
            .let { base ->
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        base.sharedElement(
                            rememberSharedContentState(key = "album_art_${album.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    base
                }
            }

        Box(
            modifier = artModifier.background(OledFallbackSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!album.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(album.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = album.title,
                    tint = OledMutedText,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val subtitle = buildString {
            append(album.artist.ifBlank { strings.unknownArtist })
            if (album.year != null) {
                append(" · ")
                append(album.year)
            }
        }
        Text(
            text = subtitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = OledMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SnappingTrackColumnGrid(
    tracks: List<Track>,
    strings: HomeStrings,
    activeTrackId: String?,
    isPlaying: Boolean,
    showRanks: Boolean,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val columns = remember(tracks) { tracks.chunked(3) }
    val listState = rememberLazyListState()
    val snapFling = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Start
    )
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columnWidth = if (columns.size <= 1) {
        (screenWidthDp.dp - 48.dp).coerceAtLeast(220.dp)
    } else {
        ((screenWidthDp.dp - 48.dp) * 0.86f).coerceAtLeast(220.dp)
    }

    LazyRow(
        state = listState,
        flingBehavior = snapFling,
        contentPadding = ShelfHorizontalPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            items = columns,
            key = { colIdx, col -> "col_${colIdx}_${col.firstOrNull()?.id.orEmpty()}" }
        ) { colIdx, columnTracks ->
            Column(
                modifier = Modifier.width(columnWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                columnTracks.forEachIndexed { rowIdx, track ->
                    val rank = colIdx * 3 + rowIdx + 1
                    SnappingTrackRow(
                        track = track,
                        strings = strings,
                        rank = if (showRanks) rank else null,
                        isActive = track.id == activeTrackId,
                        isPlaying = isPlaying,
                        onPlay = { onPlayTrack(track) },
                        onToggleLike = onToggleLike?.let { callback -> { callback(track) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SnappingTrackRow(
    track: Track,
    strings: HomeStrings,
    rank: Int?,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleLike: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val likeInteraction = remember { MutableInteractionSource() }
    val artShape = remember { SmoothCornerShape(10.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .homePressBounce(interactionSource, pressedScale = 0.98f)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 12.dp, spillX = 6.dp, spillY = 2.dp),
                onClick = onPlay
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (rank != null) {
            Text(
                text = "%02d".format(rank),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.42f),
                maxLines = 1,
                modifier = Modifier.width(26.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(artShape)
                .background(OledFallbackSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = track.title,
                    tint = OledMutedText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.ifBlank { strings.unknownArtist },
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = OledMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isActive) {
            Box(
                modifier = Modifier.damageBoundary(),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    HomePlayingIndicator(
                        color = Color.White,
                        isPlaying = true,
                        barWidth = 2.2.dp,
                        spacing = 1.5.dp,
                        modifier = Modifier.height(14.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (onToggleLike != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .size(44.dp)
                    .homePressBounce(likeInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = likeInteraction,
                        indication = HomeHighlightIndication(cornerRadius = 22.dp),
                        onClick = onToggleLike
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = track.title,
                    tint = if (track.liked) GlassTokens.ErrorCoral else OledMutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Immutable
internal sealed interface EditorialShowcaseItem {
    val id: String
    val tag: String
    val title: String
    val countLabel: String
    val artworkUrl: String?

    @Immutable
    data class AlbumEntry(
        val album: Album,
        override val tag: String,
        override val countLabel: String
    ) : EditorialShowcaseItem {
        override val id: String = "showcase_album_${album.id}"
        override val title: String = album.title
        override val artworkUrl: String? = album.artworkUrl
    }

    @Immutable
    data class ShelfMixEntry(
        val shelf: HomeShelf,
        override val tag: String,
        override val countLabel: String,
        override val artworkUrl: String?
    ) : EditorialShowcaseItem {
        override val id: String = "showcase_shelf_${shelf.id}"
        override val title: String = shelf.title
    }
}

internal fun buildEditorialShowcaseItems(
    uiState: HomeUiState,
    strings: HomeStrings
): List<EditorialShowcaseItem> {
    val items = mutableListOf<EditorialShowcaseItem>()
    val seenIds = HashSet<String>()

    (uiState.chartShelves + uiState.shelves).forEach { shelf ->
        shelf.albums.forEach { album ->
            if (seenIds.add("a_${album.id}")) {
                val countLabel = if (album.trackCount > 0) {
                    "${album.trackCount} ${strings.tracksSuffix}"
                } else {
                    album.artist.ifBlank { strings.unknownArtist }
                }
                items += EditorialShowcaseItem.AlbumEntry(
                    album = album,
                    tag = "ONDA · ${shelf.title.ifBlank { strings.charts }.uppercase()}",
                    countLabel = countLabel
                )
            }
        }
    }

    uiState.chartShelves.forEach { shelf ->
        if (shelf.tracks.isNotEmpty() && seenIds.add("s_${shelf.id}")) {
            val leadArt = shelf.tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
            if (!leadArt.isNullOrBlank()) {
                items += EditorialShowcaseItem.ShelfMixEntry(
                    shelf = shelf,
                    tag = "ONDA · ${strings.charts.uppercase()}",
                    countLabel = "${shelf.tracks.size} ${strings.tracksSuffix}",
                    artworkUrl = leadArt
                )
            }
        }
    }

    uiState.shelves.forEach { shelf ->
        if (shelf.tracks.size >= 3 && seenIds.add("s_${shelf.id}")) {
            val leadArt = shelf.tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
            if (!leadArt.isNullOrBlank()) {
                items += EditorialShowcaseItem.ShelfMixEntry(
                    shelf = shelf,
                    tag = "ONDA · SELECTION",
                    countLabel = "${shelf.tracks.size} ${strings.tracksSuffix}",
                    artworkUrl = leadArt
                )
            }
        }
    }

    return items.take(10)
}

@Composable
internal fun EditorialShowcaseShelf(
    items: List<EditorialShowcaseItem>,
    onOpenAlbum: (Album, Rect?) -> Unit,
    onPlayShelf: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = ShelfHorizontalPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = items,
            key = { item -> item.id }
        ) { item ->
            EditorialShowcaseCard(
                item = item,
                onClick = { bounds ->
                    when (item) {
                        is EditorialShowcaseItem.AlbumEntry -> onOpenAlbum(item.album, bounds)
                        is EditorialShowcaseItem.ShelfMixEntry -> onPlayShelf(item.shelf)
                    }
                }
            )
        }
    }
}

@Composable
private fun EditorialShowcaseCard(
    item: EditorialShowcaseItem,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = OndaTheme.reducedMotion
    val interactionSource = remember { MutableInteractionSource() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    val cardShape = remember { SmoothCornerShape(24.dp) }
    var extractedTone by remember(item.artworkUrl) {
        mutableStateOf(item.artworkUrl?.let(HomeGlowColorCache::cached))
    }

    LaunchedEffect(item.artworkUrl) {
        val url = item.artworkUrl
        if (!url.isNullOrBlank() && extractedTone == null) {
            extractedTone = HomeGlowColorCache.load(context, url)
        }
    }

    val tintColor by animateColorAsState(
        targetValue = extractedTone ?: OledCardSurface,
        animationSpec = if (reducedMotion) snap() else tween(420),
        label = "EditorialCardTint"
    )

    Box(
        modifier = modifier
            .width(232.dp)
            .height(268.dp)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .homePressBounce(interactionSource, pressedScale = 0.96f)
            .clip(cardShape)
            .background(OledCardSurface, cardShape)
            .border(0.5.dp, Color(0x1FFFFFFF), cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 24.dp),
                onClick = { onClick(cardBounds) }
            )
    ) {
        if (!item.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = item.title,
                tint = OledMutedText,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to tintColor.copy(alpha = 0.14f),
                            0.44f to Color(0x330A0A0D),
                            0.74f to Color(0xBF0B0B0E),
                            1.00f to Color(0xF208080A)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.55f to Color.Transparent,
                            1.00f to tintColor.copy(alpha = 0.36f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.tag,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.0.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.title,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                letterSpacing = (-0.3).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.countLabel,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = Color.White.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun MoodTwoRowGrid(
    moods: List<MoodCategory>,
    onOpenMood: (MoodCategory, Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val topRow = remember(moods) { moods.filterIndexed { index, _ -> index % 2 == 0 } }
    val bottomRow = remember(moods) { moods.filterIndexed { index, _ -> index % 2 == 1 } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topRow.forEach { mood ->
                    MoodPillCard(
                        mood = mood,
                        onClick = { bounds -> onOpenMood(mood, bounds) }
                    )
                }
            }
            if (bottomRow.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    bottomRow.forEach { mood ->
                        MoodPillCard(
                            mood = mood,
                            onClick = { bounds -> onOpenMood(mood, bounds) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MoodPillCard(
    mood: MoodCategory,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val moodAccent = Color(mood.accentColorHex or 0xFF000000L)
    val interactionSource = remember { MutableInteractionSource() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 104.dp, minHeight = DockMetrics.MinTapTarget)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .homePressBounce(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(999.dp))
            .background(OledCardSurface)
            .border(0.5.dp, Color(0x16FFFFFF), RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 999.dp),
                onClick = { onClick(cardBounds) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(moodAccent)
        )
        Text(
            text = mood.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.44f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun QuickPickCompactRow(
    track: Track,
    strings: HomeStrings,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val likeInteraction = remember { MutableInteractionSource() }
    val artShape = remember { SmoothCornerShape(10.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .homePressBounce(interactionSource, pressedScale = 0.98f)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 16.dp),
                onClick = onPlay
            )
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(artShape)
                .background(OledFallbackSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = track.title,
                    tint = OledMutedText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.ifBlank { strings.unknownArtist },
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = OledMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isActive) {
            Box(
                modifier = Modifier.damageBoundary(),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    HomePlayingIndicator(
                        color = Color.White,
                        isPlaying = true,
                        barWidth = 2.2.dp,
                        spacing = 1.5.dp,
                        modifier = Modifier.height(14.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .size(DockMetrics.MinTapTarget)
                .homePressBounce(likeInteraction, pressedScale = 0.88f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = likeInteraction,
                    indication = HomeHighlightIndication(cornerRadius = 24.dp),
                    onClick = onToggleLike
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = track.title,
                tint = if (track.liked) GlassTokens.ErrorCoral else OledMutedText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
