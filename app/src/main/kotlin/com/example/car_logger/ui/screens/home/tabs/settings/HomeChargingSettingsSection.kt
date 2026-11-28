package com.example.car_logger.ui.screens.home.tabs.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.WallConnectorType

@Composable
internal fun HomeChargingSettingsSection(
    state: HomeChargingSettingsState,
    is24Hour: Boolean,
    onSyncWallConnectors: () -> Unit,
    onReconnectTesla: () -> Unit,
    onAssignWallConnector: (WallConnectorType, Car) -> Unit,
    onEditSyncTime: () -> Unit,
    onToggleChargerSync: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeEvs = remember(state.cars) { state.cars.filter { !it.isArchived }.ifEmpty { state.cars } }
    val mappedGen3Car = OdometerSyncService.resolveCarForWallConnector(
        connectorType = WallConnectorType.GEN_3,
        cars = activeEvs,
        gen3OverrideCarId = state.wallConnectorGen3CarId,
        universalOverrideCarId = state.wallConnectorUniversalCarId
    )
    val mappedUniversalCar = OdometerSyncService.resolveCarForWallConnector(
        connectorType = WallConnectorType.UNIVERSAL,
        cars = activeEvs,
        gen3OverrideCarId = state.wallConnectorGen3CarId,
        universalOverrideCarId = state.wallConnectorUniversalCarId
    )
    val formattedSyncTime = remember(state.chargerSyncHour, state.chargerSyncMinute, is24Hour) {
        formatSettingsTime(state.chargerSyncHour, state.chargerSyncMinute, is24Hour)
    }
    val hasChargerError = state.teslaConnected && isWallConnectorErrorStatus(state.lastChargerSyncStatus)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "Home charging")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WallConnectorsHeaderRow(
                    state = state,
                    formattedSyncTime = formattedSyncTime,
                    hasChargerError = hasChargerError,
                    onSyncClick = onSyncWallConnectors
                )
                if (hasChargerError && !state.lastChargerSyncStatus.isNullOrBlank()) {
                    WallConnectorErrorBanner(
                        statusMessage = state.lastChargerSyncStatus,
                        onReconnect = onReconnectTesla
                    )
                }
                if (activeEvs.isNotEmpty()) {
                    AssignedVehiclesBentoRow(
                        activeEvs = activeEvs,
                        mappedGen3Car = mappedGen3Car,
                        mappedUniversalCar = mappedUniversalCar,
                        onAssignWallConnector = onAssignWallConnector
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                DailyChargingSyncRow(
                    chargerSyncEnabled = state.chargerSyncEnabled,
                    formattedSyncTime = formattedSyncTime,
                    onEditTime = onEditSyncTime,
                    onToggleEnabled = onToggleChargerSync
                )
            }
        }
    }
}

@Composable
private fun WallConnectorsHeaderRow(
    state: HomeChargingSettingsState,
    formattedSyncTime: String,
    hasChargerError: Boolean,
    onSyncClick: () -> Unit
) {
    val chargerSyncTimeText = OdometerSyncService.formatRelativeTime(state.lastChargerSyncTime)
    val chargerStatusText = formatWallConnectorSubtitle(
        teslaConnected = state.teslaConnected,
        lastSyncTimeMs = state.lastChargerSyncTime.takeIf { it > 0L },
        lastSyncStatus = state.lastChargerSyncStatus,
        chargerSyncEnabled = state.chargerSyncEnabled,
        formattedSyncTime = formattedSyncTime
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.EvStation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wall Connectors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = chargerStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        hasChargerError -> MaterialTheme.colorScheme.error
                        state.teslaConnected && chargerSyncTimeText != null -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        if (state.teslaConnected) {
            TextButton(onClick = onSyncClick) { Text("Sync") }
        }
    }
}

@Composable
private fun WallConnectorErrorBanner(
    statusMessage: String,
    onReconnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onReconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Reconnect", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AssignedVehiclesBentoRow(
    activeEvs: List<Car>,
    mappedGen3Car: Car?,
    mappedUniversalCar: Car?,
    onAssignWallConnector: (WallConnectorType, Car) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Assigned vehicles",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WallConnectorAssignmentTile(
                chargerName = "Gen 3",
                selectedCar = mappedGen3Car,
                fallbackLabel = "Model 3",
                vehicles = activeEvs,
                onSelectVehicle = { onAssignWallConnector(WallConnectorType.GEN_3, it) },
                modifier = Modifier.weight(1f)
            )
            WallConnectorAssignmentTile(
                chargerName = "Universal",
                selectedCar = mappedUniversalCar,
                fallbackLabel = "Blazer EV",
                vehicles = activeEvs,
                onSelectVehicle = { onAssignWallConnector(WallConnectorType.UNIVERSAL, it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DailyChargingSyncRow(
    chargerSyncEnabled: Boolean,
    formattedSyncTime: String,
    onEditTime: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Daily charging sync",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            FilterChip(
                selected = chargerSyncEnabled,
                onClick = onEditTime,
                leadingIcon = {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        if (chargerSyncEnabled) "Daily at $formattedSyncTime"
                        else "Paused ($formattedSyncTime)"
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit charging sync time",
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
        Switch(
            checked = chargerSyncEnabled,
            modifier = Modifier.semantics { contentDescription = "Daily charging sync" },
            onCheckedChange = onToggleEnabled
        )
    }
}
