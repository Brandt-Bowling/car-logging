package com.example.car_logger

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.net.testUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.car_logger.data.local.AndroidUriContentReader
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.di.AppContainer
import com.example.car_logger.services.AndroidSyncSchedulerController
import com.example.car_logger.services.AutoDevService
import com.example.car_logger.services.ChargerSyncScheduler
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SyncSchedulerController
import com.example.car_logger.services.WorkChargingSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiFoundationAndHttpPoolTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- UriContentReader MIME & Contract Tests ---

    @Test
    fun inferMimeTypeFromFileNameMapsSupportedExtensions() {
        assertEquals("application/pdf", AndroidUriContentReader.inferMimeTypeFromFileName("utility_bill.pdf"))
        assertEquals("text/csv", AndroidUriContentReader.inferMimeTypeFromFileName("wall_connector.csv"))
        assertEquals("image/png", AndroidUriContentReader.inferMimeTypeFromFileName("receipt.png"))
        assertEquals("image/webp", AndroidUriContentReader.inferMimeTypeFromFileName("vehicle.webp"))
        assertEquals("image/jpeg", AndroidUriContentReader.inferMimeTypeFromFileName("photo.jpg"))
        assertEquals("image/jpeg", AndroidUriContentReader.inferMimeTypeFromFileName("photo.jpeg"))
    }

    @Test
    fun inferMimeTypeFromFileNameHandlesCaseWhitespaceQueryAndFragments() {
        assertEquals("application/pdf", AndroidUriContentReader.inferMimeTypeFromFileName("  BILL_2026.PDF  "))
        assertEquals("image/jpeg", AndroidUriContentReader.inferMimeTypeFromFileName("CarPhoto.JpEg"))
        assertEquals("application/pdf", AndroidUriContentReader.inferMimeTypeFromFileName("folder/receipt.pdf?alt=media"))
        assertEquals("image/png", AndroidUriContentReader.inferMimeTypeFromFileName("capture.png#page=1"))
        assertEquals("text/csv", AndroidUriContentReader.inferMimeTypeFromFileName("  export.CSV?token=abc#top  "))
    }

    @Test
    fun inferMimeTypeFromFileNameFallsBackToOctetStreamForUnknownOrMissingExtensions() {
        assertEquals(AndroidUriContentReader.DEFAULT_MIME_TYPE, AndroidUriContentReader.inferMimeTypeFromFileName(""))
        assertEquals(AndroidUriContentReader.DEFAULT_MIME_TYPE, AndroidUriContentReader.inferMimeTypeFromFileName("   "))
        assertEquals(AndroidUriContentReader.DEFAULT_MIME_TYPE, AndroidUriContentReader.inferMimeTypeFromFileName("receipt"))
        assertEquals(AndroidUriContentReader.DEFAULT_MIME_TYPE, AndroidUriContentReader.inferMimeTypeFromFileName("notes.txt"))
        assertEquals(AndroidUriContentReader.DEFAULT_MIME_TYPE, AndroidUriContentReader.inferMimeTypeFromFileName("bill.pdf.bak"))
    }

    @Test
    fun resolveMimeTypePrefersNonGenericReportedMimeOverExtension() {
        assertEquals("image/heic", AndroidUriContentReader.resolveMimeType("image/heic", "photo.jpg"))
        assertEquals("application/pdf", AndroidUriContentReader.resolveMimeType("  application/pdf  ", "unknown"))
    }

    @Test
    fun resolveMimeTypeFallsBackToExtensionWhenReportedMimeIsNullBlankOrOctetStream() {
        assertEquals("application/pdf", AndroidUriContentReader.resolveMimeType(null, "bill.pdf"))
        assertEquals("text/csv", AndroidUriContentReader.resolveMimeType("", "charges.csv"))
        assertEquals("image/png", AndroidUriContentReader.resolveMimeType("   ", "screen.png"))
        assertEquals(
            "image/webp",
            AndroidUriContentReader.resolveMimeType("application/octet-stream", "car.webp")
        )
        assertEquals(
            "image/jpeg",
            AndroidUriContentReader.resolveMimeType("APPLICATION/OCTET-STREAM", "car.jpeg")
        )
    }

    @Test
    fun uriContentReaderFakeWorksWithTestUriOnPureJvm() {
        val uri = testUri("content://com.example.car_logger/documents/jan_bill.pdf")
        val payload = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        val fakeReader = object : UriContentReader {
            override fun resolveFileName(uri: Uri): String? = uri.lastPathSegment
            override fun resolveMimeType(uri: Uri, fileName: String): String =
                AndroidUriContentReader.inferMimeTypeFromFileName(fileName)
            override fun readBytes(uri: Uri): ByteArray = payload
        }

        val fileName = fakeReader.resolveFileName(uri)
        assertEquals("jan_bill.pdf", fileName)
        assertEquals("application/pdf", fakeReader.resolveMimeType(uri, fileName!!))
        assertArrayEquals(payload, fakeReader.readBytes(uri))
    }

    // --- SyncSchedulerController Contract & Delegation Tests ---

    @Test
    fun syncSchedulerControllerDefaultParametersMatchSchedulerConstants() {
        val events = mutableListOf<String>()
        val fake = RecordingSyncSchedulerController(events)

        fake.scheduleDailyOdometerPoll()
        fake.scheduleChargerSync()
        fake.scheduleWorkChargingSync()

        assertEquals(8, ChargerSyncScheduler.DEFAULT_HOUR)
        assertEquals(0, ChargerSyncScheduler.DEFAULT_MINUTE)
        assertEquals(12, WorkChargingSyncScheduler.DEFAULT_START_HOUR)
        assertEquals(0, WorkChargingSyncScheduler.DEFAULT_START_MINUTE)
        assertEquals(17, WorkChargingSyncScheduler.DEFAULT_END_HOUR)
        assertEquals(0, WorkChargingSyncScheduler.DEFAULT_END_MINUTE)
        assertEquals(
            listOf(
                "scheduleDaily",
                "scheduleCharger(8:0)",
                "scheduleWork(12:0-17:0)"
            ),
            events
        )
    }

    @Test
    fun androidSyncSchedulerControllerDelegatesAllSixOperationsWithAppContext() {
        val fakeContext: Context = ContextWrapper(null)
        val calls = mutableListOf<Pair<Context, String>>()

        val controller = AndroidSyncSchedulerController(
            appContext = fakeContext,
            onScheduleDailyPoll = { ctx -> calls += ctx to "scheduleDaily" },
            onCancelDailyPoll = { ctx -> calls += ctx to "cancelDaily" },
            onScheduleChargerSync = { ctx, h, m -> calls += ctx to "scheduleCharger($h:$m)" },
            onCancelChargerSync = { ctx -> calls += ctx to "cancelCharger" },
            onScheduleWorkChargingSync = { ctx, sh, sm, eh, em ->
                calls += ctx to "scheduleWork($sh:$sm-$eh:$em)"
            },
            onCancelWorkChargingSync = { ctx -> calls += ctx to "cancelWork" }
        )

        controller.scheduleDailyOdometerPoll()
        controller.cancelDailyOdometerPoll()
        controller.scheduleChargerSync(targetHour = 6, targetMinute = 30)
        controller.cancelChargerSync()
        controller.scheduleWorkChargingSync(startHour = 9, startMinute = 15, endHour = 16, endMinute = 45)
        controller.cancelWorkChargingSync()

        assertEquals(6, calls.size)
        calls.forEach { (ctx, _) -> assertSame(fakeContext, ctx) }
        assertEquals(
            listOf(
                "scheduleDaily",
                "cancelDaily",
                "scheduleCharger(6:30)",
                "cancelCharger",
                "scheduleWork(9:15-16:45)",
                "cancelWork"
            ),
            calls.map { it.second }
        )
    }

    @Test
    fun androidSyncSchedulerControllerImplementsSyncSchedulerController() {
        assertTrue(SyncSchedulerController::class.java.isAssignableFrom(AndroidSyncSchedulerController::class.java))
    }

    // --- AppContainer Lazy Singletons & Shared OkHttpClient Pool Tests ---

    @Test
    fun appContainerIsLazyAndExposesSingletonReferencesOnPureJvm() {
        val container = AppContainer(ContextWrapper(null))

        assertSame(AppContainer.defaultHttpClient, container.httpClient)
        assertSame(container.httpClient, container.httpClient)
        assertSame(container.uriContentReader, container.uriContentReader)
        assertSame(container.syncSchedulerController, container.syncSchedulerController)
    }

    @Test
    fun defaultHttpClientConfiguresExpectedBaseTimeouts() {
        val base = AppContainer.defaultHttpClient
        assertEquals(20_000, base.connectTimeoutMillis)
        assertEquals(60_000, base.readTimeoutMillis)
        assertEquals(60_000, base.writeTimeoutMillis)
    }

    @Test
    fun newSharedHttpClientSharesPoolAndDispatcherWhileApplyingCustomTimeouts() {
        val base = AppContainer.defaultHttpClient
        val derived = AppContainer.newSharedHttpClient(
            connectTimeoutSeconds = 15L,
            readTimeoutSeconds = 15L,
            writeTimeoutSeconds = 45L
        )

        assertSame(base.connectionPool, derived.connectionPool)
        assertSame(base.dispatcher, derived.dispatcher)
        assertEquals(15_000, derived.connectTimeoutMillis)
        assertEquals(15_000, derived.readTimeoutMillis)
        assertEquals(45_000, derived.writeTimeoutMillis)
    }

    @Test
    fun servicesShareDefaultHttpPoolDispatcherAndPreservePerServiceTimeouts() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefsFile = File(tempFolder.newFolder("prefs_pool"), "test.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { prefsFile }
            val prefs = PreferencesManager(dataStore)
            val carDao = NoOpCarDao()

            val autoDev = AutoDevService(preferencesManager = prefs)
            val odometerSync = OdometerSyncService(preferencesManager = prefs, carDao = carDao)
            val gemini = GeminiAiService()
            val base = AppContainer.defaultHttpClient

            assertSame(base.connectionPool, autoDev.client.connectionPool)
            assertSame(base.dispatcher, autoDev.client.dispatcher)
            assertEquals(15_000, autoDev.client.connectTimeoutMillis)
            assertEquals(15_000, autoDev.client.readTimeoutMillis)

            assertSame(base, odometerSync.client)
            assertSame(base.connectionPool, odometerSync.client.connectionPool)
            assertSame(base.dispatcher, odometerSync.client.dispatcher)
            assertEquals(20_000, odometerSync.client.connectTimeoutMillis)
            assertEquals(60_000, odometerSync.client.readTimeoutMillis)

            assertSame(base.connectionPool, gemini.client.connectionPool)
            assertSame(base.dispatcher, gemini.client.dispatcher)
            assertEquals(30_000, gemini.client.connectTimeoutMillis)
            assertEquals(60_000, gemini.client.readTimeoutMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun servicesPreserveCustomInjectedHttpClientVerbatim() {
        val customClient = AppContainer.newSharedHttpClient(connectTimeoutSeconds = 5L, readTimeoutSeconds = 7L)
        val gemini = GeminiAiService(client = customClient)

        assertSame(customClient, gemini.client)
        assertEquals(5_000, gemini.client.connectTimeoutMillis)
        assertEquals(7_000, gemini.client.readTimeoutMillis)
    }

    private class RecordingSyncSchedulerController(
        private val events: MutableList<String>
    ) : SyncSchedulerController {
        override fun scheduleDailyOdometerPoll() {
            events += "scheduleDaily"
        }

        override fun cancelDailyOdometerPoll() {
            events += "cancelDaily"
        }

        override fun scheduleChargerSync(targetHour: Int, targetMinute: Int) {
            events += "scheduleCharger($targetHour:$targetMinute)"
        }

        override fun cancelChargerSync() {
            events += "cancelCharger"
        }

        override fun scheduleWorkChargingSync(
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int
        ) {
            events += "scheduleWork($startHour:$startMinute-$endHour:$endMinute)"
        }

        override fun cancelWorkChargingSync() {
            events += "cancelWork"
        }
    }

    private class NoOpCarDao : CarDao {
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
}
