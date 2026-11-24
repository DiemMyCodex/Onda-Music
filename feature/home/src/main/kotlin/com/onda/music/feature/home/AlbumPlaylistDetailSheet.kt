package com.onda.music.feature.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.AgslMeshBackground
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ProgressiveGlassScrim
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.model.Track

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlbumPlaylistDetailSheet(
    detail: CollectionDetailState,
    strings: HomeStrings,
    palette: OndaArtworkPalette,
    activeTrackId: String?,
    isPlaying: Boolean,
    hasMiniPlayer: Boolean,
    dynamicMeshEnabled: Boolean,
    glassBlurEnabled: Boolean,
    detailFractionProvider: () -> Float,
    launchOrigin: CardLaunchOrigin,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = OndaTheme.reducedMotion
    val lazyListState = rememberLazyListState()
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var coverBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    val collapseState = rememberHomeDetailCollapseProgress(
        lazyListState = lazyListState,
        headerHeightPx = headerHeightPx
    )
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .graphicsLayer {
                val p = detailFractionProvider().coerceIn(0f, 1f)
                val origin = launchOrigin.bounds
                if (!reducedMotion && origin != null && rootSize.width > 0 && rootSize.height > 0) {
                    val fw = rootSize.width.toFloat()
                    val fh = rootSize.height.toFloat()
                    val startScaleX = (origin.width / fw).coerceIn(0.14f, 1f)
                    val startScaleY = (origin.height / fh).coerceIn(0.14f, 1f)
                    scaleX = lerp(startScaleX, 1f, p)
                    scaleY = lerp(startScaleY, 1f, p)
                    translationX = lerp(origin.center.x - fw * 0.5f, 0f, p)
                    translationY = lerp(origin.center.y - fh * 0.5f, 0f, p)
                    transformOrigin = TransformOrigin.Center
                    val radiusDp = AppMotion.morphCornerRadius(launchOrigin.cornerRadius, 0.dp, p)
                    shape = AppMotion.morphCornerShape(launchOrigin.cornerRadius, 0.dp, p)
                    clip = radiusDp.value > 0.25f
                }
                alpha = homeSmoothstep(0f, 0.14f, p)
            }
            .background(GlassTokens.AmoledBlack.copy(alpha = 0.96f))
    ) {
        if (dynamicMeshEnabled) {
            AgslMeshBackground(
                palette = palette,
                animated = !reducedMotion,
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = homeSmoothstep(0.08f, 0.42f, detailFractionProvider().coerceIn(0f, 1f))
                }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .dockBottomPadding(hasMiniPlayer = hasMiniPlayer),
                contentPadding = PaddingValues(top = statusBarTop + 68.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "detail_header") {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
                            .graphicsLayer {
                                alpha = collapseState.headerContentAlpha
                            },
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
                                .size(96.dp)
                                .onGloballyPositioned {
                                    coverBoundsInWindow = Rect(it.positionInWindow(), it.size.toDetailSize())
                                }
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
                                    fontWeight = FontWeight.Bold,
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
                        HomeBonesHost(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            repeat(6) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HomeBone(width = 44.dp, height = 44.dp, shape = RoundedCornerShape(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        HomeBone(width = 188.dp, height = 14.dp, shape = RoundedCornerShape(6.dp))
                                        HomeBone(width = 122.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                                    }
                                }
                            }
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
                            isPlaying = isPlaying,
                            accentColor = palette.primary,
                            onClick = { onPlayTrack(track) },
                            onToggleLike = { onToggleLike(track) }
                        )
                    }
                }
            }

            HomeDetailCollapsingTopBar(
                morphProgress = collapseState.morphProgress,
                title = detail.title,
                subtitle = detail.subtitle,
                artworkUrl = detail.artworkUrl,
                isAlbum = detail.isAlbum,
                palette = palette,
                closeDescription = strings.closeDescription,
                playAllLabel = strings.playAllAction,
                hasTracks = detail.tracks.isNotEmpty(),
                glassBlurEnabled = glassBlurEnabled,
                coverBoundsInWindow = coverBoundsInWindow,
                onClose = onClose,
                onPlayAll = onPlayAll,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun DetailTrackRow(
    index: Int,
    track: Track,
    strings: HomeStrings,
    isActive: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onToggleLike: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val likeInteraction = remember { MutableInteractionSource() }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .homePressBounce(interactionSource, pressedScale = 0.98f)
            .clickable(
                interactionSource = interactionSource,
                indication = HomeHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                onClick = onClick
            ),
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
                if (isActive && isPlaying) {
                    HomePlayingIndicator(
                        color = accentColor,
                        isPlaying = true,
                        barWidth = 2.2.dp,
                        spacing = 1.5.dp,
                        modifier = Modifier.height(14.dp)
                    )
                } else if (isActive) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
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
                    tint = if (track.liked) GlassTokens.ErrorCoral else OndaTheme.colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun IntSize.toDetailSize(): Size = Size(width.toFloat(), height.toFloat())
