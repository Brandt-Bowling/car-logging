package com.example.car_logger.ui.screens.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.InsightsUiState
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.vehicle.VehicleImageRepository
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.components.GarageNavigationTab
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val odometerLogDao = database.odometerLogDao()
    val preferencesManager = PreferencesManager(application)
    val googleDriveService = GoogleDriveService(application)
    val odometerSyncService = OdometerSyncService(preferencesManager, carDao, odometerLogDao)
    val vehicleImageRepository = VehicleImageRepository(application, carDao)
    val geminiAiService = GeminiAiService(preferencesManager)
    private var initialTelemetryChecked = false

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    val currentTab = MutableStateFlow(GarageNavigationTab.GARAGE)
    val isSpeedDialOpen = MutableStateFlow(false)

    val garageName: StateFlow<String> = preferencesManager.garageNameFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PreferencesManager.DEFAULT_GARAGE_NAME
    )

    val cars: StateFlow<List<Car>> = carDao.getAllCars().combine(currentTab) { entities, _ ->
        val models = entities.map { it.toModel() }
        if (!initialTelemetryChecked && models.isNotEmpty()) {
            initialTelemetryChecked = true
            viewModelScope.launch {
                odometerSyncService.refreshSmartcarConnectionStatus()
                odometerSyncService.syncAllEligibleVehicles(models)
            }
        }
        models
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

    val insightsVehicleFilter = MutableStateFlow<String?>(null)
    val insightsGranularity = MutableStateFlow(InsightsTimeGranularity.WEEKLY)

    val insightsUiState: StateFlow<InsightsUiState> = combine(
        cars,
        odometerLogDao.getAllLogs(),
        combine(insightsGranularity, insightsVehicleFilter, ::Pair),
        combine(preferencesManager.gasPriceFlow, preferencesManager.baselineMpgFlow, ::Pair)
    ) { carList, logs, (gran, selectedCar), (gasPrice, mpg) ->
        InsightsCalculationEngine.computeUiState(
            cars = carList,
            allLogs = logs,
            granularity = gran,
            selectedCarId = selectedCar,
            gasPrice = gasPrice,
            baselineMpg = mpg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        InsightsUiState()
    )

    fun setInsightsVehicleFilter(carId: String?) {
        insightsVehicleFilter.value = carId
    }

    fun setInsightsGranularity(granularity: InsightsTimeGranularity) {
        insightsGranularity.value = granularity
    }

    fun updateComparisonGasPrice(price: Float) {
        viewModelScope.launch {
            preferencesManager.setGasPrice(price)
        }
    }

    fun updateComparisonBaselineMpg(mpg: Float) {
        viewModelScope.launch {
            preferencesManager.setBaselineMpg(mpg)
        }
    }

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

    fun syncCarOdometer(car: Car, forceWake: Boolean = false) {
        viewModelScope.launch {
            val result = odometerSyncService.syncVehicle(car = car, isManual = true, forceWake = forceWake)
            _toastMessage.tryEmit(OdometerSyncService.formatSyncResultMessage(car, result))
        }
    }

    fun handleOAuthCallback(uri: Uri) {
        viewModelScope.launch {
            val isSmartcar = uri.path.orEmpty().contains("smartcar", ignoreCase = true)
            val providerLabel = if (isSmartcar) "Smartcar" else "Tesla"
            val connected = odometerSyncService.handleOAuthCallback(uri)
            if (connected) {
                _toastMessage.tryEmit("$providerLabel connected! Syncing odometer...")
                val targetProvider = if (isSmartcar) {
                    OdometerSyncService.PROVIDER_SMARTCAR
                } else {
                    OdometerSyncService.PROVIDER_TESLA
                }
                val currentCars = carDao.getAllCarsList().map { it.toModel() }
                val matchingCars = currentCars.filter {
                    !it.isArchived && OdometerSyncService.resolveProviderForCar(it) == targetProvider
                }
                for (car in matchingCars) {
                    val result = odometerSyncService.syncVehicle(car = car, isManual = true, forceWake = true)
                    _toastMessage.tryEmit(OdometerSyncService.formatSyncResultMessage(car, result))
                }
            } else {
                _toastMessage.tryEmit("Failed to connect $providerLabel")
            }
        }
    }

    suspend fun generateVehicleAiImage(
        year: Int,
        make: String,
        model: String,
        color: String,
        trim: String? = null,
        environment: String? = null
    ): Result<ByteArray> {
        val prompt = geminiAiService.buildVehiclePrompt(
            year = year,
            make = make,
            model = model,
            color = color,
            trim = trim,
            environment = environment ?: "clean paved driveway"
        )
        return geminiAiService.generateVehicleImage(prompt)
    }

    fun saveCustomVehicleImage(car: Car, imageBytes: ByteArray, onComplete: ((Car) -> Unit)? = null) {
        viewModelScope.launch {
            val result = vehicleImageRepository.saveCustomImage(car, imageBytes)
            result.onSuccess { updatedCar ->
                _toastMessage.emit("Updated photo for ${car.displayName}")
                onComplete?.invoke(updatedCar)
            }.onFailure { err ->
                _toastMessage.emit("Failed to save image: ${err.message}")
            }
        }
    }

    fun saveVehicleImageFromUri(car: Car, uri: Uri, onComplete: ((Car) -> Unit)? = null) {
        viewModelScope.launch {
            val result = vehicleImageRepository.saveImageFromUri(car, uri)
            result.onSuccess { updatedCar ->
                _toastMessage.emit("Updated photo for ${car.displayName}")
                onComplete?.invoke(updatedCar)
            }.onFailure { err ->
                _toastMessage.emit("Failed to import photo: ${err.message}")
            }
        }
    }

    fun resetVehicleImage(car: Car, onComplete: ((Car) -> Unit)? = null) {
        viewModelScope.launch {
            val result = vehicleImageRepository.resetToDefaultImage(car)
            result.onSuccess { updatedCar ->
                _toastMessage.emit("Reset photo for ${car.displayName}")
                onComplete?.invoke(updatedCar)
            }.onFailure { err ->
                _toastMessage.emit("Failed to reset photo: ${err.message}")
            }
        }
    }
}
