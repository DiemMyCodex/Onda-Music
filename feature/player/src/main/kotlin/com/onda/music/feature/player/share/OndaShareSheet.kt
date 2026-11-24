package com.onda.music.feature.player.share

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.ArtworkUrlResolver
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.LocalOndaToastState
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.design.rememberDeviceTiltOffset
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.Track
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.formatDurationMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun OndaShareSheet(
    track: Track,
    lyrics: LyricsDocument?,
    palette: OndaArtworkPalette,
    initialLineIndex: Int?,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val toastState = LocalOndaToastState.current
    val reducedMotion = OndaTheme.reducedMotion
    val scope = rememberCoroutineScope()

    val allLines = remember(lyrics) { lyrics?.lines.orEmpty() }
    val hasSelectableLyrics = remember(allLines) {
        allLines.any { OndaLyricSelectionLogic.isSelectableLine(it) }
    }

    var template by remember(track.id, initialLineIndex, hasSelectableLyrics) {
        mutableStateOf(
            if (hasSelectableLyrics && initialLineIndex != null) {
                OndaCardTemplate.EDITORIAL_LYRIC
            } else {
                OndaCardTemplate.VINYL_TICKET
            }
        )
    }
    var backdrop by remember { mutableStateOf(OndaCardBackdrop.ARTWORK_MIST) }
    var surfaceTone by remember { mutableStateOf(OndaCardSurfaceTone.OBSIDIAN) }
    var cardOpacity by remember { mutableFloatStateOf(0.88f) }
    var posterHeaderTop by remember { mutableStateOf(true) }
    var activeBrush by remember { mutableStateOf(OndaWordEmphasis.LUMINOUS) }
    var activeTabIndex by remember(template) { mutableIntStateOf(0) }

    var selectedIndices by remember(track.id, initialLineIndex, allLines) {
        mutableStateOf(resolveInitialSelection(allLines, initialLineIndex))
    }
    var wordEmphasesByDocKey by remember(track.id) {
        mutableStateOf<Map<String, OndaWordEmphasis>>(emptyMap())
    }

    var isSaving by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var rejectPulse by remember { mutableIntStateOf(0) }
    var showLimitNotice by remember { mutableStateOf(false) }

    val activeMaxWords = if (template == OndaCardTemplate.STAGE_POSTER) {
        OndaLyricSelectionLogic.MAX_POSTER_WORDS
    } else {
        OndaLyricSelectionLogic.MAX_CARD_WORDS
    }

    LaunchedEffect(rejectPulse) {
        if (rejectPulse > 0) {
            showLimitNotice = true
            delay(1300L)
            showLimitNotice = false
        }
    }

    val selectedLines = remember(selectedIndices, allLines) {
        selectedIndices.mapNotNull { allLines.getOrNull(it) }
    }
    val wordCount = remember(selectedIndices, allLines) {
        OndaLyricSelectionLogic.countWords(selectedIndices, allLines)
    }
    val renderWordEmphases = remember(selectedIndices, allLines, wordEmphasesByDocKey) {
        buildMap {
            selectedIndices.forEachIndexed { renderLineIdx, docLineIdx ->
                val line = allLines.getOrNull(docLineIdx) ?: return@forEachIndexed
                val words = OndaLyricSelectionLogic.splitWords(line.text)
                words.indices.forEach { wordIdx ->
                    val emphasis = wordEmphasesByDocKey[wordEmphasisKey(docLineIdx, wordIdx)]
                    if (emphasis != null && emphasis != OndaWordEmphasis.NONE) {
                        put(wordEmphasisKey(renderLineIdx, wordIdx), emphasis)
                    }
                }
            }
        }
    }

    val scrimInteraction = remember { MutableInteractionSource() }

    val handleExport: (Boolean) -> Unit = exportAction@{ shareToApps ->
        if (isSaving || isSharing) return@exportAction
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            if (shareToApps) isSharing = true else isSaving = true
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = renderOndaShareBitmap(
                    context = context,
                    track = track,
                    palette = palette,
                    template = template,
                    backdrop = backdrop,
                    surfaceTone = surfaceTone,
                    cardOpacity = cardOpacity,
                    selectedLines = selectedLines,
                    wordEmphases = renderWordEmphases,
                    posterHeaderTop = posterHeaderTop
                )
                if (shareToApps) {
                    shareCardImage(context, bitmap, track.title).onFailure { err ->
                        toastState?.show(
                            title = "Could not share Soundcard",
                            subtitle = err.localizedMessage ?: "Share unavailable"
                        )
                    }
                } else {
                    saveCardToGallery(context, bitmap, track.title).onSuccess {
                        toastState?.show(
                            title = "Saved to Pictures/Onda",
                            subtitle = track.title,
                            artworkUrl = track.artworkUrl
                        )
                    }.onFailure { err ->
                        toastState?.show(
                            title = "Could not save Soundcard",
                            subtitle = err.localizedMessage ?: "Storage error"
                        )
                    }
                }
            } finally {
                bitmap?.recycle()
                if (shareToApps) isSharing = false else isSaving = false
            }
        }
    }

    val handleTapLine: (Int) -> Unit = { tappedIdx ->
        val next = OndaLyricSelectionLogic.toggleContiguousLine(
            currentIndices = selectedIndices,
            tappedIndex = tappedIdx,
            lines = allLines,
            maxWords = activeMaxWords
        )
        if (next == selectedIndices) {
            rejectPulse++
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selectedIndices = next
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ShareSheetTopBar(
            isBusy = isSaving || isSharing,
            isSaving = isSaving,
            isSharing = isSharing,
            reducedMotion = reducedMotion,
            onDismiss = {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            },
            onSave = { handleExport(false) },
            onShare = { handleExport(true) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.47f)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            LiveSoundcardPreview(
                track = track,
                palette = palette,
                template = template,
                backdrop = backdrop,
                surfaceTone = surfaceTone,
                cardOpacity = cardOpacity,
                selectedLines = selectedLines,
                wordEmphases = renderWordEmphases,
                posterHeaderTop = posterHeaderTop,
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.53f)
                .clip(SmoothCornerShape(24.dp))
                .background(Color(0xFF111114))
                .border(1.dp, Color(0x1EFFFFFF), SmoothCornerShape(24.dp))
                .padding(top = 10.dp)
        ) {
            TemplateSelectorRow(
                selected = template,
                reducedMotion = reducedMotion,
                onSelect = { nextTemplate ->
                    if (template != nextTemplate) {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        template = nextTemplate
                        if (nextTemplate == OndaCardTemplate.EDITORIAL_LYRIC) {
                            selectedIndices = OndaLyricSelectionLogic.trimToMaxWords(
                                indices = selectedIndices,
                                lines = allLines,
                                maxWords = OndaLyricSelectionLogic.MAX_CARD_WORDS
                            )
                        }
                    }
                }
            )

            when (template) {
                OndaCardTemplate.VINYL_TICKET -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BackdropSelectorSection(
                            selected = backdrop,
                            reducedMotion = reducedMotion,
                            onSelect = {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                backdrop = it
                            }
                        )
                        TicketTelemetrySummary(track = track)
                    }
                }

                OndaCardTemplate.EDITORIAL_LYRIC -> {
                    val tabs = listOf("Lyrics", "Surface")
                    ControlTabRow(
                        tabs = tabs,
                        selectedIndex = activeTabIndex.coerceIn(0, tabs.lastIndex),
                        reducedMotion = reducedMotion,
                        onSelect = {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeTabIndex = it
                        }
                    )
                    if (activeTabIndex == 0) {
                        LyricSelectionTab(
                            wordCount = wordCount,
                            maxWords = activeMaxWords,
                            template = template,
                            palette = palette,
                            rejectPulse = rejectPulse,
                            showLimitNotice = showLimitNotice,
                            lines = allLines,
                            selectedIndices = selectedIndices,
                            reducedMotion = reducedMotion,
                            onTapLine = handleTapLine,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SurfaceToneSection(
                                selected = surfaceTone,
                                reducedMotion = reducedMotion,
                                onSelect = {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    surfaceTone = it
                                }
                            )
                            OpacitySliderRow(
                                opacity = cardOpacity,
                                palette = palette,
                                onOpacityChange = { cardOpacity = it }
                            )
                            BackdropSelectorSection(
                                selected = backdrop,
                                reducedMotion = reducedMotion,
                                onSelect = {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    backdrop = it
                                }
                            )
                        }
                    }
                }

                OndaCardTemplate.STAGE_POSTER -> {
                    val tabs = listOf("Lyrics", "Words", "Backdrop")
                    ControlTabRow(
                        tabs = tabs,
                        selectedIndex = activeTabIndex.coerceIn(0, tabs.lastIndex),
                        reducedMotion = reducedMotion,
                        onSelect = {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeTabIndex = it
                        }
                    )
                    when (activeTabIndex.coerceIn(0, tabs.lastIndex)) {
                        0 -> {
                            LyricSelectionTab(
                                wordCount = wordCount,
                                maxWords = activeMaxWords,
                                template = template,
                                palette = palette,
                                rejectPulse = rejectPulse,
                                showLimitNotice = showLimitNotice,
                                lines = allLines,
                                selectedIndices = selectedIndices,
                                reducedMotion = reducedMotion,
                                onTapLine = handleTapLine,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        1 -> {
                            PosterWordEditorSection(
                                selectedIndices = selectedIndices,
                                allLines = allLines,
                                activeBrush = activeBrush,
                                wordEmphasesByDocKey = wordEmphasesByDocKey,
                                posterHeaderTop = posterHeaderTop,
                                reducedMotion = reducedMotion,
                                onSelectBrush = {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activeBrush = it
                                },
                                onToggleWord = { docKey ->
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val existing = wordEmphasesByDocKey[docKey] ?: OndaWordEmphasis.NONE
                                    val updated = wordEmphasesByDocKey.toMutableMap()
                                    if (existing == activeBrush) {
                                        updated.remove(docKey)
                                    } else {
                                        updated[docKey] = activeBrush
                                    }
                                    wordEmphasesByDocKey = updated
                                },
                                onClearWords = {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    wordEmphasesByDocKey = emptyMap()
                                },
                                onToggleHeaderPosition = {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    posterHeaderTop = !posterHeaderTop
                                }
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BackdropSelectorSection(
                                    selected = backdrop,
                                    reducedMotion = reducedMotion,
                                    onSelect = {
                                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        backdrop = it
                                    }
                                )
                                HeaderPlacementChipRow(
                                    posterHeaderTop = posterHeaderTop,
                                    reducedMotion = reducedMotion,
                                    onToggle = {
                                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        posterHeaderTop = !posterHeaderTop
                                    }
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
private fun LyricSelectionTab(
    wordCount: Int,
    maxWords: Int,
    template: OndaCardTemplate,
    palette: OndaArtworkPalette,
    rejectPulse: Int,
    showLimitNotice: Boolean,
    lines: List<LyricsLine>,
    selectedIndices: List<Int>,
    reducedMotion: Boolean,
    onTapLine: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WordBudgetGauge(
            wordCount = wordCount,
            maxWords = maxWords,
            template = template,
            palette = palette,
            rejectPulse = rejectPulse,
            showLimitNotice = showLimitNotice,
            reducedMotion = reducedMotion,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LyricLineSelectorList(
            lines = lines,
            selectedIndices = selectedIndices,
            reducedMotion = reducedMotion,
            onTapLine = onTapLine,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun resolveInitialSelection(
    lines: List<LyricsLine>,
    initialLineIndex: Int?
): List<Int> {
    if (lines.isEmpty()) return emptyList()
    if (initialLineIndex != null && initialLineIndex in lines.indices) {
        val candidate = listOf(initialLineIndex)
        if (OndaLyricSelectionLogic.isSelectableLine(lines[initialLineIndex]) &&
            OndaLyricSelectionLogic.countWords(candidate, lines) <= OndaLyricSelectionLogic.MAX_CARD_WORDS
        ) {
            return candidate
        }
    }
    val firstValid = lines.indexOfFirst { line ->
        OndaLyricSelectionLogic.isSelectableLine(line) &&
            OndaLyricSelectionLogic.splitWords(line.text).size <= OndaLyricSelectionLogic.MAX_CARD_WORDS
    }
    return if (firstValid >= 0) listOf(firstValid) else emptyList()
}

@Composable
private fun ShareSheetTopBar(
    isBusy: Boolean,
    isSaving: Boolean,
    isSharing: Boolean,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OndaMonogramMark(
                modifier = Modifier.size(18.dp),
                tint = Color.White,
                filled = false
            )
            Text(
                text = "Onda Soundcard",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ActionPillButton(
            label = "Save",
            icon = Icons.Rounded.Download,
            isLoading = isSaving,
            enabled = !isBusy,
            filled = false,
            reducedMotion = reducedMotion,
            onClick = onSave
        )

        ActionPillButton(
            label = "Share",
            icon = Icons.Rounded.Share,
            isLoading = isSharing,
            enabled = !isBusy,
            filled = true,
            reducedMotion = reducedMotion,
            onClick = onShare
        )
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    enabled: Boolean,
    filled: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(999.dp) }
    val bgColor = if (filled) Color.White else Color(0xFF1A1A1E)
    val contentColor = if (filled) Color(0xFF070709) else Color.White

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .pressBounce(interactionSource = interaction, enabled = enabled && !reducedMotion)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(shape)
                .background(if (enabled) bgColor else bgColor.copy(alpha = 0.5f), shape)
                .then(
                    if (!filled) {
                        Modifier.border(1.dp, Color(0x28FFFFFF), shape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun LiveSoundcardPreview(
    track: Track,
    palette: OndaArtworkPalette,
    template: OndaCardTemplate,
    backdrop: OndaCardBackdrop,
    surfaceTone: OndaCardSurfaceTone,
    cardOpacity: Float,
    selectedLines: List<LyricsLine>,
    wordEmphases: Map<String, OndaWordEmphasis>,
    posterHeaderTop: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val tilt by rememberDeviceTiltOffset(enabled = !reducedMotion, maxDegrees = 8f)
    val cardShape = remember { SmoothCornerShape(24.dp) }

    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer {
                if (!reducedMotion) {
                    rotationY = tilt.x * 6f
                    rotationX = -tilt.y * 6f
                    cameraDistance = 14f * density
                }
            }
            .clip(cardShape)
            .border(1.dp, Color(0x28FFFFFF), cardShape)
    ) {
        val scale = (maxWidth / 360.dp).coerceIn(0.32f, 1.25f)
        PreviewBackdropLayer(
            backdrop = backdrop,
            artworkUrl = track.artworkUrl,
            palette = palette,
            reducedMotion = reducedMotion
        )
        when (template) {
            OndaCardTemplate.VINYL_TICKET -> VinylTicketPreview(
                track = track,
                palette = palette,
                backdrop = backdrop,
                scale = scale
            )
            OndaCardTemplate.EDITORIAL_LYRIC -> EditorialLyricPreview(
                track = track,
                palette = palette,
                backdrop = backdrop,
                surfaceTone = surfaceTone,
                cardOpacity = cardOpacity,
                selectedLines = selectedLines,
                scale = scale
            )
            OndaCardTemplate.STAGE_POSTER -> StagePosterPreview(
                track = track,
                palette = palette,
                backdrop = backdrop,
                selectedLines = selectedLines,
                wordEmphases = wordEmphases,
                posterHeaderTop = posterHeaderTop,
                scale = scale
            )
        }
    }
}

@Composable
private fun PreviewBackdropLayer(
    backdrop: OndaCardBackdrop,
    artworkUrl: String?,
    palette: OndaArtworkPalette,
    reducedMotion: Boolean
) {
    val context = LocalContext.current
    val resolvedUrl = remember(artworkUrl) { ArtworkUrlResolver.upgradeHighRes(artworkUrl) }

    when (backdrop) {
        OndaCardBackdrop.ARTWORK_MIST -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0C))
            ) {
                if (!resolvedUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedUrl)
                            .crossfade(!reducedMotion)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(36.dp)
                                } else {
                                    Modifier
                                }
                            )
                            .graphicsLayer { alpha = 0.56f }
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(palette.primary.copy(alpha = 0.34f), Color.Transparent),
                            center = Offset(size.width * 0.18f, size.height * 0.20f),
                            radius = size.width * 0.95f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(palette.tertiary.copy(alpha = 0.36f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.80f),
                            radius = size.width * 0.90f
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color(0x88070709),
                                0.45f to Color(0x40070709),
                                1f to Color(0x99070709)
                            )
                        )
                    )
                }
            }
        }

        OndaCardBackdrop.OLED_SOLID -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050507))
            )
        }

        OndaCardBackdrop.ACOUSTIC_GRID -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0B0E))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(palette.primary.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.35f),
                            radius = size.width * 0.9f
                        )
                    )
                    val step = size.width / 15f
                    var x = 0f
                    var col = 0
                    while (x <= size.width) {
                        val color = if (col % 4 == 0) Color(0x24FFFFFF) else Color(0x12FFFFFF)
                        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                        col++
                    }
                    var y = 0f
                    var row = 0
                    while (y <= size.height) {
                        val color = if (row % 4 == 0) Color(0x24FFFFFF) else Color(0x12FFFFFF)
                        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                        row++
                    }
                    val m = size.width * 0.04f
                    val len = size.width * 0.028f
                    val tickColor = Color(0x52FFFFFF)
                    drawLine(tickColor, Offset(m, m), Offset(m + len, m), 1.5f)
                    drawLine(tickColor, Offset(m, m), Offset(m, m + len), 1.5f)
                    drawLine(tickColor, Offset(size.width - m - len, m), Offset(size.width - m, m), 1.5f)
                    drawLine(tickColor, Offset(size.width - m, m), Offset(size.width - m, m + len), 1.5f)
                    drawLine(tickColor, Offset(m, size.height - m), Offset(m + len, size.height - m), 1.5f)
                    drawLine(tickColor, Offset(m, size.height - m - len), Offset(m, size.height - m), 1.5f)
                    drawLine(
                        tickColor,
                        Offset(size.width - m - len, size.height - m),
                        Offset(size.width - m, size.height - m),
                        1.5f
                    )
                    drawLine(
                        tickColor,
                        Offset(size.width - m, size.height - m - len),
                        Offset(size.width - m, size.height - m),
                        1.5f
                    )
                }
            }
        }

        OndaCardBackdrop.WARM_PAPER -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFF5F0E5), Color(0xFFE3DAC9))
                        )
                    )
                    .padding((8).dp)
                    .border(1.dp, Color(0x1E141414))
            )
        }
    }
}

@Composable
private fun VinylTicketPreview(
    track: Track,
    palette: OndaArtworkPalette,
    backdrop: OndaCardBackdrop,
    scale: Float
) {
    val ticketShape = SmoothCornerShape((14f * scale).dp)
    val bars = remember(track.id) { buildOndaWaveformBars(track.id, 36) }
    val splitIndex = remember(bars) { (bars.size * 0.62f).roundToInt() }
    val serialNum = remember(track.id) { (abs(track.id.hashCode()) % 9000) + 1000 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = (34f * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ticketShape)
                .background(Color(0xFF131316))
                .border(1.dp, Color(0x26FFFFFF), ticketShape)
                .padding((18f * scale).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SOUNDCARD",
                    color = Color(0xFF8A8A94),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (7.2f * scale).sp,
                    letterSpacing = (1.1f * scale).sp,
                    maxLines = 1
                )
                Text(
                    text = "NO.$serialNum",
                    color = Color(0xFF8A8A94),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (7.2f * scale).sp,
                    letterSpacing = (1.1f * scale).sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height((10f * scale).dp))

            TrackArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                shape = RoundedCornerShape((9f * scale).dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(0.75.dp, Color(0x22FFFFFF), RoundedCornerShape((9f * scale).dp))
            )

            Spacer(modifier = Modifier.height((12f * scale).dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x24FFFFFF))
            )

            Spacer(modifier = Modifier.height((10f * scale).dp))

            Text(
                text = track.title,
                color = Color.White,
                fontSize = (14.5f * scale).sp,
                lineHeight = (16.5f * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height((3f * scale).dp))

            Text(
                text = track.artist,
                color = Color(0xFFA2A2AC),
                fontSize = (9.8f * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height((10f * scale).dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDurationMs(track.durationMs),
                    color = Color.White,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontSize = (8.5f * scale).sp
                    ),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF1C1C22))
                        .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(999.dp))
                        .padding(horizontal = (7f * scale).dp, vertical = (2.5f * scale).dp)
                ) {
                    Text(
                        text = track.source.name,
                        color = Color(0xFFD4D4DC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (6.8f * scale).sp,
                        letterSpacing = (0.6f * scale).sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height((10f * scale).dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((18f * scale).dp)
            ) {
                val pitch = size.width / bars.size.toFloat()
                val barW = (pitch * 0.56f).coerceAtLeast(1.5f)
                bars.forEachIndexed { idx, level ->
                    val bh = (size.height * level).coerceAtLeast(3f)
                    val cx = idx * pitch + barW * 0.5f
                    drawRoundRect(
                        color = if (idx < splitIndex) palette.primary else Color(0xFF383842),
                        topLeft = Offset(cx - barW * 0.5f, (size.height - bh) * 0.5f),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height((10f * scale).dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x1EFFFFFF))
            )

            Spacer(modifier = Modifier.height((8f * scale).dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((5f * scale).dp)
                ) {
                    OndaMonogramMark(
                        modifier = Modifier.size((13f * scale).dp),
                        tint = Color.White,
                        filled = false
                    )
                    Text(
                        text = "ONDA",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = (8f * scale).sp,
                        letterSpacing = (1.6f * scale).sp
                    )
                }
                Text(
                    text = if (backdrop == OndaCardBackdrop.WARM_PAPER) "EDITION" else "ACOUSTIC ARCH",
                    color = Color(0xFF7A7A86),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (6.2f * scale).sp,
                    letterSpacing = (0.8f * scale).sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EditorialLyricPreview(
    track: Track,
    palette: OndaArtworkPalette,
    backdrop: OndaCardBackdrop,
    surfaceTone: OndaCardSurfaceTone,
    cardOpacity: Float,
    selectedLines: List<LyricsLine>,
    scale: Float
) {
    val style = remember(backdrop, surfaceTone, cardOpacity, palette) {
        resolveEditorialSurfaceStyle(backdrop, surfaceTone, cardOpacity, palette)
    }
    val fillColor = Color(style.fillArgb)
    val primaryInk = Color(style.primaryInkArgb)
    val secondaryInk = Color(style.secondaryInkArgb)
    val borderColor = Color(style.borderArgb)
    val cardShape = SmoothCornerShape((15f * scale).dp)

    val displayLines = remember(selectedLines, track.title) {
        if (selectedLines.isNotEmpty()) selectedLines else listOf(LyricsLine(0L, 0L, track.title))
    }
    val totalChars = remember(displayLines) { displayLines.sumOf { it.text.length } }
    val lyricFontSize = when {
        totalChars <= 50 -> 17.5f * scale
        totalChars <= 110 -> 14.5f * scale
        totalChars <= 180 -> 12.2f * scale
        else -> 10.4f * scale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = (30f * scale).dp, vertical = (72f * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(cardShape)
                .background(fillColor, cardShape)
                .border(1.dp, borderColor, cardShape)
                .padding((20f * scale).dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((8f * scale).dp)
            ) {
                Box(
                    modifier = Modifier
                        .width((18f * scale).dp)
                        .height((3f * scale).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (style.isLightSurface) {
                                primaryInk.copy(alpha = 0.72f)
                            } else {
                                palette.primary
                            }
                        )
                )
                Text(
                    text = "LYRIC EXCERPT",
                    color = secondaryInk,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (6.8f * scale).sp,
                    letterSpacing = (1.1f * scale).sp,
                    maxLines = 1
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .padding(vertical = (8f * scale).dp),
                verticalArrangement = Arrangement.spacedBy((7f * scale).dp, Alignment.CenterVertically)
            ) {
                displayLines.forEach { line ->
                    Column(verticalArrangement = Arrangement.spacedBy((2f * scale).dp)) {
                        Text(
                            text = line.text.trim(),
                            color = primaryInk,
                            fontSize = lyricFontSize.sp,
                            lineHeight = (lyricFontSize * 1.18f).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subText = listOfNotNull(
                            line.translatedText?.takeIf { it.isNotBlank() },
                            line.romanizedText?.takeIf { it.isNotBlank() }
                        ).joinToString(" / ")
                        if (subText.isNotBlank()) {
                            Text(
                                text = subText,
                                color = secondaryInk,
                                fontSize = (lyricFontSize * 0.62f).coerceAtLeast(7.5f * scale).sp,
                                lineHeight = (lyricFontSize * 0.76f).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy((10f * scale).dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(borderColor)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((8f * scale).dp)
                ) {
                    TrackArtworkImage(
                        artworkUrl = track.artworkUrl,
                        contentDescription = track.title,
                        shape = RoundedCornerShape((6f * scale).dp),
                        modifier = Modifier.size((28f * scale).dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = primaryInk,
                            fontSize = (9.2f * scale).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = secondaryInk,
                            fontSize = (7.5f * scale).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((4f * scale).dp)
                    ) {
                        OndaMonogramMark(
                            modifier = Modifier.size((11f * scale).dp),
                            tint = primaryInk,
                            filled = false
                        )
                        Text(
                            text = "ONDA",
                            color = primaryInk,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = (7.2f * scale).sp,
                            letterSpacing = (1.4f * scale).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StagePosterPreview(
    track: Track,
    palette: OndaArtworkPalette,
    backdrop: OndaCardBackdrop,
    selectedLines: List<LyricsLine>,
    wordEmphases: Map<String, OndaWordEmphasis>,
    posterHeaderTop: Boolean,
    scale: Float
) {
    val isLightBackdrop = backdrop == OndaCardBackdrop.WARM_PAPER
    val primaryInk = if (isLightBackdrop) Color(0xFF111113) else Color.White
    val mutedLyricInk = if (isLightBackdrop) Color(0xCC1C1C20) else Color(0xD9FFFFFF)
    val secondaryInk = if (isLightBackdrop) Color(0xB01C1C20) else Color(0xB3FFFFFF)
    val glowColor = if (isLightBackdrop) palette.primary.copy(alpha = 0.55f) else palette.primary.copy(alpha = 0.90f)

    val displayLines = remember(selectedLines, track.title) {
        if (selectedLines.isNotEmpty()) selectedLines else listOf(LyricsLine(0L, 0L, track.title))
    }
    val totalChars = remember(displayLines) { displayLines.sumOf { it.text.length } }
    val fontSize = when {
        totalChars <= 50 -> 23f * scale
        totalChars <= 120 -> 18.5f * scale
        totalChars <= 200 -> 15.2f * scale
        else -> 12.5f * scale
    }

    val annotatedLyrics = remember(displayLines, wordEmphases, isLightBackdrop, glowColor, primaryInk, mutedLyricInk) {
        buildAnnotatedString {
            displayLines.forEachIndexed { lineIdx, line ->
                if (lineIdx > 0) append("\n")
                val words = OndaLyricSelectionLogic.splitWords(line.text)
                if (words.isEmpty()) {
                    withStyle(SpanStyle(color = mutedLyricInk)) {
                        append(line.text)
                    }
                } else {
                    words.forEachIndexed { wordIdx, word ->
                        if (wordIdx > 0) append(" ")
                        val emphasis = wordEmphases[wordEmphasisKey(lineIdx, wordIdx)] ?: OndaWordEmphasis.NONE
                        when (emphasis) {
                            OndaWordEmphasis.NONE -> {
                                withStyle(SpanStyle(color = mutedLyricInk)) {
                                    append(word)
                                }
                            }
                            OndaWordEmphasis.BOLD -> {
                                withStyle(SpanStyle(color = primaryInk, fontWeight = FontWeight.Bold)) {
                                    append(word)
                                }
                            }
                            OndaWordEmphasis.CAPS -> {
                                withStyle(
                                    SpanStyle(
                                        color = primaryInk,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (1.2f * scale).sp
                                    )
                                ) {
                                    append(word.uppercase(Locale.getDefault()))
                                }
                            }
                            OndaWordEmphasis.LUMINOUS -> {
                                withStyle(
                                    SpanStyle(
                                        color = if (isLightBackdrop) Color(0xFF0A0A0C) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        shadow = Shadow(
                                            color = glowColor,
                                            offset = Offset.Zero,
                                            blurRadius = 16f
                                        )
                                    )
                                ) {
                                    append(word)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = (24f * scale).dp, vertical = (28f * scale).dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (posterHeaderTop) {
            PosterTrackHeaderRow(track, primaryInk, secondaryInk, scale)
        } else {
            PosterBrandSignatureRow(primaryInk, secondaryInk, scale)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = (12f * scale).dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = annotatedLyrics,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.16f).sp,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (posterHeaderTop) {
            PosterBrandSignatureRow(primaryInk, secondaryInk, scale)
        } else {
            PosterTrackHeaderRow(track, primaryInk, secondaryInk, scale)
        }
    }
}

@Composable
private fun PosterTrackHeaderRow(
    track: Track,
    primaryInk: Color,
    secondaryInk: Color,
    scale: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((9f * scale).dp)
    ) {
        TrackArtworkImage(
            artworkUrl = track.artworkUrl,
            contentDescription = track.title,
            shape = RoundedCornerShape((7f * scale).dp),
            modifier = Modifier.size((30f * scale).dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = primaryInk,
                fontSize = (10.5f * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = secondaryInk,
                fontSize = (8.2f * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PosterBrandSignatureRow(
    primaryInk: Color,
    secondaryInk: Color,
    scale: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((5f * scale).dp)
        ) {
            OndaMonogramMark(
                modifier = Modifier.size((12f * scale).dp),
                tint = primaryInk,
                filled = false
            )
            Text(
                text = "ONDA",
                color = primaryInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (7.5f * scale).sp,
                letterSpacing = (1.6f * scale).sp
            )
        }
        Text(
            text = "SOUNDCARD",
            color = secondaryInk,
            fontFamily = FontFamily.Monospace,
            fontSize = (6.8f * scale).sp,
            letterSpacing = (1.1f * scale).sp
        )
    }
}

@Composable
private fun TemplateSelectorRow(
    selected: OndaCardTemplate,
    reducedMotion: Boolean,
    onSelect: (OndaCardTemplate) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OndaCardTemplate.entries.forEach { template ->
            ChoiceChipPill(
                label = template.label,
                selected = template == selected,
                reducedMotion = reducedMotion,
                onClick = { onSelect(template) }
            )
        }
    }
}

@Composable
private fun ControlTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    reducedMotion: Boolean,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { idx, title ->
            val isSelected = idx == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val shape = remember { SmoothCornerShape(12.dp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .pressBounce(interactionSource = interaction, enabled = !reducedMotion)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(idx) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(shape)
                        .background(
                            if (isSelected) Color(0xFF222228) else Color.Transparent,
                            shape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0x36FFFFFF) else Color(0x16FFFFFF),
                            shape = shape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else GlassTokens.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WordBudgetGauge(
    wordCount: Int,
    maxWords: Int,
    template: OndaCardTemplate,
    palette: OndaArtworkPalette,
    rejectPulse: Int,
    showLimitNotice: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(rejectPulse, reducedMotion) {
        if (rejectPulse > 0 && !reducedMotion) {
            shakeOffset.snapTo(0f)
            val offsets = floatArrayOf(12f, -12f, 8f, -8f, 4f, 0f)
            for (target in offsets) {
                shakeOffset.animateTo(target, tween(42))
            }
        }
    }

    val totalBudget = OndaLyricSelectionLogic.MAX_POSTER_WORDS.toFloat()
    val targetFraction = (wordCount.toFloat() / totalBudget).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (reducedMotion) snap() else AppMotion.ExpressiveSpring,
        label = "WordBudgetFill"
    )
    val gaugeColor by animateColorAsState(
        targetValue = when {
            showLimitNotice -> GlassTokens.ErrorCoral
            wordCount > OndaLyricSelectionLogic.MAX_CARD_WORDS &&
                template == OndaCardTemplate.EDITORIAL_LYRIC -> GlassTokens.WarningAmber
            wordCount > OndaLyricSelectionLogic.MAX_CARD_WORDS -> palette.primary
            else -> Color.White
        },
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "WordBudgetColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shakeOffset.value },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showLimitNotice) {
                    "WORD LIMIT REACHED ($maxWords MAX)"
                } else {
                    "WORD BUDGET"
                },
                color = if (showLimitNotice) GlassTokens.ErrorCoral else GlassTokens.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.9.sp,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                OdometerText(
                    text = wordCount.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = if (showLimitNotice) GlassTokens.ErrorCoral else Color.White
                )
                Text(
                    text = " / $maxWords",
                    color = GlassTokens.TextTertiary,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontSize = 11.sp
                    )
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val cardZoneFraction = OndaLyricSelectionLogic.MAX_CARD_WORDS.toFloat() / totalBudget
            val cardZoneEnd = size.width * cardZoneFraction
            val radius = size.height * 0.5f

            drawRoundRect(
                color = Color(0xFF1F1F25),
                topLeft = Offset.Zero,
                size = Size(cardZoneEnd - 2f, size.height),
                cornerRadius = CornerRadius(radius, radius)
            )
            drawRoundRect(
                color = if (template == OndaCardTemplate.STAGE_POSTER) {
                    Color(0xFF282832)
                } else {
                    Color(0xFF16161B)
                },
                topLeft = Offset(cardZoneEnd + 2f, 0f),
                size = Size((size.width - cardZoneEnd - 2f).coerceAtLeast(0f), size.height),
                cornerRadius = CornerRadius(radius, radius)
            )

            val fillWidth = (size.width * animatedFraction).coerceAtLeast(if (wordCount > 0) size.height else 0f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = gaugeColor,
                    topLeft = Offset.Zero,
                    size = Size(fillWidth.coerceAtMost(size.width), size.height),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }

            drawLine(
                color = Color(0x88FFFFFF),
                start = Offset(cardZoneEnd, 0f),
                end = Offset(cardZoneEnd, size.height),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
private fun LyricLineSelectorList(
    lines: List<LyricsLine>,
    selectedIndices: List<Int>,
    reducedMotion: Boolean,
    onTapLine: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectableEntries = remember(lines) {
        lines.mapIndexedNotNull { idx, line ->
            if (OndaLyricSelectionLogic.isSelectableLine(line)) idx to line else null
        }
    }

    if (selectableEntries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No lyrics available for this track",
                color = GlassTokens.TextSecondary,
                fontSize = 13.sp
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val selectedSet = remember(selectedIndices) { selectedIndices.toSet() }

    LaunchedEffect(Unit) {
        val firstSelected = selectedIndices.firstOrNull() ?: return@LaunchedEffect
        val listIndex = selectableEntries.indexOfFirst { it.first == firstSelected }
        if (listIndex > 1) {
            listState.scrollToItem((listIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(
            items = selectableEntries,
            key = { _, entry -> entry.first }
        ) { _, (docIdx, line) ->
            val isSelected = docIdx in selectedSet
            val shape = SmoothCornerShape(14.dp)
            val rowInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .pressBounce(
                        interactionSource = rowInteraction,
                        pressedScale = 0.985f,
                        enabled = !reducedMotion
                    )
                    .clip(shape)
                    .background(
                        if (isSelected) Color(0xFF222229) else Color(0xFF17171B),
                        shape
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0x45FFFFFF) else Color(0x14FFFFFF),
                        shape = shape
                    )
                    .clickable(
                        interactionSource = rowInteraction,
                        indication = null,
                        role = Role.Checkbox,
                        onClick = { onTapLine(docIdx) }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color.White else Color(0xFF26262E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFF070709),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.text.trim(),
                        color = if (isSelected) Color.White else Color(0xFFC2C2CC),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = line.translatedText?.takeIf { it.isNotBlank() }
                    if (sub != null) {
                        Text(
                            text = sub,
                            color = GlassTokens.TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "${OndaLyricSelectionLogic.splitWords(line.text).size}w",
                    color = GlassTokens.TextTertiary,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun SurfaceToneSection(
    selected: OndaCardSurfaceTone,
    reducedMotion: Boolean,
    onSelect: (OndaCardSurfaceTone) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "CARD SURFACE",
            color = GlassTokens.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OndaCardSurfaceTone.entries.forEach { tone ->
                ChoiceChipPill(
                    label = tone.label,
                    selected = tone == selected,
                    reducedMotion = reducedMotion,
                    onClick = { onSelect(tone) }
                )
            }
        }
    }
}

@Composable
private fun OpacitySliderRow(
    opacity: Float,
    palette: OndaArtworkPalette,
    onOpacityChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SURFACE OPACITY",
                color = GlassTokens.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.9.sp
            )
            Text(
                text = "${(opacity * 100f).roundToInt()}%",
                color = Color.White,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontSize = 11.sp
                )
            )
        }
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0.35f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = palette.primary,
                inactiveTrackColor = Color(0xFF282830)
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosterWordEditorSection(
    selectedIndices: List<Int>,
    allLines: List<LyricsLine>,
    activeBrush: OndaWordEmphasis,
    wordEmphasesByDocKey: Map<String, OndaWordEmphasis>,
    posterHeaderTop: Boolean,
    reducedMotion: Boolean,
    onSelectBrush: (OndaWordEmphasis) -> Unit,
    onToggleWord: (String) -> Unit,
    onClearWords: () -> Unit,
    onToggleHeaderPosition: () -> Unit
) {
    val brushOptions = remember {
        listOf(OndaWordEmphasis.LUMINOUS, OndaWordEmphasis.CAPS, OndaWordEmphasis.BOLD)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EMPHASIS BRUSH",
                color = GlassTokens.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.9.sp
            )
            if (wordEmphasesByDocKey.isNotEmpty()) {
                val clearInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(
                            interactionSource = clearInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClearWords
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Reset",
                        color = GlassTokens.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            brushOptions.forEach { brush ->
                ChoiceChipPill(
                    label = brush.displayLabel(),
                    selected = brush == activeBrush,
                    reducedMotion = reducedMotion,
                    onClick = { onSelectBrush(brush) }
                )
            }
        }

        if (selectedIndices.isEmpty()) {
            Text(
                text = "Select lyric lines in the Lyrics tab to emphasize words.",
                color = GlassTokens.TextSecondary,
                fontSize = 12.sp
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedIndices.forEach { docIdx ->
                    val line = allLines.getOrNull(docIdx) ?: return@forEach
                    val words = OndaLyricSelectionLogic.splitWords(line.text)
                    words.forEachIndexed { wordIdx, word ->
                        val docKey = wordEmphasisKey(docIdx, wordIdx)
                        val currentEmphasis = wordEmphasesByDocKey[docKey] ?: OndaWordEmphasis.NONE
                        WordEmphasisChip(
                            word = word,
                            emphasis = currentEmphasis,
                            reducedMotion = reducedMotion,
                            onClick = { onToggleWord(docKey) }
                        )
                    }
                }
            }
        }

        HeaderPlacementChipRow(
            posterHeaderTop = posterHeaderTop,
            reducedMotion = reducedMotion,
            onToggle = onToggleHeaderPosition
        )
    }
}

@Composable
private fun WordEmphasisChip(
    word: String,
    emphasis: OndaWordEmphasis,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val isStyled = emphasis != OndaWordEmphasis.NONE
    val interaction = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(10.dp) }
    val displayText = if (emphasis == OndaWordEmphasis.CAPS) {
        word.uppercase(Locale.getDefault())
    } else {
        word
    }

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .pressBounce(interactionSource = interaction, enabled = !reducedMotion)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 34.dp)
                .clip(shape)
                .background(
                    when (emphasis) {
                        OndaWordEmphasis.LUMINOUS -> Color.White
                        OndaWordEmphasis.CAPS, OndaWordEmphasis.BOLD -> Color(0xFF282830)
                        OndaWordEmphasis.NONE -> Color(0xFF19191E)
                    },
                    shape
                )
                .border(
                    width = 1.dp,
                    color = if (isStyled) Color(0x66FFFFFF) else Color(0x1EFFFFFF),
                    shape = shape
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                color = if (emphasis == OndaWordEmphasis.LUMINOUS) Color(0xFF070709) else Color.White,
                fontSize = 12.sp,
                fontWeight = if (isStyled) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeaderPlacementChipRow(
    posterHeaderTop: Boolean,
    reducedMotion: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "POSTER HEADER",
            color = GlassTokens.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp
        )
        ChoiceChipPill(
            label = if (posterHeaderTop) "Header: Top" else "Header: Bottom",
            selected = true,
            reducedMotion = reducedMotion,
            onClick = onToggle
        )
    }
}

@Composable
private fun BackdropSelectorSection(
    selected: OndaCardBackdrop,
    reducedMotion: Boolean,
    onSelect: (OndaCardBackdrop) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "BACKDROP",
            color = GlassTokens.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OndaCardBackdrop.entries.forEach { option ->
                ChoiceChipPill(
                    label = option.label,
                    selected = option == selected,
                    reducedMotion = reducedMotion,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun TicketTelemetrySummary(track: Track) {
    val shape = remember { SmoothCornerShape(14.dp) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF17171C), shape)
            .border(1.dp, Color(0x16FFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "1080 x 1920 SOUNDCARD",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "${track.source.name} / ${formatDurationMs(track.durationMs)}",
                color = GlassTokens.TextSecondary,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontSize = 11.sp
                )
            )
        }
        OndaMonogramMark(
            modifier = Modifier.size(18.dp),
            tint = Color.White,
            filled = false
        )
    }
}

@Composable
private fun ChoiceChipPill(
    label: String,
    selected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(999.dp) }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .pressBounce(interactionSource = interaction, enabled = !reducedMotion)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(shape)
                .background(if (selected) Color.White else Color(0xFF1A1A1F), shape)
                .border(
                    width = 1.dp,
                    color = if (selected) Color.White else Color(0x24FFFFFF),
                    shape = shape
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) Color(0xFF070709) else Color.White,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
