package com.example.car_logger.telemetry

import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeCarDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeChargingSessionDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeOdometerLogDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.createJwt
import com.example.car_logger.telemetry.TelemetryTestFixtures.createMockClient
import com.example.car_logger.telemetry.TelemetryTestFixtures.createTestPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class TeslaOAuthAndOdometerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
    fun `wakeAndPollUntilOnline sends at most one wake_up and polls state until online`() = runBlocking {
        var wakeCalls = 0
        var statePollCalls = 0
        val pollStates = listOf("asleep", "waking", "online", "online")

        val becameOnline = OdometerSyncService.wakeAndPollUntilOnline(
            maxStatePolls = 6,
            delayMs = 0L,
            sendWake = {
                wakeCalls++
                "asleep"
            },
            pollState = {
                val s = pollStates[statePollCalls]
                statePollCalls++
                s
            }
        )

        assertTrue(becameOnline)
        assertEquals("Must only send ONE paid POST /wake_up", 1, wakeCalls)
        assertEquals("Should stop polling state as soon as online", 3, statePollCalls)
    }

    @Test
    fun `syncDailyEndOfDay uses zero-wake vehicle_data_cached when asleep and falls back to single wake_up on cache miss`() = runBlocking {
        val futureExp = Instant.now().epochSecond + 3600L
        val token = createJwt(scp = listOf("openid", "vehicle_device_data"), expEpochSec = futureExp)
        val m3 = Car(
            id = "m3",
            make = "Tesla",
            model = "Model 3",
            year = 2024,
            odometer = 28000,
            vin = "5YJ3E1EA1PF000001",
            isEv = true
        )

        // 1) Asleep car + vehicle_data_cached 200 -> updates odometer with ZERO wake_up calls!
        val prefsCached = createTestPreferencesManager(tempFolder, "nightly_cached_ok")
        prefsCached.setTeslaCredentials("cid", "csec", token)
        val carDao1 = FakeCarDao().apply { insertCar(CarEntity.fromModel(m3)) }
        val odoDao1 = FakeOdometerLogDao()
        val urls1 = mutableListOf<String>()
        val client1 = createMockClient { url, _ ->
            urls1.add(url)
            when {
                url.endsWith("/api/1/vehicles") -> 200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001","state":"asleep"}]}"""
                url.contains("/vehicle_data_cached") -> 200 to """
                    {
                      "response": {
                        "data": {
                          "vehicle_state": { "odometer": 28142.4 },
                          "charge_state": { "charge_energy_added": 0.0, "fast_charger_present": false }
                        }
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected url $url")
            }
        }
        val service1 = OdometerSyncService(prefsCached, carDao1, odoDao1, FakeChargingSessionDao(), client1).apply {
            wakePollDelayMs = 0L
        }
        val res1 = service1.syncDailyEndOfDay(listOf(m3))
        assertEquals(1, res1.size)
        assertTrue(res1[0] is OdometerSyncResult.Updated)
        assertEquals(28142, (res1[0] as OdometerSyncResult.Updated).odometerMiles)
        assertFalse("Should NOT call wake_up when vehicle_data_cached succeeds", urls1.any { it.contains("wake_up") })
        assertFalse("Should NOT call live vehicle_data when cached succeeded", urls1.any { it.contains("/vehicle_data?") })

        // 2) Asleep car + vehicle_data_cached 404 -> sends EXACTLY ONE wake_up + polls GET /vehicles/{vin} + reads live vehicle_data!
        val prefsFallback = createTestPreferencesManager(tempFolder, "nightly_cached_404_single_wake")
        prefsFallback.setTeslaCredentials("cid", "csec", token)
        val carDao2 = FakeCarDao().apply { insertCar(CarEntity.fromModel(m3)) }
        val odoDao2 = FakeOdometerLogDao()
        var wakeCount = 0
        var statePollCount = 0
        val client2 = createMockClient { url, _ ->
            when {
                url.endsWith("/api/1/vehicles") -> 200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001","state":"asleep"}]}"""
                url.contains("/vehicle_data_cached") -> 404 to """{"error":"no cached data"}"""
                url.endsWith("/wake_up") -> {
                    wakeCount++
                    200 to """{"response":{"state":"asleep"}}"""
                }
                url.endsWith("/api/1/vehicles/5YJ3E1EA1PF000001") -> {
                    statePollCount++
                    val state = if (statePollCount >= 2) "online" else "asleep"
                    200 to """{"response":{"state":"$state"}}"""
                }
                url.contains("/vehicle_data?") -> 200 to """
                    {
                      "response": {
                        "vehicle_state": { "odometer": 28205.8 }
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected url $url")
            }
        }
        val service2 = OdometerSyncService(prefsFallback, carDao2, odoDao2, FakeChargingSessionDao(), client2).apply {
            wakePollDelayMs = 0L
        }
        val res2 = service2.syncDailyEndOfDay(listOf(m3))
        assertEquals(1, res2.size)
        assertTrue(res2[0] is OdometerSyncResult.Updated)
        assertEquals(28206, (res2[0] as OdometerSyncResult.Updated).odometerMiles)
        assertEquals("Must send at most ONE paid POST /wake_up on fallback", 1, wakeCount)
        assertEquals("Should poll GET /vehicles/{vin} until online", 2, statePollCount)

        // 3) Manual "Wake & sync" (isManual = true, forceWake = true) bypasses cache, sends ONE wake_up, and reads live data
        val prefsManual = createTestPreferencesManager(tempFolder, "manual_single_wake")
        prefsManual.setTeslaCredentials("cid", "csec", token)
        val carDao3 = FakeCarDao().apply { insertCar(CarEntity.fromModel(m3)) }
        var manualWakeCount = 0
        var manualCacheCalled = false
        val client3 = createMockClient { url, _ ->
            when {
                url.endsWith("/api/1/vehicles") -> 200 to """{"response":[{"id":1,"vin":"5YJ3E1EA1PF000001","state":"asleep"}]}"""
                url.contains("/vehicle_data_cached") -> {
                    manualCacheCalled = true
                    200 to """{"response":{"data":{"vehicle_state":{"odometer":28000.0}}}}"""
                }
                url.endsWith("/wake_up") -> {
                    manualWakeCount++
                    200 to """{"response":{"state":"online"}}"""
                }
                url.contains("/vehicle_data?") -> 200 to """{"response":{"vehicle_state":{"odometer":28310.0}}}"""
                else -> error("Unexpected url $url")
            }
        }
        val service3 = OdometerSyncService(prefsManual, carDao3, FakeOdometerLogDao(), FakeChargingSessionDao(), client3).apply {
            wakePollDelayMs = 0L
        }
        val res3 = service3.syncVehicle(m3, isManual = true, forceWake = true)
        assertTrue(res3 is OdometerSyncResult.Updated)
        assertEquals(28310, (res3 as OdometerSyncResult.Updated).odometerMiles)
        assertFalse("Manual Wake & sync should fetch live data instead of cached", manualCacheCalled)
        assertEquals("Manual Wake & sync must send at most ONE wake_up", 1, manualWakeCount)
    }

    @Test
    fun `syncDailyEndOfDay polls odometers without syncing Wall Connector charging`() = runBlocking {
        val prefs = createTestPreferencesManager(tempFolder, "daily_poll_no_charger")
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
        carDao.insertCar(CarEntity.fromModel(m3))

        val service = OdometerSyncService(prefs, carDao, odoDao, chargingDao, client)
        val results = service.syncDailyEndOfDay(listOf(m3))

        assertEquals(1, results.size)
        assertTrue(results[0] is OdometerSyncResult.Updated)
        assertFalse(requestedUrls.any { it.contains("/api/1/products") || it.contains("telemetry_history") })
        assertEquals(0L, prefs.getLastChargerSyncTime())
        assertEquals("Synced 1 vehicle", prefs.lastDailyPollStatusFlow.first())
    }

    @Test
    fun `setTeslaCredentials clears stale refresh token and expiry when manual token changes or clears`() = runBlocking {
        val prefs = createTestPreferencesManager(tempFolder, "manual_token_clear")
        prefs.saveTeslaTokens("old_oauth_token", "stale_refresh_token", 3600L)

        prefs.setTeslaCredentials("cid", "csec", "  new_manual_token_with_energy  ")
        val updated = prefs.getTeslaTokens()
        assertEquals("new_manual_token_with_energy", updated.first)
        assertNull(updated.second)
        assertEquals(0L, updated.third)

        prefs.setTeslaCredentials("cid", "csec", null)
        assertEquals("new_manual_token_with_energy", prefs.getTeslaTokens().first)

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
    fun `buildTeslaAuthUrl keeps optional scopes opt-in and appends them independently or together`() {
        val defaultUrl = OdometerSyncService.buildTeslaAuthUrl("client_123")
        assertTrue(defaultUrl.contains("vehicle_device_data"))
        assertTrue(defaultUrl.contains("energy_device_data"))
        assertFalse(defaultUrl.contains("vehicle_location"))
        assertFalse(defaultUrl.contains("vehicle_charging_cmds"))

        val withLocation = OdometerSyncService.buildTeslaAuthUrl("client_123", includeLocationScope = true)
        assertTrue(withLocation.contains("vehicle_location"))
        assertFalse(withLocation.contains("vehicle_charging_cmds"))

        val withCharging = OdometerSyncService.buildTeslaAuthUrl("client_123", includeChargingHistoryScope = true)
        assertFalse(withCharging.contains("vehicle_location"))
        assertTrue(withCharging.contains("vehicle_charging_cmds"))

        val withBoth = OdometerSyncService.buildTeslaAuthUrl(
            "client_123",
            includeLocationScope = true,
            includeChargingHistoryScope = true
        )
        assertTrue(withBoth.contains("vehicle_location"))
        assertTrue(withBoth.contains("vehicle_charging_cmds"))
    }
}
