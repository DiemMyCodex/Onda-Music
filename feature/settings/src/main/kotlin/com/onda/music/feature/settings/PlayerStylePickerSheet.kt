package com.onda.music.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.press3D
import com.onda.music.core.model.PlayerStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStylePickerSheet(
    selectedStyle: PlayerStyle,
    glassBlurEnabled: Boolean,
    dynamicMeshEnabled: Boolean,
    lyricsMotionEnabled: Boolean,
    hapticsEnabled: Boolean,
    strings: SettingsStrings,
    onSelectStyle: (PlayerStyle) -> Unit,
    onGlassBlurChange: (Boolean) -> Unit,
    onDynamicMeshChange: (Boolean) -> Unit,
    onLyricsMotionChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = PlayerStyle.entries.chunked(2)

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = SettingsOledColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.playerStyleSheetTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = SettingsOledColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(DockMetrics.MinTapTarget)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.closeLabel,
                        tint = SettingsOledColors.TextSecondary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { style ->
                            PlayerStyleTile(
                                style = style,
                                selected = style == selectedStyle,
                                onClick = { onSelectStyle(style) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Text(
                text = strings.playerStyleVisualToggles,
                style = MaterialTheme.typography.labelLarge,
                color = SettingsOledColors.TextSecondary
            )

            SettingsMatteCard(
                backgroundColor = SettingsOledColors.IconBadge,
                cornerRadius = 24
            ) {
                SettingsToggleRow(
                    icon = Icons.Rounded.BlurOn,
                    title = strings.glassBlurTitle,
                    subtitle = strings.glassBlurSubtitle,
                    checked = glassBlurEnabled,
                    onCheckedChange = onGlassBlurChange,
                    iconBadgeColor = SettingsOledColors.MatteCard
                )
                SettingsRowDivider(color = SettingsOledColors.MatteCard)
                SettingsToggleRow(
                    icon = Icons.Rounded.Gradient,
                    title = strings.dynamicMeshTitle,
                    subtitle = strings.dynamicMeshSubtitle,
                    checked = dynamicMeshEnabled,
                    onCheckedChange = onDynamicMeshChange,
                    iconBadgeColor = SettingsOledColors.MatteCard
                )
                SettingsRowDivider(color = SettingsOledColors.MatteCard)
                SettingsToggleRow(
                    icon = Icons.Rounded.MusicNote,
                    title = strings.lyricsMotionTitle,
                    subtitle = strings.lyricsMotionSubtitle,
                    checked = lyricsMotionEnabled,
                    onCheckedChange = onLyricsMotionChange,
                    iconBadgeColor = SettingsOledColors.MatteCard
                )
                SettingsRowDivider(color = SettingsOledColors.MatteCard)
                SettingsToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = strings.hapticsTitle,
                    subtitle = strings.hapticsSubtitle,
                    checked = hapticsEnabled,
                    onCheckedChange = onHapticsChange,
                    iconBadgeColor = SettingsOledColors.MatteCard
                )
            }
        }
    }
}

@Composable
private fun PlayerStyleTile(
    style: PlayerStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val bgColor = if (selected) SettingsOledColors.TextPrimary else SettingsOledColors.IconBadge
    val glyphColor = if (selected) SettingsOledColors.CanvasBlack else SettingsOledColors.TextPrimary
    val dimGlyphColor = if (selected) Color(0x66000000) else Color(0x669E9E9E)

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 96.dp)
            .press3D()
            .clip(shape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PlayerStyleMiniGlyph(
                style = style,
                accentColor = glyphColor,
                dimColor = dimGlyphColor,
                modifier = Modifier.size(width = 48.dp, height = 34.dp)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(SettingsOledColors.CanvasBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = SettingsOledColors.TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = style.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) SettingsOledColors.CanvasBlack else SettingsOledColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerStyleMiniGlyph(
    style: PlayerStyle,
    accentColor: Color,
    dimColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (style) {
            PlayerStyle.FLAGSHIP -> {
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.15f, h * 0.08f),
                    size = Size(w * 0.70f, h * 0.56f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawLine(
                    color = accentColor,
                    start = Offset(w * 0.12f, h * 0.84f),
                    end = Offset(w * 0.88f, h * 0.84f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            PlayerStyle.CLASSIC -> {
                drawRoundRect(
                    color = dimColor,
                    topLeft = Offset(w * 0.25f, h * 0.08f),
                    size = Size(w * 0.50f, h * 0.50f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawLine(
                    color = accentColor,
                    start = Offset(w * 0.2f, h * 0.74f),
                    end = Offset(w * 0.8f, h * 0.74f),
                    strokeWidth = 2f
                )
                drawCircle(color = accentColor, radius = 4f, center = Offset(w * 0.5f, h * 0.90f))
            }
            PlayerStyle.POSTER -> {
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.28f),
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRect(
                    color = SettingsOledColors.CanvasBlack.copy(alpha = 0.55f),
                    topLeft = Offset(0f, h * 0.58f),
                    size = Size(w, h * 0.42f)
                )
            }
            PlayerStyle.TURNTABLE -> {
                drawCircle(
                    color = accentColor,
                    radius = h * 0.40f,
                    center = Offset(w * 0.44f, h * 0.5f),
                    style = Stroke(width = 2.5f)
                )
                drawCircle(color = accentColor, radius = 3.5f, center = Offset(w * 0.44f, h * 0.5f))
                drawLine(
                    color = dimColor,
                    start = Offset(w * 0.82f, h * 0.15f),
                    end = Offset(w * 0.62f, h * 0.62f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            PlayerStyle.VIDEO -> {
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(w * 0.06f, h * 0.12f),
                    size = Size(w * 0.88f, h * 0.76f),
                    cornerRadius = CornerRadius(5f, 5f),
                    style = Stroke(width = 2f)
                )
                drawCircle(color = accentColor, radius = 4.5f, center = Offset(w * 0.5f, h * 0.5f))
            }
            PlayerStyle.IPOD -> {
                drawRoundRect(
                    color = dimColor,
                    topLeft = Offset(w * 0.24f, h * 0.04f),
                    size = Size(w * 0.52f, h * 0.92f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = accentColor,
                    radius = h * 0.20f,
                    center = Offset(w * 0.5f, h * 0.68f),
                    style = Stroke(width = 2.5f)
                )
            }
            PlayerStyle.TIMELINE -> {
                drawLine(
                    color = dimColor,
                    start = Offset(w * 0.22f, h * 0.12f),
                    end = Offset(w * 0.22f, h * 0.88f),
                    strokeWidth = 2f
                )
                listOf(0.20f, 0.50f, 0.80f).forEachIndexed { idx, yFrac ->
                    drawCircle(
                        color = if (idx == 1) accentColor else dimColor,
                        radius = if (idx == 1) 4f else 2.5f,
                        center = Offset(w * 0.22f, h * yFrac)
                    )
                    drawLine(
                        color = if (idx == 1) accentColor else dimColor,
                        start = Offset(w * 0.36f, h * yFrac),
                        end = Offset(w * 0.85f, h * yFrac),
                        strokeWidth = if (idx == 1) 3f else 2f,
                        cap = StrokeCap.Round
                    )
                }
            }
            PlayerStyle.LYRICS -> {
                listOf(0.24f, 0.50f, 0.76f).forEachIndexed { idx, yFrac ->
                    drawLine(
                        color = if (idx == 1) accentColor else dimColor,
                        start = Offset(w * 0.14f, h * yFrac),
                        end = Offset(w * if (idx == 1) 0.90f else 0.70f, h * yFrac),
                        strokeWidth = if (idx == 1) 3.5f else 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
            PlayerStyle.BEDSIDE -> {
                drawRoundRect(
                    color = dimColor,
                    topLeft = Offset(w * 0.08f, h * 0.14f),
                    size = Size(w * 0.84f, h * 0.72f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(color = accentColor, radius = 3.5f, center = Offset(w * 0.5f, h * 0.46f))
                drawLine(
                    color = accentColor,
                    start = Offset(w * 0.24f, h * 0.70f),
                    end = Offset(w * 0.76f, h * 0.70f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            PlayerStyle.BEDSIDE_TILES -> {
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.45f),
                    topLeft = Offset(w * 0.06f, h * 0.12f),
                    size = Size(w * 0.40f, h * 0.76f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                drawRoundRect(
                    color = dimColor,
                    topLeft = Offset(w * 0.54f, h * 0.12f),
                    size = Size(w * 0.40f, h * 0.76f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
            }
        }
    }
}
