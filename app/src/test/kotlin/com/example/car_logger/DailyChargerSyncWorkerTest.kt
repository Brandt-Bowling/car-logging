package com.example.car_logger

import androidx.work.ListenableWorker.Result
import com.example.car_logger.services.DailyChargerSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DailyChargerSyncWorkerTest {

    @Test
    fun `executeSync rethrows CancellationException when cancelled`() = runBlocking {
        try {
            DailyChargerSyncWorker.executeSyncLogic(
                isChargerSyncEnabled = true,
                syncAction = { throw CancellationException("Job cancelled by WorkManager") }
            )
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Job cancelled by WorkManager", e.message)
        }
    }

    @Test
    fun `executeSync skips action and returns success when charger sync disabled`() = runBlocking {
        var actionExecuted = false
        val result = DailyChargerSyncWorker.executeSyncLogic(
            isChargerSyncEnabled = false,
            syncAction = { actionExecuted = true }
        )
        assertEquals(Result.success(), result)
        assertEquals(false, actionExecuted)
    }

    @Test
    fun `executeSync returns retry when generic Exception occurs`() = runBlocking {
        val result = DailyChargerSyncWorker.executeSyncLogic(
            isChargerSyncEnabled = true,
            syncAction = { throw java.io.IOException("Network timeout") }
        )
        assertEquals(Result.retry(), result)
    }
}
