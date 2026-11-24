package com.example.car_logger

import com.example.car_logger.ui.screens.home.tabs.formatVehicleSyncSubtitle
import com.example.car_logger.ui.screens.home.tabs.formatWallConnectorSubtitle
import com.example.car_logger.ui.screens.home.tabs.isWallConnectorErrorStatus
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
}
