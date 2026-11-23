package com.onda.music.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.model.EqBand
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.EqPreset
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqEditorSheet(
    eqConfig: EqConfig,
    strings: SettingsStrings,
    onEqConfigChange: (EqConfig) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bands = remember(eqConfig.bands) {
        if (eqConfig.bands.size == 10) eqConfig.bands else EqConfig.defaultBands()
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = null,
                    tint = SettingsOledColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.eqTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = SettingsOledColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = EqConfig.builtInPresets.firstOrNull { it.id == eqConfig.activePresetId }?.name
                            ?: strings.eqCustomLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary
                    )
                }
                Switch(
                    checked = eqConfig.enabled,
                    onCheckedChange = { onEqConfigChange(eqConfig.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SettingsOledColors.TextPrimary,
                        checkedTrackColor = SettingsOledColors.CheckedTrack,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = SettingsOledColors.TextSecondary,
                        uncheckedTrackColor = SettingsOledColors.InactiveTrack,
                        uncheckedBorderColor = Color.Transparent
                    )
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

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EqConfig.builtInPresets, key = { it.id }) { preset ->
                    SettingsPillButton(
                        text = preset.name,
                        selected = eqConfig.activePresetId == preset.id,
                        onClick = { onSelectPreset(preset) }
                    )
                }
            }

            EqCurvePreview(
                bands = bands,
                enabled = eqConfig.enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(84.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bands.forEach { band ->
                    EqBandFader(
                        band = band,
                        enabled = eqConfig.enabled,
                        onGainChange = { newGain -> onBandGainChange(band.index, newGain) }
                    )
                }
            }

            SettingsMatteCard(
                backgroundColor = SettingsOledColors.IconBadge,
                cornerRadius = 24,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.eqPreampLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = SettingsOledColors.TextPrimary
                        )
                        Text(
                            text = formatDb(eqConfig.preampDb),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = SettingsOledColors.TextPrimary
                        )
                    }
                    Slider(
                        value = eqConfig.preampDb,
                        onValueChange = { raw ->
                            val snapped = round(raw * 2f) / 2f
                            onEqConfigChange(eqConfig.copy(preampDb = snapped.coerceIn(-12f, 12f)))
                        },
                        valueRange = -12f..12f,
                        colors = SliderDefaults.colors(
                            thumbColor = SettingsOledColors.TextPrimary,
                            activeTrackColor = SettingsOledColors.TextPrimary,
                            inactiveTrackColor = SettingsOledColors.InactiveTrack
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.crossfadeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = SettingsOledColors.TextPrimary
                        )
                        Text(
                            text = if (eqConfig.crossfadeSeconds == 0) strings.offLabel
                            else "${eqConfig.crossfadeSeconds}s",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = SettingsOledColors.TextPrimary
                        )
                    }
                    Slider(
                        value = eqConfig.crossfadeSeconds.toFloat(),
                        onValueChange = { raw ->
                            onEqConfigChange(eqConfig.copy(crossfadeSeconds = round(raw).toInt().coerceIn(0, 12)))
                        },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = SettingsOledColors.TextPrimary,
                            activeTrackColor = SettingsOledColors.TextPrimary,
                            inactiveTrackColor = SettingsOledColors.InactiveTrack
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val flat = EqConfig.builtInPresets.first()
                                onEqConfigChange(
                                    eqConfig.copy(
                                        activePresetId = flat.id,
                                        bands = EqConfig.defaultBands(flat.gainsDb),
                                        preampDb = 0f
                                    )
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        ) {
                            Text(text = strings.eqResetFlatLabel, color = SettingsOledColors.TextPrimary)
                        }
                    }
                }
            }

            SettingsMatteCard(
                backgroundColor = SettingsOledColors.IconBadge,
                cornerRadius = 24,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                SettingsToggleRow(
                    icon = Icons.Rounded.VolumeUp,
                    title = strings.normalizeTitle,
                    subtitle = strings.normalizeSubtitle,
                    checked = eqConfig.normalizeLoudness,
                    onCheckedChange = { onEqConfigChange(eqConfig.copy(normalizeLoudness = it)) },
                    iconBadgeColor = SettingsOledColors.MatteCard
                )
                SettingsRowDivider(color = SettingsOledColors.MatteCard)
                SettingsToggleRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = strings.gaplessTitle,
                    subtitle = strings.gaplessSubtitle,
                    checked = eqConfig.gaplessEnabled,
                    onCheckedChange = { onEqConfigChange(eqConfig.copy(gaplessEnabled = it)) },
                    iconBadgeColor = SettingsOledColors.MatteCard
                )
            }
        }
    }
}

@Composable
private fun EqCurvePreview(
    bands: List<EqBand>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (enabled) SettingsOledColors.TextPrimary else SettingsOledColors.TextMuted
    SettingsMatteCard(
        backgroundColor = SettingsOledColors.IconBadge,
        cornerRadius = 20,
        modifier = modifier
    ) {
        val curvePath = remember { Path() }
        val fillPath = remember { Path() }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val midY = size.height * 0.5f
            drawLine(
                color = SettingsOledColors.InactiveTrack,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.dp.toPx()
            )
            if (bands.isEmpty()) return@Canvas

            val stepX = if (bands.size > 1) size.width / (bands.size - 1) else size.width
            val points = bands.mapIndexed { idx, band ->
                val x = idx * stepX
                val normalized = ((12f - band.gainDb.coerceIn(-12f, 12f)) / 24f)
                val y = normalized * size.height
                Offset(x, y)
            }

            curvePath.reset()
            fillPath.reset()
            curvePath.moveTo(points.first().x, points.first().y)
            fillPath.moveTo(points.first().x, midY)
            fillPath.lineTo(points.first().x, points.first().y)

            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cx = (p0.x + p1.x) * 0.5f
                curvePath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }

            fillPath.lineTo(points.last().x, midY)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.24f),
                        Color.Transparent
                    )
                )
            )
            drawPath(
                path = curvePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun EqBandFader(
    band: EqBand,
    enabled: Boolean,
    onGainChange: (Float) -> Unit
) {
    val latestOnGainChange by rememberUpdatedState(onGainChange)
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    val activeColor = if (enabled) SettingsOledColors.TextPrimary else SettingsOledColors.TextMuted

    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SettingsOledColors.IconBadge)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = formatShortDb(band.gainDb),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.SemiBold
            ),
            color = if (enabled) SettingsOledColors.TextPrimary else SettingsOledColors.TextMuted,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(DockMetrics.MinTapTarget)
                .height(150.dp)
                .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1).toFloat() }
                .pointerInput(band.index) {
                    detectTapGestures(
                        onDoubleTap = { latestOnGainChange(0f) },
                        onTap = { offset ->
                            val fraction = 1f - (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            val gain = round(((fraction * 24f) - 12f) * 2f) / 2f
                            latestOnGainChange(gain.coerceIn(-12f, 12f))
                        }
                    )
                }
                .pointerInput(band.index) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val fraction = 1f - (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                        val gain = round(((fraction * 24f) - 12f) * 2f) / 2f
                        latestOnGainChange(gain.coerceIn(-12f, 12f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width * 0.5f
                val pad = 10.dp.toPx()
                val topY = pad
                val bottomY = (size.height - pad).coerceAtLeast(topY + 1f)
                val midY = (topY + bottomY) * 0.5f
                val usableHeight = bottomY - topY

                drawLine(
                    color = SettingsOledColors.InactiveTrack,
                    start = Offset(centerX, topY),
                    end = Offset(centerX, bottomY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val fraction = ((band.gainDb.coerceIn(-12f, 12f) + 12f) / 24f)
                val thumbY = bottomY - fraction * usableHeight

                drawLine(
                    color = activeColor,
                    start = Offset(centerX, midY),
                    end = Offset(centerX, thumbY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = activeColor.copy(alpha = 0.25f),
                    radius = 11.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
                drawCircle(
                    color = SettingsOledColors.TextPrimary,
                    radius = 6.5.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
            }
        }
        Text(
            text = formatFrequencyHz(band.frequencyHz),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = SettingsOledColors.TextSecondary,
            maxLines = 1
        )
    }
}
