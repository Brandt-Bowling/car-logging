package com.example.car_logger.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.InsightsUiState
import com.example.car_logger.data.insights.ScheduledServiceCalculator
import com.example.car_logger.data.insights.ScheduledServiceItem
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.dao.TireDao
import com.example.car_logger.data.local.dao.UtilityBillDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.vehicle.VehicleImageRepository
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedUtilityBillDraft
import com.example.car_logger.ui.components.GarageNavigationTab
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class HomeViewModel(
    private val carDao: CarDao,
    private val recordDao: MaintenanceRecordDao,
    private val tireDao: TireDao,
    val odometerLogDao: OdometerLogDao,
    val utilityBillDao: UtilityBillDao,
    val chargingSessionDao: ChargingSessionDao,
    val preferencesManager: PreferencesManager,
    val googleDriveService: GoogleDriveService,
    val odometerSyncService: OdometerSyncService,
    val vehicleImageRepository: VehicleImageRepository,
    val geminiAiService: GeminiAiService,
    private val uriContentReader: UriContentReader
) : ViewModel() {

    private var initialTelemetryChecked = false

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _currentTab = MutableStateFlow(GarageNavigationTab.GARAGE)
    val currentTab: StateFlow<GarageNavigationTab> = _currentTab.asStateFlow()

    private val _isSpeedDialOpen = MutableStateFlow(false)
    val isSpeedDialOpen: StateFlow<Boolean> = _isSpeedDialOpen.asStateFlow()

    val garageName: StateFlow<String> = preferencesManager.garageNameFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PreferencesManager.DEFAULT_GARAGE_NAME
    )

    val cars: StateFlow<List<Car>> = carDao.getAllCars().map { entities ->
        val models = entities.map { it.toModel() }
        if (!initialTelemetryChecked && models.isNotEmpty()) {
            initialTelemetryChecked = true
            viewModelScope.launch {
                odometerSyncService.refreshSmartcarConnectionStatus()
                odometerSyncService.syncAllEligibleVehicles(models)
            }
        }
        models
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecords: StateFlow<List<MaintenanceRecord>> = recordDao.getAllRecords().map { entities ->
        entities.map { it.toModel() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduledServices: StateFlow<List<ScheduledServiceItem>> = combine(
        cars,
        allRecords,
        tireDao.getAllTires()
    ) { carList, records, tireEntities ->
        ScheduledServiceCalculator.calculateScheduledServices(carList, records, tireEntities)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasUrgentService: StateFlow<Boolean> = scheduledServices.map { services ->
        services.any { it.isUrgent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _insightsVehicleFilter = MutableStateFlow<String?>(null)
    val insightsVehicleFilter: StateFlow<String?> = _insightsVehicleFilter.asStateFlow()

    private val _insightsGranularity = MutableStateFlow(InsightsTimeGranularity.WEEKLY)
    val insightsGranularity: StateFlow<InsightsTimeGranularity> = _insightsGranularity.asStateFlow()

    private val _insightsPeriodOffset = MutableStateFlow(0)
    val insightsPeriodOffset: StateFlow<Int> = _insightsPeriodOffset.asStateFlow()

    private val electricityImportController = ElectricityImportController(
        scope = viewModelScope,
        utilityBillDao = utilityBillDao,
        chargingSessionDao = chargingSessionDao,
        preferencesManager = preferencesManager,
        geminiAiService = geminiAiService,
        uriContentReader = uriContentReader,
        emitToast = { _toastMessage.emit(it) }
    )

    private val telemetryController = HomeTelemetryController(
        scope = viewModelScope,
        carDao = carDao,
        preferencesManager = preferencesManager,
        odometerSyncService = odometerSyncService,
        cars = cars,
        insightsGranularity = insightsGranularity,
        insightsPeriodOffset = insightsPeriodOffset,
        tryEmitToast = { _toastMessage.tryEmit(it) },
        emitToast = { _toastMessage.emit(it) }
    )

    val insightsChargingVenue: StateFlow<ChargingVenue> = electricityImportController.insightsChargingVenue
    val isParsingElectricityFile: StateFlow<Boolean> = electricityImportController.isParsingElectricityFile
    val pendingUtilityBillDraft: StateFlow<ParsedUtilityBillDraft?> = electricityImportController.pendingUtilityBillDraft
    val showUtilityBillSheet: StateFlow<Boolean> = electricityImportController.showUtilityBillSheet
    val pendingChargerImport: StateFlow<ParsedChargerImportResult?> = electricityImportController.pendingChargerImport
    val showChargerUsageSheet: StateFlow<Boolean> = electricityImportController.showChargerUsageSheet

    private data class PricingPreferences(
        val gasPrice: Float,
        val baselineMpg: Float,
        val defaultElectricityRate: Float,
        val useCustomElectricityRate: Boolean
    )

    val insightsUiState: StateFlow<InsightsUiState> = combine(
        cars,
        odometerLogDao.getAllLogs(),
        combine(insightsGranularity, insightsVehicleFilter, insightsPeriodOffset, ::Triple),
        combine(
            preferencesManager.gasPriceFlow,
            preferencesManager.baselineMpgFlow,
            preferencesManager.defaultElectricityRateFlow,
            preferencesManager.useCustomElectricityRateFlow,
            ::PricingPreferences
        ),
        combine(utilityBillDao.getAllBills(), chargingSessionDao.getAllSessions(), ::Pair)
    ) { carList, logs, (gran, selectedCar, offset), pricing, (bills, sessions) ->
        InsightsCalculationEngine.computeUiState(
            cars = carList,
            allLogs = logs,
            granularity = gran,
            selectedCarId = selectedCar,
            gasPrice = pricing.gasPrice,
            baselineMpg = pricing.baselineMpg,
            periodOffset = offset,
            utilityBills = bills,
            chargingSessions = sessions,
            defaultElectricityRate = pricing.defaultElectricityRate,
            useCustomElectricityRate = pricing.useCustomElectricityRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    fun setInsightsChargingVenue(venue: ChargingVenue) = electricityImportController.setInsightsChargingVenue(venue)
    fun openManualUtilityBillSheet() = electricityImportController.openManualUtilityBillSheet()
    fun dismissUtilityBillSheet() = electricityImportController.dismissUtilityBillSheet()
    fun openManualChargerUsageSheet() = electricityImportController.openManualChargerUsageSheet()
    fun dismissChargerUsageSheet() = electricityImportController.dismissChargerUsageSheet()
    fun parseUtilityBillUri(uri: Uri) = electricityImportController.parseUtilityBillUri(uri)
    fun parseChargerUsageUri(uri: Uri) = electricityImportController.parseChargerUsageUri(uri)

    fun saveUtilityBill(
        providerName: String,
        startDateMs: Long,
        endDateMs: Long,
        totalKwh: Double,
        totalAmount: Double,
        fixedCharges: Double,
        overrideRate: Double?,
        replaceExistingId: String?
    ) = electricityImportController.saveUtilityBill(
        providerName, startDateMs, endDateMs, totalKwh, totalAmount, fixedCharges, overrideRate, replaceExistingId
    )

    fun saveChargingSessions(sessions: List<ChargingSessionEntity>, skippedFromUi: Int) =
        electricityImportController.saveChargingSessions(sessions, skippedFromUi)

    fun undoLastChargingUpload(reopenForRetag: Boolean = false) =
        electricityImportController.undoLastChargingUpload(reopenForRetag)

    fun deleteUtilityBill(billId: String) = electricityImportController.deleteUtilityBill(billId)
    fun deleteChargingSession(sessionId: String) = electricityImportController.deleteChargingSession(sessionId)
    fun updateDefaultElectricityRate(rate: Float) = electricityImportController.updateDefaultElectricityRate(rate)
    fun useSavedBillRate() = electricityImportController.useSavedBillRate()
    fun updateChargingSessionDirectCost(sessionId: String, newCost: Double) =
        electricityImportController.updateChargingSessionDirectCost(sessionId, newCost)

    fun setInsightsVehicleFilter(carId: String?) {
        _insightsVehicleFilter.value = carId
    }

    fun setInsightsGranularity(granularity: InsightsTimeGranularity) {
        _insightsGranularity.value = granularity
        _insightsPeriodOffset.value = 0
    }

    fun navigateInsightsPreviousPeriod() {
        _insightsPeriodOffset.value -= 1
    }

    fun navigateInsightsNextPeriod() {
        if (_insightsPeriodOffset.value < 0) _insightsPeriodOffset.value += 1
    }

    fun resetInsightsPeriod() {
        _insightsPeriodOffset.value = 0
    }

    fun updateComparisonGasPrice(price: Float) {
        viewModelScope.launch { preferencesManager.setGasPrice(price) }
    }

    fun updateComparisonBaselineMpg(mpg: Float) {
        viewModelScope.launch { preferencesManager.setBaselineMpg(mpg) }
    }

    fun setTab(tab: GarageNavigationTab) {
        _currentTab.value = tab
        _isSpeedDialOpen.value = false
    }

    fun toggleSpeedDial() {
        _isSpeedDialOpen.value = !_isSpeedDialOpen.value
    }

    fun closeSpeedDial() {
        _isSpeedDialOpen.value = false
    }

    fun updateGarageName(newName: String) {
        viewModelScope.launch { preferencesManager.setGarageName(newName) }
    }

    fun toggleArchiveCar(carId: String) {
        viewModelScope.launch { carDao.toggleArchive(carId) }
    }

    fun deleteCar(carId: String) {
        viewModelScope.launch { carDao.deleteCarById(carId) }
    }

    fun updateCar(car: Car) {
        viewModelScope.launch { carDao.updateCar(CarEntity.fromModel(car)) }
    }

    fun addCar(car: Car) {
        viewModelScope.launch {
            carDao.insertCar(CarEntity.fromModel(car))
            val odo = car.odometer
            if (odo != null && odo > 0) {
                odometerLogDao.insertLog(
                    OdometerLogEntity(
                        id = UUID.randomUUID().toString(),
                        carId = car.id,
                        odometer = odo,
                        timestamp = System.currentTimeMillis(),
                        source = "INITIAL"
                    )
                )
            }
        }
    }

    fun addMaintenanceRecord(record: MaintenanceRecord) {
        viewModelScope.launch {
            recordDao.insertRecord(MaintenanceRecordEntity.fromModel(record))
            val car = carDao.getCarById(record.carId)
            if (car != null && (car.odometer == null || record.odometer > car.odometer)) {
                carDao.updateOdometer(record.carId, record.odometer)
            }
            if (record.odometer > 0) {
                val ts = try {
                    LocalDate.parse(record.date.take(10))
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
                odometerLogDao.insertLog(
                    OdometerLogEntity(
                        id = "rec_${record.id}",
                        carId = record.carId,
                        odometer = record.odometer,
                        timestamp = ts,
                        source = "MAINTENANCE"
                    )
                )
            }
        }
    }

    fun deleteMaintenanceRecord(recordId: String) {
        viewModelScope.launch {
            recordDao.deleteRecordById(recordId)
            _toastMessage.emit("Deleted maintenance record")
        }
    }

    fun syncCarOdometer(car: Car, forceWake: Boolean = false) = telemetryController.syncCarOdometer(car, forceWake)
    fun syncWallConnectorCharging() = telemetryController.syncWallConnectorCharging()
    fun syncWorkCharging() = telemetryController.syncWorkCharging()
    fun syncPublicCharging() = telemetryController.syncPublicCharging()
    fun handleOAuthCallback(uri: Uri) = telemetryController.handleOAuthCallback(uri)

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
            vehicleImageRepository.saveCustomImage(car, imageBytes)
                .onSuccess { updatedCar ->
                    _toastMessage.emit("Updated photo for ${car.displayName}")
                    onComplete?.invoke(updatedCar)
                }.onFailure { _toastMessage.emit("Couldn't save photo") }
        }
    }

    fun saveVehicleImageFromUri(car: Car, uri: Uri, onComplete: ((Car) -> Unit)? = null) {
        viewModelScope.launch {
            vehicleImageRepository.saveImageFromUri(car, uri)
                .onSuccess { updatedCar ->
                    _toastMessage.emit("Updated photo for ${car.displayName}")
                    onComplete?.invoke(updatedCar)
                }.onFailure { _toastMessage.emit("Couldn't import photo") }
        }
    }

    fun resetVehicleImage(car: Car, onComplete: ((Car) -> Unit)? = null) {
        viewModelScope.launch {
            vehicleImageRepository.resetToDefaultImage(car)
                .onSuccess { updatedCar ->
                    _toastMessage.emit("Reset photo for ${car.displayName}")
                    onComplete?.invoke(updatedCar)
                }.onFailure { _toastMessage.emit("Couldn't reset photo") }
        }
    }
}
