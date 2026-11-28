package com.example.car_logger.telemetry

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SuperchargerSyncResult
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeCarDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeChargingSessionDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeOdometerLogDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.createJwt
import com.example.car_logger.telemetry.TelemetryTestFixtures.createMockClient
import com.example.car_logger.telemetry.TelemetryTestFixtures.createTestPreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class TeslaSuperchargerSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `syncTeslaSuperchargerCharging skips when scope missing, handles 403, and paginates`() = runBlocking {
        val futureExp = Instant.now().epochSecond + 3600L
        val m3 = Car(id = "car_m3", make = "Tesla", model = "Model 3", year = 2024, isEv = true, vin = "5YJ3E1EA1PF000001")

        val prefsNoScope = createTestPreferencesManager(tempFolder, "sc_no_scope")
        prefsNoScope.setTeslaCredentials("cid", "csec", createJwt(scp = listOf("openid", "vehicle_device_data"), expEpochSec = futureExp))
        var httpCalls = 0
        val noCallClient = createMockClient { url, _ ->
            httpCalls++
            error("Should not have called $url")
        }
        val resNoScope = OdometerSyncService(prefsNoScope, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), noCallClient)
            .syncTeslaSuperchargerCharging(listOf(m3))
        assertTrue(resNoScope is SuperchargerSyncResult.MissingScope)
        assertEquals(0, httpCalls)

        val prefs403 = createTestPreferencesManager(tempFolder, "sc_403")
        val jwtWithSc = createJwt(scp = listOf("openid", "vehicle_device_data", "vehicle_charging_cmds"), expEpochSec = futureExp)
        prefs403.setTeslaCredentials("cid", "csec", jwtWithSc)
        val client403 = createMockClient { _, _ -> 403 to """{"error":"forbidden"}""" }
        val res403 = OdometerSyncService(prefs403, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), client403)
            .syncTeslaSuperchargerCharging(listOf(m3))
        assertTrue(res403 is SuperchargerSyncResult.MissingScope)

        val prefsPaged = createTestPreferencesManager(tempFolder, "sc_paged")
        prefsPaged.setTeslaCredentials("cid", "csec", jwtWithSc)
        val dao = FakeChargingSessionDao()
        val requestedPages = mutableListOf<String>()
        val clientPaged = createMockClient { url, _ ->
            requestedPages.add(url)
            when {
                url.contains("pageNo=1") -> 200 to """
                    {
                      "response": {
                        "data": [
                          {
                            "sessionId": "p1_s1",
                            "vin": "5YJ3E1EA1PF000001",
                            "siteLocationName": "Page 1 SC",
                            "chargeStartDateTime": "2026-11-10T10:00:00Z",
                            "chargeStopDateTime": "2026-11-10T10:25:00Z",
                            "fees": [{ "feeType": "CHARGING", "uom": "kwh", "usageBase": 30.0, "rateBase": 0.40, "netDue": 12.0 }]
                          }
                        ],
                        "hasMore": true
                      }
                    }
                """.trimIndent()
                url.contains("pageNo=2") -> 200 to """
                    {
                      "response": {
                        "data": [
                          {
                            "sessionId": "p2_s1",
                            "vin": "5YJ3E1EA1PF000001",
                            "siteLocationName": "Page 2 SC",
                            "chargeStartDateTime": "2026-11-11T10:00:00Z",
                            "chargeStopDateTime": "2026-11-11T10:25:00Z",
                            "fees": [{ "feeType": "CHARGING", "uom": "kwh", "usageBase": 20.0, "rateBase": 0.35, "netDue": 7.0 }]
                          }
                        ],
                        "hasMore": false
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected url $url")
            }
        }
        val resPaged = OdometerSyncService(prefsPaged, FakeCarDao(), FakeOdometerLogDao(), dao, clientPaged)
            .syncTeslaSuperchargerCharging(listOf(m3))
        assertTrue(resPaged is SuperchargerSyncResult.Synced)
        val synced = resPaged as SuperchargerSyncResult.Synced
        assertEquals(2, synced.insertedCount)
        assertEquals(50.0, synced.totalKwhAdded, 0.01)
        assertEquals(19.0, synced.totalCostAdded, 0.01)
        assertEquals(2, dao.sessions.size)
        assertEquals(2, requestedPages.size)
    }

    @Test
    fun `syncTeslaSuperchargerCharging stays within 1-year API limit, chunks multi-year lookbacks, and formats 400 errors`() = runBlocking {
        val futureExp = Instant.now().epochSecond + 3600L
        val jwtWithSc = createJwt(scp = listOf("openid", "vehicle_device_data", "vehicle_charging_cmds"), expEpochSec = futureExp)
        val m3 = Car(id = "car_m3", make = "Tesla", model = "Model 3", year = 2024, isEv = true, vin = "5YJ3E1EA1PF000001")

        fun parseQueryParam(url: String, name: String): String {
            val query = url.substringAfter("?", "")
            val raw = query.split("&").first { it.startsWith("$name=") }.substringAfter("=")
            return URLDecoder.decode(raw, "UTF-8")
        }

        val prefsInitial = createTestPreferencesManager(tempFolder, "sc_initial_window")
        prefsInitial.setTeslaCredentials("cid", "csec", jwtWithSc)
        val initialUrls = mutableListOf<String>()
        val clientInitial = createMockClient { url, _ ->
            initialUrls.add(url)
            200 to """{"response":{"data":[],"hasMore":false}}"""
        }
        val resInitial = OdometerSyncService(prefsInitial, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), clientInitial)
            .syncTeslaSuperchargerCharging(listOf(m3))
        assertTrue(resInitial is SuperchargerSyncResult.Synced)
        assertEquals(1, initialUrls.size)
        val startStr = parseQueryParam(initialUrls.single(), "startTime")
        val endStr = parseQueryParam(initialUrls.single(), "endTime")
        assertTrue("Expected UTC Z timestamp, got $startStr", startStr.endsWith("Z"))
        assertTrue("Expected UTC Z timestamp, got $endStr", endStr.endsWith("Z"))
        val span = Duration.between(Instant.parse(startStr), Instant.parse(endStr))
        assertTrue("Initial window span $span must be <= 364 days", span <= Duration.ofDays(364))

        val prefsMulti = createTestPreferencesManager(tempFolder, "sc_multi_window")
        prefsMulti.setTeslaCredentials("cid", "csec", jwtWithSc)
        val multiDao = FakeChargingSessionDao()
        val multiUrls = mutableListOf<String>()
        val clientMulti = createMockClient { url, _ ->
            multiUrls.add(url)
            val idx = multiUrls.size
            when (idx) {
                1 -> 200 to """
                    {
                      "response": {
                        "data": [
                          {
                            "sessionId": "w1_s1",
                            "vin": "5YJ3E1EA1PF000001",
                            "siteLocationName": "Recent SC",
                            "chargeStartDateTime": "2026-10-01T12:00:00Z",
                            "chargeStopDateTime": "2026-10-01T12:20:00Z",
                            "fees": [{ "feeType": "CHARGING", "uom": "kwh", "usageBase": 25.0, "netDue": 10.0 }]
                          },
                          {
                            "sessionId": "boundary_s",
                            "vin": "5YJ3E1EA1PF000001",
                            "siteLocationName": "Boundary SC",
                            "chargeStartDateTime": "2025-11-26T12:00:00Z",
                            "chargeStopDateTime": "2025-11-26T12:20:00Z",
                            "fees": [{ "feeType": "CHARGING", "uom": "kwh", "usageBase": 15.0, "netDue": 6.0 }]
                          }
                        ],
                        "hasMore": false
                      }
                    }
                """.trimIndent()
                2 -> 200 to """
                    {
                      "response": {
                        "data": [
                          {
                            "sessionId": "boundary_s",
                            "vin": "5YJ3E1EA1PF000001",
                            "siteLocationName": "Boundary SC",
                            "chargeStartDateTime": "2025-11-26T12:00:00Z",
                            "chargeStopDateTime": "2025-11-26T12:20:00Z",
                            "fees": [{ "feeType": "CHARGING", "uom": "kwh", "usageBase": 15.0, "netDue": 6.0 }]
                          },
                          {
                            "sessionId": "w2_s1",
                            "vin": "5YJ3E1EA1PF000001",
                            "siteLocationName": "Older SC",
                            "chargeStartDateTime": "2025-08-10T12:00:00Z",
                            "chargeStopDateTime": "2025-08-10T12:25:00Z",
                            "fees": [{ "feeType": "CHARGING", "uom": "kwh", "usageBase": 30.0, "netDue": 12.0 }]
                          }
                        ],
                        "hasMore": false
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected request #$idx: $url")
            }
        }
        val resMulti = OdometerSyncService(prefsMulti, FakeCarDao(), FakeOdometerLogDao(), multiDao, clientMulti)
            .syncTeslaSuperchargerCharging(listOf(m3), lookbackDays = 500)
        assertTrue(resMulti is SuperchargerSyncResult.Synced)
        val syncedMulti = resMulti as SuperchargerSyncResult.Synced
        assertEquals(2, multiUrls.size)
        multiUrls.forEach { u ->
            val wSpan = Duration.between(
                Instant.parse(parseQueryParam(u, "startTime")),
                Instant.parse(parseQueryParam(u, "endTime"))
            )
            assertTrue("Window span $wSpan must be <= 364 days", wSpan <= Duration.ofDays(364))
        }
        assertEquals(3, syncedMulti.insertedCount)
        assertEquals(3, syncedMulti.totalSessionsScanned)
        assertEquals(70.0, syncedMulti.totalKwhAdded, 0.01)
        assertEquals(28.0, syncedMulti.totalCostAdded, 0.01)

        val prefs400 = createTestPreferencesManager(tempFolder, "sc_400")
        prefs400.setTeslaCredentials("cid", "csec", jwtWithSc)
        val client400 = createMockClient { _, _ -> 400 to """{"error":"Date range cannot exceed 1 year"}""" }
        val res400 = OdometerSyncService(prefs400, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), client400)
            .syncTeslaSuperchargerCharging(listOf(m3))
        assertTrue(res400 is SuperchargerSyncResult.Error)
        assertEquals(
            "Supercharger sync failed: Tesla (400: Date range cannot exceed 1 year)",
            OdometerSyncService.formatSuperchargerSyncMessage(res400)
        )

        val prefsInc = createTestPreferencesManager(tempFolder, "sc_incremental_latest")
        prefsInc.setTeslaCredentials("cid", "csec", jwtWithSc)
        val latestSessionEnd = Instant.now().minusSeconds(5L * 86_400L).truncatedTo(ChronoUnit.SECONDS)
        val incDao = FakeChargingSessionDao().apply {
            sessions.add(
                ChargingSessionEntity(
                    id = "sc_tesla_existing",
                    carId = m3.id,
                    startDateMs = latestSessionEnd.minusSeconds(1_800L).toEpochMilli(),
                    endDateMs = latestSessionEnd.toEpochMilli(),
                    kwh = 32.0,
                    venue = ElectricityImportParser.VENUE_PUBLIC,
                    directCost = 12.80,
                    chargerName = "Supercharger · Existing",
                    isSummary = false,
                    fingerprint = "fp_existing_sc",
                    uploadBatchId = ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID,
                    uploadedAtMs = latestSessionEnd.toEpochMilli()
                )
            )
        }
        val incUrls = mutableListOf<String>()
        val clientInc = createMockClient { url, _ ->
            incUrls.add(url)
            200 to """{"response":{"data":[],"hasMore":false}}"""
        }
        val resInc = OdometerSyncService(prefsInc, FakeCarDao(), FakeOdometerLogDao(), incDao, clientInc)
            .syncTeslaSuperchargerCharging(listOf(m3))
        assertTrue(resInc is SuperchargerSyncResult.Synced)
        assertEquals(1, incUrls.size)
        val incStart = Instant.parse(parseQueryParam(incUrls.single(), "startTime"))
        val incEnd = Instant.parse(parseQueryParam(incUrls.single(), "endTime"))
        assertEquals(latestSessionEnd.minusSeconds(86_400L), incStart)
        assertTrue(Duration.between(incStart, incEnd) <= Duration.ofDays(7))

        val prefsPeriod = createTestPreferencesManager(tempFolder, "sc_explicit_period")
        prefsPeriod.setTeslaCredentials("cid", "csec", jwtWithSc)
        val periodStart = Instant.parse("2025-08-01T07:00:00Z")
        val periodEnd = Instant.parse("2025-09-01T06:59:59Z")
        val periodUrls = mutableListOf<String>()
        val clientPeriod = createMockClient { url, _ ->
            periodUrls.add(url)
            200 to """{"response":{"data":[],"hasMore":false}}"""
        }
        val resPeriod = OdometerSyncService(prefsPeriod, FakeCarDao(), FakeOdometerLogDao(), incDao, clientPeriod)
            .syncTeslaSuperchargerCharging(
                cars = listOf(m3),
                startInstant = periodStart,
                endInstant = periodEnd
            )
        assertTrue(resPeriod is SuperchargerSyncResult.Synced)
        assertEquals(1, periodUrls.size)
        assertEquals(periodStart, Instant.parse(parseQueryParam(periodUrls.single(), "startTime")))
        assertEquals(periodEnd, Instant.parse(parseQueryParam(periodUrls.single(), "endTime")))
    }
}
