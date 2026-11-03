package com.example.car_logger.ui.screens.vehicledetails

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.Tire
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.components.EnvelopedBodyCanvas
import com.example.car_logger.ui.components.VehicleFloatingToolbar
import com.example.car_logger.ui.components.VehicleSubView
import com.example.car_logger.ui.screens.dialogs.DriveFolderPickerSheet
import com.example.car_logger.ui.screens.dialogs.TireRotationIntervalDialog
import com.example.car_logger.ui.screens.dialogs.VehiclePhotoCustomizerSheet
import com.example.car_logger.ui.screens.vehicledetails.tabs.GloveboxTab
import com.example.car_logger.ui.screens.vehicledetails.tabs.VehicleOverviewTab
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailsScreen(
    viewModel: VehicleDetailsViewModel,
    googleDriveService: GoogleDriveService,
    preferencesManager: PreferencesManager,
    onNavigateBack: () -> Unit,
    onOpenAddRecordDialog: (String) -> Unit,
    onOpenRecordDialog: (MaintenanceRecord) -> Unit,
    onOpenAttachmentViewer: (MaintenanceRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val car by viewModel.car.collectAsState()
    val records by viewModel.records.collectAsState()
    val filteredRecords by viewModel.filteredRecords.collectAsState()
    val costHistory by viewModel.costHistory.collectAsState()
    val activeTire by viewModel.activeTire.collectAsState()
    val glovebox by viewModel.glovebox.collectAsState()
    val currentSubView by viewModel.currentSubView.collectAsState()
    val isSyncingDrive by viewModel.isSyncingDrive.collectAsState()
    val isSyncingOdometer by viewModel.isSyncingOdometer.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedYearFilter by viewModel.selectedYearFilter.collectAsState()

    var showFolderPickerSheet by remember { mutableStateOf(false) }
    val folderPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showPhotoCustomizerSheet by remember { mutableStateOf(false) }
    val photoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showRotationDialog by remember { mutableStateOf(false) }
    var showNewTireDialog by remember { mutableStateOf(false) }
    var showEditVehicleNameDialog by remember { mutableStateOf(false) }
    var showEditVinDialog by remember { mutableStateOf(false) }
    var showEditLicensePlateDialog by remember { mutableStateOf(false) }

    var isToolbarExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(currentSubView) {
        isToolbarExpanded = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When scrolling down, available.y is negative -> collapse toolbar into FAB
                if (available.y < -4f) {
                    isToolbarExpanded = false
                }
                // When scrolling up, available.y is positive -> expand toolbar
                else if (available.y > 4f) {
                    isToolbarExpanded = true
                }
                return Offset.Zero
            }
        }
    }

    if (car == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val carItem = car!!
    val currentOdo = carItem.odometer ?: 0
    val cooldownHours by viewModel.cooldownHoursFlow.collectAsState()
    var showWakeConfirmDialog by remember { mutableStateOf(false) }

    // Wake Vehicle Confirmation Dialog
    if (showWakeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWakeConfirmDialog = false },
            title = { Text("Wake Vehicle?") },
            text = { Text("Waking ${carItem.displayName} draws battery power. Poll odometer now?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWakeConfirmDialog = false
                        Toast.makeText(
                            context,
                            "Polling ${carItem.displayName} odometer...",
                            Toast.LENGTH_SHORT
                        ).show()
                        viewModel.syncLiveOdometer(forceWake = true) { result ->
                            Toast.makeText(
                                context,
                                OdometerSyncService.formatSyncResultMessage(carItem, result),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("Wake & Poll", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWakeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Vehicle Photo Customizer Bottom Sheet (AI & Custom Photo)
    if (showPhotoCustomizerSheet) {
        VehiclePhotoCustomizerSheet(
            car = carItem,
            sheetState = photoSheetState,
            onGenerateAiImage = { color, trim, env ->
                viewModel.generateVehicleAiImage(color, trim, env)
            },
            onSaveImageBytes = { bytes ->
                viewModel.saveCustomVehicleImage(bytes) {
                    Toast.makeText(context, "Saved photo for ${carItem.displayName}", Toast.LENGTH_SHORT).show()
                }
            },
            onPickImageUri = { uri ->
                viewModel.saveVehicleImageFromUri(uri) {
                    Toast.makeText(context, "Imported photo for ${carItem.displayName}", Toast.LENGTH_SHORT).show()
                }
            },
            onResetToStock = {
                viewModel.resetVehicleImage {
                    Toast.makeText(context, "Reset photo to stock", Toast.LENGTH_SHORT).show()
                }
            },
            onDismissRequest = { showPhotoCustomizerSheet = false }
        )
    }

    // Configure Rotation Interval Dialog
    if (showIntervalDialog) {
        TireRotationIntervalDialog(
            currentInterval = carItem.effectiveTireRotationInterval,
            onDismiss = { showIntervalDialog = false },
            onConfirm = { newInterval ->
                viewModel.updateCar(carItem.copy(tireRotationInterval = newInterval))
                showIntervalDialog = false
                Toast.makeText(context, "Updated interval to $newInterval mi", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Log Tire Rotation Dialog
    if (showRotationDialog) {
        var rotDate by remember { mutableStateOf(LocalDate.now().toString()) }
        var rotOdo by remember { mutableStateOf(currentOdo.toString()) }

        AlertDialog(
            onDismissRequest = { showRotationDialog = false },
            title = { Text("Log Tire Rotation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rotDate,
                        onValueChange = { rotDate = it },
                        label = { Text("Rotation Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rotOdo,
                        onValueChange = { rotOdo = it },
                        label = { Text("Odometer at Rotation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val odoInt = rotOdo.toIntOrNull() ?: currentOdo
                        viewModel.logTireRotation(rotDate, odoInt)
                        showRotationDialog = false
                        Toast.makeText(context, "Logged tire rotation at $odoInt mi", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save Rotation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Install New Tire Set Dialog
    if (showNewTireDialog) {
        var mfg by remember { mutableStateOf("") }
        var model by remember { mutableStateOf("") }
        var installDate by remember { mutableStateOf(LocalDate.now().toString()) }
        var installOdo by remember { mutableStateOf(currentOdo.toString()) }
        var treadLife by remember { mutableStateOf("60000") }

        AlertDialog(
            onDismissRequest = { showNewTireDialog = false },
            title = { Text("Install New Tire Set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = mfg,
                        onValueChange = { mfg = it },
                        label = { Text("Manufacturer (e.g. Michelin)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model (e.g. Defender)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = installDate,
                        onValueChange = { installDate = it },
                        label = { Text("Install Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = installOdo,
                        onValueChange = { installOdo = it },
                        label = { Text("Odometer Installed") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = treadLife,
                        onValueChange = { treadLife = it },
                        label = { Text("Estimated Tread Life (miles)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mfg.isNotBlank() && model.isNotBlank()) {
                            val newTire = Tire(
                                id = UUID.randomUUID().toString(),
                                carId = carItem.id,
                                manufacturer = mfg.trim(),
                                model = model.trim(),
                                dateInstalled = installDate.trim(),
                                odometerInstalled = installOdo.toIntOrNull() ?: currentOdo,
                                estimatedTreadLifeMiles = treadLife.toIntOrNull() ?: 60000
                            )
                            viewModel.installNewTireSet(newTire)
                            showNewTireDialog = false
                            Toast.makeText(context, "Installed ${newTire.manufacturer} ${newTire.model}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Install Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewTireDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Vehicle Name / Details Dialog
    if (showEditVehicleNameDialog) {
        var yearText by remember { mutableStateOf(carItem.year.toString()) }
        var makeText by remember { mutableStateOf(carItem.make) }
        var modelText by remember { mutableStateOf(carItem.model) }
        var trimText by remember { mutableStateOf(carItem.trim ?: "") }
        var yearError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditVehicleNameDialog = false },
            title = { Text("Edit Vehicle Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = {
                            yearText = it
                            yearError = it.toIntOrNull() == null || it.toInt() !in 1900..2100
                        },
                        label = { Text("Year") },
                        isError = yearError,
                        supportingText = if (yearError) { { Text("Enter a valid 4-digit year") } } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = makeText,
                        onValueChange = { makeText = it },
                        label = { Text("Make (e.g. Tesla)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelText,
                        onValueChange = { modelText = it },
                        label = { Text("Model (e.g. Model 3)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = trimText,
                        onValueChange = { trimText = it },
                        label = { Text("Trim (optional, e.g. Long Range)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedYear = yearText.toIntOrNull()
                        if (parsedYear != null && parsedYear in 1900..2100 && makeText.isNotBlank() && modelText.isNotBlank()) {
                            viewModel.updateCar(
                                carItem.copy(
                                    year = parsedYear,
                                    make = makeText.trim(),
                                    model = modelText.trim(),
                                    trim = trimText.trim().ifBlank { null }
                                )
                            )
                            showEditVehicleNameDialog = false
                            Toast.makeText(context, "Updated vehicle details", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditVehicleNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit VIN Dialog
    if (showEditVinDialog) {
        var vinText by remember { mutableStateOf(carItem.vin ?: "") }

        AlertDialog(
            onDismissRequest = { showEditVinDialog = false },
            title = { Text("Edit Vehicle VIN") },
            text = {
                OutlinedTextField(
                    value = vinText,
                    onValueChange = { vinText = it.uppercase() },
                    label = { Text("17-Character VIN") },
                    placeholder = { Text("Enter VIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanVin = vinText.trim().uppercase().ifBlank { null }
                        viewModel.updateCar(carItem.copy(vin = cleanVin))
                        showEditVinDialog = false
                        Toast.makeText(context, "Saved VIN", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditVinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit License Plate Dialog
    if (showEditLicensePlateDialog) {
        var plateText by remember { mutableStateOf(carItem.licensePlate ?: "") }

        AlertDialog(
            onDismissRequest = { showEditLicensePlateDialog = false },
            title = { Text("Edit License Plate") },
            text = {
                OutlinedTextField(
                    value = plateText,
                    onValueChange = { plateText = it.uppercase() },
                    label = { Text("License Plate Number") },
                    placeholder = { Text("e.g. 7XYZ89") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanPlate = plateText.trim().uppercase().ifBlank { null }
                        viewModel.updateCar(carItem.copy(licensePlate = cleanPlate))
                        showEditLicensePlateDialog = false
                        Toast.makeText(context, "Saved license plate", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLicensePlateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ExpressiveThemeTokens.shellColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEditVehicleNameDialog = true }
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
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Vehicle Name",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
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
                                if (isAsleep || inCooldown || isSmartcar) {
                                    showWakeConfirmDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Syncing ${carItem.displayName} odometer...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.syncLiveOdometer(forceWake = false) { result ->
                                        Toast.makeText(
                                            context,
                                            OdometerSyncService.formatSyncResultMessage(carItem, result),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            if (isSyncingOdometer) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = "Sync Odometer")
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.toggleArchive() }) {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // Distinct enveloped body sheet with springy subview transitions
            EnvelopedBodyCanvas {
                AnimatedContent(
                    targetState = currentSubView,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> direction * fullWidth / 4 },
                            animationSpec = ExpressiveMotionTokens.SpatialOffsetSpring
                        ) + fadeIn(
                            animationSpec = ExpressiveMotionTokens.SpatialSpring
                        )).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -direction * fullWidth / 4 },
                                animationSpec = ExpressiveMotionTokens.SpatialOffsetSpring
                            ) + fadeOut(
                                animationSpec = ExpressiveMotionTokens.SpatialSpring
                            )
                        )
                    },
                    label = "vehicle_subview_transition",
                    modifier = Modifier.fillMaxSize()
                ) { subView ->
                    when (subView) {
                        VehicleSubView.VEHICLE -> {
                            VehicleOverviewTab(
                                car = carItem,
                                records = records,
                                filteredRecords = filteredRecords,
                                costHistory = costHistory,
                                activeTire = activeTire,
                                searchQuery = searchQuery,
                                selectedYear = selectedYearFilter,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onSelectYear = { viewModel.setSelectedYearFilter(it) },
                                onOpenPhotoCustomizer = { showPhotoCustomizerSheet = true },
                                onEditVinClick = { showEditVinDialog = true },
                                onEditLicensePlateClick = { showEditLicensePlateDialog = true },
                                onLogRotationClick = { showRotationDialog = true },
                                onEditIntervalClick = { showIntervalDialog = true },
                                onInstallNewTireClick = { showNewTireDialog = true },
                                onRecordClick = onOpenRecordDialog,
                                onAttachmentClick = onOpenAttachmentViewer,
                                onDeleteRecord = { viewModel.deleteRecord(it) }
                            )
                        }
                        VehicleSubView.GLOVEBOX -> {
                            GloveboxTab(
                                car = carItem,
                                glovebox = glovebox,
                                googleDriveService = googleDriveService,
                                isSyncingDrive = isSyncingDrive,
                                onOpenFolderPicker = { showFolderPickerSheet = true },
                                onSyncDriveFolder = {
                                    viewModel.syncDriveFolder(googleDriveService, preferencesManager)
                                },
                                onUpdateOdometer = { viewModel.updateOdometer(it) },
                                onUpdateDocument = { reg, ins, man -> viewModel.updateGloveboxDocument(reg, ins, man) }
                            )
                        }
                    }
                }
            }

            if (showFolderPickerSheet) {
                DriveFolderPickerSheet(
                    sheetState = folderPickerSheetState,
                    googleDriveService = googleDriveService,
                    onFolderSelected = { folderId, folderName ->
                        coroutineScope.launch {
                            viewModel.updateDriveFolder(
                                folderId = folderId,
                                folderName = folderName,
                                googleDriveService = googleDriveService,
                                preferencesManager = preferencesManager
                            )
                            folderPickerSheetState.hide()
                            showFolderPickerSheet = false
                        }
                    },
                    onDismissRequest = {
                        coroutineScope.launch {
                            folderPickerSheetState.hide()
                            showFolderPickerSheet = false
                        }
                    }
                )
            }

            // M3 Expressive Floating Toolbar with Attached FAB
            val fabIcon = when (currentSubView) {
                VehicleSubView.VEHICLE -> Icons.Filled.Add
                VehicleSubView.GLOVEBOX -> Icons.Filled.UploadFile
            }

            VehicleFloatingToolbar(
                currentSubView = currentSubView,
                onSubViewSelected = { viewModel.setSubView(it) },
                onFabClick = {
                    when (currentSubView) {
                        VehicleSubView.VEHICLE -> onOpenAddRecordDialog(carItem.id)
                        VehicleSubView.GLOVEBOX -> {
                            viewModel.setSubView(VehicleSubView.GLOVEBOX)
                        }
                    }
                },
                fabIcon = fabIcon,
                fabContentDescription = when (currentSubView) {
                    VehicleSubView.VEHICLE -> "Add Maintenance"
                    VehicleSubView.GLOVEBOX -> "Upload Document"
                },
                expanded = isToolbarExpanded,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
