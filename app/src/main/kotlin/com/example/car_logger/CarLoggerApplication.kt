package com.example.car_logger

import android.app.Application
import android.util.Log
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.migration.FlutterDataMigrator
import com.example.car_logger.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CarLoggerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    val database: AppDatabase
        get() = container.database

    val preferencesManager: PreferencesManager
        get() = container.preferencesManager

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)

        // Run automated migration on startup to preserve all existing user data from Flutter
        applicationScope.launch {
            try {
                FlutterDataMigrator.migrateIfNeeded(this@CarLoggerApplication, database, preferencesManager)
                FlutterDataMigrator.cleanupCorruptedOdometerLogs(database)
            } catch (e: Exception) {
                Log.e("CarLoggerApplication", "Migration error: ${e.message}", e)
            }

            try {
                val scheduler = container.syncSchedulerController
                if (preferencesManager.getDailyPollEnabled()) {
                    scheduler.scheduleDailyOdometerPoll()
                }
                if (preferencesManager.getChargerSyncEnabled()) {
                    scheduler.scheduleChargerSync(
                        targetHour = preferencesManager.getChargerSyncHour(),
                        targetMinute = preferencesManager.getChargerSyncMinute()
                    )
                }
                if (preferencesManager.getWorkChargingSyncEnabled()) {
                    scheduler.scheduleWorkChargingSync(
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
