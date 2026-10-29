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

    @Test
    fun testParseImageGenerationErrorWithFreeTierLimitZero() {
        val rawBody = """
            {
              "error": {
                "code": 429,
                "message": "You exceeded your current quota, please check your plan and billing details. Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-2.5-flash-preview-image",
                "status": "RESOURCE_EXHAUSTED"
              }
            }
        """.trimIndent()

        val parsed = GeminiAiService.parseImageGenerationError(429, rawBody)
        assertTrue(parsed.contains("billing enabled"))
        assertTrue(parsed.contains("Free Tier has a limit of 0"))
    }

    @Test
    fun testParseImageGenerationErrorWithAuthFailure() {
        val rawBody = """{"error": {"code": 401, "message": "API key not valid"}}"""
        val parsed = GeminiAiService.parseImageGenerationError(401, rawBody)
        assertTrue(parsed.contains("Invalid or unauthorized Gemini API key"))
    }
}
