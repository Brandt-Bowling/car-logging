package com.example.car_logger

import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.migration.FlutterDataMigrator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlutterDataMigrationFormatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testJsonHelperMethods() {
        // String helper
        assertEquals("hello", FlutterDataMigrator.asStringOrNull(JsonPrimitive("hello")))
        assertNull(FlutterDataMigrator.asStringOrNull(JsonNull))
        assertNull(FlutterDataMigrator.asStringOrNull(JsonPrimitive("null")))
        assertNull(FlutterDataMigrator.asStringOrNull(JsonPrimitive("NULL")))
        assertNull(FlutterDataMigrator.asStringOrNull(JsonPrimitive("   ")))
        assertNull(FlutterDataMigrator.asStringOrNull(null))

        // Int helper
        assertEquals(45000, FlutterDataMigrator.asIntOrNull(JsonPrimitive(45000)))
        assertEquals(45000, FlutterDataMigrator.asIntOrNull(JsonPrimitive("45000")))
        assertEquals(45000, FlutterDataMigrator.asIntOrNull(JsonPrimitive(45000.0)))
        assertNull(FlutterDataMigrator.asIntOrNull(JsonNull))
        assertNull(FlutterDataMigrator.asIntOrNull(JsonPrimitive("invalid")))
        assertNull(FlutterDataMigrator.asIntOrNull(null))

        // Double helper
        assertEquals(65.5, FlutterDataMigrator.asDoubleOrNull(JsonPrimitive(65.5))!!, 0.001)
        assertEquals(65.0, FlutterDataMigrator.asDoubleOrNull(JsonPrimitive(65))!!, 0.001)
        assertEquals(65.5, FlutterDataMigrator.asDoubleOrNull(JsonPrimitive("65.5"))!!, 0.001)
        assertNull(FlutterDataMigrator.asDoubleOrNull(JsonNull))
        assertNull(FlutterDataMigrator.asDoubleOrNull(JsonPrimitive("null")))
        assertNull(FlutterDataMigrator.asDoubleOrNull(null))

        // Boolean helper
        assertTrue(FlutterDataMigrator.asBooleanOrNull(JsonPrimitive(true))!!)
        assertFalse(FlutterDataMigrator.asBooleanOrNull(JsonPrimitive(false))!!)
        assertTrue(FlutterDataMigrator.asBooleanOrNull(JsonPrimitive("true"))!!)
        assertNull(FlutterDataMigrator.asBooleanOrNull(JsonNull))
        assertNull(FlutterDataMigrator.asBooleanOrNull(null))
    }

    @Test
    fun testParseFlutterCarsJsonWithNullFields() {
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
                    "localImagePath": null,
                    "licensePlate": null,
                    "vin": null,
                    "odometer": 170308,
                    "isEv": true,
                    "isArchived": false,
                    "trim": null,
                    "bodyStyle": null,
                    "tireRotationInterval": 6250
                }
            ]
        """.trimIndent()

        val array = json.parseToJsonElement(legacyJson).jsonArray
        assertEquals(2, array.size)

        val car1 = array[0].jsonObject
        assertEquals("c1", FlutterDataMigrator.asStringOrNull(car1["id"]))
        assertEquals("Chevrolet", FlutterDataMigrator.asStringOrNull(car1["make"]))
        assertEquals(2013, FlutterDataMigrator.asIntOrNull(car1["year"]))
        assertEquals(45000, FlutterDataMigrator.asIntOrNull(car1["odometer"]))
        assertFalse(FlutterDataMigrator.asBooleanOrNull(car1["isEv"]) ?: true)
        assertEquals("ABC-1234", FlutterDataMigrator.asStringOrNull(car1["licensePlate"]))
        assertNull(FlutterDataMigrator.asStringOrNull(car1["localImagePath"]))
        assertNull(FlutterDataMigrator.asIntOrNull(car1["tireRotationInterval"]))

        val car2 = array[1].jsonObject
        assertEquals("c2", FlutterDataMigrator.asStringOrNull(car2["id"]))
        assertEquals("Tesla", FlutterDataMigrator.asStringOrNull(car2["make"]))
        assertEquals("Model 3", FlutterDataMigrator.asStringOrNull(car2["model"]))
        assertEquals(170308, FlutterDataMigrator.asIntOrNull(car2["odometer"]))
        assertTrue(FlutterDataMigrator.asBooleanOrNull(car2["isEv"]) ?: false)
        assertFalse(FlutterDataMigrator.asBooleanOrNull(car2["isArchived"]) ?: true)
        assertEquals(6250, FlutterDataMigrator.asIntOrNull(car2["tireRotationInterval"]))

        // Verify null fields do NOT parse to literal "null" string
        assertNull(FlutterDataMigrator.asStringOrNull(car2["imageUrl"]))
        assertNull(FlutterDataMigrator.asStringOrNull(car2["localImagePath"]))
        assertNull(FlutterDataMigrator.asStringOrNull(car2["licensePlate"]))
        assertNull(FlutterDataMigrator.asStringOrNull(car2["vin"]))
        assertNull(FlutterDataMigrator.asStringOrNull(car2["trim"]))
        assertNull(FlutterDataMigrator.asStringOrNull(car2["bodyStyle"]))
    }

    @Test
    fun testParseFlutterRecordsJsonWithVariousTypes() {
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
                },
                {
                    "id": "rec_2",
                    "carId": "c2",
                    "title": "Tire Rotation",
                    "date": "2024-04-10T12:00:00.000",
                    "odometer": 170000,
                    "description": null,
                    "receiptUrl": null,
                    "cost": 45,
                    "driveFileId": null
                },
                {
                    "id": "rec_3",
                    "carId": "c2",
                    "title": "Brake Service",
                    "date": "2024-06-01",
                    "odometer": 170200,
                    "description": "Clean and lube calipers",
                    "receiptUrl": null,
                    "cost": "120.75",
                    "driveFileId": null
                }
            ]
        """.trimIndent()

        val array = json.parseToJsonElement(recordsJson).jsonArray
        assertEquals(3, array.size)

        // rec 1
        val rec1 = array[0].jsonObject
        assertEquals("rec_1", FlutterDataMigrator.asStringOrNull(rec1["id"]))
        assertEquals("c1", FlutterDataMigrator.asStringOrNull(rec1["carId"]))
        assertEquals(45000, FlutterDataMigrator.asIntOrNull(rec1["odometer"]))
        assertEquals(65.5, FlutterDataMigrator.asDoubleOrNull(rec1["cost"]) ?: 0.0, 0.001)
        assertEquals("drive_12345", FlutterDataMigrator.asStringOrNull(rec1["driveFileId"]))
        assertNull(FlutterDataMigrator.asStringOrNull(rec1["receiptUrl"]))

        // rec 2 (integer cost, null description/receiptUrl/driveFileId)
        val rec2 = array[1].jsonObject
        assertEquals("rec_2", FlutterDataMigrator.asStringOrNull(rec2["id"]))
        assertEquals("c2", FlutterDataMigrator.asStringOrNull(rec2["carId"]))
        assertEquals(170000, FlutterDataMigrator.asIntOrNull(rec2["odometer"]))
        assertEquals(45.0, FlutterDataMigrator.asDoubleOrNull(rec2["cost"]) ?: 0.0, 0.001)
        assertNull(FlutterDataMigrator.asStringOrNull(rec2["description"]))
        assertNull(FlutterDataMigrator.asStringOrNull(rec2["receiptUrl"]))
        assertNull(FlutterDataMigrator.asStringOrNull(rec2["driveFileId"]))

        // rec 3 (string cost)
        val rec3 = array[2].jsonObject
        assertEquals(120.75, FlutterDataMigrator.asDoubleOrNull(rec3["cost"]) ?: 0.0, 0.001)
    }

    @Test
    fun testCorruptedEntitiesSanitization() {
        val corruptedCar = CarEntity(
            id = "c2",
            make = "Tesla",
            model = "Model 3",
            year = 2018,
            imageUrl = "null",
            localImagePath = "null",
            licensePlate = "null",
            vin = "null",
            odometer = 170308,
            isEv = true,
            isArchived = false,
            trim = "null",
            bodyStyle = "null",
            tireRotationInterval = 6250
        )

        // cleanNullStrings
        val cleanedCar = corruptedCar.cleanNullStrings()
        assertNull(cleanedCar.imageUrl)
        assertNull(cleanedCar.localImagePath)
        assertNull(cleanedCar.licensePlate)
        assertNull(cleanedCar.vin)
        assertNull(cleanedCar.trim)
        assertNull(cleanedCar.bodyStyle)

        // toModel()
        val carModel = corruptedCar.toModel()
        assertNull(carModel.imageUrl)
        assertNull(carModel.localImagePath)
        assertNull(carModel.licensePlate)
        assertNull(carModel.vin)
        assertNull(carModel.trim)
        assertNull(carModel.bodyStyle)
        assertEquals(6250, carModel.effectiveTireRotationInterval)

        val corruptedRecord = MaintenanceRecordEntity(
            id = "r1",
            carId = "c2",
            title = "Tire Rotation",
            date = "2024-04-10",
            odometer = 170000,
            description = "null",
            receiptUrl = "null",
            cost = 45.0,
            driveFileId = "null"
        )

        val cleanedRecord = corruptedRecord.cleanNullStrings()
        assertNull(cleanedRecord.description)
        assertNull(cleanedRecord.receiptUrl)
        assertNull(cleanedRecord.driveFileId)

        val recordModel = corruptedRecord.toModel()
        assertNull(recordModel.description)
        assertNull(recordModel.receiptUrl)
        assertNull(recordModel.driveFileId)
    }
}
