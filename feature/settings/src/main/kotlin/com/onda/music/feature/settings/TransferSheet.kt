package com.onda.music.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.model.MergePolicy
import com.onda.music.core.model.TransferSummary
import com.onda.music.core.transfer.MomiArchivePeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    transferState: TransferUiState,
    strings: SettingsStrings,
    onPickMomiZip: (Uri) -> Unit,
    onMomiPolicyChange: (MergePolicy) -> Unit,
    onImportMomi: (Uri, MergePolicy) -> Unit,
    onExportOndaBackup: (Uri) -> Unit,
    onPickOndaZip: (Uri) -> Unit,
    onOndaPolicyChange: (MergePolicy) -> Unit,
    onImportOnda: (Uri, MergePolicy) -> Unit,
    onClearStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = OndaTheme.colors
    val zipMimeTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

    val momiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickMomiZip(uri)
    }

    val ondaExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onExportOndaBackup(uri)
    }

    val ondaRestorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickOndaZip(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassTokens.SmokedGlassElevated,
        contentColor = colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.textTertiary) }
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
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.transferSheetTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
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
                        tint = colors.textSecondary
                    )
                }
            }

            if (transferState.isRunning || transferState.progress != null) {
                val progress = transferState.progress
                val animatedFraction by animateFloatAsState(
                    targetValue = (progress?.fraction ?: 0.1f).coerceIn(0f, 1f),
                    label = "TransferProgress"
                )
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = GlassElevation.SUBTLE,
                    shape = RoundedCornerShape(GlassTokens.RadiusMd)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = progress?.stage ?: strings.importingLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (progress != null && progress.totalCount > 0) {
                                Text(
                                    text = "${progress.processedCount}/${progress.totalCount}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = colors.accent
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(GlassTokens.RadiusPill)),
                            color = colors.accent,
                            trackColor = GlassTokens.GlassHighlight.copy(alpha = 0.16f)
                        )
                    }
                }
            }

            transferState.errorMessage?.let { errorText ->
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = GlassElevation.SUBTLE,
                    shape = RoundedCornerShape(GlassTokens.RadiusMd),
                    accentTint = colors.error
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = colors.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearStatus,
                            modifier = Modifier.size(DockMetrics.MinTapTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.closeLabel,
                                tint = colors.textSecondary
                            )
                        }
                    }
                }
            }

            transferState.lastSummary?.let { summary ->
                TransferSummaryCard(summary = summary, strings = strings)
            }

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                elevation = GlassElevation.CARD,
                shape = RoundedCornerShape(GlassTokens.RadiusLg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = colors.accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.momiSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = strings.momiSectionDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )

                    OutlinedButton(
                        onClick = { momiPickerLauncher.launch(zipMimeTypes) },
                        enabled = !transferState.isRunning && !transferState.isPeeking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                        shape = RoundedCornerShape(GlassTokens.RadiusMd)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = transferState.selectedMomiFileName ?: strings.chooseMomiZipButton,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val momiPeek = transferState.momiPeek
                    val momiUri = transferState.selectedMomiUri
                    if (momiPeek != null && momiUri != null) {
                        MomiPeekSummaryCard(
                            peek = momiPeek,
                            hasAudioLab = transferState.momiHasAudioLab,
                            strings = strings
                        )

                        MergePolicySelector(
                            selected = transferState.momiMergePolicy,
                            strings = strings,
                            onSelect = onMomiPolicyChange
                        )

                        Button(
                            onClick = { onImportMomi(momiUri, transferState.momiMergePolicy) },
                            enabled = !transferState.isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = GlassTokens.AmoledBlack
                            ),
                            shape = RoundedCornerShape(GlassTokens.RadiusMd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        ) {
                            Text(
                                text = strings.importMomiButton,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                elevation = GlassElevation.CARD,
                shape = RoundedCornerShape(GlassTokens.RadiusLg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = colors.accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.ondaBackupSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = strings.ondaBackupSectionDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )

                    Button(
                        onClick = {
                            val date = LocalDate.now().toString()
                            ondaExportLauncher.launch("onda-backup-$date.zip")
                        },
                        enabled = !transferState.isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = GlassTokens.AmoledBlack
                        ),
                        shape = RoundedCornerShape(GlassTokens.RadiusMd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    ) {
                        Text(
                            text = strings.exportOndaButton,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = { ondaRestorePickerLauncher.launch(zipMimeTypes) },
                        enabled = !transferState.isRunning && !transferState.isPeeking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                        shape = RoundedCornerShape(GlassTokens.RadiusMd)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = transferState.selectedOndaFileName ?: strings.chooseOndaZipButton,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val ondaManifest = transferState.ondaManifest
                    val ondaUri = transferState.selectedOndaUri
                    if (ondaManifest != null && ondaUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(GlassTokens.RadiusSm))
                                .background(GlassTokens.ObsidianSurface.copy(alpha = 0.72f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${ondaManifest.format} v${ondaManifest.version}",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accent
                            )
                        }

                        MergePolicySelector(
                            selected = transferState.ondaMergePolicy,
                            strings = strings,
                            onSelect = onOndaPolicyChange
                        )

                        Button(
                            onClick = { onImportOnda(ondaUri, transferState.ondaMergePolicy) },
                            enabled = !transferState.isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentSoft,
                                contentColor = GlassTokens.AmoledBlack
                            ),
                            shape = RoundedCornerShape(GlassTokens.RadiusMd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                        ) {
                            Text(
                                text = strings.restoreOndaButton,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MomiPeekSummaryCard(
    peek: MomiArchivePeek,
    hasAudioLab: Boolean,
    strings: SettingsStrings
) {
    val colors = OndaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassTokens.RadiusMd))
            .background(GlassTokens.ObsidianSurface.copy(alpha = 0.72f))
            .border(1.dp, GlassTokens.SpecularIce, RoundedCornerShape(GlassTokens.RadiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${peek.manifest.format} v${peek.manifest.version}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent
            )
            if (peek.totalBytes > 0L) {
                Text(
                    text = formatByteSize(peek.totalBytes),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = colors.textSecondary
                )
            }
        }
        peek.username?.takeIf { it.isNotBlank() }?.let { username ->
            Text(
                text = username,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreviewStatChip(
                label = strings.previewTracksLabel,
                value = peek.songCount.toString(),
                modifier = Modifier.weight(1f)
            )
            PreviewStatChip(
                label = strings.previewPlaylistsLabel,
                value = peek.playlistCount.toString(),
                modifier = Modifier.weight(1f)
            )
            PreviewStatChip(
                label = strings.previewPlaysLabel,
                value = peek.playEventCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreviewStatChip(
                label = strings.previewEqLabel,
                value = if (hasAudioLab) strings.includedLabel else strings.notSetLabel,
                modifier = Modifier.weight(1f)
            )
            PreviewStatChip(
                label = strings.previewStyleLabel,
                value = peek.playerStyle?.label ?: strings.notSetLabel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PreviewStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(GlassTokens.RadiusSm))
            .background(GlassTokens.GlassHighlight.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = GlassTokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MergePolicySelector(
    selected: MergePolicy,
    strings: SettingsStrings,
    onSelect: (MergePolicy) -> Unit
) {
    val colors = OndaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = strings.mergePolicyLabel,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary
        )
        listOf(
            Triple(MergePolicy.IDEMPOTENT_UPSERT, strings.mergeUpsertTitle, strings.mergeUpsertDesc),
            Triple(MergePolicy.REPLACE_EXISTING, strings.mergeReplaceTitle, strings.mergeReplaceDesc)
        ).forEach { (policy, title, desc) ->
            val isSelected = selected == policy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    .clip(RoundedCornerShape(GlassTokens.RadiusSm))
                    .background(
                        if (isSelected) colors.accent.copy(alpha = 0.12f)
                        else GlassTokens.ObsidianSurface.copy(alpha = 0.45f)
                    )
                    .border(
                        1.dp,
                        if (isSelected) colors.accent else GlassTokens.SpecularEdgeDim,
                        RoundedCornerShape(GlassTokens.RadiusSm)
                    )
                    .clickable { onSelect(policy) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect(policy) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colors.accent,
                        unselectedColor = colors.textSecondary
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferSummaryCard(
    summary: TransferSummary,
    strings: SettingsStrings
) {
    val colors = OndaTheme.colors
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        elevation = GlassElevation.SUBTLE,
        shape = RoundedCornerShape(GlassTokens.RadiusMd),
        accentTint = colors.success
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${summary.format.name} · ${summary.importedTracks} ${strings.previewTracksLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }
            Text(
                text = "${strings.previewPlaylistsLabel}: ${summary.importedPlaylists} · " +
                    "${strings.previewPlaysLabel}: ${summary.importedPlayEvents} · " +
                    "${strings.previewEqLabel}: ${if (summary.importedEqConfig) strings.includedLabel else strings.notSetLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}
