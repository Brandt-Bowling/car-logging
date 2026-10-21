package com.example.car_logger

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlutterDataMigrationFormatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testParseFlutterCarsJson() {
        val legacyJson = """
            [
                {
                    "id": "c1",
                    "make": "Chevrolet",
                    "model": "Sonic",
                    "year": 2013,
                    "imageUrl": "https://example.com/sonic.jpg",
                    "localImagePath": null,
                    "licensePlate": "ABC-1234",
                    "vin": "1G1JC5SH4D4123456",
                    "odometer": 45000,
                    "isEv": false,
                    "isArchived": false,
                    "trim": "LT",
                    "bodyStyle": "Hatchback"
                },
                {
                    "id": "c2",
                    "make": "Tesla",
                    "model": "Model 3",
                    "year": 2018,
                    "imageUrl": null,
                    "isEv": true,
                    "isArchived": true
                }
            ]
        """.trimIndent()

        val array = json.parseToJsonElement(legacyJson).jsonArray
        assertEquals(2, array.size)

        val car1 = array[0].jsonObject
        assertEquals("c1", car1["id"]?.jsonPrimitive?.content)
        assertEquals("Chevrolet", car1["make"]?.jsonPrimitive?.content)
        assertEquals(2013, car1["year"]?.jsonPrimitive?.intOrNull)
        assertEquals(45000, car1["odometer"]?.jsonPrimitive?.intOrNull)
        assertFalse(car1["isEv"]?.jsonPrimitive?.booleanOrNull ?: true)

        val car2 = array[1].jsonObject
        assertEquals("c2", car2["id"]?.jsonPrimitive?.content)
        assertTrue(car2["isEv"]?.jsonPrimitive?.booleanOrNull ?: false)
        assertTrue(car2["isArchived"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    @Test
    fun testParseFlutterRecordsJson() {
        val recordsJson = """
            [
                {
                    "id": "rec_1",
                    "carId": "c1",
                    "title": "Oil Change & Filter",
                    "date": "2024-03-15T00:00:00.000",
                    "odometer": 45000,
                    "description": "Synthetic oil change",
                    "receiptUrl": null,
                    "cost": 65.5,
                    "driveFileId": "drive_12345"
                }
            ]
        """.trimIndent()

        val array = json.parseToJsonElement(recordsJson).jsonArray
        assertEquals(1, array.size)

        val rec = array[0].jsonObject
        assertEquals("rec_1", rec["id"]?.jsonPrimitive?.content)
        assertEquals("c1", rec["carId"]?.jsonPrimitive?.content)
        assertEquals(45000, rec["odometer"]?.jsonPrimitive?.intOrNull)
        assertEquals(65.5, rec["cost"]?.jsonPrimitive?.doubleOrNull ?: 0.0, 0.001)
        assertEquals("drive_12345", rec["driveFileId"]?.jsonPrimitive?.content)
    }
}
