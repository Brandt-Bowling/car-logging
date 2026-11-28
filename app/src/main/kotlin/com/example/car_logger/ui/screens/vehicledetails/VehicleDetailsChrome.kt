package com.example.car_logger.ui.screens.vehicledetails

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Glovebox
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.Tire
import com.example.car_logger.services.DriveFileItem
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.components.EnvelopedBodyCanvas
import com.example.car_logger.ui.components.VehicleFloatingToolbar
import com.example.car_logger.ui.components.VehicleSubView
import com.example.car_logger.ui.screens.dialogs.DriveFolderPickerSheet
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleCostHistory
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleInsightsBentoState
import com.example.car_logger.ui.screens.vehicledetails.tabs.GloveboxTab
import com.example.car_logger.ui.screens.vehicledetails.tabs.VehicleOverviewTab
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleDetailsTopBar(
    carItem: Car,
    isSyncingOdometer: Boolean,
    cooldownHours: Int,
    onNavigateBack: () -> Unit,
    onEditNameClick: () -> Unit,
    onWakeConfirmRequired: () -> Unit,
    onDirectSyncClick: () -> Unit,
    onToggleArchive: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditNameClick)
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = "${carItem.year} ${carItem.make} ${carItem.model}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Filled.Edit, contentDescription = "Edit vehicle name", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (OdometerSyncService.resolveProviderForCar(carItem) != null) {
                IconButton(
                    enabled = !isSyncingOdometer,
                    onClick = {
                        val inCooldown = OdometerSyncService.isCooldownActive(carItem.lastOdometerSyncTime, cooldownHours)
                        val isAsleep = carItem.telemetryStatus == "Asleep"
                        val isSmartcar = OdometerSyncService.resolveProviderForCar(carItem) == OdometerSyncService.PROVIDER_SMARTCAR
                        if (isAsleep || inCooldown || isSmartcar) onWakeConfirmRequired() else onDirectSyncClick()
                    }
                ) {
                    if (isSyncingOdometer) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync odometer")
                    }
                }
            }
            IconButton(onClick = onToggleArchive) {
                Icon(
                    imageVector = if (carItem.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    contentDescription = if (carItem.isArchived) "Unarchive" else "Archive"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ExpressiveThemeTokens.shellColor,
            scrolledContainerColor = ExpressiveThemeTokens.shellColor
        )
    )
}

@Immutable
internal data class VehicleDetailsBodyState(
    val carItem: Car,
    val currentSubView: VehicleSubView,
    val records: List<MaintenanceRecord>,
    val filteredRecords: List<MaintenanceRecord>,
    val costHistory: VehicleCostHistory,
    val bentoState: VehicleInsightsBentoState,
    val bentoGranularity: InsightsTimeGranularity,
    val activeTire: Tire?,
    val glovebox: Glovebox?,
    val searchQuery: String,
    val selectedYearFilter: Int?,
    val isDriveSignedIn: Boolean,
    val isSyncingDrive: Boolean
)

@Immutable
internal data class VehicleDetailsBodyCallbacks(
    val onSelectBentoGranularity: (InsightsTimeGranularity) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSelectYear: (Int?) -> Unit,
    val onOpenPhotoCustomizer: () -> Unit,
    val onEditVinClick: () -> Unit,
    val onEditLicensePlateClick: () -> Unit,
    val onLogRotationClick: () -> Unit,
    val onEditIntervalClick: () -> Unit,
    val onInstallNewTireClick: () -> Unit,
    val onRecordClick: (MaintenanceRecord) -> Unit,
    val onAttachmentClick: (MaintenanceRecord) -> Unit,
    val onDeleteRecord: (String) -> Unit,
    val onOpenFolderPicker: () -> Unit,
    val onSyncDriveFolder: () -> Unit,
    val onUpdateOdometer: (Int) -> Unit,
    val onUpdateDocument: (String?, String?, String?) -> Unit
)

@Composable
internal fun VehicleDetailsBodyContent(
    state: VehicleDetailsBodyState,
    callbacks: VehicleDetailsBodyCallbacks
) {
    EnvelopedBodyCanvas {
        AnimatedContent(
            targetState = state.currentSubView,
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
            label = "vehicle_subview_transition",
            modifier = Modifier.fillMaxSize()
        ) { subView ->
            when (subView) {
                VehicleSubView.VEHICLE -> VehicleOverviewTab(
                    car = state.carItem,
                    records = state.records,
                    filteredRecords = state.filteredRecords,
                    costHistory = state.costHistory,
                    bentoState = state.bentoState,
                    bentoGranularity = state.bentoGranularity,
                    onSelectBentoGranularity = callbacks.onSelectBentoGranularity,
                    activeTire = state.activeTire,
                    searchQuery = state.searchQuery,
                    selectedYear = state.selectedYearFilter,
                    onSearchQueryChange = callbacks.onSearchQueryChange,
                    onSelectYear = callbacks.onSelectYear,
                    onOpenPhotoCustomizer = callbacks.onOpenPhotoCustomizer,
                    onEditVinClick = callbacks.onEditVinClick,
                    onEditLicensePlateClick = callbacks.onEditLicensePlateClick,
                    onLogRotationClick = callbacks.onLogRotationClick,
                    onEditIntervalClick = callbacks.onEditIntervalClick,
                    onInstallNewTireClick = callbacks.onInstallNewTireClick,
                    onRecordClick = callbacks.onRecordClick,
                    onAttachmentClick = callbacks.onAttachmentClick,
                    onDeleteRecord = callbacks.onDeleteRecord
                )
                VehicleSubView.GLOVEBOX -> GloveboxTab(
                    car = state.carItem,
                    glovebox = state.glovebox,
                    isDriveSignedIn = state.isDriveSignedIn,
                    isSyncingDrive = state.isSyncingDrive,
                    onOpenFolderPicker = callbacks.onOpenFolderPicker,
                    onSyncDriveFolder = callbacks.onSyncDriveFolder,
                    onUpdateOdometer = callbacks.onUpdateOdometer,
                    onUpdateDocument = callbacks.onUpdateDocument
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleDetailsScaffold(
    bodyState: VehicleDetailsBodyState,
    bodyCallbacks: VehicleDetailsBodyCallbacks,
    isSyncingOdometer: Boolean,
    cooldownHours: Int,
    folderPickerOpen: Boolean,
    folderPickerSheetState: SheetState,
    isToolbarExpanded: Boolean,
    onToolbarExpandedChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onEditNameClick: () -> Unit,
    onWakeConfirmRequired: () -> Unit,
    onDirectSyncClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onListFolders: suspend (parentId: String?, searchName: String?) -> List<DriveFileItem>,
    onSelectDriveFolder: (folderId: String, folderName: String) -> Unit,
    onDismissFolderPicker: () -> Unit,
    onSubViewSelected: (VehicleSubView) -> Unit,
    onOpenAddRecordDialog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -4f) onToolbarExpandedChange(false)
                else if (available.y > 4f) onToolbarExpandedChange(true)
                return Offset.Zero
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ExpressiveThemeTokens.shellColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            VehicleDetailsTopBar(
                carItem = bodyState.carItem,
                isSyncingOdometer = isSyncingOdometer,
                cooldownHours = cooldownHours,
                onNavigateBack = onNavigateBack,
                onEditNameClick = onEditNameClick,
                onWakeConfirmRequired = onWakeConfirmRequired,
                onDirectSyncClick = onDirectSyncClick,
                onToggleArchive = onToggleArchive
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().nestedScroll(nestedScrollConnection)) {
            VehicleDetailsBodyContent(state = bodyState, callbacks = bodyCallbacks)
            if (folderPickerOpen) {
                DriveFolderPickerSheet(
                    sheetState = folderPickerSheetState,
                    onListFolders = onListFolders,
                    onFolderSelected = onSelectDriveFolder,
                    onDismissRequest = onDismissFolderPicker
                )
            }
            VehicleFloatingToolbar(
                currentSubView = bodyState.currentSubView,
                onSubViewSelected = onSubViewSelected,
                onFabClick = {
                    if (bodyState.currentSubView == VehicleSubView.VEHICLE) onOpenAddRecordDialog(bodyState.carItem.id)
                    else onSubViewSelected(VehicleSubView.GLOVEBOX)
                },
                fabIcon = if (bodyState.currentSubView == VehicleSubView.VEHICLE) Icons.Filled.Add else Icons.Filled.UploadFile,
                fabContentDescription = if (bodyState.currentSubView == VehicleSubView.VEHICLE) "Add record" else "Upload document",
                expanded = isToolbarExpanded,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
