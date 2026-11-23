package com.onda.music.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = SettingsOledColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.transferSheetTitle,
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

            if (transferState.isRunning || transferState.progress != null) {
                val progress = transferState.progress
                val animatedFraction by animateFloatAsState(
                    targetValue = (progress?.fraction ?: 0.1f).coerceIn(0f, 1f),
                    label = "TransferProgress"
                )
                SettingsMatteCard(
                    backgroundColor = SettingsOledColors.IconBadge,
                    cornerRadius = 20
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
                                color = SettingsOledColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (progress != null && progress.totalCount > 0) {
                                Text(
                                    text = "${progress.processedCount}/${progress.totalCount}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = SettingsOledColors.TextPrimary
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = SettingsOledColors.TextPrimary,
                            trackColor = SettingsOledColors.InactiveTrack
                        )
                    }
                }
            }

            transferState.errorMessage?.let { errorText ->
                SettingsMatteCard(
                    backgroundColor = SettingsOledColors.IconBadge,
                    cornerRadius = 20
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
                            tint = SettingsOledColors.ErrorTint
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = SettingsOledColors.ErrorTint,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearStatus,
                            modifier = Modifier.size(DockMetrics.MinTapTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.closeLabel,
                                tint = SettingsOledColors.TextSecondary
                            )
                        }
                    }
                }
            }

            transferState.lastSummary?.let { summary ->
                TransferSummaryCard(summary = summary, strings = strings)
            }

            SettingsMatteCard(
                backgroundColor = SettingsOledColors.IconBadge,
                cornerRadius = 24
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
                            tint = SettingsOledColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.momiSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = SettingsOledColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = strings.momiSectionDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary
                    )

                    Button(
                        onClick = { momiPickerLauncher.launch(zipMimeTypes) },
                        enabled = !transferState.isRunning && !transferState.isPeeking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SettingsOledColors.InactiveTrack,
                            contentColor = SettingsOledColors.TextPrimary
                        ),
                        elevation = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                        shape = RoundedCornerShape(16.dp)
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
                                containerColor = SettingsOledColors.TextPrimary,
                                contentColor = SettingsOledColors.CanvasBlack
                            ),
                            elevation = null,
                            shape = RoundedCornerShape(999.dp),
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

            SettingsMatteCard(
                backgroundColor = SettingsOledColors.IconBadge,
                cornerRadius = 24
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
                            tint = SettingsOledColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.ondaBackupSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = SettingsOledColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = strings.ondaBackupSectionDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary
                    )

                    Button(
                        onClick = {
                            val date = LocalDate.now().toString()
                            ondaExportLauncher.launch("onda-backup-$date.zip")
                        },
                        enabled = !transferState.isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SettingsOledColors.TextPrimary,
                            contentColor = SettingsOledColors.CanvasBlack
                        ),
                        elevation = null,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                    ) {
                        Text(
                            text = strings.exportOndaButton,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = { ondaRestorePickerLauncher.launch(zipMimeTypes) },
                        enabled = !transferState.isRunning && !transferState.isPeeking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SettingsOledColors.InactiveTrack,
                            contentColor = SettingsOledColors.TextPrimary
                        ),
                        elevation = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                        shape = RoundedCornerShape(16.dp)
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SettingsOledColors.MatteCard)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${ondaManifest.format} v${ondaManifest.version}",
                                style = MaterialTheme.typography.labelLarge,
                                color = SettingsOledColors.TextPrimary
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
                                containerColor = SettingsOledColors.TextPrimary,
                                contentColor = SettingsOledColors.CanvasBlack
                            ),
                            elevation = null,
                            shape = RoundedCornerShape(999.dp),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SettingsOledColors.MatteCard)
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
                color = SettingsOledColors.TextPrimary
            )
            if (peek.totalBytes > 0L) {
                Text(
                    text = formatByteSize(peek.totalBytes),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = SettingsOledColors.TextSecondary
                )
            }
        }
        peek.username?.takeIf { it.isNotBlank() }?.let { username ->
            Text(
                text = username,
                style = MaterialTheme.typography.titleSmall,
                color = SettingsOledColors.TextPrimary
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
            .clip(RoundedCornerShape(12.dp))
            .background(SettingsOledColors.IconBadge)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SettingsOledColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = SettingsOledColors.TextPrimary,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = strings.mergePolicyLabel,
            style = MaterialTheme.typography.labelLarge,
            color = SettingsOledColors.TextSecondary
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) SettingsOledColors.InactiveTrack
                        else SettingsOledColors.MatteCard
                    )
                    .clickable { onSelect(policy) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect(policy) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = SettingsOledColors.TextPrimary,
                        unselectedColor = SettingsOledColors.TextSecondary
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SettingsOledColors.TextPrimary
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary
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
    SettingsMatteCard(
        backgroundColor = SettingsOledColors.IconBadge,
        cornerRadius = 20
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
                    tint = SettingsOledColors.SuccessTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${summary.format.name} · ${summary.importedTracks} ${strings.previewTracksLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsOledColors.TextPrimary
                )
            }
            Text(
                text = "${strings.previewPlaylistsLabel}: ${summary.importedPlaylists} · " +
                    "${strings.previewPlaysLabel}: ${summary.importedPlayEvents} · " +
                    "${strings.previewEqLabel}: ${if (summary.importedEqConfig) strings.includedLabel else strings.notSetLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsOledColors.TextSecondary
            )
        }
    }
}
