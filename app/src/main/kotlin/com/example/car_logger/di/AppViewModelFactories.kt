package com.example.car_logger.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.car_logger.data.model.Car
import com.example.car_logger.ui.screens.addcar.AddCarViewModel
import com.example.car_logger.ui.screens.home.HomeViewModel
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsViewModel
import com.example.car_logger.ui.screens.vehicledetails.VehicleDetailsViewModel

object AppViewModelFactories {

    fun home(container: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    return HomeViewModel(
                        carDao = container.carDao,
                        recordDao = container.maintenanceRecordDao,
                        tireDao = container.tireDao,
                        odometerLogDao = container.odometerLogDao,
                        utilityBillDao = container.utilityBillDao,
                        chargingSessionDao = container.chargingSessionDao,
                        preferencesManager = container.preferencesManager,
                        googleDriveService = container.googleDriveService,
                        odometerSyncService = container.odometerSyncService,
                        vehicleImageRepository = container.vehicleImageRepository,
                        geminiAiService = container.geminiAiService,
                        uriContentReader = container.uriContentReader
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    fun vehicleDetails(
        container: AppContainer,
        carId: String,
        initialCar: Car? = null
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(VehicleDetailsViewModel::class.java)) {
                    return VehicleDetailsViewModel(
                        carId = carId,
                        initialCar = initialCar,
                        carDao = container.carDao,
                        recordDao = container.maintenanceRecordDao,
                        tireDao = container.tireDao,
                        gloveboxDao = container.gloveboxDao,
                        odometerLogDao = container.odometerLogDao,
                        utilityBillDao = container.utilityBillDao,
                        chargingSessionDao = container.chargingSessionDao,
                        preferencesManager = container.preferencesManager,
                        odometerSyncService = container.odometerSyncService,
                        vehicleImageRepository = container.vehicleImageRepository,
                        geminiAiService = container.geminiAiService
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    fun addCar(container: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(AddCarViewModel::class.java)) {
                    return AddCarViewModel(
                        autoDevService = container.autoDevService
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    fun settings(container: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    return SettingsViewModel(
                        carDao = container.carDao,
                        preferencesManager = container.preferencesManager,
                        googleDriveService = container.googleDriveService,
                        odometerSyncService = container.odometerSyncService,
                        syncSchedulerController = container.syncSchedulerController
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
}
