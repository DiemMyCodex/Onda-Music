package com.onda.music.feature.player

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.model.PlayerStyle
import com.onda.music.core.model.Track
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class KeyedQueueTrack(
    val key: String,
    val track: Track
)

private fun buildKeyedQueue(tracks: List<Track>): List<KeyedQueueTrack> {
    val counts = HashMap<String, Int>(tracks.size)
    return tracks.map { track ->
        val next = (counts[track.id] ?: 0) + 1
        counts[track.id] = next
        KeyedQueueTrack(key = "${track.id}#$next", track = track)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    hapticsEnabled: Boolean,
    onSelectIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localItems by remember { mutableStateOf(buildKeyedQueue(queue)) }
    var isDraggingAny by remember { mutableStateOf(false) }

    LaunchedEffect(queue, isDraggingAny) {
        if (!isDraggingAny) {
            localItems = buildKeyedQueue(queue)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localItems = localItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onMoveItem(from.index, to.index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Column {
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "${localItems.size} tracks in queue",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E)
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
                        contentDescription = "Close queue",
                        tint = Color(0xFF9E9E9E)
                    )
                }
            }

            if (localItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9E9E9E)
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(
                        items = localItems,
                        key = { _, item -> item.key }
                    ) { index, item ->
                        val isCurrent = index == currentIndex
                        ReorderableItem(
                            state = reorderableState,
                            key = item.key
                        ) { isDragging ->
                            isDraggingAny = isDragging
                            val shape = RoundedCornerShape(GlassTokens.RadiusMd)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 60.dp)
                                    .clip(shape)
                                    .background(if (isDragging || isCurrent) Color(0xFF242424) else Color(0xFF1C1C1C))
                                    .clickable {
                                        onSelectIndex(index)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .defaultMinSize(
                                            minWidth = DockMetrics.MinTapTarget,
                                            minHeight = DockMetrics.MinTapTarget
                                        )
                                        .draggableHandle(
                                            onDragStarted = {
                                                isDraggingAny = true
                                                if (hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            },
                                            onDragStopped = {
                                                isDraggingAny = false
                                            }
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Reorder track",
                                        tint = Color(0xFF8A8A8A)
                                    )
                                }

                                TrackArtworkImage(
                                    artworkUrl = item.track.artworkUrl,
                                    contentDescription = item.track.title,
                                    shape = RoundedCornerShape(GlassTokens.RadiusXs),
                                    modifier = Modifier.size(44.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.track.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9E9E9E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = "Playing",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp)
                                            .size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        localItems = localItems.toMutableList().apply { removeAt(index) }
                                        onRemoveItem(index)
                                    },
                                    modifier = Modifier.defaultMinSize(
                                        minWidth = DockMetrics.MinTapTarget,
                                        minHeight = DockMetrics.MinTapTarget
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove from queue",
                                        tint = Color(0xFF8A8A8A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    onSelectDurationMs: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presets = remember {
        listOf(
            "15 min" to 15L * 60_000L,
            "30 min" to 30L * 60_000L,
            "45 min" to 45L * 60_000L,
            "60 min" to 60L * 60_000L,
            "90 min" to 90L * 60_000L
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
                if (remainingMs != null && remainingMs > 0L) {
                    OdometerText(
                        text = formatDurationMs(remainingMs),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            presets.forEach { (label, durationMs) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .clip(RoundedCornerShape(GlassTokens.RadiusMd))
                        .background(Color(0xFF1C1C1C))
                        .clickable {
                            onSelectDurationMs(durationMs)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (remainingMs != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        .clip(RoundedCornerShape(GlassTokens.RadiusMd))
                        .background(Color(0xFF242424))
                        .clickable {
                            onCancelTimer()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Cancel Sleep Timer",
                        style = MaterialTheme.typography.labelLarge,
                        color = GlassTokens.ErrorCoral
                    )
                }
            }
        }
    }
}

private fun playerStyleIcon(style: PlayerStyle): ImageVector {
    return when (style) {
        PlayerStyle.FLAGSHIP -> Icons.Rounded.Style
        PlayerStyle.CLASSIC -> Icons.Rounded.MusicNote
        PlayerStyle.POSTER -> Icons.Rounded.ViewCarousel
        PlayerStyle.TURNTABLE -> Icons.Rounded.Album
        PlayerStyle.VIDEO -> Icons.Rounded.PlayCircle
        PlayerStyle.IPOD -> Icons.Rounded.Radio
        PlayerStyle.TIMELINE -> Icons.Rounded.Timeline
        PlayerStyle.LYRICS -> Icons.Rounded.Lyrics
        PlayerStyle.BEDSIDE -> Icons.Rounded.Schedule
        PlayerStyle.BEDSIDE_TILES -> Icons.Rounded.GridView
    }
}

private fun playerStyleSubtitle(style: PlayerStyle): String {
    return when (style) {
        PlayerStyle.FLAGSHIP -> "Matte stage & carousel"
        PlayerStyle.CLASSIC -> "Balanced centered cover"
        PlayerStyle.POSTER -> "Curved-arc full-bleed lyrics"
        PlayerStyle.TURNTABLE -> "Vinyl disc & tonearm"
        PlayerStyle.VIDEO -> "Media3 video & lyrics"
        PlayerStyle.IPOD -> "Rotary wheel & CoverFlow"
        PlayerStyle.TIMELINE -> "Live queue rail stage"
        PlayerStyle.LYRICS -> "Full-stage karaoke"
        PlayerStyle.BEDSIDE -> "Minimalist night clock"
        PlayerStyle.BEDSIDE_TILES -> "Split clock & track tiles"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStyleSelectorSheet(
    activeStyle: PlayerStyle,
    onSelectStyle: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val styles = remember { PlayerStyle.entries }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Player Style",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "10 native 120fps stages",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E)
                    )
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
                        contentDescription = "Close style picker",
                        tint = Color(0xFF9E9E9E)
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(styles, key = { it.name }) { style ->
                    val selected = style == activeStyle
                    val cardShape = RoundedCornerShape(GlassTokens.RadiusMd)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 88.dp)
                            .clip(cardShape)
                            .background(if (selected) Color(0xFF242424) else Color(0xFF1C1C1C))
                            .clickable {
                                onSelectStyle(style)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = playerStyleIcon(style),
                                contentDescription = null,
                                tint = if (selected) Color.White else Color(0xFF9E9E9E),
                                modifier = Modifier.size(22.dp)
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Active",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Canvas(modifier = Modifier.size(8.dp)) {
                                    drawCircle(color = Color(0xFF2E2E2E))
                                }
                            }
                        }
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = playerStyleSubtitle(style),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
