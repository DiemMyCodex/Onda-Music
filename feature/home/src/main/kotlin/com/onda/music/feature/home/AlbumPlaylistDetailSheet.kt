package com.onda.music.feature.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.onda.music.core.design.ProgressiveGlassScrim
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.design.morphingCornerClip
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.Track

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlbumPlaylistDetailSheet(
    detail: CollectionDetailState,
    strings: HomeStrings,
    palette: OndaArtworkPalette,
    activeTrackId: String?,
    hasMiniPlayer: Boolean,
    dynamicMeshEnabled: Boolean,
    detailFraction: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTokens.AmoledBlack.copy(alpha = 0.94f))
            .morphingCornerClip(
                startRadius = GlassTokens.RadiusXl,
                endRadius = 0.dp,
                fractionProvider = { detailFraction }
            )
    ) {
        if (dynamicMeshEnabled) {
            AgslMeshBackground(
                palette = palette,
                animated = true,
                speed = 0.7f,
                dimAlpha = 0.65f,
                modifier = Modifier.fillMaxSize()
            )
        }

        ProgressiveGlassScrim(
            topToBottom = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                        .size(DockMetrics.MinTapTarget)
                        .smokedGlass(
                            shape = CircleShape,
                            elevation = GlassElevation.CARD,
                            accentTint = palette.primary
                        )
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.closeDescription,
                        tint = OndaTheme.colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (detail.tracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(palette.primary, palette.secondary)
                                )
                            )
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = palette.onAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = strings.playAllAction,
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.onAccent,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .dockBottomPadding(hasMiniPlayer = hasMiniPlayer),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "detail_header") {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        elevation = GlassElevation.SHEET,
                        shape = RoundedCornerShape(GlassTokens.RadiusLg),
                        accentTint = palette.primary
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val artKey = if (detail.isAlbum) {
                                "album_art_${detail.sourceId}"
                            } else {
                                "playlist_art_${detail.sourceId}"
                            }
                            val artModifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(GlassTokens.RadiusMd))
                                .let { base ->
                                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                        with(sharedTransitionScope) {
                                            base.sharedElement(
                                                rememberSharedContentState(key = artKey),
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
                                if (!detail.artworkUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(detail.artworkUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = detail.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (detail.isAlbum) Icons.Rounded.Album else Icons.Rounded.QueueMusic,
                                        contentDescription = detail.title,
                                        tint = palette.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = detail.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = OndaTheme.colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (detail.subtitle.isNotBlank()) {
                                    Text(
                                        text = detail.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OndaTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (detail.tracks.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OdometerText(
                                            text = detail.tracks.size.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.primary
                                        )
                                        Text(
                                            text = strings.tracksSuffix,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = OndaTheme.colors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (detail.isLoading) {
                    item(key = "detail_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = palette.primary)
                        }
                    }
                } else if (!detail.errorMessage.isNullOrBlank() && detail.tracks.isEmpty()) {
                    item(key = "detail_error") {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            elevation = GlassElevation.CARD,
                            accentTint = GlassTokens.ErrorCoral
                        ) {
                            Text(
                                text = detail.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OndaTheme.colors.textSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else if (detail.tracks.isEmpty()) {
                    item(key = "detail_empty") {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            elevation = GlassElevation.SUBTLE
                        ) {
                            Text(
                                text = strings.emptyDetail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OndaTheme.colors.textSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = detail.tracks,
                        key = { index, track -> "${track.id}_$index" }
                    ) { index, track ->
                        DetailTrackRow(
                            index = index + 1,
                            track = track,
                            strings = strings,
                            isActive = track.id == activeTrackId,
                            accentColor = palette.primary,
                            onClick = { onPlayTrack(track) },
                            onToggleLike = { onToggleLike(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTrackRow(
    index: Int,
    track: Track,
    strings: HomeStrings,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onToggleLike: () -> Unit
) {
    val context = LocalContext.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick),
        elevation = GlassElevation.SUBTLE,
        shape = RoundedCornerShape(GlassTokens.RadiusMd),
        accentTint = if (isActive) accentColor else OndaTheme.colors.accentSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = OndaTheme.colors.textTertiary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(GlassTokens.RadiusXs))
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

            val durationText = formatDurationMs(track.durationMs)
            if (durationText.isNotEmpty()) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = OndaTheme.colors.textTertiary,
                    maxLines = 1
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
