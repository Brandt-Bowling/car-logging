package com.example.car_logger.ui.screens.home.tabs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.car_logger.BuildConfig

@Composable
internal fun GeneralSettingsSection(
    garageName: String,
    onEditGarageName: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "General")
        SettingsGroupCard {
            SettingsRowItem(
                icon = Icons.Filled.DirectionsCar,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                title = "Garage name",
                subtitle = garageName,
                onClick = onEditGarageName,
                trailing = {
                    IconButton(onClick = onEditGarageName) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit garage name")
                    }
                }
            )
        }
    }
}

@Composable
internal fun BackupSettingsSection(
    isDriveConnected: Boolean,
    driveEmail: String,
    onSignInClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "Backup")
        SettingsGroupCard {
            SettingsRowItem(
                icon = if (isDriveConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                iconContainerColor = if (isDriveConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                iconTint = if (isDriveConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                title = "Google Drive sync",
                subtitle = if (isDriveConnected) driveEmail else "Back up and import service receipts",
                trailing = {
                    if (isDriveConnected) {
                        OutlinedButton(onClick = onDisconnectClick) { Text("Disconnect") }
                    } else {
                        Button(onClick = onSignInClick) { Text("Sign in") }
                    }
                }
            )
        }
    }
}

@Composable
internal fun ApiKeysSettingsSection(
    state: GeneralBackupSettingsState,
    onOpenGeminiDialog: () -> Unit,
    onOpenAutoDevDialog: () -> Unit,
    onOpenSmartcarDialog: () -> Unit,
    onOpenTeslaDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "API keys")
        SettingsGroupCard {
            SettingsRowItem(
                icon = Icons.Filled.AutoAwesome,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Receipt scanning",
                subtitle = if (state.geminiKey.isNotBlank()) "Gemini AI enabled" else "Basic (filename only)",
                onClick = onOpenGeminiDialog,
                trailing = {
                    IconButton(onClick = onOpenGeminiDialog) {
                        Icon(Icons.Filled.Key, contentDescription = "Configure Gemini key")
                    }
                }
            )
            SettingsItemDivider()
            SettingsRowItem(
                icon = Icons.Filled.DirectionsCar,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                title = "VIN lookup",
                subtitle = if (state.autoDevKey.isNotBlank()) "Custom key" else "Default key",
                onClick = onOpenAutoDevDialog,
                trailing = {
                    IconButton(onClick = onOpenAutoDevDialog) {
                        Icon(Icons.Filled.Key, contentDescription = "Configure Auto.dev key")
                    }
                }
            )
            SettingsItemDivider()
            SettingsRowItem(
                icon = Icons.Filled.Key,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                title = "Smartcar (Blazer EV)",
                subtitle = if (state.smartcarConnected) "Connected" else "Not set up",
                onClick = onOpenSmartcarDialog,
                trailing = {
                    IconButton(onClick = onOpenSmartcarDialog) {
                        Icon(Icons.Filled.Key, contentDescription = "Configure Smartcar credentials")
                    }
                }
            )
            SettingsItemDivider()
            SettingsRowItem(
                icon = Icons.Filled.Key,
                iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Tesla Fleet (Model 3)",
                subtitle = if (state.teslaConnected) "Connected" else "Not set up",
                onClick = onOpenTeslaDialog,
                trailing = {
                    IconButton(onClick = onOpenTeslaDialog) {
                        Icon(Icons.Filled.Key, contentDescription = "Configure Tesla credentials")
                    }
                }
            )
        }
    }
}

@Composable
internal fun AboutSettingsSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "About")
        SettingsGroupCard {
            SettingsRowItem(
                icon = Icons.Filled.Info,
                iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Garage Journal",
                subtitle = "Version ${BuildConfig.VERSION_NAME}"
            )
        }
    }
}
