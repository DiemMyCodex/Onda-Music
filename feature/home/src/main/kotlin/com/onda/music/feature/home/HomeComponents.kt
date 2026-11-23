package com.onda.music.feature.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.AgslMeshBackground
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.morphingCornerClip
import com.onda.music.core.design.press3D
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.Album
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun HomeTopBar(
    username: String,
    avatarPath: String?,
    strings: HomeStrings,
    isRefreshing: Boolean,
    accentColor: Color,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OndaWaveBadge(accentColor = accentColor)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeGreeting(strings),
                style = MaterialTheme.typography.labelMedium,
                color = OndaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = username.ifBlank { strings.listenerFallback },
                style = MaterialTheme.typography.titleMedium,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        GlassIconAction(
            onClick = onRefresh,
            contentDescription = strings.refreshDescription
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = strings.refreshDescription,
                    tint = OndaTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        GlassIconAction(
            onClick = onOpenSearch,
            contentDescription = strings.searchDescription
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = strings.searchDescription,
                tint = OndaTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        UserAvatarSettingsButton(
            avatarPath = avatarPath,
            username = username,
            contentDescription = strings.settingsDescription,
            accentColor = accentColor,
            onClick = onOpenSettings
        )
    }
}

@Composable
internal fun OndaWaveBadge(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.height(40.dp),
        elevation = GlassElevation.SUBTLE,
        shape = RoundedCornerShape(GlassTokens.RadiusPill),
        accentTint = accentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Canvas(modifier = Modifier.size(width = 22.dp, height = 16.dp)) {
                val path = Path()
                val midY = size.height * 0.5f
                val amp = size.height * 0.34f
                val steps = 16
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = size.width * t
                    val y = midY + sin(t * 2.5f * PI.toFloat()) * amp
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        colors = listOf(accentColor, GlassTokens.IceBlue)
                    ),
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            Text(
                text = "ONDA",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = OndaTheme.colors.textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GlassIconAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .size(DockMetrics.MinTapTarget)
            .smokedGlass(
                shape = shape,
                elevation = GlassElevation.SUBTLE,
                accentTint = OndaTheme.colors.accentSoft
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun UserAvatarSettingsButton(
    avatarPath: String?,
    username: String,
    contentDescription: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .size(DockMetrics.MinTapTarget)
            .smokedGlass(
                shape = CircleShape,
                elevation = GlassElevation.CARD,
                accentTint = accentColor
            )
            .clickable(onClick = onClick),
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
                    .padding(2.dp)
                    .clip(CircleShape)
            )
        } else if (username.isNotBlank()) {
            Text(
                text = username.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = contentDescription,
                tint = OndaTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun HeroTrackCard(
    track: Track,
    subtitle: String,
    palette: OndaArtworkPalette,
    strings: HomeStrings,
    isPlaying: Boolean,
    isActiveTrack: Boolean,
    dynamicMeshEnabled: Boolean,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onPlayClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heroShape = RoundedCornerShape(GlassTokens.RadiusXl)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(heroShape)
        ) {
            if (dynamicMeshEnabled) {
                AgslMeshBackground(
                    palette = palette,
                    animated = true,
                    speed = 0.85f,
                    dimAlpha = 0.44f,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    palette.primary.copy(alpha = 0.34f),
                                    GlassTokens.AmoledBlack
                                )
                            )
                        )
                )
            }
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .press3D(),
            elevation = GlassElevation.HERO,
            shape = heroShape,
            accentTint = palette.primary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val artworkModifier = Modifier
                        .size(104.dp)
                        .morphingCornerClip(
                            startRadius = GlassTokens.RadiusMd,
                            endRadius = GlassTokens.RadiusXl,
                            fractionProvider = { detailFraction }
                        )
                        .let { base ->
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    base.sharedElement(
                                        rememberSharedContentState(key = "hero_art_${track.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            } else {
                                base
                            }
                        }

                    Box(
                        modifier = artworkModifier
                            .background(GlassTokens.ObsidianSurface),
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
                                tint = palette.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                    .background(palette.primary.copy(alpha = 0.20f))
                                    .border(
                                        width = GlassTokens.BorderHairline,
                                        color = palette.primary.copy(alpha = 0.50f),
                                        shape = RoundedCornerShape(GlassTokens.RadiusPill)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = strings.continueListening,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.primary,
                                    maxLines = 1
                                )
                            }
                        }

                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = OndaTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = subtitle.ifBlank { strings.unknownArtist },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OndaTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = sourceBadgeLabel(track.source, strings),
                                style = MaterialTheme.typography.labelSmall,
                                color = OndaTheme.colors.textTertiary,
                                maxLines = 1
                            )
                            val durationText = formatDurationMs(track.durationMs)
                            if (durationText.isNotEmpty()) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OndaTheme.colors.textTertiary
                                )
                                OdometerText(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OndaTheme.colors.textTertiary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(palette.primary, palette.secondary)
                                )
                            )
                            .clickable(onClick = onPlayClick)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isActiveTrack && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = palette.onAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isActiveTrack && isPlaying) strings.pauseAction else strings.playAction,
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.onAccent,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                            .size(DockMetrics.MinTapTarget)
                            .smokedGlass(
                                shape = CircleShape,
                                elevation = GlassElevation.CARD,
                                accentTint = if (track.liked) GlassTokens.ErrorCoral else palette.primary
                            )
                            .clickable(onClick = onToggleLike),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = track.title,
                            tint = if (track.liked) GlassTokens.ErrorCoral else OndaTheme.colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfflineFallbackBanner(
    strings: HomeStrings,
    accentColor: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = GlassElevation.SUBTLE,
        shape = RoundedCornerShape(GlassTokens.RadiusMd),
        accentTint = accentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(accentColor.copy(alpha = 0.16f))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.retryAction,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String? = null,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (count != null && count > 0) {
            OdometerText(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = OndaTheme.colors.textTertiary
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
    accentColor: Color,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(GlassTokens.RadiusMd)

    GlassSurface(
        modifier = modifier
            .width(148.dp)
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick),
        elevation = GlassElevation.CARD,
        shape = cardShape,
        accentTint = if (isActive) accentColor else OndaTheme.colors.accentSoft
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val artModifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .morphingCornerClip(
                    startRadius = GlassTokens.RadiusSm,
                    endRadius = GlassTokens.RadiusLg,
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
                modifier = artModifier.background(GlassTokens.ObsidianSurface),
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
                        tint = accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Pause,
                            contentDescription = null,
                            tint = GlassTokens.AmoledBlack,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) accentColor else OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = track.artist.ifBlank { strings.unknownArtist },
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlbumSquareCard(
    album: Album,
    strings: HomeStrings,
    accentColor: Color,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    GlassSurface(
        modifier = modifier
            .width(156.dp)
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick),
        elevation = GlassElevation.CARD,
        shape = RoundedCornerShape(GlassTokens.RadiusMd),
        accentTint = accentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val artModifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .morphingCornerClip(
                    startRadius = GlassTokens.RadiusSm,
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
                modifier = artModifier.background(GlassTokens.ObsidianSurface),
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
                        tint = accentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = buildString {
                append(album.artist.ifBlank { strings.unknownArtist })
                if (album.year != null) {
                    append(" • ")
                    append(album.year)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun MoodPillCard(
    mood: MoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moodAccent = Color(mood.accentColorHex or 0xFF000000L)
    GlassSurface(
        modifier = modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick),
        elevation = GlassElevation.SUBTLE,
        shape = RoundedCornerShape(GlassTokens.RadiusMd),
        accentTint = moodAccent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(moodAccent)
            )
            Text(
                text = mood.title,
                style = MaterialTheme.typography.titleSmall,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = OndaTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun QuickPickCompactRow(
    track: Track,
    strings: HomeStrings,
    isActive: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onPlay),
        elevation = GlassElevation.SUBTLE,
        shape = RoundedCornerShape(GlassTokens.RadiusMd),
        accentTint = if (isActive) accentColor else OndaTheme.colors.accentSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(GlassTokens.RadiusSm))
                    .background(GlassTokens.ObsidianSurface),
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
                        tint = accentColor,
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
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) accentColor else OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { strings.unknownArtist },
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isActive) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Pause,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .size(DockMetrics.MinTapTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleLike),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = track.title,
                    tint = if (track.liked) GlassTokens.ErrorCoral else OndaTheme.colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

internal fun sourceBadgeLabel(source: SourceKind, strings: HomeStrings): String {
    return when (source) {
        SourceKind.LOCAL -> strings.sourceLocal
        SourceKind.MONOCHROME -> strings.sourceLossless
        SourceKind.YOUTUBE -> strings.sourceStream
    }
}

internal val ShelfHorizontalPadding = PaddingValues(horizontal = 16.dp)
