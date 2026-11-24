package com.example.car_logger

import androidx.work.ListenableWorker.Result
import com.example.car_logger.services.WorkChargingSyncWorker
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkChargingSyncWorkerTest {

    @Test
    fun `executeSyncLogic skips when work charging sync is disabled`() = runBlocking {
        var executed = false
        val result = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = false,
            weekdaysOnly = true,
            dayOfWeek = DayOfWeek.WEDNESDAY,
            currentTime = LocalTime.of(14, 0),
            syncAction = { executed = true }
        )
        assertEquals(Result.success(), result)
        assertFalse(executed)
    }

    @Test
    fun `executeSyncLogic skips Saturday and Sunday when weekdaysOnly is true`() = runBlocking {
        var executed = false
        val satResult = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = true,
            weekdaysOnly = true,
            dayOfWeek = DayOfWeek.SATURDAY,
            currentTime = LocalTime.of(14, 0),
            syncAction = { executed = true }
        )
        val sunResult = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = true,
            weekdaysOnly = true,
            dayOfWeek = DayOfWeek.SUNDAY,
            currentTime = LocalTime.of(14, 0),
            syncAction = { executed = true }
        )
        assertEquals(Result.success(), satResult)
        assertEquals(Result.success(), sunResult)
        assertFalse(executed)
    }

    @Test
    fun `executeSyncLogic skips outside 12_00 PM to 5_00 PM window with zero API calls`() = runBlocking {
        var executed = false
        val morning = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = true,
            weekdaysOnly = true,
            dayOfWeek = DayOfWeek.TUESDAY,
            currentTime = LocalTime.of(9, 0),
            syncAction = { executed = true }
        )
        val evening = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = true,
            weekdaysOnly = true,
            dayOfWeek = DayOfWeek.TUESDAY,
            currentTime = LocalTime.of(19, 0),
            syncAction = { executed = true }
        )
        assertEquals(Result.success(), morning)
        assertEquals(Result.success(), evening)
        assertFalse(executed)
    }

    @Test
    fun `executeSyncLogic runs inside window on weekend when weekdaysOnly is false`() = runBlocking {
        var executed = false
        val result = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = true,
            weekdaysOnly = false,
            dayOfWeek = DayOfWeek.SATURDAY,
            currentTime = LocalTime.of(13, 30),
            syncAction = { executed = true }
        )
        assertEquals(Result.success(), result)
        assertTrue(executed)
    }

    @Test
    fun `executeSyncLogic runs inside window on weekdays and retries on IOException`() = runBlocking {
        var ran = false
        val ok = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = true,
            weekdaysOnly = true,
            dayOfWeek = DayOfWeek.FRIDAY,
            currentTime = LocalTime.of(16, 0),
            syncAction = { ran = true }
        )
        assertEquals(Result.success(), ok)
        assertTrue(ran)

        val retry = WorkChargingSyncWorker.executeSyncLogic(
            isWorkChargingSyncEnabled = true,
            weekdaysOnly = true,
            dayOfWeek = DayOfWeek.MONDAY,
            currentTime = LocalTime.of(12, 30),
            syncAction = { throw IOException("Network timeout") }
        )
        assertEquals(Result.retry(), retry)
    }

    @Test
    fun `executeSyncLogic rethrows CancellationException`() = runBlocking {
        try {
            WorkChargingSyncWorker.executeSyncLogic(
                isWorkChargingSyncEnabled = true,
                weekdaysOnly = true,
                dayOfWeek = DayOfWeek.TUESDAY,
                currentTime = LocalTime.of(15, 0),
                syncAction = { throw CancellationException("Cancelled") }
            )
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }
}
