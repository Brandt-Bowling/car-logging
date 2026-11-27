package com.example.car_logger.ui.screens.home.tabs.settings

import androidx.compose.runtime.Immutable
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.WallConnectorType

@Immutable
data class GeneralBackupSettingsState(
    val garageName: String = PreferencesManager.DEFAULT_GARAGE_NAME,
    val isDriveConnected: Boolean = false,
    val driveEmail: String = "",
    val geminiKey: String = "",
    val autoDevKey: String = "",
    val smartcarConnected: Boolean = false,
    val teslaConnected: Boolean = false
)

@Immutable
data class VehicleSyncSettingsState(
    val cars: List<Car> = emptyList(),
    val wakePolicy: WakeProtectionPolicy = WakeProtectionPolicy.AWAKE_ONLY,
    val cooldownHours: Int = PreferencesManager.DEFAULT_COOLDOWN_HOURS,
    val smartcarConnected: Boolean = false,
    val teslaConnected: Boolean = false,
    val dailyPollEnabled: Boolean = true,
    val lastDailyPollTime: Long = 0L,
    val lastDailyPollStatus: String? = null
)

@Immutable
data class HomeChargingSettingsState(
    val cars: List<Car> = emptyList(),
    val teslaConnected: Boolean = false,
    val wallConnectorGen3CarId: String? = null,
    val wallConnectorUniversalCarId: String? = null,
    val chargerSyncEnabled: Boolean = true,
    val chargerSyncHour: Int = PreferencesManager.DEFAULT_CHARGER_SYNC_HOUR,
    val chargerSyncMinute: Int = PreferencesManager.DEFAULT_CHARGER_SYNC_MINUTE,
    val lastChargerSyncTime: Long = 0L,
    val lastChargerSyncStatus: String? = null
)

@Immutable
data class WorkChargingSettingsState(
    val cars: List<Car> = emptyList(),
    val teslaConnected: Boolean = false,
    val hasTeslaLocationScope: Boolean = false,
    val hasTeslaChargingHistoryScope: Boolean = false,
    val workChargingSyncEnabled: Boolean = true,
    val workWindowStartHour: Int = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_START_HOUR,
    val workWindowStartMinute: Int = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_START_MINUTE,
    val workWindowEndHour: Int = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_END_HOUR,
    val workWindowEndMinute: Int = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_END_MINUTE,
    val workChargingWeekdaysOnly: Boolean = true,
    val lastWorkChargingSyncTime: Long = 0L,
    val lastWorkChargingSyncStatus: String? = null,
    val workLocationLat: Double? = null,
    val workLocationLng: Double? = null,
    val workLocationRadiusMeters: Int = PreferencesManager.DEFAULT_WORK_LOCATION_RADIUS_METERS
)

@Immutable
data class PublicChargingSettingsState(
    val cars: List<Car> = emptyList(),
    val teslaConnected: Boolean = false,
    val smartcarConnected: Boolean = false,
    val hasTeslaLocationScope: Boolean = false,
    val hasTeslaChargingHistoryScope: Boolean = false,
    val superchargerSyncEnabled: Boolean = true,
    val lastSuperchargerSyncTime: Long = 0L,
    val lastSuperchargerSyncStatus: String? = null,
    val smartcarDcSyncEnabled: Boolean = true,
    val smartcarDcCapability: String = PreferencesManager.SMARTCAR_CAPABILITY_UNKNOWN,
    val lastSmartcarDcSyncTime: Long = 0L,
    val lastSmartcarDcSyncStatus: String? = null
)

@Immutable
data class SettingsUiState(
    val generalBackup: GeneralBackupSettingsState = GeneralBackupSettingsState(),
    val vehicleSync: VehicleSyncSettingsState = VehicleSyncSettingsState(),
    val homeCharging: HomeChargingSettingsState = HomeChargingSettingsState(),
    val workCharging: WorkChargingSettingsState = WorkChargingSettingsState(),
    val publicCharging: PublicChargingSettingsState = PublicChargingSettingsState(),
    val dialog: SettingsDialogState = SettingsDialogState.None
)

sealed interface SettingsDialogState {
    data object None : SettingsDialogState
    data class GarageName(val currentName: String) : SettingsDialogState
    data class GeminiKey(val currentKey: String) : SettingsDialogState
    data class AutoDevKey(val currentKey: String) : SettingsDialogState
    data class SmartcarCredentials(
        val clientId: String,
        val clientSecret: String,
        val accessToken: String,
        val isConnected: Boolean
    ) : SettingsDialogState
    data class TeslaCredentials(
        val clientId: String,
        val clientSecret: String,
        val accessToken: String,
        val isConnected: Boolean,
        val includeChargingHistoryScope: Boolean,
        val includeLocationScope: Boolean
    ) : SettingsDialogState
    data class ForceWakeVehicle(val car: Car) : SettingsDialogState
    data object WorkLocationMissingScope : SettingsDialogState
    data object WorkLocationWakeConfirm : SettingsDialogState
    data object WorkChargeWakeConfirm : SettingsDialogState
    data class ChargerSyncTimePicker(val hour: Int, val minute: Int) : SettingsDialogState
    data class WorkWindowTimePicker(val isStart: Boolean, val hour: Int, val minute: Int) : SettingsDialogState
}

sealed interface SettingsUiEvent {
    data class ShowToast(val message: String, val longDuration: Boolean = false) : SettingsUiEvent
    data class OpenExternalUrl(val url: String) : SettingsUiEvent
    data object LaunchGoogleSignIn : SettingsUiEvent
}

@Immutable
internal data class SettingsListCallbacks(
    val onEditGarageName: () -> Unit,
    val onSignInDrive: () -> Unit,
    val onDisconnectDrive: () -> Unit,
    val onSelectWakePolicy: (WakeProtectionPolicy) -> Unit,
    val onSelectCooldownHours: (Int) -> Unit,
    val onToggleDailyPoll: (Boolean) -> Unit,
    val onConnectSmartcar: () -> Unit,
    val onConnectTesla: () -> Unit,
    val onRequestForceWakeSync: (Car) -> Unit,
    val onSyncWallConnectors: () -> Unit,
    val onReconnectTesla: () -> Unit,
    val onAssignWallConnector: (WallConnectorType, Car) -> Unit,
    val onEditChargerSyncTime: () -> Unit,
    val onToggleChargerSync: (Boolean) -> Unit,
    val onSyncWorkCharging: () -> Unit,
    val onToggleWorkChargingSync: (Boolean) -> Unit,
    val onEditWorkWindowStart: () -> Unit,
    val onEditWorkWindowEnd: () -> Unit,
    val onToggleWeekdaysOnly: () -> Unit,
    val onClearWorkLocation: () -> Unit,
    val onCaptureWorkLocation: () -> Unit,
    val onSyncSuperchargers: () -> Unit,
    val onEnableSuperchargerScope: () -> Unit,
    val onToggleSuperchargerSync: (Boolean) -> Unit,
    val onSyncSmartcarDc: () -> Unit,
    val onReconnectSmartcarDc: () -> Unit,
    val onToggleSmartcarDcSync: (Boolean) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onOpenGeminiDialog: () -> Unit,
    val onOpenAutoDevDialog: () -> Unit,
    val onOpenSmartcarDialog: () -> Unit,
    val onOpenTeslaDialog: () -> Unit
)

internal fun SettingsViewModel.buildListCallbacks(
    hasPublicLocationScope: Boolean
): SettingsListCallbacks = SettingsListCallbacks(
    onEditGarageName = ::openGarageNameDialog,
    onSignInDrive = ::requestGoogleSignIn,
    onDisconnectDrive = ::signOutGoogleDrive,
    onSelectWakePolicy = ::setWakePolicy,
    onSelectCooldownHours = ::setCooldownHours,
    onToggleDailyPoll = ::setDailyPollEnabled,
    onConnectSmartcar = ::connectSmartcarFromSyncRow,
    onConnectTesla = ::connectTeslaFromSyncRow,
    onRequestForceWakeSync = ::requestForceWakeSync,
    onSyncWallConnectors = { syncWallConnectors() },
    onReconnectTesla = { launchTeslaOAuth() },
    onAssignWallConnector = { type, car -> assignWallConnector(type, car) },
    onEditChargerSyncTime = ::openChargerSyncTimePicker,
    onToggleChargerSync = { setChargerSyncEnabled(it) },
    onSyncWorkCharging = { syncWorkCharging(forceWake = false) },
    onToggleWorkChargingSync = { setWorkChargingSyncEnabled(it) },
    onEditWorkWindowStart = { openWorkWindowPicker(isStart = true) },
    onEditWorkWindowEnd = { openWorkWindowPicker(isStart = false) },
    onToggleWeekdaysOnly = { toggleWorkChargingWeekdaysOnly() },
    onClearWorkLocation = { clearWorkLocation() },
    onCaptureWorkLocation = { captureWorkLocation(forceWake = false) },
    onSyncSuperchargers = { syncSuperchargers() },
    onEnableSuperchargerScope = {
        launchTeslaOAuth(
            includeLocationScope = hasPublicLocationScope,
            includeChargingHistoryScope = true
        )
    },
    onToggleSuperchargerSync = { setSuperchargerSyncEnabled(it) },
    onSyncSmartcarDc = { syncSmartcarDc() },
    onReconnectSmartcarDc = { reconnectSmartcarDc() },
    onToggleSmartcarDcSync = { setSmartcarDcSyncEnabled(it) },
    onOpenUrl = ::openExternalUrl,
    onOpenGeminiDialog = ::openGeminiKeyDialog,
    onOpenAutoDevDialog = ::openAutoDevKeyDialog,
    onOpenSmartcarDialog = ::openSmartcarDialog,
    onOpenTeslaDialog = ::openTeslaDialog
)

internal fun SettingsViewModel.buildDialogCallbacks(
    hasWorkChargingHistoryScope: Boolean
): SettingsDialogCallbacks = SettingsDialogCallbacks(
    onDismiss = ::dismissDialog,
    onSaveGarageName = ::saveGarageName,
    onSaveGeminiKey = ::saveGeminiKey,
    onSaveAutoDevKey = ::saveAutoDevKey,
    onSaveSmartcarCredentials = ::saveSmartcarCredentials,
    onSignInSmartcar = ::signInSmartcarFromDialog,
    onDisconnectSmartcar = ::disconnectSmartcar,
    onSaveTeslaCredentials = ::saveTeslaCredentials,
    onSignInTesla = ::signInTeslaFromDialog,
    onDisconnectTesla = ::disconnectTesla,
    onConfirmForceWakeVehicle = ::confirmForceWakeSync,
    onReconnectWithLocationScope = {
        dismissDialog()
        launchTeslaOAuth(
            includeLocationScope = true,
            includeChargingHistoryScope = hasWorkChargingHistoryScope
        )
    },
    onConfirmWakeWorkLocation = { captureWorkLocation(forceWake = true) },
    onConfirmWakeWorkCharge = { syncWorkCharging(forceWake = true) },
    onSaveChargerSyncTime = { h, m -> saveChargerSyncTime(h, m) },
    onSaveWorkWindowTime = { isStart, h, m -> saveWorkChargingWindow(isStart, h, m) }
)
