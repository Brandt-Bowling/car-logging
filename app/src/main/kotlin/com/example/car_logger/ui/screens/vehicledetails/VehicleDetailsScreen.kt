package com.example.car_logger.ui.screens.vehicledetails

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.services.OdometerSyncService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailsScreen(
    viewModel: VehicleDetailsViewModel,
    onNavigateBack: () -> Unit,
    onOpenAddRecordDialog: (String) -> Unit,
    onOpenRecordDialog: (MaintenanceRecord) -> Unit,
    onOpenAttachmentViewer: (MaintenanceRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val car by viewModel.car.collectAsStateWithLifecycle()
    val carItem = car ?: run {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentSubView by viewModel.currentSubView.collectAsStateWithLifecycle()
    val isSyncingOdometer by viewModel.isSyncingOdometer.collectAsStateWithLifecycle()
    val cooldownHours by viewModel.cooldownHoursFlow.collectAsStateWithLifecycle()
    var dialogState by remember { mutableStateOf(VehicleDialogFlags()) }
    val folderPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val photoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isToolbarExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(currentSubView) { isToolbarExpanded = true }

    val runOdometerSync: (Boolean) -> Unit = { forceWake ->
        Toast.makeText(context, "Syncing ${carItem.displayName}...", Toast.LENGTH_SHORT).show()
        viewModel.syncLiveOdometer(forceWake = forceWake) {
            Toast.makeText(context, OdometerSyncService.formatSyncResultMessage(carItem, it), Toast.LENGTH_LONG).show()
        }
    }
    val bodyState = VehicleDetailsBodyState(
        carItem = carItem,
        currentSubView = currentSubView,
        records = viewModel.records.collectAsStateWithLifecycle().value,
        filteredRecords = viewModel.filteredRecords.collectAsStateWithLifecycle().value,
        costHistory = viewModel.costHistory.collectAsStateWithLifecycle().value,
        bentoState = viewModel.vehicleBentoState.collectAsStateWithLifecycle().value,
        bentoGranularity = viewModel.bentoGranularity.collectAsStateWithLifecycle().value,
        activeTire = viewModel.activeTire.collectAsStateWithLifecycle().value,
        glovebox = viewModel.glovebox.collectAsStateWithLifecycle().value,
        searchQuery = viewModel.searchQuery.collectAsStateWithLifecycle().value,
        selectedYearFilter = viewModel.selectedYearFilter.collectAsStateWithLifecycle().value,
        isDriveSignedIn = viewModel.isDriveSignedIn.collectAsStateWithLifecycle().value,
        isSyncingDrive = viewModel.isSyncingDrive.collectAsStateWithLifecycle().value
    )
    val bodyCallbacks = VehicleDetailsBodyCallbacks(
        onSelectBentoGranularity = viewModel::setBentoGranularity,
        onSearchQueryChange = viewModel::setSearchQuery,
        onSelectYear = viewModel::setSelectedYearFilter,
        onOpenPhotoCustomizer = { dialogState = dialogState.copy(photoCustomizer = true) },
        onEditVinClick = { dialogState = dialogState.copy(editVin = true) },
        onEditLicensePlateClick = { dialogState = dialogState.copy(editLicensePlate = true) },
        onLogRotationClick = { dialogState = dialogState.copy(rotation = true) },
        onEditIntervalClick = { dialogState = dialogState.copy(interval = true) },
        onInstallNewTireClick = { dialogState = dialogState.copy(newTire = true) },
        onRecordClick = onOpenRecordDialog,
        onAttachmentClick = onOpenAttachmentViewer,
        onDeleteRecord = viewModel::deleteRecord,
        onOpenFolderPicker = { dialogState = dialogState.copy(folderPicker = true) },
        onSyncDriveFolder = { viewModel.syncDriveFolder() },
        onUpdateOdometer = viewModel::updateOdometer,
        onUpdateDocument = { r, i, m -> viewModel.updateGloveboxDocument(r, i, m) }
    )

    VehicleDetailsDialogsHost(
        carItem = carItem,
        dialogState = dialogState,
        photoSheetState = photoSheetState,
        onUpdateDialogState = { dialogState = it },
        onConfirmWakeSync = { runOdometerSync(true) },
        onGenerateAiImage = { c, t, e -> viewModel.generateVehicleAiImage(c, t, e) },
        onSaveCustomImage = { viewModel.saveCustomVehicleImage(it) { Toast.makeText(context, "Saved photo for ${carItem.displayName}", Toast.LENGTH_SHORT).show() } },
        onPickImageUri = { viewModel.saveVehicleImageFromUri(it) { Toast.makeText(context, "Updated photo for ${carItem.displayName}", Toast.LENGTH_SHORT).show() } },
        onResetToStock = { viewModel.resetVehicleImage { Toast.makeText(context, "Reset to default photo", Toast.LENGTH_SHORT).show() } },
        onUpdateCar = viewModel::updateCar,
        onLogRotation = viewModel::logTireRotation,
        onInstallNewTire = viewModel::installNewTireSet
    )
    VehicleDetailsScaffold(
        bodyState = bodyState,
        bodyCallbacks = bodyCallbacks,
        isSyncingOdometer = isSyncingOdometer,
        cooldownHours = cooldownHours,
        folderPickerOpen = dialogState.folderPicker,
        folderPickerSheetState = folderPickerSheetState,
        isToolbarExpanded = isToolbarExpanded,
        onToolbarExpandedChange = { isToolbarExpanded = it },
        onNavigateBack = onNavigateBack,
        onEditNameClick = { dialogState = dialogState.copy(editVehicleName = true) },
        onWakeConfirmRequired = { dialogState = dialogState.copy(wakeConfirm = true) },
        onDirectSyncClick = { runOdometerSync(false) },
        onToggleArchive = viewModel::toggleArchive,
        onListFolders = viewModel::listDriveFolders,
        onSelectDriveFolder = { folderId, folderName ->
            coroutineScope.launch {
                viewModel.updateDriveFolder(folderId, folderName)
                folderPickerSheetState.hide()
                dialogState = dialogState.copy(folderPicker = false)
            }
        },
        onDismissFolderPicker = {
            coroutineScope.launch { folderPickerSheetState.hide(); dialogState = dialogState.copy(folderPicker = false) }
        },
        onSubViewSelected = viewModel::setSubView,
        onOpenAddRecordDialog = onOpenAddRecordDialog,
        modifier = modifier
    )
}
