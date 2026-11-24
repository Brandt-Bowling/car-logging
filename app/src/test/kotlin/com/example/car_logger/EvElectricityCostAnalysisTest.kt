package com.example.car_logger

import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.GeminiAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EvElectricityCostAnalysisTest {

    private val zoneId = ZoneId.systemDefault()

    private fun startMs(isoDate: String): Long =
        LocalDate.parse(isoDate).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun endMs(isoDate: String): Long =
        LocalDate.parse(isoDate).atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

    private fun tsAtHour(isoDate: String, hour: Int): Long =
        LocalDate.parse(isoDate).atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun variableBlendedRate_subtractsFixedCharges_andHandlesEdgeCases() {
        // ($150.00 - $18.00) / 880 kWh = $0.15/kWh
        val rate = ElectricityImportParser.calculateVariableBlendedRate(
            totalAmount = 150.0,
            fixedCharges = 18.0,
            totalKwh = 880.0
        )
        assertEquals(0.15, rate, 0.0001)

        // Zero kWh safely returns 0.0
        assertEquals(
            0.0,
            ElectricityImportParser.calculateVariableBlendedRate(100.0, 10.0, 0.0),
            0.0001
        )

        // Fixed charges greater than total bill clamps volumetric portion to 0.0
        assertEquals(
            0.0,
            ElectricityImportParser.calculateVariableBlendedRate(12.0, 20.0, 100.0),
            0.0001
        )
    }

    @Test
    fun csvParser_andVenueClassification_andFingerprintDeduplication() {
        val csv = """
            Start Date,Energy (kWh),Charger Location,Total Cost
            2026-11-04,32.5,Garage Wall Connector,
            2026-11-05,40.0,Tesla Supercharger - Highway 101,${'$'}16.80
        """.trimIndent()

        val parsed = ElectricityImportParser.parseChargerCsv("charge_export.csv", csv)
        assertEquals(2, parsed.size)

        assertEquals("2026-11-04", parsed[0].startDateIso)
        assertEquals(32.5, parsed[0].kwh, 0.01)
        assertEquals("HOME", parsed[0].venue)

        assertEquals("2026-11-05", parsed[1].startDateIso)
        assertEquals(40.0, parsed[1].kwh, 0.01)
        assertEquals("PUBLIC", parsed[1].venue)
        assertEquals(16.80, parsed[1].directCost ?: 0.0, 0.01)

        assertEquals("PUBLIC", ElectricityImportParser.classifyVenue("Electrify America", 0.0))
        assertEquals("HOME", ElectricityImportParser.classifyVenue("Home Emporia", null))
        assertEquals("PUBLIC", ElectricityImportParser.classifyVenue("Unknown Station", 9.50))

        val fp1 = ElectricityImportParser.buildFingerprint(
            startMs("2026-11-04"),
            endMs("2026-11-04"),
            32.5,
            "HOME",
            "car_1",
            zoneId
        )
        val fp2 = ElectricityImportParser.buildFingerprint(
            tsAtHour("2026-11-04", 19),
            tsAtHour("2026-11-04", 22),
            32.51,
            "home",
            "car_1",
            zoneId
        )
        val fpDifferentCar = ElectricityImportParser.buildFingerprint(
            startMs("2026-11-04"),
            endMs("2026-11-04"),
            32.5,
            "HOME",
            "car_2",
            zoneId
        )
        assertEquals(fp1, fp2)
        assertNotEquals(fp1, fpDifferentCar)
    }

    @Test
    fun billMatching_respectsOverrideRate_overlappingUploadRecency_andFallbackChain() {
        val ev = Car(id = "ev_1", year = 2024, make = "Tesla", model = "Model Y", isEv = true)

        // Two overlapping bills covering Nov 2026: newer uploadedAtMs (bill_2 with custom overrideRate = 0.12) wins over older bill_1
        val olderBill = UtilityBillEntity(
            id = "bill_1",
            providerName = "PG&E",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-30"),
            totalKwh = 500.0,
            totalAmount = 115.0,
            fixedCharges = 15.0,
            calculatedRate = 0.20,
            overrideRate = null,
            effectiveRate = 0.20,
            uploadedAtMs = 1_000L
        )
        val newerOverrideBill = UtilityBillEntity(
            id = "bill_2",
            providerName = "PG&E EV-TOU",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-15"),
            totalKwh = 500.0,
            totalAmount = 115.0,
            fixedCharges = 15.0,
            calculatedRate = 0.20,
            overrideRate = 0.12,
            effectiveRate = 0.12,
            uploadedAtMs = 5_000L
        )

        // Session on Nov 10 overlaps both -> newerOverrideBill ($0.12)
        // Session on Nov 20 only matches olderBill ($0.20)
        val sessions = listOf(
            ChargingSessionEntity(
                id = "s1",
                carId = "ev_1",
                startDateMs = tsAtHour("2026-11-10", 12),
                endDateMs = tsAtHour("2026-11-10", 14),
                kwh = 50.0,
                venue = "HOME",
                fingerprint = "fp_s1"
            ),
            ChargingSessionEntity(
                id = "s2",
                carId = "ev_1",
                startDateMs = tsAtHour("2026-11-20", 12),
                endDateMs = tsAtHour("2026-11-20", 14),
                kwh = 50.0,
                venue = "HOME",
                fingerprint = "fp_s2"
            )
        )

        val stateMatched = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-11-25", 12),
            utilityBills = listOf(olderBill, newerOverrideBill),
            chargingSessions = sessions,
            defaultElectricityRate = 0.16f
        )

        // 50 kWh * $0.12 + 50 kWh * $0.20 = $6.00 + $10.00 = $16.00 (blended $0.16/kWh across month)
        assertTrue(stateMatched.electricityCost.hasHomeData)
        assertEquals(100.0, stateMatched.electricityCost.homeTotalKwh, 0.01)
        assertEquals(16.0, stateMatched.electricityCost.homeTotalCost, 0.01)
        assertFalse(stateMatched.electricityCost.homeIsEstimatedRate)

        // Session in Dec (after all bills) falls back to latest bill by endDateMs (olderBill ends Nov 30 -> $0.20)
        val decSession = listOf(
            ChargingSessionEntity(
                id = "s_dec",
                carId = "ev_1",
                startDateMs = tsAtHour("2026-12-05", 12),
                endDateMs = tsAtHour("2026-12-05", 14),
                kwh = 40.0,
                venue = "HOME",
                fingerprint = "fp_dec"
            )
        )
        val stateFallbackLatestBill = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-12-10", 12),
            utilityBills = listOf(olderBill, newerOverrideBill),
            chargingSessions = decSession,
            defaultElectricityRate = 0.16f
        )
        assertEquals(8.0, stateFallbackLatestBill.electricityCost.homeTotalCost, 0.01)
        assertTrue(stateFallbackLatestBill.electricityCost.homeIsEstimatedRate)
        assertFalse(stateFallbackLatestBill.electricityCost.homeHasMatchedBill)

        // With NO bills at all, falls back to defaultElectricityRate ($0.16/kWh -> 40 * 0.16 = $6.40)
        val stateDefaultRate = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-12-10", 12),
            utilityBills = emptyList(),
            chargingSessions = decSession,
            defaultElectricityRate = 0.16f
        )
        assertEquals(6.40, stateDefaultRate.electricityCost.homeTotalCost, 0.01)
        assertEquals(0.16, stateDefaultRate.electricityCost.homeEffectiveRate, 0.001)
        assertTrue(stateDefaultRate.electricityCost.homeIsEstimatedRate)
    }

    @Test
    fun monthlySummary_proratesEvenlyAcrossWeekMonthAndYear() {
        val ev = Car(id = "ev_1", year = 2024, make = "Rivian", model = "R1S", isEv = true)
        // Nov 2026 has 30 days. 300 kWh summary -> 10.0 kWh/day
        val novSummary = listOf(
            ChargingSessionEntity(
                id = "sum_nov",
                carId = "ev_1",
                startDateMs = startMs("2026-11-01"),
                endDateMs = endMs("2026-11-30"),
                kwh = 300.0,
                venue = "HOME",
                isSummary = true,
                fingerprint = "fp_sum_nov"
            )
        )

        // Week of Mon Nov 9 – Sun Nov 15 (7 days wholly inside Nov): expects 70.0 kWh
        val weeklyState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-11-11", 12),
            chargingSessions = novSummary,
            defaultElectricityRate = 0.20f
        )
        assertEquals(70.0, weeklyState.electricityCost.homeTotalKwh, 0.01)
        assertEquals(14.0, weeklyState.electricityCost.homeTotalCost, 0.01)

        // Monthly Nov 2026: expects all 300.0 kWh
        val monthlyState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-11-11", 12),
            chargingSessions = novSummary,
            defaultElectricityRate = 0.20f
        )
        assertEquals(300.0, monthlyState.electricityCost.homeTotalKwh, 0.01)

        // Year 2026: expects all 300.0 kWh
        val yearlyState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.YTD,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-11-11", 12),
            chargingSessions = novSummary,
            defaultElectricityRate = 0.20f
        )
        assertEquals(300.0, yearlyState.electricityCost.homeTotalKwh, 0.01)
    }

    @Test
    fun sharedChargerAttribution_splitsByEvMiles_fallsBackToEqualOnZeroMiles_andExcludesGasCars() {
        val ev1 = Car(id = "ev1", year = 2023, make = "Tesla", model = "Model 3", isEv = true)
        val ev2 = Car(id = "ev2", year = 2023, make = "Chevy", model = "Bolt EUV", isEv = true)
        val gasCar = Car(id = "ice1", year = 2021, make = "Honda", model = "Civic", isEv = false)

        val sharedSession = listOf(
            ChargingSessionEntity(
                id = "shared_1",
                carId = null,
                startDateMs = tsAtHour("2026-11-11", 10),
                endDateMs = tsAtHour("2026-11-11", 14),
                kwh = 100.0,
                venue = "HOME",
                fingerprint = "fp_shared_1"
            )
        )

        // 1) Proportional split across two EVs: ev1 drove 300 mi (75%), ev2 drove 100 mi (25%)
        val logsTwoEvs = listOf(
            OdometerLogEntity("l1", "ev1", 10000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l2", "ev1", 10300, tsAtHour("2026-11-14", 16), "TELEMETRY"),
            OdometerLogEntity("l3", "ev2", 5000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l4", "ev2", 5100, tsAtHour("2026-11-14", 16), "TELEMETRY")
        )
        val twoEvState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1, ev2),
            allLogs = logsTwoEvs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = sharedSession,
            defaultElectricityRate = 0.16f
        )
        assertEquals(75.0, twoEvState.electricityCost.homeCar1Kwh, 0.01)
        assertEquals(25.0, twoEvState.electricityCost.homeCar2Kwh, 0.01)
        assertEquals(75, twoEvState.electricityCost.homeCar1SharePct)
        assertEquals(25, twoEvState.electricityCost.homeCar2SharePct)
        assertEquals(4.0, twoEvState.electricityCost.homeMilesPerKwh ?: 0.0, 0.05)

        // 2) Zero EV miles fallback: no telemetry -> equal 50% / 50% split
        val zeroMilesState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1, ev2),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = sharedSession,
            defaultElectricityRate = 0.16f
        )
        assertFalse(zeroMilesState.hasOdometerData)
        assertEquals(50.0, zeroMilesState.electricityCost.homeCar1Kwh, 0.01)
        assertEquals(50.0, zeroMilesState.electricityCost.homeCar2Kwh, 0.01)
        assertEquals(50, zeroMilesState.electricityCost.homeCar1SharePct)
        assertEquals(50, zeroMilesState.electricityCost.homeCar2SharePct)

        // 3) Mixed garage (1 EV + 1 Gas car): 100% goes to ev1, 0% to gasCar even though gasCar drove 500 mi
        val mixedLogs = listOf(
            OdometerLogEntity("m1", "ev1", 10000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("m2", "ev1", 10200, tsAtHour("2026-11-14", 16), "TELEMETRY"),
            OdometerLogEntity("m3", "ice1", 20000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("m4", "ice1", 20500, tsAtHour("2026-11-14", 16), "TELEMETRY")
        )
        val mixedState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1, gasCar),
            allLogs = mixedLogs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = sharedSession,
            defaultElectricityRate = 0.16f
        )
        assertEquals(100.0, mixedState.electricityCost.homeCar1Kwh, 0.01)
        assertEquals(0.0, mixedState.electricityCost.homeCar2Kwh, 0.01)
        // Efficiency counts only ev1's 200 miles / 100 kWh = 2.0 mi/kWh (excludes Civic gas miles)
        assertEquals(2.0, mixedState.electricityCost.homeMilesPerKwh ?: 0.0, 0.05)
    }

    @Test
    fun homeVsPublicSeparation_andSavedVsGasNetSavings() {
        val ev = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val logs = listOf(
            OdometerLogEntity("l1", "ev1", 1000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l2", "ev1", 1560, tsAtHour("2026-11-14", 16), "TELEMETRY") // 560 miles -> 20 gal @ 28mpg -> $73.00 @ $3.65/gal
        )
        val sessions = listOf(
            ChargingSessionEntity(
                id = "h1",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-10", 12),
                endDateMs = tsAtHour("2026-11-10", 14),
                kwh = 100.0,
                venue = "HOME",
                fingerprint = "fp_h1"
            ),
            ChargingSessionEntity(
                id = "p1",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-12", 12),
                endDateMs = tsAtHour("2026-11-12", 13),
                kwh = 50.0,
                venue = "PUBLIC",
                directCost = 20.0,
                fingerprint = "fp_p1"
            )
        )

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = sessions,
            defaultElectricityRate = 0.15f
        )

        assertTrue(state.electricityCost.hasHomeData)
        assertTrue(state.electricityCost.hasPublicData)
        assertEquals(15.0, state.electricityCost.homeTotalCost, 0.01)
        assertEquals(20.0, state.electricityCost.publicTotalCost, 0.01)
        assertEquals(0.40, state.electricityCost.publicEffectiveRate, 0.001)
        assertEquals(35.0, state.electricityCost.combinedTotalCost, 0.01)

        // Gas cost: 560 mi / 28 MPG * $3.65 = $73.00; EV total = $35.00; Saved vs. Gas = $38.00
        assertEquals(73.0, state.gasComparison.totalCost, 0.05)
        assertNotNull(state.gasComparison.netFuelSavings)
        assertEquals(38.0, state.gasComparison.netFuelSavings ?: 0.0, 0.1)
    }

    @Test
    fun noOdometerTelemetry_stillComputesElectricityCost_andAllowsPreviousNavigation() {
        val ev = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val pastSession = listOf(
            ChargingSessionEntity(
                id = "old_1",
                carId = "ev1",
                startDateMs = tsAtHour("2026-10-10", 12),
                endDateMs = tsAtHour("2026-10-10", 14),
                kwh = 60.0,
                venue = "HOME",
                fingerprint = "fp_old_1"
            )
        )

        val stateCurrentMonth = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = pastSession,
            defaultElectricityRate = 0.16f
        )
        assertFalse(stateCurrentMonth.hasOdometerData)
        assertTrue(stateCurrentMonth.electricityCost.hasAnyData)
        assertTrue(stateCurrentMonth.canGoPrevious)

        val statePastMonth = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = -1,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = pastSession,
            defaultElectricityRate = 0.16f
        )
        assertTrue(statePastMonth.canGoNext)
        assertTrue(statePastMonth.electricityCost.hasHomeData)
        assertEquals(9.60, statePastMonth.electricityCost.homeTotalCost, 0.01)
    }

    @Test
    fun undoLastUpload_resolvesExplicitBatch_andLegacyRowidBatch_andConvertsForRetag() {
        // 1. Legacy sessions (uploadBatchId == null, uploadedAtMs backfilled from rowid)
        // Earlier upload tagged to car_1 (rowids 1, 2) followed by untagged upload (carId = null, rowids 3, 4)
        val legacyTagged1 = ChargingSessionEntity(
            id = "leg_1",
            carId = "car_1",
            startDateMs = startMs("2026-10-01"),
            endDateMs = endMs("2026-10-01"),
            kwh = 25.0,
            venue = "HOME",
            fingerprint = "fp_leg_1",
            uploadBatchId = null,
            uploadedAtMs = 1L
        )
        val legacyTagged2 = ChargingSessionEntity(
            id = "leg_2",
            carId = "car_1",
            startDateMs = startMs("2026-10-02"),
            endDateMs = endMs("2026-10-02"),
            kwh = 30.0,
            venue = "HOME",
            fingerprint = "fp_leg_2",
            uploadBatchId = null,
            uploadedAtMs = 2L
        )
        val legacyUntagged1 = ChargingSessionEntity(
            id = "leg_3",
            carId = null,
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-01"),
            kwh = 40.0,
            venue = "HOME",
            chargerName = "Wall Connector",
            fingerprint = "fp_leg_3",
            uploadBatchId = null,
            uploadedAtMs = 3L
        )
        val legacyUntagged2 = ChargingSessionEntity(
            id = "leg_4",
            carId = null,
            startDateMs = startMs("2026-11-05"),
            endDateMs = endMs("2026-11-05"),
            kwh = 45.5,
            venue = "PUBLIC",
            directCost = 18.20,
            chargerName = "Supercharger",
            fingerprint = "fp_leg_4",
            uploadBatchId = null,
            uploadedAtMs = 4L
        )

        val legacyAll = listOf(legacyTagged1, legacyTagged2, legacyUntagged1, legacyUntagged2)
        val legacyLastBatch = ElectricityImportParser.resolveLastUploadBatch(legacyAll)
        assertEquals(listOf("leg_4", "leg_3"), legacyLastBatch.map { it.id })

        // Convert undone batch back into ParsedChargerImportResult for re-tagging with a car
        val retagImport = ElectricityImportParser.sessionsToImportResult(legacyLastBatch, "last upload", zoneId)
        assertEquals("undo", retagImport.source)
        assertEquals(2, retagImport.sessions.size)
        assertEquals("2026-11-05", retagImport.sessions[0].startDateIso)
        assertEquals(45.5, retagImport.sessions[0].kwh, 0.01)
        assertEquals("PUBLIC", retagImport.sessions[0].venue)
        assertEquals(18.20, retagImport.sessions[0].directCost ?: 0.0, 0.01)
        assertEquals("2026-11-01", retagImport.sessions[1].startDateIso)
        assertEquals(40.0, retagImport.sessions[1].kwh, 0.01)
        assertEquals("HOME", retagImport.sessions[1].venue)

        // 2. Explicit uploadBatchId sessions override older batches cleanly
        val newBatch1 = ChargingSessionEntity(
            id = "new_1",
            carId = null,
            startDateMs = startMs("2026-11-10"),
            endDateMs = endMs("2026-11-10"),
            kwh = 20.0,
            venue = "HOME",
            fingerprint = "fp_new_1",
            uploadBatchId = "batch_a",
            uploadedAtMs = 1_000_000L
        )
        val newBatch2a = ChargingSessionEntity(
            id = "new_2a",
            carId = null,
            startDateMs = startMs("2026-11-11"),
            endDateMs = endMs("2026-11-11"),
            kwh = 22.0,
            venue = "HOME",
            fingerprint = "fp_new_2a",
            uploadBatchId = "batch_b",
            uploadedAtMs = 2_000_000L
        )
        val newBatch2b = ChargingSessionEntity(
            id = "new_2b",
            carId = null,
            startDateMs = startMs("2026-11-12"),
            endDateMs = endMs("2026-11-12"),
            kwh = 28.0,
            venue = "HOME",
            fingerprint = "fp_new_2b",
            uploadBatchId = "batch_b",
            uploadedAtMs = 2_000_000L
        )

        val withExplicitBatches = legacyAll + listOf(newBatch1, newBatch2a, newBatch2b)
        val resolvedBatchB = ElectricityImportParser.resolveLastUploadBatch(withExplicitBatches)
        assertEquals(listOf("new_2b", "new_2a"), resolvedBatchB.map { it.id })

        // After undoing batch_b, batch_a becomes the next undoable upload
        val afterUndoingB = withExplicitBatches.filterNot { it.uploadBatchId == "batch_b" }
        val resolvedBatchA = ElectricityImportParser.resolveLastUploadBatch(afterUndoingB)
        assertEquals(listOf("new_1"), resolvedBatchA.map { it.id })

        // 3. Automated Wall Connector sessions (even with newer uploadedAtMs) are never selected by resolveLastUploadBatch
        val autoWallConnectorSession = ChargingSessionEntity(
            id = "twc_1457768_1763200000",
            carId = "car_1",
            startDateMs = startMs("2026-11-15"),
            endDateMs = endMs("2026-11-15"),
            kwh = 38.4,
            venue = "HOME",
            chargerName = "Tesla Wall Connector (Gen 3)",
            fingerprint = "fp_twc_1",
            uploadBatchId = ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID,
            uploadedAtMs = 9_999_999L
        )
        val mixedManualAndAuto = afterUndoingB + autoWallConnectorSession
        val resolvedIgnoringAuto = ElectricityImportParser.resolveLastUploadBatch(mixedManualAndAuto)
        assertEquals(listOf("new_1"), resolvedIgnoringAuto.map { it.id })
    }

    @Test
    fun periodChargingLogs_filtersByPeriodVenueAndVehicle_andMatchesAttributedTotals() {
        val ev1 = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val ev2 = Car(id = "ev2", year = 2024, make = "Chevy", model = "Blazer EV", isEv = true)

        val logs = listOf(
            OdometerLogEntity("l1", "ev1", 1000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l2", "ev1", 1300, tsAtHour("2026-11-14", 16), "TELEMETRY"), // 300 mi (75%)
            OdometerLogEntity("l3", "ev2", 2000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l4", "ev2", 2100, tsAtHour("2026-11-14", 16), "TELEMETRY")  // 100 mi (25%)
        )

        val billNov = UtilityBillEntity(
            id = "bill_nov",
            providerName = "PG&E",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-30"),
            totalKwh = 500.0,
            totalAmount = 100.0,
            fixedCharges = 0.0,
            calculatedRate = 0.20,
            overrideRate = null,
            effectiveRate = 0.20,
            uploadedAtMs = 1_000L
        )

        val sessions = listOf(
            // Inside Nov 9-15 week: ev1 dedicated HOME
            ChargingSessionEntity(
                id = "h_ev1_nov",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-12", 20),
                endDateMs = tsAtHour("2026-11-12", 22),
                kwh = 40.0,
                venue = "HOME",
                fingerprint = "fp_h_ev1_nov"
            ),
            // Inside Nov 9-15 week: shared HOME (carId = null -> 75% ev1 / 25% ev2)
            ChargingSessionEntity(
                id = "h_shared_nov",
                carId = null,
                startDateMs = tsAtHour("2026-11-10", 18),
                endDateMs = tsAtHour("2026-11-10", 21),
                kwh = 100.0,
                venue = "HOME",
                fingerprint = "fp_h_shared_nov"
            ),
            // Inside Nov 9-15 week: ev2 dedicated HOME (excluded when filtering for ev1)
            ChargingSessionEntity(
                id = "h_ev2_nov",
                carId = "ev2",
                startDateMs = tsAtHour("2026-11-11", 19),
                endDateMs = tsAtHour("2026-11-11", 21),
                kwh = 30.0,
                venue = "HOME",
                fingerprint = "fp_h_ev2_nov"
            ),
            // Inside Nov 9-15 week: ev1 PUBLIC
            ChargingSessionEntity(
                id = "p_ev1_nov",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-13", 13),
                endDateMs = tsAtHour("2026-11-13", 14),
                kwh = 50.0,
                venue = "PUBLIC",
                directCost = 22.50,
                fingerprint = "fp_p_ev1_nov"
            ),
            // Outside Nov week, inside October: ev1 HOME
            ChargingSessionEntity(
                id = "h_ev1_oct",
                carId = "ev1",
                startDateMs = tsAtHour("2026-10-15", 19),
                endDateMs = tsAtHour("2026-10-15", 22),
                kwh = 60.0,
                venue = "HOME",
                fingerprint = "fp_h_ev1_oct"
            )
        )

        // 1. All vehicles, current week (Nov 9-15)
        val weekAllState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1, ev2),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(billNov),
            chargingSessions = sessions,
            defaultElectricityRate = 0.16f
        )
        assertEquals(
            listOf("h_ev1_nov", "h_ev2_nov", "h_shared_nov"),
            weekAllState.electricityCost.homePeriodLogs.map { it.session.id }
        )
        assertEquals(listOf("p_ev1_nov"), weekAllState.electricityCost.publicPeriodLogs.map { it.session.id })
        assertTrue(weekAllState.electricityCost.homePeriodLogs.none { it.isSharedSplit })
        assertEquals(
            weekAllState.electricityCost.homeTotalKwh,
            weekAllState.electricityCost.homePeriodLogs.sumOf { it.attributedKwh },
            0.001
        )
        assertEquals(
            weekAllState.electricityCost.homeTotalCost,
            weekAllState.electricityCost.homePeriodLogs.sumOf { it.attributedCost },
            0.001
        )

        // 2. Single vehicle (ev1), current week -> excludes ev2's session and splits shared session 75%
        val weekEv1State = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1, ev2),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "ev1",
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(billNov),
            chargingSessions = sessions,
            defaultElectricityRate = 0.16f
        )
        assertEquals(
            listOf("h_ev1_nov", "h_shared_nov"),
            weekEv1State.electricityCost.homePeriodLogs.map { it.session.id }
        )
        val sharedItem = weekEv1State.electricityCost.homePeriodLogs.last()
        assertTrue(sharedItem.isSharedSplit)
        assertEquals(75.0, sharedItem.attributedKwh, 0.01)
        assertEquals(15.0, sharedItem.attributedCost, 0.01)
        assertFalse(sharedItem.isEstimatedRate)
        assertEquals(
            weekEv1State.electricityCost.homeTotalKwh,
            weekEv1State.electricityCost.homePeriodLogs.sumOf { it.attributedKwh },
            0.001
        )
        assertEquals(
            weekEv1State.electricityCost.homeTotalCost,
            weekEv1State.electricityCost.homePeriodLogs.sumOf { it.attributedCost },
            0.001
        )

        // 3. Previous month (October, periodOffset = -1) -> only October log appears
        val prevMonthState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1, ev2),
            allLogs = logs,
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = -1,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(billNov),
            chargingSessions = sessions,
            defaultElectricityRate = 0.16f
        )
        assertEquals(listOf("h_ev1_oct"), prevMonthState.electricityCost.homePeriodLogs.map { it.session.id })
        assertTrue(prevMonthState.electricityCost.publicPeriodLogs.isEmpty())

        // 4. Summary session proration flag in Weekly vs Monthly
        val novSummary = listOf(
            ChargingSessionEntity(
                id = "sum_nov",
                carId = "ev1",
                startDateMs = startMs("2026-11-01"),
                endDateMs = endMs("2026-11-30"),
                kwh = 300.0,
                venue = "HOME",
                isSummary = true,
                fingerprint = "fp_sum_nov_2"
            )
        )
        val weeklySummaryState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-11", 12),
            chargingSessions = novSummary,
            defaultElectricityRate = 0.20f
        )
        assertEquals(1, weeklySummaryState.electricityCost.homePeriodLogs.size)
        assertTrue(weeklySummaryState.electricityCost.homePeriodLogs.first().isProratedSummary)
        assertEquals(70.0, weeklySummaryState.electricityCost.homePeriodLogs.first().attributedKwh, 0.01)

        val monthlySummaryState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-11", 12),
            chargingSessions = novSummary,
            defaultElectricityRate = 0.20f
        )
        assertEquals(1, monthlySummaryState.electricityCost.homePeriodLogs.size)
        assertFalse(monthlySummaryState.electricityCost.homePeriodLogs.first().isProratedSummary)
        assertEquals(300.0, monthlySummaryState.electricityCost.homePeriodLogs.first().attributedKwh, 0.01)
    }

    @Test
    fun touOffPeakRate_combinesSupplyAndDistribution_andHandlesMissingOrZero() {
        assertEquals(
            0.15,
            ElectricityImportParser.calculateTouOffPeakRate(0.082, 0.068)!!,
            0.0001
        )
        assertEquals(
            0.095,
            ElectricityImportParser.calculateTouOffPeakRate(0.095, null)!!,
            0.0001
        )
        assertEquals(
            0.095,
            ElectricityImportParser.calculateTouOffPeakRate(0.095, -0.02)!!,
            0.0001
        )
        assertNull(ElectricityImportParser.calculateTouOffPeakRate(null, 0.068))
        assertNull(ElectricityImportParser.calculateTouOffPeakRate(0.0, 0.068))
    }

    @Test
    fun parseUtilityBillJsonResponse_parsesTouUnbundledAndFlatNullPayloads() {
        val touJson = """
            {
              "providerName": "DTE Energy",
              "billingStartDate": "2026-10-01",
              "billingEndDate": "2026-10-31",
              "totalKwh": 1250.0,
              "totalAmount": 195.40,
              "fixedCharges": 12.50,
              "isTouPlan": true,
              "offPeakSupplyRate": 0.082,
              "onPeakSupplyRate": 0.185,
              "distributionRatePerKwh": 0.068,
              "offPeakKwh": 980.0,
              "onPeakKwh": 270.0,
              "breakdownItems": [
                { "label": "Off-Peak Power Supply", "category": "OFF_PEAK_SUPPLY", "kwh": 980.0, "ratePerKwh": 0.082, "amount": 80.36 },
                { "label": "Distribution Delivery", "category": "delivery", "kwh": 1250.0, "ratePerKwh": 0.068, "amount": 85.00 },
                { "label": "Customer Charge", "category": "FIXED", "kwh": null, "ratePerKwh": null, "amount": 12.50 }
              ]
            }
        """.trimIndent()

        val touDraft = GeminiAiService.parseUtilityBillJsonResponse(touJson, "dte_oct.pdf")
        assertEquals("DTE Energy", touDraft.providerName)
        assertEquals("2026-10-01", touDraft.startDateIso)
        assertEquals("2026-10-31", touDraft.endDateIso)
        assertEquals(1250.0, touDraft.totalKwh, 0.001)
        assertEquals(195.40, touDraft.totalAmount, 0.001)
        assertEquals(12.50, touDraft.fixedCharges, 0.001)
        assertTrue(touDraft.isTouPlan)
        assertEquals(0.082, touDraft.offPeakSupplyRate!!, 0.0001)
        assertEquals(0.185, touDraft.onPeakSupplyRate!!, 0.0001)
        assertEquals(0.068, touDraft.distributionRatePerKwh!!, 0.0001)
        assertEquals(980.0, touDraft.offPeakKwh!!, 0.001)
        assertEquals(270.0, touDraft.onPeakKwh!!, 0.001)
        assertEquals(3, touDraft.breakdownItems.size)
        assertEquals("OFF_PEAK_SUPPLY", touDraft.breakdownItems[0].category)
        assertEquals(80.36, touDraft.breakdownItems[0].amount!!, 0.001)
        assertEquals("DISTRIBUTION", touDraft.breakdownItems[1].category)
        assertEquals("FIXED", touDraft.breakdownItems[2].category)
        assertNull(touDraft.breakdownItems[2].kwh)
        assertEquals(
            0.15,
            ElectricityImportParser.calculateTouOffPeakRate(
                touDraft.offPeakSupplyRate,
                touDraft.distributionRatePerKwh
            )!!,
            0.0001
        )

        val flatJson = """
            {
              "providerName": "Flat Power Co",
              "billingStartDate": "2026-11-01",
              "billingEndDate": "2026-11-30",
              "totalKwh": 800.0,
              "totalAmount": 136.0,
              "fixedCharges": 16.0,
              "isTouPlan": false,
              "offPeakSupplyRate": null,
              "onPeakSupplyRate": null,
              "distributionRatePerKwh": 0.0,
              "offPeakKwh": null,
              "onPeakKwh": null,
              "breakdownItems": null
            }
        """.trimIndent()

        val flatDraft = GeminiAiService.parseUtilityBillJsonResponse(flatJson, "flat_nov.pdf")
        assertEquals("Flat Power Co", flatDraft.providerName)
        assertFalse(flatDraft.isTouPlan)
        assertNull(flatDraft.offPeakSupplyRate)
        assertNull(flatDraft.onPeakSupplyRate)
        assertEquals(0.0, flatDraft.distributionRatePerKwh!!, 0.0001)
        assertNull(flatDraft.offPeakKwh)
        assertNull(flatDraft.onPeakKwh)
        assertTrue(flatDraft.breakdownItems.isEmpty())
        assertEquals(
            0.15,
            ElectricityImportParser.calculateVariableBlendedRate(
                flatDraft.totalAmount,
                flatDraft.fixedCharges,
                flatDraft.totalKwh
            ),
            0.0001
        )
    }

    @Test
    fun useCustomElectricityRate_overridesMatchedBillsAndRestoresWhenDisabled() {
        val ev1 = Car(
            id = "ev1",
            year = 2024,
            make = "Tesla",
            model = "Model Y",
            isEv = true,
            telemetryProvider = "tessie"
        )
        val matchedBill = UtilityBillEntity(
            id = "bill_nov",
            providerName = "DTE Energy",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-30"),
            totalKwh = 1000.0,
            totalAmount = 212.0,
            fixedCharges = 12.0,
            calculatedRate = 0.20,
            overrideRate = null,
            effectiveRate = 0.20,
            uploadedAtMs = 1_000L
        )
        val sessions = listOf(
            ChargingSessionEntity(
                id = "h_nov",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-11", 22),
                endDateMs = tsAtHour("2026-11-12", 4),
                kwh = 50.0,
                venue = "HOME",
                fingerprint = "fp_custom_test"
            )
        )

        val customRateState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(matchedBill),
            chargingSessions = sessions,
            defaultElectricityRate = 0.135f,
            useCustomElectricityRate = true
        )

        assertTrue(customRateState.useCustomElectricityRate)
        assertTrue(customRateState.electricityCost.homeIsCustomRate)
        assertFalse(customRateState.electricityCost.homeIsEstimatedRate)
        assertFalse(customRateState.electricityCost.homeHasMatchedBill)
        assertEquals(0.135, customRateState.electricityCost.homeEffectiveRate, 0.0001)
        assertEquals(6.75, customRateState.electricityCost.homeTotalCost, 0.01)
        assertEquals("Custom rate", customRateState.electricityCost.homeRateSourceLabel)
        assertEquals(0.135, customRateState.electricityCost.homePeriodLogs.first().effectiveRate, 0.0001)

        val billRateState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(matchedBill),
            chargingSessions = sessions,
            defaultElectricityRate = 0.135f,
            useCustomElectricityRate = false
        )

        assertFalse(billRateState.useCustomElectricityRate)
        assertFalse(billRateState.electricityCost.homeIsCustomRate)
        assertFalse(billRateState.electricityCost.homeIsEstimatedRate)
        assertTrue(billRateState.electricityCost.homeHasMatchedBill)
        assertEquals(0.20, billRateState.electricityCost.homeEffectiveRate, 0.0001)
        assertEquals(10.0, billRateState.electricityCost.homeTotalCost, 0.01)
        assertNotEquals("Custom rate", billRateState.electricityCost.homeRateSourceLabel)
    }

    @Test
    fun workCharging_computesZeroCost_avoidedHomeCost_combinedEfficiency_andNetFuelSavings() {
        val ev1 = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val bill = UtilityBillEntity(
            id = "bill_nov",
            providerName = "DTE Energy",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-30"),
            totalKwh = 1000.0,
            totalAmount = 200.0,
            fixedCharges = 0.0,
            calculatedRate = 0.20,
            overrideRate = null,
            effectiveRate = 0.20,
            uploadedAtMs = 1_000L
        )
        // Week Nov 9–15: drove 280 miles -> 10 gal @ 28 MPG -> $36.50 gas @ $3.65/gal
        val logs = listOf(
            OdometerLogEntity("l1", "ev1", 10000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l2", "ev1", 10280, tsAtHour("2026-11-14", 16), "TELEMETRY")
        )

        // 1) Mixed Home (20 kWh @ $0.20 = $4.00) + Work (30 kWh @ Free, $6.00 avoided) + Public (20 kWh @ $8.00) = 70 kWh total, $12.00 total
        val mixedSessions = listOf(
            ChargingSessionEntity(
                id = "h1", carId = "ev1",
                startDateMs = tsAtHour("2026-11-10", 20), endDateMs = tsAtHour("2026-11-10", 22),
                kwh = 20.0, venue = "HOME", fingerprint = "fp_mix_h1"
            ),
            ChargingSessionEntity(
                id = "w1", carId = "ev1",
                startDateMs = tsAtHour("2026-11-11", 9), endDateMs = tsAtHour("2026-11-11", 16),
                kwh = 30.0, venue = "WORK", directCost = 0.0, chargerName = "Work J1772", fingerprint = "fp_mix_w1"
            ),
            ChargingSessionEntity(
                id = "p1", carId = "ev1",
                startDateMs = tsAtHour("2026-11-12", 14), endDateMs = tsAtHour("2026-11-12", 15),
                kwh = 20.0, venue = "PUBLIC", directCost = 8.0, chargerName = "Supercharger", fingerprint = "fp_mix_p1"
            )
        )

        val mixedState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(bill),
            chargingSessions = mixedSessions,
            defaultElectricityRate = 0.16f
        )

        val elec = mixedState.electricityCost
        assertTrue(elec.hasHomeData)
        assertTrue(elec.hasWorkData)
        assertTrue(elec.hasPublicData)
        assertEquals(30.0, elec.workTotalKwh, 0.01)
        assertEquals(6.0, elec.workAvoidedHomeCost, 0.01)
        assertEquals(1, elec.workSessionCount)
        assertEquals(1, elec.workPeriodLogs.size)
        assertEquals(0.0, elec.workPeriodLogs.first().attributedCost, 0.001)
        assertEquals(6.0, elec.workPeriodLogs.first().avoidedHomeCost, 0.01)
        // Combined kWh = 20 + 30 + 20 = 70 kWh; Combined cost = $4.00 + $0.00 + $8.00 = $12.00
        assertEquals(70.0, elec.combinedTotalKwh, 0.01)
        assertEquals(12.0, elec.combinedTotalCost, 0.01)
        // Combined efficiency = 280 mi / 70 kWh = 4.0 mi/kWh; $12 / 280 mi = $0.04286/mi
        assertEquals(4.0, elec.combinedMilesPerKwh!!, 0.01)
        assertEquals(12.0 / 280.0, elec.combinedCostPerMile!!, 0.0005)
        // Net fuel savings = $36.50 - $12.00 = $24.50
        assertEquals(24.50, mixedState.gasComparison.netFuelSavings!!, 0.05)

        // 2) 100% Free Work Charging week: EV cost = $0.00, full $36.50 netFuelSavings preserved!
        val workOnlySessions = listOf(
            ChargingSessionEntity(
                id = "w_only", carId = "ev1",
                startDateMs = startMs("2026-11-01"), endDateMs = endMs("2026-11-30"),
                kwh = 300.0, venue = "WORK", directCost = 0.0, isSummary = true,
                chargerName = "Work (Tesla Charge Stats)", fingerprint = "fp_work_sum"
            )
        )
        val workOnlyState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-11", 12),
            utilityBills = listOf(bill),
            chargingSessions = workOnlySessions,
            defaultElectricityRate = 0.16f
        )
        // 300 kWh / 30 days * 7 days = 70 kWh prorated
        assertTrue(workOnlyState.electricityCost.hasWorkData)
        assertFalse(workOnlyState.electricityCost.hasHomeData)
        assertEquals(70.0, workOnlyState.electricityCost.workTotalKwh, 0.01)
        assertEquals(14.0, workOnlyState.electricityCost.workAvoidedHomeCost, 0.01)
        assertEquals(0.0, workOnlyState.electricityCost.combinedTotalCost, 0.001)
        assertEquals(4.0, workOnlyState.electricityCost.combinedMilesPerKwh!!, 0.01)
        assertEquals(0.0, workOnlyState.electricityCost.combinedCostPerMile!!, 0.0001)
        assertNotNull(workOnlyState.gasComparison.netFuelSavings)
        assertEquals(36.50, workOnlyState.gasComparison.netFuelSavings!!, 0.05)
    }

    @Test
    fun workVenueClassification_csvZeroCost_andAutomatedBatchExclusion() {
        assertEquals("WORK", ElectricityImportParser.classifyVenue("Work J1772", 0.0))
        assertEquals("WORK", ElectricityImportParser.classifyVenue("Office Charger", null))
        assertEquals("WORK", ElectricityImportParser.classifyVenue("Company Campus Free Charging", 0.0))
        assertEquals("PUBLIC", ElectricityImportParser.classifyVenue("Electrify America", 0.0))
        assertEquals("HOME", ElectricityImportParser.classifyVenue("Garage Wall Connector", null))

        val csv = """
            Date,Energy (kWh),Location,Cost
            2026-11-04,24.0,Work J1772,0.00
            2026-11-05,30.0,Home Wall Connector,
            2026-11-06,42.5,Tesla Supercharger,${'$'}17.50
        """.trimIndent()
        val parsed = ElectricityImportParser.parseChargerCsv("all_venues.csv", csv)
        assertEquals(3, parsed.size)
        assertEquals("WORK", parsed[0].venue)
        assertEquals(0.0, parsed[0].directCost!!, 0.001)
        assertEquals("HOME", parsed[1].venue)
        assertNull(parsed[1].directCost)
        assertEquals("PUBLIC", parsed[2].venue)
        assertEquals(17.50, parsed[2].directCost!!, 0.01)

        val manualSession = ChargingSessionEntity(
            id = "man_1", carId = "ev1",
            startDateMs = startMs("2026-11-01"), endDateMs = endMs("2026-11-01"),
            kwh = 20.0, venue = "HOME", fingerprint = "fp_man_1",
            uploadBatchId = "manual_batch_1", uploadedAtMs = 1_000L
        )
        val autoSessions = listOf(
            ChargingSessionEntity(
                id = "twc_1", carId = "ev1",
                startDateMs = startMs("2026-11-02"), endDateMs = endMs("2026-11-02"),
                kwh = 25.0, venue = "HOME", fingerprint = "fp_auto_1",
                uploadBatchId = ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID, uploadedAtMs = 9_001L
            ),
            ChargingSessionEntity(
                id = "tesla_work_vin_2026-11-03", carId = "ev1",
                startDateMs = startMs("2026-11-03"), endDateMs = endMs("2026-11-03"),
                kwh = 18.0, venue = "WORK", directCost = 0.0, fingerprint = "fp_auto_2",
                uploadBatchId = ElectricityImportParser.AUTO_WORK_CHARGING_BATCH_ID, uploadedAtMs = 9_002L
            ),
            ChargingSessionEntity(
                id = "sc_tesla_12345", carId = "ev1",
                startDateMs = startMs("2026-11-04"), endDateMs = endMs("2026-11-04"),
                kwh = 40.0, venue = "PUBLIC", directCost = 16.0, fingerprint = "fp_auto_3",
                uploadBatchId = ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID, uploadedAtMs = 9_003L
            ),
            ChargingSessionEntity(
                id = "sc_smartcar_999", carId = "ev2",
                startDateMs = startMs("2026-11-05"), endDateMs = endMs("2026-11-05"),
                kwh = 35.0, venue = "PUBLIC", directCost = 14.0, fingerprint = "fp_auto_4",
                uploadBatchId = ElectricityImportParser.AUTO_SMARTCAR_CHARGING_BATCH_ID, uploadedAtMs = 9_004L
            )
        )
        val resolved = ElectricityImportParser.resolveLastUploadBatch(listOf(manualSession) + autoSessions)
        assertEquals(listOf("man_1"), resolved.map { it.id })
    }

    @Test
    fun parseChargerUsageJsonResponse_parsesTeslaChargeStatsThreeVenues() {
        val rawJson = """
            {
              "sessions": [
                {
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-30",
                  "kwh": 210.0,
                  "venue": "HOME",
                  "directCost": null,
                  "chargerName": "Home (Tesla Charge Stats)",
                  "isSummary": true
                },
                {
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-30",
                  "kwh": 145.5,
                  "venue": "WORK",
                  "directCost": 0.0,
                  "chargerName": "Work (Tesla Charge Stats)",
                  "isSummary": true
                },
                {
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-30",
                  "kwh": 64.0,
                  "venue": "PUBLIC",
                  "directCost": 25.60,
                  "chargerName": "Supercharger (Tesla Charge Stats)",
                  "isSummary": true
                }
              ]
            }
        """.trimIndent()

        val parsed = GeminiAiService.parseChargerUsageJsonResponse(rawJson, "tesla_charge_stats.png")
        assertEquals(3, parsed.size)

        assertEquals("HOME", parsed[0].venue)
        assertEquals(210.0, parsed[0].kwh, 0.01)
        assertNull(parsed[0].directCost)
        assertTrue(parsed[0].isSummary)

        assertEquals("WORK", parsed[1].venue)
        assertEquals(145.5, parsed[1].kwh, 0.01)
        assertEquals(0.0, parsed[1].directCost!!, 0.001)
        assertTrue(parsed[1].isSummary)

        assertEquals("PUBLIC", parsed[2].venue)
        assertEquals(64.0, parsed[2].kwh, 0.01)
        assertEquals(25.60, parsed[2].directCost!!, 0.01)
        assertTrue(parsed[2].isSummary)
    }
}

