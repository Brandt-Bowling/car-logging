package com.example.car_logger.ui.screens.home.tabs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.ui.screens.home.tabs.DeveloperDashboardUrls

internal data class SettingsDialogCallbacks(
    val onDismiss: () -> Unit,
    val onSaveGarageName: (String) -> Unit,
    val onSaveGeminiKey: (String) -> Unit,
    val onSaveAutoDevKey: (String) -> Unit,
    val onSaveSmartcarCredentials: (String, String, String) -> Unit,
    val onSignInSmartcar: (String, String, String) -> Unit,
    val onDisconnectSmartcar: () -> Unit,
    val onSaveTeslaCredentials: (String, String, String) -> Unit,
    val onSignInTesla: (String, String, Boolean, Boolean) -> Unit,
    val onDisconnectTesla: () -> Unit,
    val onConfirmForceWakeVehicle: (Car) -> Unit,
    val onReconnectWithLocationScope: () -> Unit,
    val onConfirmWakeWorkLocation: () -> Unit,
    val onConfirmWakeWorkCharge: () -> Unit,
    val onSaveChargerSyncTime: (Int, Int) -> Unit,
    val onSaveWorkWindowTime: (Boolean, Int, Int) -> Unit
)

@Composable
internal fun SettingsActiveDialogHost(
    dialog: SettingsDialogState,
    is24Hour: Boolean,
    callbacks: SettingsDialogCallbacks
) {
    when (dialog) {
        SettingsDialogState.None -> Unit
        is SettingsDialogState.GarageName -> GarageNameDialog(
            initialName = dialog.currentName,
            onDismiss = callbacks.onDismiss,
            onSave = callbacks.onSaveGarageName
        )
        is SettingsDialogState.GeminiKey -> ApiKeyConfigDialog(
            title = "Gemini API key",
            description = "Add a Google AI Studio key to scan receipts and electric bills automatically.",
            linkLabel = "Open Google AI Studio",
            linkUrl = DeveloperDashboardUrls.GEMINI,
            initialKey = dialog.currentKey,
            onDismiss = callbacks.onDismiss,
            onSave = callbacks.onSaveGeminiKey
        )
        is SettingsDialogState.AutoDevKey -> ApiKeyConfigDialog(
            title = "Auto.dev API key",
            description = "Optional key for VIN lookup and stock photos. Leave blank to use the default.",
            linkLabel = "Open Auto.dev",
            linkUrl = DeveloperDashboardUrls.AUTO_DEV,
            initialKey = dialog.currentKey,
            onDismiss = callbacks.onDismiss,
            onSave = callbacks.onSaveAutoDevKey
        )
        is SettingsDialogState.SmartcarCredentials -> SmartcarCredentialsDialog(
            state = dialog,
            onDismiss = callbacks.onDismiss,
            onSave = callbacks.onSaveSmartcarCredentials,
            onSignIn = callbacks.onSignInSmartcar,
            onDisconnect = callbacks.onDisconnectSmartcar
        )
        is SettingsDialogState.TeslaCredentials -> TeslaCredentialsDialog(
            state = dialog,
            onDismiss = callbacks.onDismiss,
            onSave = callbacks.onSaveTeslaCredentials,
            onSignIn = callbacks.onSignInTesla,
            onDisconnect = callbacks.onDisconnectTesla
        )
        is SettingsDialogState.ForceWakeVehicle -> ForceWakeVehicleDialog(
            car = dialog.car,
            onDismiss = callbacks.onDismiss,
            onConfirm = { callbacks.onConfirmForceWakeVehicle(dialog.car) }
        )
        SettingsDialogState.WorkLocationMissingScope -> WorkLocationMissingScopeDialog(
            onDismiss = callbacks.onDismiss,
            onReconnect = callbacks.onReconnectWithLocationScope
        )
        SettingsDialogState.WorkLocationWakeConfirm -> WorkLocationWakeDialog(
            onDismiss = callbacks.onDismiss,
            onConfirm = callbacks.onConfirmWakeWorkLocation
        )
        SettingsDialogState.WorkChargeWakeConfirm -> WorkChargeWakeDialog(
            onDismiss = callbacks.onDismiss,
            onConfirm = callbacks.onConfirmWakeWorkCharge
        )
        is SettingsDialogState.ChargerSyncTimePicker -> SettingsTimePickerDialog(
            title = "Charging sync time",
            initialHour = dialog.hour,
            initialMinute = dialog.minute,
            is24Hour = is24Hour,
            onDismiss = callbacks.onDismiss,
            onConfirm = callbacks.onSaveChargerSyncTime
        )
        is SettingsDialogState.WorkWindowTimePicker -> SettingsTimePickerDialog(
            title = if (dialog.isStart) "Work check window start" else "Work check window end",
            initialHour = dialog.hour,
            initialMinute = dialog.minute,
            is24Hour = is24Hour,
            onDismiss = callbacks.onDismiss,
            onConfirm = { h, m -> callbacks.onSaveWorkWindowTime(dialog.isStart, h, m) }
        )
    }
}

@Composable
private fun ForceWakeVehicleDialog(
    car: Car,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wake vehicle?") },
        text = { Text("Waking ${car.displayName} uses a small amount of battery. Sync odometer now?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Wake & sync", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WorkLocationMissingScopeDialog(
    onDismiss: () -> Unit,
    onReconnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Work GPS scope") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Saving your work location requires Tesla's optional vehicle_location scope. " +
                        "Make sure Vehicle Location is enabled for your app in the Tesla Developer Portal, then reconnect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DashboardLinkButton(
                    label = "Open Tesla developer portal",
                    url = DeveloperDashboardUrls.TESLA
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onReconnect) { Text("Reconnect with location") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WorkLocationWakeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wake Tesla to save location?") },
        text = {
            Text("Your Tesla is currently asleep. Waking it briefly uses a small amount of battery to read its current GPS coordinates as your work location.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Wake & save", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WorkChargeWakeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tesla is asleep") },
        text = {
            Text("Zero-wake check did not find cached charge data. Wake your Tesla briefly to check for a work charge now?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Wake & sync", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
