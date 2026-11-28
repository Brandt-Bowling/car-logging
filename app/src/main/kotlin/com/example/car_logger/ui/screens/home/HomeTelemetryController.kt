package com.example.car_logger.ui.screens.home

import android.net.Uri
import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SmartcarDcSyncResult
import com.example.car_logger.services.SuperchargerSyncResult
import com.example.car_logger.services.WallConnectorSyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

internal class HomeTelemetryController(
    private val scope: CoroutineScope,
    private val carDao: CarDao,
    private val preferencesManager: PreferencesManager,
    private val odometerSyncService: OdometerSyncService,
    private val cars: StateFlow<List<Car>>,
    private val insightsGranularity: StateFlow<InsightsTimeGranularity>,
    private val insightsPeriodOffset: StateFlow<Int>,
    private val tryEmitToast: (String) -> Unit,
    private val emitToast: suspend (String) -> Unit
) {
    fun syncCarOdometer(car: Car, forceWake: Boolean = false) {
        scope.launch {
            val result = odometerSyncService.syncVehicle(car = car, isManual = true, forceWake = forceWake)
            tryEmitToast(OdometerSyncService.formatSyncResultMessage(car, result))
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            odometerSyncService.syncWallConnectorCharging(currentCars)
        }
    }

    fun syncWallConnectorCharging() {
        scope.launch {
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            val result = odometerSyncService.syncWallConnectorCharging(currentCars)
            emitToast(OdometerSyncService.formatWallConnectorSyncMessage(result))
        }
    }

    fun syncWorkCharging() {
        scope.launch {
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            val result = odometerSyncService.syncTeslaWorkCharging(currentCars, forceWake = false)
            emitToast(OdometerSyncService.formatWorkChargingSyncMessage(result))
        }
    }

    fun syncPublicCharging() {
        scope.launch {
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            val (periodStartMs, periodEndMs) = InsightsCalculationEngine.resolvePeriodBounds(
                granularity = insightsGranularity.value,
                periodOffset = insightsPeriodOffset.value,
                nowMs = System.currentTimeMillis()
            )
            val scResult = odometerSyncService.syncTeslaSuperchargerCharging(
                cars = currentCars,
                startInstant = Instant.ofEpochMilli(periodStartMs),
                endInstant = Instant.ofEpochMilli(periodEndMs)
            )
            val smartcarResult = odometerSyncService.syncSmartcarDcCharging(
                cars = currentCars,
                forceCapabilityRecheck = true,
                allowLiveProbe = true
            )
            val parts = buildList {
                if (scResult !is SuperchargerSyncResult.NotConfigured) {
                    add(OdometerSyncService.formatSuperchargerSyncMessage(scResult))
                }
                if (smartcarResult !is SmartcarDcSyncResult.NotConfigured) {
                    val includeSmartcar = (smartcarResult is SmartcarDcSyncResult.Synced && smartcarResult.insertedCount > 0) ||
                        scResult is SuperchargerSyncResult.NotConfigured ||
                        smartcarResult is SmartcarDcSyncResult.MissingPermission
                    if (includeSmartcar) {
                        add(OdometerSyncService.formatSmartcarDcSyncMessage(smartcarResult))
                    }
                }
            }
            emitToast(
                parts.joinToString(" · ").ifBlank { "Connect Tesla or Smartcar in Settings to sync public charging" }
            )
        }
    }

    fun handleOAuthCallback(uri: Uri) {
        scope.launch {
            val isSmartcar = uri.path.orEmpty().contains("smartcar", ignoreCase = true)
            val providerLabel = if (isSmartcar) "Smartcar" else "Tesla"
            val connected = odometerSyncService.handleOAuthCallback(uri)
            if (!connected) {
                val detail = odometerSyncService.lastSyncMessage.value
                tryEmitToast(detail ?: "Couldn't connect $providerLabel")
                return@launch
            }

            tryEmitToast("$providerLabel connected. Syncing odometer...")
            val targetProvider = if (isSmartcar) {
                OdometerSyncService.PROVIDER_SMARTCAR
            } else {
                OdometerSyncService.PROVIDER_TESLA
            }
            val currentCars = carDao.getAllCarsList().map { it.toModel() }
            val matchingCars = currentCars.filter {
                !it.isArchived && OdometerSyncService.resolveProviderForCar(it) == targetProvider
            }
            for (car in matchingCars) {
                val result = odometerSyncService.syncVehicle(car = car, isManual = true, forceWake = true)
                tryEmitToast(OdometerSyncService.formatSyncResultMessage(car, result))
            }
            if (!isSmartcar) {
                notifyTeslaPostConnectChargingSync(currentCars)
            }
        }
    }

    private suspend fun notifyTeslaPostConnectChargingSync(currentCars: List<Car>) {
        val chargerResult = odometerSyncService.syncWallConnectorCharging(currentCars)
        when (chargerResult) {
            is WallConnectorSyncResult.Synced -> {
                if (chargerResult.insertedCount > 0) {
                    tryEmitToast(OdometerSyncService.formatWallConnectorSyncMessage(chargerResult))
                }
            }
            is WallConnectorSyncResult.MissingScope,
            is WallConnectorSyncResult.Error,
            is WallConnectorSyncResult.NoDevices -> {
                tryEmitToast(OdometerSyncService.formatWallConnectorSyncMessage(chargerResult))
            }
            WallConnectorSyncResult.NotConfigured -> Unit
        }

        val (teslaAccess, _, _) = preferencesManager.getTeslaTokens()
        if (OdometerSyncService.hasTeslaChargingHistoryScope(teslaAccess)) {
            val scResult = odometerSyncService.syncTeslaSuperchargerCharging(currentCars)
            if (scResult is SuperchargerSyncResult.Synced && scResult.insertedCount > 0) {
                tryEmitToast(OdometerSyncService.formatSuperchargerSyncMessage(scResult))
            }
        }
    }
}
