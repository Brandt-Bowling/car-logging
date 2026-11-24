package com.example.car_logger

import com.example.car_logger.services.WorkChargingSyncScheduler
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkChargingSyncSchedulerTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `default work window is 12_00 PM to 5_00 PM hourly`() {
        assertEquals(12, WorkChargingSyncScheduler.DEFAULT_START_HOUR)
        assertEquals(0, WorkChargingSyncScheduler.DEFAULT_START_MINUTE)
        assertEquals(17, WorkChargingSyncScheduler.DEFAULT_END_HOUR)
        assertEquals(0, WorkChargingSyncScheduler.DEFAULT_END_MINUTE)
        assertEquals(1L, WorkChargingSyncScheduler.POLL_INTERVAL_HOURS)
    }

    @Test
    fun `calculateInitialDelay targets window start before window, 0 inside window, and next day start after window`() {
        // 10:30 AM -> 1h 30m until 12:00 PM today
        val morning = LocalDateTime.of(2026, 11, 2, 10, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals((1 * 3600 + 30 * 60) * 1000L, WorkChargingSyncScheduler.calculateInitialDelay(nowMs = morning, zoneId = zone))

        // 2:15 PM (inside 12–5 window) -> 0ms delay
        val midAfternoon = LocalDateTime.of(2026, 11, 2, 14, 15, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(0L, WorkChargingSyncScheduler.calculateInitialDelay(nowMs = midAfternoon, zoneId = zone))

        // 6:00 PM (after 5:00 PM) -> 18h until 12:00 PM tomorrow
        val evening = LocalDateTime.of(2026, 11, 2, 18, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(18 * 3600 * 1000L, WorkChargingSyncScheduler.calculateInitialDelay(nowMs = evening, zoneId = zone))
    }

    @Test
    fun `isWithinWindow enforces 12_00 PM to 5_00 PM with end flex grace and supports custom windows`() {
        assertFalse(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(11, 59)))
        assertTrue(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(12, 0)))
        assertTrue(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(14, 30)))
        assertTrue(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(17, 0)))
        // WorkManager flex grace (up to 5:20 PM)
        assertTrue(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(17, 15)))
        assertFalse(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(17, 30)))
        assertFalse(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(22, 0)))

        // Custom 10:30 AM - 6:00 PM window
        assertTrue(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(10, 30), 10, 30, 18, 0))
        assertFalse(WorkChargingSyncScheduler.isWithinWindow(LocalTime.of(10, 15), 10, 30, 18, 0))
    }
}
