package com.onda.music.feature.player.sheets

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaSwitch
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.AutoEqProfile
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.PlaybackState
import com.onda.music.core.model.Track
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.findActiveLyricsLineIndex
import com.onda.music.feature.player.formatDurationMs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SheetObsidian = Color(0xFF0E0E10)
private val SheetCardSurface = Color(0xFF161619)
private val SheetElevatedSurface = Color(0xFF1F1F24)
private val SheetDivider = Color(0xFF242429)
private val SheetMutedText = Color(0xFF9E9E9E)

private fun monoTelemetryStyle(
    fontSizeSp: Float = 12f,
    fontWeight: FontWeight = FontWeight.Medium
): TextStyle {
    return TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSizeSp.sp,
        fontWeight = fontWeight,
        fontFeatureSettings = "tnum"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaPlayerActionSheet(
    track: Track?,
    liked: Boolean,
    downloadItem: DownloadItem?,
    playbackState: PlaybackState,
    lyricsOffsetMs: Long,
    hapticsEnabled: Boolean,
    onOpenShare: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenStreamInfo: () -> Unit,
    onOpenSpeedSheet: () -> Unit,
    onOpenLyricsOffset: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onRefreshLyrics: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val stream = playbackState.activeStream

    val qualityPillText = remember(stream, track) {
        when {
            stream != null && stream.bitrateKbps > 0 ->
                "${stream.codec.uppercase(Locale.US)} ${stream.bitrateKbps}K"
            stream != null && stream.qualityLabel.isNotBlank() ->
                stream.qualityLabel.uppercase(Locale.US)
            track != null -> track.source.name
            else -> "STANDBY"
        }
    }

    val downloadStatus = downloadItem?.status
    val downloadTileTitle = when (downloadStatus) {
        DownloadStatus.COMPLETED -> "Saved"
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED ->
            "Saving ${((downloadItem?.progress ?: 0f) * 100f).roundToInt().coerceIn(0, 100)}%"
        else -> "Download"
    }
    val downloadTileIcon = when (downloadStatus) {
        DownloadStatus.COMPLETED -> Icons.Rounded.CheckCircle
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Icons.Rounded.Downloading
        else -> Icons.Rounded.Download
    }

    val sleepRemainingMs = playbackState.sleepTimerRemainingMs
    val sleepSubtitle = when {
        playbackState.sleepTimerEndOfTrack -> "END OF TRACK"
        sleepRemainingMs != null && sleepRemainingMs > 0L ->
            formatDurationMs(sleepRemainingMs)
        else -> "OFF"
    }
    val signedOffset = if (lyricsOffsetMs >= 0L) "+$lyricsOffsetMs ms" else "$lyricsOffsetMs ms"
    val speedPitchText = String.format(
        Locale.US,
        "%.2fx / P %.2fx",
        playbackState.playbackSpeed,
        playbackState.playbackPitch
    )
    val signalSubtitle = if (stream != null && stream.bitrateKbps > 0) {
        "${stream.codec.uppercase(Locale.US)} ${stream.bitrateKbps} kbps"
    } else {
        track?.source?.name ?: "IDLE"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetObsidian,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtworkImage(
                        artworkUrl = track?.artworkUrl,
                        contentDescription = track?.title,
                        shape = SmoothCornerShape(12.dp),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = track?.title ?: "No Active Track",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track?.artist ?: "Onda Acoustic Core",
                            style = MaterialTheme.typography.bodySmall,
                            color = SheetMutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(SmoothCornerShape(8.dp))
                                .background(SheetElevatedSurface)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = qualityPillText,
                                style = monoTelemetryStyle(10f, FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onToggleLike()
                        },
                        modifier = Modifier.defaultMinSize(
                            minWidth = DockMetrics.MinTapTarget,
                            minHeight = DockMetrics.MinTapTarget
                        )
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (liked) "Remove from favorites" else "Add to favorites",
                            tint = if (liked) GlassTokens.ErrorCoral else Color.White
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SheetActionTile(
                        title = "Soundcard",
                        subtitle = "Visual card",
                        icon = Icons.Rounded.Share,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDismiss()
                            onOpenShare()
                        }
                    )
                    SheetActionTile(
                        title = "Onda Studio",
                        subtitle = "Stage builder",
                        icon = Icons.Rounded.Style,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDismiss()
                            onOpenStudio()
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SheetActionTile(
                        title = downloadTileTitle,
                        subtitle = if (downloadItem?.status == DownloadStatus.COMPLETED) "Offline ready" else "Local cache",
                        icon = downloadTileIcon,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onDownloadClick()
                            onDismiss()
                        }
                    )
                    SheetActionTile(
                        title = "Up Next",
                        subtitle = "${playbackState.queue.size} queued",
                        icon = Icons.Rounded.QueueMusic,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDismiss()
                            onOpenQueue()
                        }
                    )
                }
            }

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SheetActionRow(
                        title = "Signal Path Telemetry",
                        trailingBadge = signalSubtitle,
                        icon = Icons.Rounded.GraphicEq,
                        onClick = {
                            onDismiss()
                            onOpenStreamInfo()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Speed & Pitch",
                        trailingBadge = speedPitchText,
                        icon = Icons.Rounded.Speed,
                        onClick = {
                            onDismiss()
                            onOpenSpeedSheet()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Lyrics Sync & Offset",
                        trailingBadge = signedOffset,
                        icon = Icons.Rounded.Lyrics,
                        onClick = {
                            onDismiss()
                            onOpenLyricsOffset()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Sleep Timer",
                        trailingBadge = sleepSubtitle,
                        icon = Icons.Rounded.Bedtime,
                        onClick = {
                            onDismiss()
                            onOpenSleepTimer()
                        }
                    )
                    if (track != null) {
                        SheetRowDivider()
                        SheetActionRow(
                            title = "Play Next",
                            trailingBadge = null,
                            icon = Icons.Rounded.PlaylistPlay,
                            onClick = {
                                onPlayNext(track)
                                onDismiss()
                            }
                        )
                        SheetRowDivider()
                        SheetActionRow(
                            title = "Add to Queue",
                            trailingBadge = null,
                            icon = Icons.Rounded.PlaylistAdd,
                            onClick = {
                                onAddToQueue(track)
                                onDismiss()
                            }
                        )
                    }
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Refetch Lyrics",
                        trailingBadge = null,
                        icon = Icons.Rounded.Refresh,
                        onClick = {
                            onRefreshLyrics()
                            onDismiss()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Audio & App Settings",
                        trailingBadge = null,
                        icon = Icons.Rounded.Settings,
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    }
}

private data class SignalNodeData(
    val stage: String,
    val title: String,
    val details: List<Pair<String, String>>,
    val active: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaStreamTelemetrySheet(
    track: Track?,
    playbackState: PlaybackState,
    eqConfig: EqConfig,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val audioManager = remember(context) {
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()
    }

    val minVol = remember(audioManager) {
        runCatching { audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0 }.getOrDefault(0)
    }
    val maxVol = remember(audioManager) {
        runCatching { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
            .getOrDefault(15)
            .coerceAtLeast(minVol + 1)
    }
    var currentVol by remember(audioManager) {
        mutableIntStateOf(
            runCatching { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0 }
                .getOrDefault(0)
                .coerceIn(minVol, maxVol)
        )
    }
    var outputRoute by remember(audioManager) {
        mutableStateOf(resolveAudioOutputRoute(audioManager))
    }

    DisposableEffect(context, audioManager, minVol, maxVol) {
        outputRoute = resolveAudioOutputRoute(audioManager)
        currentVol = runCatching {
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: currentVol
        }.getOrDefault(currentVol).coerceIn(minVol, maxVol)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                currentVol = runCatching {
                    audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: currentVol
                }.getOrDefault(currentVol).coerceIn(minVol, maxVol)
                outputRoute = resolveAudioOutputRoute(audioManager)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    val stream = playbackState.activeStream
    val signal = playbackState.audioSignal
    val activePresetName = remember(eqConfig.activePresetId) {
        if (eqConfig.activePresetId.startsWith(AutoEqProfile.AUTO_EQ_PREFIX)) {
            val slug = eqConfig.activePresetId.removePrefix(AutoEqProfile.AUTO_EQ_PREFIX)
            val label = slug.split('_').filter { it.isNotBlank() }.joinToString(" ") { part ->
                part.replaceFirstChar { ch -> ch.titlecase(Locale.US) }
            }.ifBlank { "AutoEq" }
            "AutoEq · $label"
        } else {
            EqConfig.builtInPresets.firstOrNull { it.id == eqConfig.activePresetId }?.name
                ?: eqConfig.activePresetId.replace('_', ' ').uppercase(Locale.US)
        }
    }

    val nodes = remember(track, playbackState, signal, eqConfig, activePresetName, outputRoute) {
        val preampSign = if (eqConfig.preampDb >= 0f) "+" else ""
        val decodedCodec = signal?.codec?.takeIf { it.isNotBlank() }
            ?: stream?.codec?.uppercase(Locale.US)
            ?: "PCM / PASS"
        val sampleRateLabel = signal?.formatSampleRateLabel().orEmpty()
        val resolutionLabel = when {
            signal != null && signal.bitDepth > 0 && sampleRateLabel.isNotEmpty() ->
                "${signal.bitDepth}-bit / $sampleRateLabel"
            sampleRateLabel.isNotEmpty() -> sampleRateLabel
            signal != null && signal.bitDepth > 0 -> "${signal.bitDepth}-bit"
            else -> "--"
        }
        val effectiveBitrate = signal?.bitrateKbps?.takeIf { it > 0 }
            ?: stream?.bitrateKbps?.takeIf { it > 0 }
            ?: 0
        val channelsLabel = when (signal?.channelCount ?: 0) {
            1 -> "Mono (1.0)"
            2 -> "Stereo (2.0)"
            in 3..16 -> "${signal?.channelCount} ch"
            else -> "--"
        }
        val effectiveRouteLabel = signal?.formatRouteLabel() ?: outputRoute.first
        val effectiveDeviceLabel = signal?.outputDeviceName?.takeIf { it.isNotBlank() } ?: outputRoute.second
        listOf(
            SignalNodeData(
                stage = "SOURCE",
                title = track?.source?.name ?: "UNBOUND",
                details = listOf(
                    "TRACK" to (track?.title ?: "None"),
                    "STATE" to playbackState.status.name,
                    "QUALITY" to (stream?.qualityLabel ?: if (signal?.isLossless == true) "LOSSLESS" else "AUTO")
                ),
                active = track != null
            ),
            SignalNodeData(
                stage = "DECODER",
                title = decodedCodec,
                details = listOf(
                    "RESOLUTION" to resolutionLabel,
                    "BITRATE" to if (effectiveBitrate > 0) "$effectiveBitrate kbps" else "--",
                    "CHANNELS" to channelsLabel,
                    "MIME" to (stream?.mimeType ?: "--")
                ),
                active = signal != null || stream != null
            ),
            SignalNodeData(
                stage = "DSP ENGINE",
                title = if (eqConfig.enabled) "ACTIVE ($activePresetName)" else "BYPASS ($activePresetName)",
                details = listOf(
                    "PIPELINE" to "32-bit Float Parametric EQ",
                    "PREAMP" to "$preampSign${String.format(Locale.US, "%.1f dB", eqConfig.preampDb)}",
                    "CROSSFADE" to if (eqConfig.crossfadeSeconds > 0) "${eqConfig.crossfadeSeconds}s" else "OFF",
                    "GAPLESS" to if (eqConfig.gaplessEnabled) "ON" else "OFF",
                    "RATE / PITCH" to String.format(
                        Locale.US,
                        "%.2fx / %.2fx",
                        playbackState.playbackSpeed,
                        playbackState.playbackPitch
                    )
                ),
                active = eqConfig.enabled ||
                    eqConfig.crossfadeSeconds > 0 ||
                    eqConfig.gaplessEnabled ||
                    abs(playbackState.playbackSpeed - 1f) > 0.01f
            ),
            SignalNodeData(
                stage = "OUTPUT",
                title = effectiveRouteLabel,
                details = listOf(
                    "ROUTE" to effectiveRouteLabel,
                    "DEVICE" to effectiveDeviceLabel
                ),
                active = true
            )
        )
    }

    val volumePct = (((currentVol - minVol).toFloat() / (maxVol - minVol).toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetObsidian,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeaderBar(
                title = "Signal Path Telemetry",
                subtitle = "ACOUSTIC CHAIN INSPECTION",
                icon = Icons.Rounded.GraphicEq,
                onDismiss = onDismiss
            )

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    nodes.forEachIndexed { index, node ->
                        SignalChainNodeRow(
                            node = node,
                            hasTopConnector = index > 0,
                            hasBottomConnector = index < nodes.lastIndex
                        )
                    }
                }
            }

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "MEDIA STREAM GAIN",
                                style = monoTelemetryStyle(11f, FontWeight.SemiBold),
                                color = SheetMutedText
                            )
                        }
                        Text(
                            text = "$volumePct% ($currentVol/$maxVol)",
                            style = monoTelemetryStyle(12f, FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Slider(
                        value = currentVol.toFloat(),
                        onValueChange = { raw ->
                            val target = raw.roundToInt().coerceIn(minVol, maxVol)
                            if (target != currentVol) {
                                currentVol = target
                                runCatching {
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                }
                            }
                        },
                        valueRange = minVol.toFloat()..maxVol.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF2D2D33)
                        )
                    )
                }
            }

            val labInteraction = remember { MutableInteractionSource() }
            val labShape = SmoothCornerShape(18.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    .pressBounce(labInteraction, pressedScale = 0.97f)
                    .clip(labShape)
                    .background(Color.White)
                    .clickable(
                        interactionSource = labInteraction,
                        indication = RoundedHighlight(cornerRadius = 18.dp, color = Color.Black.copy(alpha = 0.1f)),
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = SheetObsidian,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Equalizer & Audio Lab",
                    style = MaterialTheme.typography.labelLarge,
                    color = SheetObsidian
                )
            }
        }
    }
}

@Composable
private fun SignalChainNodeRow(
    node: SignalNodeData,
    hasTopConnector: Boolean,
    hasBottomConnector: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
            ) {
                val cx = size.width * 0.5f
                val dotRadius = 5.dp.toPx()
                val dotCenterY = 10.dp.toPx()
                val lineWidth = 2.dp.toPx()
                val connectorColor = Color(0xFF34343B)

                if (hasTopConnector) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, (dotCenterY - dotRadius - 2.dp.toPx()).coerceAtLeast(0f)),
                        strokeWidth = lineWidth
                    )
                }
                if (hasBottomConnector) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(cx, dotCenterY + dotRadius + 2.dp.toPx()),
                        end = Offset(cx, size.height),
                        strokeWidth = lineWidth
                    )
                }
                if (node.active) {
                    drawCircle(
                        color = Color.White,
                        radius = dotRadius,
                        center = Offset(cx, dotCenterY)
                    )
                } else {
                    drawCircle(
                        color = Color(0xFF707078),
                        radius = dotRadius,
                        center = Offset(cx, dotCenterY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (hasBottomConnector) 14.dp else 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.stage,
                    style = monoTelemetryStyle(10f, FontWeight.Bold),
                    color = SheetMutedText
                )
                Text(
                    text = node.title,
                    style = monoTelemetryStyle(12f, FontWeight.Bold),
                    color = if (node.active) Color.White else SheetMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothCornerShape(12.dp))
                    .background(SheetElevatedSurface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                node.details.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            style = monoTelemetryStyle(10f, FontWeight.Medium),
                            color = SheetMutedText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = value,
                            style = monoTelemetryStyle(11f, FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OndaPlaybackSpeedSheet(
    speed: Float,
    pitch: Float,
    hapticsEnabled: Boolean,
    persistGlobally: Boolean = true,
    languageCode: String = "en",
    onPersistGloballyChange: (Boolean) -> Unit = {},
    onSetSpeedAndPitch: (Float, Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val isVi = languageCode.startsWith("vi", ignoreCase = true)

    var localSpeed by remember { mutableFloatStateOf(quantizeSpeedStep(speed)) }
    var localPitch by remember { mutableFloatStateOf(quantizeSpeedStep(pitch)) }
    var isDraggingSpeed by remember { mutableStateOf(false) }
    var isDraggingPitch by remember { mutableStateOf(false) }

    LaunchedEffect(speed, isDraggingSpeed) {
        if (!isDraggingSpeed) {
            localSpeed = quantizeSpeedStep(speed)
        }
    }
    LaunchedEffect(pitch, isDraggingPitch, isDraggingSpeed) {
        if (!isDraggingPitch && !isDraggingSpeed) {
            localPitch = quantizeSpeedStep(pitch)
        }
    }

    var linkPitchToSpeed by remember {
        mutableStateOf(
            abs(localSpeed - 1.0f) >= 0.01f && abs(localSpeed - localPitch) < 0.01f
        )
    }

    val speedPresets = remember {
        listOf(0.75f, 0.90f, 1.00f, 1.15f, 1.25f, 1.50f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetObsidian,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeaderBar(
                title = "Speed & Pitch",
                subtitle = "TIME-STRETCH & RESAMPLING",
                icon = Icons.Rounded.Speed,
                onDismiss = onDismiss
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = SheetCardSurface,
                    shape = SmoothCornerShape(20.dp),
                    border = BorderStroke(1.dp, SheetDivider)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "TEMPO SPEED",
                            style = monoTelemetryStyle(10f, FontWeight.SemiBold),
                            color = SheetMutedText
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", localSpeed),
                            style = monoTelemetryStyle(26f, FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = SheetCardSurface,
                    shape = SmoothCornerShape(20.dp),
                    border = BorderStroke(1.dp, SheetDivider)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (linkPitchToSpeed) "PITCH (LINKED)" else "PITCH SHIFT",
                            style = monoTelemetryStyle(10f, FontWeight.SemiBold),
                            color = SheetMutedText
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", localPitch),
                            style = monoTelemetryStyle(26f, FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable {
                            val next = !linkPitchToSpeed
                            linkPitchToSpeed = next
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (next) {
                                localPitch = localSpeed
                                onSetSpeedAndPitch(localSpeed, localSpeed)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Link Pitch to Speed",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Text(
                            text = "Tracks pitch lock proportionally with speed",
                            style = MaterialTheme.typography.bodySmall,
                            color = SheetMutedText
                        )
                    }
                    OndaSwitch(
                        checked = linkPitchToSpeed,
                        onCheckedChange = { checked ->
                            linkPitchToSpeed = checked
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (checked) {
                                localPitch = localSpeed
                                onSetSpeedAndPitch(localSpeed, localSpeed)
                            }
                        }
                    )
                }
            }

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isVi) "Phạm vi lưu tốc độ & cao độ" else "Persistence Scope",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = if (persistGlobally) {
                            if (isVi) "Giữ nguyên tốc độ và cao độ qua các lần mở lại app"
                            else "Keep speed and pitch across app restarts"
                        } else {
                            if (isVi) "Chỉ áp dụng cho phiên hiện tại, đặt lại 1.00x khi khởi động lại"
                            else "Apply to current session only, reset to 1.00x on restart"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SheetMutedText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SheetPillChip(
                                label = if (isVi) "Phiên hiện tại" else "Per-Session",
                                selected = !persistGlobally,
                                fillWidth = true,
                                onClick = {
                                    if (hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onPersistGloballyChange(false)
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SheetPillChip(
                                label = if (isVi) "Toàn cục" else "Global",
                                selected = persistGlobally,
                                fillWidth = true,
                                onClick = {
                                    if (hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onPersistGloballyChange(true)
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEED (0.50x - 2.00x)",
                            style = monoTelemetryStyle(11f, FontWeight.SemiBold),
                            color = SheetMutedText
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", localSpeed),
                            style = monoTelemetryStyle(12f, FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Slider(
                        value = localSpeed,
                        onValueChange = { raw ->
                            isDraggingSpeed = true
                            val snapped = quantizeSpeedStep(raw)
                            if (abs(snapped - localSpeed) >= 0.01f) {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(
                                        if (abs(snapped - 1.0f) < 0.01f) {
                                            HapticFeedbackType.LongPress
                                        } else {
                                            HapticFeedbackType.TextHandleMove
                                        }
                                    )
                                }
                                localSpeed = snapped
                                val targetPitch = if (linkPitchToSpeed) snapped else localPitch
                                localPitch = targetPitch
                                onSetSpeedAndPitch(snapped, targetPitch)
                            }
                        },
                        onValueChangeFinished = { isDraggingSpeed = false },
                        valueRange = 0.50f..2.00f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF2D2D33),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PITCH (0.50x - 2.00x)",
                            style = monoTelemetryStyle(11f, FontWeight.SemiBold),
                            color = SheetMutedText
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", localPitch),
                            style = monoTelemetryStyle(12f, FontWeight.Bold),
                            color = if (linkPitchToSpeed) SheetMutedText else Color.White
                        )
                    }
                    Slider(
                        value = localPitch,
                        enabled = !linkPitchToSpeed,
                        onValueChange = { raw ->
                            isDraggingPitch = true
                            val snapped = quantizeSpeedStep(raw)
                            if (abs(snapped - localPitch) >= 0.01f) {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(
                                        if (abs(snapped - 1.0f) < 0.01f) {
                                            HapticFeedbackType.LongPress
                                        } else {
                                            HapticFeedbackType.TextHandleMove
                                        }
                                    )
                                }
                                localPitch = snapped
                                onSetSpeedAndPitch(localSpeed, snapped)
                            }
                        },
                        onValueChangeFinished = { isDraggingPitch = false },
                        valueRange = 0.50f..2.00f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF2D2D33),
                            disabledThumbColor = Color(0xFF6B6B72),
                            disabledActiveTrackColor = Color(0xFF525258),
                            disabledInactiveTrackColor = Color(0xFF242429),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speedPresets.forEach { preset ->
                    val selected = abs(localSpeed - preset) < 0.01f &&
                        (!linkPitchToSpeed || abs(localPitch - preset) < 0.01f)
                    SheetPillChip(
                        label = String.format(Locale.US, "%.2fx", preset),
                        selected = selected,
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            localSpeed = preset
                            val targetPitch = if (linkPitchToSpeed) preset else localPitch
                            localPitch = targetPitch
                            onSetSpeedAndPitch(preset, targetPitch)
                        }
                    )
                }

                SheetPillChip(
                    label = "Slowed",
                    selected = abs(localSpeed - 0.85f) < 0.01f && abs(localPitch - 0.85f) < 0.01f,
                    onClick = {
                        linkPitchToSpeed = true
                        localSpeed = 0.85f
                        localPitch = 0.85f
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSetSpeedAndPitch(0.85f, 0.85f)
                    }
                )

                SheetPillChip(
                    label = "Nightcore",
                    selected = abs(localSpeed - 1.25f) < 0.01f && abs(localPitch - 1.25f) < 0.01f,
                    onClick = {
                        linkPitchToSpeed = true
                        localSpeed = 1.25f
                        localPitch = 1.25f
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSetSpeedAndPitch(1.25f, 1.25f)
                    }
                )
            }

            SheetPillChip(
                label = "Reset 1.00x",
                selected = false,
                fillWidth = true,
                onClick = {
                    linkPitchToSpeed = false
                    localSpeed = 1.00f
                    localPitch = 1.00f
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onReset()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaLyricsOffsetSheet(
    lyrics: LyricsDocument?,
    positionMs: Long,
    offsetMs: Long,
    hapticsEnabled: Boolean,
    onAdjustOffset: (Long) -> Unit,
    onSetOffset: (Long) -> Unit,
    onRefreshLyrics: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    var localOffsetMs by remember { mutableLongStateOf(offsetMs.coerceIn(-10_000L, 10_000L)) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    LaunchedEffect(offsetMs, isDraggingSlider) {
        if (!isDraggingSlider) {
            localOffsetMs = offsetMs.coerceIn(-10_000L, 10_000L)
        }
    }

    val effectivePosMs = (positionMs + localOffsetMs).coerceAtLeast(0L)
    val activeLineIndex = remember(lyrics, effectivePosMs) {
        if (lyrics == null) {
            -1
        } else {
            findActiveLyricsLineIndex(lyrics.lines, effectivePosMs, lyrics.isSynced)
        }
    }
    val previewLine = lyrics?.lines?.getOrNull(activeLineIndex)?.text?.takeIf { it.isNotBlank() }
        ?: when {
            lyrics == null || lyrics.lines.isEmpty() -> "No lyrics loaded for current track"
            !lyrics.isSynced -> lyrics.lines.firstOrNull()?.text ?: "Static lyrics available"
            else -> "Waiting for timed cue..."
        }
    val formattedOffset = if (localOffsetMs >= 0L) "+$localOffsetMs ms" else "$localOffsetMs ms"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetObsidian,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeaderBar(
                title = "Lyrics Sync & Offset",
                subtitle = "TIMING CALIBRATION",
                icon = Icons.Rounded.Lyrics,
                onDismiss = onDismiss
            )

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lyrics?.isSynced == true) "LIVE SYNC PREVIEW" else "LYRICS PREVIEW",
                            style = monoTelemetryStyle(10f, FontWeight.SemiBold),
                            color = SheetMutedText
                        )
                        Text(
                            text = formatDurationMs(effectivePosMs),
                            style = monoTelemetryStyle(11f, FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Text(
                        text = previewLine,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedOffset,
                        style = monoTelemetryStyle(30f, FontWeight.Bold),
                        color = Color.White
                    )

                    Slider(
                        value = localOffsetMs.toFloat(),
                        onValueChange = { raw ->
                            isDraggingSlider = true
                            val snapped = ((raw / 50f).roundToInt() * 50L).coerceIn(-10_000L, 10_000L)
                            if (snapped != localOffsetMs) {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(
                                        if (snapped == 0L) {
                                            HapticFeedbackType.LongPress
                                        } else {
                                            HapticFeedbackType.TextHandleMove
                                        }
                                    )
                                }
                                localOffsetMs = snapped
                                onSetOffset(snapped)
                            }
                        },
                        onValueChangeFinished = { isDraggingSlider = false },
                        valueRange = -10_000f..10_000f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF2D2D33)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "-10.0s",
                            style = monoTelemetryStyle(10f),
                            color = SheetMutedText
                        )
                        Text(
                            text = "0 ms",
                            style = monoTelemetryStyle(10f),
                            color = SheetMutedText
                        )
                        Text(
                            text = "+10.0s",
                            style = monoTelemetryStyle(10f),
                            color = SheetMutedText
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "-500ms" to -500L,
                    "-50ms" to -50L,
                    "+50ms" to 50L,
                    "+500ms" to 500L
                ).forEach { (label, delta) ->
                    SheetPillChip(
                        label = label,
                        selected = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            localOffsetMs = (localOffsetMs + delta).coerceIn(-10_000L, 10_000L)
                            onAdjustOffset(delta)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SheetPillChip(
                    label = "Reset 0 ms",
                    selected = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        localOffsetMs = 0L
                        onSetOffset(0L)
                    }
                )
                SheetPillChip(
                    label = "Refetch Lyrics",
                    selected = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRefreshLyrics()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaSleepTimerSheet(
    remainingMs: Long?,
    endOfTrack: Boolean,
    isFading: Boolean = false,
    onSelectDurationMs: (Long) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onAdjustDurationMs: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasCountdown = remainingMs != null && remainingMs > 0L
    val isActive = hasCountdown || endOfTrack
    val presetsMinutes = remember { listOf(5, 10, 15, 25, 30, 45, 60, 90) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetObsidian,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeaderBar(
                title = "Sleep Timer",
                subtitle = "AUTO SHUTDOWN SCHEDULER",
                icon = Icons.Rounded.Bedtime,
                onDismiss = onDismiss
            )

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = when {
                            isFading -> "FADING OUT"
                            endOfTrack -> "ACTIVE MODE"
                            hasCountdown -> "REMAINING"
                            else -> "TIMER STANDBY"
                        },
                        style = monoTelemetryStyle(10f, FontWeight.SemiBold),
                        color = if (isFading) Color.White else SheetMutedText
                    )

                    if (endOfTrack) {
                        Text(
                            text = "END OF TRACK",
                            style = monoTelemetryStyle(24f, FontWeight.Bold),
                            color = Color.White
                        )
                    } else if (hasCountdown) {
                        OdometerText(
                            text = formatDurationMs(remainingMs ?: 0L),
                            style = monoTelemetryStyle(32f, FontWeight.Bold),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "00:00",
                            style = monoTelemetryStyle(32f, FontWeight.Bold),
                            color = SheetMutedText
                        )
                    }

                    Text(
                        text = if (isFading) {
                            "Gently lowering volume before pause"
                        } else {
                            "15s acoustic fade-out before pause"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFading) Color.White else SheetMutedText
                    )

                    if (hasCountdown) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SheetPillChip(
                                label = "-1m",
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { onAdjustDurationMs(-60_000L) }
                            )
                            SheetPillChip(
                                label = "+1m",
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { onAdjustDurationMs(60_000L) }
                            )
                        }
                    }

                    if (isActive) {
                        val cancelInteraction = remember { MutableInteractionSource() }
                        val cancelShape = SmoothCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                .pressBounce(cancelInteraction, pressedScale = 0.97f)
                                .clip(cancelShape)
                                .background(SheetElevatedSurface)
                                .border(1.dp, GlassTokens.ErrorCoral.copy(alpha = 0.45f), cancelShape)
                                .clickable(
                                    interactionSource = cancelInteraction,
                                    indication = RoundedHighlight(cornerRadius = 16.dp),
                                    onClick = {
                                        onCancelTimer()
                                        onDismiss()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel Timer",
                                style = MaterialTheme.typography.labelLarge,
                                color = GlassTokens.ErrorCoral
                            )
                        }
                    }
                }
            }

            val eotInteraction = remember { MutableInteractionSource() }
            val eotShape = SmoothCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .pressBounce(eotInteraction, pressedScale = 0.98f)
                    .clip(eotShape)
                    .background(if (endOfTrack) Color.White else SheetCardSurface)
                    .border(1.dp, if (endOfTrack) Color.White else SheetDivider, eotShape)
                    .clickable(
                        interactionSource = eotInteraction,
                        indication = RoundedHighlight(cornerRadius = 20.dp),
                        onClick = {
                            onSelectEndOfTrack()
                            onDismiss()
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Stop After Current Track",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (endOfTrack) SheetObsidian else Color.White
                    )
                    Text(
                        text = "Pauses smoothly once current audio completes",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (endOfTrack) Color(0xFF4B4B52) else SheetMutedText
                    )
                }
                if (endOfTrack) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Active",
                        tint = SheetObsidian,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFF52525B), CircleShape)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rowIdx in 0 until 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (colIdx in 0 until 4) {
                            val minutes = presetsMinutes[rowIdx * 4 + colIdx]
                            val durationMs = minutes * 60_000L
                            SheetPillChip(
                                label = "${minutes}m",
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onSelectDurationMs(durationMs)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaSongMenuSheet(
    track: Track,
    liked: Boolean,
    canRemoveFromQueue: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onStreamInfo: () -> Unit,
    onRemoveFromQueue: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetObsidian,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtworkImage(
                        artworkUrl = track.artworkUrl,
                        contentDescription = track.title,
                        shape = SmoothCornerShape(12.dp),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = SheetMutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatDurationMs(track.durationMs)} / ${track.source.name}",
                            style = monoTelemetryStyle(10f, FontWeight.Medium),
                            color = SheetMutedText,
                            maxLines = 1
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
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (liked) "Remove favorite" else "Favorite",
                            tint = if (liked) GlassTokens.ErrorCoral else Color.White
                        )
                    }
                }
            }

            Surface(
                color = SheetCardSurface,
                shape = SmoothCornerShape(20.dp),
                border = BorderStroke(1.dp, SheetDivider)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SheetActionRow(
                        title = "Play Next",
                        trailingBadge = null,
                        icon = Icons.Rounded.PlaylistPlay,
                        onClick = {
                            onPlayNext()
                            onDismiss()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Add to Queue",
                        trailingBadge = null,
                        icon = Icons.Rounded.PlaylistAdd,
                        onClick = {
                            onAddToQueue()
                            onDismiss()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = if (liked) "Remove from Favorites" else "Add to Favorites",
                        trailingBadge = null,
                        icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = {
                            onToggleLike()
                            onDismiss()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Download",
                        trailingBadge = null,
                        icon = Icons.Rounded.Download,
                        onClick = {
                            onDownload()
                            onDismiss()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Soundcard",
                        trailingBadge = null,
                        icon = Icons.Rounded.Share,
                        onClick = {
                            onDismiss()
                            onShare()
                        }
                    )
                    SheetRowDivider()
                    SheetActionRow(
                        title = "Signal Path Telemetry",
                        trailingBadge = track.source.name,
                        icon = Icons.Rounded.GraphicEq,
                        onClick = {
                            onDismiss()
                            onStreamInfo()
                        }
                    )
                    if (canRemoveFromQueue && onRemoveFromQueue != null) {
                        SheetRowDivider()
                        SheetActionRow(
                            title = "Remove from Queue",
                            trailingBadge = null,
                            icon = Icons.Rounded.DeleteOutline,
                            tint = GlassTokens.ErrorCoral,
                            onClick = {
                                onRemoveFromQueue()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeaderBar(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onDismiss: () -> Unit
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
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(SmoothCornerShape(12.dp))
                    .background(SheetCardSurface)
                    .border(1.dp, SheetDivider, SmoothCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = monoTelemetryStyle(10f, FontWeight.Medium),
                    color = SheetMutedText
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
                contentDescription = "Close sheet",
                tint = SheetMutedText
            )
        }
    }
}

@Composable
private fun SheetActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = SmoothCornerShape(20.dp)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .pressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .background(SheetCardSurface)
            .border(1.dp, SheetDivider, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 20.dp),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SmoothCornerShape(11.dp))
                .background(SheetElevatedSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = monoTelemetryStyle(10f),
                color = SheetMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    title: String,
    trailingBadge: String?,
    icon: ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .pressBounce(interactionSource, pressedScale = 0.985f)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 0.dp),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = tint,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!trailingBadge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(SmoothCornerShape(8.dp))
                    .background(SheetElevatedSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = trailingBadge,
                    style = monoTelemetryStyle(11f, FontWeight.SemiBold),
                    color = SheetMutedText,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF6B6B73),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SheetRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(SheetDivider)
    )
}

@Composable
private fun SheetPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = SmoothCornerShape(16.dp)
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .pressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .background(if (selected) Color.White else SheetCardSurface)
            .border(1.dp, if (selected) Color.White else SheetDivider, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 16.dp),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = monoTelemetryStyle(
                fontSizeSp = 12f,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (selected) SheetObsidian else Color.White,
            maxLines = 1
        )
    }
}

private fun quantizeSpeedStep(value: Float): Float {
    return ((value * 20f).roundToInt() / 20f).coerceIn(0.50f, 2.00f)
}

private fun resolveAudioOutputRoute(audioManager: AudioManager?): Pair<String, String> {
    if (audioManager == null) return "Built-in Speaker" to "System Default"
    val outputs = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrNull() ?: return "Built-in Speaker" to "System Default"

    var bestPriority = -1
    var bestRoute = "Built-in Speaker"
    var bestDevice = "Internal Transducer"

    for (device in outputs) {
        val type = device.type
        val (priority, routeLabel) = classifyAudioDevice(type)
        if (priority > bestPriority) {
            bestPriority = priority
            bestRoute = routeLabel
            val product = device.productName?.toString()?.trim()
            bestDevice = if (!product.isNullOrBlank()) product else routeLabel
        }
    }
    return bestRoute to bestDevice
}

private fun classifyAudioDevice(deviceType: Int): Pair<Int, String> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER
        ) {
            return 3 to "Bluetooth Audio"
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (deviceType == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
            return 3 to "Bluetooth Audio"
        }
    }
    return when (deviceType) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> 4 to "USB Audio DAC"

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID -> 3 to "Bluetooth Audio"

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE -> 2 to "Wired Headset"

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 1 to "Built-in Speaker"

        else -> 0 to "Built-in Speaker"
    }
}
