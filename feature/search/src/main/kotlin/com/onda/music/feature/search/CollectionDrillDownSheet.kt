package com.onda.music.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.dockBottomPadding
import com.onda.music.core.design.press3D
import com.onda.music.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val placeholderIcon = when (state.target) {
        is DrillDownTarget.AlbumTarget -> Icons.Rounded.Album
        else -> Icons.Rounded.QueueMusic
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassTokens.ObsidianSurface,
        contentColor = OndaTheme.colors.textPrimary,
        scrimColor = GlassTokens.SmokedGlassScrim
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkThumbnail(
                    artworkUrl = state.artworkUrl,
                    placeholderIcon = placeholderIcon,
                    size = 64.dp,
                    cornerRadius = GlassTokens.RadiusMd,
                    modifier = Modifier.press3D(maxTiltDegrees = 5f)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = OndaTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = OndaTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close collection",
                        tint = OndaTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (state.tracks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
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
                Spacer(modifier = Modifier.height(10.dp))
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = OndaTheme.colors.accent)
                    }
                }
                state.errorMessage != null -> {
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
                state.tracks.isEmpty() -> {
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
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .dockBottomPadding(hasMiniPlayer = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
        }
    }
}
