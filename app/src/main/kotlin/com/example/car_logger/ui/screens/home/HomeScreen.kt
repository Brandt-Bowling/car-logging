package com.example.car_logger.ui.screens.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.car_logger.data.insights.InsightsUiState
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.components.EnvelopedBodyCanvas
import com.example.car_logger.ui.components.GarageNavigationBar
import com.example.car_logger.ui.components.GarageNavigationTab
import com.example.car_logger.ui.components.SpeedDialFab
import com.example.car_logger.ui.components.VehicleCardBoundsRegistry
import com.example.car_logger.ui.screens.home.tabs.ActivityTab
import com.example.car_logger.ui.screens.home.tabs.GarageTab
import com.example.car_logger.ui.screens.home.tabs.InsightsTab
import com.example.car_logger.ui.screens.home.tabs.insights.InsightsActions
import com.example.car_logger.ui.screens.home.tabs.insights.InsightsElectricityImportState
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import kotlinx.coroutines.CancellationException

@Immutable
private data class HomeGarageActions(
    val onToggleArchive: (String) -> Unit,
    val onDeleteCar: (String) -> Unit,
    val onUpdateCar: (Car) -> Unit,
    val onGenerateAiImage: suspend (Car, String, String?, String?) -> Result<ByteArray>,
    val onSaveCustomImage: (Car, ByteArray) -> Unit,
    val onSaveImageFromUri: (Car, android.net.Uri) -> Unit,
    val onResetVehicleImage: (Car) -> Unit
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCarDetails: (Car) -> Unit,
    onOpenAddCarSheet: () -> Unit,
    onOpenAddRecordDialog: (String?) -> Unit,
    onOpenRecordDialog: (MaintenanceRecord) -> Unit,
    onOpenAttachmentViewer: (MaintenanceRecord) -> Unit,
    settingsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    cardBoundsRegistry: VehicleCardBoundsRegistry? = null
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isSpeedDialOpen by viewModel.isSpeedDialOpen.collectAsStateWithLifecycle()
    val garageName by viewModel.garageName.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val hasUrgentService by viewModel.hasUrgentService.collectAsStateWithLifecycle()
    val insightsUiState by viewModel.insightsUiState.collectAsStateWithLifecycle()
    val insightsImportState = InsightsElectricityImportState(
        selectedVenue = viewModel.insightsChargingVenue.collectAsStateWithLifecycle().value,
        isParsingElectricityFile = viewModel.isParsingElectricityFile.collectAsStateWithLifecycle().value,
        pendingUtilityBillDraft = viewModel.pendingUtilityBillDraft.collectAsStateWithLifecycle().value,
        showUtilityBillSheet = viewModel.showUtilityBillSheet.collectAsStateWithLifecycle().value,
        pendingChargerImport = viewModel.pendingChargerImport.collectAsStateWithLifecycle().value,
        showChargerUsageSheet = viewModel.showChargerUsageSheet.collectAsStateWithLifecycle().value
    )
    val insightsActions = remember(viewModel) { buildInsightsActions(viewModel) }
    val garageActions = remember(viewModel) { buildGarageActions(viewModel) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showArchivedOnly by remember { mutableStateOf(false) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    if (isSpeedDialOpen) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collect { predictiveBackProgress = it.progress }
                viewModel.closeSpeedDial()
            } catch (_: CancellationException) {
                predictiveBackProgress = 0f
            } finally {
                predictiveBackProgress = 0f
            }
        }
    }

    if (showRenameDialog) {
        RenameGarageDialog(
            initialName = garageName,
            onSave = { viewModel.updateGarageName(it); showRenameDialog = false },
            onDismiss = { showRenameDialog = false }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ExpressiveThemeTokens.shellColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                HomeTopBar(
                    garageName = garageName,
                    currentTab = currentTab,
                    cars = cars,
                    showArchivedOnly = showArchivedOnly,
                    onRenameClick = { showRenameDialog = true },
                    onToggleShowArchived = { showArchivedOnly = !showArchivedOnly }
                )
            },
            bottomBar = {
                GarageNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = viewModel::setTab,
                    hasUrgentService = hasUrgentService
                )
            },
            floatingActionButton = {
                if (currentTab == GarageNavigationTab.GARAGE || currentTab == GarageNavigationTab.ACTIVITY) {
                    SpeedDialFab(
                        isOpen = isSpeedDialOpen,
                        onToggle = viewModel::toggleSpeedDial,
                        onAddCar = { viewModel.closeSpeedDial(); onOpenAddCarSheet() },
                        onAddRecord = {
                            viewModel.closeSpeedDial()
                            onOpenAddRecordDialog(cars.firstOrNull { !it.isArchived }?.id ?: cars.firstOrNull()?.id)
                        },
                        predictiveBackProgress = predictiveBackProgress
                    )
                }
            }
        ) { innerPadding ->
            EnvelopedBodyCanvas(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                HomeTabContent(
                    currentTab = currentTab,
                    cars = cars,
                    allRecords = allRecords,
                    showArchivedOnly = showArchivedOnly,
                    insightsUiState = insightsUiState,
                    insightsImportState = insightsImportState,
                    insightsActions = insightsActions,
                    garageActions = garageActions,
                    cardBoundsRegistry = cardBoundsRegistry,
                    onNavigateToCarDetails = onNavigateToCarDetails,
                    onOpenAddCarSheet = onOpenAddCarSheet,
                    onOpenRecordDialog = onOpenRecordDialog,
                    onOpenAttachmentViewer = onOpenAttachmentViewer,
                    settingsContent = settingsContent
                )
            }
        }

        SpeedDialScrim(
            visible = isSpeedDialOpen,
            predictiveBackProgress = predictiveBackProgress,
            onDismiss = viewModel::closeSpeedDial
        )
    }
}

@Composable
private fun RenameGarageDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempName by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename garage") },
        text = {
            OutlinedTextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = { Text("Garage name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(tempName) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    garageName: String,
    currentTab: GarageNavigationTab,
    cars: List<Car>,
    showArchivedOnly: Boolean,
    onRenameClick: () -> Unit,
    onToggleShowArchived: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onRenameClick)
            ) {
                Text(text = garageName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        actions = {
            if (currentTab == GarageNavigationTab.GARAGE && (cars.any { it.isArchived } || showArchivedOnly)) {
                IconButton(onClick = onToggleShowArchived) {
                    Icon(
                        imageVector = if (showArchivedOnly) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = if (showArchivedOnly) "Show all vehicles" else "Show archived only",
                        tint = if (showArchivedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ExpressiveThemeTokens.shellColor,
            scrolledContainerColor = ExpressiveThemeTokens.shellColor
        )
    )
}

@Composable
private fun HomeTabContent(
    currentTab: GarageNavigationTab,
    cars: List<Car>,
    allRecords: List<MaintenanceRecord>,
    showArchivedOnly: Boolean,
    insightsUiState: InsightsUiState,
    insightsImportState: InsightsElectricityImportState,
    insightsActions: InsightsActions,
    garageActions: HomeGarageActions,
    cardBoundsRegistry: VehicleCardBoundsRegistry?,
    onNavigateToCarDetails: (Car) -> Unit,
    onOpenAddCarSheet: () -> Unit,
    onOpenRecordDialog: (MaintenanceRecord) -> Unit,
    onOpenAttachmentViewer: (MaintenanceRecord) -> Unit,
    settingsContent: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> direction * fullWidth / 4 },
                animationSpec = ExpressiveMotionTokens.SpatialOffsetSpring
            ) + fadeIn(animationSpec = ExpressiveMotionTokens.SpatialSpring)).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -direction * fullWidth / 4 },
                    animationSpec = ExpressiveMotionTokens.SpatialOffsetSpring
                ) + fadeOut(animationSpec = ExpressiveMotionTokens.SpatialSpring)
            )
        },
        label = "home_tab_transition",
        modifier = Modifier.fillMaxSize()
    ) { tab ->
        when (tab) {
            GarageNavigationTab.GARAGE -> GarageTab(
                cars = if (showArchivedOnly) cars.filter { it.isArchived } else cars,
                onCarClick = onNavigateToCarDetails,
                onToggleArchive = garageActions.onToggleArchive,
                onDeleteCar = garageActions.onDeleteCar,
                onAddCarClick = onOpenAddCarSheet,
                onUpdateCar = garageActions.onUpdateCar,
                onGenerateAiImage = garageActions.onGenerateAiImage,
                onSaveCustomImage = garageActions.onSaveCustomImage,
                onSaveImageFromUri = garageActions.onSaveImageFromUri,
                onResetVehicleImage = garageActions.onResetVehicleImage,
                cardBoundsRegistry = cardBoundsRegistry
            )
            GarageNavigationTab.ACTIVITY -> ActivityTab(
                records = allRecords,
                cars = cars,
                onRecordClick = onOpenRecordDialog,
                onAttachmentClick = onOpenAttachmentViewer
            )
            GarageNavigationTab.INSIGHTS -> InsightsTab(
                state = insightsUiState,
                cars = cars,
                actions = insightsActions,
                importState = insightsImportState
            )
            GarageNavigationTab.SETTINGS -> settingsContent()
        }
    }
}

@Composable
private fun SpeedDialScrim(
    visible: Boolean,
    predictiveBackProgress: Float,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = ExpressiveMotionTokens.SnappySpring),
        exit = fadeOut(animationSpec = ExpressiveMotionTokens.SnappySpring)
    ) {
        val scrimAlpha = (0.42f * (1f - predictiveBackProgress)).coerceIn(0f, 0.42f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }
}

private fun buildGarageActions(viewModel: HomeViewModel) = HomeGarageActions(
    onToggleArchive = viewModel::toggleArchiveCar,
    onDeleteCar = viewModel::deleteCar,
    onUpdateCar = viewModel::updateCar,
    onGenerateAiImage = { car, color, trim, env ->
        viewModel.generateVehicleAiImage(
            year = car.year,
            make = car.make,
            model = car.model,
            color = color,
            trim = trim,
            environment = env
        )
    },
    onSaveCustomImage = { car, bytes -> viewModel.saveCustomVehicleImage(car, bytes) },
    onSaveImageFromUri = { car, uri -> viewModel.saveVehicleImageFromUri(car, uri) },
    onResetVehicleImage = { car -> viewModel.resetVehicleImage(car) }
)

private fun buildInsightsActions(viewModel: HomeViewModel) = InsightsActions(
    onSelectVehicle = viewModel::setInsightsVehicleFilter,
    onSelectGranularity = viewModel::setInsightsGranularity,
    onPreviousPeriod = viewModel::navigateInsightsPreviousPeriod,
    onNextPeriod = viewModel::navigateInsightsNextPeriod,
    onResetPeriod = viewModel::resetInsightsPeriod,
    onUpdateGasPrice = viewModel::updateComparisonGasPrice,
    onUpdateBaselineMpg = viewModel::updateComparisonBaselineMpg,
    onSelectVenue = viewModel::setInsightsChargingVenue,
    onUploadUtilityBillUri = viewModel::parseUtilityBillUri,
    onUploadChargerUsageUri = viewModel::parseChargerUsageUri,
    onOpenManualUtilityBillSheet = viewModel::openManualUtilityBillSheet,
    onDismissUtilityBillSheet = viewModel::dismissUtilityBillSheet,
    onSaveUtilityBill = { provider, startMs, endMs, kwh, amount, fixed, override, replaceId ->
        viewModel.saveUtilityBill(
            providerName = provider,
            startDateMs = startMs,
            endDateMs = endMs,
            totalKwh = kwh,
            totalAmount = amount,
            fixedCharges = fixed,
            overrideRate = override,
            replaceExistingId = replaceId
        )
    },
    onOpenManualChargerUsageSheet = viewModel::openManualChargerUsageSheet,
    onDismissChargerUsageSheet = viewModel::dismissChargerUsageSheet,
    onSaveChargingSessions = viewModel::saveChargingSessions,
    onUndoLastChargingUpload = { viewModel.undoLastChargingUpload(reopenForRetag = false) },
    onUndoAndRetagChargingUpload = { viewModel.undoLastChargingUpload(reopenForRetag = true) },
    onDeleteUtilityBill = viewModel::deleteUtilityBill,
    onDeleteChargingSession = viewModel::deleteChargingSession,
    onUpdateChargingSessionCost = viewModel::updateChargingSessionDirectCost,
    onUpdateDefaultElectricityRate = viewModel::updateDefaultElectricityRate,
    onUseSavedBillRate = viewModel::useSavedBillRate,
    onSyncWallConnectors = viewModel::syncWallConnectorCharging,
    onSyncWorkCharging = viewModel::syncWorkCharging,
    onSyncPublicCharging = viewModel::syncPublicCharging
)
