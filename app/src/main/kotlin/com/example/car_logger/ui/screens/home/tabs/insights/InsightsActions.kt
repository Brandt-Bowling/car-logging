package com.example.car_logger.ui.screens.home.tabs.insights

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedUtilityBillDraft

@Immutable
data class InsightsElectricityImportState(
    val selectedVenue: ChargingVenue = ChargingVenue.HOME,
    val isParsingElectricityFile: Boolean = false,
    val pendingUtilityBillDraft: ParsedUtilityBillDraft? = null,
    val showUtilityBillSheet: Boolean = false,
    val pendingChargerImport: ParsedChargerImportResult? = null,
    val showChargerUsageSheet: Boolean = false
)

@Immutable
data class InsightsActions(
    val onSelectVehicle: (String?) -> Unit = {},
    val onSelectGranularity: (InsightsTimeGranularity) -> Unit = {},
    val onPreviousPeriod: () -> Unit = {},
    val onNextPeriod: () -> Unit = {},
    val onResetPeriod: () -> Unit = {},
    val onUpdateGasPrice: (Float) -> Unit = {},
    val onUpdateBaselineMpg: (Float) -> Unit = {},
    val onSelectVenue: (ChargingVenue) -> Unit = {},
    val onUploadUtilityBillUri: (Uri) -> Unit = {},
    val onUploadChargerUsageUri: (Uri) -> Unit = {},
    val onOpenManualUtilityBillSheet: () -> Unit = {},
    val onDismissUtilityBillSheet: () -> Unit = {},
    val onSaveUtilityBill: (String, Long, Long, Double, Double, Double, Double?, String?) -> Unit = { _, _, _, _, _, _, _, _ -> },
    val onOpenManualChargerUsageSheet: () -> Unit = {},
    val onDismissChargerUsageSheet: () -> Unit = {},
    val onSaveChargingSessions: (List<ChargingSessionEntity>, Int) -> Unit = { _, _ -> },
    val onUndoLastChargingUpload: () -> Unit = {},
    val onUndoAndRetagChargingUpload: () -> Unit = {},
    val onDeleteUtilityBill: (String) -> Unit = {},
    val onDeleteChargingSession: (String) -> Unit = {},
    val onUpdateChargingSessionCost: (String, Double) -> Unit = { _, _ -> },
    val onUpdateDefaultElectricityRate: (Float) -> Unit = {},
    val onUseSavedBillRate: () -> Unit = {},
    val onSyncWallConnectors: () -> Unit = {},
    val onSyncWorkCharging: () -> Unit = {},
    val onSyncPublicCharging: () -> Unit = {}
)
