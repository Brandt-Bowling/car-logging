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
    fun computeUiState_monthlyView_calculatesMilesAndGasComparison() {
        // Oct 1 2026 to Oct 29 2026 (28 days in October 2026)
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
            granularity = InsightsTimeGranularity.MONTHLY,
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

        // Activity bars: October has 5 weekly segments (1-7, 8-14, 15-21, 22-28, 29-31)
        assertEquals(5, state.activityBars.size)
        assertEquals("October", state.observationWindowText)
    }

    @Test
    fun computeUiState_weeklyView_generatesSevenDailyBars() {
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val log1 = OdometerLogEntity("p1", "car_1", 20000, oct1Ms, "TESLA_FLEET")
        val log2 = OdometerLogEntity("p2", "car_1", 20500, oct29Ms, "TESLA_FLEET")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct29Ms
        )

        // Week view displays exactly 7 daily bars (Monday through Sunday)
        assertEquals(7, state.activityBars.size)
        val expectedDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        state.activityBars.forEachIndexed { i, bar ->
            assertEquals(expectedDays[i], bar.shortLabel)
        }
    }

    @Test
    fun computeUiState_ytdView_generatesTwelveMonthlyBars() {
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val log1 = OdometerLogEntity("p1", "car_1", 20000, oct1Ms, "TESLA_FLEET")
        val log2 = OdometerLogEntity("p2", "car_1", 20500, oct29Ms, "TESLA_FLEET")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.YTD,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct29Ms
        )

        // Year view displays 12 monthly bars
        assertEquals(12, state.activityBars.size)
        assertEquals("2026", state.observationWindowText)
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
            granularity = InsightsTimeGranularity.MONTHLY,
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

    @Test
    fun computeUiState_threeCars_prioritizesCarsWithTelemetryAndCalculatesSelectedCar3Miles() {
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val blazer = Car(id = "car_blazer", make = "Chevrolet", model = "Blazer EV", year = 2024, telemetryProvider = "SMARTCAR")
        val sonic = Car(id = "car_sonic", make = "Chevrolet", model = "Sonic", year = 2013)
        val model3 = Car(id = "car_model3", make = "Tesla", model = "Model 3", year = 2023, telemetryProvider = "TESLA_FLEET")

        val blazerLog1 = OdometerLogEntity("1", "car_blazer", 10000, oct1Ms, "sync")
        val blazerLog2 = OdometerLogEntity("2", "car_blazer", 11200, oct29Ms, "sync")

        val model3Log1 = OdometerLogEntity("3", "car_model3", 20000, oct1Ms, "sync")
        val model3Log2 = OdometerLogEntity("4", "car_model3", 20500, oct29Ms, "sync")

        val allLogs = listOf(blazerLog1, blazerLog2, model3Log1, model3Log2)

        // Fleet view: should prioritize Blazer and Model 3 over Sonic
        val fleetState = InsightsCalculationEngine.computeUiState(
            cars = listOf(blazer, sonic, model3),
            allLogs = allLogs,
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct29Ms
        )

        assertEquals("Blazer EV", fleetState.car1Name)
        assertEquals("Model 3", fleetState.car2Name)
        assertEquals(1200, fleetState.car1Miles)
        assertEquals(500, fleetState.car2Miles)
        assertEquals(1700, fleetState.totalMiles)

        // Single car filter for Model 3: should calculate Model 3 miles even as 3rd car
        val model3State = InsightsCalculationEngine.computeUiState(
            cars = listOf(blazer, sonic, model3),
            allLogs = allLogs,
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = "car_model3",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct29Ms
        )

        assertEquals(500, model3State.totalMiles)
        assertEquals(500, model3State.car1Miles)
        assertEquals("Model 3", model3State.car1Name)
    }

    @Test
    fun computeUiState_threeCars_sonicHasPastLogs_prioritizesBlazerAndTeslaTelemetry() {
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oldMs = LocalDate.of(2024, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val blazer = Car(id = "car_blazer", make = "Chevrolet", model = "Blazer EV", year = 2024, telemetryProvider = "SMARTCAR")
        val sonic = Car(id = "car_sonic", make = "Chevrolet", model = "Sonic", year = 2013)
        val model3 = Car(id = "car_model3", make = "Tesla", model = "Model 3", year = 2023, telemetryProvider = "TESLA_FLEET")

        val sonicLog1 = OdometerLogEntity("s1", "car_sonic", 30000, oldMs, "MAINTENANCE")
        val sonicLog2 = OdometerLogEntity("s2", "car_sonic", 35000, oldMs + 86400000L, "MAINTENANCE")

        val blazerLog1 = OdometerLogEntity("b1", "car_blazer", 10000, oct1Ms, "SMARTCAR")
        val blazerLog2 = OdometerLogEntity("b2", "car_blazer", 11200, oct29Ms, "SMARTCAR")

        val model3Log1 = OdometerLogEntity("m1", "car_model3", 20000, oct1Ms, "TESLA_FLEET")
        val model3Log2 = OdometerLogEntity("m2", "car_model3", 20500, oct29Ms, "TESLA_FLEET")

        val allLogs = listOf(sonicLog1, sonicLog2, blazerLog1, blazerLog2, model3Log1, model3Log2)

        val fleetState = InsightsCalculationEngine.computeUiState(
            cars = listOf(blazer, sonic, model3),
            allLogs = allLogs,
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct29Ms
        )

        // Despite Sonic having logs, Blazer EV and Model 3 have active telemetry and recent logs
        assertEquals("Blazer EV", fleetState.car1Name)
        assertEquals("Model 3", fleetState.car2Name)
        assertEquals(1200, fleetState.car1Miles)
        assertEquals(500, fleetState.car2Miles)
        assertEquals(1700, fleetState.totalMiles)
    }

    @Test
    fun computeUiState_zeroMilesDriven_fleetShareIsFiftyFifty() {
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val log1 = OdometerLogEntity("1", "car_1", 20000, oct1Ms, "sync")
        val log2 = OdometerLogEntity("2", "car_1", 20000, oct29Ms, "sync")
        val log3 = OdometerLogEntity("3", "car_2", 15000, oct1Ms, "sync")
        val log4 = OdometerLogEntity("4", "car_2", 15000, oct29Ms, "sync")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1, car2),
            allLogs = listOf(log1, log2, log3, log4),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct29Ms
        )

        assertEquals(0, state.totalMiles)
        assertEquals(0, state.car1Miles)
        assertEquals(0, state.car2Miles)
        assertEquals(50, state.car1SharePct)
        assertEquals(50, state.car2SharePct)
    }

    @Test
    fun computeUiState_ignoresMaintenanceAndInitialLogs() {
        // Old maintenance record in January at 0 miles
        val jan1Ms = LocalDate.of(2026, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct15Ms = LocalDate.of(2026, 10, 15).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct20Ms = LocalDate.of(2026, 10, 20).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val maintLog = OdometerLogEntity("rec_1", "car_1", 0, jan1Ms, "MAINTENANCE")
        val initialFakeLog = OdometerLogEntity("car_odo_car_1", "car_1", 25980, oct15Ms, "INITIAL")
        val realPing1 = OdometerLogEntity("ping_1", "car_1", 25980, oct15Ms, "TESLA_FLEET")
        val realPing2 = OdometerLogEntity("ping_2", "car_1", 25999, oct20Ms, "TESLA_FLEET")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(maintLog, initialFakeLog, realPing1, realPing2),
            granularity = InsightsTimeGranularity.YTD,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = oct20Ms
        )

        assertTrue(state.hasOdometerData)
        // Should only count the 19 miles between the two telemetry pings, not 25999 miles from Jan!
        assertEquals(19, state.totalMiles)
        assertEquals(19, state.car1Miles)
    }

    @Test
    fun computeUiState_periodNavigation_canGoPreviousAndNext() {
        // Data from Oct 1 to Oct 29, 2026
        val oct1Ms = LocalDate.of(2026, 10, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val oct29Ms = LocalDate.of(2026, 10, 29).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val log1 = OdometerLogEntity("p1", "car_1", 20000, oct1Ms, "TESLA_FLEET")
        val log2 = OdometerLogEntity("p2", "car_1", 20500, oct29Ms, "TESLA_FLEET")

        // Current week (periodOffset = 0)
        val stateCurrent = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            periodOffset = 0,
            nowMs = oct29Ms
        )

        assertFalse("At current period, cannot go next", stateCurrent.canGoNext)
        assertTrue("Has data earlier in October, can go previous", stateCurrent.canGoPrevious)

        // Previous week (periodOffset = -1)
        val statePrev = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            periodOffset = -1,
            nowMs = oct29Ms
        )

        assertTrue("In previous period, can go next back to current", statePrev.canGoNext)

        // Navigating far into the past (e.g. 10 weeks ago, before Oct 1)
        val stateFarPast = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            periodOffset = -10,
            nowMs = oct29Ms
        )

        assertFalse("Cannot go previous beyond earliest telemetry data", stateFarPast.canGoPrevious)
        assertTrue("Can navigate forward towards current data", stateFarPast.canGoNext)
    }

    @Test
    fun computeUiState_missedNightlyPollWithMorningManualSync_attributesMilesToYesterdayNotToday() {
        // Week of Mon Nov 2, 2026:
        // Mon Nov 2 at 11:00 PM: 20,000 mi
        // Tue Nov 3 at 11:00 PM: missed (car asleep)
        // Wed Nov 4 at 8:30 AM: manual sync reads 20,040 mi (40 mi driven on Tuesday)
        val mon11Pm = LocalDate.of(2026, 11, 2).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val wed830Am = LocalDate.of(2026, 11, 4).atTime(8, 30).atZone(zoneId).toInstant().toEpochMilli()

        val logMon = OdometerLogEntity("p1", "car_1", 20000, mon11Pm, "TESLA_FLEET")
        val logWedMorning = OdometerLogEntity("p2", "car_1", 20040, wed830Am, "TESLA_FLEET")

        val stateMorning = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(logMon, logWedMorning),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = wed830Am
        )

        // Bars: 0=Mon, 1=Tue, 2=Wed, 3=Thu, 4=Fri, 5=Sat, 6=Sun
        assertEquals("Monday should have 0 miles", 0, stateMorning.activityBars[0].car1Miles)
        assertEquals("Tuesday should get all 40 miles", 40, stateMorning.activityBars[1].car1Miles)
        assertEquals("Wednesday (today) should have 0 miles before driving today", 0, stateMorning.activityBars[2].car1Miles)

        // Now user drives 15 miles on Wednesday and syncs Wednesday at 11:00 PM
        val wed11Pm = LocalDate.of(2026, 11, 4).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val logWedNight = OdometerLogEntity("p3", "car_1", 20055, wed11Pm, "TESLA_FLEET")

        val stateEvening = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(logMon, logWedMorning, logWedNight),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = wed11Pm
        )

        assertEquals(0, stateEvening.activityBars[0].car1Miles)
        assertEquals(40, stateEvening.activityBars[1].car1Miles)
        assertEquals(15, stateEvening.activityBars[2].car1Miles)
    }

    @Test
    fun computeUiState_afternoonSyncYesterdayThenMorningSyncToday_attributesMilesToYesterday() {
        // Tue Nov 3 at 5:00 PM: 20,000 mi
        // Wed Nov 4 at 8:30 AM: manual sync reads 20,030 mi
        val tue5Pm = LocalDate.of(2026, 11, 3).atTime(17, 0).atZone(zoneId).toInstant().toEpochMilli()
        val wed830Am = LocalDate.of(2026, 11, 4).atTime(8, 30).atZone(zoneId).toInstant().toEpochMilli()

        val log1 = OdometerLogEntity("p1", "car_1", 20000, tue5Pm, "TESLA_FLEET")
        val log2 = OdometerLogEntity("p2", "car_1", 20030, wed830Am, "TESLA_FLEET")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = wed830Am
        )

        assertEquals("Tuesday should get all 30 miles", 30, state.activityBars[1].car1Miles)
        assertEquals("Wednesday morning sync should not attribute yesterday's miles to today", 0, state.activityBars[2].car1Miles)
    }

    @Test
    fun computeUiState_nightlySyncAt11Pm_doesNotBleedMilesIntoPreviousDay() {
        // Mon Nov 2 at 10:50 PM (within WorkManager flex window): 20,000 mi
        // Tue Nov 3 at 11:00 PM: 20,048 mi
        val mon1050Pm = LocalDate.of(2026, 11, 2).atTime(22, 50).atZone(zoneId).toInstant().toEpochMilli()
        val tue11Pm = LocalDate.of(2026, 11, 3).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()

        val log1 = OdometerLogEntity("p1", "car_1", 20000, mon1050Pm, "TESLA_FLEET")
        val log2 = OdometerLogEntity("p2", "car_1", 20048, tue11Pm, "TESLA_FLEET")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = tue11Pm
        )

        assertEquals("Monday should not get miles from Monday 10:50 PM - midnight", 0, state.activityBars[0].car1Miles)
        assertEquals("Tuesday should get all 48 miles", 48, state.activityBars[1].car1Miles)
    }

    @Test
    fun computeUiState_morningDriveAfterSuccessfulNightlySync_attributesMilesToToday() {
        // Tue Nov 3 at 11:00 PM: 20,000 mi (nightly poll succeeded)
        // Wed Nov 4 at 8:30 AM: 20,018 mi (user drove early Wednesday morning and synced)
        val tue11Pm = LocalDate.of(2026, 11, 3).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val wed830Am = LocalDate.of(2026, 11, 4).atTime(8, 30).atZone(zoneId).toInstant().toEpochMilli()

        val log1 = OdometerLogEntity("p1", "car_1", 20000, tue11Pm, "TESLA_FLEET")
        val log2 = OdometerLogEntity("p2", "car_1", 20018, wed830Am, "TESLA_FLEET")

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(car1),
            allLogs = listOf(log1, log2),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = "car_1",
            gasPrice = 3.50f,
            baselineMpg = 25.0f,
            nowMs = wed830Am
        )

        assertEquals("Tuesday already had its 11 PM snapshot, so 0 miles on Tuesday", 0, state.activityBars[1].car1Miles)
        assertEquals("Wednesday gets all 18 miles driven after Tuesday 11 PM", 18, state.activityBars[2].car1Miles)
    }
}
