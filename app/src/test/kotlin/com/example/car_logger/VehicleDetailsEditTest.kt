package com.example.car_logger

import com.example.car_logger.data.model.Car
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDetailsEditTest {

    @Test
    fun testVinNormalizationAndEmptyHandling() {
        val car = Car(id = "c1", make = "Tesla", model = "Model 3", year = 2023, vin = "initial_vin")

        // Normalizing uppercase and trimming
        val inputVin = "  5yj3e1eb4kf123456  "
        val normalizedVin = inputVin.trim().uppercase().ifBlank { null }
        val updatedCar = car.copy(vin = normalizedVin)

        assertEquals("5YJ3E1EB4KF123456", updatedCar.vin)

        // Blank input should result in null
        val blankVin = "   "
        val blankNormalized = blankVin.trim().uppercase().ifBlank { null }
        val clearedCar = car.copy(vin = blankNormalized)

        assertNull(clearedCar.vin)
    }

    @Test
    fun testLicensePlateNormalizationAndEmptyHandling() {
        val car = Car(id = "c1", make = "Tesla", model = "Model 3", year = 2023, licensePlate = "OLD-PLATE")

        // Normalizing uppercase and trimming
        val inputPlate = "  7xyz89  "
        val normalizedPlate = inputPlate.trim().uppercase().ifBlank { null }
        val updatedCar = car.copy(licensePlate = normalizedPlate)

        assertEquals("7XYZ89", updatedCar.licensePlate)

        // Blank input should result in null
        val blankPlate = ""
        val blankNormalized = blankPlate.trim().uppercase().ifBlank { null }
        val clearedCar = car.copy(licensePlate = blankNormalized)

        assertNull(clearedCar.licensePlate)
    }

    @Test
    fun testVehicleNameAndYearValidation() {
        val car = Car(id = "c1", make = "Tesla", model = "Model 3", year = 2023, trim = "Standard Range Plus")

        val newYearStr = "2024"
        val newMake = "Tesla"
        val newModel = "Model 3 Highland"
        val newTrim = "Long Range"

        val parsedYear = newYearStr.toIntOrNull()
        assertTrue("Year must be a valid integer", parsedYear != null && parsedYear in 1900..2100)

        val updatedCar = car.copy(
            year = parsedYear!!,
            make = newMake.trim(),
            model = newModel.trim(),
            trim = newTrim.trim().ifBlank { null }
        )

        assertEquals(2024, updatedCar.year)
        assertEquals("Tesla", updatedCar.make)
        assertEquals("Model 3 Highland", updatedCar.model)
        assertEquals("2024 Tesla Model 3 Highland", updatedCar.displayName)
        assertEquals("Long Range", updatedCar.trim)
    }
}
