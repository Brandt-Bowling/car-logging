package com.example.car_logger.telemetry

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.TeslaChargeSnapshot
import com.example.car_logger.services.WorkChargeDecision
import com.example.car_logger.services.WorkChargingSyncResult
import com.example.car_logger.services.WorkLocationConfig
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeCarDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeChargingSessionDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeOdometerLogDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.createJwt
import com.example.car_logger.telemetry.TelemetryTestFixtures.createMockClient
import com.example.car_logger.telemetry.TelemetryTestFixtures.createTestPreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class TeslaWorkChargingSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `parseTeslaChargeSnapshot parses both live vehicle_data and vehicle_data_cached payloads`() {
        val liveJson = """
            {
              "response": {
                "vehicle_state": { "odometer": 28450.7, "timestamp": 1762460000000 },
                "charge_state": {
                  "charge_energy_added": 21.45,
                  "charging_state": "Complete",
                  "fast_charger_present": false,
                  "conn_charge_cable": "SAE",
                  "battery_level": 80,
                  "timestamp": 1762460100000
                },
                "drive_state": { "latitude": 42.3314, "longitude": -83.0458 }
              }
            }
        """.trimIndent()
        val live = OdometerSyncService.parseTeslaChargeSnapshot(liveJson, "5YJ3E1EA1PF000001", isCachedResponse = false)
        assertTrue(live != null)
        assertEquals(21.45, live!!.chargeEnergyAddedKwh, 0.001)
        assertEquals("Complete", live.chargingState)
        assertFalse(live.fastChargerPresent)
        assertEquals("SAE", live.connChargeCable)
        assertEquals(28451, live.odometerMiles)
        assertEquals(42.3314, live.latitude!!, 0.0001)
        assertEquals(-83.0458, live.longitude!!, 0.0001)
        assertFalse(live.isCachedResponse)

        val cachedJson = """
            {
              "response": {
                "data": {
                  "charge_state": {
                    "charge_energy_added": 16.2,
                    "charging_state": "Disconnected",
                    "fast_charger_present": false
                  }
                }
              }
            }
        """.trimIndent()
        val cached = OdometerSyncService.parseTeslaChargeSnapshot(cachedJson, "5YJ3E1EA1PF000001", isCachedResponse = true)
        assertTrue(cached != null)
        assertEquals(16.2, cached!!.chargeEnergyAddedKwh, 0.001)
        assertEquals("Disconnected", cached.chargingState)
        assertTrue(cached.isCachedResponse)
    }

    @Test
    fun `evaluateWorkChargeCandidate enforces all 5 filtering and deduplication rules`() {
        val zone = ZoneId.of("America/New_York")
        val nowMs = OffsetDateTime.parse("2026-11-11T16:30:00-05:00").toInstant().toEpochMilli()
        val vin = "5YJ3E1EA1PF000001"
        val workGps = WorkLocationConfig(latitude = 42.3314, longitude = -83.0458, radiusMeters = 500)

        // Rule 1: Skip fast charger or < 0.5 kWh noise
        val dcSkip = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(vin = vin, chargeEnergyAddedKwh = 35.0, fastChargerPresent = true),
            targetCarId = "m3",
            existingSessions = emptyList(),
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(dcSkip is WorkChargeDecision.Skip)
        assertEquals(ElectricityImportParser.VENUE_PUBLIC, (dcSkip as WorkChargeDecision.Skip).watermarkVenue)

        val noiseSkip = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(vin = vin, chargeEnergyAddedKwh = 0.25, fastChargerPresent = false),
            targetCarId = "m3",
            existingSessions = emptyList(),
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(noiseSkip is WorkChargeDecision.Skip)

        // Rule 2: GPS geofence skips when plugged in away from work, verifies when inside radius
        val awaySkip = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(
                vin = vin, chargeEnergyAddedKwh = 18.0, chargingState = "Charging",
                latitude = 42.4000, longitude = -83.2000
            ),
            targetCarId = "m3",
            existingSessions = emptyList(),
            workLocation = workGps,
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(awaySkip is WorkChargeDecision.Skip)

        val insideCapture = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(
                vin = vin, chargeEnergyAddedKwh = 18.0, chargingState = "Complete",
                latitude = 42.3315, longitude = -83.0457
            ),
            targetCarId = "m3",
            existingSessions = emptyList(),
            workLocation = workGps,
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(insideCapture is WorkChargeDecision.CaptureNew)
        assertTrue((insideCapture as WorkChargeDecision.CaptureNew).verifiedByGps)

        val stoppedHomeScheduled = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(
                vin = vin, chargeEnergyAddedKwh = 18.0, chargingState = "Stopped",
                latitude = 42.4000, longitude = -83.2000
            ),
            targetCarId = "m3",
            existingSessions = emptyList(),
            workLocation = workGps,
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(stoppedHomeScheduled is WorkChargeDecision.CaptureNew)
        assertFalse((stoppedHomeScheduled as WorkChargeDecision.CaptureNew).verifiedByGps)

        // Rule 3: 36h Home Wall Connector echo filter (0.75x - 1.12x kWh) skips home overnight charge
        val homeWallConnectorSession = ChargingSessionEntity(
            id = "twc_home_1",
            carId = "m3",
            startDateMs = nowMs - 14L * 3_600_000L,
            endDateMs = nowMs - 11L * 3_600_000L,
            kwh = 24.0,
            venue = ElectricityImportParser.VENUE_HOME,
            fingerprint = "fp_twc_home_1"
        )
        val homeEchoSkip = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(vin = vin, chargeEnergyAddedKwh = 21.8, chargingState = "Disconnected"),
            targetCarId = "m3",
            existingSessions = listOf(homeWallConnectorSession),
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(homeEchoSkip is WorkChargeDecision.Skip)
        assertEquals(ElectricityImportParser.VENUE_HOME, (homeEchoSkip as WorkChargeDecision.Skip).watermarkVenue)

        // Rule 4: Same-day progressive update vs already-logged idempotency
        val existingTodayWork = ChargingSessionEntity(
            id = "tesla_work_${vin}_2026-11-11",
            carId = "m3",
            startDateMs = nowMs - 3_600_000L,
            endDateMs = nowMs - 3_600_000L,
            kwh = 14.0,
            venue = ElectricityImportParser.VENUE_WORK,
            directCost = 0.0,
            fingerprint = "fp_work_today"
        )
        val progressiveUpdate = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(vin = vin, chargeEnergyAddedKwh = 19.5, chargingState = "Charging"),
            targetCarId = "m3",
            existingSessions = listOf(existingTodayWork),
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(progressiveUpdate is WorkChargeDecision.UpdateExisting)
        assertEquals(19.5, (progressiveUpdate as WorkChargeDecision.UpdateExisting).updatedEnergyKwh, 0.01)

        val alreadyLogged = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(vin = vin, chargeEnergyAddedKwh = 14.0, chargingState = "Complete"),
            targetCarId = "m3",
            existingSessions = listOf(existingTodayWork),
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(alreadyLogged is WorkChargeDecision.AlreadyLogged)

        // Rule 5: Cross-day stale watermark guard prevents re-logging yesterday's charge when disconnected
        val staleSkip = OdometerSyncService.evaluateWorkChargeCandidate(
            snapshot = TeslaChargeSnapshot(vin = vin, chargeEnergyAddedKwh = 19.5, chargingState = "Disconnected"),
            targetCarId = "m3",
            existingSessions = emptyList(),
            lastWatermarkEnergy = 19.5,
            lastWatermarkDateIso = "2026-11-10",
            lastWatermarkVenue = ElectricityImportParser.VENUE_WORK,
            nowMs = nowMs,
            zoneId = zone
        )
        assertTrue(staleSkip is WorkChargeDecision.Skip)
    }

    @Test
    fun `syncTeslaWorkCharging handles location 403 retry, zero-wake cached success, and cached 404 asleep`() = runBlocking {
        val futureExp = Instant.now().epochSecond + 3600L
        val m3 = Car(
            id = "m3", make = "Tesla", model = "Model 3", year = 2024,
            isEv = true, vin = "5YJ3E1EA1PF000001"
        )

        val prefs1 = createTestPreferencesManager(tempFolder, "work_403_fallback")
        val jwtWithLoc = createJwt(scp = listOf("openid", "vehicle_device_data", "vehicle_location"), expEpochSec = futureExp)
        prefs1.setTeslaCredentials("cid", "csec", jwtWithLoc)
        prefs1.setWorkLocation(42.3314, -83.0458, 500)
        val dao1 = FakeChargingSessionDao()
        val urls1 = mutableListOf<String>()
        val client1 = createMockClient { url, _ ->
            urls1.add(url)
            when {
                url.endsWith("/api/1/vehicles") -> 200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001","state":"online"}]}"""
                url.contains("location_data") -> 403 to """{"error":"missing vehicle_location"}"""
                url.contains("/vehicle_data") -> 200 to """
                    {
                      "response": {
                        "charge_state": {
                          "charge_energy_added": 19.2,
                          "charging_state": "Complete",
                          "fast_charger_present": false
                        }
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected url $url")
            }
        }
        val service1 = OdometerSyncService(prefs1, FakeCarDao(), FakeOdometerLogDao(), dao1, client1)
        val res1 = service1.syncTeslaWorkCharging(listOf(m3), forceWake = false, syncHomeChargersFirst = false)
        assertTrue(res1 is WorkChargingSyncResult.Captured)
        assertEquals(19.2, (res1 as WorkChargingSyncResult.Captured).kwh, 0.01)
        assertEquals(1, dao1.sessions.size)
        assertEquals(ElectricityImportParser.VENUE_WORK, dao1.sessions[0].venue)
        assertEquals(0.0, dao1.sessions[0].directCost!!, 0.001)
        assertEquals(ElectricityImportParser.AUTO_WORK_CHARGING_BATCH_ID, dao1.sessions[0].uploadBatchId)
        assertTrue(urls1.any { it.contains("location_data") })
        assertTrue(urls1.any { it.contains("/vehicle_data") && !it.contains("location_data") })

        val prefs2 = createTestPreferencesManager(tempFolder, "work_cached_ok")
        val jwtBasic = createJwt(scp = listOf("openid", "vehicle_device_data"), expEpochSec = futureExp)
        prefs2.setTeslaCredentials("cid", "csec", jwtBasic)
        val dao2 = FakeChargingSessionDao()
        val urls2 = mutableListOf<String>()
        val client2 = createMockClient { url, _ ->
            urls2.add(url)
            when {
                url.endsWith("/api/1/vehicles") -> 200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001","state":"asleep"}]}"""
                url.contains("/vehicle_data_cached") -> 200 to """
                    {
                      "response": {
                        "data": {
                          "charge_state": {
                            "charge_energy_added": 15.6,
                            "charging_state": "Disconnected",
                            "fast_charger_present": false
                          }
                        }
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected url $url")
            }
        }
        val service2 = OdometerSyncService(prefs2, FakeCarDao(), FakeOdometerLogDao(), dao2, client2)
        val res2 = service2.syncTeslaWorkCharging(listOf(m3), forceWake = false, syncHomeChargersFirst = false)
        assertTrue(res2 is WorkChargingSyncResult.Captured)
        assertEquals(15.6, (res2 as WorkChargingSyncResult.Captured).kwh, 0.01)
        assertFalse(urls2.any { it.contains("wake_up") })

        val prefs3 = createTestPreferencesManager(tempFolder, "work_cached_404")
        prefs3.setTeslaCredentials("cid", "csec", jwtBasic)
        val urls3 = mutableListOf<String>()
        val client3 = createMockClient { url, _ ->
            urls3.add(url)
            when {
                url.endsWith("/api/1/vehicles") -> 200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001","state":"asleep"}]}"""
                url.contains("/vehicle_data_cached") -> 404 to """{"error":"no cached data"}"""
                else -> error("Unexpected url $url")
            }
        }
        val service3 = OdometerSyncService(prefs3, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), client3)
        val res3 = service3.syncTeslaWorkCharging(listOf(m3), forceWake = false, syncHomeChargersFirst = true)
        assertTrue(res3 is WorkChargingSyncResult.SkippedAsleep)
        assertFalse(urls3.any { it.contains("wake_up") })
        assertFalse(urls3.any { it.contains("/api/1/products") })
    }
}
