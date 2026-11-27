package com.example.car_logger.telemetry

import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.WallConnectorSyncResult
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLDecoder
import java.time.Instant
import java.time.OffsetDateTime

class WallConnectorSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `syncWallConnectorCharging returns MissingScope when JWT lacks energy_device_data even if products returns 200`() = runBlocking {
        val prefs = createTestPreferencesManager(tempFolder, "missing_scope_jwt")
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
        val prefs = createTestPreferencesManager(tempFolder, "no_devices")
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
        val prefs = createTestPreferencesManager(tempFolder, "refresh_retry")
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
                        parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
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

        val startMs = OffsetDateTime.parse(telemetryStartDate).toInstant().toEpochMilli()
        val endMs = OffsetDateTime.parse(telemetryEndDate).toInstant().toEpochMilli()
        assertTrue((endMs - startMs) <= (30L * 86_400_000L + 1000L))
    }

    @Test
    fun `syncWallConnectorCharging returns Error on unrecoverable 401 and manual token is not overwritten by stale refresh`() = runBlocking {
        val prefs = createTestPreferencesManager(tempFolder, "manual_override_401")
        val futureExp = Instant.now().epochSecond + 3600L
        val oldJwt = createJwt(scp = listOf("openid", "vehicle_device_data"), expEpochSec = futureExp)
        val manualJwt = createJwt(scp = listOf("openid", "vehicle_device_data", "energy_device_data"), expEpochSec = futureExp)

        prefs.saveTeslaTokens(oldJwt, "stale_refresh_should_be_cleared", -60L)
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
        val prefs = createTestPreferencesManager(tempFolder, "fallback_and_error")
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

        returnPersistentError = true
        val errResult = service.syncWallConnectorCharging(emptyList())
        assertTrue(errResult is WallConnectorSyncResult.Error)
        assertTrue((errResult as WallConnectorSyncResult.Error).message.contains("500"))
    }

    @Test
    fun `syncWallConnectorCharging imports protobuf charge_history sessions and sends second-precision RFC3339 timestamps`() = runBlocking {
        val prefs = createTestPreferencesManager(tempFolder, "protobuf_charge_history")
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
                        parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
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

        assertFalse("Expected no fractional seconds in start_date=$capturedStart", capturedStart!!.contains("."))
        assertFalse("Expected no fractional seconds in end_date=$capturedEnd", capturedEnd!!.contains("."))
    }
}
