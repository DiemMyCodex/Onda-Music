package com.onda.music.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaLyricSweepLine
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.LyricsMotionStyle
import com.onda.music.core.model.LyricsWord

private const val PREVIEW_LOOP_MS = 4_600L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsStylePickerSheet(
    selectedStyle: LyricsMotionStyle,
    lyricsMotionEnabled: Boolean,
    hapticsEnabled: Boolean,
    strings: SettingsStrings,
    onSelectStyle: (LyricsMotionStyle) -> Unit,
    onLyricsMotionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val previewActive = lyricsMotionEnabled && !reducedMotion

    val sampleWords = remember(strings.lyricsStyleSampleLine) {
        buildLyricsPreviewWords(strings.lyricsStyleSampleLine)
    }

    var loopPositionMs by remember { mutableLongStateOf(2_200L) }
    LaunchedEffect(previewActive) {
        if (!previewActive) {
            loopPositionMs = 2_400L
            return@LaunchedEffect
        }
        var lastNanos = 0L
        var elapsed = 350L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dtMs = ((frameNanos - lastNanos) / 1_000_000L).coerceIn(0L, 48L)
                    elapsed = (elapsed + dtMs) % PREVIEW_LOOP_MS
                    loopPositionMs = elapsed
                }
                lastNanos = frameNanos
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SettingsOledColors.MatteCard,
        contentColor = SettingsOledColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SettingsOledColors.TextMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = SettingsOledColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.lyricsStyleSheetTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = SettingsOledColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val closeInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(DockMetrics.MinTapTarget)
                        .pressBounce(closeInteraction, pressedScale = 0.90f)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = RoundedHighlight(cornerRadius = 24.dp),
                            role = Role.Button,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.closeLabel,
                        tint = SettingsOledColors.TextSecondary
                    )
                }
            }

            LyricsMotionStyle.entries.forEach { style ->
                LyricsStylePreviewCard(
                    style = style,
                    selected = style == selectedStyle,
                    strings = strings,
                    sampleWords = sampleWords,
                    positionProvider = { loopPositionMs },
                    previewActive = previewActive,
                    onClick = {
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        if (!lyricsMotionEnabled) {
                            onLyricsMotionChange(true)
                        }
                        onSelectStyle(style)
                    }
                )
            }

            SettingsMatteCard(
                backgroundColor = SettingsOledColors.IconBadge,
                cornerRadius = 22
            ) {
                SettingsToggleRow(
                    icon = Icons.Rounded.MusicNote,
                    title = strings.lyricsMotionTitle,
                    subtitle = strings.lyricsMotionSubtitle,
                    checked = lyricsMotionEnabled,
                    onCheckedChange = onLyricsMotionChange,
                    iconBadgeColor = SettingsOledColors.MatteCard,
                    hapticsEnabled = hapticsEnabled
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsStylePreviewCard(
    style: LyricsMotionStyle,
    selected: Boolean,
    strings: SettingsStrings,
    sampleWords: List<LyricsWord>,
    positionProvider: () -> Long,
    previewActive: Boolean,
    onClick: () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val cardShape = remember { SmoothCornerShape(22.dp) }
    val stageShape = remember { SmoothCornerShape(16.dp) }
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFF232328),
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "LyricsStyleBorder"
    )
    val cardBg by animateColorAsState(
        targetValue = if (selected) Color(0xFF1A1A1E) else SettingsOledColors.IconBadge,
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "LyricsStyleBg"
    )

    val tags = remember(style, strings) {
        when (style) {
            LyricsMotionStyle.KINETIC_GLOW -> listOf(strings.lyricsStyleTagBloom, strings.lyricsStyleTagCascade)
            LyricsMotionStyle.WAVE_GLOW -> listOf(strings.lyricsStyleTagWave, strings.lyricsStyleTagBloom)
            LyricsMotionStyle.KINETIC_CLEAN -> listOf(strings.lyricsStyleTagCrisp, strings.lyricsStyleTagCascade)
            LyricsMotionStyle.PURE_SWEEP -> listOf(strings.lyricsStyleTagSweep, strings.lyricsStyleTagCrisp)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(interactionSource, pressedScale = 0.985f)
            .clip(cardShape)
            .background(cardBg)
            .border(width = if (selected) 1.5.dp else 1.dp, color = borderColor, shape = cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 22.dp),
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.lyricsMotionStyleName(style),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SettingsOledColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (selected) Color.Black else SettingsOledColors.TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) Color.White else Color(0xFF26262C))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = strings.lyricsMotionStyleDesc(style),
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsOledColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color(0xFF26262C)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF6B6B73),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(stageShape)
                .background(Color(0xFF000000))
                .border(1.dp, Color(0xFF1C1C20), stageShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = strings.lyricsStyleSamplePrev,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0x42FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Color.White else Color(0x7AFFFFFF))
                )
                OndaLyricSweepLine(
                    text = strings.lyricsStyleSampleLine,
                    words = sampleWords,
                    lineStartMs = 350L,
                    lineEndMs = 4_100L,
                    positionProvider = positionProvider,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    motionStyle = style,
                    motionActive = previewActive,
                    activeColor = Color.White,
                    baseColor = Color(0x52FFFFFF),
                    glowColor = Color(0xFFE6EAFF),
                    textAlign = TextAlign.Start,
                    motionScale = 0.85f,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = strings.lyricsStyleSampleNext,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0x34FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildLyricsPreviewWords(line: String): List<LyricsWord> {
    val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    val startMs = 350L
    val heldFinalMs = 1_150L
    val regularSpanMs = 2_450L
    if (tokens.size == 1) {
        return listOf(LyricsWord(tokens[0], startMs, startMs + heldFinalMs))
    }
    val regularTokens = tokens.dropLast(1)
    val weights = regularTokens.map { it.length.coerceAtLeast(2).toFloat() }
    val totalWeight = weights.sum().coerceAtLeast(1f)
    var cursor = startMs
    var acc = 0f
    val built = ArrayList<LyricsWord>(tokens.size)
    regularTokens.forEachIndexed { idx, token ->
        val segStart = cursor
        acc += weights[idx]
        val segEnd = (startMs + ((acc / totalWeight) * regularSpanMs).toLong()).coerceAtLeast(segStart + 120L)
        cursor = segEnd
        built.add(LyricsWord(token, segStart, segEnd))
    }
    built.add(LyricsWord(tokens.last(), cursor, cursor + heldFinalMs))
    return built
}
