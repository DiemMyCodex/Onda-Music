package com.onda.music.feature.player.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.LocalOndaToastState
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.RepeatMode
import com.onda.music.core.model.Track
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.formatDurationMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

private val SheetBackdropColor = Color(0xFF0D0D0F)
private val QueueRowSurface = Color(0xFF141414)
private val QueueElevatedSurface = Color(0xFF1A1A1E)
private val QueueHairlineBorder = Color(0x1AFFFFFF)
private val QueueActiveBorder = Color(0x38FFFFFF)
private val SwipePlayNextColor = Color(0xFF4ADE80)
private val SwipeRemoveColor = Color(0xFFFF6B7A)
private val SwipeInkColor = Color(0xFF0D0D0F)

private data class KeyedQueueEntry(
    val key: String,
    val track: Track,
    val originalIndex: Int
)

private fun buildKeyedQueueEntries(tracks: List<Track>): List<KeyedQueueEntry> {
    val occurrences = HashMap<String, Int>(tracks.size)
    return tracks.mapIndexed { index, track ->
        val count = (occurrences[track.id] ?: 0) + 1
        occurrences[track.id] = count
        KeyedQueueEntry(
            key = "${track.id}#$count",
            track = track,
            originalIndex = index
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaQueueOverlay(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    activeStreamLabel: String?,
    hapticsEnabled: Boolean,
    onSelectIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onClearUpNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenSongMenu: (Track, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val toastState = LocalOndaToastState.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val safeCurrentIndex = remember(queue.size, currentIndex) {
        if (queue.isEmpty()) -1 else currentIndex.coerceIn(-1, queue.lastIndex)
    }
    val keyedAll = remember(queue) { buildKeyedQueueEntries(queue) }
    val historyRows = remember(keyedAll, safeCurrentIndex) {
        if (safeCurrentIndex > 0) keyedAll.subList(0, safeCurrentIndex) else emptyList()
    }
    val currentRow = remember(keyedAll, safeCurrentIndex) {
        if (safeCurrentIndex in keyedAll.indices) keyedAll[safeCurrentIndex] else null
    }
    val upNextBaseIndex = remember(safeCurrentIndex) { safeCurrentIndex + 1 }
    val upNextRows = remember(keyedAll, upNextBaseIndex) {
        if (upNextBaseIndex < keyedAll.size) keyedAll.subList(upNextBaseIndex, keyedAll.size) else emptyList()
    }

    var localUpNext by remember { mutableStateOf(upNextRows) }
    var isDraggingUpNext by remember { mutableStateOf(false) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var pendingDragStartIndex by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(upNextRows) {
        if (!isDraggingUpNext) {
            localUpNext = upNextRows
        }
    }

    val lazyListState = rememberLazyListState()
    val currentQueueSize by rememberUpdatedState(queue.size)
    val currentUpNextBase by rememberUpdatedState(upNextBaseIndex)
    val currentOnMoveItem by rememberUpdatedState(onMoveItem)

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = localUpNext.indexOfFirst { it.key == from.key }
        val toIdx = localUpNext.indexOfFirst { it.key == to.key }
        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
            localUpNext = localUpNext.toMutableList().apply {
                add(toIdx, removeAt(fromIdx))
            }
            if (hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val canClearUpNext = currentIndex >= 0 && currentIndex < queue.lastIndex

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackdropColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(SmoothCornerShape(4.dp))
                    .background(Color(0xFF2E2E2E))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            OndaQueueHeader(
                trackCount = queue.size,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                canClearUpNext = canClearUpNext,
                hapticsEnabled = hapticsEnabled,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onClearUpNext = {
                    onClearUpNext()
                    toastState?.show(title = "Up Next cleared")
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (queue.isEmpty()) {
                OndaQueueEmptyState()
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (historyRows.isNotEmpty()) {
                        item(key = "section_history_toggle") {
                            OndaHistoryTogglePill(
                                historyCount = historyRows.size,
                                expanded = showHistory,
                                hapticsEnabled = hapticsEnabled,
                                onToggle = { showHistory = !showHistory }
                            )
                        }

                        if (showHistory) {
                            itemsIndexed(
                                items = historyRows,
                                key = { _, item -> "history_${item.key}" }
                            ) { _, item ->
                                OndaHistoryTrackRow(
                                    track = item.track,
                                    hapticsEnabled = hapticsEnabled,
                                    onSelect = { onSelectIndex(item.originalIndex) },
                                    onOpenMenu = { onOpenSongMenu(item.track, item.originalIndex) }
                                )
                            }
                        }
                    }

                    if (currentRow != null) {
                        item(key = "section_now_playing_label") {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = GlassTokens.TextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        item(key = "now_playing_${currentRow.key}") {
                            OndaNowPlayingCard(
                                track = currentRow.track,
                                isPlaying = isPlaying,
                                activeStreamLabel = activeStreamLabel,
                                onSelect = { onSelectIndex(safeCurrentIndex) },
                                onOpenMenu = { onOpenSongMenu(currentRow.track, safeCurrentIndex) }
                            )
                        }
                    }

                    item(key = "section_up_next_label") {
                        OndaUpNextSectionLabel(count = localUpNext.size)
                    }

                    if (localUpNext.isEmpty()) {
                        item(key = "empty_up_next") {
                            OndaUpNextEmptyCard()
                        }
                    } else {
                        itemsIndexed(
                            items = localUpNext,
                            key = { _, item -> item.key }
                        ) { localIndex, item ->
                            val origIndex = upNextBaseIndex + localIndex
                            ReorderableItem(
                                state = reorderableState,
                                key = item.key
                            ) { isDragging ->
                                val dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        isDraggingUpNext = true
                                        draggingKey = item.key
                                        val startIdx = localUpNext.indexOfFirst { it.key == item.key }
                                        pendingDragStartIndex = if (startIdx != -1) startIdx else localIndex
                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDragStopped = {
                                        val activeKey = draggingKey
                                        val startIdx = pendingDragStartIndex
                                        val endIdx = if (activeKey != null) {
                                            localUpNext.indexOfFirst { it.key == activeKey }
                                        } else {
                                            -1
                                        }
                                        if (startIdx != null && endIdx != -1) {
                                            val origFrom = currentUpNextBase + startIdx
                                            val origTo = currentUpNextBase + endIdx
                                            if (
                                                origFrom != origTo &&
                                                origFrom in 0 until currentQueueSize &&
                                                origTo in 0 until currentQueueSize
                                            ) {
                                                currentOnMoveItem(origFrom, origTo)
                                            }
                                        }
                                        isDraggingUpNext = false
                                        draggingKey = null
                                        pendingDragStartIndex = null
                                    }
                                )

                                OndaQueueSwipeRow(
                                    item = item,
                                    origIndex = origIndex,
                                    safeCurrentIndex = safeCurrentIndex,
                                    queueLastIndex = queue.lastIndex,
                                    isDragging = isDragging,
                                    reducedMotion = reducedMotion,
                                    hapticsEnabled = hapticsEnabled,
                                    dragHandleModifier = dragHandleModifier,
                                    onSelect = { onSelectIndex(origIndex) },
                                    onSwipePlayNext = {
                                        val targetIndex = (safeCurrentIndex + 1).coerceIn(0, queue.lastIndex)
                                        if (origIndex !in queue.indices) {
                                            onPlayNext(item.track)
                                        } else if (origIndex > targetIndex) {
                                            localUpNext = localUpNext.toMutableList().apply {
                                                val currentLocalIdx = indexOfFirst { it.key == item.key }
                                                if (currentLocalIdx > 0) {
                                                    add(0, removeAt(currentLocalIdx))
                                                }
                                            }
                                            onMoveItem(origIndex, targetIndex)
                                        }
                                        toastState?.show(
                                            title = "Playing next",
                                            subtitle = item.track.title,
                                            artworkUrl = item.track.artworkUrl
                                        )
                                    },
                                    onSwipeRemove = {
                                        localUpNext = localUpNext.filterNot { it.key == item.key }
                                        onRemoveItem(origIndex)
                                        toastState?.show(
                                            title = "Removed from queue",
                                            subtitle = item.track.title,
                                            artworkUrl = item.track.artworkUrl
                                        )
                                    },
                                    onOpenMenu = { onOpenSongMenu(item.track, origIndex) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OndaQueueHeader(
    trackCount: Int,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    canClearUpNext: Boolean,
    hapticsEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onClearUpNext: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val countChipShape = remember { SmoothCornerShape(10.dp) }
    val controlShape = remember { SmoothCornerShape(14.dp) }
    val clearInteraction = remember { MutableInteractionSource() }
    val clearScaleEnabled = canClearUpNext && !reducedMotion

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .clip(countChipShape)
                    .background(QueueElevatedSurface, countChipShape)
                    .border(1.dp, QueueHairlineBorder, countChipShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                OdometerText(
                    text = trackCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QueueModeIconPill(
                icon = Icons.Rounded.Shuffle,
                contentDescription = "Toggle shuffle",
                active = shuffleEnabled,
                hapticsEnabled = hapticsEnabled,
                onClick = onToggleShuffle
            )

            QueueModeIconPill(
                icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Cycle repeat mode",
                active = repeatMode != RepeatMode.OFF,
                hapticsEnabled = hapticsEnabled,
                onClick = onToggleRepeat
            )

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .pressBounce(
                        interactionSource = clearInteraction,
                        pressedScale = 0.94f,
                        enabled = clearScaleEnabled
                    )
                    .clip(controlShape)
                    .background(QueueRowSurface, controlShape)
                    .border(1.dp, QueueHairlineBorder, controlShape)
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = RoundedHighlight(cornerRadius = 14.dp),
                        enabled = canClearUpNext,
                        role = Role.Button,
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onClearUpNext()
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (canClearUpNext) Color.White else GlassTokens.TextTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QueueModeIconPill(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    hapticsEnabled: Boolean,
    onClick: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(14.dp) }

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .pressBounce(
                interactionSource = interactionSource,
                pressedScale = 0.90f,
                enabled = !reducedMotion
            )
            .clip(shape)
            .background(if (active) QueueElevatedSurface else QueueRowSurface, shape)
            .border(1.dp, if (active) QueueActiveBorder else QueueHairlineBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 14.dp),
                role = Role.Button,
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color.White else GlassTokens.TextSecondary,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun OndaHistoryTogglePill(
    historyCount: Int,
    expanded: Boolean,
    hapticsEnabled: Boolean,
    onToggle: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(14.dp) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .pressBounce(
                interactionSource = interactionSource,
                pressedScale = 0.98f,
                enabled = !reducedMotion
            )
            .clip(shape)
            .background(QueueRowSurface, shape)
            .border(1.dp, QueueHairlineBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 14.dp),
                role = Role.Button,
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onToggle()
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Played Earlier ($historyCount)",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.SemiBold
            ),
            color = GlassTokens.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) "Hide played earlier" else "Show played earlier",
            tint = GlassTokens.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun OndaHistoryTrackRow(
    track: Track,
    hapticsEnabled: Boolean,
    onSelect: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val rowShape = remember { SmoothCornerShape(16.dp) }
    val thumbShape = remember { SmoothCornerShape(10.dp) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .alpha(0.76f)
            .clip(rowShape)
            .background(QueueRowSurface, rowShape)
            .border(1.dp, QueueHairlineBorder, rowShape)
            .clickable {
                if (hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onSelect()
            }
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtworkImage(
            artworkUrl = track.artworkUrl,
            contentDescription = track.title,
            shape = thumbShape,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (track.durationMs > 0L) {
            Text(
                text = formatDurationMs(track.durationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                ),
                color = GlassTokens.TextTertiary,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.defaultMinSize(
                minWidth = DockMetrics.MinTapTarget,
                minHeight = DockMetrics.MinTapTarget
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Track options",
                tint = GlassTokens.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun OndaNowPlayingCard(
    track: Track,
    isPlaying: Boolean,
    activeStreamLabel: String?,
    onSelect: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val cardShape = remember { SmoothCornerShape(18.dp) }
    val thumbShape = remember { SmoothCornerShape(12.dp) }
    val chipShape = remember { SmoothCornerShape(8.dp) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .clip(cardShape)
            .background(QueueElevatedSurface, cardShape)
            .border(1.dp, QueueActiveBorder, cardShape)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtworkImage(
            artworkUrl = track.artworkUrl,
            contentDescription = track.title,
            shape = thumbShape,
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, QueueHairlineBorder, thumbShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!activeStreamLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(QueueRowSurface, chipShape)
                            .border(1.dp, QueueHairlineBorder, chipShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = activeStreamLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        NowPlayingEqualizerWave(
            isPlaying = isPlaying,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.defaultMinSize(
                minWidth = DockMetrics.MinTapTarget,
                minHeight = DockMetrics.MinTapTarget
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Now playing options",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NowPlayingEqualizerWave(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                    phase = (phase + dt * 6.4f) % (2f * PI.toFloat() * 100f)
                }
                lastNanos = frameNanos
            }
        }
    }

    Canvas(modifier = modifier.size(width = 20.dp, height = 16.dp)) {
        val barCount = 4
        val barWidth = 2.5.dp.toPx()
        val gap = (size.width - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)
        val radius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
        val staticHeights = floatArrayOf(0.45f, 0.80f, 0.60f, 0.36f)
        val speeds = floatArrayOf(1.00f, 1.37f, 0.83f, 1.19f)
        val offsets = floatArrayOf(0.1f, 1.4f, 2.8f, 4.0f)

        for (index in 0 until barCount) {
            val fraction = if (isPlaying && !reducedMotion) {
                0.24f + 0.72f * (0.5f + 0.5f * sin(phase * speeds[index] + offsets[index]))
            } else {
                staticHeights[index]
            }
            val barHeight = (size.height * fraction).coerceAtLeast(barWidth)
            val left = index * (barWidth + gap)
            val top = (size.height - barHeight) * 0.5f
            drawRoundRect(
                color = if (isPlaying) Color.White else GlassTokens.TextSecondary,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
        }
    }
}

@Composable
private fun OndaUpNextSectionLabel(count: Int) {
    val chipShape = remember { SmoothCornerShape(8.dp) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "UP NEXT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            ),
            color = GlassTokens.TextSecondary
        )
        Box(
            modifier = Modifier
                .clip(chipShape)
                .background(QueueRowSurface, chipShape)
                .border(1.dp, QueueHairlineBorder, chipShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OndaQueueSwipeRow(
    item: KeyedQueueEntry,
    origIndex: Int,
    safeCurrentIndex: Int,
    queueLastIndex: Int,
    isDragging: Boolean,
    reducedMotion: Boolean,
    hapticsEnabled: Boolean,
    dragHandleModifier: Modifier,
    onSelect: () -> Unit,
    onSwipePlayNext: () -> Unit,
    onSwipeRemove: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val thresholdPx = remember(density) { with(density) { 92.dp.toPx() } }
    val offsetX = remember(item.key) { Animatable(0f) }
    var rawDragX by remember(item.key) { mutableFloatStateOf(0f) }
    var armedDirection by remember(item.key) { mutableIntStateOf(0) }
    var springJob by remember(item.key) { mutableStateOf<Job?>(null) }

    val currentOrigIndex by rememberUpdatedState(origIndex)
    val currentSafeIndex by rememberUpdatedState(safeCurrentIndex)
    val currentLastIndex by rememberUpdatedState(queueLastIndex)
    val currentOnPlayNext by rememberUpdatedState(onSwipePlayNext)
    val currentOnRemove by rememberUpdatedState(onSwipeRemove)

    val rowShape = remember { SmoothCornerShape(18.dp) }
    val thumbShape = remember { SmoothCornerShape(10.dp) }
    val returnSpring = remember { spring<Float>(dampingRatio = 0.68f, stiffness = 400f) }

    LaunchedEffect(isDragging) {
        if (isDragging && offsetX.value != 0f) {
            springJob?.cancel()
            armedDirection = 0
            offsetX.snapTo(0f)
        }
    }

    val currentOffset = offsetX.value
    val rowSurface = if (isDragging) QueueElevatedSurface else QueueRowSurface
    val rowBorder = if (isDragging) QueueActiveBorder else QueueHairlineBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .pointerInput(item.key, isDragging, reducedMotion, hapticsEnabled) {
                if (isDragging) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        springJob?.cancel()
                        val existing = offsetX.value
                        rawDragX = if (abs(existing) <= thresholdPx) {
                            existing
                        } else {
                            sign(existing) * (thresholdPx + (abs(existing) - thresholdPx) / 0.35f)
                        }
                        armedDirection = 0
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        rawDragX += dragAmount
                        val absRaw = abs(rawDragX)
                        val damped = if (absRaw <= thresholdPx) {
                            rawDragX
                        } else {
                            sign(rawDragX) * (thresholdPx + (absRaw - thresholdPx) * 0.35f)
                        }
                        scope.launch {
                            offsetX.snapTo(damped)
                        }
                        val nextArmed = when {
                            damped >= thresholdPx -> 1
                            damped <= -thresholdPx -> -1
                            else -> 0
                        }
                        if (nextArmed != armedDirection) {
                            armedDirection = nextArmed
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        val committedDirection = armedDirection
                        armedDirection = 0
                        rawDragX = 0f
                        if (committedDirection == 1 && currentSafeIndex in -1..currentLastIndex) {
                            currentOnPlayNext()
                        } else if (committedDirection == -1 && currentOrigIndex in 0..currentLastIndex) {
                            currentOnRemove()
                        }
                        springJob?.cancel()
                        springJob = scope.launch {
                            if (reducedMotion) {
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = returnSpring
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        armedDirection = 0
                        rawDragX = 0f
                        springJob?.cancel()
                        springJob = scope.launch {
                            if (reducedMotion) {
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = returnSpring
                                )
                            }
                        }
                    }
                )
            }
    ) {
        if (currentOffset > 2f) {
            QueueSwipeRevealPill(
                alignment = Alignment.CenterStart,
                backgroundColor = SwipePlayNextColor,
                icon = Icons.Rounded.PlaylistPlay,
                label = "Play Next",
                progress = (currentOffset / thresholdPx).coerceIn(0f, 1f),
                armed = currentOffset >= thresholdPx
            )
        } else if (currentOffset < -2f) {
            QueueSwipeRevealPill(
                alignment = Alignment.CenterEnd,
                backgroundColor = SwipeRemoveColor,
                icon = Icons.Rounded.DeleteOutline,
                label = "Remove",
                progress = (-currentOffset / thresholdPx).coerceIn(0f, 1f),
                armed = currentOffset <= -thresholdPx
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .graphicsLayer {
                    translationX = currentOffset
                }
                .clip(rowShape)
                .background(rowSurface, rowShape)
                .border(1.dp, rowBorder, rowShape)
                .clickable {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onSelect()
                }
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkImage(
                artworkUrl = item.track.artworkUrl,
                contentDescription = item.track.title,
                shape = thumbShape,
                modifier = Modifier
                    .size(44.dp)
                    .border(1.dp, QueueHairlineBorder, thumbShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.track.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.track.durationMs > 0L) {
                        Text(
                            text = "· ${formatDurationMs(item.track.durationMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum"
                            ),
                            color = GlassTokens.TextTertiary,
                            maxLines = 1
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier.defaultMinSize(
                    minWidth = DockMetrics.MinTapTarget,
                    minHeight = DockMetrics.MinTapTarget
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Track options",
                    tint = GlassTokens.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = {},
                modifier = Modifier
                    .defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                    .then(dragHandleModifier)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Reorder track",
                    tint = if (isDragging) Color.White else GlassTokens.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.QueueSwipeRevealPill(
    alignment: Alignment,
    backgroundColor: Color,
    icon: ImageVector,
    label: String,
    progress: Float,
    armed: Boolean
) {
    val pillShape = remember { SmoothCornerShape(14.dp) }
    val scale = 0.82f + 0.18f * progress
    val alpha = (0.35f + 0.65f * progress).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(pillShape)
            .background(
                color = if (armed) backgroundColor else backgroundColor.copy(alpha = 0.82f),
                shape = pillShape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = SwipeInkColor,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = SwipeInkColor,
            maxLines = 1
        )
    }
}

@Composable
private fun OndaQueueEmptyState() {
    val shape = remember { SmoothCornerShape(18.dp) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(shape)
            .background(QueueRowSurface, shape)
            .border(1.dp, QueueHairlineBorder, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.QueueMusic,
            contentDescription = null,
            tint = GlassTokens.TextSecondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Queue is empty",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Select a track to start your Onda Deck",
            style = MaterialTheme.typography.bodySmall,
            color = GlassTokens.TextSecondary
        )
    }
}

@Composable
private fun OndaUpNextEmptyCard() {
    val shape = remember { SmoothCornerShape(16.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QueueRowSurface, shape)
            .border(1.dp, QueueHairlineBorder, shape)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "End of queue",
            style = MaterialTheme.typography.bodySmall,
            color = GlassTokens.TextSecondary
        )
    }
}
