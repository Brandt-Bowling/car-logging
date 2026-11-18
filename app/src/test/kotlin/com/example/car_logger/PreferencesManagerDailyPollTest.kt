package com.example.car_logger

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.car_logger.data.local.PreferencesManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesManagerDailyPollTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `verify preferences keys are defined with correct names`() {
        assertEquals("telemetry_daily_poll_enabled", PreferencesManager.KEY_DAILY_POLL_ENABLED.name)
        assertEquals("telemetry_last_daily_poll_time", PreferencesManager.KEY_LAST_DAILY_POLL_TIME.name)
        assertEquals("telemetry_last_daily_poll_status", PreferencesManager.KEY_LAST_DAILY_POLL_STATUS.name)
        assertEquals("telemetry_charger_sync_enabled", PreferencesManager.KEY_CHARGER_SYNC_ENABLED.name)
        assertEquals("telemetry_charger_sync_hour", PreferencesManager.KEY_CHARGER_SYNC_HOUR.name)
        assertEquals("telemetry_charger_sync_minute", PreferencesManager.KEY_CHARGER_SYNC_MINUTE.name)
    }

    @Test
    fun `charger sync schedule defaults to enabled at 8_00 AM and persists edits`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tempFolder.newFolder("prefs"), "charger_prefs.preferences_pb") }
            )
            val prefs = PreferencesManager(dataStore)

            assertTrue(prefs.getChargerSyncEnabled())
            assertEquals(8, prefs.getChargerSyncHour())
            assertEquals(0, prefs.getChargerSyncMinute())

            prefs.setChargerSyncEnabled(false)
            prefs.setChargerSyncTime(6, 45)
            assertFalse(prefs.getChargerSyncEnabled())
            assertEquals(6, prefs.getChargerSyncHour())
            assertEquals(45, prefs.getChargerSyncMinute())

            prefs.setChargerSyncTime(99, -5)
            assertEquals(23, prefs.getChargerSyncHour())
            assertEquals(0, prefs.getChargerSyncMinute())
        } finally {
            scope.cancel()
        }
    }
}
