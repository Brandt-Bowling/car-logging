package com.example.car_logger.ui.screens.home.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.InsightsUiState
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedUtilityBillDraft
import com.example.car_logger.ui.screens.home.tabs.insights.ChargerUsageReviewSheet
import com.example.car_logger.ui.screens.home.tabs.insights.DrivingActivityCard
import com.example.car_logger.ui.screens.home.tabs.insights.EditGasComparisonSheet
import com.example.car_logger.ui.screens.home.tabs.insights.EvElectricityCostCard
import com.example.car_logger.ui.screens.home.tabs.insights.FleetShareCard
import com.example.car_logger.ui.screens.home.tabs.insights.GasComparisonCard
import com.example.car_logger.ui.screens.home.tabs.insights.ManageElectricityDataSheet
import com.example.car_logger.ui.screens.home.tabs.insights.UtilityBillReviewSheet
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsTab(
    state: InsightsUiState,
    cars: List<Car>,
    onSelectVehicle: (String?) -> Unit,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    onPreviousPeriod: () -> Unit = {},
    onNextPeriod: () -> Unit = {},
    onResetPeriod: () -> Unit = {},
    onUpdateGasPrice: (Float) -> Unit,
    onUpdateBaselineMpg: (Float) -> Unit,
    selectedVenue: ChargingVenue = ChargingVenue.HOME,
    onSelectVenue: (ChargingVenue) -> Unit = {},
    isParsingElectricityFile: Boolean = false,
    pendingUtilityBillDraft: ParsedUtilityBillDraft? = null,
    showUtilityBillSheet: Boolean = false,
    pendingChargerImport: ParsedChargerImportResult? = null,
    showChargerUsageSheet: Boolean = false,
    onUploadUtilityBillUri: (Uri) -> Unit = {},
    onUploadChargerUsageUri: (Uri) -> Unit = {},
    onOpenManualUtilityBillSheet: () -> Unit = {},
    onDismissUtilityBillSheet: () -> Unit = {},
    onSaveUtilityBill: (String, Long, Long, Double, Double, Double, Double?, String?) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onOpenManualChargerUsageSheet: () -> Unit = {},
    onDismissChargerUsageSheet: () -> Unit = {},
    onSaveChargingSessions: (List<ChargingSessionEntity>, Int) -> Unit = { _, _ -> },
    onUndoLastChargingUpload: () -> Unit = {},
    onUndoAndRetagChargingUpload: () -> Unit = {},
    onDeleteUtilityBill: (String) -> Unit = {},
    onDeleteChargingSession: (String) -> Unit = {},
    onUpdateDefaultElectricityRate: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEditSheet by remember { mutableStateOf(false) }
    var showManageElectricitySheet by remember { mutableStateOf(false) }
    val activeCars = cars.filter { !it.isArchived }.ifEmpty { cars }
    val lastUploadBatch = remember(state.chargingSessions) {
        ElectricityImportParser.resolveLastUploadBatch(state.chargingSessions)
    }

    val billFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onUploadUtilityBillUri(uri)
    }

    val chargerFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onUploadChargerUsageUri(uri)
    }

    val launchBillPicker = {
        billFilePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
    }
    val launchChargerPicker = {
        chargerFilePickerLauncher.launch(
            arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "text/plain",
                "application/pdf",
                "image/*"
            )
        )
    }

    if (cars.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "No vehicles in garage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add a vehicle to your garage to track driving analytics and insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vehicle Selection Controls (M3 Expressive Connected Button Group)
        if (activeCars.size > 1) {
            item {
                val vehicleOptions = listOf("Both" to null) + activeCars.map {
                    (it.model.ifBlank { it.displayName }) to it.id
                }

                if (vehicleOptions.size <= 3) {
                    VehicleFilterConnectedButtonGroup(
                        options = vehicleOptions,
                        selectedCarId = state.selectedCarId,
                        onSelectVehicle = onSelectVehicle
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vehicleOptions.forEach { (label, carId) ->
                            val selected = state.selectedCarId == carId
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectVehicle(carId) },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.expressiveBounceClick()
                            )
                        }
                    }
                }
            }
        }

        if (state.hasOdometerData) {
            // Card 1: Fleet Share & Overall Miles
            item {
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

            // Card 2: Driving Activity Bar Chart (Google Health Steps view layout)
            item {
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
                    onSelectGranularity = onSelectGranularity,
                    onPreviousPeriod = onPreviousPeriod,
                    onNextPeriod = onNextPeriod,
                    onResetPeriod = onResetPeriod,
                    car1Name = state.car1Name,
                    car2Name = state.car2Name,
                    selectedCarId = state.selectedCarId,
                    car1Id = state.car1Id,
                    car2Id = state.car2Id
                )
            }

            // Card 3: EV Charging Costs Card
            item {
                EvElectricityCostCard(
                    data = state.electricityCost,
                    selectedVenue = selectedVenue,
                    onSelectVenue = onSelectVenue,
                    selectedCarId = state.selectedCarId,
                    car1Id = state.car1Id,
                    car1Name = state.car1Name,
                    car2Id = state.car2Id,
                    car2Name = state.car2Name,
                    isParsingFile = isParsingElectricityFile,
                    billCount = state.utilityBills.size,
                    sessionCount = state.chargingSessions.size,
                    onUploadUtilityBill = launchBillPicker,
                    onUploadChargerUsage = launchChargerPicker,
                    onOpenManualChargerSheet = onOpenManualChargerUsageSheet,
                    onOpenManualBillSheet = onOpenManualUtilityBillSheet,
                    onOpenManageSheet = { showManageElectricitySheet = true },
                    lastUploadCount = lastUploadBatch.size,
                    onUndoLastChargerUpload = onUndoLastChargingUpload,
                    onUndoAndRetagLastChargerUpload = onUndoAndRetagChargingUpload
                )
            }

            // Card 4: Gas Comparison Card (includes Saved vs. Gas when EV charging data exists)
            item {
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
                    onOpenEditSheet = { showEditSheet = true }
                )
            }
        } else {
            // Allow switching Week/Month/Year when electricity data exists even before telemetry syncs
            if (state.electricityCost.hasAnyData) {
                item {
                    CompactElectricityPeriodBar(
                        granularity = state.granularity,
                        periodText = InsightsCalculationEngine.formatObservationWindowText(
                            state.granularity,
                            state.periodOffset
                        ),
                        canGoPrevious = state.canGoPrevious,
                        canGoNext = state.canGoNext,
                        onSelectGranularity = onSelectGranularity,
                        onPreviousPeriod = onPreviousPeriod,
                        onNextPeriod = onNextPeriod
                    )
                }
            }

            // Always show EV Charging Costs card even before 2+ odometer telemetry syncs exist
            item {
                EvElectricityCostCard(
                    data = state.electricityCost,
                    selectedVenue = selectedVenue,
                    onSelectVenue = onSelectVenue,
                    selectedCarId = state.selectedCarId,
                    car1Id = state.car1Id,
                    car1Name = state.car1Name,
                    car2Id = state.car2Id,
                    car2Name = state.car2Name,
                    isParsingFile = isParsingElectricityFile,
                    billCount = state.utilityBills.size,
                    sessionCount = state.chargingSessions.size,
                    onUploadUtilityBill = launchBillPicker,
                    onUploadChargerUsage = launchChargerPicker,
                    onOpenManualChargerSheet = onOpenManualChargerUsageSheet,
                    onOpenManualBillSheet = onOpenManualUtilityBillSheet,
                    onOpenManageSheet = { showManageElectricitySheet = true },
                    lastUploadCount = lastUploadBatch.size,
                    onUndoLastChargerUpload = onUndoLastChargingUpload,
                    onUndoAndRetagLastChargerUpload = onUndoAndRetagChargingUpload
                )
            }

            // Empty / Initial telemetry state card
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Text(
                            text = "More Odometer Readings Needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Driving insights and gas comparison charts are calculated using verified odometer telemetry over time. As your vehicle syncs, weekly, monthly, and YTD charts will populate automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Bottom spacer for comfortable navigation clearance
        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    if (showEditSheet) {
        EditGasComparisonSheet(
            currentGasPrice = state.gasPrice,
            currentBaselineMpg = state.baselineMpg,
            onDismiss = { showEditSheet = false },
            onSave = { price, mpg ->
                onUpdateGasPrice(price)
                onUpdateBaselineMpg(mpg)
                showEditSheet = false
            }
        )
    }

    if (showUtilityBillSheet) {
        UtilityBillReviewSheet(
            initialDraft = pendingUtilityBillDraft,
            existingBills = state.utilityBills,
            onUploadDifferentFile = launchBillPicker,
            onDismiss = onDismissUtilityBillSheet,
            onSave = onSaveUtilityBill
        )
    }

    if (showChargerUsageSheet) {
        val fingerprints = remember(state.chargingSessions) {
            state.chargingSessions.map { it.fingerprint }.toSet()
        }
        ChargerUsageReviewSheet(
            initialImport = pendingChargerImport,
            cars = activeCars,
            existingFingerprints = fingerprints,
            onUploadFile = launchChargerPicker,
            onDismiss = onDismissChargerUsageSheet,
            onSaveSessions = onSaveChargingSessions,
            initialSelectedCarId = state.selectedCarId,
            lastUploadBatch = lastUploadBatch,
            onUndoLastUpload = onUndoLastChargingUpload,
            onUndoAndRetagLastUpload = onUndoAndRetagChargingUpload
        )
    }

    if (showManageElectricitySheet) {
        ManageElectricityDataSheet(
            utilityBills = state.utilityBills,
            chargingSessions = state.chargingSessions,
            cars = activeCars,
            currentDefaultRate = state.defaultElectricityRate,
            onUpdateDefaultRate = onUpdateDefaultElectricityRate,
            onDeleteBill = onDeleteUtilityBill,
            onDeleteSession = onDeleteChargingSession,
            onUndoLastChargerUpload = onUndoLastChargingUpload,
            onUndoAndRetagLastChargerUpload = {
                showManageElectricitySheet = false
                onUndoAndRetagChargingUpload()
            },
            onOpenAddBill = {
                showManageElectricitySheet = false
                onOpenManualUtilityBillSheet()
            },
            onOpenAddCharger = {
                showManageElectricitySheet = false
                onOpenManualChargerUsageSheet()
            },
            onDismiss = { showManageElectricitySheet = false }
        )
    }
}

@Composable
private fun VehicleFilterConnectedButtonGroup(
    options: List<Pair<String, String?>>,
    selectedCarId: String?,
    onSelectVehicle: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, (label, carId) ->
            val selected = selectedCarId == carId
            val startTarget = if (index == 0 || selected) 24.dp else 6.dp
            val endTarget = if (index == options.lastIndex || selected) 24.dp else 6.dp

            val startRadius by animateDpAsState(
                targetValue = startTarget,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "veh_start_$index"
            )
            val endRadius by animateDpAsState(
                targetValue = endTarget,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "veh_end_$index"
            )
            val bg by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "veh_bg_$index"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "veh_fg_$index"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectVehicle(carId) },
                shape = RoundedCornerShape(
                    topStart = startRadius,
                    bottomStart = startRadius,
                    topEnd = endRadius,
                    bottomEnd = endRadius
                ),
                color = bg,
                contentColor = fg
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactElectricityPeriodBar(
    granularity: InsightsTimeGranularity,
    periodText: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InsightsTimeGranularity.entries.forEachIndexed { index, gran ->
                    val selected = gran == granularity
                    val startR by animateDpAsState(
                        if (index == 0 || selected) 20.dp else 6.dp,
                        spring(dampingRatio = 0.8f, stiffness = 380f),
                        label = "c_s_$index"
                    )
                    val endR by animateDpAsState(
                        if (index == InsightsTimeGranularity.entries.lastIndex || selected) 20.dp else 6.dp,
                        spring(dampingRatio = 0.8f, stiffness = 380f),
                        label = "c_e_$index"
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .expressiveBounceClick { onSelectGranularity(gran) },
                        shape = RoundedCornerShape(
                            topStart = startR,
                            bottomStart = startR,
                            topEnd = endR,
                            bottomEnd = endR
                        ),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Text(
                            text = gran.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousPeriod,
                    enabled = canGoPrevious
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous period"
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = periodText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                IconButton(
                    onClick = onNextPeriod,
                    enabled = canGoNext
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next period"
                    )
                }
            }
        }
    }
}

