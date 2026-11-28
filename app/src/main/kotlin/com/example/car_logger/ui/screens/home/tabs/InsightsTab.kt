package com.example.car_logger.ui.screens.home.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsUiState
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.ui.screens.home.tabs.insights.ChargerUsageReviewSheet
import com.example.car_logger.ui.screens.home.tabs.insights.CompactElectricityPeriodBar
import com.example.car_logger.ui.screens.home.tabs.insights.DrivingActivityCard
import com.example.car_logger.ui.screens.home.tabs.insights.EditGasComparisonSheet
import com.example.car_logger.ui.screens.home.tabs.insights.EvElectricityCostCard
import com.example.car_logger.ui.screens.home.tabs.insights.FleetShareCard
import com.example.car_logger.ui.screens.home.tabs.insights.GasComparisonCard
import com.example.car_logger.ui.screens.home.tabs.insights.InsightsActions
import com.example.car_logger.ui.screens.home.tabs.insights.InsightsElectricityImportState
import com.example.car_logger.ui.screens.home.tabs.insights.InsightsVehicleFilterRow
import com.example.car_logger.ui.screens.home.tabs.insights.ManageElectricityDataSheet
import com.example.car_logger.ui.screens.home.tabs.insights.NoVehiclesInsightsEmptyState
import com.example.car_logger.ui.screens.home.tabs.insights.PeriodChargingLogsSheet
import com.example.car_logger.ui.screens.home.tabs.insights.UtilityBillReviewSheet
import com.example.car_logger.ui.screens.home.tabs.insights.WaitingForMileageCard

@Composable
fun InsightsTab(
    state: InsightsUiState,
    cars: List<Car>,
    actions: InsightsActions,
    modifier: Modifier = Modifier,
    importState: InsightsElectricityImportState = InsightsElectricityImportState()
) {
    if (cars.isEmpty()) {
        NoVehiclesInsightsEmptyState(modifier = modifier)
        return
    }

    var showEditSheet by remember { mutableStateOf(false) }
    var showManageElectricitySheet by remember { mutableStateOf(false) }
    var openManageSessionsDirectly by remember { mutableStateOf(false) }
    var periodLogsVenue by remember { mutableStateOf<ChargingVenue?>(null) }

    val activeCars = remember(cars) { cars.filter { !it.isArchived }.ifEmpty { cars } }
    val lastUploadBatch = remember(state.chargingSessions) {
        ElectricityImportParser.resolveLastUploadBatch(state.chargingSessions)
    }

    val billFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.onUploadUtilityBillUri(uri)
    }
    val chargerFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.onUploadChargerUsageUri(uri)
    }

    val launchBillPicker = { billFilePickerLauncher.launch(arrayOf("application/pdf", "image/*")) }
    val launchChargerPicker = {
        chargerFilePickerLauncher.launch(
            arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/pdf", "image/*")
        )
    }

    InsightsTabLazyContent(
        state = state,
        activeCars = activeCars,
        importState = importState,
        actions = actions,
        onLaunchChargerPicker = launchChargerPicker,
        onOpenEditGasSheet = { showEditSheet = true },
        onOpenManageElectricity = { showManageElectricitySheet = true },
        onOpenPeriodLogs = { periodLogsVenue = it },
        modifier = modifier
    )

    InsightsTabSheetsHost(
        state = state,
        activeCars = activeCars,
        importState = importState,
        actions = actions,
        lastUploadBatch = lastUploadBatch,
        showEditSheet = showEditSheet,
        showManageElectricitySheet = showManageElectricitySheet,
        openManageSessionsDirectly = openManageSessionsDirectly,
        periodLogsVenue = periodLogsVenue,
        onDismissEditSheet = { showEditSheet = false },
        onDismissManageSheet = {
            showManageElectricitySheet = false
            openManageSessionsDirectly = false
        },
        onOpenManageAllSessions = {
            periodLogsVenue = null
            openManageSessionsDirectly = true
            showManageElectricitySheet = true
        },
        onDismissPeriodLogs = { periodLogsVenue = null },
        onLaunchBillPicker = launchBillPicker,
        onLaunchChargerPicker = launchChargerPicker
    )
}

@Composable
private fun InsightsTabLazyContent(
    state: InsightsUiState,
    activeCars: List<Car>,
    importState: InsightsElectricityImportState,
    actions: InsightsActions,
    onLaunchChargerPicker: () -> Unit,
    onOpenEditGasSheet: () -> Unit,
    onOpenManageElectricity: () -> Unit,
    onOpenPeriodLogs: (ChargingVenue) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activeCars.size > 1) {
            item {
                InsightsVehicleFilterRow(
                    activeCars = activeCars,
                    selectedCarId = state.selectedCarId,
                    onSelectVehicle = actions.onSelectVehicle
                )
            }
        }

        if (state.hasOdometerData) {
            item { FleetShareSection(state = state, onSelectVehicle = actions.onSelectVehicle) }
            item { DrivingActivitySection(state = state, actions = actions) }
            item {
                ElectricityCostSection(
                    state = state,
                    importState = importState,
                    actions = actions,
                    onLaunchChargerPicker = onLaunchChargerPicker,
                    onOpenManageElectricity = onOpenManageElectricity,
                    onOpenPeriodLogs = onOpenPeriodLogs
                )
            }
            item { GasComparisonSection(state = state, onOpenEditSheet = onOpenEditGasSheet) }
        } else {
            if (state.electricityCost.hasAnyData) {
                item {
                    CompactElectricityPeriodBar(
                        granularity = state.granularity,
                        periodText = InsightsCalculationEngine.formatObservationWindowText(state.granularity, state.periodOffset),
                        canGoPrevious = state.canGoPrevious,
                        canGoNext = state.canGoNext,
                        onSelectGranularity = actions.onSelectGranularity,
                        onPreviousPeriod = actions.onPreviousPeriod,
                        onNextPeriod = actions.onNextPeriod
                    )
                }
            }
            item {
                ElectricityCostSection(
                    state = state,
                    importState = importState,
                    actions = actions,
                    onLaunchChargerPicker = onLaunchChargerPicker,
                    onOpenManageElectricity = onOpenManageElectricity,
                    onOpenPeriodLogs = onOpenPeriodLogs
                )
            }
            item { WaitingForMileageCard() }
        }

        item { Spacer(modifier = Modifier.height(88.dp)) }
    }
}

@Composable
private fun FleetShareSection(state: InsightsUiState, onSelectVehicle: (String?) -> Unit) {
    FleetShareCard(
        totalMiles = state.fleetTotalMiles,
        car1Id = state.car1Id,
        car1Name = state.car1Name,
        car1Miles = state.car1Miles,
        car1SharePct = state.car1SharePct,
        car1DailyRate = state.car1DailyRate,
        car2Id = state.car2Id,
        car2Name = state.car2Name,
        car2Miles = state.car2Miles,
        car2SharePct = state.car2SharePct,
        car2DailyRate = state.car2DailyRate,
        selectedCarId = state.selectedCarId,
        onSelectVehicle = onSelectVehicle
    )
}

@Composable
private fun DrivingActivitySection(state: InsightsUiState, actions: InsightsActions) {
    val activeDailyRate = when (state.selectedCarId) {
        state.car1Id -> state.car1DailyRate
        state.car2Id -> state.car2DailyRate
        else -> state.car1DailyRate + state.car2DailyRate
    }
    DrivingActivityCard(
        granularity = state.granularity,
        activityBars = state.activityBars,
        observationWindowText = state.observationWindowText,
        totalMiles = state.totalMiles,
        dailyRate = activeDailyRate,
        canGoPrevious = state.canGoPrevious,
        canGoNext = state.canGoNext,
        canReset = state.canGoNext,
        onSelectGranularity = actions.onSelectGranularity,
        onPreviousPeriod = actions.onPreviousPeriod,
        onNextPeriod = actions.onNextPeriod,
        onResetPeriod = actions.onResetPeriod,
        car1Name = state.car1Name,
        car2Name = state.car2Name,
        selectedCarId = state.selectedCarId,
        car1Id = state.car1Id,
        car2Id = state.car2Id
    )
}

@Composable
private fun ElectricityCostSection(
    state: InsightsUiState,
    importState: InsightsElectricityImportState,
    actions: InsightsActions,
    onLaunchChargerPicker: () -> Unit,
    onOpenManageElectricity: () -> Unit,
    onOpenPeriodLogs: (ChargingVenue) -> Unit
) {
    EvElectricityCostCard(
        data = state.electricityCost,
        selectedVenue = importState.selectedVenue,
        onSelectVenue = actions.onSelectVenue,
        selectedCarId = state.selectedCarId,
        car1Id = state.car1Id,
        car1Name = state.car1Name,
        car2Id = state.car2Id,
        car2Name = state.car2Name,
        isParsingFile = importState.isParsingElectricityFile,
        billCount = state.utilityBills.size,
        sessionCount = state.chargingSessions.size,
        onUploadChargerUsage = onLaunchChargerPicker,
        onOpenManualChargerSheet = actions.onOpenManualChargerUsageSheet,
        onOpenManualBillSheet = actions.onOpenManualUtilityBillSheet,
        onOpenManageSheet = onOpenManageElectricity,
        onSaveCustomRate = actions.onUpdateDefaultElectricityRate,
        onSyncWallConnectors = actions.onSyncWallConnectors,
        onSyncWorkCharging = actions.onSyncWorkCharging,
        onSyncPublicCharging = actions.onSyncPublicCharging,
        onOpenPeriodLogs = onOpenPeriodLogs
    )
}

@Composable
private fun GasComparisonSection(state: InsightsUiState, onOpenEditSheet: () -> Unit) {
    GasComparisonCard(
        gasComparison = state.gasComparison,
        gasPrice = state.gasPrice,
        baselineMpg = state.baselineMpg,
        car1Name = state.car1Name,
        car1Miles = state.car1Miles,
        car2Name = state.car2Name,
        car2Miles = state.car2Miles,
        selectedCarId = state.selectedCarId,
        car1Id = state.car1Id,
        car2Id = state.car2Id,
        onOpenEditSheet = onOpenEditSheet
    )
}

@Composable
private fun InsightsTabSheetsHost(
    state: InsightsUiState,
    activeCars: List<Car>,
    importState: InsightsElectricityImportState,
    actions: InsightsActions,
    lastUploadBatch: List<ChargingSessionEntity>,
    showEditSheet: Boolean,
    showManageElectricitySheet: Boolean,
    openManageSessionsDirectly: Boolean,
    periodLogsVenue: ChargingVenue?,
    onDismissEditSheet: () -> Unit,
    onDismissManageSheet: () -> Unit,
    onOpenManageAllSessions: () -> Unit,
    onDismissPeriodLogs: () -> Unit,
    onLaunchBillPicker: () -> Unit,
    onLaunchChargerPicker: () -> Unit
) {
    if (showEditSheet) {
        EditGasComparisonSheet(
            currentGasPrice = state.gasPrice,
            currentBaselineMpg = state.baselineMpg,
            onDismiss = onDismissEditSheet,
            onSave = { price, mpg ->
                actions.onUpdateGasPrice(price)
                actions.onUpdateBaselineMpg(mpg)
                onDismissEditSheet()
            }
        )
    }

    if (importState.showUtilityBillSheet) {
        UtilityBillReviewSheet(
            initialDraft = importState.pendingUtilityBillDraft,
            existingBills = state.utilityBills,
            onUploadDifferentFile = onLaunchBillPicker,
            onDismiss = actions.onDismissUtilityBillSheet,
            onSave = actions.onSaveUtilityBill
        )
    }

    if (importState.showChargerUsageSheet) {
        val fingerprints = remember(state.chargingSessions) {
            state.chargingSessions.map { it.fingerprint }.toSet()
        }
        ChargerUsageReviewSheet(
            initialImport = importState.pendingChargerImport,
            cars = activeCars,
            existingFingerprints = fingerprints,
            onUploadFile = onLaunchChargerPicker,
            onDismiss = actions.onDismissChargerUsageSheet,
            onSaveSessions = actions.onSaveChargingSessions,
            initialSelectedCarId = state.selectedCarId,
            initialVenue = importState.selectedVenue,
            lastUploadBatch = lastUploadBatch,
            onUndoLastUpload = actions.onUndoLastChargingUpload,
            onUndoAndRetagLastUpload = actions.onUndoAndRetagChargingUpload
        )
    }

    if (showManageElectricitySheet) {
        ManageElectricityDataSheet(
            utilityBills = state.utilityBills,
            chargingSessions = state.chargingSessions,
            cars = activeCars,
            currentDefaultRate = state.defaultElectricityRate,
            onUpdateDefaultRate = actions.onUpdateDefaultElectricityRate,
            onDeleteBill = actions.onDeleteUtilityBill,
            onDeleteSession = actions.onDeleteChargingSession,
            onUndoLastChargerUpload = actions.onUndoLastChargingUpload,
            onUndoAndRetagLastChargerUpload = {
                onDismissManageSheet()
                actions.onUndoAndRetagChargingUpload()
            },
            onOpenAddBill = {
                onDismissManageSheet()
                actions.onOpenManualUtilityBillSheet()
            },
            onOpenAddCharger = {
                onDismissManageSheet()
                actions.onOpenManualChargerUsageSheet()
            },
            onDismiss = onDismissManageSheet,
            initialShowSessionsTab = openManageSessionsDirectly,
            useCustomRate = state.useCustomElectricityRate,
            onUseSavedBillRate = actions.onUseSavedBillRate
        )
    }

    periodLogsVenue?.let { activeVenue ->
        val periodText = InsightsCalculationEngine.formatObservationWindowText(state.granularity, state.periodOffset)
        PeriodChargingLogsSheet(
            venue = activeVenue,
            data = state.electricityCost,
            granularity = state.granularity,
            periodText = periodText,
            canGoPrevious = state.canGoPrevious,
            canGoNext = state.canGoNext,
            selectedCarId = state.selectedCarId,
            cars = activeCars,
            onPreviousPeriod = actions.onPreviousPeriod,
            onNextPeriod = actions.onNextPeriod,
            onDeleteSession = actions.onDeleteChargingSession,
            onUpdateSessionCost = actions.onUpdateChargingSessionCost,
            onOpenAddCharger = {
                onDismissPeriodLogs()
                actions.onOpenManualChargerUsageSheet()
            },
            onOpenAllHistory = onOpenManageAllSessions,
            onDismiss = onDismissPeriodLogs
        )
    }
}
