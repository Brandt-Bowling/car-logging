package com.example.car_logger

import android.content.ContextWrapper
import android.net.Uri
import android.net.testUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
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
import com.example.car_logger.di.AppContainer
import com.example.car_logger.di.AppViewModelFactories
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.SyncSchedulerController
import com.example.car_logger.ui.screens.addcar.AddCarViewModel
import com.example.car_logger.ui.screens.home.HomeViewModel
import com.example.car_logger.ui.screens.vehicledetails.VehicleDetailsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelFactoriesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun factoriesCanBeConstructedFromContextWrapperContainerLazily() {
        val container = AppContainer(ContextWrapper(null))
        val homeFactory = AppViewModelFactories.home(container)
        val detailsFactory = AppViewModelFactories.vehicleDetails(container, carId = "car-1")
        val addCarFactory = AppViewModelFactories.addCar(container)

        assertNotNull(homeFactory)
        assertNotNull(detailsFactory)
        assertNotNull(addCarFactory)
    }

    @Test
    fun factoriesRejectUnknownViewModelClassesOnBothCreateOverloads() {
        val container = AppContainer(ContextWrapper(null))
        val factories = listOf(
            AppViewModelFactories.home(container),
            AppViewModelFactories.vehicleDetails(container, carId = "car-1"),
            AppViewModelFactories.addCar(container)
        )

        factories.forEach { factory ->
            var threw1 = false
            try {
                factory.create(UnrelatedViewModel::class.java)
            } catch (_: IllegalArgumentException) {
                threw1 = true
            }
            assertTrue("Expected IllegalArgumentException for 1-arg create", threw1)

            var threw2 = false
            try {
                factory.create(UnrelatedViewModel::class.java, CreationExtras.Empty)
            } catch (_: IllegalArgumentException) {
                threw2 = true
            }
            assertTrue("Expected IllegalArgumentException for 2-arg create", threw2)
        }
    }

    @Test
    fun factoriesCreateAllThreeViewModelsSharingContainerDependencies() {
        val container = createTestContainer()

        val homeVm = AppViewModelFactories.home(container).create(HomeViewModel::class.java)
        val initialCar = Car(id = "car-42", make = "Tesla", model = "Model 3", year = 2024, isEv = true)
        val detailsVm = AppViewModelFactories.vehicleDetails(container, "car-42", initialCar)
            .create(VehicleDetailsViewModel::class.java, CreationExtras.Empty)
        val addCarVm = AppViewModelFactories.addCar(container).create(AddCarViewModel::class.java)

        assertSame(container.preferencesManager, homeVm.preferencesManager)
        assertSame(container.googleDriveService, homeVm.googleDriveService)
        assertSame(container.odometerSyncService, homeVm.odometerSyncService)
        assertSame(container.vehicleImageRepository, homeVm.vehicleImageRepository)
        assertSame(container.geminiAiService, homeVm.geminiAiService)
        assertSame(container.odometerLogDao, homeVm.odometerLogDao)
        assertSame(container.utilityBillDao, homeVm.utilityBillDao)
        assertSame(container.chargingSessionDao, homeVm.chargingSessionDao)

        assertEquals("car-42", detailsVm.carId)
        assertEquals(initialCar, detailsVm.car.value)
        assertSame(container.odometerSyncService, detailsVm.odometerSyncService)
        assertSame(container.vehicleImageRepository, detailsVm.vehicleImageRepository)
        assertSame(container.geminiAiService, detailsVm.geminiAiService)

        assertNotNull(addCarVm)
    }

    @Test
    fun homeViewModelUsesInjectedUriContentReaderForChargerCsvImport() = runBlocking {
        val csvContent = "Date,Energy (kWh)\n2026-11-01,24.5\n"
        val reader = object : UriContentReader {
            override fun resolveFileName(uri: Uri): String = "wall_connector.csv"
            override fun resolveMimeType(uri: Uri, fileName: String): String = "text/csv"
            override fun readBytes(uri: Uri): ByteArray = csvContent.toByteArray(Charsets.UTF_8)
        }
        val container = createTestContainer(uriContentReader = reader)
        val homeVm = AppViewModelFactories.home(container).create(HomeViewModel::class.java)

        homeVm.parseChargerUsageUri(testUri("content://com.example/wall_connector.csv"))
        val draft = withTimeout(5_000) {
            homeVm.pendingChargerImport.first { it != null }
        }

        assertNotNull(draft)
        assertEquals("csv", draft!!.source)
        assertEquals("wall_connector.csv", draft.fileName)
        assertEquals(1, draft.sessions.size)
        assertEquals(24.5, draft.sessions.first().kwh, 0.001)
    }

    private fun createTestContainer(
        uriContentReader: UriContentReader = FakeUriContentReader()
    ): AppContainer {
        val prefsFile = File(tempFolder.newFolder("prefs_${System.nanoTime()}"), "test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) { prefsFile }
        val prefs = PreferencesManager(dataStore)
        val carDao = FakeCarDao()
        val imagesDir = tempFolder.newFolder("images_${System.nanoTime()}")
        return AppContainer.forTest(
            preferencesManager = prefs,
            carDao = carDao,
            maintenanceRecordDao = FakeMaintenanceRecordDao(),
            tireDao = FakeTireDao(),
            gloveboxDao = FakeGloveboxDao(),
            odometerLogDao = FakeOdometerLogDao(),
            utilityBillDao = FakeUtilityBillDao(),
            chargingSessionDao = FakeChargingSessionDao(),
            uriContentReader = uriContentReader,
            syncSchedulerController = FakeSyncSchedulerController(),
            googleDriveService = GoogleDriveService(initialUser = null),
            vehicleImageRepository = VehicleImageRepository(
                imagesDir = imagesDir,
                uriContentReader = uriContentReader,
                carDao = carDao
            )
        )
    }

    private class UnrelatedViewModel : ViewModel()

    private class FakeUriContentReader : UriContentReader {
        override fun resolveFileName(uri: Uri): String? = null
        override fun resolveMimeType(uri: Uri, fileName: String): String = "application/octet-stream"
        override fun readBytes(uri: Uri): ByteArray? = null
    }

    private class FakeSyncSchedulerController : SyncSchedulerController {
        override fun scheduleDailyOdometerPoll() = Unit
        override fun cancelDailyOdometerPoll() = Unit
        override fun scheduleChargerSync(targetHour: Int, targetMinute: Int) = Unit
        override fun cancelChargerSync() = Unit
        override fun scheduleWorkChargingSync(
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int
        ) = Unit
        override fun cancelWorkChargingSync() = Unit
    }

    private class FakeCarDao : CarDao {
        override fun getAllCars(): Flow<List<CarEntity>> = flowOf(emptyList())
        override suspend fun getAllCarsList(): List<CarEntity> = emptyList()
        override suspend fun getCarById(carId: String): CarEntity? = null
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
        override suspend fun getCarCount(): Int = 0
    }

    private class FakeMaintenanceRecordDao : MaintenanceRecordDao {
        override fun getAllRecords(): Flow<List<MaintenanceRecordEntity>> = flowOf(emptyList())
        override suspend fun getAllRecordsList(): List<MaintenanceRecordEntity> = emptyList()
        override fun getRecordsForCar(carId: String): Flow<List<MaintenanceRecordEntity>> = flowOf(emptyList())
        override suspend fun getRecordsListForCar(carId: String): List<MaintenanceRecordEntity> = emptyList()
        override suspend fun getRecordByDriveFileId(driveFileId: String): MaintenanceRecordEntity? = null
        override suspend fun getRecordById(id: String): MaintenanceRecordEntity? = null
        override suspend fun insertRecord(record: MaintenanceRecordEntity) = Unit
        override suspend fun insertRecords(records: List<MaintenanceRecordEntity>) = Unit
        override suspend fun updateRecord(record: MaintenanceRecordEntity) = Unit
        override suspend fun deleteRecord(record: MaintenanceRecordEntity) = Unit
        override suspend fun deleteRecordById(id: String) = Unit
        override suspend fun deleteRecordsForCar(carId: String) = Unit
    }

    private class FakeTireDao : TireDao {
        override fun getActiveTireForCar(carId: String): Flow<TireEntity?> = flowOf(null)
        override fun getAllTiresForCar(carId: String): Flow<List<TireEntity>> = flowOf(emptyList())
        override fun getAllTires(): Flow<List<TireEntity>> = flowOf(emptyList())
        override suspend fun insertTire(tire: TireEntity) = Unit
        override suspend fun updateTire(tire: TireEntity) = Unit
        override suspend fun deleteTire(tire: TireEntity) = Unit
    }

    private class FakeGloveboxDao : GloveboxDao {
        override fun getGloveboxForCar(carId: String): Flow<GloveboxEntity?> = flowOf(null)
        override suspend fun getGloveboxDirect(carId: String): GloveboxEntity? = null
        override suspend fun upsertGlovebox(glovebox: GloveboxEntity) = Unit
    }

    private class FakeOdometerLogDao : OdometerLogDao {
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

    private class FakeUtilityBillDao : UtilityBillDao {
        override fun getAllBills(): Flow<List<UtilityBillEntity>> = flowOf(emptyList())
        override suspend fun getAllBillsList(): List<UtilityBillEntity> = emptyList()
        override suspend fun findByPeriod(startDateMs: Long, endDateMs: Long): UtilityBillEntity? = null
        override suspend fun insertBill(bill: UtilityBillEntity) = Unit
        override suspend fun deleteBillById(id: String) = Unit
    }

    private class FakeChargingSessionDao : ChargingSessionDao {
        override fun getAllSessions(): Flow<List<ChargingSessionEntity>> = flowOf(emptyList())
        override suspend fun getAllSessionsList(): List<ChargingSessionEntity> = emptyList()
        override suspend fun getAllFingerprints(): List<String> = emptyList()
        override suspend fun insertSessions(sessions: List<ChargingSessionEntity>) = Unit
        override suspend fun insertSession(session: ChargingSessionEntity) = Unit
        override suspend fun deleteSessionById(id: String) = Unit
        override suspend fun deleteSessionsByIds(ids: List<String>) = Unit
        override suspend fun deleteSessionsByBatchId(batchId: String) = Unit
    }
}
