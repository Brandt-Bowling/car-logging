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

class DailyChargerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyChargerSyncWorker"

        suspend fun executeSyncLogic(
            isChargerSyncEnabled: Boolean,
            syncAction: suspend () -> Unit
        ): Result {
            if (!isChargerSyncEnabled) {
                return Result.success()
            }
            return try {
                syncAction()
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled Wall Connector sync failed: ${e.message}", e)
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
            isChargerSyncEnabled = prefs.getChargerSyncEnabled()
        ) {
            val entities = carDao.getAllCarsList()
            val models = entities.map { it.toModel() }
            syncService.syncWallConnectorCharging(models)

            if (prefs.getSuperchargerSyncEnabled()) {
                try {
                    syncService.syncTeslaSuperchargerCharging(models)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Scheduled Supercharger sync skipped/failed: ${e.message}")
                }
            }

            if (prefs.getSmartcarDcSyncEnabled()) {
                try {
                    syncService.syncSmartcarDcCharging(
                        cars = models,
                        forceCapabilityRecheck = false,
                        allowLiveProbe = false
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Scheduled Smartcar DC sync skipped/failed: ${e.message}")
                }
            }
        }
    }
}
