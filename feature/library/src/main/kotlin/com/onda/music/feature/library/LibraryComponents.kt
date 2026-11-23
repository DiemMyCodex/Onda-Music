package com.onda.music.feature.library

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import java.util.Locale

private val LibraryTitleBlockHeight = 62.dp
private val LibraryTopInset = 12.dp

@Composable
internal fun libraryExpandedTopPadding(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + LibraryTopInset + LibraryTitleBlockHeight + 10.dp + 48.dp + 12.dp
}

@Composable
internal fun libraryCollapsedTopPadding(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + LibraryTopInset + 48.dp + 10.dp
}

@Composable
internal fun LibraryGlassTopBar(
    subtitle: String,
    collapseProgress: () -> Float,
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val titleTravelPx = with(density) { (LibraryTitleBlockHeight + 10.dp).toPx() }
    val actionsReservePx = with(density) { 108.dp.toPx() }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    onDrawBehind {
                        val p = collapseProgress().coerceIn(0f, 1f)
                        if (p > 0.01f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to GlassTokens.AmoledBlack.copy(alpha = (0.96f * p).coerceIn(0f, 1f)),
                                        0.64f to GlassTokens.AmoledBlack.copy(alpha = (0.82f * p).coerceIn(0f, 1f)),
                                        1.0f to Color.Transparent
                                    )
                                )
                            )
                        }
                    }
                }
        )

        Column(modifier = Modifier.padding(top = statusBar + LibraryTopInset)) {
            Box(
                modifier = Modifier
                    .height(LibraryTitleBlockHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        val p = collapseProgress().coerceIn(0f, 1f)
                        alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                        translationY = -p * titleTravelPx
                        val s = 1f - p * 0.16f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        if (!reducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = if (p > 0.08f) LibraryBlurCache.blur(p * 12f) else null
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Column(modifier = Modifier.padding(end = 112.dp)) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp
                        ),
                        color = OndaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OndaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -collapseProgress().coerceIn(0f, 1f) * titleTravelPx
                    }
            ) {
                Box(
                    modifier = Modifier.layout { measurable, constraints ->
                        val reserve = (actionsReservePx * collapseProgress().coerceIn(0f, 1f)).toInt()
                        val width = (constraints.maxWidth - reserve).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width)
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                ) {
                    LibraryCategoryPills(
                        selectedTab = selectedTab,
                        onSelectTab = onSelectTab
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBar + LibraryTopInset, end = 16.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val addInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .size(48.dp)
                    .libraryPressBounce(addInteraction, pressedScale = 0.90f)
                    .smokedGlass(
                        shape = CircleShape,
                        elevation = GlassElevation.SUBTLE,
                        accentTint = OndaTheme.colors.accent
                    )
                    .clickable(
                        interactionSource = addInteraction,
                        indication = LibraryHighlightIndication(cornerRadius = 24.dp),
                        onClick = onCreatePlaylist
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New playlist",
                    tint = OndaTheme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            val settingsInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .size(48.dp)
                    .libraryPressBounce(settingsInteraction, pressedScale = 0.90f)
                    .smokedGlass(
                        shape = CircleShape,
                        elevation = GlassElevation.SUBTLE,
                        accentTint = OndaTheme.colors.accentSoft
                    )
                    .clickable(
                        interactionSource = settingsInteraction,
                        indication = LibraryHighlightIndication(cornerRadius = 24.dp),
                        onClick = onOpenSettings
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = OndaTheme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
internal fun LibraryHeader(
    subtitle: String,
    onCreatePlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.displayMedium,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onCreatePlaylist,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .smokedGlass(
                    shape = CircleShape,
                    elevation = GlassElevation.SUBTLE,
                    accentTint = OndaTheme.colors.accent
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "New playlist",
                tint = OndaTheme.colors.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .smokedGlass(
                    shape = CircleShape,
                    elevation = GlassElevation.SUBTLE,
                    accentTint = OndaTheme.colors.accentSoft
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = OndaTheme.colors.textPrimary
            )
        }
    }
}

@Composable
internal fun LibraryRecapCard(
    totalPlayCount: Int,
    totalPlayDurationMs: Long,
    libraryCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hoursDisplay = remember(totalPlayDurationMs) { formatHoursListened(totalPlayDurationMs) }
    val interactionSource = remember { MutableInteractionSource() }
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .libraryPressBounce(interactionSource, pressedScale = 0.97f)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = GlassTokens.RadiusLg),
                onClick = onClick
            ),
        elevation = GlassElevation.HERO,
        shape = RoundedCornerShape(GlassTokens.RadiusLg),
        accentTint = OndaTheme.colors.accent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = OndaTheme.colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Listening Recap",
                        style = MaterialTheme.typography.titleSmall,
                        color = OndaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.labelMedium,
                    color = OndaTheme.colors.accentSoft
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecapStatCell(
                    label = "Total Plays",
                    valueText = totalPlayCount.toString(),
                    accentColor = OndaTheme.colors.accent,
                    modifier = Modifier.weight(1f)
                )
                RecapStatCell(
                    label = "Listened",
                    valueText = hoursDisplay,
                    accentColor = OndaTheme.colors.accentSoft,
                    modifier = Modifier.weight(1f)
                )
                RecapStatCell(
                    label = "Saved",
                    valueText = libraryCount.toString(),
                    accentColor = OndaTheme.colors.accentSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RecapStatCell(
    label: String,
    valueText: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .smokedGlass(
                shape = RoundedCornerShape(GlassTokens.RadiusSm),
                elevation = GlassElevation.SUBTLE,
                accentTint = accentColor
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        OdometerText(
            text = valueText,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = OndaTheme.colors.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OndaTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun LibraryCategoryPills(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(LibraryTab.entries, key = { it.name }) { tab ->
            val selected = tab == selectedTab
            val tint = if (selected) OndaTheme.colors.accent else OndaTheme.colors.accentSecondary
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .heightIn(min = 48.dp)
                    .libraryPressBounce(interactionSource, pressedScale = 0.94f)
                    .smokedGlass(
                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                        elevation = if (selected) GlassElevation.SHEET else GlassElevation.SUBTLE,
                        accentTint = tint
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LibraryHighlightIndication(cornerRadius = GlassTokens.RadiusPill),
                        onClick = { onSelectTab(tab) }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) OndaTheme.colors.textPrimary else OndaTheme.colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun QuickPlayActionRow(
    countLabel: String,
    hasTracks: Boolean,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelMedium,
            color = OndaTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onShuffleAll,
            enabled = hasTracks,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = OndaTheme.colors.textPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Shuffle", maxLines = 1)
        }
        Button(
            onClick = onPlayAll,
            enabled = hasTracks,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OndaTheme.colors.accent,
                contentColor = GlassTokens.AmoledBlack
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Play", maxLines = 1)
        }
    }
}

@Composable
internal fun PlaylistRowCard(
    playlist: Playlist,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    coverPool: List<String?> = emptyList()
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val stackCovers = remember(playlist.artworkUrl, coverPool) {
        (listOfNotNull(playlist.artworkUrl) + coverPool.filterNotNull())
            .distinct()
            .take(2)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.97f)
            .smokedGlass(
                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                elevation = GlassElevation.CARD,
                accentTint = OndaTheme.colors.accentSoft
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                onClick = onOpen
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCoverStack(
            covers = stackCovers,
            tile = 46.dp,
            showFan = playlist.trackCount > 1 || stackCovers.size > 1
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(playlist.trackCount)
                append(if (playlist.trackCount == 1) " track" else " tracks")
                if (!playlist.description.isNullOrBlank()) {
                    append("  |  ")
                    append(playlist.description)
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
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Playlist actions",
                    tint = OndaTheme.colors.textSecondary
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = GlassTokens.ObsidianSurface
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = OndaTheme.colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = OndaTheme.colors.textSecondary
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = OndaTheme.colors.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = OndaTheme.colors.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
internal fun LibraryTrackRow(
    track: Track,
    isActive: Boolean,
    downloadStatus: DownloadStatus?,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val accent = if (isActive) OndaTheme.colors.accent else OndaTheme.colors.accentSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.98f)
            .smokedGlass(
                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                elevation = if (isActive) GlassElevation.SHEET else GlassElevation.CARD,
                accentTint = accent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                onClick = onPlay
            )
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtworkBox(
            artworkUrl = track.artworkUrl,
            title = track.title,
            isActive = isActive,
            isPlaying = isPlaying
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) OndaTheme.colors.accent else OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SourceBadge(source = track.source)
                when (downloadStatus) {
                    DownloadStatus.COMPLETED -> Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = OndaTheme.colors.success,
                        modifier = Modifier.size(14.dp)
                    )
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = OndaTheme.colors.accent
                    )
                    else -> Unit
                }
                Text(
                    text = "${track.artist}  |  ${formatDurationMs(track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(
                imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (track.liked) "Unlike" else "Like",
                tint = if (track.liked) GlassTokens.ErrorCoral else OndaTheme.colors.textSecondary
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Track actions",
                    tint = OndaTheme.colors.textSecondary
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = GlassTokens.ObsidianSurface
            ) {
                DropdownMenuItem(
                    text = { Text("Add to playlist", color = OndaTheme.colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = OndaTheme.colors.accent
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
                val downloadLabel = when (downloadStatus) {
                    DownloadStatus.COMPLETED -> "Remove download"
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> "Cancel download"
                    DownloadStatus.FAILED -> "Retry download"
                    null -> "Download offline"
                }
                DropdownMenuItem(
                    text = { Text(downloadLabel, color = OndaTheme.colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (downloadStatus == DownloadStatus.COMPLETED) {
                                Icons.Rounded.DeleteOutline
                            } else {
                                Icons.Rounded.Download
                            },
                            contentDescription = null,
                            tint = if (downloadStatus == DownloadStatus.COMPLETED) {
                                OndaTheme.colors.error
                            } else {
                                OndaTheme.colors.accentSoft
                            }
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDownloadClick()
                    }
                )
            }
        }
    }
}

@Composable
internal fun TrackArtworkBox(
    artworkUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(GlassTokens.RadiusSm))
            .background(GlassTokens.IconBadgeSurface),
        contentAlignment = Alignment.Center
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = OndaTheme.colors.accentSoft,
                modifier = Modifier.size(22.dp)
            )
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(GlassTokens.AmoledBlack.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    LibraryPlayingIndicator(
                        color = OndaTheme.colors.accent,
                        isPlaying = true,
                        modifier = Modifier.height(14.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = OndaTheme.colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SourceBadge(
    source: SourceKind,
    modifier: Modifier = Modifier
) {
    val label = when (source) {
        SourceKind.LOCAL -> "LOCAL"
        SourceKind.MONOCHROME -> "FLAC"
        SourceKind.YOUTUBE -> "YTM"
    }
    val tint = when (source) {
        SourceKind.LOCAL -> OndaTheme.colors.success
        SourceKind.MONOCHROME -> OndaTheme.colors.accent
        SourceKind.YOUTUBE -> OndaTheme.colors.accentSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(GlassTokens.IconBadgeSurface)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
internal fun DownloadRowCard(
    entry: DownloadEntryUi,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onDeleteOrCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = entry.item
    val track = entry.track
    val title = track?.title ?: item.trackId
    val artist = track?.artist ?: "Saved track"
    val isActive = item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .libraryPressBounce(interactionSource, pressedScale = 0.98f)
            .smokedGlass(
                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                elevation = GlassElevation.CARD,
                accentTint = when (item.status) {
                    DownloadStatus.COMPLETED -> OndaTheme.colors.success
                    DownloadStatus.FAILED -> OndaTheme.colors.error
                    else -> OndaTheme.colors.accent
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                enabled = item.status == DownloadStatus.COMPLETED && track != null,
                onClick = onPlay
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkBox(artworkUrl = track?.artworkUrl, title = title)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusLabel = when (item.status) {
                    DownloadStatus.QUEUED -> "$artist  |  Queued"
                    DownloadStatus.DOWNLOADING -> {
                        val pct = (item.progress * 100f).toInt().coerceIn(0, 100)
                        "$artist  |  Downloading $pct%"
                    }
                    DownloadStatus.COMPLETED -> "$artist  |  ${formatBytes(item.totalBytes.coerceAtLeast(item.bytesDownloaded))}"
                    DownloadStatus.FAILED -> "$artist  |  Download failed"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == DownloadStatus.FAILED) OndaTheme.colors.error else OndaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.status == DownloadStatus.FAILED && track != null) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Retry download",
                        tint = OndaTheme.colors.accent
                    )
                }
            }
            IconButton(
                onClick = onDeleteOrCancel,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.Close else Icons.Rounded.DeleteOutline,
                    contentDescription = if (isActive) "Cancel download" else "Delete download",
                    tint = if (isActive) OndaTheme.colors.textSecondary else OndaTheme.colors.error
                )
            }
        }
        if (isActive) {
            LinearProgressIndicator(
                progress = { item.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = OndaTheme.colors.accent,
                trackColor = GlassTokens.GlassHighlight.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
internal fun LocalScannerBanner(
    hasPermission: Boolean,
    isScanning: Boolean,
    lastScanMessage: String?,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .smokedGlass(
                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                elevation = GlassElevation.CARD,
                accentTint = OndaTheme.colors.accent
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = OndaTheme.colors.accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Local Audio Scanner",
                    style = MaterialTheme.typography.titleSmall,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lastScanMessage ?: if (hasPermission) {
                        "Scan MediaStore folders for offline audio"
                    } else {
                        "Grant audio permission to discover device tracks"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = if (hasPermission) onScan else onRequestPermission,
                enabled = !isScanning,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.accent,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = GlassTokens.AmoledBlack
                    )
                } else {
                    Text(
                        text = if (hasPermission) "Scan" else "Allow",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun RelinkTracksCard(
    tracks: List<Track>,
    onRelinkTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .smokedGlass(
                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                elevation = GlassElevation.CARD,
                accentTint = OndaTheme.colors.warning
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Needs Relink (${tracks.size})",
            style = MaterialTheme.typography.titleSmall,
            color = OndaTheme.colors.warning
        )
        Text(
            text = "Imported local tracks missing a file URI. Scan local storage or pick an audio file manually.",
            style = MaterialTheme.typography.bodySmall,
            color = OndaTheme.colors.textSecondary
        )
        tracks.take(4).forEach { track ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = OndaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { onRelinkTrack(track) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Relink", maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun EmptyLibrarySection(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .smokedGlass(
                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                elevation = GlassElevation.SUBTLE
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = OndaTheme.colors.accentSoft,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OndaTheme.colors.textPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OndaTheme.colors.textSecondary
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.accent,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
internal fun PlaylistEditorDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        titleContentColor = OndaTheme.colors.textPrimary,
        textContentColor = OndaTheme.colors.textSecondary,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OndaTheme.colors.textPrimary,
                        unfocusedTextColor = OndaTheme.colors.textPrimary,
                        focusedBorderColor = OndaTheme.colors.accent,
                        unfocusedBorderColor = GlassTokens.HairlineDivider
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OndaTheme.colors.textPrimary,
                        unfocusedTextColor = OndaTheme.colors.textPrimary,
                        focusedBorderColor = OndaTheme.colors.accent,
                        unfocusedBorderColor = GlassTokens.HairlineDivider
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), description.trim().takeIf { it.isNotEmpty() }) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.accent,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = "Cancel", color = OndaTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
internal fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        titleContentColor = OndaTheme.colors.textPrimary,
        textContentColor = OndaTheme.colors.textSecondary,
        title = { Text(text = "Delete Playlist") },
        text = { Text(text = "Remove \"$playlistName\" from your library? Tracks remain in your library.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.error,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                Text(text = "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = "Cancel", color = OndaTheme.colors.textSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickPlaylistSheet(
    track: Track,
    playlists: List<Playlist>,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        contentColor = OndaTheme.colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        color = OndaTheme.colors.textPrimary
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = OndaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onCreatePlaylist,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = "New", color = OndaTheme.colors.accent)
                }
            }
            if (playlists.isEmpty()) {
                EmptyLibrarySection(
                    title = "No playlists yet",
                    subtitle = "Create a playlist to save tracks into collections."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .smokedGlass(
                                    shape = RoundedCornerShape(GlassTokens.RadiusMd),
                                    elevation = GlassElevation.CARD
                                )
                                .clickable { onSelectPlaylist(playlist) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = OndaTheme.colors.accent
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = OndaTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${playlist.trackCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = OndaTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTracksToPlaylistSheet(
    playlistName: String,
    candidateTracks: List<Track>,
    existingTrackIds: Set<String>,
    onAddTrack: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        contentColor = OndaTheme.colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Add Tracks to $playlistName",
                style = MaterialTheme.typography.titleLarge,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (candidateTracks.isEmpty()) {
                EmptyLibrarySection(
                    title = "No saved tracks found",
                    subtitle = "Like songs, scan local audio, or play tracks first."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(candidateTracks, key = { it.id }) { track ->
                        val added = track.id in existingTrackIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .smokedGlass(
                                    shape = RoundedCornerShape(GlassTokens.RadiusMd),
                                    elevation = GlassElevation.CARD
                                )
                                .clickable(enabled = !added) { onAddTrack(track) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtworkBox(artworkUrl = track.artworkUrl, title = track.title)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OndaTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OndaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { if (!added) onAddTrack(track) },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = if (added) "Added" else "Add track",
                                    tint = if (added) OndaTheme.colors.success else OndaTheme.colors.accent
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

internal fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun formatHoursListened(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0L)
    val totalMinutes = safeMs / 60_000L
    val hours = totalMinutes / 60L
    val tenths = ((safeMs % 3_600_000L) * 10L) / 3_600_000L
    return if (hours >= 10L) {
        "${hours}h"
    } else {
        "${hours}.${tenths}h"
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
