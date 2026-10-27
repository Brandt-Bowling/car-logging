package com.example.car_logger.ui.screens.vehicledetails

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.components.EnvelopedBodyCanvas
import com.example.car_logger.ui.components.VehicleFloatingToolbar
import com.example.car_logger.ui.components.VehicleSubView
import com.example.car_logger.ui.screens.dialogs.DriveFolderPickerSheet
import com.example.car_logger.ui.screens.vehicledetails.tabs.GloveboxTab
import com.example.car_logger.ui.screens.vehicledetails.tabs.MaintenanceTab
import com.example.car_logger.ui.screens.vehicledetails.tabs.TiresTab
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import kotlinx.coroutines.launch

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
    val activeTire by viewModel.activeTire.collectAsState()
    val glovebox by viewModel.glovebox.collectAsState()
    val currentSubView by viewModel.currentSubView.collectAsState()
    val isSyncingDrive by viewModel.isSyncingDrive.collectAsState()
    val isSyncingOdometer by viewModel.isSyncingOdometer.collectAsState()

    var showFolderPickerSheet by remember { mutableStateOf(false) }
    val folderPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
    val cooldownHours by viewModel.cooldownHoursFlow.collectAsState()
    var showWakeConfirmDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ExpressiveThemeTokens.shellColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${carItem.year} ${carItem.make} ${carItem.model}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                        VehicleSubView.MAINTENANCE -> {
                            MaintenanceTab(
                                car = carItem,
                                records = records,
                                onRecordClick = onOpenRecordDialog,
                                onAttachmentClick = onOpenAttachmentViewer,
                                onDeleteRecord = { viewModel.deleteRecord(it) },
                                onUpdateCar = { viewModel.updateCar(it) }
                            )
                        }
                        VehicleSubView.TIRES -> {
                            TiresTab(
                                car = carItem,
                                activeTire = activeTire,
                                onLogRotation = { date, odo -> viewModel.logTireRotation(date, odo) },
                                onInstallNewTire = { viewModel.installNewTireSet(it) },
                                onUpdateCar = { viewModel.updateCar(it) }
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

            // M3 Expressive Floating Toolbar with Attached FAB (No bottom nav bar!)
            val fabIcon = when (currentSubView) {
                VehicleSubView.MAINTENANCE -> Icons.Filled.Add
                VehicleSubView.TIRES -> Icons.Filled.TireRepair
                VehicleSubView.GLOVEBOX -> Icons.Filled.UploadFile
            }

            VehicleFloatingToolbar(
                currentSubView = currentSubView,
                onSubViewSelected = { viewModel.setSubView(it) },
                onFabClick = {
                    when (currentSubView) {
                        VehicleSubView.MAINTENANCE -> onOpenAddRecordDialog(carItem.id)
                        VehicleSubView.TIRES -> onOpenAddRecordDialog(carItem.id) // Or new tire action
                        VehicleSubView.GLOVEBOX -> {
                            // Subview glovebox actions handled in tab or trigger doc picker
                            viewModel.setSubView(VehicleSubView.GLOVEBOX)
                        }
                    }
                },
                fabIcon = fabIcon,
                fabContentDescription = when (currentSubView) {
                    VehicleSubView.MAINTENANCE -> "Add Maintenance"
                    VehicleSubView.TIRES -> "Tire Action"
                    VehicleSubView.GLOVEBOX -> "Upload Document"
                },
                expanded = isToolbarExpanded,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
