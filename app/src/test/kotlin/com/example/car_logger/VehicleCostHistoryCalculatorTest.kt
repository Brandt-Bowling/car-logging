package com.example.car_logger

import com.example.car_logger.data.insights.VehicleCostHistoryCalculator
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCostHistoryCalculatorTest {

    @Test
    fun emptyRecordsReturnZeroCostHistory() {
        val result = VehicleCostHistoryCalculator.calculate(
            records = emptyList(),
            car = Car(id = "c1", make = "Tesla", model = "Model 3", year = 2024, odometer = 10_000, isEv = true)
        )
        assertEquals(0.0, result.lifetimeTotalCost, 1e-6)
        assertNull(result.lifetimeCostPerMile)
        assertTrue(result.yearlyBreakdowns.isEmpty())
        assertEquals(0.0, result.averageAnnualSpend, 1e-6)
    }

    @Test
    fun calculatesYearlyBreakdownsAndLifetimeCostPerMile() {
        val car = Car(id = "c1", make = "Honda", model = "Civic", year = 2021, odometer = 35_000, isEv = false)
        val records = listOf(
            MaintenanceRecord(
                id = "r1",
                carId = "c1",
                title = "Oil Change",
                date = "2024-03-01",
                odometer = 15_000,
                cost = 80.0
            ),
            MaintenanceRecord(
                id = "r2",
                carId = "c1",
                title = "Brake Pads",
                date = "2024-09-15",
                odometer = 25_000,
                cost = 420.0
            ),
            MaintenanceRecord(
                id = "r3",
                carId = "c1",
                title = "Tire Alignment",
                date = "2025-05-10",
                odometer = 30_000,
                cost = 150.0
            ),
            MaintenanceRecord(
                id = "r4",
                carId = "c1",
                title = "Free Inspection",
                date = "2025-06-01",
                odometer = 31_000,
                cost = 0.0
            )
        )

        val result = VehicleCostHistoryCalculator.calculate(records = records, car = car)

        assertEquals(650.0, result.lifetimeTotalCost, 1e-6)
        // Lifetime miles = car.odometer (35_000) - min valid record odometer (15_000) = 20_000
        assertNotNull(result.lifetimeCostPerMile)
        assertEquals(650.0 / 20_000.0, result.lifetimeCostPerMile!!, 1e-6)
        assertEquals(325.0, result.averageAnnualSpend, 1e-6)
        assertEquals(listOf(2025, 2024), result.yearlyBreakdowns.map { it.year })

        val year2025 = result.yearlyBreakdowns[0]
        assertEquals(150.0, year2025.totalCost, 1e-6)
        assertEquals(1, year2025.recordCount)
        assertNull(year2025.milesDriven)

        val year2024 = result.yearlyBreakdowns[1]
        assertEquals(500.0, year2024.totalCost, 1e-6)
        assertEquals(2, year2024.recordCount)
        assertEquals(10_000, year2024.milesDriven)
        assertEquals(0.05, year2024.costPerMile!!, 1e-6)
    }
}
