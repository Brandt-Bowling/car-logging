package com.example.car_logger.ui.screens.home.tabs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService

@Composable
internal fun VehicleSyncSettingsSection(
    state: VehicleSyncSettingsState,
    onSelectWakePolicy: (WakeProtectionPolicy) -> Unit,
    onSelectCooldownHours: (Int) -> Unit,
    onToggleDailyPoll: (Boolean) -> Unit,
    onConnectSmartcar: () -> Unit,
    onConnectTesla: () -> Unit,
    onRequestForceWakeSync: (Car) -> Unit,
    modifier: Modifier = Modifier
) {
    val gmCar = state.cars.firstOrNull {
        OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR
    }
    val teslaCar = state.cars.firstOrNull {
        OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_TESLA
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "Vehicle sync")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OdometerSyncHeaderRow()
                SleepProtectionSelectorRow(
                    selectedPolicy = state.wakePolicy,
                    onSelectWakePolicy = onSelectWakePolicy
                )
                MinimumWaitSelectorRow(
                    selectedCooldownHours = state.cooldownHours,
                    onSelectCooldownHours = onSelectCooldownHours
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                NightlyMileageCheckRow(
                    dailyPollEnabled = state.dailyPollEnabled,
                    lastDailyPollTime = state.lastDailyPollTime,
                    lastDailyPollStatus = state.lastDailyPollStatus,
                    onToggleDailyPoll = onToggleDailyPoll
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SmartcarOdometerRow(
                    gmCar = gmCar,
                    smartcarConnected = state.smartcarConnected,
                    cooldownHours = state.cooldownHours,
                    onConnectClick = onConnectSmartcar,
                    onSyncClick = onRequestForceWakeSync
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                TeslaOdometerRow(
                    teslaCar = teslaCar,
                    teslaConnected = state.teslaConnected,
                    cooldownHours = state.cooldownHours,
                    onConnectClick = onConnectTesla,
                    onSyncClick = onRequestForceWakeSync
                )
            }
        }
    }
}

@Composable
private fun OdometerSyncHeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Odometer sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Automatic mileage updates",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SleepProtectionSelectorRow(
    selectedPolicy: WakeProtectionPolicy,
    onSelectWakePolicy: (WakeProtectionPolicy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Sleep protection",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WakeProtectionPolicy.entries.forEach { policy ->
                FilterChip(
                    selected = selectedPolicy == policy,
                    onClick = { onSelectWakePolicy(policy) },
                    label = { Text(policy.label) }
                )
            }
        }
    }
}

@Composable
private fun MinimumWaitSelectorRow(
    selectedCooldownHours: Int,
    onSelectCooldownHours: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Minimum wait",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24 to "24 hr", 72 to "3 days", 168 to "7 days").forEach { (hours, label) ->
                FilterChip(
                    selected = selectedCooldownHours == hours,
                    onClick = { onSelectCooldownHours(hours) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun NightlyMileageCheckRow(
    dailyPollEnabled: Boolean,
    lastDailyPollTime: Long,
    lastDailyPollStatus: String?,
    onToggleDailyPoll: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nightly mileage check",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Sync odometer daily at 11:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dailyPollEnabled) {
                val lastRunText = if (lastDailyPollTime > 0L) {
                    val formattedTime = OdometerSyncService.formatRelativeTime(lastDailyPollTime)
                    val statusText = lastDailyPollStatus?.let { " • $it" } ?: ""
                    "Last run $formattedTime$statusText"
                } else {
                    "Scheduled for 11:00 PM tonight"
                }
                Text(
                    text = lastRunText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = dailyPollEnabled,
            modifier = Modifier.semantics { contentDescription = "Nightly mileage check" },
            onCheckedChange = onToggleDailyPoll
        )
    }
}

@Composable
private fun SmartcarOdometerRow(
    gmCar: Car?,
    smartcarConnected: Boolean,
    cooldownHours: Int,
    onConnectClick: () -> Unit,
    onSyncClick: (Car) -> Unit
) {
    val gmSyncTimeText = OdometerSyncService.formatRelativeTime(gmCar?.lastOdometerSyncTime)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Smartcar (Blazer EV)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatVehicleSyncSubtitle(
                    isConnected = smartcarConnected,
                    lastSyncTimeMs = gmCar?.lastOdometerSyncTime,
                    cooldownHours = cooldownHours,
                    showSyncedWhenDisconnected = true
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!smartcarConnected || gmSyncTimeText == null) {
                TextButton(onClick = onConnectClick) {
                    Text(if (smartcarConnected) "Reconnect" else "Connect")
                }
            }
            if (gmCar != null) {
                TextButton(onClick = { onSyncClick(gmCar) }) { Text("Sync") }
            }
        }
    }
}

@Composable
private fun TeslaOdometerRow(
    teslaCar: Car?,
    teslaConnected: Boolean,
    cooldownHours: Int,
    onConnectClick: () -> Unit,
    onSyncClick: (Car) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tesla Fleet (Model 3)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatVehicleSyncSubtitle(
                    isConnected = teslaConnected,
                    lastSyncTimeMs = teslaCar?.lastOdometerSyncTime,
                    cooldownHours = cooldownHours,
                    showSyncedWhenDisconnected = false
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!teslaConnected) {
                TextButton(onClick = onConnectClick) { Text("Connect") }
            } else if (teslaCar != null) {
                TextButton(onClick = { onSyncClick(teslaCar) }) { Text("Sync") }
            }
        }
    }
}
