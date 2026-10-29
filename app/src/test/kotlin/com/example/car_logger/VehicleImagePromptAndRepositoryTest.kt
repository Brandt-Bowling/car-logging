package com.example.car_logger

import com.example.car_logger.data.vehicle.VehicleData
import com.example.car_logger.services.GeminiAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleImagePromptAndRepositoryTest {

    @Test
    fun testBuildVehiclePromptWithAllParameters() {
        val service = GeminiAiService()

        val prompt = service.buildVehiclePrompt(
            year = 2024,
            make = "Chevrolet",
            model = "Blazer EV",
            color = "Summit White",
            trim = "RS AWD",
            environment = "clean paved driveway"
        )

        assertTrue(prompt.contains("2024 Chevrolet Blazer EV RS AWD"))
        assertTrue(prompt.contains("Summit White paint"))
        assertTrue(prompt.contains("clean paved driveway"))
        assertTrue(prompt.contains("bright natural daylight"))
    }

    @Test
    fun testBuildVehiclePromptWithDefaults() {
        val service = GeminiAiService()

        val prompt = service.buildVehiclePrompt(
            year = 2023,
            make = "Tesla",
            model = "Model 3",
            color = "Pearl White"
        )

        assertTrue(prompt.contains("2023 Tesla Model 3 in Pearl White paint"))
        assertTrue(prompt.contains("clean paved driveway"))
    }

    @Test
    fun testDefaultChevyBlazerEvImageUrl() {
        val url = VehicleData.getDefaultImageUrl("Chevrolet", "Blazer EV")
        assertNotNull(url)
        assertTrue(url!!.contains("3GNKDCRJ1RS284858-1.jpg"))
    }
}
