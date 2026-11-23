package com.onda.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ProgressiveGlassScrim
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.model.Track

@Composable
fun CollectionDrillDownSheet(
    state: DrillDownState,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    hasMiniPlayer: Boolean = false,
    openFraction: Float = 1f,
    launchOrigin: SearchLaunchOrigin = SearchLaunchOrigin()
) {
    val reducedMotion = OndaTheme.reducedMotion
    val lazyListState = rememberLazyListState()
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var coverBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    val collapseState = rememberSearchDetailCollapseProgress(
        lazyListState = lazyListState,
        headerHeightPx = headerHeightPx
    )
    val isAlbum = state.target is DrillDownTarget.AlbumTarget
    val placeholderIcon = if (isAlbum) Icons.Rounded.Album else Icons.Rounded.QueueMusic
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .graphicsLayer {
                val p = openFraction.coerceIn(0f, 1f)
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
                    val cornerPx = lerp(launchOrigin.cornerRadius, 0.dp, p).coerceAtLeast(0.dp).toPx()
                    shape = RoundedCornerShape(cornerPx)
                    clip = cornerPx > 0.5f
                }
                alpha = searchSmoothstep(0f, 0.14f, p)
            }
            .background(GlassTokens.AmoledBlack)
    ) {
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
                    alpha = searchSmoothstep(0.08f, 0.42f, openFraction.coerceIn(0f, 1f))
                }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .dockBottomPadding(hasMiniPlayer = hasMiniPlayer),
                contentPadding = PaddingValues(top = statusBarTop + 68.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "drill_down_header") {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
                            .graphicsLayer { alpha = collapseState.headerContentAlpha },
                        elevation = GlassElevation.SHEET,
                        shape = RoundedCornerShape(GlassTokens.RadiusLg),
                        accentTint = OndaTheme.colors.accentSoft
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkThumbnail(
                                    artworkUrl = state.artworkUrl,
                                    placeholderIcon = placeholderIcon,
                                    size = 84.dp,
                                    cornerRadius = GlassTokens.RadiusMd,
                                    modifier = Modifier.onGloballyPositioned {
                                        coverBoundsInWindow = Rect(it.positionInWindow(), it.size.toDrillSize())
                                    }
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = OndaTheme.colors.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state.subtitle.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = state.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OndaTheme.colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (state.tracks.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onPlayAll,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = OndaTheme.colors.accent,
                                            contentColor = GlassTokens.AmoledBlack
                                        ),
                                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Play All",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onShufflePlay,
                                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            tint = OndaTheme.colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Shuffle",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = OndaTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                when {
                    state.isLoading -> {
                        item(key = "drill_down_loading") {
                            SearchBonesHost(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SearchLoadingDots(dotColor = OndaTheme.colors.accent)
                                    Text(
                                        text = "Loading tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OndaTheme.colors.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                repeat(6) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        SearchBone(
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            SearchBone(
                                                modifier = Modifier
                                                    .width(190.dp)
                                                    .height(14.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            SearchBone(
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .height(12.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state.errorMessage != null -> {
                        item(key = "drill_down_error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OndaTheme.colors.textSecondary
                                )
                                Button(
                                    onClick = onRetry,
                                    shape = RoundedCornerShape(GlassTokens.RadiusPill),
                                    modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Retry")
                                }
                            }
                        }
                    }
                    state.tracks.isEmpty() -> {
                        item(key = "drill_down_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No playable tracks found in this collection.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OndaTheme.colors.textTertiary
                                )
                            }
                        }
                    }
                    else -> {
                        itemsIndexed(
                            items = state.tracks,
                            key = { index, track -> "${track.id}_$index" }
                        ) { index, track ->
                            SearchTrackRow(
                                track = track,
                                isCurrent = track.id == currentTrackId,
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(index) },
                                onToggleLike = { onToggleLike(track) }
                            )
                        }
                    }
                }
            }

            SearchDetailCollapsingTopBar(
                morphProgress = collapseState.morphProgress,
                title = state.title,
                subtitle = state.subtitle,
                artworkUrl = state.artworkUrl,
                isAlbum = isAlbum,
                hasTracks = state.tracks.isNotEmpty(),
                coverBoundsInWindow = coverBoundsInWindow,
                onClose = onDismiss,
                onPlayAll = onPlayAll,
                onShufflePlay = onShufflePlay,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

private fun IntSize.toDrillSize(): Size = Size(width.toFloat(), height.toFloat())
