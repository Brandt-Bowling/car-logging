package com.example.car_logger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.components.DriveAttachmentViewerSheet
import com.example.car_logger.ui.components.VehicleContainerTransformHost
import com.example.car_logger.ui.screens.addcar.AddCarModalSheet
import com.example.car_logger.ui.screens.addcar.AddCarViewModel
import com.example.car_logger.ui.screens.dialogs.MaintenanceRecordDialog
import com.example.car_logger.ui.screens.home.HomeScreen
import com.example.car_logger.ui.screens.home.HomeViewModel
import com.example.car_logger.ui.screens.home.tabs.VehicleCard
import com.example.car_logger.ui.screens.vehicledetails.VehicleDetailsScreen
import com.example.car_logger.ui.screens.vehicledetails.VehicleDetailsViewModel
import com.example.car_logger.ui.theme.CarLoggerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "carlogger" && uri.host == "oauth") {
                homeViewModel.handleOAuthCallback(uri)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.data?.let { uri ->
            if (uri.scheme == "carlogger" && uri.host == "oauth") {
                homeViewModel.handleOAuthCallback(uri)
            }
        }

        setContent {
            CarLoggerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    val cars by homeViewModel.cars.collectAsState()

                    LaunchedEffect(homeViewModel) {
                        homeViewModel.toastMessage.collect { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }

                    // Modal state holders
                    var showAddCarSheet by remember { mutableStateOf(false) }
                    val addCarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                    var activeRecordForDialog by remember { mutableStateOf<MaintenanceRecord?>(null) }
                    var showRecordDialog by remember { mutableStateOf(false) }
                    var targetCarIdForRecord by remember { mutableStateOf<String?>(null) }

                    var activeRecordForAttachment by remember { mutableStateOf<MaintenanceRecord?>(null) }
                    val attachmentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                    VehicleContainerTransformHost(
                        cars = cars,
                        isPredictiveBackEnabled = !showRecordDialog && activeRecordForAttachment == null && !showAddCarSheet,
                        modifier = Modifier.fillMaxSize(),
                        homeContent = { boundsRegistry, onOpenCar ->
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToCarDetails = onOpenCar,
                                onOpenAddCarSheet = { showAddCarSheet = true },
                                onOpenAddRecordDialog = { carId ->
                                    targetCarIdForRecord = carId
                                    activeRecordForDialog = null
                                    showRecordDialog = true
                                },
                                onOpenRecordDialog = { record ->
                                    targetCarIdForRecord = record.carId
                                    activeRecordForDialog = record
                                    showRecordDialog = true
                                },
                                onOpenAttachmentViewer = { record ->
                                    activeRecordForAttachment = record
                                },
                                cardBoundsRegistry = boundsRegistry
                            )
                        },
                        cardPreviewContent = { previewCar ->
                            VehicleCard(
                                car = previewCar,
                                onClick = {},
                                onToggleArchive = {},
                                onDelete = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        detailContent = { carId, initialCar, onNavigateBack ->
                            val detailsViewModel: VehicleDetailsViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return VehicleDetailsViewModel(application, carId, initialCar) as T
                                    }
                                }
                            )

                            VehicleDetailsScreen(
                                viewModel = detailsViewModel,
                                googleDriveService = homeViewModel.googleDriveService,
                                preferencesManager = homeViewModel.preferencesManager,
                                onNavigateBack = onNavigateBack,
                                onOpenAddRecordDialog = { targetId ->
                                    targetCarIdForRecord = targetId
                                    activeRecordForDialog = null
                                    showRecordDialog = true
                                },
                                onOpenRecordDialog = { record ->
                                    targetCarIdForRecord = record.carId
                                    activeRecordForDialog = record
                                    showRecordDialog = true
                                },
                                onOpenAttachmentViewer = { record ->
                                    activeRecordForAttachment = record
                                }
                            )
                        }
                    )

                    // Add Car Modal Sheet
                    if (showAddCarSheet) {
                        val addCarViewModel: AddCarViewModel = viewModel()
                        AddCarModalSheet(
                            sheetState = addCarSheetState,
                            viewModel = addCarViewModel,
                            onDismissRequest = {
                                coroutineScope.launch {
                                    addCarSheetState.hide()
                                    showAddCarSheet = false
                                }
                            },
                            onCarAdded = { newCar ->
                                homeViewModel.addCar(newCar)
                                coroutineScope.launch {
                                    addCarSheetState.hide()
                                    showAddCarSheet = false
                                }
                            }
                        )
                    }

                    // Add / Edit Maintenance Record Dialog
                    if (showRecordDialog) {
                        MaintenanceRecordDialog(
                            cars = cars,
                            initialCarId = targetCarIdForRecord,
                            record = activeRecordForDialog,
                            onDismissRequest = { showRecordDialog = false },
                            onSave = { record ->
                                homeViewModel.addMaintenanceRecord(record)
                                showRecordDialog = false
                            },
                            onDelete = { recordId ->
                                // Handled via homeViewModel or detailsViewModel
                                showRecordDialog = false
                            }
                        )
                    }

                    // Drive Attachment Viewer Sheet
                    if (activeRecordForAttachment != null) {
                        val rec = activeRecordForAttachment!!
                        DriveAttachmentViewerSheet(
                            sheetState = attachmentSheetState,
                            recordTitle = rec.title,
                            driveFileId = rec.driveFileId,
                            receiptUrl = rec.receiptUrl,
                            googleDriveService = homeViewModel.googleDriveService,
                            onDismissRequest = {
                                coroutineScope.launch {
                                    attachmentSheetState.hide()
                                    activeRecordForAttachment = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
