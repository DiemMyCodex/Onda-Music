package com.onda.music.feature.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.morphingCornerClip
import com.onda.music.core.model.Album
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Track
import kotlin.math.PI
import kotlin.math.sin

internal val OledBlack = Color(0xFF000000)
internal val OledFallbackSurface = Color(0xFF0D0D0D)
internal val OledSubtleSurface = Color(0xFF141414)
internal val OledMutedText = Color(0xFF9A9A9A)
internal val OledChevronTint = Color(0xFF9E9E9E)

@Composable
internal fun MinimalFloatingTopBar(
    username: String,
    avatarPath: String?,
    settingsDescription: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollProgress: () -> Float = { 0f }
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = homeSmoothstep(0.15f, 0.65f, scrollProgress())
                }
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xEC000000),
                            0.72f to Color(0xCC000000),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.75.dp)
                .graphicsLayer {
                    alpha = homeSmoothstep(0.25f, 0.75f, scrollProgress()) * 0.55f
                }
                .background(Color.White.copy(alpha = 0.14f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MinimalOndaWaveIcon(modifier = Modifier.size(30.dp))

            UserAvatarSettingsButton(
                avatarPath = avatarPath,
                username = username,
                contentDescription = settingsDescription,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
internal fun MinimalOndaWaveIcon(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.085f

        val playPath = Path().apply {
            moveTo(w * 0.16f, h * 0.22f)
            lineTo(w * 0.44f, h * 0.50f)
            lineTo(w * 0.16f, h * 0.78f)
            close()
        }
        drawPath(
            path = playPath,
            color = Color.White
        )

        val wavePath = Path()
        val startX = w * 0.52f
        val endX = w * 0.94f
        val midY = h * 0.50f
        val amp = h * 0.24f
        val steps = 16
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = startX + (endX - startX) * t
            val envelope = sin(t * PI.toFloat())
            val y = midY - sin(t * 2.2f * PI.toFloat()) * amp * (0.45f + 0.55f * envelope)
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        drawPath(
            path = wavePath,
            color = Color.White,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun UserAvatarSettingsButton(
    avatarPath: String?,
    username: String,
    contentDescription: String,
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
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.46f).dp.coerceIn(380.dp, 420.dp)
    val headline = if (subtitle.isNotBlank()) {
        "${track.title} - $subtitle"
    } else {
        track.title
    }

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
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = strings.continueListening,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFD0D0D0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = headline,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xB3C4C4C4))
                    .clickable(onClick = onPlayClick)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isActiveTrack && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF121212),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isActiveTrack && isPlaying) strings.pauseAction else strings.playAction,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF121212),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(OledSubtleSurface)
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = OledChevronTint,
                modifier = Modifier.size(18.dp)
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .width(148.dp)
            .homePressBounce(interactionSource, pressedScale = 0.96f)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 26.dp, spillX = 4.dp, spillY = 4.dp),
                onClick = onClick
            )
    ) {
        val artModifier = Modifier
            .size(148.dp)
            .morphingCornerClip(
                startRadius = 24.dp,
                endRadius = GlassTokens.RadiusXl,
                fractionProvider = { detailFraction }
            )
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
                    modifier = Modifier.size(34.dp)
                )
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000)),
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        modifier = modifier
            .width(148.dp)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .homePressBounce(interactionSource, pressedScale = 0.96f)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = 26.dp, spillX = 4.dp, spillY = 4.dp),
                onClick = { onClick(cardBounds) }
            )
    ) {
        val artModifier = Modifier
            .size(148.dp)
            .morphingCornerClip(
                startRadius = 24.dp,
                endRadius = GlassTokens.RadiusXl,
                fractionProvider = { detailFraction }
            )
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
                append(" • ")
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
            .background(OledSubtleSurface)
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
            tint = OledChevronTint,
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
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

internal val ShelfHorizontalPadding = PaddingValues(horizontal = 16.dp)
