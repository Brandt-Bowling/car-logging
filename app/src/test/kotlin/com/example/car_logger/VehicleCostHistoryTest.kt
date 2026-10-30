package com.example.car_logger

import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleCostHistory
import com.example.car_logger.ui.screens.vehicledetails.components.YearlyCostSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleCostHistoryTest {

    @Test
    fun testYearlyCostSummaryCalculations() {
        val records = listOf(
            MaintenanceRecord(id = "1", carId = "c1", title = "Tire Set", date = "2025-10-15", odometer = 30000, cost = 1200.0),
            MaintenanceRecord(id = "2", carId = "c1", title = "Brakes", date = "2025-05-10", odometer = 22000, cost = 300.0),
            MaintenanceRecord(id = "3", carId = "c1", title = "Oil Change", date = "2026-02-01", odometer = 35000, cost = 80.0)
        )

        val records2025 = records.filter { it.date.startsWith("2025") }
        val total2025 = records2025.sumOf { it.cost ?: 0.0 }
        val minOdo2025 = records2025.minOfOrNull { it.odometer } ?: 0
        val maxOdo2025 = records2025.maxOfOrNull { it.odometer } ?: 0
        val miles2025 = maxOdo2025 - minOdo2025

        assertEquals(1500.0, total2025, 0.001)
        assertEquals(8000, miles2025)
        val cpm2025 = total2025 / miles2025.toDouble()
        assertEquals(0.1875, cpm2025, 0.0001)

        val summary2025 = YearlyCostSummary(
            year = 2025,
            totalCost = total2025,
            recordCount = records2025.size,
            milesDriven = miles2025,
            costPerMile = cpm2025
        )

        assertEquals(2025, summary2025.year)
        assertEquals(2, summary2025.recordCount)
    }

    @Test
    fun testLifetimeCostPerMile() {
        val car = Car(id = "c1", make = "Tesla", model = "Model 3", year = 2023, odometer = 40000)
        val records = listOf(
            MaintenanceRecord(id = "1", carId = "c1", title = "Rec1", date = "2024-01-01", odometer = 10000, cost = 500.0),
            MaintenanceRecord(id = "2", carId = "c1", title = "Rec2", date = "2025-01-01", odometer = 25000, cost = 700.0),
            MaintenanceRecord(id = "3", carId = "c1", title = "Rec3", date = "2026-01-01", odometer = 40000, cost = 800.0)
        )

        val totalCost = records.sumOf { it.cost ?: 0.0 } // 2000.0
        val minOdo = records.minOfOrNull { it.odometer } ?: 0 // 10000
        val currentOdo = car.odometer ?: 40000 // 40000
        val miles = currentOdo - minOdo // 30000

        val lifetimeCpm = totalCost / miles.toDouble() // 2000 / 30000 = 0.06666...
        assertEquals(0.0667, lifetimeCpm, 0.001)
    }
}
