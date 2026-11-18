package com.example.car_logger.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object ChargerSyncScheduler {
    const val UNIQUE_WORK_NAME = "daily_wall_connector_charging_sync"
    const val DEFAULT_HOUR = 8
    const val DEFAULT_MINUTE = 0

    fun calculateInitialDelay(
        targetHour: Int = DEFAULT_HOUR,
        targetMinute: Int = DEFAULT_MINUTE,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = DailyPollScheduler.calculateInitialDelay(
        targetHour = targetHour,
        targetMinute = targetMinute,
        nowMs = nowMs,
        zoneId = zoneId
    )

    fun schedule(
        context: Context,
        targetHour: Int = DEFAULT_HOUR,
        targetMinute: Int = DEFAULT_MINUTE
    ) {
        val delayMs = calculateInitialDelay(
            targetHour = targetHour.coerceIn(0, 23),
            targetMinute = targetMinute.coerceIn(0, 59)
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DailyChargerSyncWorker>(
            24, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
