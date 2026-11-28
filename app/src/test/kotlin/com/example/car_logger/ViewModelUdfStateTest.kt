package com.example.car_logger

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.GloveboxDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.dao.TireDao
import com.example.car_logger.data.local.dao.UtilityBillDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.GloveboxEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.TireEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.vehicle.VehicleImageRepository
import com.example.car_logger.services.AutoDevService
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.components.GarageNavigationTab
import com.example.car_logger.ui.components.VehicleSubView
import com.example.car_logger.ui.screens.addcar.AddCarViewModel
import com.example.car_logger.ui.screens.home.HomeViewModel
import com.example.car_logger.ui.screens.vehicledetails.VehicleDetailsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelUdfStateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope
    private lateinit var viewModelStore: ViewModelStore
    private val trackedViewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        viewModelStore = ViewModelStore()
        trackedViewModels.clear()
    }

    @After
    fun tearDown() {
        val children = trackedViewModels.flatMap { vm ->
            vm.viewModelScope.coroutineContext[Job]?.children?.toList().orEmpty()
        }
        viewModelStore.clear()
        kotlinx.coroutines.runBlocking {
            children.forEach { it.join() }
        }
        trackedViewModels.clear()
        testScope.cancel()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> registerInStore(key: String, modelClass: Class<T>, creator: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
        }
        return ViewModelProvider(viewModelStore, factory)[key, modelClass].also {
            trackedViewModels.add(it)
        }
    }

    @Test
    fun addCarViewModelIntentSettersUpdateStateFlowsAndBuildCar() {
        val prefs = createPrefs("add_car")
        val vm = registerInStore("add_car", AddCarViewModel::class.java) {
            AddCarViewModel(AutoDevService(prefs))
        }

        assertNull(vm.buildCar())

        vm.onVinChange("5yj3e1ea7jf000001")
        assertEquals("5YJ3E1EA7JF000001", vm.vinInput.value)

        vm.onMakeSelected("Tesla")
        vm.onModelSelected("Model 3")
        vm.onImageUrlSelected("https://example.com/model3.png")
        vm.onYearChange(2025)
        vm.onOdometerChange(14_250)
        vm.onLicensePlateChange("  EV 2025  ")
        vm.onIsEvChange(true)
        vm.onTireRotationIntervalChange(null)

        val built = vm.buildCar()
        assertNotNull(built)
        assertEquals("Tesla", built!!.make)
        assertEquals("Model 3", built.model)
        assertEquals(2025, built.year)
        assertEquals("https://example.com/model3.png", built.imageUrl)
        assertEquals(14_250, built.odometer)
        assertEquals("EV 2025", built.licensePlate)
        assertEquals("5YJ3E1EA7JF000001", built.vin)
        assertTrue(built.isEv)
        assertEquals(7500, built.tireRotationInterval)

        vm.onTireRotationIntervalChange(6000)
        assertEquals(6000, vm.buildCar()!!.tireRotationInterval)

        vm.onVinChange("short")
        vm.decodeVin()
        assertEquals("Enter a 17-character VIN", vm.vinError.value)
        vm.onVinChange("123")
        assertNull(vm.vinError.value)
    }

    @Test
    fun homeViewModelUdfIntentsAndMappedFlowsEmitWithoutTabCombine() = runTest {
        val prefs = createPrefs("home_udf")
        val carDao = MutableCarDao()
        val recordDao = MutableRecordDao()
        val odometerLogDao = NoOpOdometerLogDao()
        val chargingSessionDao = NoOpChargingSessionDao()
        val uriContentReader = NoOpUriContentReader()

        val vm = registerInStore("home_udf", HomeViewModel::class.java) {
            HomeViewModel(
                carDao = carDao,
                recordDao = recordDao,
                tireDao = NoOpTireDao(),
                odometerLogDao = odometerLogDao,
                utilityBillDao = NoOpUtilityBillDao(),
                chargingSessionDao = chargingSessionDao,
                preferencesManager = prefs,
                googleDriveService = GoogleDriveService(initialUser = null),
                odometerSyncService = OdometerSyncService(prefs, carDao, odometerLogDao, chargingSessionDao),
                vehicleImageRepository = VehicleImageRepository(
                    imagesDir = tempFolder.newFolder("home_images"),
                    uriContentReader = uriContentReader,
                    carDao = carDao
                ),
                geminiAiService = GeminiAiService(prefs),
                uriContentReader = uriContentReader
            )
        }

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(dispatcher) { vm.cars.collect {} }
        backgroundScope.launch(dispatcher) { vm.allRecords.collect {} }
        backgroundScope.launch(dispatcher) { vm.scheduledServices.collect {} }
        backgroundScope.launch(dispatcher) { vm.hasUrgentService.collect {} }

        vm.toggleSpeedDial()
        assertTrue(vm.isSpeedDialOpen.value)
        vm.setTab(GarageNavigationTab.INSIGHTS)
        assertEquals(GarageNavigationTab.INSIGHTS, vm.currentTab.value)
        assertFalse(vm.isSpeedDialOpen.value)
        vm.toggleSpeedDial()
        vm.closeSpeedDial()
        assertFalse(vm.isSpeedDialOpen.value)

        vm.setInsightsVehicleFilter("car-1")
        assertEquals("car-1", vm.insightsVehicleFilter.value)
        vm.navigateInsightsPreviousPeriod()
        assertEquals(-1, vm.insightsPeriodOffset.value)
        vm.navigateInsightsNextPeriod()
        assertEquals(0, vm.insightsPeriodOffset.value)
        vm.navigateInsightsNextPeriod()
        assertEquals(0, vm.insightsPeriodOffset.value)
        vm.navigateInsightsPreviousPeriod()
        vm.setInsightsGranularity(InsightsTimeGranularity.MONTHLY)
        assertEquals(InsightsTimeGranularity.MONTHLY, vm.insightsGranularity.value)
        assertEquals(0, vm.insightsPeriodOffset.value)
        vm.navigateInsightsPreviousPeriod()
        vm.resetInsightsPeriod()
        assertEquals(0, vm.insightsPeriodOffset.value)
        vm.setInsightsChargingVenue(ChargingVenue.PUBLIC)
        assertEquals(ChargingVenue.PUBLIC, vm.insightsChargingVenue.value)

        carDao.carsFlow.value = listOf(
            CarEntity(id = "car-1", make = "Honda", model = "Civic", year = 2020, odometer = 4_800, isEv = false)
        )
        recordDao.recordsFlow.value = listOf(
            MaintenanceRecordEntity(
                id = "rec-1",
                carId = "car-1",
                title = "Initial Check",
                date = "2026-01-10",
                odometer = 100
            )
        )

        withTimeout(5_000) {
            vm.cars.first { it.size == 1 }
            vm.allRecords.first { it.size == 1 }
            vm.hasUrgentService.first { it }
        }
        assertEquals(1, vm.cars.value.size)
        assertEquals(1, vm.allRecords.value.size)
        assertTrue(vm.hasUrgentService.value)
    }

    @Test
    fun homeViewModelDeleteMaintenanceRecordRemovesRecordAndEmitsToast() = runTest {
        val prefs = createPrefs("home_delete_rec")
        val carDao = MutableCarDao()
        val recordDao = MutableRecordDao()
        val odometerLogDao = NoOpOdometerLogDao()
        val chargingSessionDao = NoOpChargingSessionDao()
        val uriContentReader = NoOpUriContentReader()

        recordDao.recordsFlow.value = listOf(
            MaintenanceRecordEntity(
                id = "rec-1",
                carId = "car-1",
                title = "Cabin Air Filter",
                date = "2026-02-14",
                odometer = 12_000
            )
        )

        val vm = registerInStore("home_delete_rec", HomeViewModel::class.java) {
            HomeViewModel(
                carDao = carDao,
                recordDao = recordDao,
                tireDao = NoOpTireDao(),
                odometerLogDao = odometerLogDao,
                utilityBillDao = NoOpUtilityBillDao(),
                chargingSessionDao = chargingSessionDao,
                preferencesManager = prefs,
                googleDriveService = GoogleDriveService(initialUser = null),
                odometerSyncService = OdometerSyncService(prefs, carDao, odometerLogDao, chargingSessionDao),
                vehicleImageRepository = VehicleImageRepository(
                    imagesDir = tempFolder.newFolder("home_delete_images"),
                    uriContentReader = uriContentReader,
                    carDao = carDao
                ),
                geminiAiService = GeminiAiService(prefs),
                uriContentReader = uriContentReader
            )
        }

        val toastDeferred = CompletableDeferred<String>()
        val collectorJob = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            toastDeferred.complete(vm.toastMessage.first())
        }

        vm.deleteMaintenanceRecord("rec-1")

        val toast = withTimeout(5_000) { toastDeferred.await() }
        collectorJob.cancel()
        assertEquals("Deleted maintenance record", toast)
        assertEquals(listOf("rec-1"), recordDao.deletedIds)
        assertTrue(recordDao.recordsFlow.value.isEmpty())
    }

    @Test
    fun vehicleDetailsViewModelEncapsulatedFlowsFilterRecordsAndUpdateSubView() = runTest {
        val prefs = createPrefs("details_udf")
        val carDao = MutableCarDao()
        val recordDao = MutableRecordDao()
        val odometerLogDao = NoOpOdometerLogDao()
        val chargingSessionDao = NoOpChargingSessionDao()
        val uriContentReader = NoOpUriContentReader()

        recordDao.recordsFlow.value = listOf(
            MaintenanceRecordEntity(
                id = "r1",
                carId = "car-1",
                title = "Tire Rotation",
                date = "2025-04-01",
                odometer = 10_000,
                description = "Rotated all four tires"
            ),
            MaintenanceRecordEntity(
                id = "r2",
                carId = "car-1",
                title = "Brake Fluid Flush",
                date = "2026-02-15",
                odometer = 18_500,
                description = "DOT 4 service"
            )
        )

        val vm = registerInStore("details_udf", VehicleDetailsViewModel::class.java) {
            VehicleDetailsViewModel(
                carId = "car-1",
                initialCar = Car(id = "car-1", make = "Tesla", model = "Model 3", year = 2024, isEv = true),
                carDao = carDao,
                recordDao = recordDao,
                tireDao = NoOpTireDao(),
                gloveboxDao = NoOpGloveboxDao(),
                odometerLogDao = odometerLogDao,
                utilityBillDao = NoOpUtilityBillDao(),
                chargingSessionDao = chargingSessionDao,
                preferencesManager = prefs,
                odometerSyncService = OdometerSyncService(prefs, carDao, odometerLogDao, chargingSessionDao),
                vehicleImageRepository = VehicleImageRepository(
                    imagesDir = tempFolder.newFolder("details_images"),
                    uriContentReader = uriContentReader,
                    carDao = carDao
                ),
                geminiAiService = GeminiAiService(prefs)
            )
        }

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(dispatcher) { vm.records.collect {} }
        backgroundScope.launch(dispatcher) { vm.filteredRecords.collect {} }

        vm.setSubView(VehicleSubView.GLOVEBOX)
        assertEquals(VehicleSubView.GLOVEBOX, vm.currentSubView.value)

        vm.setBentoGranularity(InsightsTimeGranularity.YTD)
        assertEquals(InsightsTimeGranularity.YTD, vm.bentoGranularity.value)

        assertEquals(2, vm.filteredRecords.first { it.size == 2 }.size)

        vm.setSearchQuery("brake")
        assertEquals(listOf("r2"), vm.filteredRecords.first { it.size == 1 }.map { it.id })

        vm.setSearchQuery("")
        vm.setSelectedYearFilter(2025)
        assertEquals(listOf("r1"), vm.filteredRecords.first { it.size == 1 && it.first().id == "r1" }.map { it.id })
    }

    private fun createPrefs(name: String): PreferencesManager {
        val prefsFile = File(tempFolder.newFolder("prefs_$name"), "test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) { prefsFile }
        return PreferencesManager(dataStore)
    }

    private class MutableCarDao : CarDao {
        val carsFlow = MutableStateFlow<List<CarEntity>>(emptyList())
        override fun getAllCars(): Flow<List<CarEntity>> = carsFlow
        override suspend fun getAllCarsList(): List<CarEntity> = carsFlow.value
        override suspend fun getCarById(carId: String): CarEntity? = carsFlow.value.firstOrNull { it.id == carId }
        override fun observeCarById(carId: String): Flow<CarEntity?> = flowOf(null)
        override suspend fun insertCar(car: CarEntity) = Unit
        override suspend fun insertCars(cars: List<CarEntity>) = Unit
        override suspend fun updateCar(car: CarEntity) = Unit
        override suspend fun toggleArchive(carId: String) = Unit
        override suspend fun updateOdometer(carId: String, odometer: Int) = Unit
        override suspend fun updateOdometerFromTelemetry(
            carId: String,
            odometer: Int,
            provider: String,
            status: String,
            syncTime: Long
        ) = Unit
        override suspend fun updateTelemetryStatus(carId: String, provider: String, status: String) = Unit
        override suspend fun updateDriveFolder(carId: String, folderId: String?, folderName: String?) = Unit
        override suspend fun deleteCar(car: CarEntity) = Unit
        override suspend fun deleteCarById(carId: String) = Unit
        override suspend fun getCarCount(): Int = carsFlow.value.size
    }

    private class MutableRecordDao : MaintenanceRecordDao {
        val recordsFlow = MutableStateFlow<List<MaintenanceRecordEntity>>(emptyList())
        val deletedIds = mutableListOf<String>()
        override fun getAllRecords(): Flow<List<MaintenanceRecordEntity>> = recordsFlow
        override suspend fun getAllRecordsList(): List<MaintenanceRecordEntity> = recordsFlow.value
        override fun getRecordsForCar(carId: String): Flow<List<MaintenanceRecordEntity>> = recordsFlow
        override suspend fun getRecordsListForCar(carId: String): List<MaintenanceRecordEntity> = recordsFlow.value
        override suspend fun getRecordByDriveFileId(driveFileId: String): MaintenanceRecordEntity? = null
        override suspend fun getRecordById(id: String): MaintenanceRecordEntity? = null
        override suspend fun insertRecord(record: MaintenanceRecordEntity) = Unit
        override suspend fun insertRecords(records: List<MaintenanceRecordEntity>) = Unit
        override suspend fun updateRecord(record: MaintenanceRecordEntity) = Unit
        override suspend fun deleteRecord(record: MaintenanceRecordEntity) = Unit
        override suspend fun deleteRecordById(id: String) {
            deletedIds.add(id)
            recordsFlow.value = recordsFlow.value.filterNot { it.id == id }
        }
        override suspend fun deleteRecordsForCar(carId: String) = Unit
    }

    private class NoOpTireDao : TireDao {
        override fun getActiveTireForCar(carId: String): Flow<TireEntity?> = flowOf(null)
        override fun getAllTiresForCar(carId: String): Flow<List<TireEntity>> = flowOf(emptyList())
        override fun getAllTires(): Flow<List<TireEntity>> = flowOf(emptyList())
        override suspend fun insertTire(tire: TireEntity) = Unit
        override suspend fun updateTire(tire: TireEntity) = Unit
        override suspend fun deleteTire(tire: TireEntity) = Unit
    }

    private class NoOpGloveboxDao : GloveboxDao {
        override fun getGloveboxForCar(carId: String): Flow<GloveboxEntity?> = flowOf(null)
        override suspend fun getGloveboxDirect(carId: String): GloveboxEntity? = null
        override suspend fun upsertGlovebox(glovebox: GloveboxEntity) = Unit
    }

    private class NoOpOdometerLogDao : OdometerLogDao {
        override fun getAllLogs(): Flow<List<OdometerLogEntity>> = flowOf(emptyList())
        override suspend fun getAllLogsList(): List<OdometerLogEntity> = emptyList()
        override fun getLogsForCar(carId: String): Flow<List<OdometerLogEntity>> = flowOf(emptyList())
        override suspend fun getLogsForCarList(carId: String): List<OdometerLogEntity> = emptyList()
        override suspend fun getLatestLogForCar(carId: String): OdometerLogEntity? = null
        override suspend fun deleteNonTelemetryLogs() = Unit
        override suspend fun getLogCount(): Int = 0
        override suspend fun insertLog(log: OdometerLogEntity) = Unit
        override suspend fun insertLogs(logs: List<OdometerLogEntity>) = Unit
        override suspend fun deleteLogsForCar(carId: String) = Unit
    }

    private class NoOpUtilityBillDao : UtilityBillDao {
        override fun getAllBills(): Flow<List<UtilityBillEntity>> = flowOf(emptyList())
        override suspend fun getAllBillsList(): List<UtilityBillEntity> = emptyList()
        override suspend fun findByPeriod(startDateMs: Long, endDateMs: Long): UtilityBillEntity? = null
        override suspend fun insertBill(bill: UtilityBillEntity) = Unit
        override suspend fun deleteBillById(id: String) = Unit
    }

    private class NoOpChargingSessionDao : ChargingSessionDao {
        override fun getAllSessions(): Flow<List<ChargingSessionEntity>> = flowOf(emptyList())
        override suspend fun getAllSessionsList(): List<ChargingSessionEntity> = emptyList()
        override suspend fun getAllFingerprints(): List<String> = emptyList()
        override suspend fun insertSessions(sessions: List<ChargingSessionEntity>) = Unit
        override suspend fun insertSession(session: ChargingSessionEntity) = Unit
        override suspend fun deleteSessionById(id: String) = Unit
        override suspend fun deleteSessionsByIds(ids: List<String>) = Unit
        override suspend fun deleteSessionsByBatchId(batchId: String) = Unit
    }

    private class NoOpUriContentReader : UriContentReader {
        override fun resolveFileName(uri: Uri): String? = null
        override fun resolveMimeType(uri: Uri, fileName: String): String = "application/octet-stream"
        override fun readBytes(uri: Uri): ByteArray? = null
    }
}
