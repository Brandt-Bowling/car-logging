package com.example.car_logger

import com.example.car_logger.data.model.Tire
import org.junit.Assert.assertEquals
import org.junit.Test

class TireModelTest {

    @Test
    fun testRemainingMiles() {
        val tire = Tire(
            id = "t1",
            carId = "c1",
            manufacturer = "Michelin",
            model = "Defender",
            dateInstalled = "2023-01-01",
            odometerInstalled = 40000,
            estimatedTreadLifeMiles = 60000
        )

        // 40000 + 60000 = 100000 max odometer
        assertEquals(55000, tire.remainingMiles(45000))
        assertEquals(0, tire.remainingMiles(105000))
    }

    @Test
    fun testTreadWearPercentage() {
        val tire = Tire(
            id = "t1",
            carId = "c1",
            manufacturer = "Michelin",
            model = "Defender",
            dateInstalled = "2023-01-01",
            odometerInstalled = 40000,
            estimatedTreadLifeMiles = 60000
        )

        // At 40000: 60000 / 60000 = 1.0 (100%)
        assertEquals(1.0f, tire.treadWearPercentage(40000), 0.001f)

        // At 70000: 30000 / 60000 = 0.5 (50%)
        assertEquals(0.5f, tire.treadWearPercentage(70000), 0.001f)

        // At 100000: 0 / 60000 = 0.0 (0%)
        assertEquals(0.0f, tire.treadWearPercentage(100000), 0.001f)
    }
}
