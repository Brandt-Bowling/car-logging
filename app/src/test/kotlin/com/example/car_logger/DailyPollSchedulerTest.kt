package com.example.car_logger

import com.example.car_logger.services.DailyPollScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyPollSchedulerTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `calculateInitialDelay before 11 PM targets today 11 PM`() {
        // 9:00 AM today -> 14 hours until 11:00 PM
        val morning = LocalDateTime.of(2026, 10, 30, 9, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val delay = DailyPollScheduler.calculateInitialDelay(
            targetHour = 23,
            targetMinute = 0,
            nowMs = morning,
            zoneId = zone
        )
        val expectedDelayMs = 14 * 3600 * 1000L
        assertEquals(expectedDelayMs, delay)
    }

    @Test
    fun `calculateInitialDelay after 11 PM targets tomorrow 11 PM`() {
        // 11:30 PM today -> 23.5 hours until tomorrow 11:00 PM
        val lateNight = LocalDateTime.of(2026, 10, 30, 23, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val delay = DailyPollScheduler.calculateInitialDelay(
            targetHour = 23,
            targetMinute = 0,
            nowMs = lateNight,
            zoneId = zone
        )
        val expectedDelayMs = (23 * 3600 + 30 * 60) * 1000L
        assertEquals(expectedDelayMs, delay)
    }
}
