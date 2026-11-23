package com.onda.music.feature.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.press3D
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track

@Composable
internal fun SearchTopHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.displayMedium,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "YT Music, Monochrome FLAC & Local",
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
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
internal fun SmokedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        elevation = GlassElevation.CARD,
        shape = shape,
        accentTint = OndaTheme.colors.accentSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = OndaTheme.colors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Songs, albums, artists or lyrics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = OndaTheme.colors.textPrimary
                    ),
                    cursorBrush = SolidColor(OndaTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = OndaTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
internal fun SourceFilterRow(
    activeFilter: SearchSourceFilter,
    onSelectFilter: (SearchSourceFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SearchSourceFilter.entries, key = { it.name }) { filter ->
            val selected = filter == activeFilter
            val shape = RoundedCornerShape(GlassTokens.RadiusPill)
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier.background(GlassTokens.IconBadgeSurface, shape)
                        } else {
                            Modifier.smokedGlass(
                                shape = shape,
                                elevation = GlassElevation.SUBTLE
                            )
                        }
                    )
                    .clickable { onSelectFilter(filter) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) GlassTokens.TextPrimary else GlassTokens.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun LinkImportPromoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusLg)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .press3D(maxTiltDegrees = 4f)
            .clip(shape)
            .clickable(onClick = onClick),
        elevation = GlassElevation.HERO,
        shape = shape,
        accentTint = OndaTheme.colors.accent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(GlassTokens.RadiusSm))
                    .background(GlassTokens.IconBadgeSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = GlassTokens.TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Import Playlist Link",
                    style = MaterialTheme.typography.titleMedium,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Spotify, Apple Music, Deezer & YouTube Music",
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open link importer",
                tint = OndaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
internal fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Recent Searches",
            actionLabel = "Clear",
            onActionClick = onClearAll
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history, key = { it }) { entry ->
                HistoryChip(
                    query = entry,
                    onClick = { onSelect(entry) },
                    onDelete = { onDelete(entry) }
                )
            }
        }
    }
}

@Composable
private fun HistoryChip(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .smokedGlass(shape = shape, elevation = GlassElevation.SUBTLE)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = OndaTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.labelLarge,
            color = OndaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.defaultMinSize(
                minWidth = DockMetrics.MinTapTarget,
                minHeight = DockMetrics.MinTapTarget
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove $query",
                tint = OndaTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun MoodCategoryRow(
    pair: List<MoodCategory>,
    onSelect: (MoodCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        pair.forEach { category ->
            MoodCategoryCard(
                category = category,
                onClick = { onSelect(category) },
                modifier = Modifier.weight(1f)
            )
        }
        if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MoodCategoryCard(
    category: MoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = GlassTokens.ensureContrastOnDark(Color(category.accentColorHex), GlassTokens.ObsidianSurface, 3.2)
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    GlassSurface(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .press3D(maxTiltDegrees = 5f)
            .clip(shape)
            .clickable(onClick = onClick),
        elevation = GlassElevation.CARD,
        shape = shape,
        accentTint = accent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleSmall,
                color = OndaTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun MoodLoadFallbackCard(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = GlassElevation.SUBTLE,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = OndaTheme.colors.accent
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading moods and genres",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textSecondary
                    )
                }
            } else {
                Text(
                    text = errorMessage ?: "No moods available right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Retry moods",
                        tint = OndaTheme.colors.accent
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchSuggestionRow(
    suggestion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = OndaTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyMedium,
            color = OndaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun SearchTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    val durationLabel = SearchLogic.formatDuration(track.durationMs)
    val subtitle = buildString {
        if (track.artist.isNotBlank()) append(track.artist)
        if (!track.album.isNullOrBlank()) {
            if (isNotEmpty()) append(" • ")
            append(track.album)
        }
        if (durationLabel.isNotEmpty()) {
            if (isNotEmpty()) append(" • ")
            append(durationLabel)
        }
    }.ifBlank { "Track" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .then(
                if (isCurrent) {
                    Modifier.smokedGlass(
                        shape = shape,
                        elevation = GlassElevation.CARD,
                        accentTint = OndaTheme.colors.accent
                    )
                } else {
                    Modifier.background(Color.Transparent, shape)
                }
            )
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkThumbnail(
            artworkUrl = track.artworkUrl,
            placeholderIcon = Icons.Rounded.MusicNote,
            size = 48.dp,
            cornerRadius = GlassTokens.RadiusSm,
            isActive = isCurrent && isPlaying
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) OndaTheme.colors.accent else OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.source != SourceKind.YOUTUBE) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SourceTagBadge(source = track.source)
                }
            }
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
            onClick = onToggleLike,
            modifier = Modifier.defaultMinSize(
                minWidth = DockMetrics.MinTapTarget,
                minHeight = DockMetrics.MinTapTarget
            )
        ) {
            Icon(
                imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (track.liked) "Unlike" else "Like",
                tint = if (track.liked) GlassTokens.ErrorCoral else OndaTheme.colors.textTertiary
            )
        }
    }
}

@Composable
internal fun SourceTagBadge(source: SourceKind) {
    val (label, tint) = when (source) {
        SourceKind.MONOCHROME -> "FLAC" to GlassTokens.TextPrimary
        SourceKind.LOCAL -> "LOCAL" to GlassTokens.SuccessMint
        SourceKind.YOUTUBE -> "YT" to GlassTokens.TextSecondary
    }
    val shape = RoundedCornerShape(GlassTokens.RadiusXs)
    Row(
        modifier = Modifier
            .background(GlassTokens.IconBadgeSurface, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (source == SourceKind.LOCAL) Icons.Rounded.Smartphone else Icons.Rounded.HighQuality,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
internal fun SearchAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    GlassSurface(
        modifier = modifier
            .width(148.dp)
            .press3D(maxTiltDegrees = 5f)
            .clip(shape)
            .clickable(onClick = onClick),
        elevation = GlassElevation.CARD,
        shape = shape
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            ArtworkThumbnail(
                artworkUrl = album.artworkUrl,
                placeholderIcon = Icons.Rounded.Album,
                size = 128.dp,
                cornerRadius = GlassTokens.RadiusSm
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist.ifBlank { "Album" },
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SearchPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .press3D(maxTiltDegrees = 4f)
            .clip(shape)
            .clickable(onClick = onClick),
        elevation = GlassElevation.CARD,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 68.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkThumbnail(
                artworkUrl = playlist.artworkUrl,
                placeholderIcon = Icons.Rounded.QueueMusic,
                size = 48.dp,
                cornerRadius = GlassTokens.RadiusSm
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playlist.trackCount > 0) "${playlist.trackCount} tracks" else "YouTube Music Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Inspect playlist",
                tint = OndaTheme.colors.textTertiary
            )
        }
    }
}

@Composable
internal fun SearchArtistChip(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    GlassSurface(
        modifier = modifier
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clip(shape)
            .clickable(onClick = onClick),
        elevation = GlassElevation.SUBTLE,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkThumbnail(
                artworkUrl = artist.thumbnailUrl,
                placeholderIcon = Icons.Rounded.Person,
                size = 36.dp,
                cornerRadius = GlassTokens.RadiusPill
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.labelLarge,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ArtworkThumbnail(
    artworkUrl: String?,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(GlassTokens.IconBadgeSurface, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = placeholderIcon,
            contentDescription = null,
            tint = OndaTheme.colors.textTertiary,
            modifier = Modifier.size((size * 0.45f).coerceAtLeast(18.dp))
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlassTokens.AmoledBlack.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Playing",
                    tint = OndaTheme.colors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
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
                style = MaterialTheme.typography.titleMedium,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = OndaTheme.colors.accent
                )
            }
        }
    }
}
