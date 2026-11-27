package com.example.car_logger.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.car_logger.CarLoggerApplication
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkChargingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WorkChargingSyncWorker"

        suspend fun executeSyncLogic(
            isWorkChargingSyncEnabled: Boolean,
            weekdaysOnly: Boolean,
            startHour: Int = WorkChargingSyncScheduler.DEFAULT_START_HOUR,
            startMinute: Int = WorkChargingSyncScheduler.DEFAULT_START_MINUTE,
            endHour: Int = WorkChargingSyncScheduler.DEFAULT_END_HOUR,
            endMinute: Int = WorkChargingSyncScheduler.DEFAULT_END_MINUTE,
            dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek,
            currentTime: LocalTime = LocalTime.now(),
            syncAction: suspend () -> Unit
        ): Result {
            if (!isWorkChargingSyncEnabled) {
                return Result.success()
            }
            if (weekdaysOnly && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
                return Result.success()
            }
            if (!WorkChargingSyncScheduler.isWithinWindow(currentTime, startHour, startMinute, endHour, endMinute)) {
                return Result.success()
            }
            return try {
                syncAction()
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled Tesla work charging sync failed: ${e.message}", e)
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
            isWorkChargingSyncEnabled = prefs.getWorkChargingSyncEnabled(),
            weekdaysOnly = prefs.getWorkChargingWeekdaysOnly(),
            startHour = prefs.getWorkChargingWindowStartHour(),
            startMinute = prefs.getWorkChargingWindowStartMinute(),
            endHour = prefs.getWorkChargingWindowEndHour(),
            endMinute = prefs.getWorkChargingWindowEndMinute()
        ) {
            val entities = carDao.getAllCarsList()
            val models = entities.map { it.toModel() }
            syncService.syncTeslaWorkCharging(models, forceWake = false)
        }
    }
}
