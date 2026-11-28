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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import java.util.Locale

@Composable
internal fun WorkChargingSettingsSection(
    state: WorkChargingSettingsState,
    is24Hour: Boolean,
    onSyncWorkCharging: () -> Unit,
    onToggleWorkChargingSync: (Boolean) -> Unit,
    onEditWorkWindowStart: () -> Unit,
    onEditWorkWindowEnd: () -> Unit,
    onToggleWeekdaysOnly: () -> Unit,
    onClearWorkLocation: () -> Unit,
    onCaptureWorkLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startText = remember(state.workWindowStartHour, state.workWindowStartMinute, is24Hour) {
        formatSettingsTime(state.workWindowStartHour, state.workWindowStartMinute, is24Hour)
    }
    val endText = remember(state.workWindowEndHour, state.workWindowEndMinute, is24Hour) {
        formatSettingsTime(state.workWindowEndHour, state.workWindowEndMinute, is24Hour)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "Work charging")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WorkChargingHeaderRow(
                    state = state,
                    formattedWindowRange = "$startText – $endText",
                    onSyncClick = onSyncWorkCharging
                )
                WorkWindowControlsBlock(
                    state = state,
                    formattedStart = startText,
                    formattedEnd = endText,
                    onToggleWorkChargingSync = onToggleWorkChargingSync,
                    onEditStart = onEditWorkWindowStart,
                    onEditEnd = onEditWorkWindowEnd,
                    onToggleWeekdaysOnly = onToggleWeekdaysOnly
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                WorkGpsLocationRow(
                    state = state,
                    onClear = onClearWorkLocation,
                    onCaptureFromCar = onCaptureWorkLocation
                )
            }
        }
    }
}

@Composable
private fun WorkChargingHeaderRow(
    state: WorkChargingSettingsState,
    formattedWindowRange: String,
    onSyncClick: () -> Unit
) {
    val workIconContainer = ExpressiveThemeTokens.workIconContainer
    val workIconTint = ExpressiveThemeTokens.workIconTint
    val hasWorkError = state.teslaConnected && isWorkChargingErrorStatus(state.lastWorkChargingSyncStatus)
    val workStatusText = formatWorkChargingSubtitle(
        teslaConnected = state.teslaConnected,
        lastSyncTimeMs = state.lastWorkChargingSyncTime.takeIf { it > 0L },
        lastSyncStatus = state.lastWorkChargingSyncStatus,
        syncEnabled = state.workChargingSyncEnabled,
        weekdaysOnly = state.workChargingWeekdaysOnly,
        formattedWindowRange = formattedWindowRange
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
                    .background(workIconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Work,
                    contentDescription = null,
                    tint = workIconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Work charging (Tesla)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = workStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        hasWorkError -> MaterialTheme.colorScheme.error
                        state.teslaConnected && state.lastWorkChargingSyncTime > 0L -> workIconTint
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
private fun WorkWindowControlsBlock(
    state: WorkChargingSettingsState,
    formattedStart: String,
    formattedEnd: String,
    onToggleWorkChargingSync: (Boolean) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onToggleWeekdaysOnly: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-capture during work",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Hourly zero-wake checks — catches variable departure times before home charging",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.workChargingSyncEnabled,
                modifier = Modifier.semantics { contentDescription = "Auto-capture during work" },
                onCheckedChange = onToggleWorkChargingSync
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.workChargingSyncEnabled,
                onClick = onEditStart,
                leadingIcon = {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(formattedStart) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit work window start time",
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
            Text(
                text = "–",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilterChip(
                selected = state.workChargingSyncEnabled,
                onClick = onEditEnd,
                label = { Text(formattedEnd) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit work window end time",
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
        FilterChip(
            selected = state.workChargingWeekdaysOnly,
            onClick = onToggleWeekdaysOnly,
            leadingIcon = if (state.workChargingWeekdaysOnly) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
            } else null,
            label = { Text("Weekdays only") }
        )
    }
}

@Composable
private fun WorkGpsLocationRow(
    state: WorkChargingSettingsState,
    onClear: () -> Unit,
    onCaptureFromCar: () -> Unit
) {
    val workIconTint = ExpressiveThemeTokens.workIconTint
    val hasWorkGps = state.workLocationLat != null && state.workLocationLng != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = if (hasWorkGps) workIconTint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Work location (GPS)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (hasWorkGps) {
                        "Saved (${String.format(Locale.US, "%.4f, %.4f", state.workLocationLat, state.workLocationLng)} · ${state.workLocationRadiusMeters}m)"
                    } else {
                        "Optional • using Home Wall Connector filter"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasWorkGps) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
            if (state.teslaConnected) {
                TextButton(onClick = onCaptureFromCar) { Text("Set from car") }
            }
        }
    }
}
