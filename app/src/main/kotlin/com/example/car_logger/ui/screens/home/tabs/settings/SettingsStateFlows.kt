package com.example.car_logger.ui.screens.home.tabs.settings

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal fun buildSettingsUiStateFlow(
    carsFlow: Flow<List<Car>>,
    preferencesManager: PreferencesManager,
    googleDriveService: GoogleDriveService,
    dialogState: StateFlow<SettingsDialogState>
): Flow<SettingsUiState> {
    val generalBackupFlow = buildGeneralBackupFlow(preferencesManager, googleDriveService)
    val vehicleSyncFlow = buildVehicleSyncFlow(carsFlow, preferencesManager)
    val homeChargingFlow = buildHomeChargingFlow(carsFlow, preferencesManager)
    val workChargingFlow = buildWorkChargingFlow(carsFlow, preferencesManager)
    val publicChargingFlow = buildPublicChargingFlow(carsFlow, preferencesManager)

    return combine(
        generalBackupFlow,
        combine(vehicleSyncFlow, homeChargingFlow, workChargingFlow, publicChargingFlow) { v, h, w, p ->
            arrayOf(v, h, w, p)
        },
        dialogState
    ) { general, sections, dialog ->
        SettingsUiState(
            generalBackup = general,
            vehicleSync = sections[0] as VehicleSyncSettingsState,
            homeCharging = sections[1] as HomeChargingSettingsState,
            workCharging = sections[2] as WorkChargingSettingsState,
            publicCharging = sections[3] as PublicChargingSettingsState,
            dialog = dialog
        )
    }
}

private fun buildGeneralBackupFlow(
    preferencesManager: PreferencesManager,
    googleDriveService: GoogleDriveService
): Flow<GeneralBackupSettingsState> = combine(
    combine(
        preferencesManager.garageNameFlow,
        googleDriveService.currentUser,
        preferencesManager.geminiApiKeyFlow
    ) { garageName, user, geminiKey ->
        Triple(garageName, user, geminiKey.orEmpty())
    },
    combine(
        preferencesManager.autoDevApiKeyFlow,
        preferencesManager.smartcarConnectedFlow,
        preferencesManager.teslaConnectedFlow
    ) { autoDevKey, smartcarConnected, teslaConnected ->
        Triple(autoDevKey.orEmpty(), smartcarConnected, teslaConnected)
    }
) { left, right ->
    GeneralBackupSettingsState(
        garageName = left.first,
        isDriveConnected = left.second != null,
        driveEmail = left.second?.email.orEmpty(),
        geminiKey = left.third,
        autoDevKey = right.first,
        smartcarConnected = right.second,
        teslaConnected = right.third
    )
}

@Suppress("UNCHECKED_CAST")
private fun buildVehicleSyncFlow(
    carsFlow: Flow<List<Car>>,
    preferencesManager: PreferencesManager
): Flow<VehicleSyncSettingsState> = combine(
    combine(
        carsFlow,
        preferencesManager.wakePolicyFlow,
        preferencesManager.syncCooldownHoursFlow,
        preferencesManager.smartcarConnectedFlow
    ) { cars, wakePolicy, cooldownHours, smartcarConnected ->
        arrayOf(cars, wakePolicy, cooldownHours, smartcarConnected)
    },
    combine(
        preferencesManager.teslaConnectedFlow,
        preferencesManager.dailyPollEnabledFlow,
        preferencesManager.lastDailyPollTimeFlow,
        preferencesManager.lastDailyPollStatusFlow
    ) { teslaConnected, dailyPollEnabled, lastPollTime, lastPollStatus ->
        arrayOf<Any?>(teslaConnected, dailyPollEnabled, lastPollTime, lastPollStatus)
    }
) { left, right ->
    VehicleSyncSettingsState(
        cars = left[0] as List<Car>,
        wakePolicy = left[1] as WakeProtectionPolicy,
        cooldownHours = left[2] as Int,
        smartcarConnected = left[3] as Boolean,
        teslaConnected = right[0] as Boolean,
        dailyPollEnabled = right[1] as Boolean,
        lastDailyPollTime = right[2] as Long,
        lastDailyPollStatus = right[3] as String?
    )
}

@Suppress("UNCHECKED_CAST")
private fun buildHomeChargingFlow(
    carsFlow: Flow<List<Car>>,
    preferencesManager: PreferencesManager
): Flow<HomeChargingSettingsState> = combine(
    combine(
        carsFlow,
        preferencesManager.teslaConnectedFlow,
        preferencesManager.wallConnectorGen3CarIdFlow,
        preferencesManager.wallConnectorUniversalCarIdFlow
    ) { cars, teslaConnected, gen3Id, universalId ->
        arrayOf(cars, teslaConnected, gen3Id, universalId)
    },
    combine(
        preferencesManager.chargerSyncEnabledFlow,
        preferencesManager.chargerSyncHourFlow,
        preferencesManager.chargerSyncMinuteFlow,
        preferencesManager.lastChargerSyncTimeFlow,
        preferencesManager.lastChargerSyncStatusFlow
    ) { enabled, hour, minute, lastTime, lastStatus ->
        arrayOf<Any?>(enabled, hour, minute, lastTime, lastStatus)
    }
) { left, right ->
    HomeChargingSettingsState(
        cars = left[0] as List<Car>,
        teslaConnected = left[1] as Boolean,
        wallConnectorGen3CarId = left[2] as String?,
        wallConnectorUniversalCarId = left[3] as String?,
        chargerSyncEnabled = right[0] as Boolean,
        chargerSyncHour = right[1] as Int,
        chargerSyncMinute = right[2] as Int,
        lastChargerSyncTime = right[3] as Long,
        lastChargerSyncStatus = right[4] as String?
    )
}

@Suppress("UNCHECKED_CAST")
private fun buildWorkChargingFlow(
    carsFlow: Flow<List<Car>>,
    preferencesManager: PreferencesManager
): Flow<WorkChargingSettingsState> = combine(
    combine(
        carsFlow,
        preferencesManager.teslaConnectedFlow,
        preferencesManager.teslaAccessTokenFlow,
        preferencesManager.workChargingSyncEnabledFlow,
        preferencesManager.workChargingWeekdaysOnlyFlow
    ) { cars, teslaConnected, token, enabled, weekdaysOnly ->
        arrayOf(cars, teslaConnected, token, enabled, weekdaysOnly)
    },
    combine(
        preferencesManager.workChargingWindowStartHourFlow,
        preferencesManager.workChargingWindowStartMinuteFlow,
        preferencesManager.workChargingWindowEndHourFlow,
        preferencesManager.workChargingWindowEndMinuteFlow
    ) { startH, startM, endH, endM ->
        intArrayOf(startH, startM, endH, endM)
    },
    combine(
        preferencesManager.lastWorkChargingSyncTimeFlow,
        preferencesManager.lastWorkChargingSyncStatusFlow,
        preferencesManager.workLocationLatFlow,
        preferencesManager.workLocationLngFlow,
        preferencesManager.workLocationRadiusMetersFlow
    ) { lastTime, lastStatus, lat, lng, radius ->
        arrayOf<Any?>(lastTime, lastStatus, lat, lng, radius)
    }
) { flags, window, geo ->
    val token = flags[2] as String?
    WorkChargingSettingsState(
        cars = flags[0] as List<Car>,
        teslaConnected = flags[1] as Boolean,
        hasTeslaLocationScope = OdometerSyncService.hasTeslaLocationScope(token),
        hasTeslaChargingHistoryScope = OdometerSyncService.hasTeslaChargingHistoryScope(token),
        workChargingSyncEnabled = flags[3] as Boolean,
        workChargingWeekdaysOnly = flags[4] as Boolean,
        workWindowStartHour = window[0],
        workWindowStartMinute = window[1],
        workWindowEndHour = window[2],
        workWindowEndMinute = window[3],
        lastWorkChargingSyncTime = geo[0] as Long,
        lastWorkChargingSyncStatus = geo[1] as String?,
        workLocationLat = geo[2] as Double?,
        workLocationLng = geo[3] as Double?,
        workLocationRadiusMeters = geo[4] as Int
    )
}

@Suppress("UNCHECKED_CAST")
private fun buildPublicChargingFlow(
    carsFlow: Flow<List<Car>>,
    preferencesManager: PreferencesManager
): Flow<PublicChargingSettingsState> = combine(
    combine(
        carsFlow,
        preferencesManager.teslaConnectedFlow,
        preferencesManager.smartcarConnectedFlow,
        preferencesManager.teslaAccessTokenFlow
    ) { cars, teslaConnected, smartcarConnected, token ->
        arrayOf(cars, teslaConnected, smartcarConnected, token)
    },
    combine(
        preferencesManager.superchargerSyncEnabledFlow,
        preferencesManager.lastSuperchargerSyncTimeFlow,
        preferencesManager.lastSuperchargerSyncStatusFlow
    ) { scEnabled, scLastTime, scLastStatus ->
        Triple(scEnabled, scLastTime, scLastStatus)
    },
    combine(
        preferencesManager.smartcarDcSyncEnabledFlow,
        preferencesManager.smartcarDcCapabilityFlow,
        preferencesManager.lastSmartcarDcSyncTimeFlow,
        preferencesManager.lastSmartcarDcSyncStatusFlow
    ) { dcEnabled, dcCap, dcLastTime, dcLastStatus ->
        arrayOf<Any?>(dcEnabled, dcCap, dcLastTime, dcLastStatus)
    }
) { auth, sc, dc ->
    val token = auth[3] as String?
    PublicChargingSettingsState(
        cars = auth[0] as List<Car>,
        teslaConnected = auth[1] as Boolean,
        smartcarConnected = auth[2] as Boolean,
        hasTeslaLocationScope = OdometerSyncService.hasTeslaLocationScope(token),
        hasTeslaChargingHistoryScope = OdometerSyncService.hasTeslaChargingHistoryScope(token),
        superchargerSyncEnabled = sc.first,
        lastSuperchargerSyncTime = sc.second,
        lastSuperchargerSyncStatus = sc.third,
        smartcarDcSyncEnabled = dc[0] as Boolean,
        smartcarDcCapability = dc[1] as String,
        lastSmartcarDcSyncTime = dc[2] as Long,
        lastSmartcarDcSyncStatus = dc[3] as String?
    )
}
