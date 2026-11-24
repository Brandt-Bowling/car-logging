package com.example.car_logger

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.WallConnectorSyncResult
import com.example.car_logger.services.WallConnectorType
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OdometerSyncServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
            "Couldn't sync 2024 Chevrolet Blazer EV: No vehicle linked in Smartcar — tap Connect in Settings",
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
        assertEquals("Blazer EV: not connected, Model 3: not connected", summaryUnconfigured)

        val emptySummary = OdometerSyncService.formatDailyPollSummary(emptyList(), emptyList())
        assertEquals("No vehicles set up", emptySummary)
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

    @Test
    fun `parseTeslaEnergySites extracts Gen 3 and Universal Wall Connectors while ignoring vehicle entries`() {
        val productsPayload = """
            {
              "response": [
                {
                  "id": 999111,
                  "vehicle_id": 888222,
                  "vin": "5YJ3E1EA1PF000001",
                  "display_name": "Model 3",
                  "state": "asleep"
                },
                {
                  "energy_site_id": "225511001",
                  "resource_type": "wall_connector",
                  "site_name": "Garage Gen 3",
                  "id": "1457768-02-G--TWC11111",
                  "components": {
                    "wall_connectors": [
                      {
                        "din": "1457768-02-G--TWC11111",
                        "part_number": "1457768-02-G"
                      }
                    ]
                  }
                },
                {
                  "energy_site_id": "225511002",
                  "resource_type": "wall_connector",
                  "site_name": "Driveway Universal",
                  "id": "1734412-02-D--TWC22222",
                  "components": {
                    "wall_connectors": [
                      {
                        "din": "1734412-02-D--TWC22222",
                        "part_number": "1734412-02-D"
                      }
                    ]
                  }
                }
              ],
              "count": 3
            }
        """.trimIndent()

        val devices = OdometerSyncService.parseTeslaEnergySites(productsPayload)
        assertEquals(2, devices.size)

        assertEquals("225511001", devices[0].energySiteId)
        assertEquals("1457768-02-G--TWC11111", devices[0].din)
        assertEquals(WallConnectorType.GEN_3, devices[0].connectorType)

        assertEquals("225511002", devices[1].energySiteId)
        assertEquals("1734412-02-D--TWC22222", devices[1].din)
        assertEquals(WallConnectorType.UNIVERSAL, devices[1].connectorType)
    }

    @Test
    fun `resolveCarForWallConnector maps Gen 3 to Model 3 and Universal to Blazer EV and honors user overrides`() {
        val blazerEv = Car(
            id = "car_blazer",
            make = "Chevrolet",
            model = "Blazer EV",
            year = 2024,
            vin = "3GNKDCRJ5RS100002",
            isEv = true
        )
        val model3 = Car(
            id = "car_model3",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            vin = "5YJ3E1EA1PF000001",
            isEv = true
        )
        val garage = listOf(blazerEv, model3)

        // Default auto-attribution: Gen 3 -> Model 3, Universal -> Blazer EV
        assertEquals(
            "car_model3",
            OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.GEN_3,
                cars = garage
            )?.id
        )
        assertEquals(
            "car_blazer",
            OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.UNIVERSAL,
                cars = garage
            )?.id
        )

        // Explicit user overrides in PreferencesManager take precedence
        assertEquals(
            "car_blazer",
            OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.GEN_3,
                cars = garage,
                gen3OverrideCarId = "car_blazer",
                universalOverrideCarId = "car_model3"
            )?.id
        )
        assertEquals(
            "car_model3",
            OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.UNIVERSAL,
                cars = garage,
                gen3OverrideCarId = "car_blazer",
                universalOverrideCarId = "car_model3"
            )?.id
        )
    }

    @Test
    fun `parseTeslaWallConnectorChargeHistory and buildWallConnectorSessionEntities convert Wh to kWh and deduplicate`() {
        val blazerEv = Car(
            id = "car_blazer",
            make = "Chevrolet",
            model = "Blazer EV",
            year = 2024,
            isEv = true
        )
        val model3 = Car(
            id = "car_model3",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            vin = "5YJ3E1EA1PF000001",
            isEv = true
        )
        val garage = listOf(blazerEv, model3)

        val devices = listOf(
            com.example.car_logger.services.TeslaWallConnectorDevice(
                energySiteId = "site_home",
                din = "1457768-02-G--GEN3",
                partNumber = "1457768-02-G",
                siteName = "Left Bay",
                connectorType = WallConnectorType.GEN_3
            ),
            com.example.car_logger.services.TeslaWallConnectorDevice(
                energySiteId = "site_home",
                din = "1734412-02-D--UNIV",
                partNumber = "1734412-02-D",
                siteName = "Right Bay",
                connectorType = WallConnectorType.UNIVERSAL
            )
        )

        val telemetryHistoryJson = """
            {
              "response": {
                "time_series": [
                  {
                    "charge_start_time": "2026-11-14T21:15:00-08:00",
                    "charge_stop_time": "2026-11-15T01:05:00-08:00",
                    "charge_energy_imported": 34500,
                    "din": "1457768-02-G--GEN3",
                    "vin": "5YJ3E1EA1PF000001"
                  },
                  {
                    "charge_start_time": "2026-11-14T22:00:00-08:00",
                    "charge_stop_time": "2026-11-15T03:30:00-08:00",
                    "charge_energy_imported": 52800,
                    "din": "1734412-02-D--UNIV"
                  },
                  {
                    "charge_start_time": "2026-11-15T04:00:00-08:00",
                    "charge_stop_time": "2026-11-15T04:01:00-08:00",
                    "charge_energy_imported": 10,
                    "din": "1734412-02-D--UNIV"
                  }
                ]
              }
            }
        """.trimIndent()

        val rawSessions = OdometerSyncService.parseTeslaWallConnectorChargeHistory(telemetryHistoryJson, "site_home")
        assertEquals("Standby <50Wh noise should be filtered out", 2, rawSessions.size)

        val zoneId = ZoneId.of("America/Los_Angeles")
        val entities = OdometerSyncService.buildWallConnectorSessionEntities(
            rawSessions = rawSessions,
            devices = devices,
            cars = garage,
            uploadedAtMs = 1_763_200_000_000L,
            zoneId = zoneId
        )
        assertEquals(2, entities.size)

        val model3Session = entities[0]
        assertEquals("car_model3", model3Session.carId)
        assertEquals(34.5, model3Session.kwh, 0.001)
        assertEquals("HOME", model3Session.venue)
        assertEquals("Tesla Wall Connector (Gen 3) (Left Bay)", model3Session.chargerName)
        assertEquals(ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID, model3Session.uploadBatchId)

        val blazerSession = entities[1]
        assertEquals("car_blazer", blazerSession.carId)
        assertEquals(52.8, blazerSession.kwh, 0.001)
        assertEquals("HOME", blazerSession.venue)
        assertEquals("Tesla Universal Wall Connector (Right Bay)", blazerSession.chargerName)
        assertEquals(ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID, blazerSession.uploadBatchId)

        // Subsequent poll over overlapping 7-day window must skip already-saved sessions
        val secondPollEntities = OdometerSyncService.buildWallConnectorSessionEntities(
            rawSessions = rawSessions,
            devices = devices,
            cars = garage,
            existingIds = entities.map { it.id }.toSet(),
            existingFingerprints = entities.map { it.fingerprint }.toSet(),
            uploadedAtMs = 1_763_286_400_000L,
            zoneId = zoneId
        )
        assertTrue(secondPollEntities.isEmpty())
    }

    @Test
    fun `formatDailyPollSummary includes Wall Connector kWh when sessions are added`() {
        val car1 = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, odometer = 15000)
        val car2 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, odometer = 28000)
        val odoResults = listOf(
            OdometerSyncResult.Updated(car1.id, 15025, OdometerSyncService.PROVIDER_SMARTCAR),
            OdometerSyncResult.Updated(car2.id, 28040, OdometerSyncService.PROVIDER_TESLA)
        )
        val chargerResult = WallConnectorSyncResult.Synced(
            insertedCount = 2,
            totalKwhAdded = 87.3,
            totalSessionsScanned = 5
        )
        val summary = OdometerSyncService.formatDailyPollSummary(listOf(car1, car2), odoResults, chargerResult)
        assertEquals("Synced 2 vehicles • +87.3 kWh", summary)
    }

    @Test
    fun `setTeslaCredentials clears stale refresh token and expiry when manual token changes or clears`() = runBlocking {
        val prefs = createTestPreferencesManager("manual_token_clear")
        prefs.saveTeslaTokens("old_oauth_token", "stale_refresh_token", 3600L)

        // Updating access token manually must clear stale OAuth refresh/expiry so old refresh_token never overwrites it
        prefs.setTeslaCredentials("cid", "csec", "  new_manual_token_with_energy  ")
        val updated = prefs.getTeslaTokens()
        assertEquals("new_manual_token_with_energy", updated.first)
        assertNull(updated.second)
        assertEquals(0L, updated.third)

        // Passing null preserves existing token (used by OAuth Reconnect dialog before callback finishes)
        prefs.setTeslaCredentials("cid", "csec", null)
        assertEquals("new_manual_token_with_energy", prefs.getTeslaTokens().first)

        // Passing blank clears access token, refresh token, and expiry
        prefs.setTeslaCredentials("cid", "csec", "   ")
        val cleared = prefs.getTeslaTokens()
        assertNull(cleared.first)
        assertNull(cleared.second)
        assertEquals(0L, cleared.third)
    }

    @Test
    fun `buildTeslaAuthUrl includes energy_device_data prompt_missing_scopes and uses fleet-auth token url`() {
        val url = OdometerSyncService.buildTeslaAuthUrl("my_client_id")
        assertTrue(url.startsWith("https://auth.tesla.com/oauth2/v3/authorize?"))
        assertTrue(url.contains("client_id=my_client_id"))
        assertTrue(url.contains("energy_device_data"))
        assertTrue(url.contains("prompt_missing_scopes=true"))
        assertTrue(url.contains("require_requested_scopes=true"))
        assertEquals(
            "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token",
            OdometerSyncService.TESLA_AUTH_TOKEN_URL
        )
    }

    @Test
    fun `parseTeslaEnergySites ignores JsonNull fields on vehicles and parses string-array wall_connectors`() {
        val productsPayload = """
            {
              "response": [
                {
                  "id": 999111,
                  "vin": "5YJ3E1EA1PF000001",
                  "energy_site_id": null,
                  "resource_type": null,
                  "state": "asleep"
                },
                {
                  "id": 999112,
                  "vin": null,
                  "energy_site_id": null,
                  "resource_type": "vehicle"
                },
                {
                  "energy_site_id": "225511099",
                  "resource_type": "energy",
                  "site_name": "Garage Chargers",
                  "id": "site-uuid-99",
                  "components": {
                    "wall_connectors": [
                      "1457768-02-G--GEN3ABC",
                      "1734412-02-D--UNIVXYZ"
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val devices = OdometerSyncService.parseTeslaEnergySites(productsPayload)
        assertEquals(2, devices.size)
        assertEquals("1457768-02-G--GEN3ABC", devices[0].din)
        assertEquals(WallConnectorType.GEN_3, devices[0].connectorType)
        assertEquals("1734412-02-D--UNIVXYZ", devices[1].din)
        assertEquals(WallConnectorType.UNIVERSAL, devices[1].connectorType)
    }

    @Test
    fun `parseTeslaWallConnectorChargeHistory supports time value keys and JsonNull optionals`() {
        val json = """
            {
              "response": {
                "time_series": [
                  {
                    "time": "2026-11-15T10:00:00-08:00",
                    "value": 18500,
                    "charge_duration": 7200,
                    "din": null,
                    "vin": null
                  }
                ]
              }
            }
        """.trimIndent()

        val sessions = OdometerSyncService.parseTeslaWallConnectorChargeHistory(json, "site_1")
        assertEquals(1, sessions.size)
        assertEquals(18500.0, sessions[0].energyWh, 0.01)
        assertNull(sessions[0].din)
        assertNull(sessions[0].vin)
        assertEquals(sessions[0].startDateMs + 7_200_000L, sessions[0].endDateMs)
    }

    @Test
    fun `syncWallConnectorCharging returns MissingScope when JWT lacks energy_device_data even if products returns 200`() = runBlocking {
        val prefs = createTestPreferencesManager("missing_scope_jwt")
        val futureExp = Instant.now().epochSecond + 3600L
        val jwtWithoutEnergy = createJwt(scp = listOf("openid", "offline_access", "vehicle_device_data"), expEpochSec = futureExp)
        prefs.setTeslaCredentials("cid", null, jwtWithoutEnergy)

        val mockClient = createMockClient { url, _ ->
            if (url.endsWith("/api/1/products")) {
                200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001","energy_site_id":null}]}"""
            } else {
                error("Unexpected request: $url")
            }
        }
        val service = OdometerSyncService(prefs, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), mockClient)
        val result = service.syncWallConnectorCharging(emptyList())

        assertTrue(result is WallConnectorSyncResult.MissingScope)
        assertTrue(prefs.lastChargerSyncStatusFlow.first().orEmpty().contains("energy_device_data"))
    }

    @Test
    fun `syncWallConnectorCharging returns NoDevices when energy scope is present and zero sites exist`() = runBlocking {
        val prefs = createTestPreferencesManager("no_devices")
        val futureExp = Instant.now().epochSecond + 3600L
        val jwtWithEnergy = createJwt(scp = listOf("openid", "energy_device_data"), expEpochSec = futureExp)
        prefs.setTeslaCredentials("cid", null, jwtWithEnergy)

        val mockClient = createMockClient { _, _ ->
            200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001"}]}"""
        }
        val service = OdometerSyncService(prefs, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), mockClient)
        val result = service.syncWallConnectorCharging(emptyList())

        assertTrue(result is WallConnectorSyncResult.NoDevices)
    }

    @Test
    fun `syncWallConnectorCharging retries 401 via refresh token and succeeds with clamped 30d window`() = runBlocking {
        val prefs = createTestPreferencesManager("refresh_retry")
        val futureExp = Instant.now().epochSecond + 3600L
        val oldJwt = createJwt(scp = listOf("openid", "energy_device_data"), expEpochSec = futureExp)
        val refreshedJwt = createJwt(scp = listOf("openid", "energy_device_data"), expEpochSec = futureExp + 3600L)
        prefs.setTeslaCredentials("cid", "csec", oldJwt)
        prefs.saveTeslaTokens(oldJwt, "valid_refresh_tok", 3600L)

        val dao = FakeChargingSessionDao()
        var telemetryStartDate: String? = null
        var telemetryEndDate: String? = null
        val mockClient = createMockClient { url, auth ->
            when {
                url.contains("/oauth2/v3/token") -> {
                    200 to """{"access_token":"$refreshedJwt","refresh_token":"next_refresh","expires_in":28800}"""
                }
                url.endsWith("/api/1/products") && auth == "Bearer $oldJwt" -> {
                    401 to """{"error":"token_revoked"}"""
                }
                url.endsWith("/api/1/products") && auth == "Bearer $refreshedJwt" -> {
                    200 to """
                        {
                          "response": [
                            {
                              "energy_site_id": "998877",
                              "resource_type": "wall_connector",
                              "site_name": "Garage",
                              "components": {
                                "wall_connectors": [
                                  { "din": "1457768-02-G--GEN3ONE", "part_number": "1457768-02-G" }
                                ]
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                }
                url.contains("/api/1/energy_sites/998877/telemetry_history") && auth == "Bearer $refreshedJwt" -> {
                    val uriQuery = url.substringAfter("?")
                    val params = uriQuery.split("&").associate {
                        val parts = it.split("=", limit = 2)
                        parts[0] to java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                    }
                    telemetryStartDate = params["start_date"]
                    telemetryEndDate = params["end_date"]
                    200 to """
                        {
                          "response": {
                            "time_series": [
                              {
                                "charge_start_time": "2026-11-15T20:00:00-08:00",
                                "charge_stop_time": "2026-11-15T23:00:00-08:00",
                                "charge_energy_imported": 24000,
                                "din": "1457768-02-G--GEN3ONE",
                                "vin": "5YJ3E1EA1PF000001"
                              }
                            ]
                          }
                        }
                    """.trimIndent()
                }
                else -> error("Unexpected request: $url with auth=$auth")
            }
        }

        val garage = listOf(Car(id = "m3", make = "Tesla", model = "Model 3", year = 2023, vin = "5YJ3E1EA1PF000001", isEv = true))
        val service = OdometerSyncService(prefs, FakeCarDao(), FakeOdometerLogDao(), dao, mockClient)
        val result = service.syncWallConnectorCharging(garage)

        assertTrue(result is WallConnectorSyncResult.Synced)
        val synced = result as WallConnectorSyncResult.Synced
        assertEquals(1, synced.insertedCount)
        assertEquals(24.0, synced.totalKwhAdded, 0.01)
        assertEquals(refreshedJwt, prefs.getTeslaTokens().first)
        assertEquals(1, dao.sessions.size)

        // Ensure clamped date span never exceeds 30 days
        val startMs = OffsetDateTime.parse(telemetryStartDate).toInstant().toEpochMilli()
        val endMs = OffsetDateTime.parse(telemetryEndDate).toInstant().toEpochMilli()
        assertTrue((endMs - startMs) <= (30L * 86_400_000L + 1000L))
    }

    @Test
    fun `syncWallConnectorCharging returns Error on unrecoverable 401 and manual token is not overwritten by stale refresh`() = runBlocking {
        val prefs = createTestPreferencesManager("manual_override_401")
        val futureExp = Instant.now().epochSecond + 3600L
        val oldJwt = createJwt(scp = listOf("openid", "vehicle_device_data"), expEpochSec = futureExp)
        val manualJwt = createJwt(scp = listOf("openid", "vehicle_device_data", "energy_device_data"), expEpochSec = futureExp)

        // First simulate prior OAuth connect with expired token & old refresh_token
        prefs.saveTeslaTokens(oldJwt, "stale_refresh_should_be_cleared", -60L)
        // User updates manual token in Settings -> stale refresh & expiry MUST be wiped
        prefs.setTeslaCredentials("cid", null, manualJwt)

        var calledTokenRefresh = false
        val mockClient = createMockClient { url, auth ->
            if (url.contains("/oauth2/v3/token")) {
                calledTokenRefresh = true
                error("Token refresh endpoint must not be called with stale refresh_token!")
            }
            assertEquals("Bearer $manualJwt", auth)
            401 to """{"error":"invalid_token"}"""
        }

        val service = OdometerSyncService(prefs, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), mockClient)
        val result = service.syncWallConnectorCharging(emptyList())

        assertFalse(calledTokenRefresh)
        assertTrue(result is WallConnectorSyncResult.Error)
        val msg = (result as WallConnectorSyncResult.Error).message
        assertTrue("Expected 401 error message, got: $msg", msg.contains("401"))
    }

    @Test
    fun `syncWallConnectorCharging retries 400 with YYYY-MM-DD and surfaces persistent errors instead of no new sessions`() = runBlocking {
        val prefs = createTestPreferencesManager("fallback_and_error")
        val futureExp = Instant.now().epochSecond + 3600L
        val jwt = createJwt(scp = listOf("openid", "energy_device_data"), expEpochSec = futureExp)
        prefs.setTeslaCredentials("cid", null, jwt)

        val dao = FakeChargingSessionDao()
        var returnPersistentError = false
        val seenHistoryUrls = mutableListOf<String>()
        val mockClient = createMockClient { url, _ ->
            when {
                url.endsWith("/api/1/products") -> {
                    200 to """
                        {
                          "response": [
                            {
                              "energy_site_id": "555",
                              "resource_type": "wall_connector",
                              "components": {
                                "wall_connectors": ["1734412-02-D--UNIV1"]
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                }
                url.contains("/telemetry_history") -> {
                    seenHistoryUrls.add(url)
                    if (returnPersistentError) {
                        500 to """{"error":"internal_server_error"}"""
                    } else if (url.contains("%3A")) {
                        // RFC3339 timestamp format contains encoded colons (%3A) -> simulate 400 to trigger YYYY-MM-DD retry
                        400 to """{"error":"invalid start_date"}"""
                    } else {
                        200 to """
                            {
                              "response": {
                                "time_series": [
                                  {
                                    "charge_start_time": "2026-11-15T18:00:00-08:00",
                                    "charge_stop_time": "2026-11-15T20:00:00-08:00",
                                    "charge_energy_imported": 15200,
                                    "din": "1734412-02-D--UNIV1"
                                  }
                                ]
                              }
                            }
                        """.trimIndent()
                    }
                }
                else -> error("Unexpected url: $url")
            }
        }

        val service = OdometerSyncService(prefs, FakeCarDao(), FakeOdometerLogDao(), dao, mockClient)
        val okResult = service.syncWallConnectorCharging(emptyList())
        assertTrue(okResult is WallConnectorSyncResult.Synced)
        assertEquals(1, (okResult as WallConnectorSyncResult.Synced).insertedCount)
        assertEquals(2, seenHistoryUrls.size)

        // Now verify persistent 500 surfaces Error rather than Synced(0)
        returnPersistentError = true
        val errResult = service.syncWallConnectorCharging(emptyList())
        assertTrue(errResult is WallConnectorSyncResult.Error)
        assertTrue((errResult as WallConnectorSyncResult.Error).message.contains("500"))
    }

    @Test
    fun `parseTeslaWallConnectorChargeHistory parses protobuf seconds objects for charge_start_time and charge_duration`() {
        val realTeslaPayload = """
            {
              "response": {
                "charge_history": [
                  {
                    "charge_start_time": {
                      "seconds": 1731643200
                    },
                    "charge_duration": {
                      "seconds": 12000
                    },
                    "energy_added_wh": 25400
                  },
                  {
                    "charging_start_time": {
                      "seconds": "1731729600",
                      "nanos": 500000000
                    },
                    "charge_duration": {
                      "seconds": "7200",
                      "nanos": 0
                    },
                    "energy_added_wh": 14800,
                    "din": "1734412-02-D--UNIVXYZ"
                  }
                ]
              }
            }
        """.trimIndent()

        val sessions = OdometerSyncService.parseTeslaWallConnectorChargeHistory(realTeslaPayload, "225511099")
        assertEquals(2, sessions.size)

        assertEquals(1731643200_000L, sessions[0].startDateMs)
        assertEquals(1731643200_000L + 12_000_000L, sessions[0].endDateMs)
        assertEquals(25400.0, sessions[0].energyWh, 0.01)

        assertEquals(1731729600_500L, sessions[1].startDateMs)
        assertEquals(1731729600_500L + 7_200_000L, sessions[1].endDateMs)
        assertEquals(14800.0, sessions[1].energyWh, 0.01)
        assertEquals("1734412-02-D--UNIVXYZ", sessions[1].din)
    }

    @Test
    fun `parseTeslaSiteInfoWallConnectors uses part_name for Gen 3 vs Universal classification`() {
        val siteInfoPayload = """
            {
              "response": {
                "site_name": "Home",
                "components": {
                  "wall_connectors": [
                    {
                      "device_id": "abc-1",
                      "din": "din-gen3-no-part-prefix",
                      "part_name": "Gen 3 Wall Connector",
                      "is_active": true
                    },
                    {
                      "device_id": "abc-2",
                      "din": "din-univ-no-part-prefix",
                      "part_name": "Universal Wall Connector",
                      "is_active": true
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val devices = OdometerSyncService.parseTeslaSiteInfoWallConnectors(siteInfoPayload, "site_home")
        assertEquals(2, devices.size)
        assertEquals(WallConnectorType.GEN_3, devices[0].connectorType)
        assertEquals(WallConnectorType.UNIVERSAL, devices[1].connectorType)
        assertEquals("Home", devices[0].siteName)
    }

    @Test
    fun `syncWallConnectorCharging imports protobuf charge_history sessions and sends second-precision RFC3339 timestamps`() = runBlocking {
        val prefs = createTestPreferencesManager("protobuf_charge_history")
        val futureExp = Instant.now().epochSecond + 3600L
        val jwt = createJwt(scp = listOf("openid", "energy_device_data"), expEpochSec = futureExp)
        prefs.setTeslaCredentials("cid", null, jwt)

        val dao = FakeChargingSessionDao()
        var capturedStart: String? = null
        var capturedEnd: String? = null
        val mockClient = createMockClient { url, _ ->
            when {
                url.endsWith("/api/1/products") -> {
                    200 to """
                        {
                          "response": [
                            {
                              "energy_site_id": 225511099,
                              "resource_type": "wall_connector",
                              "site_name": "Garage",
                              "components": {
                                "wall_connectors": [
                                  { "din": "1457768-02-G--GEN3ONE" }
                                ]
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                }
                url.contains("/api/1/energy_sites/225511099/telemetry_history") -> {
                    val uriQuery = url.substringAfter("?")
                    val params = uriQuery.split("&").associate {
                        val parts = it.split("=", limit = 2)
                        parts[0] to java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                    }
                    capturedStart = params["start_date"]
                    capturedEnd = params["end_date"]
                    200 to """
                        {
                          "response": {
                            "charge_history": [
                              {
                                "charge_start_time": { "seconds": 1731556800 },
                                "charge_duration": { "seconds": 10800 },
                                "energy_added_wh": 22500
                              }
                            ]
                          }
                        }
                    """.trimIndent()
                }
                else -> error("Unexpected url: $url")
            }
        }

        val cars = listOf(Car(id = "m3", make = "Tesla", model = "Model 3", year = 2023, isEv = true))
        val service = OdometerSyncService(prefs, FakeCarDao(), FakeOdometerLogDao(), dao, mockClient)
        val result = service.syncWallConnectorCharging(cars)

        assertTrue(result is WallConnectorSyncResult.Synced)
        val synced = result as WallConnectorSyncResult.Synced
        assertEquals(1, synced.insertedCount)
        assertEquals(1, synced.totalSessionsScanned)
        assertEquals(22.5, synced.totalKwhAdded, 0.01)
        assertEquals(1, dao.sessions.size)
        assertEquals("m3", dao.sessions[0].carId)
        assertEquals(1731556800_000L, dao.sessions[0].startDateMs)
        assertEquals(1731556800_000L + 10_800_000L, dao.sessions[0].endDateMs)

        // Verify second-precision RFC3339 timestamps (no fractional sub-seconds)
        assertFalse("Expected no fractional seconds in start_date=$capturedStart", capturedStart!!.contains("."))
        assertFalse("Expected no fractional seconds in end_date=$capturedEnd", capturedEnd!!.contains("."))
    }

    @Test
    fun `syncDailyEndOfDay polls odometers without syncing Wall Connector charging`() = runBlocking {
        val prefs = createTestPreferencesManager("daily_poll_no_charger")
        val token = createJwt(scp = listOf("vehicle_device_data", "energy_device_data"), expEpochSec = 4_102_444_800L)
        prefs.setTeslaCredentials("cid", "csec", token)

        val requestedUrls = mutableListOf<String>()
        val client = createMockClient { url, _ ->
            requestedUrls.add(url)
            when {
                url.endsWith("/api/1/vehicles") -> 200 to """
                    {
                      "response": [
                        {
                          "id": 42,
                          "vin": "5YJ3E1EA1PF000001",
                          "state": "online"
                        }
                      ]
                    }
                """.trimIndent()
                url.contains("/vehicle_data") -> 200 to """
                    {
                      "response": {
                        "state": "online",
                        "vehicle_state": {
                          "odometer": 28100.0
                        }
                      }
                    }
                """.trimIndent()
                else -> 500 to """{"error":"unexpected endpoint $url"}"""
            }
        }

        val carDao = FakeCarDao()
        val odoDao = FakeOdometerLogDao()
        val chargingDao = FakeChargingSessionDao()
        val m3 = Car(
            id = "m3",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            odometer = 28000,
            vin = "5YJ3E1EA1PF000001"
        )
        carDao.insertCar(com.example.car_logger.data.local.entity.CarEntity.fromModel(m3))

        val service = OdometerSyncService(prefs, carDao, odoDao, chargingDao, client)
        val results = service.syncDailyEndOfDay(listOf(m3))

        assertEquals(1, results.size)
        assertTrue(results[0] is OdometerSyncResult.Updated)
        assertFalse(requestedUrls.any { it.contains("/api/1/products") || it.contains("telemetry_history") })
        assertEquals(0L, prefs.getLastChargerSyncTime())
        assertEquals("Synced 1 vehicle", prefs.lastDailyPollStatusFlow.first())
    }

    private fun createTestPreferencesManager(name: String): PreferencesManager {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("$name.preferences_pb") }
        )
        return PreferencesManager(dataStore)
    }

    private fun createJwt(scp: List<String>? = null, expEpochSec: Long? = null): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claimsParts = mutableListOf<String>()
        if (scp != null) {
            val arr = scp.joinToString(",") { "\"$it\"" }
            claimsParts.add("\"scp\":[$arr]")
        }
        if (expEpochSec != null) {
            claimsParts.add("\"exp\":$expEpochSec")
        }
        val payload = encoder.encodeToString("{${claimsParts.joinToString(",")}}".toByteArray())
        return "$header.$payload.sig"
    }

    private fun createMockClient(handler: (url: String, authHeader: String?) -> Pair<Int, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val (code, bodyStr) = handler(req.url.toString(), req.header("Authorization"))
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("HTTP $code")
                    .body(bodyStr.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
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

    private class FakeChargingSessionDao : ChargingSessionDao {
        val sessions = mutableListOf<ChargingSessionEntity>()

        override fun getAllSessions(): Flow<List<ChargingSessionEntity>> = emptyFlow()
        override suspend fun getAllSessionsList(): List<ChargingSessionEntity> = sessions.sortedByDescending { it.startDateMs }
        override suspend fun getAllFingerprints(): List<String> = sessions.map { it.fingerprint }
        override suspend fun insertSessions(sessions: List<ChargingSessionEntity>) {
            sessions.forEach { session ->
                if (this.sessions.none { it.id == session.id }) {
                    this.sessions.add(session)
                }
            }
        }
        override suspend fun insertSession(session: ChargingSessionEntity) {
            sessions.removeAll { it.id == session.id }
            sessions.add(session)
        }
        override suspend fun deleteSessionById(id: String) { sessions.removeAll { it.id == id } }
        override suspend fun deleteSessionsByIds(ids: List<String>) { sessions.removeAll { it.id in ids } }
        override suspend fun deleteSessionsByBatchId(batchId: String) { sessions.removeAll { it.uploadBatchId == batchId } }
    }
}
