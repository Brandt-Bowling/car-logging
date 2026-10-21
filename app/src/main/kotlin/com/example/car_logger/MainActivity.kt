package com.example.car_logger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.components.DriveAttachmentViewerSheet
import com.example.car_logger.ui.screens.addcar.AddCarModalSheet
import com.example.car_logger.ui.screens.addcar.AddCarViewModel
import com.example.car_logger.ui.screens.dialogs.DriveFolderPickerSheet
import com.example.car_logger.ui.screens.dialogs.MaintenanceRecordDialog
import com.example.car_logger.ui.screens.home.HomeScreen
import com.example.car_logger.ui.screens.home.HomeViewModel
import com.example.car_logger.ui.screens.vehicledetails.VehicleDetailsScreen
import com.example.car_logger.ui.screens.vehicledetails.VehicleDetailsViewModel
import com.example.car_logger.ui.theme.CarLoggerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CarLoggerTheme {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()

                // Modal state holders
                var showAddCarSheet by remember { mutableStateOf(false) }
                val addCarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                var activeRecordForDialog by remember { mutableStateOf<MaintenanceRecord?>(null) }
                var showRecordDialog by remember { mutableStateOf(false) }
                var targetCarIdForRecord by remember { mutableStateOf<String?>(null) }

                var activeRecordForAttachment by remember { mutableStateOf<MaintenanceRecord?>(null) }
                val attachmentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                var showFolderPickerSheet by remember { mutableStateOf(false) }
                val folderPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToCarDetails = { carId ->
                                navController.navigate("vehicle_details/$carId")
                            },
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
                            onOpenFolderPicker = { showFolderPickerSheet = true }
                        )
                    }

                    composable(
                        route = "vehicle_details/{carId}",
                        arguments = listOf(navArgument("carId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val carId = backStackEntry.arguments?.getString("carId") ?: ""
                        val detailsViewModel: VehicleDetailsViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    @Suppress("UNCHECKED_CAST")
                                    return VehicleDetailsViewModel(application, carId) as T
                                }
                            }
                        )

                        VehicleDetailsScreen(
                            viewModel = detailsViewModel,
                            onNavigateBack = { navController.popBackStack() },
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
                }

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
                    val cars = homeViewModel.cars.value
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

                // Drive Folder Picker Sheet
                if (showFolderPickerSheet) {
                    DriveFolderPickerSheet(
                        sheetState = folderPickerSheetState,
                        googleDriveService = homeViewModel.googleDriveService,
                        onFolderSelected = { folderId, folderName ->
                            coroutineScope.launch {
                                homeViewModel.preferencesManager.setSyncFolder(folderId, folderName)
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
            }
        }
    }
}
