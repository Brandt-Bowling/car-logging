package com.example.car_logger.ui.screens.home.tabs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.ui.screens.home.tabs.DeveloperDashboardUrls

@Composable
internal fun GarageNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tempName by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename garage") },
        text = {
            OutlinedTextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = { Text("Garage name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempName) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun ApiKeyConfigDialog(
    title: String,
    description: String,
    linkLabel: String,
    linkUrl: String,
    initialKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tempKey by remember(initialKey) { mutableStateOf(initialKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                DashboardLinkButton(label = linkLabel, url = linkUrl)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempKey) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun SmartcarCredentialsDialog(
    state: SettingsDialogState.SmartcarCredentials,
    onDismiss: () -> Unit,
    onSave: (clientId: String, clientSecret: String, accessToken: String) -> Unit,
    onSignIn: (clientId: String, clientSecret: String, accessToken: String) -> Unit,
    onDisconnect: () -> Unit
) {
    var clientId by remember(state) { mutableStateOf(state.clientId) }
    var clientSecret by remember(state) { mutableStateOf(state.clientSecret) }
    var accessToken by remember(state) { mutableStateOf(state.accessToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smartcar (Blazer EV)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardLinkButton(label = "Open Smartcar dashboard", url = DeveloperDashboardUrls.SMARTCAR)
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Client secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = { Text("Access token (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(
                    onClick = { onSignIn(clientId, clientSecret, accessToken) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isConnected) "Reconnect Smartcar" else "Sign in with Smartcar")
                }
                if (state.isConnected) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect Smartcar")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(clientId, clientSecret, accessToken) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun TeslaCredentialsDialog(
    state: SettingsDialogState.TeslaCredentials,
    onDismiss: () -> Unit,
    onSave: (clientId: String, clientSecret: String, accessToken: String) -> Unit,
    onSignIn: (
        clientId: String,
        clientSecret: String,
        includeLocationScope: Boolean,
        includeChargingHistoryScope: Boolean
    ) -> Unit,
    onDisconnect: () -> Unit
) {
    var clientId by remember(state) { mutableStateOf(state.clientId) }
    var clientSecret by remember(state) { mutableStateOf(state.clientSecret) }
    var accessToken by remember(state) { mutableStateOf(state.accessToken) }
    var includeChargingHistoryScope by remember(state) { mutableStateOf(state.includeChargingHistoryScope) }
    var includeLocationScope by remember(state) { mutableStateOf(state.includeLocationScope) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tesla Fleet (Model 3)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardLinkButton(label = "Open Tesla developer portal", url = DeveloperDashboardUrls.TESLA)
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Client secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = { Text("Access token (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (clientId.isNotBlank()) {
                    TeslaOptionalScopesBlock(
                        includeChargingHistoryScope = includeChargingHistoryScope,
                        includeLocationScope = includeLocationScope,
                        isConnected = state.isConnected,
                        onToggleChargingScope = { includeChargingHistoryScope = !includeChargingHistoryScope },
                        onToggleLocationScope = { includeLocationScope = !includeLocationScope },
                        onSignInClick = {
                            onSignIn(clientId, clientSecret, includeLocationScope, includeChargingHistoryScope)
                        }
                    )
                }
                if (state.isConnected) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect Tesla")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(clientId, clientSecret, accessToken) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TeslaOptionalScopesBlock(
    includeChargingHistoryScope: Boolean,
    includeLocationScope: Boolean,
    isConnected: Boolean,
    onToggleChargingScope: () -> Unit,
    onToggleLocationScope: () -> Unit,
    onSignInClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Optional Tesla scopes (enable in portal first)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilterChip(
            selected = includeChargingHistoryScope,
            onClick = onToggleChargingScope,
            leadingIcon = if (includeChargingHistoryScope) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            label = {
                Text(
                    text = "Supercharger history (vehicle_charging_cmds)",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        FilterChip(
            selected = includeLocationScope,
            onClick = onToggleLocationScope,
            leadingIcon = if (includeLocationScope) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            label = {
                Text(
                    text = "Work GPS (vehicle_location)",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        FilledTonalButton(onClick = onSignInClick, modifier = Modifier.fillMaxWidth()) {
            Text(if (isConnected) "Reconnect Tesla" else "Sign in with Tesla")
        }
    }
}
