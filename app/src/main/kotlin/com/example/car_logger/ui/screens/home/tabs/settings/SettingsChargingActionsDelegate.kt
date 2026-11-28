package com.example.car_logger.ui.screens.home.tabs.settings

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SyncSchedulerController
import com.example.car_logger.services.WallConnectorType
import com.example.car_logger.services.WorkChargingSyncResult
import com.example.car_logger.services.WorkLocationCaptureResult
import java.util.Locale

internal class SettingsChargingActionsDelegate(
    private val preferencesManager: PreferencesManager,
    private val odometerSyncService: OdometerSyncService,
    private val syncSchedulerController: SyncSchedulerController,
    private val emitEvent: suspend (SettingsUiEvent) -> Unit,
    private val setDialog: (SettingsDialogState) -> Unit,
    private val currentUiState: () -> SettingsUiState,
    private val currentCars: suspend () -> List<Car>
) {
    suspend fun syncWallConnectors() {
        emitEvent(SettingsUiEvent.ShowToast("Syncing Wall Connectors..."))
        val res = odometerSyncService.syncWallConnectorCharging(currentCars())
        emitEvent(
            SettingsUiEvent.ShowToast(OdometerSyncService.formatWallConnectorSyncMessage(res), longDuration = true)
        )
    }

    suspend fun assignWallConnector(connectorType: WallConnectorType, selected: Car) {
        val state = currentUiState().homeCharging
        val activeEvs = state.cars.filter { !it.isArchived }.ifEmpty { state.cars }
        val mappedGen3 = OdometerSyncService.resolveCarForWallConnector(
            connectorType = WallConnectorType.GEN_3,
            cars = activeEvs,
            gen3OverrideCarId = state.wallConnectorGen3CarId,
            universalOverrideCarId = state.wallConnectorUniversalCarId
        )
        val mappedUniversal = OdometerSyncService.resolveCarForWallConnector(
            connectorType = WallConnectorType.UNIVERSAL,
            cars = activeEvs,
            gen3OverrideCarId = state.wallConnectorGen3CarId,
            universalOverrideCarId = state.wallConnectorUniversalCarId
        )
        val gen3Id = if (connectorType == WallConnectorType.GEN_3) selected.id else (state.wallConnectorGen3CarId ?: mappedGen3?.id)
        val universalId = if (connectorType == WallConnectorType.UNIVERSAL) selected.id else (state.wallConnectorUniversalCarId ?: mappedUniversal?.id)
        preferencesManager.setWallConnectorCarMapping(gen3CarId = gen3Id, universalCarId = universalId)
        val vehicleLabel = selected.model.ifBlank { selected.displayName }
        val prefix = if (connectorType == WallConnectorType.GEN_3) "Gen 3" else "Universal"
        emitEvent(SettingsUiEvent.ShowToast("$prefix assigned to $vehicleLabel"))
    }

    suspend fun setChargerSyncEnabled(enabled: Boolean) {
        preferencesManager.setChargerSyncEnabled(enabled)
        val home = currentUiState().homeCharging
        if (enabled) {
            syncSchedulerController.scheduleChargerSync(home.chargerSyncHour, home.chargerSyncMinute)
        } else {
            syncSchedulerController.cancelChargerSync()
        }
    }

    suspend fun saveChargerSyncTime(hour: Int, minute: Int) {
        setDialog(SettingsDialogState.None)
        preferencesManager.setChargerSyncTime(hour, minute)
        if (currentUiState().homeCharging.chargerSyncEnabled) {
            syncSchedulerController.scheduleChargerSync(hour, minute)
        }
    }

    suspend fun syncWorkCharging(forceWake: Boolean) {
        if (forceWake) setDialog(SettingsDialogState.None)
        val prompt = if (forceWake) "Waking Tesla to check work charge..." else "Checking Tesla for work charge..."
        emitEvent(SettingsUiEvent.ShowToast(prompt))
        val res = odometerSyncService.syncTeslaWorkCharging(currentCars(), forceWake = forceWake)
        if (!forceWake && res is WorkChargingSyncResult.SkippedAsleep) {
            setDialog(SettingsDialogState.WorkChargeWakeConfirm)
        } else {
            emitEvent(
                SettingsUiEvent.ShowToast(OdometerSyncService.formatWorkChargingSyncMessage(res), longDuration = true)
            )
        }
    }

    suspend fun setWorkChargingSyncEnabled(enabled: Boolean) {
        preferencesManager.setWorkChargingSyncEnabled(enabled)
        val work = currentUiState().workCharging
        if (enabled) {
            syncSchedulerController.scheduleWorkChargingSync(
                startHour = work.workWindowStartHour,
                startMinute = work.workWindowStartMinute,
                endHour = work.workWindowEndHour,
                endMinute = work.workWindowEndMinute
            )
        } else {
            syncSchedulerController.cancelWorkChargingSync()
        }
    }

    suspend fun saveWorkChargingWindow(isStart: Boolean, hour: Int, minute: Int) {
        setDialog(SettingsDialogState.None)
        val work = currentUiState().workCharging
        val newStartH = if (isStart) hour else work.workWindowStartHour
        val newStartM = if (isStart) minute else work.workWindowStartMinute
        val newEndH = if (isStart) work.workWindowEndHour else hour
        val newEndM = if (isStart) work.workWindowEndMinute else minute
        preferencesManager.setWorkChargingWindow(newStartH, newStartM, newEndH, newEndM)
        if (work.workChargingSyncEnabled) {
            syncSchedulerController.scheduleWorkChargingSync(
                startHour = preferencesManager.getWorkChargingWindowStartHour(),
                startMinute = preferencesManager.getWorkChargingWindowStartMinute(),
                endHour = preferencesManager.getWorkChargingWindowEndHour(),
                endMinute = preferencesManager.getWorkChargingWindowEndMinute()
            )
        }
    }

    suspend fun clearWorkLocation() {
        preferencesManager.clearWorkLocation()
        emitEvent(SettingsUiEvent.ShowToast("Cleared work GPS location"))
    }

    suspend fun captureWorkLocation(forceWake: Boolean) {
        if (forceWake) setDialog(SettingsDialogState.None)
        val prompt = if (forceWake) "Waking Tesla to save work location..." else "Reading Tesla location..."
        emitEvent(SettingsUiEvent.ShowToast(prompt))
        when (val result = odometerSyncService.captureCurrentTeslaWorkLocation(currentCars(), forceWake = forceWake)) {
            is WorkLocationCaptureResult.Saved -> emitEvent(
                SettingsUiEvent.ShowToast(
                    "Saved work GPS (${String.format(Locale.US, "%.4f, %.4f", result.latitude, result.longitude)})",
                    longDuration = true
                )
            )
            is WorkLocationCaptureResult.MissingScope -> {
                setDialog(SettingsDialogState.WorkLocationMissingScope)
                if (forceWake) emitEvent(SettingsUiEvent.ShowToast(result.message, longDuration = true))
            }
            is WorkLocationCaptureResult.SkippedAsleep -> {
                if (!forceWake) {
                    setDialog(SettingsDialogState.WorkLocationWakeConfirm)
                } else {
                    emitEvent(SettingsUiEvent.ShowToast(result.message, longDuration = true))
                }
            }
            WorkLocationCaptureResult.NotConfigured ->
                emitEvent(SettingsUiEvent.ShowToast("Connect Tesla first", longDuration = forceWake))
            is WorkLocationCaptureResult.Error ->
                emitEvent(SettingsUiEvent.ShowToast(result.message, longDuration = true))
        }
    }

    suspend fun syncSuperchargers() {
        emitEvent(SettingsUiEvent.ShowToast("Syncing Tesla Superchargers..."))
        val res = odometerSyncService.syncTeslaSuperchargerCharging(currentCars())
        emitEvent(
            SettingsUiEvent.ShowToast(OdometerSyncService.formatSuperchargerSyncMessage(res), longDuration = true)
        )
    }

    suspend fun syncSmartcarDc() {
        emitEvent(SettingsUiEvent.ShowToast("Checking Smartcar DC charging..."))
        val res = odometerSyncService.syncSmartcarDcCharging(
            cars = currentCars(),
            forceCapabilityRecheck = true,
            allowLiveProbe = true
        )
        emitEvent(
            SettingsUiEvent.ShowToast(OdometerSyncService.formatSmartcarDcSyncMessage(res), longDuration = true)
        )
    }

    suspend fun reconnectSmartcarDc() {
        val (clientId, _) = odometerSyncService.getEffectiveSmartcarCredentials()
        val url = odometerSyncService.resolveSmartcarAuthUrl(clientId.orEmpty())
        emitEvent(SettingsUiEvent.OpenExternalUrl(url))
    }
}
