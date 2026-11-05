package com.example.car_logger

import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerSyncServiceTest {

    @Test
    fun `isCooldownActive enforces 72h window accurately`() {
        val now = 1_700_000_000_000L
        val fourHoursAgo = now - (4 * 3_600_000L)
        val eightyHoursAgo = now - (80 * 3_600_000L)

        assertTrue(OdometerSyncService.isCooldownActive(fourHoursAgo, 72, now))
        assertEquals(68, OdometerSyncService.remainingCooldownHours(fourHoursAgo, 72, now))

        assertFalse(OdometerSyncService.isCooldownActive(eightyHoursAgo, 72, now))
        assertEquals(0, OdometerSyncService.remainingCooldownHours(eightyHoursAgo, 72, now))
        assertFalse(OdometerSyncService.isCooldownActive(null, 72, now))
    }

    @Test
    fun `parseSmartcarOdometerMiles parses v2 distance and rounds`() {
        val payload = """{"distance": 14820.42}"""
        assertEquals(14820, OdometerSyncService.parseSmartcarOdometerMiles(payload))

        val payloadRoundUp = """{"distance": 14820.68}"""
        assertEquals(14821, OdometerSyncService.parseSmartcarOdometerMiles(payloadRoundUp))
    }

    @Test
    fun `parseSmartcarOdometerMiles parses v3 signal JSON API and converts km to miles`() {
        val v3KmPayload = """
            {
              "data": {
                "id": "odometer-traveleddistance",
                "type": "signal",
                "attributes": {
                  "body": {
                    "value": 16093.44,
                    "unit": "km"
                  }
                }
              }
            }
        """.trimIndent()
        assertEquals(10000, OdometerSyncService.parseSmartcarOdometerMiles(v3KmPayload))
    }

    @Test
    fun `parseSmartcarV3Connection extracts vehicleId and userId`() {
        val v3Connections = """
            {
              "data": [
                {
                  "id": "conn_1",
                  "attributes": {
                    "vehicleId": "veh_abc123",
                    "userId": "usr_xyz789"
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = OdometerSyncService.parseSmartcarV3Connection(v3Connections)
        assertEquals("veh_abc123", parsed?.first)
        assertEquals("usr_xyz789", parsed?.second)
    }

    @Test
    fun `parseTeslaVehicleListState detects asleep vs online by VIN`() {
        val payload = """
            {
              "response": [
                {
                  "id": 12345,
                  "vin": "5YJ3E1EA1PF000001",
                  "state": "asleep"
                }
              ],
              "count": 1
            }
        """.trimIndent()

        val result = OdometerSyncService.parseTeslaVehicleListState(payload, "5YJ3E1EA1PF000001")
        assertEquals("5YJ3E1EA1PF000001", result?.first)
        assertEquals("asleep", result?.second)
    }

    @Test
    fun `parseTeslaOdometerMiles extracts nested vehicle_state odometer`() {
        val payload = """
            {
              "response": {
                "vehicle_state": {
                  "odometer": 28410.35,
                  "car_version": "2026.12.1"
                }
              }
            }
        """.trimIndent()

        assertEquals(28410, OdometerSyncService.parseTeslaOdometerMiles(payload))
    }

    @Test
    fun `resolveProviderForCar maps Blazer EV to SMARTCAR and Model 3 to TESLA_FLEET`() {
        val blazerEv = Car(
            id = "1",
            make = "Chevrolet",
            model = "Blazer EV",
            year = 2024,
            isEv = true
        )
        val model3 = Car(
            id = "2",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            isEv = true
        )
        val otherCar = Car(
            id = "3",
            make = "Mazda",
            model = "Miata",
            year = 2020
        )

        assertEquals(OdometerSyncService.PROVIDER_SMARTCAR, OdometerSyncService.resolveProviderForCar(blazerEv))
        assertEquals(OdometerSyncService.PROVIDER_TESLA, OdometerSyncService.resolveProviderForCar(model3))
        assertNull(OdometerSyncService.resolveProviderForCar(otherCar))
    }

    @Test
    fun `parseSmartcarV3Connection supports JSON API relationships and empty list`() {
        val emptyPayload = """{"data":[],"meta":{"totalCount":0}}"""
        assertNull(OdometerSyncService.parseSmartcarV3Connection(emptyPayload))

        val relsPayload = """
            {
              "data": [
                {
                  "id": "conn_1",
                  "type": "connection",
                  "relationships": {
                    "user": {
                      "data": {
                        "id": "usr_rel_111",
                        "type": "user"
                      }
                    },
                    "vehicle": {
                      "data": {
                        "id": "veh_rel_222",
                        "type": "vehicle"
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = OdometerSyncService.parseSmartcarV3Connection(relsPayload)
        assertEquals("veh_rel_222", parsed?.first)
        assertEquals("usr_rel_111", parsed?.second)
    }

    @Test
    fun `parseSmartcarV3ApplicationId extracts UUID from applications payload`() {
        val payload = """
            {
              "data": [
                {
                  "id": "c6e28e0b-2199-47e6-b367-51cb96965b30",
                  "type": "application",
                  "attributes": {
                    "name": "Garage Journal",
                    "clientId": "client_01M5REPWJCMZEPD8QKP13FJN4H"
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals(
            "c6e28e0b-2199-47e6-b367-51cb96965b30",
            OdometerSyncService.parseSmartcarV3ApplicationId(payload)
        )
    }

    @Test
    fun `formatSyncResultMessage produces clear confirmation and error messages`() {
        val blazerEv = Car(
            id = "1",
            make = "Chevrolet",
            model = "Blazer EV",
            year = 2024,
            isEv = true
        )

        val updatedMsg = OdometerSyncService.formatSyncResultMessage(
            blazerEv,
            OdometerSyncResult.Updated("1", 14820, OdometerSyncService.PROVIDER_SMARTCAR)
        )
        assertEquals("Synced 2024 Chevrolet Blazer EV: 14,820 mi", updatedMsg)

        val errMsg = OdometerSyncService.formatSyncResultMessage(
            blazerEv,
            OdometerSyncResult.Error("1", "No vehicle linked in Smartcar — tap Connect in Settings")
        )
        assertEquals(
            "Sync failed for 2024 Chevrolet Blazer EV: No vehicle linked in Smartcar — tap Connect in Settings",
            errMsg
        )
    }

    @Test
    fun `parseSmartcarErrorDetail detects v3 signal permission error`() {
        val permErrorPayload = """
            {
              "data": {
                "id": "odometer-traveleddistance",
                "type": "signal",
                "attributes": {
                  "code": "odometer-traveleddistance",
                  "name": "TraveledDistance",
                  "group": "Odometer",
                  "status": {
                    "value": "ERROR",
                    "error": {
                      "type": "PERMISSION",
                      "code": "UNKNOWN",
                      "title": "Unknown",
                      "detail": "Your application has insufficient permissions to access the requested resource. Please prompt the user to re-authenticate using Smartcar Connect.",
                      "resolution": {
                        "type": "REAUTHENTICATE"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        assertEquals(
            "Missing odometer permission — tap Reconnect in Settings",
            OdometerSyncService.parseSmartcarErrorDetail(permErrorPayload)
        )
    }

    @Test
    fun `buildSmartcarAuthUrl constructs clean url without scope parameter`() {
        val testAppId = "c6e28e0b-2199-47e6-b367-51cb96965b30"
        val url = OdometerSyncService.buildSmartcarAuthUrl(testAppId)

        assertTrue(url.startsWith("https://connect.smartcar.com/oauth/authorize?"))
        assertTrue(url.contains("application_id=$testAppId"))
        assertTrue(url.contains("response_type=none"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fcar-logger-track.web.app%2Foauth%2Fsmartcar"))
        assertTrue(url.contains("mode=live"))
        assertFalse("Url should not contain scope parameter because v3 permissions are configured in Smartcar Dashboard", url.contains("scope="))
    }

    @Test
    fun `buildSmartcarAuthUrl falls back from client_id prefix to default application id`() {
        val clientPrefixedId = "client_01M5REPWJCMZEPD8QKP13FJN4H"
        val url = OdometerSyncService.buildSmartcarAuthUrl(clientPrefixedId)

        assertTrue(url.contains("application_id=c6e28e0b-2199-47e6-b367-51cb96965b30"))
        assertFalse(url.contains("client_01M5REPWJCMZEPD8QKP13FJN4H"))
        assertFalse(url.contains("scope="))
    }

    @Test
    fun `isCooldownActive respects bypassCooldown parameter concept`() {
        val now = 1_700_000_000_000L
        val oneHourAgo = now - 3_600_000L
        val inCooldown = OdometerSyncService.isCooldownActive(oneHourAgo, 72, now)
        assertTrue(inCooldown)
        // With bypassCooldown, callers skip isCooldownActive check
    }

    @Test
    fun `formatDailyPollSummary creates descriptive message for updated and skipped results`() {
        val car1 = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, odometer = 15000)
        val car2 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, odometer = 28000)
        val results = listOf(
            OdometerSyncResult.Updated(car1.id, 15000, OdometerSyncService.PROVIDER_SMARTCAR),
            OdometerSyncResult.Updated(car2.id, 28000, OdometerSyncService.PROVIDER_TESLA)
        )
        val summary = OdometerSyncService.formatDailyPollSummary(listOf(car1, car2), results)
        assertEquals("Synced 2 vehicles", summary)
    }

    @Test
    fun `formatDailyPollSummary handles not configured and empty vehicles properly`() {
        val car1 = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, odometer = 15000)
        val car2 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, odometer = 28000)

        val unconfiguredResults = listOf(
            OdometerSyncResult.NotConfigured(car1.id),
            OdometerSyncResult.NotConfigured(car2.id)
        )
        val summaryUnconfigured = OdometerSyncService.formatDailyPollSummary(listOf(car1, car2), unconfiguredResults)
        assertEquals("Blazer EV: not linked, Model 3: not linked", summaryUnconfigured)

        val emptySummary = OdometerSyncService.formatDailyPollSummary(emptyList(), emptyList())
        assertEquals("No vehicles configured", emptySummary)
    }

    @Test
    fun `shouldRecordOdometerLog records stationary zero-delta syncs on new day or after 1 hour`() {
        val zoneId = ZoneId.of("America/New_York")
        val mon11Pm = LocalDate.of(2026, 11, 2).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val mon1105Pm = LocalDate.of(2026, 11, 2).atTime(23, 5).atZone(zoneId).toInstant().toEpochMilli()
        val tue11Pm = LocalDate.of(2026, 11, 3).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()

        val latest = OdometerLogEntity(
            id = "log_1",
            carId = "car_1",
            odometer = 20000,
            timestamp = mon11Pm,
            source = OdometerSyncService.PROVIDER_TESLA
        )

        // First ever log
        assertTrue(OdometerSyncService.shouldRecordOdometerLog(null, 20000, mon11Pm, zoneId))

        // Higher odometer always recorded
        assertTrue(OdometerSyncService.shouldRecordOdometerLog(latest, 20001, mon1105Pm, zoneId))

        // Lower odometer never recorded
        assertFalse(OdometerSyncService.shouldRecordOdometerLog(latest, 19999, tue11Pm, zoneId))

        // Same odometer 5 minutes later on same day -> skip duplicate spam
        assertFalse(OdometerSyncService.shouldRecordOdometerLog(latest, 20000, mon1105Pm, zoneId))

        // Same odometer next day (stationary day) -> MUST record to anchor timeline!
        assertTrue(OdometerSyncService.shouldRecordOdometerLog(latest, 20000, tue11Pm, zoneId))
    }

    @Test
    fun `parseTeslaWakeState extracts state from wake_up response`() {
        val asleepPayload = """{"response": {"id": 123, "vin": "5YJ3E1EA1PF000001", "state": "asleep"}}"""
        val onlinePayload = """{"response": {"id": 123, "vin": "5YJ3E1EA1PF000001", "state": "online"}}"""

        assertEquals("asleep", OdometerSyncService.parseTeslaWakeState(asleepPayload))
        assertEquals("online", OdometerSyncService.parseTeslaWakeState(onlinePayload))
    }

    @Test
    fun `waitForTeslaWakeUp polls until vehicle transitions from asleep to online`() = runBlocking {
        val states = listOf("asleep", "asleep", "waking", "online", "online")
        var callCount = 0

        val becameOnline = OdometerSyncService.waitForTeslaWakeUp(
            maxAttempts = 6,
            delayMs = 0L
        ) {
            val state = states[callCount]
            callCount++
            state
        }

        assertTrue(becameOnline)
        assertEquals("Should stop polling as soon as online is returned", 4, callCount)
    }

    @Test
    fun `recordOdometerReading seeds baseline and latest log when latest log is null and previous odometer exists`() = runBlocking {
        val carDao = FakeCarDao()
        val odometerLogDao = FakeOdometerLogDao()

        val initialCar = CarEntity(
            id = "tesla_1",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            odometer = 24000,
            lastOdometerSyncTime = 1700000000000L
        )
        carDao.insertCar(initialCar)

        val syncTime = 1700086400000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24150,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = syncTime
        )

        // Car odometer updated in carDao
        assertEquals(24150, carDao.getCarById("tesla_1")?.odometer)

        // 2 logs created: baseline (24000) and synced (24150)
        assertEquals(2, odometerLogDao.logs.size)
        val initialLog = odometerLogDao.logs.first()
        val syncedLog = odometerLogDao.logs.last()

        assertEquals(24000, initialLog.odometer)
        assertEquals(1700000000000L, initialLog.timestamp)
        assertEquals(OdometerSyncService.PROVIDER_TESLA, initialLog.source)

        assertEquals(24150, syncedLog.odometer)
        assertEquals(syncTime, syncedLog.timestamp)
        assertEquals(OdometerSyncService.PROVIDER_TESLA, syncedLog.source)
    }

    @Test
    fun `recordOdometerReading inserts new log when latest log exists and odometer increased`() = runBlocking {
        val carDao = FakeCarDao()
        val odometerLogDao = FakeOdometerLogDao()

        val initialCar = CarEntity(
            id = "tesla_1",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            odometer = 24000
        )
        carDao.insertCar(initialCar)
        odometerLogDao.insertLog(
            OdometerLogEntity("log1", "tesla_1", 24000, 1700000000000L, OdometerSyncService.PROVIDER_TESLA)
        )

        val syncTime = 1700086400000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24200,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = syncTime
        )

        assertEquals(24200, carDao.getCarById("tesla_1")?.odometer)
        assertEquals(2, odometerLogDao.logs.size)
        assertEquals(24200, odometerLogDao.logs.last().odometer)
    }

    @Test
    fun `recordOdometerReading skips duplicate log within same hour when odometer has not increased but records next-day anchor`() = runBlocking {
        val carDao = FakeCarDao()
        val odometerLogDao = FakeOdometerLogDao()

        val initialCar = CarEntity(
            id = "tesla_1",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            odometer = 24000
        )
        carDao.insertCar(initialCar)
        odometerLogDao.insertLog(
            OdometerLogEntity("log1", "tesla_1", 24000, 1700000000000L, OdometerSyncService.PROVIDER_TESLA)
        )

        // Same reading 5 minutes later -> skipped
        val fiveMinLater = 1700000000000L + 300_000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24000,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = fiveMinLater
        )
        assertEquals(1, odometerLogDao.logs.size)

        // Same reading 24 hours later -> recorded as stationary timeline anchor
        val nextDay = 1700086400000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24000,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = nextDay
        )
        assertEquals(2, odometerLogDao.logs.size)
    }

    private class FakeCarDao : CarDao {
        val cars = mutableMapOf<String, CarEntity>()

        override fun getAllCars(): Flow<List<CarEntity>> = emptyFlow()
        override suspend fun getAllCarsList(): List<CarEntity> = cars.values.toList()
        override suspend fun getCarById(carId: String): CarEntity? = cars[carId]
        override fun observeCarById(carId: String): Flow<CarEntity?> = emptyFlow()
        override suspend fun insertCar(car: CarEntity) { cars[car.id] = car }
        override suspend fun insertCars(cars: List<CarEntity>) { cars.forEach { this.cars[it.id] = it } }
        override suspend fun updateCar(car: CarEntity) { cars[car.id] = car }
        override suspend fun toggleArchive(carId: String) {}
        override suspend fun updateOdometer(carId: String, odometer: Int) {
            cars[carId]?.let { cars[carId] = it.copy(odometer = odometer) }
        }
        override suspend fun updateOdometerFromTelemetry(
            carId: String,
            odometer: Int,
            provider: String,
            status: String,
            syncTime: Long
        ) {
            cars[carId]?.let {
                cars[carId] = it.copy(
                    odometer = odometer,
                    telemetryProvider = provider,
                    telemetryStatus = status,
                    lastOdometerSyncTime = syncTime
                )
            }
        }
        override suspend fun updateTelemetryStatus(carId: String, provider: String, status: String) {}
        override suspend fun updateDriveFolder(carId: String, folderId: String?, folderName: String?) {}
        override suspend fun deleteCar(car: CarEntity) { cars.remove(car.id) }
        override suspend fun deleteCarById(carId: String) { cars.remove(carId) }
        override suspend fun getCarCount(): Int = cars.size
    }

    private class FakeOdometerLogDao : OdometerLogDao {
        val logs = mutableListOf<OdometerLogEntity>()

        override fun getAllLogs(): Flow<List<OdometerLogEntity>> = emptyFlow()
        override suspend fun getAllLogsList(): List<OdometerLogEntity> = logs.sortedBy { it.timestamp }
        override fun getLogsForCar(carId: String): Flow<List<OdometerLogEntity>> = emptyFlow()
        override suspend fun getLogsForCarList(carId: String): List<OdometerLogEntity> =
            logs.filter { it.carId == carId }.sortedBy { it.timestamp }
        override suspend fun getLatestLogForCar(carId: String): OdometerLogEntity? =
            logs.filter { it.carId == carId }.maxByOrNull { it.timestamp }
        override suspend fun getLogCount(): Int = logs.size
        override suspend fun insertLog(log: OdometerLogEntity) {
            logs.removeAll { it.id == log.id }
            logs.add(log)
        }
        override suspend fun insertLogs(logs: List<OdometerLogEntity>) {
            logs.forEach { insertLog(it) }
        }
        override suspend fun deleteLogsForCar(carId: String) {
            logs.removeAll { it.carId == carId }
        }
        override suspend fun deleteNonTelemetryLogs() {
            logs.removeAll { it.source in listOf("MAINTENANCE", "INITIAL") || it.id.startsWith("rec_") || it.id.startsWith("car_odo_") }
        }
    }
}
