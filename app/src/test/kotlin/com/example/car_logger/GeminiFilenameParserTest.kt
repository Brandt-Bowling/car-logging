package com.example.car_logger

import com.example.car_logger.services.GeminiAiService
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiFilenameParserTest {

    @Test
    fun testParseFromFileName() {
        val result1 = GeminiAiService.parseFromFileName("2023-05-12_Oil_Change_45000mi_$65.pdf")
        assertEquals("2023-05-12", result1.date)
        assertEquals(45000, result1.odometer)
        assertEquals(65.0, result1.cost)
        assertEquals("Oil & Filter Change", result1.title)

        val result2 = GeminiAiService.parseFromFileName("2024-01-20-Tire-Rotation-52500-miles.jpg")
        assertEquals("2024-01-20", result2.date)
        assertEquals(52500, result2.odometer)
        assertEquals("Tire Rotation", result2.title)
    }
}
