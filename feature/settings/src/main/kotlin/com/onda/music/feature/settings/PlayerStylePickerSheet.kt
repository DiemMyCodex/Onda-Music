package com.onda.music.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.damageBoundary
import com.onda.music.core.design.pressBounce
import com.onda.music.core.design.springPress3D
import com.onda.music.core.model.PlayerStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val rows = remember { PlayerStyle.entries.chunked(2) }
    val reducedMotion = OndaTheme.reducedMotion

    val previewPhase: Float = if (reducedMotion) {
        0.35f
    } else {
        val transition = rememberInfiniteTransition(label = "StylePreviewPhase")
        val anim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "StylePreviewLoop"
        )
        anim
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
                                phaseProvider = { previewPhase },
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
    phaseProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileShape = SmoothCornerShape(20.dp)
    val previewShape = SmoothCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor = if (selected) SettingsOledColors.TextPrimary else SettingsOledColors.IconBadge
    val titleColor = if (selected) SettingsOledColors.CanvasBlack else SettingsOledColors.TextPrimary
    val stageBg = if (selected) Color(0xFF101010) else Color(0xFF0A0A0A)
    val accent = Color.White
    val dim = Color(0xFF6B6B6B)

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 148.dp)
            .springPress3D(maxTiltDegrees = 5f, pressedScale = 0.97f)
            .pressBounce(interactionSource, pressedScale = 0.96f)
            .clip(tileShape)
            .background(bgColor)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, Color.White, tileShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 20.dp),
                onClick = onClick
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(previewShape)
                .background(stageBg)
                .damageBoundary()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawStylePreviewStage(
                    style = style,
                    phase = phaseProvider(),
                    accent = accent,
                    dim = dim
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(SettingsOledColors.CanvasBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = SettingsOledColors.TextPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawStylePreviewStage(
    style: PlayerStyle,
    phase: Float,
    accent: Color,
    dim: Color
) {
    val w = size.width
    val h = size.height
    val pulse = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
    val waveSweep = sin(phase * 2f * PI.toFloat())

    when (style) {
        PlayerStyle.FLAGSHIP -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f + 0.08f * pulse), Color.Transparent),
                    center = Offset(w * (0.4f + 0.2f * waveSweep), h * 0.34f),
                    radius = w * 0.72f
                )
            )
            val artSize = minOf(w * 0.52f, h * 0.50f)
            val artLeft = (w - artSize) * 0.5f
            val artTop = h * 0.11f
            drawRoundRect(
                color = accent.copy(alpha = 0.22f),
                topLeft = Offset(artLeft, artTop),
                size = Size(artSize, artSize),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.45f),
                topLeft = Offset(artLeft, artTop),
                size = Size(artSize, artSize),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
            val barY = h * 0.73f
            drawLine(
                color = dim.copy(alpha = 0.45f),
                start = Offset(w * 0.16f, barY),
                end = Offset(w * 0.84f, barY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = accent,
                start = Offset(w * 0.16f, barY),
                end = Offset(w * (0.34f + 0.38f * pulse), barY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            listOf(0.32f, 0.50f, 0.68f).forEachIndexed { idx, xFrac ->
                drawCircle(
                    color = if (idx == 1) accent else dim,
                    radius = if (idx == 1) 4.5.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(w * xFrac, h * 0.87f)
                )
            }
        }
        PlayerStyle.CLASSIC -> {
            val artSize = minOf(w * 0.46f, h * 0.44f)
            val scale = 0.96f + 0.04f * pulse
            val scaledSize = artSize * scale
            drawRoundRect(
                color = dim.copy(alpha = 0.55f),
                topLeft = Offset((w - scaledSize) * 0.5f, h * 0.10f),
                size = Size(scaledSize, scaledSize),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawLine(
                color = accent,
                start = Offset(w * 0.24f, h * 0.63f),
                end = Offset(w * 0.76f, h * 0.63f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            val sliderY = h * 0.75f
            val dotX = w * (0.24f + 0.50f * pulse)
            drawLine(
                color = dim.copy(alpha = 0.5f),
                start = Offset(w * 0.18f, sliderY),
                end = Offset(w * 0.82f, sliderY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = accent,
                start = Offset(w * 0.18f, sliderY),
                end = Offset(dotX, sliderY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(dotX, sliderY))
            drawCircle(color = accent, radius = 5.dp.toPx(), center = Offset(w * 0.5f, h * 0.88f))
        }
        PlayerStyle.POSTER -> {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.26f),
                    0.6f to accent.copy(alpha = 0.10f),
                    1f to Color.Transparent
                )
            )
            val arcPath = Path().apply {
                moveTo(0f, h * 0.56f)
                quadraticTo(w * 0.52f, h * 0.48f, w, h * 0.34f)
            }
            drawPath(
                path = arcPath,
                color = dim.copy(alpha = 0.55f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            val activeEndT = 0.35f + 0.45f * pulse
            val activeArc = Path().apply {
                moveTo(0f, h * 0.56f)
                val steps = 12
                for (i in 1..steps) {
                    val t = activeEndT * i / steps
                    val u = 1f - t
                    val x = 2f * u * t * (w * 0.52f) + t * t * w
                    val y = u * u * (h * 0.56f) + 2f * u * t * (h * 0.48f) + t * t * (h * 0.34f)
                    lineTo(x, y)
                }
            }
            drawPath(
                path = activeArc,
                color = accent,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawLine(
                color = accent,
                start = Offset(w * 0.10f, h * 0.69f),
                end = Offset(w * 0.58f, h * 0.69f),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = dim.copy(alpha = 0.7f),
                start = Offset(w * 0.10f, h * 0.80f),
                end = Offset(w * 0.46f, h * 0.80f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            repeat(4) { i ->
                val pillY = h * (0.44f + i * 0.125f)
                drawRoundRect(
                    color = accent.copy(alpha = if (i == 2) 0.45f else 0.22f),
                    topLeft = Offset(w * 0.72f, pillY),
                    size = Size(w * 0.20f, h * 0.085f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        PlayerStyle.TURNTABLE -> {
            val center = Offset(w * 0.46f, h * 0.50f)
            val radius = minOf(w, h) * 0.34f
            rotate(degrees = phase * 360f, pivot = center) {
                drawCircle(
                    color = accent.copy(alpha = 0.22f),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = accent.copy(alpha = 0.75f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = dim.copy(alpha = 0.5f),
                    radius = radius * 0.66f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawLine(
                    color = accent.copy(alpha = 0.65f),
                    start = Offset(center.x + radius * 0.35f, center.y),
                    end = Offset(center.x + radius * 0.85f, center.y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = accent, radius = 4.dp.toPx(), center = center)
            }
            val armSweep = -4f + 4f * pulse
            rotate(degrees = armSweep, pivot = Offset(w * 0.82f, h * 0.16f)) {
                drawLine(
                    color = accent,
                    start = Offset(w * 0.82f, h * 0.16f),
                    end = Offset(w * 0.66f, h * 0.62f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        PlayerStyle.VIDEO -> {
            val frameW = w * 0.82f
            val frameH = h * 0.50f
            val frameLeft = (w - frameW) * 0.5f
            val frameTop = h * 0.14f
            drawRoundRect(
                color = accent.copy(alpha = 0.14f + 0.08f * pulse),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.75f),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )
            val cx = w * 0.5f
            val cy = frameTop + frameH * 0.5f
            val triSize = 7.dp.toPx()
            val playPath = Path().apply {
                moveTo(cx - triSize * 0.45f, cy - triSize * 0.6f)
                lineTo(cx + triSize * 0.75f, cy)
                lineTo(cx - triSize * 0.45f, cy + triSize * 0.6f)
                close()
            }
            drawPath(path = playPath, color = accent)
            drawLine(
                color = dim.copy(alpha = 0.5f),
                start = Offset(frameLeft, h * 0.78f),
                end = Offset(frameLeft + frameW, h * 0.78f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = accent,
                start = Offset(frameLeft, h * 0.78f),
                end = Offset(frameLeft + frameW * (0.25f + 0.55f * pulse), h * 0.78f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        PlayerStyle.IPOD -> {
            val bodyW = w * 0.42f
            val bodyH = h * 0.84f
            val bodyLeft = (w - bodyW) * 0.5f
            val bodyTop = h * 0.08f
            drawRoundRect(
                color = dim.copy(alpha = 0.30f),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.25f),
                topLeft = Offset(bodyLeft + bodyW * 0.12f, bodyTop + bodyH * 0.10f),
                size = Size(bodyW * 0.76f, bodyH * 0.34f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            val wheelCenter = Offset(w * 0.5f, bodyTop + bodyH * 0.70f)
            val wheelRadius = bodyW * 0.30f
            drawCircle(
                color = accent.copy(alpha = 0.85f),
                radius = wheelRadius,
                center = wheelCenter,
                style = Stroke(width = 3.dp.toPx())
            )
            val dotAngle = phase * 2f * PI.toFloat()
            drawCircle(
                color = accent,
                radius = 2.2.dp.toPx(),
                center = Offset(
                    wheelCenter.x + cos(dotAngle) * wheelRadius,
                    wheelCenter.y + sin(dotAngle) * wheelRadius
                )
            )
            drawCircle(
                color = dim,
                radius = wheelRadius * 0.34f,
                center = wheelCenter
            )
        }
        PlayerStyle.TIMELINE -> {
            val spineX = w * 0.22f
            drawLine(
                color = dim.copy(alpha = 0.55f),
                start = Offset(spineX, h * 0.14f),
                end = Offset(spineX, h * 0.86f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            val activeIdx = ((phase * 3f).toInt()) % 3
            listOf(0.24f, 0.50f, 0.76f).forEachIndexed { idx, yFrac ->
                val isActive = idx == activeIdx
                drawCircle(
                    color = if (isActive) accent else dim,
                    radius = if (isActive) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(spineX, h * yFrac)
                )
                drawRoundRect(
                    color = if (isActive) accent.copy(alpha = 0.9f) else dim.copy(alpha = 0.45f),
                    topLeft = Offset(w * 0.34f, h * yFrac - 5.dp.toPx()),
                    size = Size(w * if (isActive) 0.50f else 0.36f, 10.dp.toPx()),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
            }
        }
        PlayerStyle.LYRICS -> {
            val activeIdx = ((phase * 3f).toInt()) % 3
            listOf(0.25f, 0.50f, 0.75f).forEachIndexed { idx, yFrac ->
                val isActive = idx == activeIdx
                val barW = if (isActive) w * 0.72f else w * 0.50f
                val stroke = if (isActive) 5.dp.toPx() else 3.dp.toPx()
                drawLine(
                    color = if (isActive) accent else dim.copy(alpha = 0.45f),
                    start = Offset(w * 0.14f, h * yFrac),
                    end = Offset(w * 0.14f + barW, h * yFrac),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
        PlayerStyle.BEDSIDE -> {
            drawRoundRect(
                color = dim.copy(alpha = 0.45f),
                topLeft = Offset(w * 0.12f, h * 0.16f),
                size = Size(w * 0.76f, h * 0.68f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )
            val clockAlpha = 0.75f + 0.25f * pulse
            drawLine(
                color = accent.copy(alpha = clockAlpha),
                start = Offset(w * 0.28f, h * 0.42f),
                end = Offset(w * 0.46f, h * 0.42f),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = accent.copy(alpha = pulse),
                radius = 1.8.dp.toPx(),
                center = Offset(w * 0.50f, h * 0.39f)
            )
            drawCircle(
                color = accent.copy(alpha = pulse),
                radius = 1.8.dp.toPx(),
                center = Offset(w * 0.50f, h * 0.45f)
            )
            drawLine(
                color = accent.copy(alpha = clockAlpha),
                start = Offset(w * 0.54f, h * 0.42f),
                end = Offset(w * 0.72f, h * 0.42f),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = dim,
                start = Offset(w * 0.26f, h * 0.68f),
                end = Offset(w * 0.74f, h * 0.68f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        PlayerStyle.BEDSIDE_TILES -> {
            drawRoundRect(
                color = accent.copy(alpha = 0.24f),
                topLeft = Offset(w * 0.10f, h * 0.16f),
                size = Size(w * 0.38f, h * 0.68f),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = dim.copy(alpha = 0.28f),
                topLeft = Offset(w * 0.53f, h * 0.16f),
                size = Size(w * 0.37f, h * 0.31f),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = dim.copy(alpha = 0.28f),
                topLeft = Offset(w * 0.53f, h * 0.53f),
                size = Size(w * 0.37f, h * 0.31f),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            repeat(4) { barIdx ->
                val barPhase = 0.4f + 0.6f * absSin(phase * 2f * PI.toFloat() + barIdx * 0.9f)
                val bx = w * (0.60f + barIdx * 0.07f)
                val barBaseY = h * 0.78f
                val barHeight = h * 0.18f * barPhase
                drawLine(
                    color = accent,
                    start = Offset(bx, barBaseY),
                    end = Offset(bx, barBaseY - barHeight),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun absSin(radians: Float): Float {
    val v = sin(radians)
    return if (v < 0f) -v else v
}
