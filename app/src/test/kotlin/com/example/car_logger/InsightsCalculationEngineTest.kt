package com.example.car_logger

import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InsightsCalculationEngineTest {

    private val zoneId = ZoneId.systemDefault()

    private val car1 = Car(
        id = "car_1",
        year = 2023,
        make = "Tesla",
        model = "Model 3",
        trim = "Long Range AWD",
        odometer = 24500,
        isArchived = false
    )

    private val car2 = Car(
        id = "car_2",
        year = 2022,
        make = "Chevrolet",
        model = "Bolt EUV",
        trim = "Premier",
        odometer = 18200,
        isArchived = false
    )

    @Test
    fun computeUiState_emptyLogs_returnsHasOdometerDataFalse() {
        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1, car2),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f
        )

        assertFalse(state.hasOdometerData)
        assertEquals(0, state.totalMiles)
        assertTrue(state.activityBars.isEmpty())
        assertTrue(state.observationWindowText.contains("No odometer readings"))
    }

    @Test
    fun computeUiState_singleLog_returnsHasOdometerDataFalseWithDate() {
        val singleLog = OdometerLogEntity(
            id = "1",
            carId = "car_1",
            odometer = 24000,
            timestamp = 1727740800000L, // Oct 1 2024
            source = "manual"
        )

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1, car2),
            allLogs = listOf(singleLog),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f
        )

        assertFalse(state.hasOdometerData)
        assertEquals(0, state.totalMiles)
        assertTrue(state.observationWindowText.contains("more readings needed"))
    }

    @Test
    fun computeUiState_twoLogs_calculatesMilesAndGasComparison() {
        // Oct 1 2026 to Oct 29 2026 (28 days = exactly 4 full weeks)
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()

        // Car 1 drove 1,200 miles
        val car1Log1 = OdometerLogEntity("1", "car_1", 20000, oct1Ms, "sync")
        val car1Log2 = OdometerLogEntity("2", "car_1", 21200, oct29Ms, "sync")

        // Car 2 drove 800 miles
        val car2Log1 = OdometerLogEntity("3", "car_2", 15000, oct1Ms, "sync")
        val car2Log2 = OdometerLogEntity("4", "car_2", 15800, oct29Ms, "sync")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1, car2),
            allLogs = listOf(car1Log1, car1Log2, car2Log1, car2Log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct29Ms
        )

        assertTrue(state.hasOdometerData)
        assertEquals(2000, state.totalMiles)
        assertEquals(1200, state.car1Miles)
        assertEquals(800, state.car2Miles)

        // Fleet shares: 1200/2000 = 60%, 800/2000 = 40%
        assertEquals(60, state.car1SharePct)
        assertEquals(40, state.car2SharePct)

        // Gas comparison: 2000 miles / 25 mpg = 80 gallons; 80 gal * $3.50 = $280.00
        assertEquals(80.0, state.gasComparison.totalGallons, 0.01)
        assertEquals(280.0, state.gasComparison.totalCost, 0.01)
        // CO2: 80 * 19.64 = 1571.2 lbs -> 1571 lbs
        assertEquals(1571, state.gasComparison.co2Lbs)

        // Activity bars: 5 overlapping calendar weeks
        assertEquals(5, state.activityBars.size)
    }

    @Test
    fun computeUiState_singleCarFilter_filtersTotalsAndGas() {
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val car1Log1 = OdometerLogEntity("1", "car_1", 20000, oct1Ms, "sync")
        val car1Log2 = OdometerLogEntity("2", "car_1", 21200, oct29Ms, "sync")
        val car2Log1 = OdometerLogEntity("3", "car_2", 15000, oct1Ms, "sync")
        val car2Log2 = OdometerLogEntity("4", "car_2", 15800, oct29Ms, "sync")

        val stateCar1 = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1, car2),
            allLogs = listOf(car1Log1, car1Log2, car2Log1, car2Log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 4.00f,
            baselineMpg = 30.0f,
            nowMs = oct29Ms
        )

        // Car 1 only: 1200 miles
        assertEquals(1200, stateCar1.totalMiles)
        // 1200 / 30 = 40 gallons; 40 * $4 = $160
        assertEquals(40.0, stateCar1.gasComparison.totalGallons, 0.01)
        assertEquals(160.0, stateCar1.gasComparison.totalCost, 0.01)
    }
}
