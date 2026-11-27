package com.example.car_logger.services

import android.content.Context

interface SyncSchedulerController {
    fun scheduleDailyOdometerPoll()
    fun cancelDailyOdometerPoll()

    fun scheduleChargerSync(
        targetHour: Int = ChargerSyncScheduler.DEFAULT_HOUR,
        targetMinute: Int = ChargerSyncScheduler.DEFAULT_MINUTE
    )

    fun cancelChargerSync()

    fun scheduleWorkChargingSync(
        startHour: Int = WorkChargingSyncScheduler.DEFAULT_START_HOUR,
        startMinute: Int = WorkChargingSyncScheduler.DEFAULT_START_MINUTE,
        endHour: Int = WorkChargingSyncScheduler.DEFAULT_END_HOUR,
        endMinute: Int = WorkChargingSyncScheduler.DEFAULT_END_MINUTE
    )

    fun cancelWorkChargingSync()
}

class AndroidSyncSchedulerController internal constructor(
    private val appContext: Context,
    private val onScheduleDailyPoll: (Context) -> Unit,
    private val onCancelDailyPoll: (Context) -> Unit,
    private val onScheduleChargerSync: (Context, Int, Int) -> Unit,
    private val onCancelChargerSync: (Context) -> Unit,
    private val onScheduleWorkChargingSync: (Context, Int, Int, Int, Int) -> Unit,
    private val onCancelWorkChargingSync: (Context) -> Unit
) : SyncSchedulerController {

    constructor(context: Context) : this(
        appContext = context.applicationContext ?: context,
        onScheduleDailyPoll = DailyPollScheduler::schedule,
        onCancelDailyPoll = DailyPollScheduler::cancel,
        onScheduleChargerSync = ChargerSyncScheduler::schedule,
        onCancelChargerSync = ChargerSyncScheduler::cancel,
        onScheduleWorkChargingSync = WorkChargingSyncScheduler::schedule,
        onCancelWorkChargingSync = WorkChargingSyncScheduler::cancel
    )

    override fun scheduleDailyOdometerPoll() {
        onScheduleDailyPoll(appContext)
    }

    override fun cancelDailyOdometerPoll() {
        onCancelDailyPoll(appContext)
    }

    override fun scheduleChargerSync(targetHour: Int, targetMinute: Int) {
        onScheduleChargerSync(appContext, targetHour, targetMinute)
    }

    override fun cancelChargerSync() {
        onCancelChargerSync(appContext)
    }

    override fun scheduleWorkChargingSync(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        onScheduleWorkChargingSync(appContext, startHour, startMinute, endHour, endMinute)
    }

    override fun cancelWorkChargingSync() {
        onCancelWorkChargingSync(appContext)
    }
}
