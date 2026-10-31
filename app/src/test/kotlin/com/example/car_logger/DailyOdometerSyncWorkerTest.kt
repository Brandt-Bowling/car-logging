package com.example.car_logger

import androidx.work.ListenableWorker.Result
import com.example.car_logger.services.DailyOdometerSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DailyOdometerSyncWorkerTest {

    @Test
    fun `executeSync rethrows CancellationException when cancelled`() = runBlocking {
        try {
            DailyOdometerSyncWorker.executeSyncLogic(
                isDailyPollEnabled = true,
                syncAction = { throw CancellationException("Job cancelled by WorkManager") }
            )
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Job cancelled by WorkManager", e.message)
        }
    }

    @Test
    fun `executeSync skips action and returns success when daily poll disabled`() = runBlocking {
        var actionExecuted = false
        val result = DailyOdometerSyncWorker.executeSyncLogic(
            isDailyPollEnabled = false,
            syncAction = { actionExecuted = true }
        )
        assertEquals(Result.success(), result)
        assertEquals(false, actionExecuted)
    }

    @Test
    fun `executeSync returns retry when generic Exception occurs`() = runBlocking {
        val result = DailyOdometerSyncWorker.executeSyncLogic(
            isDailyPollEnabled = true,
            syncAction = { throw java.io.IOException("Network timeout") }
        )
        assertEquals(Result.retry(), result)
    }
}
