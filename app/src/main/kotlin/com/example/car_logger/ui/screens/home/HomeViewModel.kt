package com.example.car_logger.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.InsightsUiState
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
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.TireRotationCalculator
import com.example.car_logger.data.vehicle.VehicleImageRepository
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedUtilityBillDraft
import com.example.car_logger.ui.components.GarageNavigationTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class ScheduledServiceItem(
    val car: Car,
    val title: String,
    val dueOdometer: Int,
    val remainingMiles: Int,
    val isUrgent: Boolean // <= 500 miles
)

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
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allRecords: StateFlow<List<MaintenanceRecord>> = recordDao.getAllRecords().map { entities ->
        entities.map { it.toModel() }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val scheduledServices: StateFlow<List<ScheduledServiceItem>> = combine(
        cars,
        allRecords,
        tireDao.getAllTires()
    ) { carList, records, tireEntities ->
        val activeTiresByCar = tireEntities
            .groupBy { it.carId }
            .mapValues { (_, list) -> list.firstOrNull()?.toModel() }
        val items = mutableListOf<ScheduledServiceItem>()
        for (car in carList) {
            if (car.isArchived || car.odometer == null) continue
            val carRecords = records.filter { it.carId == car.id }

            if (car.isEv) {
                val status = TireRotationCalculator.calculateRotationStatus(
                    car = car,
                    activeTire = activeTiresByCar[car.id],
                    records = carRecords
                )
                items.add(
                    ScheduledServiceItem(
                        car = car,
                        title = "Tire Rotation & Cabin Filter Inspection",
                        dueOdometer = status.nextRotationOdometer,
                        remainingMiles = status.remainingMilesToRotation,
                        isUrgent = status.remainingMilesToRotation <= 500
                    )
                )
            } else {
                val latestOdo = if (carRecords.isNotEmpty()) {
                    carRecords.maxOf { it.odometer }
                } else {
                    car.odometer
                }
                val interval = 5000
                val nextOdo = latestOdo + interval
                val remaining = nextOdo - car.odometer
                items.add(
                    ScheduledServiceItem(
                        car = car,
                        title = "Oil & Filter Change",
                        dueOdometer = nextOdo,
                        remainingMiles = remaining,
                        isUrgent = remaining <= 500
                    )
                )
            }
        }
        items.sortedBy { it.remainingMiles }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val hasUrgentService: StateFlow<Boolean> = scheduledServices.map { services ->
        services.any { it.isUrgent }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    private val _insightsVehicleFilter = MutableStateFlow<String?>(null)
    val insightsVehicleFilter: StateFlow<String?> = _insightsVehicleFilter.asStateFlow()

    private val _insightsGranularity = MutableStateFlow(InsightsTimeGranularity.WEEKLY)
    val insightsGranularity: StateFlow<InsightsTimeGranularity> = _insightsGranularity.asStateFlow()

    private val _insightsPeriodOffset = MutableStateFlow(0)
    val insightsPeriodOffset: StateFlow<Int> = _insightsPeriodOffset.asStateFlow()

    private val _insightsChargingVenue = MutableStateFlow(ChargingVenue.HOME)
    val insightsChargingVenue: StateFlow<ChargingVenue> = _insightsChargingVenue.asStateFlow()

    private val _isParsingElectricityFile = MutableStateFlow(false)
    val isParsingElectricityFile: StateFlow<Boolean> = _isParsingElectricityFile.asStateFlow()

    private val _pendingUtilityBillDraft = MutableStateFlow<ParsedUtilityBillDraft?>(null)
    val pendingUtilityBillDraft: StateFlow<ParsedUtilityBillDraft?> = _pendingUtilityBillDraft.asStateFlow()

    private val _showUtilityBillSheet = MutableStateFlow(false)
    val showUtilityBillSheet: StateFlow<Boolean> = _showUtilityBillSheet.asStateFlow()

    private val _pendingChargerImport = MutableStateFlow<ParsedChargerImportResult?>(null)
    val pendingChargerImport: StateFlow<ParsedChargerImportResult?> = _pendingChargerImport.asStateFlow()

    private val _showChargerUsageSheet = MutableStateFlow(false)
    val showChargerUsageSheet: StateFlow<Boolean> = _showChargerUsageSheet.asStateFlow()

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
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        InsightsUiState()
    )

    fun setInsightsChargingVenue(venue: ChargingVenue) {
        _insightsChargingVenue.value = venue
    }

    fun openManualUtilityBillSheet() {
        _pendingUtilityBillDraft.value = null
        _showUtilityBillSheet.value = true
    }

    fun dismissUtilityBillSheet() {
        _showUtilityBillSheet.value = false
        _pendingUtilityBillDraft.value = null
    }

    fun openManualChargerUsageSheet() {
        _pendingChargerImport.value = null
        _showChargerUsageSheet.value = true
    }

    fun dismissChargerUsageSheet() {
        _showChargerUsageSheet.value = false
        _pendingChargerImport.value = null
    }

    fun parseUtilityBillUri(uri: Uri) {
        viewModelScope.launch {
            _isParsingElectricityFile.value = true
            val result = withContext(Dispatchers.IO) {
                readAndParseUtilityBill(uri)
            }
            _isParsingElectricityFile.value = false
            _pendingUtilityBillDraft.value = result
            _showUtilityBillSheet.value = true
        }
    }

    private suspend fun readAndParseUtilityBill(uri: Uri): ParsedUtilityBillDraft {
        val fileName = uriContentReader.resolveFileName(uri) ?: "utility_bill"
        val mimeType = uriContentReader.resolveMimeType(uri, fileName)
        val bytes = uriContentReader.readBytes(uri)
            ?: return ParsedUtilityBillDraft(
                providerName = "",
                startDateIso = "",
                endDateIso = "",
                totalKwh = 0.0,
                totalAmount = 0.0,
                fixedCharges = 0.0,
                source = "manual",
                fileName = fileName,
                error = "Couldn't open $fileName. Enter the bill details manually."
            )

        return geminiAiService.parseUtilityBill(
            fileName = fileName,
            mimeType = mimeType,
            fileBytes = bytes
        )
    }

    fun parseChargerUsageUri(uri: Uri) {
        viewModelScope.launch {
            _isParsingElectricityFile.value = true
            val result = withContext(Dispatchers.IO) {
                readAndParseChargerUsage(uri)
            }
            _isParsingElectricityFile.value = false
            _pendingChargerImport.value = result
            _showChargerUsageSheet.value = true
        }
    }

    private suspend fun readAndParseChargerUsage(uri: Uri): ParsedChargerImportResult {
        val fileName = uriContentReader.resolveFileName(uri) ?: "charger_export"
        val mimeType = uriContentReader.resolveMimeType(uri, fileName)
        val bytes = uriContentReader.readBytes(uri)
            ?: return ParsedChargerImportResult(
                sessions = emptyList(),
                fileName = fileName,
                source = "manual",
                error = "Couldn't open $fileName. Enter sessions or monthly totals manually."
            )

        val isCsv = fileName.endsWith(".csv", ignoreCase = true) ||
            mimeType.contains("csv", ignoreCase = true) ||
            mimeType == "text/plain" ||
            mimeType == "text/comma-separated-values"

        if (isCsv) {
            val csvText = String(bytes, Charsets.UTF_8)
            val parsed = ElectricityImportParser.parseChargerCsv(fileName, csvText)
            return if (parsed.isNotEmpty()) {
                ParsedChargerImportResult(
                    sessions = parsed,
                    fileName = fileName,
                    source = "csv"
                )
            } else {
                ParsedChargerImportResult(
                    sessions = emptyList(),
                    fileName = fileName,
                    source = "csv",
                    error = "Couldn't detect date and kWh columns in $fileName. Try a screenshot or PDF, or enter it manually."
                )
            }
        }

        return geminiAiService.parseChargerUsage(
            fileName = fileName,
            mimeType = mimeType,
            fileBytes = bytes
        )
    }

    fun saveUtilityBill(
        providerName: String,
        startDateMs: Long,
        endDateMs: Long,
        totalKwh: Double,
        totalAmount: Double,
        fixedCharges: Double,
        overrideRate: Double?,
        replaceExistingId: String?
    ) {
        viewModelScope.launch {
            val calculatedRate = ElectricityImportParser.calculateVariableBlendedRate(
                totalAmount = totalAmount,
                fixedCharges = fixedCharges,
                totalKwh = totalKwh
            )
            val effectiveRate = overrideRate ?: calculatedRate
            withContext(Dispatchers.IO) {
                if (replaceExistingId != null) {
                    utilityBillDao.deleteBillById(replaceExistingId)
                }
                utilityBillDao.insertBill(
                    UtilityBillEntity(
                        id = "bill_${UUID.randomUUID()}",
                        providerName = providerName,
                        startDateMs = startDateMs,
                        endDateMs = endDateMs,
                        totalKwh = totalKwh,
                        totalAmount = totalAmount,
                        fixedCharges = fixedCharges,
                        calculatedRate = calculatedRate,
                        overrideRate = overrideRate,
                        effectiveRate = effectiveRate,
                        uploadedAtMs = System.currentTimeMillis()
                    )
                )
            }
            preferencesManager.setDefaultElectricityRate(effectiveRate.toFloat())
            preferencesManager.setUseCustomElectricityRate(false)
            dismissUtilityBillSheet()
            _toastMessage.emit(
                "Saved electric bill ($${String.format(Locale.US, "%.3f", effectiveRate)}/kWh)"
            )
        }
    }

    fun saveChargingSessions(sessions: List<ChargingSessionEntity>, skippedFromUi: Int) {
        viewModelScope.launch {
            var insertedCount = 0
            var totalSkipped = skippedFromUi
            val batchId = "batch_${UUID.randomUUID()}"
            val uploadedAtMs = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val existingFingerprints = chargingSessionDao.getAllFingerprints().toMutableSet()
                val uniqueNew = mutableListOf<ChargingSessionEntity>()
                for (session in sessions) {
                    if (existingFingerprints.add(session.fingerprint)) {
                        uniqueNew.add(
                            session.copy(
                                uploadBatchId = batchId,
                                uploadedAtMs = uploadedAtMs
                            )
                        )
                    } else {
                        totalSkipped++
                    }
                }
                if (uniqueNew.isNotEmpty()) {
                    chargingSessionDao.insertSessions(uniqueNew)
                }
                insertedCount = uniqueNew.size
            }
            dismissChargerUsageSheet()
            val msg = when {
                insertedCount > 0 && totalSkipped > 0 ->
                    "Added $insertedCount charging ${if (insertedCount == 1) "session" else "sessions"} ($totalSkipped ${if (totalSkipped == 1) "duplicate" else "duplicates"} skipped)"
                insertedCount > 0 ->
                    "Added $insertedCount charging ${if (insertedCount == 1) "session" else "sessions"}"
                totalSkipped > 0 ->
                    "All $totalSkipped sessions were already saved"
                else -> "No charging sessions saved"
            }
            _toastMessage.emit(msg)
        }
    }

    fun undoLastChargingUpload(reopenForRetag: Boolean = false) {
        viewModelScope.launch {
            val lastBatch = withContext(Dispatchers.IO) {
                val all = chargingSessionDao.getAllSessionsList()
                val batch = ElectricityImportParser.resolveLastUploadBatch(all)
                if (batch.isNotEmpty()) {
                    batch.map { it.id }.chunked(500).forEach { chunk ->
                        chargingSessionDao.deleteSessionsByIds(chunk)
                    }
                }
                batch
            }
            if (lastBatch.isEmpty()) {
                _toastMessage.emit("No charging import to undo")
                return@launch
            }
            if (reopenForRetag) {
                _pendingChargerImport.value = ElectricityImportParser.sessionsToImportResult(
                    sessions = lastBatch,
                    fileName = "last import"
                )
                _showChargerUsageSheet.value = true
                _toastMessage.emit(
                    "Removed ${lastBatch.size} ${if (lastBatch.size == 1) "session" else "sessions"} — choose a vehicle to save again"
                )
            } else {
                _toastMessage.emit(
                    "Removed ${lastBatch.size} charging ${if (lastBatch.size == 1) "session" else "sessions"}"
                )
            }
        }
    }

    fun deleteUtilityBill(billId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                utilityBillDao.deleteBillById(billId)
            }
            _toastMessage.emit("Deleted electric bill")
        }
    }

    fun deleteChargingSession(sessionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chargingSessionDao.deleteSessionById(sessionId)
            }
            _toastMessage.emit("Deleted charging session")
        }
    }

    fun updateDefaultElectricityRate(rate: Float) {
        viewModelScope.launch {
            preferencesManager.setCustomElectricityRate(rate)
            _toastMessage.emit(
                "Home rate set to $${String.format(Locale.US, "%.3f", rate)}/kWh"
            )
        }
    }

    fun useSavedBillRate() {
        viewModelScope.launch {
            preferencesManager.setUseCustomElectricityRate(false)
            _toastMessage.emit("Using rates from saved bills")
        }
    }

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
        if (_insightsPeriodOffset.value < 0) {
            _insightsPeriodOffset.value += 1
        }
    }

    fun resetInsightsPeriod() {
        _insightsPeriodOffset.value = 0
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
            // Auto update car odometer if new record is higher
            val car = carDao.getCarById(record.carId)
            if (car != null && (car.odometer == null || record.odometer > car.odometer)) {
                carDao.updateOdometer(record.carId, record.odometer)
            }
            if (record.odometer > 0) {
                val ts = try {
                    java.time.LocalDate.parse(record.date.take(10))
                        .atStartOfDay(java.time.ZoneId.systemDefault())
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

    fun syncCarOdometer(car: Car, forceWake: Boolean = false) {
        viewModelScope.launch {
            val result = odometerSyncService.syncVehicle(car = car, isManual = true, forceWake = forceWake)
            _toastMessage.tryEmit(OdometerSyncService.formatSyncResultMessage(car, result))
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            odometerSyncService.syncWallConnectorCharging(currentCars)
        }
    }

    fun syncWallConnectorCharging() {
        viewModelScope.launch {
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            val result = odometerSyncService.syncWallConnectorCharging(currentCars)
            _toastMessage.emit(OdometerSyncService.formatWallConnectorSyncMessage(result))
        }
    }

    fun syncWorkCharging() {
        viewModelScope.launch {
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            val result = odometerSyncService.syncTeslaWorkCharging(currentCars, forceWake = false)
            _toastMessage.emit(OdometerSyncService.formatWorkChargingSyncMessage(result))
        }
    }

    fun syncPublicCharging() {
        viewModelScope.launch {
            val currentCars = cars.value.ifEmpty { carDao.getAllCarsList().map { it.toModel() } }
            val (periodStartMs, periodEndMs) = InsightsCalculationEngine.resolvePeriodBounds(
                granularity = insightsGranularity.value,
                periodOffset = insightsPeriodOffset.value,
                nowMs = System.currentTimeMillis()
            )
            val scResult = odometerSyncService.syncTeslaSuperchargerCharging(
                cars = currentCars,
                startInstant = Instant.ofEpochMilli(periodStartMs),
                endInstant = Instant.ofEpochMilli(periodEndMs)
            )
            val smartcarResult = odometerSyncService.syncSmartcarDcCharging(
                cars = currentCars,
                forceCapabilityRecheck = true,
                allowLiveProbe = true
            )
            val parts = buildList {
                if (scResult !is com.example.car_logger.services.SuperchargerSyncResult.NotConfigured) {
                    add(OdometerSyncService.formatSuperchargerSyncMessage(scResult))
                }
                if (smartcarResult !is com.example.car_logger.services.SmartcarDcSyncResult.NotConfigured) {
                    val includeSmartcar = smartcarResult is com.example.car_logger.services.SmartcarDcSyncResult.Synced &&
                        smartcarResult.insertedCount > 0 ||
                        scResult is com.example.car_logger.services.SuperchargerSyncResult.NotConfigured ||
                        smartcarResult is com.example.car_logger.services.SmartcarDcSyncResult.MissingPermission
                    if (includeSmartcar) {
                        add(OdometerSyncService.formatSmartcarDcSyncMessage(smartcarResult))
                    }
                }
            }
            _toastMessage.emit(
                parts.joinToString(" · ").ifBlank { "Connect Tesla or Smartcar in Settings to sync public charging" }
            )
        }
    }

    fun updateChargingSessionDirectCost(sessionId: String, newCost: Double) {
        viewModelScope.launch {
            val sanitizedCost = newCost.coerceAtLeast(0.0)
            val updated = withContext(Dispatchers.IO) {
                val existing = chargingSessionDao.getAllSessionsList().firstOrNull { it.id == sessionId }
                if (existing != null) {
                    chargingSessionDao.insertSession(existing.copy(directCost = sanitizedCost))
                    true
                } else {
                    false
                }
            }
            if (updated) {
                _toastMessage.emit(
                    "Updated session cost to $${String.format(Locale.US, "%.2f", sanitizedCost)}"
                )
            }
        }
    }

    fun handleOAuthCallback(uri: Uri) {
        viewModelScope.launch {
            val isSmartcar = uri.path.orEmpty().contains("smartcar", ignoreCase = true)
            val providerLabel = if (isSmartcar) "Smartcar" else "Tesla"
            val connected = odometerSyncService.handleOAuthCallback(uri)
            if (connected) {
                _toastMessage.tryEmit("$providerLabel connected. Syncing odometer...")
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
                if (!isSmartcar) {
                    val chargerResult = odometerSyncService.syncWallConnectorCharging(currentCars)
                    when (chargerResult) {
                        is com.example.car_logger.services.WallConnectorSyncResult.Synced -> {
                            if (chargerResult.insertedCount > 0) {
                                _toastMessage.tryEmit(OdometerSyncService.formatWallConnectorSyncMessage(chargerResult))
                            }
                        }
                        is com.example.car_logger.services.WallConnectorSyncResult.MissingScope,
                        is com.example.car_logger.services.WallConnectorSyncResult.Error,
                        is com.example.car_logger.services.WallConnectorSyncResult.NoDevices -> {
                            _toastMessage.tryEmit(OdometerSyncService.formatWallConnectorSyncMessage(chargerResult))
                        }
                        com.example.car_logger.services.WallConnectorSyncResult.NotConfigured -> Unit
                    }

                    val (teslaAccess, _, _) = preferencesManager.getTeslaTokens()
                    if (OdometerSyncService.hasTeslaChargingHistoryScope(teslaAccess)) {
                        val scResult = odometerSyncService.syncTeslaSuperchargerCharging(currentCars)
                        if (scResult is com.example.car_logger.services.SuperchargerSyncResult.Synced &&
                            scResult.insertedCount > 0
                        ) {
                            _toastMessage.tryEmit(OdometerSyncService.formatSuperchargerSyncMessage(scResult))
                        }
                    }
                }
            } else {
                val detail = odometerSyncService.lastSyncMessage.value
                _toastMessage.tryEmit(detail ?: "Couldn't connect $providerLabel")
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
                _toastMessage.emit("Couldn't save photo")
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
                _toastMessage.emit("Couldn't import photo")
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
                _toastMessage.emit("Couldn't reset photo")
            }
        }
    }
}
