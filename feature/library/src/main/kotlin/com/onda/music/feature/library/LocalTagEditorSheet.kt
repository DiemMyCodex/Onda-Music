package com.onda.music.feature.library

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.model.Track
import com.onda.music.core.sources.AudioTagMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTagEditorSheet(
    track: Track,
    onLoadMetadata: suspend (Track) -> AudioTagMetadata?,
    onSaveMetadata: suspend (Track, AudioTagMetadata) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album.orEmpty()) }
    var trackNumberText by remember(track.id) { mutableStateOf("") }
    var pickedArtBytes by remember(track.id) { mutableStateOf<ByteArray?>(null) }
    var pickedArtMime by remember(track.id) { mutableStateOf("image/jpeg") }
    var pickedArtPreviewUri by remember(track.id) { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track.id) {
        val loaded = onLoadMetadata(track)
        if (loaded != null) {
            if (loaded.title.isNotBlank()) title = loaded.title
            if (loaded.artist.isNotBlank()) artist = loaded.artist
            if (loaded.album.isNotBlank()) album = loaded.album
            val trackNum = loaded.trackNumber
            if (trackNum != null && trackNum > 0) {
                trackNumberText = trackNum.toString()
            }
        }
    }

    fun executeWrite() {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isEmpty()) {
            errorText = "Title cannot be empty"
            return
        }
        val metadata = AudioTagMetadata(
            title = cleanTitle,
            artist = cleanArtist.ifEmpty { "Unknown Artist" },
            album = album.trim(),
            trackNumber = trackNumberText.trim().toIntOrNull()?.takeIf { it > 0 },
            artworkBytes = pickedArtBytes,
            artworkMimeType = pickedArtMime
        )
        scope.launch {
            isSaving = true
            errorText = null
            val result = onSaveMetadata(track, metadata)
            isSaving = false
            if (result.isSuccess) {
                onDismiss()
            } else {
                errorText = result.exceptionOrNull()?.message ?: "Failed to update tags"
            }
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            executeWrite()
        } else {
            errorText = "Write permission required to edit local audio file"
        }
    }

    val artworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedArtPreviewUri = uri
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) mime to bytes else null
                    }.getOrNull()
                }
                if (loaded != null) {
                    pickedArtMime = loaded.first
                    pickedArtBytes = loaded.second
                }
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color(0xFF2A2A2E),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color.White,
        focusedContainerColor = Color(0xFF0A0A0B),
        unfocusedContainerColor = Color(0xFF0A0A0B),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color(0xFF9A9AA0)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White
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
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit Audio Tags",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "MP3 ID3v2 · FLAC Vorbis · M4A Atoms",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9A9AA0)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(DockMetrics.MinTapTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF9A9AA0)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A0A0B))
                        .border(0.75.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val coverModel: Any? = pickedArtPreviewUri ?: track.artworkUrl
                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = "Cover art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = { artworkPickerLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pickedArtBytes != null) "Cover Selected" else "Embed Cover Art",
                        maxLines = 1
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Album") },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = trackNumberText,
                onValueChange = { input -> trackNumberText = input.filter { it.isDigit() }.take(4) },
                label = { Text("Track Number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            val err = errorText
            if (!err.isNullOrBlank()) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val localUriString = track.localUri
                    if (localUriString.isNullOrBlank()) {
                        errorText = "Track does not have a writable local URI"
                        return@Button
                    }
                    val targetUri = Uri.parse(localUriString)
                    val isMediaStore = targetUri.authority == MediaStore.AUTHORITY
                    if (isMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            val pendingIntent = MediaStore.createWriteRequest(
                                context.contentResolver,
                                listOf(targetUri)
                            )
                            writePermissionLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        }.onFailure {
                            executeWrite()
                        }
                    } else {
                        executeWrite()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                Text(
                    text = if (isSaving) "Writing Tags..." else "Save Tags Atomically",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
