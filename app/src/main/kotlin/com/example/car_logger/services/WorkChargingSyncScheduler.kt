package com.example.car_logger.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object WorkChargingSyncScheduler {
    const val UNIQUE_WORK_NAME = "daily_tesla_work_charging_sync"
    const val DEFAULT_START_HOUR = 12
    const val DEFAULT_START_MINUTE = 0
    const val DEFAULT_END_HOUR = 17
    const val DEFAULT_END_MINUTE = 0
    const val POLL_INTERVAL_HOURS = 1L
    const val END_WINDOW_GRACE_MINUTES = 20

    fun isWithinWindow(
        localTime: LocalTime,
        startHour: Int = DEFAULT_START_HOUR,
        startMinute: Int = DEFAULT_START_MINUTE,
        endHour: Int = DEFAULT_END_HOUR,
        endMinute: Int = DEFAULT_END_MINUTE,
        endGraceMinutes: Int = END_WINDOW_GRACE_MINUTES
    ): Boolean {
        val startTotalMin = startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
        val rawEndTotalMin = endHour.coerceIn(0, 23) * 60 + endMinute.coerceIn(0, 59)
        val effectiveStart = minOf(startTotalMin, rawEndTotalMin)
        val effectiveEnd = (maxOf(startTotalMin, rawEndTotalMin) + endGraceMinutes.coerceAtLeast(0))
            .coerceAtMost(23 * 60 + 59)
        val nowTotalMin = localTime.hour * 60 + localTime.minute
        return nowTotalMin in effectiveStart..effectiveEnd
    }

    fun calculateInitialDelay(
        startHour: Int = DEFAULT_START_HOUR,
        startMinute: Int = DEFAULT_START_MINUTE,
        endHour: Int = DEFAULT_END_HOUR,
        endMinute: Int = DEFAULT_END_MINUTE,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val clampedStartHour = startHour.coerceIn(0, 23)
        val clampedStartMin = startMinute.coerceIn(0, 59)
        val clampedEndHour = endHour.coerceIn(0, 23)
        val clampedEndMin = endMinute.coerceIn(0, 59)

        val todayStart = nowZdt
            .withHour(clampedStartHour)
            .withMinute(clampedStartMin)
            .withSecond(0)
            .withNano(0)
        val todayEnd = nowZdt
            .withHour(clampedEndHour)
            .withMinute(clampedEndMin)
            .withSecond(0)
            .withNano(0)

        val effectiveStart = if (todayEnd.isBefore(todayStart)) todayEnd else todayStart
        val effectiveEnd = if (todayEnd.isBefore(todayStart)) todayStart else todayEnd

        return when {
            nowZdt.isBefore(effectiveStart) -> Duration.between(nowZdt, effectiveStart).toMillis()
            !nowZdt.isAfter(effectiveEnd) -> 0L
            else -> Duration.between(nowZdt, effectiveStart.plusDays(1)).toMillis()
        }
    }

    fun schedule(
        context: Context,
        startHour: Int = DEFAULT_START_HOUR,
        startMinute: Int = DEFAULT_START_MINUTE,
        endHour: Int = DEFAULT_END_HOUR,
        endMinute: Int = DEFAULT_END_MINUTE
    ) {
        val delayMs = calculateInitialDelay(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WorkChargingSyncWorker>(
            POLL_INTERVAL_HOURS, TimeUnit.HOURS,
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
