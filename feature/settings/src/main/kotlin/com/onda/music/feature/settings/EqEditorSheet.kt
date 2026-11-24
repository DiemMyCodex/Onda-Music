package com.onda.music.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaSwitch
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.damageBoundary
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.AudioOutputDevice
import com.onda.music.core.model.AutoEqProfile
import com.onda.music.core.model.EqBand
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.EqPreset
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqEditorSheet(
    eqConfig: EqConfig,
    strings: SettingsStrings,
    onEqConfigChange: (EqConfig) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onDismiss: () -> Unit,
    activeOutputDevice: AudioOutputDevice = AudioOutputDevice(),
    autoEqQuery: String = "",
    autoEqResults: List<AutoEqProfile> = emptyList(),
    autoEqTotalCount: Int = 0,
    onAutoEqQueryChange: (String) -> Unit = {},
    onApplyAutoEqProfile: (AutoEqProfile) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bands = remember(eqConfig.bands) {
        if (eqConfig.bands.size == 10) eqConfig.bands else EqConfig.defaultBands()
    }
    val reducedMotion = OndaTheme.reducedMotion
    val scope = rememberCoroutineScope()
    var activeDragBand by remember { mutableIntStateOf(-1) }

    val animatedGains: List<Animatable<Float, AnimationVector1D>> = remember {
        List(10) { idx -> Animatable(bands.getOrElse(idx) { EqBand(idx, 1000) }.gainDb) }
    }

    bands.forEachIndexed { idx, band ->
        LaunchedEffect(band.gainDb, activeDragBand, reducedMotion) {
            if (activeDragBand == band.index || reducedMotion) {
                animatedGains[idx].snapTo(band.gainDb)
            } else {
                animatedGains[idx].animateTo(
                    targetValue = band.gainDb,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f)
                )
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
                    val activePresetTitle = remember(eqConfig.activePresetId, strings.eqCustomLabel) {
                        formatActiveEqPresetName(eqConfig.activePresetId, strings.eqCustomLabel)
                    }
                    Text(
                        text = activePresetTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OndaSwitch(
                    checked = eqConfig.enabled,
                    onCheckedChange = { onEqConfigChange(eqConfig.copy(enabled = it)) }
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
                bandCount = bands.size,
                gainProvider = { idx -> animatedGains.getOrNull(idx)?.value ?: 0f },
                activeBandIndex = activeDragBand,
                enabled = eqConfig.enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(92.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bands.forEachIndexed { idx, band ->
                    EqBandFader(
                        band = band,
                        animatedGainProvider = { animatedGains[idx].value },
                        enabled = eqConfig.enabled,
                        isDragging = activeDragBand == band.index,
                        onDragStateChange = { dragging ->
                            activeDragBand = if (dragging) band.index else if (activeDragBand == band.index) -1 else activeDragBand
                        },
                        onGainChange = { newGain ->
                            scope.launch { animatedGains[idx].snapTo(newGain) }
                            onBandGainChange(band.index, newGain)
                        }
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
                        val resetInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                                .pressBounce(resetInteraction, pressedScale = 0.96f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(SettingsOledColors.MatteCard)
                                .clickable(
                                    interactionSource = resetInteraction,
                                    indication = RoundedHighlight(cornerRadius = 999.dp),
                                    onClick = {
                                        val flat = EqConfig.builtInPresets.first()
                                        onEqConfigChange(
                                            eqConfig.copy(
                                                activePresetId = flat.id,
                                                bands = EqConfig.defaultBands(flat.gainsDb),
                                                preampDb = 0f
                                            )
                                        )
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.eqResetFlatLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = SettingsOledColors.TextPrimary
                            )
                        }
                    }
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SettingsOledColors.MatteCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = SettingsOledColors.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.eqAutoEqTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SettingsOledColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    strings.eqAutoEqSubtitleFormat,
                                    autoEqTotalCount,
                                    activeOutputDevice.displayName
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = SettingsOledColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedTextField(
                        value = autoEqQuery,
                        onValueChange = onAutoEqQueryChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = strings.eqAutoEqSearchPlaceholder,
                                color = SettingsOledColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = SettingsOledColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (autoEqQuery.isNotEmpty()) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(DockMetrics.MinTapTarget)
                                        .clip(CircleShape)
                                        .clickable(
                                            role = Role.Button,
                                            onClick = { onAutoEqQueryChange("") }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = strings.clearSearchLabel,
                                        tint = SettingsOledColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SettingsOledColors.TextPrimary,
                            unfocusedBorderColor = SettingsOledColors.InsetDivider,
                            focusedTextColor = SettingsOledColors.TextPrimary,
                            unfocusedTextColor = SettingsOledColors.TextPrimary,
                            cursorColor = SettingsOledColors.TextPrimary,
                            focusedContainerColor = SettingsOledColors.MatteCard,
                            unfocusedContainerColor = SettingsOledColors.MatteCard
                        )
                    )

                    val visibleProfiles = remember(autoEqResults, autoEqQuery) {
                        autoEqResults.take(if (autoEqQuery.isBlank()) 5 else 8)
                    }

                    if (visibleProfiles.isEmpty()) {
                        Text(
                            text = strings.eqAutoEqEmptyResults,
                            style = MaterialTheme.typography.bodySmall,
                            color = SettingsOledColors.TextMuted,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SettingsOledColors.MatteCard)
                        ) {
                            visibleProfiles.forEachIndexed { idx, profile ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        thickness = 0.75.dp,
                                        color = SettingsOledColors.InsetDivider
                                    )
                                }
                                val isActiveProfile = eqConfig.activePresetId == "${AutoEqProfile.AUTO_EQ_PREFIX}${profile.id}"
                                val formLabel = when (profile.formFactor) {
                                    "I" -> "IEM"
                                    "T" -> "TWS"
                                    "N" -> "On-Ear"
                                    else -> "Over-Ear"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clickable { onApplyAutoEqProfile(profile) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isActiveProfile) FontWeight.SemiBold else FontWeight.Medium,
                                            color = SettingsOledColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$formLabel · ${formatDb(profile.preampDb)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            color = SettingsOledColors.TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                    if (isActiveProfile) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = SettingsOledColors.TextPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
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

internal fun formatActiveEqPresetName(presetId: String, customFallback: String): String {
    if (presetId.startsWith(AutoEqProfile.AUTO_EQ_PREFIX)) {
        val slug = presetId.removePrefix(AutoEqProfile.AUTO_EQ_PREFIX)
        val pretty = slug.split('_').filter { it.isNotBlank() }.joinToString(" ") { part ->
            part.replaceFirstChar { ch -> ch.titlecase(Locale.US) }
        }.ifBlank { "AutoEq" }
        return "AutoEq · $pretty"
    }
    return EqConfig.builtInPresets.firstOrNull { it.id == presetId }?.name ?: customFallback
}

@Composable
private fun EqCurvePreview(
    bandCount: Int,
    gainProvider: (Int) -> Float,
    activeBandIndex: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (enabled) SettingsOledColors.TextPrimary else SettingsOledColors.TextMuted
    val reducedMotion = OndaTheme.reducedMotion
    val activePulse by animateFloatAsState(
        targetValue = if (activeBandIndex >= 0 && !reducedMotion) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.65f, stiffness = 480f),
        label = "EqCurveDragPulse"
    )

    SettingsMatteCard(
        backgroundColor = SettingsOledColors.IconBadge,
        cornerRadius = 20,
        modifier = modifier.damageBoundary()
    ) {
        val curvePath = remember { Path() }
        val fillPath = remember { Path() }
        val pointCache = remember(bandCount) { Array(bandCount.coerceAtLeast(1)) { Offset.Zero } }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            val midY = size.height * 0.5f
            listOf(0.25f, 0.5f, 0.75f).forEach { yFrac ->
                drawLine(
                    color = SettingsOledColors.InactiveTrack.copy(alpha = if (yFrac == 0.5f) 1f else 0.55f),
                    start = Offset(0f, size.height * yFrac),
                    end = Offset(size.width, size.height * yFrac),
                    strokeWidth = 1.dp.toPx()
                )
            }
            if (bandCount <= 0) return@Canvas

            val stepX = if (bandCount > 1) size.width / (bandCount - 1) else size.width
            for (idx in 0 until bandCount) {
                val gain = gainProvider(idx).coerceIn(-12f, 12f)
                val x = idx * stepX
                val y = ((12f - gain) / 24f) * size.height
                pointCache[idx] = Offset(x, y)
            }

            curvePath.reset()
            fillPath.reset()
            val first = pointCache[0]
            curvePath.moveTo(first.x, first.y)
            fillPath.moveTo(first.x, midY)
            fillPath.lineTo(first.x, first.y)

            for (i in 0 until bandCount - 1) {
                val p0 = pointCache[i]
                val p1 = pointCache[i + 1]
                val cx = (p0.x + p1.x) * 0.5f
                curvePath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }

            val last = pointCache[bandCount - 1]
            fillPath.lineTo(last.x, midY)
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

            if (activeBandIndex in 0 until bandCount) {
                val focusPoint = pointCache[activeBandIndex]
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(focusPoint.x, 0f),
                    end = Offset(focusPoint.x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f + 0.14f * activePulse),
                    radius = (7.dp + 5.dp * activePulse).toPx(),
                    center = focusPoint
                )
                drawCircle(
                    color = SettingsOledColors.TextPrimary,
                    radius = 4.dp.toPx(),
                    center = focusPoint
                )
            }
        }
    }
}

@Composable
private fun EqBandFader(
    band: EqBand,
    animatedGainProvider: () -> Float,
    enabled: Boolean,
    isDragging: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    onGainChange: (Float) -> Unit
) {
    val latestOnGainChange by rememberUpdatedState(onGainChange)
    val latestOnDragStateChange by rememberUpdatedState(onDragStateChange)
    val haptic = LocalHapticFeedback.current
    val reducedMotion = OndaTheme.reducedMotion
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    val activeColor = if (enabled) SettingsOledColors.TextPrimary else SettingsOledColors.TextMuted

    val dragPulse by animateFloatAsState(
        targetValue = if (isDragging && !reducedMotion) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.60f, stiffness = 500f),
        label = "EqBandDragPulse"
    )
    val containerColor = if (isDragging) SettingsOledColors.CheckedTrack.copy(alpha = 0.45f) else SettingsOledColors.IconBadge

    Column(
        modifier = Modifier
            .width(54.dp)
            .clip(SmoothCornerShape(16.dp))
            .background(containerColor)
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
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            latestOnGainChange(0f)
                        },
                        onTap = { offset ->
                            val fraction = 1f - (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            val gain = round(((fraction * 24f) - 12f) * 2f) / 2f
                            latestOnGainChange(gain.coerceIn(-12f, 12f))
                        }
                    )
                }
                .pointerInput(band.index) {
                    var lastSign = 0
                    detectVerticalDragGestures(
                        onDragStart = {
                            latestOnDragStateChange(true)
                            lastSign = if (band.gainDb > 0.05f) 1 else if (band.gainDb < -0.05f) -1 else 0
                        },
                        onDragEnd = { latestOnDragStateChange(false) },
                        onDragCancel = { latestOnDragStateChange(false) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val fraction = 1f - (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                            val gain = (round(((fraction * 24f) - 12f) * 2f) / 2f).coerceIn(-12f, 12f)
                            val newSign = if (gain > 0.05f) 1 else if (gain < -0.05f) -1 else 0
                            if (newSign == 0 && lastSign != 0 || (lastSign != 0 && newSign != lastSign)) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            lastSign = newSign
                            latestOnGainChange(gain)
                        }
                    )
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

                val displayGain = animatedGainProvider().coerceIn(-12f, 12f)
                val fraction = (displayGain + 12f) / 24f
                val thumbY = bottomY - fraction * usableHeight

                drawLine(
                    color = activeColor,
                    start = Offset(centerX, midY),
                    end = Offset(centerX, thumbY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                if (dragPulse > 0.01f || abs(displayGain) > 0.05f) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.22f + 0.16f * dragPulse),
                        radius = (11.dp + 7.dp * dragPulse).toPx(),
                        center = Offset(centerX, thumbY)
                    )
                }
                if (dragPulse > 0.05f) {
                    drawCircle(
                        color = SettingsOledColors.TextPrimary.copy(alpha = 0.65f * dragPulse),
                        radius = (13.dp + 7.dp * dragPulse).toPx(),
                        center = Offset(centerX, thumbY),
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }
                drawCircle(
                    color = SettingsOledColors.TextPrimary,
                    radius = (6.5.dp + 1.5.dp * dragPulse).toPx(),
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
