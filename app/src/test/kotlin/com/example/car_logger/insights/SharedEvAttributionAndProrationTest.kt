package com.example.car_logger.insights

import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.insights.InsightsTestFixtures.endMs
import com.example.car_logger.insights.InsightsTestFixtures.startMs
import com.example.car_logger.insights.InsightsTestFixtures.tsAtHour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedEvAttributionAndProrationTest {

    @Test
    fun monthlySummary_proratesEvenlyAcrossWeekMonthAndYear() {
        val ev = Car(id = "ev_1", year = 2024, make = "Rivian", model = "R1S", isEv = true)
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
        assertEquals(2.0, mixedState.electricityCost.homeMilesPerKwh ?: 0.0, 0.05)
    }

    @Test
    fun periodChargingLogs_filtersByPeriodVenueAndVehicle_andMatchesAttributedTotals() {
        val ev1 = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val ev2 = Car(id = "ev2", year = 2024, make = "Chevy", model = "Blazer EV", isEv = true)

        val logs = listOf(
            OdometerLogEntity("l1", "ev1", 1000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l2", "ev1", 1300, tsAtHour("2026-11-14", 16), "TELEMETRY"),
            OdometerLogEntity("l3", "ev2", 2000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l4", "ev2", 2100, tsAtHour("2026-11-14", 16), "TELEMETRY")
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
            ChargingSessionEntity(
                id = "h_ev1_nov",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-12", 20),
                endDateMs = tsAtHour("2026-11-12", 22),
                kwh = 40.0,
                venue = "HOME",
                fingerprint = "fp_h_ev1_nov"
            ),
            ChargingSessionEntity(
                id = "h_shared_nov",
                carId = null,
                startDateMs = tsAtHour("2026-11-10", 18),
                endDateMs = tsAtHour("2026-11-10", 21),
                kwh = 100.0,
                venue = "HOME",
                fingerprint = "fp_h_shared_nov"
            ),
            ChargingSessionEntity(
                id = "h_ev2_nov",
                carId = "ev2",
                startDateMs = tsAtHour("2026-11-11", 19),
                endDateMs = tsAtHour("2026-11-11", 21),
                kwh = 30.0,
                venue = "HOME",
                fingerprint = "fp_h_ev2_nov"
            ),
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
}
