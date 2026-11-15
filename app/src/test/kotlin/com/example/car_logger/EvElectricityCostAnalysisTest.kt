package com.example.car_logger

import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
}
