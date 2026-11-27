package com.example.car_logger.telemetry

import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.TeslaSuperchargerSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TeslaSuperchargerParserTest {

    @Test
    fun `parseTeslaSuperchargerHistory prevents usageTier1 double-counting, sums all fee netDue, and skips tiny sessions`() {
        val realWorldJson = """
            {
              "response": {
                "data": [
                  {
                    "sessionId": "88001",
                    "vin": "5YJ3E1EA1PF000001",
                    "vehicleMakeType": "TSLA",
                    "siteLocationName": "Ann Arbor, MI",
                    "chargeStartDateTime": "2026-11-10T14:00:00Z",
                    "chargeStopDateTime": "2026-11-10T14:25:00Z",
                    "fees": [
                      {
                        "feeType": "CHARGING",
                        "uom": "kwh",
                        "currencyCode": "USD",
                        "usageBase": 7.0,
                        "rateBase": 0.39,
                        "totalBase": 2.73,
                        "usageTier1": 8.0,
                        "rateTier1": 0.0,
                        "totalTier1": 0.0,
                        "netDue": 2.73,
                        "totalDue": 2.73
                      },
                      {
                        "feeType": "PARKING",
                        "uom": "min",
                        "currencyCode": "USD",
                        "usageBase": 3.0,
                        "rateBase": 0.50,
                        "netDue": 1.50,
                        "totalDue": 1.50
                      }
                    ]
                  },
                  {
                    "sessionId": "88002",
                    "vin": "3GNKDCRJ8RS123456",
                    "vehicleMakeType": "CHEVROLET",
                    "siteLocationName": "Toledo, OH",
                    "chargeStartDateTime": "2026-11-12T18:00:00Z",
                    "chargeStopDateTime": "2026-11-12T18:40:00Z",
                    "fees": [
                      {
                        "feeType": "CHARGING",
                        "uom": "kwh",
                        "currencyCode": "USD",
                        "usageBase": 20.0,
                        "rateBase": 0.35,
                        "totalBase": 7.00,
                        "usageTier1": 15.5,
                        "rateTier1": 0.42,
                        "totalTier1": 6.51,
                        "netDue": 13.51
                      }
                    ]
                  },
                  {
                    "sessionId": "88003_aborted",
                    "vin": "5YJ3E1EA1PF000001",
                    "siteLocationName": "Aborted Stop",
                    "chargeStartDateTime": "2026-11-13T10:00:00Z",
                    "chargeStopDateTime": "2026-11-13T10:01:00Z",
                    "fees": [
                      { "feeType": "CHARGING", "uom": "kwh", "usageBase": 0.05, "rateBase": 0.39, "netDue": 0.02 }
                    ]
                  }
                ],
                "hasMore": false
              }
            }
        """.trimIndent()

        val (sessions, hasMore) = OdometerSyncService.parseTeslaSuperchargerHistory(realWorldJson)
        assertFalse(hasMore)
        assertEquals(2, sessions.size)

        assertEquals("88001", sessions[0].sessionId)
        assertEquals(7.0, sessions[0].energyKwh, 0.001)
        assertEquals(4.23, sessions[0].totalPaidCost, 0.001)
        assertEquals("Ann Arbor, MI", sessions[0].siteLocationName)

        assertEquals("88002", sessions[1].sessionId)
        assertEquals(35.5, sessions[1].energyKwh, 0.001)
        assertEquals(13.51, sessions[1].totalPaidCost, 0.001)
        assertEquals("CHEVROLET", sessions[1].vehicleMakeType)

        val rootEnvelope = """
            {
              "data": [
                {
                  "chargeSessionId": "99001",
                  "siteLocationName": "Detroit, MI",
                  "chargeStartDateTime": "2026-11-14T12:00:00Z",
                  "chargeStopDateTime": "2026-11-14T12:20:00Z",
                  "fees": [
                    { "feeType": "CHARGING", "uom": "kwh", "usageBase": 25.0, "rateBase": 0.40, "netDue": 10.0 }
                  ]
                }
              ],
              "hasMore": true
            }
        """.trimIndent()
        val (rootSessions, rootHasMore) = OdometerSyncService.parseTeslaSuperchargerHistory(rootEnvelope)
        assertTrue(rootHasMore)
        assertEquals(1, rootSessions.size)
        assertEquals("99001", rootSessions[0].sessionId)
    }

    @Test
    fun `buildSuperchargerSessionEntities attributes Model 3 and Blazer EV and dedupes by ID and overlap`() {
        val m3 = Car(
            id = "car_m3", make = "Tesla", model = "Model 3", year = 2024,
            isEv = true, vin = "5YJ3E1EA1PF000001"
        )
        val blazer = Car(
            id = "car_blazer", make = "Chevrolet", model = "Blazer EV", year = 2024,
            isEv = true, vin = "3GNKDCRJ8RS123456"
        )
        val t1 = Instant.parse("2026-11-10T14:00:00Z").toEpochMilli()
        val t2 = Instant.parse("2026-11-12T18:00:00Z").toEpochMilli()
        val t3 = Instant.parse("2026-11-13T12:00:00Z").toEpochMilli()

        val existingManualReceipt = ChargingSessionEntity(
            id = "manual_sc_receipt",
            carId = "car_m3",
            startDateMs = t3 + 5 * 60_000L,
            endDateMs = t3 + 25 * 60_000L,
            kwh = 41.0,
            venue = ElectricityImportParser.VENUE_PUBLIC,
            directCost = 16.40,
            fingerprint = "fp_manual_receipt"
        )

        val raw = listOf(
            TeslaSuperchargerSession("sc1", "5YJ3E1EA1PF000001", "TSLA", "Ann Arbor", t1, t1 + 1_200_000L, 28.0, 11.20, "USD"),
            TeslaSuperchargerSession("sc2", null, "NON_TESLA", "Toledo", t2, t2 + 2_400_000L, 45.0, 19.80, "USD"),
            TeslaSuperchargerSession("sc3", "5YJ3E1EA1PF000001", "TSLA", "Lansing", t3, t3 + 1_800_000L, 40.0, 16.40, "USD")
        )

        val entities = OdometerSyncService.buildSuperchargerSessionEntities(
            rawSessions = raw,
            cars = listOf(m3, blazer),
            existingSessions = listOf(existingManualReceipt)
        )
        assertEquals(2, entities.size)
        assertEquals("sc_tesla_sc1", entities[0].id)
        assertEquals("car_m3", entities[0].carId)
        assertEquals("Supercharger · Ann Arbor", entities[0].chargerName)
        assertEquals(11.20, entities[0].directCost!!, 0.01)
        assertEquals(ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID, entities[0].uploadBatchId)

        assertEquals("sc_tesla_sc2", entities[1].id)
        assertEquals("car_blazer", entities[1].carId)
        assertEquals("Supercharger · Toledo", entities[1].chargerName)
        assertEquals(19.80, entities[1].directCost!!, 0.01)
    }

    @Test
    fun `buildSuperchargerDateWindows never exceeds 364 days and chunks multi-year lookbacks`() {
        val laZone = ZoneId.of("America/Los_Angeles")
        val maxDuration = Duration.ofDays(OdometerSyncService.TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS.toLong())

        val middayEnd = Instant.parse("2026-11-24T17:53:08.999Z")
        val initialWindows = OdometerSyncService.buildSuperchargerDateWindows(
            endInstant = middayEnd,
            lookbackDays = OdometerSyncService.DEFAULT_SUPERCHARGER_INITIAL_LOOKBACK_DAYS,
            zoneId = laZone
        )
        assertEquals(1, initialWindows.size)
        val (initStart, initEnd) = initialWindows.single()
        assertEquals(Instant.parse("2026-11-24T17:53:08Z"), initEnd)
        assertEquals(maxDuration, Duration.between(initStart, initEnd))
        assertTrue(initStart.atZone(ZoneOffset.UTC).plusYears(1).isAfter(initEnd.atZone(ZoneOffset.UTC)))

        val leapEnd = Instant.parse("2028-03-15T20:15:30Z")
        val leapWindows = OdometerSyncService.buildSuperchargerDateWindows(
            endInstant = leapEnd,
            lookbackDays = 364,
            zoneId = laZone
        )
        assertEquals(1, leapWindows.size)
        val (leapStart, leapEndTrunc) = leapWindows.single()
        assertTrue(Duration.between(leapStart, leapEndTrunc) <= maxDuration)
        assertTrue(leapStart.atZone(ZoneOffset.UTC).plusYears(1).isAfter(leapEndTrunc.atZone(ZoneOffset.UTC)))

        val incWindows = OdometerSyncService.buildSuperchargerDateWindows(
            endInstant = middayEnd,
            lookbackDays = 30,
            zoneId = laZone
        )
        assertEquals(1, incWindows.size)
        val (incStart, incEnd) = incWindows.single()
        val expectedMidnight = middayEnd.atZone(laZone).minusDays(30).toLocalDate().atStartOfDay(laZone).toInstant()
        assertEquals(expectedMidnight, incStart)
        assertTrue(Duration.between(incStart, incEnd) <= maxDuration)

        for (days in listOf(365, 500, 730)) {
            val windows = OdometerSyncService.buildSuperchargerDateWindows(
                endInstant = middayEnd,
                lookbackDays = days,
                zoneId = laZone
            )
            assertTrue("Expected multiple windows for $days days", windows.size >= 2)
            assertEquals(Instant.parse("2026-11-24T17:53:08Z"), windows.first().second)
            assertEquals(
                Instant.parse("2026-11-24T17:53:08Z").minusSeconds(days.toLong() * 86_400L),
                windows.last().first
            )
            for (i in windows.indices) {
                val (wStart, wEnd) = windows[i]
                val span = Duration.between(wStart, wEnd)
                assertTrue("Window $i span must be > 0", span > Duration.ZERO)
                assertTrue("Window $i span $span exceeded max $maxDuration", span <= maxDuration)
                if (i > 0) {
                    assertEquals(windows[i - 1].first, wEnd)
                }
            }
        }
    }

    @Test
    fun `resolveIncrementalSuperchargerWindows and Insights period bounds handle initial, latest-date, stale, and full-year ranges`() {
        val laZone = ZoneId.of("America/Los_Angeles")
        val nowInstant = Instant.parse("2026-11-24T18:15:00Z")
        val maxDuration = Duration.ofDays(OdometerSyncService.TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS.toLong())

        val homeOnlySession = ChargingSessionEntity(
            id = "wc_1",
            carId = "car_m3",
            startDateMs = nowInstant.minusSeconds(2L * 86_400L).toEpochMilli(),
            endDateMs = nowInstant.minusSeconds(2L * 86_400L).toEpochMilli(),
            kwh = 20.0,
            venue = ElectricityImportParser.VENUE_HOME,
            directCost = null,
            chargerName = "Wall Connector",
            isSummary = false,
            fingerprint = "fp_wc_1",
            uploadBatchId = ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID,
            uploadedAtMs = nowInstant.toEpochMilli()
        )
        val initialWindows = OdometerSyncService.resolveIncrementalSuperchargerWindows(
            existingSessions = listOf(homeOnlySession),
            nowInstant = nowInstant,
            zoneId = laZone
        )
        assertEquals(1, initialWindows.size)
        assertEquals(maxDuration, Duration.between(initialWindows.single().first, initialWindows.single().second))

        val latestScEnd = Instant.parse("2026-11-10T20:00:00Z")
        val recentScSession = homeOnlySession.copy(
            id = "sc_tesla_1",
            startDateMs = latestScEnd.minusSeconds(1_200L).toEpochMilli(),
            endDateMs = latestScEnd.toEpochMilli(),
            venue = ElectricityImportParser.VENUE_PUBLIC,
            uploadBatchId = ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID
        )
        val incrementalWindows = OdometerSyncService.resolveIncrementalSuperchargerWindows(
            existingSessions = listOf(homeOnlySession, recentScSession),
            nowInstant = nowInstant,
            zoneId = laZone
        )
        assertEquals(1, incrementalWindows.size)
        assertEquals(Instant.parse("2026-11-09T20:00:00Z"), incrementalWindows.single().first)
        assertEquals(nowInstant, incrementalWindows.single().second)

        val staleScSession = recentScSession.copy(
            id = "sc_tesla_old",
            startDateMs = Instant.parse("2025-05-01T12:00:00Z").toEpochMilli(),
            endDateMs = Instant.parse("2025-05-01T12:30:00Z").toEpochMilli()
        )
        val staleWindows = OdometerSyncService.resolveIncrementalSuperchargerWindows(
            existingSessions = listOf(staleScSession),
            nowInstant = nowInstant,
            zoneId = laZone
        )
        assertEquals(1, staleWindows.size)
        assertEquals(maxDuration, Duration.between(staleWindows.single().first, staleWindows.single().second))

        val (weekStartMs, weekEndMs) = InsightsCalculationEngine.resolvePeriodBounds(
            granularity = InsightsTimeGranularity.WEEKLY,
            periodOffset = 0,
            nowMs = nowInstant.toEpochMilli(),
            zoneId = laZone
        )
        val weekWindows = OdometerSyncService.buildSuperchargerDateWindowsForRange(
            startInstant = Instant.ofEpochMilli(weekStartMs),
            endInstant = Instant.ofEpochMilli(weekEndMs),
            nowInstant = nowInstant
        )
        assertEquals(1, weekWindows.size)
        assertEquals(LocalDate.of(2026, 11, 23).atStartOfDay(laZone).toInstant(), weekWindows.single().first)
        assertEquals(nowInstant, weekWindows.single().second)

        val (sepStartMs, sepEndMs) = InsightsCalculationEngine.resolvePeriodBounds(
            granularity = InsightsTimeGranularity.MONTHLY,
            periodOffset = -2,
            nowMs = nowInstant.toEpochMilli(),
            zoneId = laZone
        )
        val sepWindows = OdometerSyncService.buildSuperchargerDateWindowsForRange(
            startInstant = Instant.ofEpochMilli(sepStartMs),
            endInstant = Instant.ofEpochMilli(sepEndMs),
            nowInstant = nowInstant
        )
        assertEquals(1, sepWindows.size)
        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(laZone).toInstant(), sepWindows.single().first)
        assertEquals(
            LocalDate.of(2026, 9, 30).atTime(23, 59, 59).atZone(laZone).toInstant(),
            sepWindows.single().second
        )

        val (yr2024StartMs, yr2024EndMs) = InsightsCalculationEngine.resolvePeriodBounds(
            granularity = InsightsTimeGranularity.YTD,
            periodOffset = -2,
            nowMs = nowInstant.toEpochMilli(),
            zoneId = laZone
        )
        val yr2024Windows = OdometerSyncService.buildSuperchargerDateWindowsForRange(
            startInstant = Instant.ofEpochMilli(yr2024StartMs),
            endInstant = Instant.ofEpochMilli(yr2024EndMs),
            nowInstant = nowInstant
        )
        assertEquals(2, yr2024Windows.size)
        yr2024Windows.forEach { (wStart, wEnd) ->
            assertTrue(Duration.between(wStart, wEnd) <= maxDuration)
        }
        assertEquals(LocalDate.of(2024, 1, 1).atStartOfDay(laZone).toInstant(), yr2024Windows.last().first)
        assertEquals(
            LocalDate.of(2024, 12, 31).atTime(23, 59, 59).atZone(laZone).toInstant(),
            yr2024Windows.first().second
        )
    }
}
