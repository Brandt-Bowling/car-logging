package com.example.car_logger

import com.example.car_logger.services.ChargerSyncScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargerSyncSchedulerTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `default schedule time is 8_00 AM`() {
        assertEquals(8, ChargerSyncScheduler.DEFAULT_HOUR)
        assertEquals(0, ChargerSyncScheduler.DEFAULT_MINUTE)
    }

    @Test
    fun `calculateInitialDelay before 8 AM targets today 8 AM`() {
        // 6:30 AM today -> 1.5 hours until 8:00 AM
        val earlyMorning = LocalDateTime.of(2026, 10, 30, 6, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val delay = ChargerSyncScheduler.calculateInitialDelay(
            nowMs = earlyMorning,
            zoneId = zone
        )
        val expectedDelayMs = (1 * 3600 + 30 * 60) * 1000L
        assertEquals(expectedDelayMs, delay)
    }

    @Test
    fun `calculateInitialDelay after 8 AM targets tomorrow 8 AM`() {
        // 11:00 PM today -> 9 hours until tomorrow 8:00 AM
        val night = LocalDateTime.of(2026, 10, 30, 23, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val delay = ChargerSyncScheduler.calculateInitialDelay(
            nowMs = night,
            zoneId = zone
        )
        val expectedDelayMs = 9 * 3600 * 1000L
        assertEquals(expectedDelayMs, delay)
    }

    @Test
    fun `calculateInitialDelay supports custom edited time`() {
        // 8:00 AM today, custom target 9:15 AM -> 1h 15m
        val eightAm = LocalDateTime.of(2026, 10, 30, 8, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val delay = ChargerSyncScheduler.calculateInitialDelay(
            targetHour = 9,
            targetMinute = 15,
            nowMs = eightAm,
            zoneId = zone
        )
        val expectedDelayMs = (1 * 3600 + 15 * 60) * 1000L
        assertEquals(expectedDelayMs, delay)
    }
}
