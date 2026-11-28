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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.ui.screens.home.tabs.DeveloperDashboardUrls

@Composable
internal fun PublicChargingSettingsSection(
    state: PublicChargingSettingsState,
    onSyncSuperchargers: () -> Unit,
    onEnableAndReconnectTesla: () -> Unit,
    onToggleSuperchargerSync: (Boolean) -> Unit,
    onSyncSmartcarDc: () -> Unit,
    onReconnectSmartcarDc: () -> Unit,
    onToggleSmartcarDcSync: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = "Public & DC charging")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TeslaSuperchargerBlock(
                    state = state,
                    onSyncSuperchargers = onSyncSuperchargers,
                    onOpenPortal = { onOpenUrl(DeveloperDashboardUrls.TESLA) },
                    onEnableAndReconnect = onEnableAndReconnectTesla,
                    onToggleSync = onToggleSuperchargerSync
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SmartcarDcChargingBlock(
                    state = state,
                    onSyncSmartcarDc = onSyncSmartcarDc,
                    onOpenDashboard = { onOpenUrl(DeveloperDashboardUrls.SMARTCAR) },
                    onReconnectSmartcar = onReconnectSmartcarDc,
                    onToggleSync = onToggleSmartcarDcSync
                )
            }
        }
    }
}

@Composable
private fun TeslaSuperchargerBlock(
    state: PublicChargingSettingsState,
    onSyncSuperchargers: () -> Unit,
    onOpenPortal: () -> Unit,
    onEnableAndReconnect: () -> Unit,
    onToggleSync: (Boolean) -> Unit
) {
    val showBanner = state.teslaConnected &&
        (!state.hasTeslaChargingHistoryScope || isSuperchargerScopeError(state.lastSuperchargerSyncStatus))
    val scSubtitle = formatSuperchargerSubtitle(
        teslaConnected = state.teslaConnected,
        hasChargingScope = state.hasTeslaChargingHistoryScope,
        lastSyncTimeMs = state.lastSuperchargerSyncTime.takeIf { it > 0L },
        lastSyncStatus = state.lastSuperchargerSyncStatus,
        syncEnabled = state.superchargerSyncEnabled
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tesla Superchargers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = scSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            showBanner -> MaterialTheme.colorScheme.error
                            state.teslaConnected && state.lastSuperchargerSyncTime > 0L -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (state.teslaConnected) {
                TextButton(onClick = onSyncSuperchargers) { Text("Sync") }
            }
        }
        if (showBanner) {
            ChargingPermissionBanner(
                text = "Enable vehicle_charging_cmds in Tesla Developer Portal, then reconnect to pull paid Supercharger sessions (Model 3 & Blazer EV).",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                linkLabel = "Portal",
                reconnectLabel = "Enable & reconnect",
                reconnectWeight = 1.3f,
                onOpenLink = onOpenPortal,
                onReconnect = onEnableAndReconnect
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-sync Superchargers", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Zero-wake pull with daily charging sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.superchargerSyncEnabled,
                modifier = Modifier.semantics { contentDescription = "Auto-sync Superchargers" },
                onCheckedChange = onToggleSync
            )
        }
    }
}

@Composable
private fun SmartcarDcChargingBlock(
    state: PublicChargingSettingsState,
    onSyncSmartcarDc: () -> Unit,
    onOpenDashboard: () -> Unit,
    onReconnectSmartcar: () -> Unit,
    onToggleSync: (Boolean) -> Unit
) {
    val showBanner = state.smartcarConnected &&
        state.smartcarDcCapability == PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION
    val subtitle = formatSmartcarDcSubtitle(
        smartcarConnected = state.smartcarConnected,
        capability = state.smartcarDcCapability,
        lastSyncTimeMs = state.lastSmartcarDcSyncTime.takeIf { it > 0L },
        lastSyncStatus = state.lastSmartcarDcSyncStatus,
        syncEnabled = state.smartcarDcSyncEnabled
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EvStation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smartcar DC charging",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            showBanner -> MaterialTheme.colorScheme.error
                            state.smartcarConnected && state.lastSmartcarDcSyncTime > 0L &&
                                state.smartcarDcCapability != PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED ->
                                MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (state.smartcarConnected) {
                TextButton(onClick = onSyncSmartcarDc) { Text("Check / Sync") }
            }
        }
        if (showBanner) {
            ChargingPermissionBanner(
                text = "Enable charge records or fast-charger signals under Vehicle Access in the Smartcar dashboard, then reconnect.",
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                linkLabel = "Dashboard",
                reconnectLabel = "Reconnect",
                reconnectWeight = 1.2f,
                onOpenLink = onOpenDashboard,
                onReconnect = onReconnectSmartcar
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-sync Smartcar DC", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (state.smartcarDcCapability == PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED) {
                        "Skipped in background — vehicle does not expose charge history"
                    } else {
                        "Runs with daily sync when supported by vehicle"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.smartcarDcSyncEnabled,
                modifier = Modifier.semantics { contentDescription = "Auto-sync Smartcar DC" },
                onCheckedChange = onToggleSync
            )
        }
    }
}

@Composable
private fun ChargingPermissionBanner(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    linkLabel: String,
    reconnectLabel: String,
    reconnectWeight: Float,
    onOpenLink: () -> Unit,
    onReconnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenLink,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(linkLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onReconnect,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(reconnectWeight)
                ) {
                    Text(reconnectLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}
