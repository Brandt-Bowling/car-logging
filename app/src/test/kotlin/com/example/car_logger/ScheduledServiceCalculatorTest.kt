package com.example.car_logger

import com.example.car_logger.data.insights.ScheduledServiceCalculator
import com.example.car_logger.data.local.entity.TireEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledServiceCalculatorTest {

    @Test
    fun `calculates EV tire rotation and ICE oil change due intervals and urgent flags`() {
        val evCar = Car(
            id = "ev-1",
            make = "Tesla",
            model = "Model 3",
            year = 2024,
            odometer = 14_700,
            isEv = true,
            tireRotationInterval = 7_500
        )
        val iceCar = Car(
            id = "ice-1",
            make = "Honda",
            model = "Civic",
            year = 2020,
            odometer = 45_200,
            isEv = false
        )
        val records = listOf(
            MaintenanceRecord(
                id = "r1",
                carId = "ev-1",
                title = "Tire Rotation",
                date = "2026-01-10",
                odometer = 7_500
            ),
            MaintenanceRecord(
                id = "r2",
                carId = "ice-1",
                title = "Oil Change",
                date = "2025-11-01",
                odometer = 42_000
            )
        )

        val result = ScheduledServiceCalculator.calculateScheduledServices(
            cars = listOf(iceCar, evCar),
            records = records,
            tireEntities = emptyList()
        )

        assertEquals(2, result.size)
        // EV: due at 7500 + 7500 = 15000, remaining = 300 (urgent)
        assertEquals("ev-1", result[0].car.id)
        assertEquals("Tire Rotation & Cabin Filter Inspection", result[0].title)
        assertEquals(15_000, result[0].dueOdometer)
        assertEquals(300, result[0].remainingMiles)
        assertTrue(result[0].isUrgent)

        // ICE: due at 42000 + 5000 = 47000, remaining = 1800 (not urgent)
        assertEquals("ice-1", result[1].car.id)
        assertEquals("Oil & Filter Change", result[1].title)
        assertEquals(47_000, result[1].dueOdometer)
        assertEquals(1_800, result[1].remainingMiles)
        assertFalse(result[1].isUrgent)
    }

    @Test
    fun `skips archived cars and cars without odometer and respects active tire last rotation odometer`() {
        val archivedCar = Car(id = "arch", make = "Ford", model = "Focus", year = 2015, odometer = 90_000, isArchived = true)
        val noOdoCar = Car(id = "noodo", make = "Chevy", model = "Volt", year = 2018, odometer = null, isEv = true)
        val evWithTire = Car(id = "ev-2", make = "Chevy", model = "Blazer EV", year = 2025, odometer = 9_600, isEv = true, tireRotationInterval = 5_000)
        val activeTire = TireEntity(
            id = "tire-1",
            carId = "ev-2",
            manufacturer = "Michelin",
            model = "Primacy",
            dateInstalled = "2025-06-01",
            odometerInstalled = 0,
            estimatedTreadLifeMiles = 50_000,
            lastRotationDate = "2025-12-01",
            lastRotationOdometer = 5_000
        )

        val result = ScheduledServiceCalculator.calculateScheduledServices(
            cars = listOf(archivedCar, noOdoCar, evWithTire),
            records = emptyList(),
            tireEntities = listOf(activeTire)
        )

        assertEquals(1, result.size)
        assertEquals("ev-2", result.first().car.id)
        assertEquals(10_000, result.first().dueOdometer)
        assertEquals(400, result.first().remainingMiles)
        assertTrue(result.first().isUrgent)
    }
}
