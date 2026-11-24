package com.example.car_logger

import android.app.Application
import android.util.Log
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.migration.FlutterDataMigrator
import com.example.car_logger.services.ChargerSyncScheduler
import com.example.car_logger.services.DailyPollScheduler
import com.example.car_logger.services.WorkChargingSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CarLoggerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)

        // Run automated migration on startup to preserve all existing user data from Flutter
        applicationScope.launch {
            try {
                FlutterDataMigrator.migrateIfNeeded(this@CarLoggerApplication, database, preferencesManager)
                FlutterDataMigrator.cleanupCorruptedOdometerLogs(database)
            } catch (e: Exception) {
                Log.e("CarLoggerApplication", "Migration error: ${e.message}", e)
            }

            try {
                if (preferencesManager.getDailyPollEnabled()) {
                    DailyPollScheduler.schedule(this@CarLoggerApplication)
                }
                if (preferencesManager.getChargerSyncEnabled()) {
                    ChargerSyncScheduler.schedule(
                        context = this@CarLoggerApplication,
                        targetHour = preferencesManager.getChargerSyncHour(),
                        targetMinute = preferencesManager.getChargerSyncMinute()
                    )
                }
                if (preferencesManager.getWorkChargingSyncEnabled()) {
                    WorkChargingSyncScheduler.schedule(
                        context = this@CarLoggerApplication,
                        startHour = preferencesManager.getWorkChargingWindowStartHour(),
                        startMinute = preferencesManager.getWorkChargingWindowStartMinute(),
                        endHour = preferencesManager.getWorkChargingWindowEndHour(),
                        endMinute = preferencesManager.getWorkChargingWindowEndMinute()
                    )
                }
            } catch (e: Exception) {
                Log.e("CarLoggerApplication", "Scheduler init error: ${e.message}", e)
            }
        }
    }
}
