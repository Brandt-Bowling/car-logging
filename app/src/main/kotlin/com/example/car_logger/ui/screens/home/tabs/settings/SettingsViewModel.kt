package com.example.car_logger.ui.screens.home.tabs.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SyncSchedulerController
import com.example.car_logger.services.WallConnectorType
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val carDao: CarDao,
    private val preferencesManager: PreferencesManager,
    private val googleDriveService: GoogleDriveService,
    private val odometerSyncService: OdometerSyncService,
    private val syncSchedulerController: SyncSchedulerController
) : ViewModel() {

    private val _dialogState = MutableStateFlow<SettingsDialogState>(SettingsDialogState.None)
    private val _events = MutableSharedFlow<SettingsUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = buildSettingsUiStateFlow(
        carsFlow = carDao.getAllCars().map { list -> list.map(CarEntity::toModel) },
        preferencesManager = preferencesManager,
        googleDriveService = googleDriveService,
        dialogState = _dialogState
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val chargingActions = SettingsChargingActionsDelegate(
        preferencesManager = preferencesManager,
        odometerSyncService = odometerSyncService,
        syncSchedulerController = syncSchedulerController,
        emitEvent = { event -> _events.emit(event) },
        setDialog = { dialog -> _dialogState.value = dialog },
        currentUiState = { uiState.value },
        currentCars = ::currentCars
    )

    fun googleSignInIntent(): Intent = googleDriveService.getSignInIntent()

    fun refreshAuthStatus() {
        viewModelScope.launch {
            val isLinked = odometerSyncService.refreshSmartcarConnectionStatus()
            val gmCar = currentCars().firstOrNull {
                OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR
            }
            if (isLinked && gmCar != null && gmCar.lastOdometerSyncTime == null) {
                val result = odometerSyncService.syncVehicle(gmCar, isManual = true, forceWake = true)
                _events.emit(
                    SettingsUiEvent.ShowToast(
                        message = OdometerSyncService.formatSyncResultMessage(gmCar, result),
                        longDuration = true
                    )
                )
            }
        }
    }

    fun dismissDialog() {
        _dialogState.value = SettingsDialogState.None
    }

    fun openGarageNameDialog() {
        _dialogState.value = SettingsDialogState.GarageName(uiState.value.generalBackup.garageName)
    }

    fun saveGarageName(name: String) {
        viewModelScope.launch {
            preferencesManager.setGarageName(name)
            _dialogState.value = SettingsDialogState.None
        }
    }

    fun openGeminiKeyDialog() {
        _dialogState.value = SettingsDialogState.GeminiKey(uiState.value.generalBackup.geminiKey)
    }

    fun saveGeminiKey(key: String) {
        viewModelScope.launch {
            preferencesManager.setGeminiApiKey(key)
            _dialogState.value = SettingsDialogState.None
        }
    }

    fun openAutoDevKeyDialog() {
        _dialogState.value = SettingsDialogState.AutoDevKey(uiState.value.generalBackup.autoDevKey)
    }

    fun saveAutoDevKey(key: String) {
        viewModelScope.launch {
            preferencesManager.setAutoDevApiKey(key)
            _dialogState.value = SettingsDialogState.None
        }
    }

    fun requestGoogleSignIn() {
        _events.tryEmit(SettingsUiEvent.LaunchGoogleSignIn)
    }

    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        googleDriveService.handleSignInResult(account)
    }

    fun signOutGoogleDrive() {
        viewModelScope.launch { googleDriveService.signOut() }
    }

    fun setWakePolicy(policy: WakeProtectionPolicy) {
        viewModelScope.launch { preferencesManager.setWakePolicy(policy) }
    }

    fun setCooldownHours(hours: Int) {
        viewModelScope.launch { preferencesManager.setSyncCooldownHours(hours) }
    }

    fun setDailyPollEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDailyPollEnabled(enabled)
            if (enabled) syncSchedulerController.scheduleDailyOdometerPoll()
            else syncSchedulerController.cancelDailyOdometerPoll()
        }
    }

    fun connectSmartcarFromSyncRow() {
        viewModelScope.launch {
            val (id, _) = odometerSyncService.getEffectiveSmartcarCredentials()
            if (!id.isNullOrBlank() || OdometerSyncService.DEFAULT_SMARTCAR_APPLICATION_ID.isNotBlank()) {
                _events.emit(SettingsUiEvent.OpenExternalUrl(odometerSyncService.resolveSmartcarAuthUrl(id.orEmpty())))
            } else {
                openSmartcarDialog()
            }
        }
    }

    fun connectTeslaFromSyncRow() {
        viewModelScope.launch {
            val (id, _) = odometerSyncService.getEffectiveTeslaCredentials()
            if (!id.isNullOrBlank()) {
                _events.emit(SettingsUiEvent.OpenExternalUrl(odometerSyncService.buildTeslaAuthUrl(id)))
            } else {
                openTeslaDialog()
            }
        }
    }

    fun requestForceWakeSync(car: Car) {
        _dialogState.value = SettingsDialogState.ForceWakeVehicle(car)
    }

    fun confirmForceWakeSync(car: Car) {
        _dialogState.value = SettingsDialogState.None
        viewModelScope.launch {
            _events.emit(SettingsUiEvent.ShowToast("Syncing ${car.displayName} odometer..."))
            val result = odometerSyncService.syncVehicle(car, isManual = true, forceWake = true)
            _events.emit(
                SettingsUiEvent.ShowToast(
                    message = OdometerSyncService.formatSyncResultMessage(car, result),
                    longDuration = true
                )
            )
            odometerSyncService.syncWallConnectorCharging(currentCars())
        }
    }

    fun openSmartcarDialog() {
        viewModelScope.launch {
            val (id, secret) = odometerSyncService.getEffectiveSmartcarCredentials()
            val (token, _, _) = preferencesManager.getSmartcarTokens()
            _dialogState.value = SettingsDialogState.SmartcarCredentials(
                clientId = id.orEmpty(),
                clientSecret = secret.orEmpty(),
                accessToken = token.orEmpty(),
                isConnected = uiState.value.generalBackup.smartcarConnected
            )
        }
    }

    fun saveSmartcarCredentials(clientId: String, clientSecret: String, accessToken: String) {
        viewModelScope.launch {
            preferencesManager.setSmartcarCredentials(clientId, clientSecret, accessToken)
            _dialogState.value = SettingsDialogState.None
        }
    }

    fun signInSmartcarFromDialog(clientId: String, clientSecret: String, accessToken: String) {
        viewModelScope.launch {
            val tokenToSave = if (clientId.trim().startsWith("client_")) "" else accessToken.ifBlank { null }
            preferencesManager.setSmartcarCredentials(clientId, clientSecret, tokenToSave)
            val url = odometerSyncService.resolveSmartcarAuthUrl(clientId)
            _dialogState.value = SettingsDialogState.None
            _events.emit(SettingsUiEvent.OpenExternalUrl(url))
        }
    }

    fun disconnectSmartcar() {
        viewModelScope.launch {
            preferencesManager.disconnectSmartcar()
            _dialogState.value = SettingsDialogState.None
            _events.emit(SettingsUiEvent.ShowToast("Smartcar disconnected"))
        }
    }

    fun openTeslaDialog() {
        viewModelScope.launch {
            val (id, secret) = odometerSyncService.getEffectiveTeslaCredentials()
            val (token, _, _) = preferencesManager.getTeslaTokens()
            _dialogState.value = SettingsDialogState.TeslaCredentials(
                clientId = id.orEmpty(),
                clientSecret = secret.orEmpty(),
                accessToken = token.orEmpty(),
                isConnected = uiState.value.generalBackup.teslaConnected,
                includeChargingHistoryScope = OdometerSyncService.hasTeslaChargingHistoryScope(token),
                includeLocationScope = OdometerSyncService.hasTeslaLocationScope(token)
            )
        }
    }

    fun saveTeslaCredentials(clientId: String, clientSecret: String, accessToken: String) {
        viewModelScope.launch {
            preferencesManager.setTeslaCredentials(clientId, clientSecret, accessToken)
            _dialogState.value = SettingsDialogState.None
        }
    }

    fun signInTeslaFromDialog(
        clientId: String,
        clientSecret: String,
        includeLocationScope: Boolean,
        includeChargingHistoryScope: Boolean
    ) {
        viewModelScope.launch {
            preferencesManager.setTeslaCredentials(clientId, clientSecret, accessToken = null)
            val url = odometerSyncService.buildTeslaAuthUrl(
                clientId = clientId,
                includeLocationScope = includeLocationScope,
                includeChargingHistoryScope = includeChargingHistoryScope
            )
            _dialogState.value = SettingsDialogState.None
            _events.emit(SettingsUiEvent.OpenExternalUrl(url))
        }
    }

    fun disconnectTesla() {
        viewModelScope.launch {
            preferencesManager.disconnectTesla()
            _dialogState.value = SettingsDialogState.None
            _events.emit(SettingsUiEvent.ShowToast("Tesla disconnected"))
        }
    }

    fun launchTeslaOAuth(
        includeLocationScope: Boolean = uiState.value.workCharging.hasTeslaLocationScope,
        includeChargingHistoryScope: Boolean = uiState.value.workCharging.hasTeslaChargingHistoryScope
    ) {
        viewModelScope.launch {
            val (id, _) = odometerSyncService.getEffectiveTeslaCredentials()
            if (!id.isNullOrBlank()) {
                val url = odometerSyncService.buildTeslaAuthUrl(
                    clientId = id,
                    includeLocationScope = includeLocationScope,
                    includeChargingHistoryScope = includeChargingHistoryScope
                )
                _events.emit(SettingsUiEvent.OpenExternalUrl(url))
            } else {
                openTeslaDialog()
            }
        }
    }

    fun syncWallConnectors() = viewModelScope.launch { chargingActions.syncWallConnectors() }

    fun assignWallConnector(connectorType: WallConnectorType, selected: Car) =
        viewModelScope.launch { chargingActions.assignWallConnector(connectorType, selected) }

    fun openChargerSyncTimePicker() {
        val home = uiState.value.homeCharging
        _dialogState.value = SettingsDialogState.ChargerSyncTimePicker(home.chargerSyncHour, home.chargerSyncMinute)
    }

    fun setChargerSyncEnabled(enabled: Boolean) =
        viewModelScope.launch { chargingActions.setChargerSyncEnabled(enabled) }

    fun saveChargerSyncTime(hour: Int, minute: Int) =
        viewModelScope.launch { chargingActions.saveChargerSyncTime(hour, minute) }

    fun syncWorkCharging(forceWake: Boolean = false) =
        viewModelScope.launch { chargingActions.syncWorkCharging(forceWake) }

    fun setWorkChargingSyncEnabled(enabled: Boolean) =
        viewModelScope.launch { chargingActions.setWorkChargingSyncEnabled(enabled) }

    fun openWorkWindowPicker(isStart: Boolean) {
        val work = uiState.value.workCharging
        _dialogState.value = SettingsDialogState.WorkWindowTimePicker(
            isStart = isStart,
            hour = if (isStart) work.workWindowStartHour else work.workWindowEndHour,
            minute = if (isStart) work.workWindowStartMinute else work.workWindowEndMinute
        )
    }

    fun saveWorkChargingWindow(isStart: Boolean, hour: Int, minute: Int) =
        viewModelScope.launch { chargingActions.saveWorkChargingWindow(isStart, hour, minute) }

    fun toggleWorkChargingWeekdaysOnly() {
        viewModelScope.launch {
            preferencesManager.setWorkChargingWeekdaysOnly(!uiState.value.workCharging.workChargingWeekdaysOnly)
        }
    }

    fun clearWorkLocation() = viewModelScope.launch { chargingActions.clearWorkLocation() }

    fun captureWorkLocation(forceWake: Boolean = false) =
        viewModelScope.launch { chargingActions.captureWorkLocation(forceWake) }

    fun syncSuperchargers() = viewModelScope.launch { chargingActions.syncSuperchargers() }

    fun setSuperchargerSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSuperchargerSyncEnabled(enabled) }
    }

    fun syncSmartcarDc() = viewModelScope.launch { chargingActions.syncSmartcarDc() }

    fun reconnectSmartcarDc() = viewModelScope.launch { chargingActions.reconnectSmartcarDc() }

    fun setSmartcarDcSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSmartcarDcSyncEnabled(enabled) }
    }

    fun openExternalUrl(url: String) {
        _events.tryEmit(SettingsUiEvent.OpenExternalUrl(url))
    }

    private suspend fun currentCars(): List<Car> =
        uiState.value.vehicleSync.cars.ifEmpty { carDao.getAllCarsList().map(CarEntity::toModel) }
}
