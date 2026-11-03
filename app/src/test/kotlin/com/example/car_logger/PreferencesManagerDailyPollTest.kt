package com.example.car_logger

import com.example.car_logger.data.local.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesManagerDailyPollTest {
    @Test
    fun `verify preferences keys are defined with correct names`() {
        assertEquals("telemetry_daily_poll_enabled", PreferencesManager.KEY_DAILY_POLL_ENABLED.name)
        assertEquals("telemetry_last_daily_poll_time", PreferencesManager.KEY_LAST_DAILY_POLL_TIME.name)
        assertEquals("telemetry_last_daily_poll_status", PreferencesManager.KEY_LAST_DAILY_POLL_STATUS.name)
    }
}
