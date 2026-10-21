package com.example.car_logger

import com.example.car_logger.data.vehicle.VehicleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDataTest {

    @Test
    fun testAllMakesLoadedAndSorted() {
        val makes = VehicleData.getAllMakes()
        assertTrue(makes.isNotEmpty())
        assertTrue(makes.contains("Chevrolet"))
        assertTrue(makes.contains("Tesla"))
        assertTrue(makes.contains("Toyota"))
        assertEquals("Acura", makes.first())
    }

    @Test
    fun testModelsForMake() {
        val chevyModels = VehicleData.getModelsForMake("Chevrolet")
        assertTrue(chevyModels.contains("Sonic"))
        assertTrue(chevyModels.contains("Corvette"))

        val teslaModels = VehicleData.getModelsForMake("Tesla")
        assertTrue(teslaModels.contains("Model 3"))
        assertTrue(teslaModels.contains("Model Y"))
    }

    @Test
    fun testIsKnownEv() {
        assertTrue(VehicleData.isKnownEv("Tesla", "Model 3"))
        assertTrue(VehicleData.isKnownEv("Ford", "F-150 Lightning"))
        assertTrue(VehicleData.isKnownEv("Hyundai", "Ioniq 5"))
        assertFalse(VehicleData.isKnownEv("Chevrolet", "Sonic"))
        assertFalse(VehicleData.isKnownEv("Toyota", "Camry"))
    }

    @Test
    fun testDefaultImages() {
        assertNotNull(VehicleData.getDefaultImageUrl("Chevrolet", "Sonic"))
        assertNotNull(VehicleData.getDefaultImageUrl("Tesla", "Model 3"))
    }

    @Test
    fun testSanitizeImageUrl() {
        val thumbUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/2012_Chevrolet_Sonic_1LT_hatchback_--_10-19-2011_front_1.jpg/800px-2012_Chevrolet_Sonic_1LT_hatchback_--_10-19-2011_front_1.jpg"
        val directUrl = VehicleData.sanitizeImageUrl(thumbUrl)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/f/f9/2012_Chevrolet_Sonic_1LT_hatchback_--_10-19-2011_front_1.jpg", directUrl)
    }
}
