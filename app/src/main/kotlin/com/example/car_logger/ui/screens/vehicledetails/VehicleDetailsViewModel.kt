package com.example.car_logger.ui.screens.vehicledetails

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.VehicleCostHistoryCalculator
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.GloveboxDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.dao.TireDao
import com.example.car_logger.data.local.dao.UtilityBillDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.GloveboxEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.TireEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Glovebox
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.Tire
import com.example.car_logger.data.model.TireRotationCalculator
import com.example.car_logger.data.vehicle.VehicleImageRepository
import com.example.car_logger.services.DriveFileItem
import com.example.car_logger.services.DriveReceiptSyncUseCase
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.components.VehicleSubView
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleCostHistory
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleInsightsBentoCalculator
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleInsightsBentoState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class VehicleDetailsViewModel(
    val carId: String,
    initialCar: Car? = null,
    private val carDao: CarDao,
    private val recordDao: MaintenanceRecordDao,
    private val tireDao: TireDao,
    private val gloveboxDao: GloveboxDao,
    private val odometerLogDao: OdometerLogDao,
    private val utilityBillDao: UtilityBillDao,
    private val chargingSessionDao: ChargingSessionDao,
    private val preferencesManager: PreferencesManager,
    private val googleDriveService: GoogleDriveService,
    val odometerSyncService: OdometerSyncService,
    val vehicleImageRepository: VehicleImageRepository,
    val geminiAiService: GeminiAiService,
    val driveReceiptSyncUseCase: DriveReceiptSyncUseCase
) : ViewModel() {

    val isSyncingOdometer: StateFlow<Boolean> = odometerSyncService.isSyncing
    val isDriveSignedIn: StateFlow<Boolean> = googleDriveService.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cooldownHoursFlow: StateFlow<Int> = preferencesManager.syncCooldownHoursFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PreferencesManager.DEFAULT_COOLDOWN_HOURS
    )

    private val _currentSubView = MutableStateFlow(VehicleSubView.VEHICLE)
    val currentSubView: StateFlow<VehicleSubView> = _currentSubView.asStateFlow()

    private val _bentoGranularity = MutableStateFlow(InsightsTimeGranularity.WEEKLY)
    val bentoGranularity: StateFlow<InsightsTimeGranularity> = _bentoGranularity.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedYearFilter = MutableStateFlow<Int?>(null)
    val selectedYearFilter: StateFlow<Int?> = _selectedYearFilter.asStateFlow()

    private val _isSyncingDrive = MutableStateFlow(false)
    val isSyncingDrive: StateFlow<Boolean> = _isSyncingDrive.asStateFlow()

    val car: StateFlow<Car?> = carDao.observeCarById(carId).map { it?.toModel() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        initialCar
    )

    val records: StateFlow<List<MaintenanceRecord>> = recordDao.getRecordsForCar(carId).map { list ->
        list.map { it.toModel() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val costHistory: StateFlow<VehicleCostHistory> = combine(records, car) { recordsList, carItem ->
        VehicleCostHistoryCalculator.calculate(records = recordsList, car = carItem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VehicleCostHistory(0.0, null, emptyList(), 0.0))

    private data class BentoPricingPrefs(
        val gasPrice: Float,
        val baselineMpg: Float,
        val defaultElectricityRate: Float,
        val useCustomElectricityRate: Boolean
    )

    val vehicleBentoState: StateFlow<VehicleInsightsBentoState> = combine(
        combine(
            car,
            carDao.getAllCars().map { list -> list.map { it.toModel() } },
            odometerLogDao.getAllLogs(),
            ::Triple
        ),
        combine(records, costHistory, ::Pair),
        combine(utilityBillDao.getAllBills(), chargingSessionDao.getAllSessions(), ::Pair),
        combine(
            preferencesManager.gasPriceFlow,
            preferencesManager.baselineMpgFlow,
            preferencesManager.defaultElectricityRateFlow,
            preferencesManager.useCustomElectricityRateFlow,
            ::BentoPricingPrefs
        )
    ) { (targetCar, allCars, allLogs), (carRecords, carCostHistory), (bills, sessions), pricing ->
        VehicleInsightsBentoCalculator.compute(
            targetCar = targetCar,
            allCars = allCars,
            allLogs = allLogs,
            records = carRecords,
            costHistory = carCostHistory,
            utilityBills = bills,
            chargingSessions = sessions,
            gasPrice = pricing.gasPrice,
            baselineMpg = pricing.baselineMpg,
            defaultElectricityRate = pricing.defaultElectricityRate,
            useCustomElectricityRate = pricing.useCustomElectricityRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VehicleInsightsBentoState())

    val filteredRecords: StateFlow<List<MaintenanceRecord>> =
        combine(records, searchQuery, selectedYearFilter) { recs, query, year ->
            recs.filter { rec ->
                val matchesSearch = query.isBlank() ||
                    rec.title.contains(query, ignoreCase = true) ||
                    rec.description?.contains(query, ignoreCase = true) == true
                val recYear = rec.date.take(4).toIntOrNull()
                val matchesYear = year == null || recYear == year
                matchesSearch && matchesYear
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTire: StateFlow<Tire?> = tireDao.getActiveTireForCar(carId).map { it?.toModel() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val glovebox: StateFlow<Glovebox?> = gloveboxDao.getGloveboxForCar(carId).map { it?.toModel() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setBentoGranularity(granularity: InsightsTimeGranularity) {
        _bentoGranularity.value = granularity
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedYearFilter(year: Int?) {
        _selectedYearFilter.value = year
    }

    fun setSubView(subView: VehicleSubView) {
        _currentSubView.value = subView
    }

    suspend fun generateVehicleAiImage(
        color: String,
        trim: String? = null,
        environment: String? = null
    ): Result<ByteArray> {
        val c = car.value ?: return Result.failure(IllegalStateException("Vehicle data not loaded"))
        val prompt = geminiAiService.buildVehiclePrompt(
            year = c.year,
            make = c.make,
            model = c.model,
            color = color,
            trim = trim ?: c.trim,
            environment = environment ?: "clean paved driveway"
        )
        return geminiAiService.generateVehicleImage(prompt)
    }

    fun saveCustomVehicleImage(bytes: ByteArray, onComplete: ((Car) -> Unit)? = null) {
        val c = car.value ?: return
        viewModelScope.launch {
            vehicleImageRepository.saveCustomImage(c, bytes).onSuccess { onComplete?.invoke(it) }
        }
    }

    fun saveVehicleImageFromUri(uri: Uri, onComplete: ((Car) -> Unit)? = null) {
        val c = car.value ?: return
        viewModelScope.launch {
            vehicleImageRepository.saveImageFromUri(c, uri).onSuccess { onComplete?.invoke(it) }
        }
    }

    fun resetVehicleImage(onComplete: ((Car) -> Unit)? = null) {
        val c = car.value ?: return
        viewModelScope.launch {
            vehicleImageRepository.resetToDefaultImage(c).onSuccess { onComplete?.invoke(it) }
        }
    }

    fun toggleArchive() {
        viewModelScope.launch { carDao.toggleArchive(carId) }
    }

    fun updateCar(updatedCar: Car) {
        viewModelScope.launch { carDao.updateCar(CarEntity.fromModel(updatedCar)) }
    }

    fun updateOdometer(newOdometer: Int) {
        viewModelScope.launch {
            val prevOdo = carDao.getCarById(carId)?.odometer
            carDao.updateOdometer(carId, newOdometer)
            try {
                val latest = odometerLogDao.getLatestLogForCar(carId)
                val now = System.currentTimeMillis()
                if (latest == null) {
                    if (prevOdo != null && prevOdo > 0 && prevOdo < newOdometer) {
                        odometerLogDao.insertLog(
                            OdometerLogEntity(
                                id = UUID.randomUUID().toString(),
                                carId = carId,
                                odometer = prevOdo,
                                timestamp = now - 86_400_000L,
                                source = "INITIAL"
                            )
                        )
                    }
                    odometerLogDao.insertLog(
                        OdometerLogEntity(
                            id = UUID.randomUUID().toString(),
                            carId = carId,
                            odometer = newOdometer,
                            timestamp = now,
                            source = "MANUAL"
                        )
                    )
                } else if (latest.odometer < newOdometer) {
                    odometerLogDao.insertLog(
                        OdometerLogEntity(
                            id = UUID.randomUUID().toString(),
                            carId = carId,
                            odometer = newOdometer,
                            timestamp = now,
                            source = "MANUAL"
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun syncLiveOdometer(forceWake: Boolean = false, onResult: ((OdometerSyncResult) -> Unit)? = null) {
        val currentCar = car.value ?: return
        viewModelScope.launch {
            val result = odometerSyncService.syncVehicle(currentCar, isManual = true, forceWake = forceWake)
            onResult?.invoke(result)
        }
    }

    suspend fun listDriveFolders(parentId: String? = null, searchName: String? = null): List<DriveFileItem> {
        return if (!searchName.isNullOrBlank()) {
            googleDriveService.listFolders(searchName = searchName)
        } else {
            googleDriveService.listFolders(parentId = parentId ?: "root")
        }
    }

    fun updateDriveFolder(folderId: String?, folderName: String?) {
        viewModelScope.launch {
            carDao.updateDriveFolder(carId, folderId, folderName)
            if (!folderId.isNullOrBlank()) {
                syncDriveFolder(folderIdOverride = folderId)
            }
        }
    }

    fun syncDriveFolder(folderIdOverride: String? = null) {
        val targetFolderId = folderIdOverride ?: car.value?.driveFolderId ?: return
        if (_isSyncingDrive.value) return

        viewModelScope.launch {
            _isSyncingDrive.value = true
            try {
                driveReceiptSyncUseCase.syncFolder(
                    carId = carId,
                    folderId = targetFolderId,
                    fallbackOdometer = car.value?.odometer,
                    existingRecords = records.value
                )
            } finally {
                _isSyncingDrive.value = false
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch { recordDao.deleteRecordById(recordId) }
    }

    fun logTireRotation(date: String, odometer: Int) {
        viewModelScope.launch {
            val current = activeTire.value
            if (current != null) {
                tireDao.updateTire(
                    TireEntity.fromModel(current.copy(lastRotationDate = date, lastRotationOdometer = odometer))
                )
            } else {
                tireDao.insertTire(
                    TireEntity.fromModel(
                        TireRotationCalculator.createDefaultTireForRotation(
                            carId = carId,
                            date = date,
                            rotationOdometer = odometer
                        )
                    )
                )
            }
            recordDao.insertRecord(
                MaintenanceRecordEntity(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    title = "Tire Rotation",
                    date = date,
                    odometer = odometer
                )
            )
            val currentCarOdo = carDao.getCarById(carId)?.odometer
            if (odometer > 0 && (currentCarOdo == null || odometer > currentCarOdo)) {
                carDao.updateOdometer(carId, odometer)
            }
        }
    }

    fun installNewTireSet(tire: Tire) {
        viewModelScope.launch { tireDao.insertTire(TireEntity.fromModel(tire)) }
    }

    fun updateGloveboxDocument(registrationUrl: String? = null, insuranceUrl: String? = null, manualUrl: String? = null) {
        viewModelScope.launch {
            val existing = glovebox.value
            gloveboxDao.upsertGlovebox(
                GloveboxEntity.fromModel(
                    Glovebox(
                        carId = carId,
                        registrationUrl = registrationUrl ?: existing?.registrationUrl,
                        insuranceUrl = insuranceUrl ?: existing?.insuranceUrl,
                        manualUrl = manualUrl ?: existing?.manualUrl
                    )
                )
            )
        }
    }
}
