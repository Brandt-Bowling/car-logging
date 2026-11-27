package com.example.car_logger.ui.screens.home.tabs.settings

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.services.OdometerSyncService
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WALL_CONNECTOR_SYNCED_REGEX = Regex(
    pattern = """^Synced\s+(\d+)\s+Wall Connector\s+(sessions?)(.*)$""",
    option = RegexOption.IGNORE_CASE
)

internal fun formatSettingsTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    val scheduledTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return scheduledTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

internal fun formatVehicleSyncSubtitle(
    isConnected: Boolean,
    lastSyncTimeMs: Long?,
    cooldownHours: Int,
    showSyncedWhenDisconnected: Boolean = false,
    nowMs: Long = System.currentTimeMillis()
): String {
    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    return when {
        !isConnected && (!showSyncedWhenDisconnected || syncTimeText == null) -> "Not connected"
        syncTimeText != null && OdometerSyncService.isCooldownActive(lastSyncTimeMs, cooldownHours, nowMs) ->
            "Synced $syncTimeText • Next auto-sync in ${OdometerSyncService.remainingCooldownHours(lastSyncTimeMs, cooldownHours, nowMs)}h"
        syncTimeText != null -> "Synced $syncTimeText"
        else -> "Connected • Not yet synced"
    }
}

internal fun isWallConnectorErrorStatus(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    return status.contains("energy_device_data", ignoreCase = true) ||
        status.contains("failed", ignoreCase = true) ||
        status.contains("expired", ignoreCase = true) ||
        status.contains("unauthorized", ignoreCase = true) ||
        status.contains("401") ||
        status.contains("403") ||
        status.contains("error", ignoreCase = true) ||
        status.contains("forbidden", ignoreCase = true) ||
        status.contains("reconnect", ignoreCase = true)
}

internal fun formatWallConnectorSubtitle(
    teslaConnected: Boolean,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    chargerSyncEnabled: Boolean,
    formattedSyncTime: String,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!teslaConnected) return "Connect Tesla to sync home charging"

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    if (isWallConnectorErrorStatus(lastSyncStatus)) {
        val status = lastSyncStatus.orEmpty()
        val needsReconnect = status.contains("energy_device_data", ignoreCase = true) ||
            status.contains("expired", ignoreCase = true) ||
            status.contains("unauthorized", ignoreCase = true) ||
            status.contains("401") ||
            status.contains("403") ||
            status.contains("forbidden", ignoreCase = true) ||
            status.contains("reconnect", ignoreCase = true)
        val shortError = if (needsReconnect) "Reconnect required" else "Sync failed"
        return if (syncTimeText != null) "Last sync $syncTimeText • $shortError" else shortError
    }

    if (syncTimeText != null) {
        val compactDetail = compactWallConnectorStatus(lastSyncStatus)
        return if (compactDetail != null) {
            "Synced $syncTimeText • $compactDetail"
        } else {
            "Synced $syncTimeText"
        }
    }

    return if (chargerSyncEnabled) {
        "Syncs daily at $formattedSyncTime"
    } else {
        "Auto-sync off"
    }
}

private fun compactWallConnectorStatus(status: String?): String? {
    val trimmed = status?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    WALL_CONNECTOR_SYNCED_REGEX.matchEntire(trimmed)?.let { match ->
        val count = match.groupValues[1]
        val unit = match.groupValues[2]
        val suffix = match.groupValues[3]
        return "$count $unit$suffix"
    }

    if (trimmed.startsWith("Wall Connectors up to date", ignoreCase = true) ||
        trimmed.startsWith("Wall Connectors synced", ignoreCase = true)
    ) {
        return "Up to date"
    }

    return trimmed
}

internal fun isWorkChargingErrorStatus(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    return status.contains("failed", ignoreCase = true) ||
        status.contains("expired", ignoreCase = true) ||
        status.contains("unauthorized", ignoreCase = true) ||
        status.contains("401") ||
        status.contains("403") ||
        status.contains("error", ignoreCase = true) ||
        status.contains("reconnect", ignoreCase = true)
}

internal fun formatWorkChargingSubtitle(
    teslaConnected: Boolean,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    syncEnabled: Boolean,
    weekdaysOnly: Boolean,
    formattedWindowRange: String,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!teslaConnected) return "Connect Tesla to auto-capture free work charges"

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    if (isWorkChargingErrorStatus(lastSyncStatus)) {
        val status = lastSyncStatus.orEmpty()
        val needsReconnect = status.contains("expired", ignoreCase = true) ||
            status.contains("unauthorized", ignoreCase = true) ||
            status.contains("401") ||
            status.contains("403") ||
            status.contains("reconnect", ignoreCase = true)
        val shortError = if (needsReconnect) "Reconnect required" else "Check failed"
        return if (syncTimeText != null) "Checked $syncTimeText • $shortError" else shortError
    }

    if (syncTimeText != null) {
        val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
        return if (detail != null) {
            "Checked $syncTimeText • $detail"
        } else {
            "Checked $syncTimeText"
        }
    }

    return if (syncEnabled) {
        val freq = if (weekdaysOnly) "weekdays" else "daily"
        "Checks hourly $freq, $formattedWindowRange (zero-wake)"
    } else {
        "Auto-capture off"
    }
}

internal fun isSuperchargerScopeError(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    return status.contains("vehicle_charging_cmds", ignoreCase = true) ||
        status.contains("missing", ignoreCase = true) ||
        status.contains("403") ||
        status.contains("forbidden", ignoreCase = true) ||
        status.contains("reconnect", ignoreCase = true)
}

internal fun formatSuperchargerSubtitle(
    teslaConnected: Boolean,
    hasChargingScope: Boolean,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    syncEnabled: Boolean,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!teslaConnected) return "Connect Tesla to pull paid Supercharger sessions"
    if (!hasChargingScope || isSuperchargerScopeError(lastSyncStatus)) {
        return "Enable vehicle_charging_cmds scope to sync"
    }

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    if (syncTimeText != null) {
        val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
        return if (detail != null) {
            "Synced $syncTimeText • $detail"
        } else {
            "Synced $syncTimeText"
        }
    }

    return if (syncEnabled) {
        "Ready • Zero-wake sync with daily charging sync"
    } else {
        "Auto-sync off"
    }
}

internal fun formatSmartcarDcSubtitle(
    smartcarConnected: Boolean,
    capability: String,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    syncEnabled: Boolean,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!smartcarConnected) return "Connect Smartcar to check DC fast charging"

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    return when (capability) {
        PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION ->
            "Enable charging permissions in Smartcar dashboard"
        PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED ->
            "History unavailable on this vehicle • use receipt scan or Tesla Supercharger sync"
        PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY -> {
            val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
            if (syncTimeText != null && detail != null) {
                "Live DC only • Checked $syncTimeText • $detail"
            } else {
                "Live DC fast-charge detection available (no history)"
            }
        }
        PreferencesManager.SMARTCAR_CAPABILITY_CHARGE_RECORDS -> {
            val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
            when {
                syncTimeText != null && detail != null -> "Synced $syncTimeText • $detail"
                syncTimeText != null -> "Synced $syncTimeText"
                syncEnabled -> "Supported • Syncs with daily charging sync"
                else -> "Supported • Auto-sync off"
            }
        }
        else -> {
            if (syncTimeText != null && !lastSyncStatus.isNullOrBlank()) {
                "Checked $syncTimeText • ${lastSyncStatus.trim()}"
            } else {
                "Tap Check / Sync to probe vehicle DC charging support"
            }
        }
    }
}
