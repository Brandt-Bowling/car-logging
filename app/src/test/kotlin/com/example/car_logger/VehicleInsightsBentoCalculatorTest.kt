package com.example.car_logger

import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleCostHistory
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleInsightsBentoCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class VehicleInsightsBentoCalculatorTest {

    private val zoneId = ZoneId.systemDefault()

    private val model3 = Car(
        id = "car_m3",
        year = 2024,
        make = "Tesla",
        model = "Model 3",
        odometer = 20000,
        isEv = true,
        isArchived = false
    )

    private val blazer = Car(
        id = "car_blazer",
        year = 2024,
        make = "Chevrolet",
        model = "Blazer EV",
        odometer = 15000,
        isEv = true,
        isArchived = false
    )

    @Test
    fun compute_calculatesWeeklyAndMonthlyPaceFleetShareAndTco() {
        val nov1Ms = LocalDate.of(2026, 11, 1).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val nov15NightMs = LocalDate.of(2026, 11, 15).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val nov18Ms = LocalDate.of(2026, 11, 18).atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

        // Model 3 drove 600 mi over Nov 1..18 (and 120 mi in the current week Nov 16..18)
        // Blazer drove 300 mi over Nov 1..18 (and 60 mi in the current week Nov 16..18)
        val logs = listOf(
            OdometerLogEntity("l1", "car_m3", 19400, nov1Ms, "TESLA_FLEET"),
            OdometerLogEntity("l2", "car_m3", 19880, nov15NightMs, "TESLA_FLEET"),
            OdometerLogEntity("l3", "car_m3", 20000, nov18Ms, "TESLA_FLEET"),
            OdometerLogEntity("l4", "car_blazer", 14700, nov1Ms, "SMARTCAR"),
            OdometerLogEntity("l5", "car_blazer", 14940, nov15NightMs, "SMARTCAR"),
            OdometerLogEntity("l6", "car_blazer", 15000, nov18Ms, "SMARTCAR")
        )

        val records = listOf(
            MaintenanceRecord(
                id = "r1",
                carId = "car_m3",
                title = "Cabin Air Filter",
                date = "2026-05-10",
                odometer = 10000,
                cost = 200.0
            )
        )
        val costHistory = VehicleCostHistory(
            lifetimeTotalCost = 200.0,
            lifetimeCostPerMile = 0.02, // $200 over 10,000 tracked miles (10,000 -> 20,000)
            yearlyBreakdowns = emptyList(),
            averageAnnualSpend = 200.0
        )

        val bill = UtilityBillEntity(
            id = "b1",
            providerName = "DTE Energy",
            startDateMs = nov1Ms,
            endDateMs = nov18Ms,
            totalKwh = 1000.0,
            totalAmount = 160.0,
            fixedCharges = 0.0,
            calculatedRate = 0.16,
            effectiveRate = 0.16,
            uploadedAtMs = nov18Ms
        )

        val nov16Ms = LocalDate.of(2026, 11, 16).atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
        val homeSession = ChargingSessionEntity(
            id = "s1",
            startDateMs = nov16Ms,
            endDateMs = nov16Ms + 3600_000L,
            kwh = 30.0,
            carId = "car_m3",
            fingerprint = "fp1",
            uploadedAtMs = nov18Ms,
            venue = "HOME"
        )
        val publicSession = ChargingSessionEntity(
            id = "s2",
            startDateMs = nov16Ms + 7200_000L,
            endDateMs = nov16Ms + 10_800_000L,
            kwh = 10.0,
            carId = "car_m3",
            fingerprint = "fp2",
            uploadedAtMs = nov18Ms,
            venue = "PUBLIC",
            directCost = 4.00
        )

        val bentoState = VehicleInsightsBentoCalculator.compute(
            targetCar = model3,
            allCars = listOf(model3, blazer),
            allLogs = logs,
            records = records,
            costHistory = costHistory,
            utilityBills = listOf(bill),
            chargingSessions = listOf(homeSession, publicSession),
            gasPrice = 3.50f,
            baselineMpg = 28.0f,
            defaultElectricityRate = 0.16f,
            useCustomElectricityRate = false,
            nowMs = nov18Ms
        )

        // Weekly metrics (7D)
        val weekly = bentoState.metricsFor(InsightsTimeGranularity.WEEKLY)
        assertTrue(weekly.hasOdometerData)
        assertEquals(120, weekly.totalMiles)
        // Model 3 drove 120 mi out of 180 fleet mi in week -> 67% share
        assertEquals(67, weekly.fleetSharePct)
        assertEquals(40.0, weekly.totalKwh, 0.01)
        // Home 30 kWh @ $0.16 = $4.80; Public 10 kWh = $4.00 -> Total $8.80
        assertEquals(8.80, weekly.totalElectricityCost, 0.01)
        assertEquals(75, weekly.homePct)
        assertEquals(25, weekly.publicPct)
        // Efficiency: 120 mi / 40 kWh = 3.0 mi/kWh
        assertNotNull(weekly.milesPerKwh)
        assertEquals(3.0, weekly.milesPerKwh!!, 0.01)

        // Monthly metrics (30D)
        val monthly = bentoState.metricsFor(InsightsTimeGranularity.MONTHLY)
        assertTrue(monthly.hasOdometerData)
        assertEquals(600, monthly.totalMiles)
        assertEquals(67, monthly.fleetSharePct)

        // Lifetime TCO metrics
        assertEquals(200.0, bentoState.lifetimeMaintenanceCost, 0.01)
        assertEquals(8.80, bentoState.lifetimeChargingCost, 0.01)
        assertEquals(208.80, bentoState.lifetimeTotalCost, 0.01)
        assertNotNull(bentoState.lifetimeAllInCostPerMile)
        // $208.80 over 10,000 tracked miles = $0.02088/mi
        assertEquals(0.02088, bentoState.lifetimeAllInCostPerMile!!, 0.0005)
    }

    @Test
    fun compute_handlesWorkVenueThreeWaySplit_avoidedCost_andZeroLifetimeTco() {
        val nov1Ms = LocalDate.of(2026, 11, 1).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val nov15NightMs = LocalDate.of(2026, 11, 15).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val nov18Ms = LocalDate.of(2026, 11, 18).atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

        val logs = listOf(
            OdometerLogEntity("l1", "car_m3", 19790, nov15NightMs, "TESLA_FLEET"),
            OdometerLogEntity("l2", "car_m3", 20000, nov18Ms, "TESLA_FLEET") // 210 mi in current week (70 mi/day)
        )
        val costHistory = VehicleCostHistory(
            lifetimeTotalCost = 100.0,
            lifetimeCostPerMile = 0.01,
            yearlyBreakdowns = emptyList(),
            averageAnnualSpend = 100.0
        )
        val bill = UtilityBillEntity(
            id = "b1",
            providerName = "DTE Energy",
            startDateMs = nov1Ms,
            endDateMs = nov18Ms,
            totalKwh = 1000.0,
            totalAmount = 200.0,
            fixedCharges = 0.0,
            calculatedRate = 0.20,
            effectiveRate = 0.20,
            uploadedAtMs = nov18Ms
        )

        val nov16Ms = LocalDate.of(2026, 11, 16).atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
        val sessions = listOf(
            ChargingSessionEntity(
                id = "h1",
                startDateMs = nov16Ms,
                endDateMs = nov16Ms + 3_600_000L,
                kwh = 25.0, // 50% -> $5.00 @ $0.20
                carId = "car_m3",
                fingerprint = "fp_h1",
                uploadedAtMs = nov18Ms,
                venue = "HOME"
            ),
            ChargingSessionEntity(
                id = "w1",
                startDateMs = nov16Ms + 7_200_000L,
                endDateMs = nov16Ms + 10_800_000L,
                kwh = 15.0, // 30% -> $0.00 actual, $3.00 avoided @ $0.20
                carId = "car_m3",
                fingerprint = "fp_w1",
                uploadedAtMs = nov18Ms,
                venue = "WORK",
                directCost = 0.0
            ),
            ChargingSessionEntity(
                id = "p1",
                startDateMs = nov16Ms + 14_400_000L,
                endDateMs = nov16Ms + 18_000_000L,
                kwh = 10.0, // 20% -> $4.50 paid
                carId = "car_m3",
                fingerprint = "fp_p1",
                uploadedAtMs = nov18Ms,
                venue = "PUBLIC",
                directCost = 4.50
            )
        )

        val bentoState = VehicleInsightsBentoCalculator.compute(
            targetCar = model3,
            allCars = listOf(model3),
            allLogs = logs,
            records = emptyList(),
            costHistory = costHistory,
            utilityBills = listOf(bill),
            chargingSessions = sessions,
            gasPrice = 3.60f,
            baselineMpg = 28.0f,
            defaultElectricityRate = 0.16f,
            useCustomElectricityRate = false,
            nowMs = nov18Ms
        )

        val weekly = bentoState.metricsFor(InsightsTimeGranularity.WEEKLY)
        assertTrue(weekly.hasChargingData)
        assertEquals(50.0, weekly.totalKwh, 0.01)
        assertEquals(25.0, weekly.homeKwh, 0.01)
        assertEquals(15.0, weekly.workKwh, 0.01)
        assertEquals(10.0, weekly.publicKwh, 0.01)
        assertEquals(50, weekly.homePct)
        assertEquals(30, weekly.workPct)
        assertEquals(20, weekly.publicPct)
        assertEquals(100, weekly.homePct + weekly.workPct + weekly.publicPct)
        assertEquals(3.00, weekly.workAvoidedCost, 0.01)
        // Actual spend = $5.00 Home + $0.00 Work + $4.50 Public = $9.50
        assertEquals(9.50, weekly.totalElectricityCost, 0.01)
        // Combined efficiency = 210 mi / 50 kWh = 4.2 mi/kWh
        assertEquals(4.2, weekly.milesPerKwh!!, 0.01)
        // Lifetime TCO charging cost MUST exclude free work kWh ($5.00 + $4.50 = $9.50, NOT $12.50)
        assertEquals(9.50, bentoState.lifetimeChargingCost, 0.01)
        assertEquals(109.50, bentoState.lifetimeTotalCost, 0.01)
    }
}

