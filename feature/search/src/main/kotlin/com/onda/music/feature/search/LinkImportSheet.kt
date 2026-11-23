package com.onda.music.feature.search

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.LinkProvider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LinkImportSheet(
    linkInput: String,
    detectedProvider: LinkProvider?,
    progress: LinkImportProgress,
    onLinkInputChange: (String) -> Unit,
    onStartImport: () -> Unit,
    onReset: () -> Unit,
    onPlayImported: () -> Unit,
    onInspectImported: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassTokens.ObsidianSurface,
        contentColor = OndaTheme.colors.textPrimary,
        scrimColor = GlassTokens.SmokedGlassScrim
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = OndaTheme.colors.accent
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Import Playlist Link",
                            style = MaterialTheme.typography.headlineMedium,
                            color = OndaTheme.colors.textPrimary
                        )
                        Text(
                            text = "Matches tracks to YT Music & Monochrome FLAC",
                            style = MaterialTheme.typography.bodySmall,
                            color = OndaTheme.colors.textSecondary
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
                        contentDescription = "Close",
                        tint = OndaTheme.colors.textSecondary
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinkProvider.entries.forEach { provider ->
                    val highlighted = provider == detectedProvider
                    val pillShape = RoundedCornerShape(GlassTokens.RadiusPill)
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(
                                color = if (highlighted) {
                                    GlassTokens.IconBadgeSurface
                                } else {
                                    GlassTokens.MatteCard
                                },
                                shape = pillShape
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = SearchLogic.providerLabel(provider),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (highlighted) GlassTokens.TextPrimary else GlassTokens.TextSecondary
                        )
                    }
                }
            }

            val inputShape = RoundedCornerShape(GlassTokens.RadiusMd)
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp),
                elevation = GlassElevation.CARD,
                shape = inputShape,
                accentTint = OndaTheme.colors.accentSoft
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (linkInput.isEmpty()) {
                            Text(
                                text = "https://open.spotify.com/playlist/...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OndaTheme.colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = linkInput,
                            onValueChange = onLinkInputChange,
                            enabled = !progress.isBusy,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = OndaTheme.colors.textPrimary
                            ),
                            cursorBrush = SolidColor(OndaTheme.colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onStartImport() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (linkInput.isNotEmpty() && !progress.isBusy) {
                        IconButton(
                            onClick = onReset,
                            modifier = Modifier.defaultMinSize(
                                minWidth = DockMetrics.MinTapTarget,
                                minHeight = DockMetrics.MinTapTarget
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear link",
                                tint = OndaTheme.colors.textSecondary
                            )
                        }
                    } else if (!progress.isBusy) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (pasted.isNotBlank()) {
                                    onLinkInputChange(pasted.trim())
                                }
                            },
                            modifier = Modifier.defaultMinSize(
                                minWidth = DockMetrics.MinTapTarget,
                                minHeight = DockMetrics.MinTapTarget
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Paste link",
                                tint = OndaTheme.colors.accent
                            )
                        }
                    }
                }
            }

            when (progress.stage) {
                LinkImportStage.IDLE -> {
                    Button(
                        onClick = onStartImport,
                        enabled = linkInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OndaTheme.colors.accent,
                            contentColor = GlassTokens.AmoledBlack,
                            disabledContainerColor = GlassTokens.SmokedGlassBase,
                            disabledContentColor = OndaTheme.colors.textTertiary
                        ),
                        shape = RoundedCornerShape(GlassTokens.RadiusPill),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                    ) {
                        Text(
                            text = "Fetch & Save Playlist",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                LinkImportStage.FETCHING,
                LinkImportStage.MATCHING,
                LinkImportStage.SAVING -> {
                    LinkImportProgressCard(progress = progress)
                }
                LinkImportStage.SUCCESS -> {
                    LinkImportSuccessCard(
                        progress = progress,
                        onPlay = onPlayImported,
                        onInspect = onInspectImported,
                        onImportAnother = onReset
                    )
                }
                LinkImportStage.ERROR -> {
                    LinkImportErrorCard(
                        message = progress.errorMessage ?: "Import failed.",
                        onRetry = onStartImport
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkImportProgressCard(progress: LinkImportProgress) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .smokedGlass(shape = shape, elevation = GlassElevation.CARD)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtworkThumbnail(
                artworkUrl = progress.artworkUrl,
                placeholderIcon = Icons.Rounded.QueueMusic,
                size = 48.dp,
                cornerRadius = GlassTokens.RadiusSm
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.collectionTitle ?: "Reading link...",
                    style = MaterialTheme.typography.titleSmall,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusLabel = when (progress.stage) {
                    LinkImportStage.FETCHING -> "Fetching playlist metadata"
                    LinkImportStage.MATCHING -> "Matching ${progress.processedCount} / ${progress.totalCount} (${progress.matchedCount} matched)"
                    LinkImportStage.SAVING -> "Saving playlist to Library"
                    else -> ""
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.fraction },
            color = GlassTokens.TextPrimary,
            trackColor = GlassTokens.InactiveSurface,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
        )
    }
}

@Composable
private fun LinkImportSuccessCard(
    progress: LinkImportProgress,
    onPlay: () -> Unit,
    onInspect: () -> Unit,
    onImportAnother: () -> Unit
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .smokedGlass(
                shape = shape,
                elevation = GlassElevation.CARD,
                accentTint = GlassTokens.SuccessMint
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtworkThumbnail(
                artworkUrl = progress.artworkUrl,
                placeholderIcon = Icons.Rounded.CheckCircle,
                size = 52.dp,
                cornerRadius = GlassTokens.RadiusSm
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.collectionTitle ?: "Imported Playlist",
                    style = MaterialTheme.typography.titleMedium,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Saved ${progress.matchedCount} of ${progress.totalCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.SuccessMint
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.accent,
                    contentColor = GlassTokens.AmoledBlack
                ),
                shape = RoundedCornerShape(GlassTokens.RadiusPill),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            OutlinedButton(
                onClick = onInspect,
                shape = RoundedCornerShape(GlassTokens.RadiusPill),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            ) {
                Text(
                    text = "Inspect",
                    style = MaterialTheme.typography.labelLarge,
                    color = OndaTheme.colors.textPrimary
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                .clickable(onClick = onImportAnother),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Import Another Link",
                style = MaterialTheme.typography.labelLarge,
                color = OndaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun LinkImportErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .smokedGlass(
                shape = shape,
                elevation = GlassElevation.CARD,
                accentTint = GlassTokens.ErrorCoral
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = GlassTokens.ErrorCoral
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textPrimary
            )
        }
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = GlassTokens.ErrorCoral,
                contentColor = GlassTokens.AmoledBlack
            ),
            shape = RoundedCornerShape(GlassTokens.RadiusPill),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
        ) {
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
