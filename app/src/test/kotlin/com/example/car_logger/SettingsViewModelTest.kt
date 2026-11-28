package com.example.car_logger

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SyncSchedulerController
import com.example.car_logger.services.WallConnectorType
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsDialogState
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsUiEvent
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsViewModel
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
        runBlocking {
            children.forEach { it.join() }
        }
        trackedViewModels.clear()
        testScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        scheduler: RecordingScheduler = RecordingScheduler(),
        cars: List<CarEntity> = listOf(
            CarEntity(id = "ev-tesla", make = "Tesla", model = "Model 3", year = 2024, isEv = true),
            CarEntity(id = "ev-gm", make = "Chevrolet", model = "Blazer EV", year = 2025, isEv = true)
        )
    ): Pair<SettingsViewModel, PreferencesManager> {
        val prefsFile = File(tempFolder.newFolder("settings_vm_${System.nanoTime()}"), "prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) { prefsFile }
        val prefs = PreferencesManager(dataStore)
        val carDao = InMemoryCarDao(cars)
        val odometerSyncService = OdometerSyncService(preferencesManager = prefs, carDao = carDao)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                carDao = carDao,
                preferencesManager = prefs,
                googleDriveService = GoogleDriveService(initialUser = null),
                odometerSyncService = odometerSyncService,
                syncSchedulerController = scheduler
            ) as T
        }
        val vm = ViewModelProvider(viewModelStore, factory)[SettingsViewModel::class.java]
        trackedViewModels.add(vm)
        return vm to prefs
    }

    @Test
    fun `garage name and api keys dialog save updates uiState and clears dialog`() = runBlocking {
        val (vm, _) = createViewModel()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
        try {
            vm.openGarageNameDialog()
            withTimeout(5_000) {
                vm.uiState.first { it.dialog is SettingsDialogState.GarageName }
            }
            vm.saveGarageName("NorthSide Garage")

            val updated = withTimeout(5_000) {
                vm.uiState.first {
                    it.generalBackup.garageName == "NorthSide Garage" && it.dialog == SettingsDialogState.None
                }
            }
            assertEquals("NorthSide Garage", updated.generalBackup.garageName)

            vm.openGeminiKeyDialog()
            withTimeout(5_000) {
                vm.uiState.first { it.dialog is SettingsDialogState.GeminiKey }
            }
            vm.saveGeminiKey("ai-key-123")

            val withKey = withTimeout(5_000) {
                vm.uiState.first { it.generalBackup.geminiKey == "ai-key-123" && it.dialog == SettingsDialogState.None }
            }
            assertEquals("ai-key-123", withKey.generalBackup.geminiKey)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `sync policy cooldown and schedulers delegate cleanly without Context`() = runBlocking {
        val scheduler = RecordingScheduler()
        val (vm, _) = createViewModel(scheduler = scheduler)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
        try {
            vm.setWakePolicy(WakeProtectionPolicy.MANUAL_ONLY)
            vm.setCooldownHours(72)
            withTimeout(5_000) {
                vm.uiState.first {
                    it.vehicleSync.wakePolicy == WakeProtectionPolicy.MANUAL_ONLY &&
                        it.vehicleSync.cooldownHours == 72
                }
            }

            vm.setDailyPollEnabled(false)
            withTimeout(5_000) {
                vm.uiState.first { !it.vehicleSync.dailyPollEnabled }
                while (!scheduler.events.contains("cancelDaily")) kotlinx.coroutines.delay(10)
            }
            vm.setDailyPollEnabled(true)
            withTimeout(5_000) {
                vm.uiState.first { it.vehicleSync.dailyPollEnabled }
                while (!scheduler.events.contains("scheduleDaily")) kotlinx.coroutines.delay(10)
            }
            assertEquals(listOf("cancelDaily", "scheduleDaily"), scheduler.events)

            vm.openChargerSyncTimePicker()
            withTimeout(5_000) { vm.uiState.first { it.dialog is SettingsDialogState.ChargerSyncTimePicker } }
            vm.saveChargerSyncTime(6, 45)
            val afterCharger = withTimeout(5_000) {
                val s = vm.uiState.first { it.homeCharging.chargerSyncHour == 6 && it.homeCharging.chargerSyncMinute == 45 }
                while (!scheduler.events.contains("scheduleCharger:6:45")) kotlinx.coroutines.delay(10)
                s
            }
            assertEquals(45, afterCharger.homeCharging.chargerSyncMinute)
            assertTrue(scheduler.events.contains("scheduleCharger:6:45"))

            vm.setChargerSyncEnabled(false)
            withTimeout(5_000) {
                vm.uiState.first { !it.homeCharging.chargerSyncEnabled }
                while (!scheduler.events.contains("cancelCharger")) kotlinx.coroutines.delay(10)
            }
            assertTrue(scheduler.events.contains("cancelCharger"))

            vm.toggleWorkChargingWeekdaysOnly()
            val afterWeekdays = withTimeout(5_000) { vm.uiState.first { !it.workCharging.workChargingWeekdaysOnly } }
            assertFalse(afterWeekdays.workCharging.workChargingWeekdaysOnly)

            vm.saveWorkChargingWindow(isStart = true, hour = 10, minute = 15)
            val afterWindow = withTimeout(5_000) {
                val s = vm.uiState.first { it.workCharging.workWindowStartHour == 10 && it.workCharging.workWindowStartMinute == 15 }
                while (scheduler.events.none { it.startsWith("scheduleWork:10:15:") }) kotlinx.coroutines.delay(10)
                s
            }
            assertEquals(15, afterWindow.workCharging.workWindowStartMinute)
            assertTrue(scheduler.events.any { it.startsWith("scheduleWork:10:15:") })
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `requestGoogleSignIn wallConnectorMapping and externalUrl emit events`() = runBlocking {
        val (vm, prefs) = createViewModel()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
        val events = mutableListOf<SettingsUiEvent>()
        val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { events.add(it) } }
        try {
            vm.requestGoogleSignIn()
            vm.openExternalUrl("https://developer.tesla.com/dashboard")
            vm.assignWallConnector(
                WallConnectorType.GEN_3,
                Car(
                    id = "ev-gm",
                    make = "Chevrolet",
                    model = "Blazer EV",
                    year = 2025,
                    isEv = true
                )
            )
            withTimeout(5_000) {
                while (events.size < 3 || prefs.wallConnectorGen3CarIdFlow.first() != "ev-gm") {
                    kotlinx.coroutines.delay(20)
                }
            }
            assertTrue(events.contains(SettingsUiEvent.LaunchGoogleSignIn))
            assertTrue(events.contains(SettingsUiEvent.OpenExternalUrl("https://developer.tesla.com/dashboard")))
            assertTrue(events.any { it is SettingsUiEvent.ShowToast && it.message.contains("Blazer EV") })
        } finally {
            collector.cancel()
            eventCollector.cancel()
        }
    }

    private class RecordingScheduler : SyncSchedulerController {
        val events = mutableListOf<String>()
        override fun scheduleDailyOdometerPoll() {
            events += "scheduleDaily"
        }
        override fun cancelDailyOdometerPoll() {
            events += "cancelDaily"
        }
        override fun scheduleChargerSync(targetHour: Int, targetMinute: Int) {
            events += "scheduleCharger:$targetHour:$targetMinute"
        }
        override fun cancelChargerSync() {
            events += "cancelCharger"
        }
        override fun scheduleWorkChargingSync(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
            events += "scheduleWork:$startHour:$startMinute:$endHour:$endMinute"
        }
        override fun cancelWorkChargingSync() {
            events += "cancelWork"
        }
    }

    private class InMemoryCarDao(initial: List<CarEntity>) : CarDao {
        private val flow = MutableStateFlow(initial)
        override fun getAllCars(): Flow<List<CarEntity>> = flow
        override suspend fun getAllCarsList(): List<CarEntity> = flow.value
        override suspend fun getCarById(carId: String): CarEntity? = flow.value.find { it.id == carId }
        override fun observeCarById(carId: String): Flow<CarEntity?> = MutableStateFlow(flow.value.firstOrNull())
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
        override suspend fun getCarCount(): Int = flow.value.size
    }
}
