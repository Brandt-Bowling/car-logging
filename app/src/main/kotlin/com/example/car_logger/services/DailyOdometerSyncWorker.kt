package com.example.car_logger.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.car_logger.CarLoggerApplication
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DailyOdometerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyOdometerWorker"

        suspend fun executeSyncLogic(
            isDailyPollEnabled: Boolean,
            syncAction: suspend () -> Unit
        ): Result {
            if (!isDailyPollEnabled) {
                return Result.success()
            }
            return try {
                syncAction()
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Nightly sync failed: ${e.message}", e)
                Result.retry()
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as? CarLoggerApplication)?.container
        val prefs = container?.preferencesManager ?: PreferencesManager(applicationContext)
        val database = container?.database ?: AppDatabase.getInstance(applicationContext)
        val carDao = database.carDao()
        val syncService = container?.odometerSyncService ?: OdometerSyncService(
            preferencesManager = prefs,
            carDao = carDao,
            odometerLogDao = database.odometerLogDao(),
            chargingSessionDao = database.chargingSessionDao()
        )

        executeSyncLogic(
            isDailyPollEnabled = prefs.getDailyPollEnabled()
        ) {
            val entities = carDao.getAllCarsList()
            val models = entities.map { it.toModel() }
            syncService.syncDailyEndOfDay(models)
        }
    }
}
