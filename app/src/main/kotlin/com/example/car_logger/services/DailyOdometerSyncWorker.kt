package com.example.car_logger.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DailyOdometerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.getDailyPollEnabled()) {
            return@withContext Result.success()
        }

        val database = AppDatabase.getInstance(applicationContext)
        val carDao = database.carDao()
        val odometerLogDao = database.odometerLogDao()
        val syncService = OdometerSyncService(prefs, carDao, odometerLogDao)

        return@withContext try {
            val entities = carDao.getAllCarsList()
            val models = entities.map { it.toModel() }
            syncService.syncDailyEndOfDay(models)
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyOdometerWorker", "Nightly sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
