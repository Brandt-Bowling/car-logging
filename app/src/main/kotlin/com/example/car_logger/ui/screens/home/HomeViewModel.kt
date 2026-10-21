package com.example.car_logger.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.ui.components.GarageNavigationTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduledServiceItem(
    val car: Car,
    val title: String,
    val dueOdometer: Int,
    val remainingMiles: Int,
    val isUrgent: Boolean // <= 500 miles
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val carDao = database.carDao()
    private val recordDao = database.maintenanceRecordDao()
    val preferencesManager = PreferencesManager(application)
    val googleDriveService = GoogleDriveService(application)

    val currentTab = MutableStateFlow(GarageNavigationTab.GARAGE)
    val isSpeedDialOpen = MutableStateFlow(false)

    val garageName: StateFlow<String> = preferencesManager.garageNameFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PreferencesManager.DEFAULT_GARAGE_NAME
    )

    val cars: StateFlow<List<Car>> = carDao.getAllCars().combine(currentTab) { entities, _ ->
        entities.map { it.toModel() }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allRecords: StateFlow<List<MaintenanceRecord>> = recordDao.getAllRecords().combine(currentTab) { entities, _ ->
        entities.map { it.toModel() }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val scheduledServices: StateFlow<List<ScheduledServiceItem>> = combine(cars, allRecords) { carList, records ->
        val items = mutableListOf<ScheduledServiceItem>()
        for (car in carList) {
            if (car.isArchived || car.odometer == null) continue
            val carRecords = records.filter { it.carId == car.id }
            val latestOdo = if (carRecords.isNotEmpty()) {
                carRecords.maxOf { it.odometer }
            } else {
                car.odometer
            }

            val interval = if (car.isEv) car.effectiveTireRotationInterval else 5000
            val nextOdo = latestOdo + interval
            val remaining = nextOdo - car.odometer

            items.add(
                ScheduledServiceItem(
                    car = car,
                    title = if (car.isEv) "Tire Rotation & Cabin Filter Inspection" else "Oil & Filter Change",
                    dueOdometer = nextOdo,
                    remainingMiles = remaining,
                    isUrgent = remaining <= 500
                )
            )
        }
        items.sortedBy { it.remainingMiles }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val hasUrgentService: StateFlow<Boolean> = scheduledServices.combine(currentTab) { services, _ ->
        services.any { it.isUrgent }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun setTab(tab: GarageNavigationTab) {
        currentTab.value = tab
        isSpeedDialOpen.value = false
    }

    fun toggleSpeedDial() {
        isSpeedDialOpen.value = !isSpeedDialOpen.value
    }

    fun closeSpeedDial() {
        isSpeedDialOpen.value = false
    }

    fun updateGarageName(newName: String) {
        viewModelScope.launch {
            preferencesManager.setGarageName(newName)
        }
    }

    fun toggleArchiveCar(carId: String) {
        viewModelScope.launch {
            carDao.toggleArchive(carId)
        }
    }

    fun deleteCar(carId: String) {
        viewModelScope.launch {
            carDao.deleteCarById(carId)
        }
    }

    fun updateCar(car: Car) {
        viewModelScope.launch {
            carDao.updateCar(CarEntity.fromModel(car))
        }
    }

    fun addCar(car: Car) {
        viewModelScope.launch {
            carDao.insertCar(CarEntity.fromModel(car))
        }
    }

    fun addMaintenanceRecord(record: MaintenanceRecord) {
        viewModelScope.launch {
            recordDao.insertRecord(MaintenanceRecordEntity.fromModel(record))
            // Auto update car odometer if new record is higher
            val car = carDao.getCarById(record.carId)
            if (car != null && (car.odometer == null || record.odometer > car.odometer)) {
                carDao.updateOdometer(record.carId, record.odometer)
            }
        }
    }
}
