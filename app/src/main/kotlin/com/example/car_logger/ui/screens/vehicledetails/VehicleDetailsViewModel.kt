package com.example.car_logger.ui.screens.vehicledetails

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.GloveboxEntity
import com.example.car_logger.data.local.entity.TireEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Glovebox
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.Tire
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.components.VehicleSubView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class VehicleDetailsViewModel(
    application: Application,
    val carId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val carDao = database.carDao()
    private val recordDao = database.maintenanceRecordDao()
    private val tireDao = database.tireDao()
    private val gloveboxDao = database.gloveboxDao()
    private val preferencesManager = PreferencesManager(application)
    val odometerSyncService = OdometerSyncService(preferencesManager, carDao)
    val isSyncingOdometer: StateFlow<Boolean> = odometerSyncService.isSyncing
    val cooldownHoursFlow: StateFlow<Int> = preferencesManager.syncCooldownHoursFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PreferencesManager.DEFAULT_COOLDOWN_HOURS
    )

    val currentSubView = MutableStateFlow(VehicleSubView.MAINTENANCE)

    val car: StateFlow<Car?> = carDao.observeCarById(carId).map { it?.toModel() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val records: StateFlow<List<MaintenanceRecord>> = recordDao.getRecordsForCar(carId).map { list ->
        list.map { it.toModel() }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeTire: StateFlow<Tire?> = tireDao.getActiveTireForCar(carId).map { it?.toModel() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val glovebox: StateFlow<Glovebox?> = gloveboxDao.getGloveboxForCar(carId).map { it?.toModel() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    fun setSubView(subView: VehicleSubView) {
        currentSubView.value = subView
    }

    fun toggleArchive() {
        viewModelScope.launch {
            carDao.toggleArchive(carId)
        }
    }

    fun updateCar(updatedCar: Car) {
        viewModelScope.launch {
            carDao.updateCar(CarEntity.fromModel(updatedCar))
        }
    }

    fun updateOdometer(newOdometer: Int) {
        viewModelScope.launch {
            carDao.updateOdometer(carId, newOdometer)
        }
    }

    fun syncLiveOdometer(forceWake: Boolean = false) {
        val currentCar = car.value ?: return
        viewModelScope.launch {
            odometerSyncService.syncVehicle(currentCar, isManual = true, forceWake = forceWake)
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            recordDao.deleteRecordById(recordId)
        }
    }

    fun logTireRotation(date: String, odometer: Int) {
        viewModelScope.launch {
            val current = activeTire.value
            if (current != null) {
                val updated = current.copy(
                    lastRotationDate = date,
                    lastRotationOdometer = odometer
                )
                tireDao.updateTire(TireEntity.fromModel(updated))
            } else {
                val newTire = Tire(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    manufacturer = "OEM / All Season",
                    model = "Standard Tires",
                    dateInstalled = date,
                    odometerInstalled = car.value?.odometer ?: 0,
                    estimatedTreadLifeMiles = 50000,
                    lastRotationDate = date,
                    lastRotationOdometer = odometer
                )
                tireDao.insertTire(TireEntity.fromModel(newTire))
            }
        }
    }

    fun installNewTireSet(tire: Tire) {
        viewModelScope.launch {
            tireDao.insertTire(TireEntity.fromModel(tire))
        }
    }

    fun updateGloveboxDocument(registrationUrl: String? = null, insuranceUrl: String? = null, manualUrl: String? = null) {
        viewModelScope.launch {
            val existing = glovebox.value
            val updated = Glovebox(
                carId = carId,
                registrationUrl = registrationUrl ?: existing?.registrationUrl,
                insuranceUrl = insuranceUrl ?: existing?.insuranceUrl,
                manualUrl = manualUrl ?: existing?.manualUrl
            )
            gloveboxDao.upsertGlovebox(GloveboxEntity.fromModel(updated))
        }
    }
}
