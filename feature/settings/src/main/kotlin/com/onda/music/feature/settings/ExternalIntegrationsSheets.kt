package com.onda.music.feature.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobbleConfigSheet(
    settings: OndaUserSettings,
    strings: SettingsStrings,
    onEnabledChange: (Boolean) -> Unit,
    onSaveConfig: (service: String, username: String, sessionKey: String, apiKey: String, apiSecret: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var service by remember(settings.scrobbleService) { mutableStateOf(settings.scrobbleService) }
    var username by remember(settings.scrobbleUsername) { mutableStateOf(settings.scrobbleUsername) }
    var sessionKey by remember(settings.scrobbleSessionKey) { mutableStateOf(settings.scrobbleSessionKey) }
    var apiKey by remember(settings.scrobbleApiKey) { mutableStateOf(settings.scrobbleApiKey) }
    var apiSecret by remember(settings.scrobbleApiSecret) { mutableStateOf(settings.scrobbleApiSecret) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SettingsOledColors.TextPrimary,
        unfocusedBorderColor = SettingsOledColors.InsetDivider,
        focusedTextColor = SettingsOledColors.TextPrimary,
        unfocusedTextColor = SettingsOledColors.TextPrimary,
        cursorColor = SettingsOledColors.TextPrimary,
        focusedContainerColor = SettingsOledColors.CanvasBlack,
        unfocusedContainerColor = SettingsOledColors.CanvasBlack
    )

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
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = SettingsOledColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.scrobbleTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = SettingsOledColors.TextPrimary
                    )
                    Text(
                        text = strings.scrobbleSheetDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary
                    )
                }
                OndaSwitch(
                    checked = settings.scrobbleEnabled,
                    onCheckedChange = onEnabledChange
                )
                Box(
                    modifier = Modifier
                        .size(DockMetrics.MinTapTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.closeLabel,
                        tint = SettingsOledColors.TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsPillButton(
                    text = "Libre.fm",
                    selected = service != "lastfm",
                    onClick = { service = "librefm" },
                    modifier = Modifier.weight(1f)
                )
                SettingsPillButton(
                    text = "Last.fm",
                    selected = service == "lastfm",
                    onClick = { service = "lastfm" },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(strings.scrobbleUsernameLabel) },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sessionKey,
                onValueChange = { sessionKey = it },
                label = { Text(strings.scrobbleSessionKeyLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (service == "lastfm") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(strings.scrobbleApiKeyLabel) },
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text(strings.scrobbleApiSecretLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = strings.scrobbleLibreFmNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsOledColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SettingsPillButton(
                text = strings.saveLabel,
                selected = true,
                onClick = {
                    onSaveConfig(service, username, sessionKey, apiKey, apiSecret)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordRpcConfigSheet(
    settings: OndaUserSettings,
    strings: SettingsStrings,
    onEnabledChange: (Boolean) -> Unit,
    onSaveToken: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tokenInput by remember(settings.discordUserToken) { mutableStateOf(settings.discordUserToken.orEmpty()) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SettingsOledColors.TextPrimary,
        unfocusedBorderColor = SettingsOledColors.InsetDivider,
        focusedTextColor = SettingsOledColors.TextPrimary,
        unfocusedTextColor = SettingsOledColors.TextPrimary,
        cursorColor = SettingsOledColors.TextPrimary,
        focusedContainerColor = SettingsOledColors.CanvasBlack,
        unfocusedContainerColor = SettingsOledColors.CanvasBlack
    )

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
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = SettingsOledColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.discordRpcTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = SettingsOledColors.TextPrimary
                    )
                    Text(
                        text = strings.discordRpcSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsOledColors.TextSecondary
                    )
                }
                OndaSwitch(
                    checked = settings.discordRpcEnabled,
                    onCheckedChange = onEnabledChange
                )
                Box(
                    modifier = Modifier
                        .size(DockMetrics.MinTapTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.closeLabel,
                        tint = SettingsOledColors.TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1F1616))
                    .border(1.dp, Color(0x55FF6B7A), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFFF6B7A),
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = strings.discordTosWarningTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFB4BC)
                    )
                    Text(
                        text = strings.discordTosWarningBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD6B8BC)
                    )
                }
            }

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text(strings.discordTokenLabel) },
                placeholder = { Text(strings.discordTokenPlaceholder, color = SettingsOledColors.TextMuted) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tokenInput.isNotBlank() || !settings.discordUserToken.isNullOrBlank()) {
                    SettingsPillButton(
                        text = strings.clearLabel,
                        selected = false,
                        onClick = {
                            tokenInput = ""
                            onSaveToken(null)
                            onEnabledChange(false)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                SettingsPillButton(
                    text = strings.saveLabel,
                    selected = true,
                    onClick = {
                        onSaveToken(tokenInput)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
