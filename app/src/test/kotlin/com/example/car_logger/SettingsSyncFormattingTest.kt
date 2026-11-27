package com.example.car_logger

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.ui.screens.home.tabs.settings.formatSmartcarDcSubtitle
import com.example.car_logger.ui.screens.home.tabs.settings.formatSuperchargerSubtitle
import com.example.car_logger.ui.screens.home.tabs.settings.formatVehicleSyncSubtitle
import com.example.car_logger.ui.screens.home.tabs.settings.formatWallConnectorSubtitle
import com.example.car_logger.ui.screens.home.tabs.settings.formatWorkChargingSubtitle
import com.example.car_logger.ui.screens.home.tabs.settings.isSuperchargerScopeError
import com.example.car_logger.ui.screens.home.tabs.settings.isWallConnectorErrorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSyncFormattingTest {

    private val nowMs = 1_800_000_000_000L
    private val hourMs = 3_600_000L

    @Test
    fun `formatVehicleSyncSubtitle shows next auto-sync countdown when within cooldown`() {
        val lastSyncMs = nowMs - (21 * hourMs)
        val subtitle = formatVehicleSyncSubtitle(
            isConnected = true,
            lastSyncTimeMs = lastSyncMs,
            cooldownHours = 24,
            showSyncedWhenDisconnected = false,
            nowMs = nowMs
        )
        assertEquals("Synced 21h ago • Next auto-sync in 3h", subtitle)
    }

    @Test
    fun `formatVehicleSyncSubtitle omits countdown once cooldown expires`() {
        val lastSyncMs = nowMs - (48 * hourMs)
        val subtitle = formatVehicleSyncSubtitle(
            isConnected = true,
            lastSyncTimeMs = lastSyncMs,
            cooldownHours = 24,
            showSyncedWhenDisconnected = false,
            nowMs = nowMs
        )
        assertEquals("Synced 2d ago", subtitle)
    }

    @Test
    fun `formatVehicleSyncSubtitle handles connected without prior sync`() {
        val subtitle = formatVehicleSyncSubtitle(
            isConnected = true,
            lastSyncTimeMs = null,
            cooldownHours = 24,
            showSyncedWhenDisconnected = false,
            nowMs = nowMs
        )
        assertEquals("Connected • Not yet synced", subtitle)
    }

    @Test
    fun `formatVehicleSyncSubtitle handles disconnected mode flags`() {
        val lastSyncMs = nowMs - (21 * hourMs)
        assertEquals(
            "Not connected",
            formatVehicleSyncSubtitle(
                isConnected = false,
                lastSyncTimeMs = lastSyncMs,
                cooldownHours = 24,
                showSyncedWhenDisconnected = false,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Synced 21h ago • Next auto-sync in 3h",
            formatVehicleSyncSubtitle(
                isConnected = false,
                lastSyncTimeMs = lastSyncMs,
                cooldownHours = 24,
                showSyncedWhenDisconnected = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Not connected",
            formatVehicleSyncSubtitle(
                isConnected = false,
                lastSyncTimeMs = null,
                cooldownHours = 24,
                showSyncedWhenDisconnected = true,
                nowMs = nowMs
            )
        )
    }

    @Test
    fun `formatWallConnectorSubtitle tightens persisted session counts and up-to-date messages`() {
        val lastSync13h = nowMs - (13 * hourMs)
        assertEquals(
            "Synced 13h ago • 3 sessions (+88.8 kWh)",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync13h,
                lastSyncStatus = "Synced 3 Wall Connector sessions (+88.8 kWh)",
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )

        val lastSync2h = nowMs - (2 * hourMs)
        assertEquals(
            "Synced 2h ago • 1 session (+12.4 kWh)",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync2h,
                lastSyncStatus = "Synced 1 Wall Connector session (+12.4 kWh)",
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )

        assertEquals(
            "Synced 2h ago • Up to date",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync2h,
                lastSyncStatus = "Wall Connectors up to date (5 sessions checked)",
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )

        assertEquals(
            "Synced 2h ago • Up to date",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync2h,
                lastSyncStatus = "Wall Connectors synced (no new sessions)",
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )
    }

    @Test
    fun `formatWallConnectorSubtitle handles errors and disconnected or un-synced states`() {
        val lastSync13h = nowMs - (13 * hourMs)
        assertEquals(
            "Connect Tesla to sync home charging",
            formatWallConnectorSubtitle(
                teslaConnected = false,
                lastSyncTimeMs = lastSync13h,
                lastSyncStatus = "Synced 3 Wall Connector sessions (+88.8 kWh)",
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )

        assertEquals(
            "Last sync 13h ago • Reconnect required",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync13h,
                lastSyncStatus = "Tesla token is missing energy_device_data scope — tap Reconnect in Settings",
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )

        assertEquals(
            "Last sync 13h ago • Sync failed",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync13h,
                lastSyncStatus = "Wall Connector sync failed: Network timeout",
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )

        assertEquals(
            "Syncs daily at 8:00 AM",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                chargerSyncEnabled = true,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )

        assertEquals(
            "Auto-sync off",
            formatWallConnectorSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                chargerSyncEnabled = false,
                formattedSyncTime = "8:00 AM",
                nowMs = nowMs
            )
        )
    }

    @Test
    fun `isWallConnectorErrorStatus distinguishes healthy and error strings`() {
        assertFalse(isWallConnectorErrorStatus(null))
        assertFalse(isWallConnectorErrorStatus(""))
        assertFalse(isWallConnectorErrorStatus("Synced 3 Wall Connector sessions (+88.8 kWh)"))
        assertFalse(isWallConnectorErrorStatus("Wall Connectors up to date (4 sessions checked)"))

        assertTrue(isWallConnectorErrorStatus("Tesla token is missing energy_device_data scope"))
        assertTrue(isWallConnectorErrorStatus("Tesla authorization failed (401)"))
        assertTrue(isWallConnectorErrorStatus("Wall Connector sync failed: Network error"))
    }

    @Test
    fun `formatWorkChargingSubtitle handles disconnected scheduled synced asleep and error states`() {
        val lastSync3h = nowMs - (3 * hourMs)
        val window = "12:00 PM – 5:00 PM"
        assertEquals(
            "Connect Tesla to auto-capture free work charges",
            formatWorkChargingSubtitle(
                teslaConnected = false,
                lastSyncTimeMs = lastSync3h,
                lastSyncStatus = "Logged work charge: 18.4 kWh (Free)",
                syncEnabled = true,
                weekdaysOnly = true,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Checks hourly weekdays, 12:00 PM – 5:00 PM (zero-wake)",
            formatWorkChargingSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = true,
                weekdaysOnly = true,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Checks hourly daily, 12:00 PM – 5:00 PM (zero-wake)",
            formatWorkChargingSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = true,
                weekdaysOnly = false,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Auto-capture off",
            formatWorkChargingSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = false,
                weekdaysOnly = true,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Checked 3h ago • Logged work charge: 18.4 kWh (Free)",
            formatWorkChargingSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync3h,
                lastSyncStatus = "Logged work charge: 18.4 kWh (Free)",
                syncEnabled = true,
                weekdaysOnly = true,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Checked 3h ago • Tesla asleep (zero-wake check skipped)",
            formatWorkChargingSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync3h,
                lastSyncStatus = "Tesla asleep (zero-wake check skipped)",
                syncEnabled = true,
                weekdaysOnly = true,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Checked 3h ago • Reconnect required",
            formatWorkChargingSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync3h,
                lastSyncStatus = "Tesla token expired (401)",
                syncEnabled = true,
                weekdaysOnly = true,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Checked 3h ago • Check failed",
            formatWorkChargingSubtitle(
                teslaConnected = true,
                lastSyncTimeMs = lastSync3h,
                lastSyncStatus = "Work charge sync failed: timeout",
                syncEnabled = true,
                weekdaysOnly = true,
                formattedWindowRange = window,
                nowMs = nowMs
            )
        )
    }

    @Test
    fun `formatSuperchargerSubtitle handles disconnected missingScope ready synced and paused states`() {
        val lastSync4h = nowMs - (4 * hourMs)
        assertEquals(
            "Connect Tesla to pull paid Supercharger sessions",
            formatSuperchargerSubtitle(
                teslaConnected = false,
                hasChargingScope = false,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Enable vehicle_charging_cmds scope to sync",
            formatSuperchargerSubtitle(
                teslaConnected = true,
                hasChargingScope = false,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertTrue(isSuperchargerScopeError("Missing vehicle_charging_cmds scope (403)"))
        assertEquals(
            "Ready • Zero-wake sync with daily charging sync",
            formatSuperchargerSubtitle(
                teslaConnected = true,
                hasChargingScope = true,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Auto-sync off",
            formatSuperchargerSubtitle(
                teslaConnected = true,
                hasChargingScope = true,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = false,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Synced 4h ago • Synced 2 Supercharger sessions (84.2 kWh · $31.40)",
            formatSuperchargerSubtitle(
                teslaConnected = true,
                hasChargingScope = true,
                lastSyncTimeMs = lastSync4h,
                lastSyncStatus = "Synced 2 Supercharger sessions (84.2 kWh · $31.40)",
                syncEnabled = true,
                nowMs = nowMs
            )
        )
    }

    @Test
    fun `formatSmartcarDcSubtitle handles all capability states`() {
        val lastSync2h = nowMs - (2 * hourMs)
        assertEquals(
            "Connect Smartcar to check DC fast charging",
            formatSmartcarDcSubtitle(
                smartcarConnected = false,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_UNKNOWN,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Enable charging permissions in Smartcar dashboard",
            formatSmartcarDcSubtitle(
                smartcarConnected = true,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION,
                lastSyncTimeMs = lastSync2h,
                lastSyncStatus = "Missing permission",
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "History unavailable on this vehicle • use receipt scan or Tesla Supercharger sync",
            formatSmartcarDcSubtitle(
                smartcarConnected = true,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED,
                lastSyncTimeMs = lastSync2h,
                lastSyncStatus = "VEHICLE_NOT_CAPABLE",
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Live DC only • Checked 2h ago • Live DC ready",
            formatSmartcarDcSubtitle(
                smartcarConnected = true,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY,
                lastSyncTimeMs = lastSync2h,
                lastSyncStatus = "Live DC ready",
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Synced 2h ago • Smartcar DC charging up to date",
            formatSmartcarDcSubtitle(
                smartcarConnected = true,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_CHARGE_RECORDS,
                lastSyncTimeMs = lastSync2h,
                lastSyncStatus = "Smartcar DC charging up to date",
                syncEnabled = true,
                nowMs = nowMs
            )
        )
        assertEquals(
            "Tap Check / Sync to probe vehicle DC charging support",
            formatSmartcarDcSubtitle(
                smartcarConnected = true,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_UNKNOWN,
                lastSyncTimeMs = null,
                lastSyncStatus = null,
                syncEnabled = true,
                nowMs = nowMs
            )
        )
    }
}

