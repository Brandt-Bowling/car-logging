package com.example.car_logger.data.migration

import android.content.Context
import android.util.Log
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object FlutterDataMigrator {
    private const val TAG = "FlutterDataMigrator"
    private const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"
    private const val FLUTTER_LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun asStringOrNull(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        return try {
            val str = element.jsonPrimitive.content
            if (str.equals("null", ignoreCase = true) || str.isBlank()) null else str
        } catch (_: Exception) {
            null
        }
    }

    fun asIntOrNull(element: JsonElement?): Int? {
        if (element == null || element is JsonNull) return null
        return try {
            element.jsonPrimitive.intOrNull
                ?: element.jsonPrimitive.content.toIntOrNull()
                ?: element.jsonPrimitive.doubleOrNull?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    fun asDoubleOrNull(element: JsonElement?): Double? {
        if (element == null || element is JsonNull) return null
        return try {
            element.jsonPrimitive.doubleOrNull
                ?: element.jsonPrimitive.content.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun asBooleanOrNull(element: JsonElement?): Boolean? {
        if (element == null || element is JsonNull) return null
        return try {
            element.jsonPrimitive.booleanOrNull
                ?: element.jsonPrimitive.content.toBooleanStrictOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun migrateIfNeeded(context: Context, database: AppDatabase, preferencesManager: PreferencesManager) {
        if (preferencesManager.isMigrationV2Complete()) {
            Log.d(TAG, "Migration v2 already completed previously.")
            return
        }

        val carDao = database.carDao()
        val recordDao = database.maintenanceRecordDao()

        val flutterPrefs = context.getSharedPreferences(FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyCarsJson = flutterPrefs.getString("flutter.car_logger_cars", null)
            ?: flutterPrefs.getString("car_logger_cars", null)

        var migratedCarsCount = 0
        var migratedRecordsCount = 0

        if (!legacyCarsJson.isNullOrBlank()) {
            try {
                val carsArray = json.parseToJsonElement(legacyCarsJson).jsonArray
                val carEntities = mutableListOf<CarEntity>()

                for (elem in carsArray) {
                    val obj = elem.jsonObject
                    val id = asStringOrNull(obj["id"]) ?: continue
                    val make = asStringOrNull(obj["make"]) ?: ""
                    val model = asStringOrNull(obj["model"]) ?: ""
                    val year = asIntOrNull(obj["year"]) ?: 0
                    val imageUrl = asStringOrNull(obj["imageUrl"])
                    val localImagePath = asStringOrNull(obj["localImagePath"])
                    val licensePlate = asStringOrNull(obj["licensePlate"])
                    val vin = asStringOrNull(obj["vin"])
                    val legacyOdo = asIntOrNull(obj["odometer"])
                    val isEv = asBooleanOrNull(obj["isEv"]) ?: false
                    val isArchived = asBooleanOrNull(obj["isArchived"]) ?: false
                    val trim = asStringOrNull(obj["trim"])
                    val bodyStyle = asStringOrNull(obj["bodyStyle"])
                    val tireRotationInterval = asIntOrNull(obj["tireRotationInterval"])

                    val existingCar = carDao.getCarById(id)
                    val resolvedOdometer = listOfNotNull(existingCar?.odometer, legacyOdo).maxOrNull()

                    val carEntity = CarEntity(
                        id = id,
                        make = make,
                        model = model,
                        year = year,
                        imageUrl = imageUrl ?: existingCar?.imageUrl?.takeIf { !it.equals("null", ignoreCase = true) },
                        localImagePath = localImagePath ?: existingCar?.localImagePath?.takeIf { !it.equals("null", ignoreCase = true) },
                        licensePlate = licensePlate,
                        vin = vin,
                        odometer = resolvedOdometer,
                        isEv = isEv,
                        isArchived = existingCar?.isArchived ?: isArchived,
                        trim = trim,
                        bodyStyle = bodyStyle,
                        tireRotationInterval = tireRotationInterval ?: existingCar?.tireRotationInterval
                    )
                    carEntities.add(carEntity)
                }

                // CRITICAL: Insert cars FIRST so foreign keys exist in Room for child maintenance records
                if (carEntities.isNotEmpty()) {
                    carDao.insertCars(carEntities)
                    migratedCarsCount = carEntities.size
                    Log.i(TAG, "Successfully inserted/updated $migratedCarsCount cars.")
                }

                // Collect and insert records for all cars
                val recordEntities = mutableListOf<MaintenanceRecordEntity>()
                val processedRecordKeys = mutableSetOf<String>()

                // 1. Check known cars for records
                for (car in carEntities) {
                    val candidateKeys = listOf(
                        "flutter.car_logger_records_${car.id}",
                        "car_logger_records_${car.id}"
                    )
                    for (recordsKey in candidateKeys) {
                        if (processedRecordKeys.contains(recordsKey)) continue
                        val recordsJson = flutterPrefs.getString(recordsKey, null)
                        if (!recordsJson.isNullOrBlank()) {
                            processedRecordKeys.add(recordsKey)
                            parseRecordsJson(recordsJson, car.id, recordEntities)
                        }
                    }
                }

                // 2. Scan all SharedPreferences keys for any additional record keys
                val allPrefs = flutterPrefs.all
                for ((key, value) in allPrefs) {
                    if (key.startsWith("flutter.car_logger_records_") || key.startsWith("car_logger_records_")) {
                        if (processedRecordKeys.contains(key)) continue
                        val jsonStr = value as? String ?: continue
                        if (jsonStr.isBlank()) continue
                        val carIdFromKey = key.removePrefix("flutter.car_logger_records_")
                            .removePrefix("car_logger_records_")
                        processedRecordKeys.add(key)
                        parseRecordsJson(jsonStr, carIdFromKey, recordEntities)
                    }
                }

                // Filter records to ensure foreign key constraint is satisfied (carId must exist in carDao)
                val validRecordEntities = recordEntities.filter { rec ->
                    carDao.getCarById(rec.carId) != null
                }

                if (validRecordEntities.isNotEmpty()) {
                    recordDao.insertRecords(validRecordEntities)
                    migratedRecordsCount = validRecordEntities.size
                    Log.i(TAG, "Successfully migrated $migratedRecordsCount maintenance records.")
                }

                // Migrate preferences and settings
                val garageName = flutterPrefs.getString("flutter.car_logger_garage_name", null)
                    ?: flutterPrefs.getString("car_logger_garage_name", null)
                if (!garageName.isNullOrBlank() && !garageName.equals("null", ignoreCase = true)) {
                    preferencesManager.setGarageName(garageName)
                }

                val syncFolderId = flutterPrefs.getString("flutter.car_logger_sync_folder_id", null)
                    ?: flutterPrefs.getString("car_logger_sync_folder_id", null)
                val syncFolderName = flutterPrefs.getString("flutter.car_logger_sync_folder_name", null)
                    ?: flutterPrefs.getString("car_logger_sync_folder_name", null)
                if (!syncFolderId.isNullOrBlank() && !syncFolderId.equals("null", ignoreCase = true)) {
                    preferencesManager.setSyncFolder(
                        syncFolderId,
                        syncFolderName?.takeIf { !it.equals("null", ignoreCase = true) }
                    )
                }

                val geminiKey = flutterPrefs.getString("flutter.car_logger_gemini_api_key", null)
                    ?: flutterPrefs.getString("car_logger_gemini_api_key", null)
                if (!geminiKey.isNullOrBlank() && !geminiKey.equals("null", ignoreCase = true)) {
                    preferencesManager.setGeminiApiKey(geminiKey)
                }

                val autoDevKey = flutterPrefs.getString("flutter.car_logger_auto_dev_api_key", null)
                    ?: flutterPrefs.getString("car_logger_auto_dev_api_key", null)
                if (!autoDevKey.isNullOrBlank() && !autoDevKey.equals("null", ignoreCase = true)) {
                    preferencesManager.setAutoDevApiKey(autoDevKey)
                }

                // Migrate imported files
                val importedFilesSet = flutterPrefs.getStringSet("flutter.car_logger_imported_files", null)
                    ?: flutterPrefs.getStringSet("car_logger_imported_files", null)
                if (importedFilesSet != null) {
                    preferencesManager.setImportedFileIds(importedFilesSet)
                } else {
                    val importedFilesJson = flutterPrefs.getString("flutter.car_logger_imported_files", null)
                        ?: flutterPrefs.getString("car_logger_imported_files", null)
                    if (!importedFilesJson.isNullOrBlank()) {
                        try {
                            val cleanJson = if (importedFilesJson.startsWith(FLUTTER_LIST_PREFIX)) {
                                importedFilesJson.removePrefix(FLUTTER_LIST_PREFIX)
                            } else {
                                importedFilesJson
                            }
                            val parsedSet = json.parseToJsonElement(cleanJson)
                                .jsonArray
                                .mapNotNull { asStringOrNull(it) }
                                .toSet()
                            preferencesManager.setImportedFileIds(parsedSet)
                        } catch (_: Exception) {}
                    }
                }

                Log.i(TAG, "Migrated $migratedCarsCount cars and $migratedRecordsCount records from Flutter.")
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating data from FlutterSharedPreferences: ${e.message}", e)
            }
        }

        // Sanitize any corrupt "null" strings that may already be in the database from earlier runs
        sanitizeExistingDatabase(carDao, recordDao)

        // If no cars existed in database after migration attempt, seed initial default cars
        if (carDao.getCarCount() == 0) {
            seedDefaultCars(carDao, recordDao)
        }

        preferencesManager.setMigrationComplete(true)
        preferencesManager.setMigrationV2Complete(true)
    }

    private fun parseRecordsJson(
        recordsJson: String,
        fallbackCarId: String,
        destination: MutableList<MaintenanceRecordEntity>
    ) {
        try {
            val recordsArray = json.parseToJsonElement(recordsJson).jsonArray
            for (recElem in recordsArray) {
                val recObj = recElem.jsonObject
                val recId = asStringOrNull(recObj["id"]) ?: continue
                val recCarId = asStringOrNull(recObj["carId"]) ?: fallbackCarId
                val recTitle = asStringOrNull(recObj["title"]) ?: ""
                val recDate = asStringOrNull(recObj["date"]) ?: ""
                val recOdometer = asIntOrNull(recObj["odometer"]) ?: 0
                val recDesc = asStringOrNull(recObj["description"])
                val recReceipt = asStringOrNull(recObj["receiptUrl"])
                val recCost = asDoubleOrNull(recObj["cost"])
                val recDriveId = asStringOrNull(recObj["driveFileId"])

                destination.add(
                    MaintenanceRecordEntity(
                        id = recId,
                        carId = recCarId,
                        title = recTitle,
                        date = recDate,
                        odometer = recOdometer,
                        description = recDesc,
                        receiptUrl = recReceipt,
                        cost = recCost,
                        driveFileId = recDriveId
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse records JSON: ${e.message}", e)
        }
    }

    private suspend fun sanitizeExistingDatabase(carDao: CarDao, recordDao: MaintenanceRecordDao) {
        try {
            val allCars = carDao.getAllCarsList()
            for (car in allCars) {
                val cleaned = car.cleanNullStrings()
                if (cleaned != car) {
                    carDao.updateCar(cleaned)
                }
            }

            val allRecords = recordDao.getAllRecordsList()
            for (rec in allRecords) {
                val cleaned = rec.cleanNullStrings()
                if (cleaned != rec) {
                    recordDao.updateRecord(cleaned)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing database: ${e.message}", e)
        }
    }

    private suspend fun seedDefaultCars(carDao: CarDao, recordDao: MaintenanceRecordDao) {
        val defaultCars = listOf(
            CarEntity(
                id = "1",
                make = "Chevrolet",
                model = "Sonic",
                year = 2013,
                odometer = 45000,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/f/f9/2012_Chevrolet_Sonic_1LT_hatchback_--_10-19-2011_front_1.jpg",
                isEv = false
            ),
            CarEntity(
                id = "2",
                make = "Tesla",
                model = "Model 3",
                year = 2018,
                odometer = 32000,
                imageUrl = "https://api.auto.dev/photos/retail/5YJ3E1EAXMF860954-1.jpg",
                isEv = true
            )
        )
        carDao.insertCars(defaultCars)

        val defaultRecords = listOf(
            MaintenanceRecordEntity(
                id = "mock_1",
                carId = "1",
                title = "Oil Change & Filter",
                date = "2024-03-15",
                odometer = 45000,
                cost = 65.0,
                description = "Regular synthetic oil and oil filter replacement."
            ),
            MaintenanceRecordEntity(
                id = "mock_2",
                carId = "1",
                title = "Brake Pad Replacement & Rotor Resurface",
                date = "2023-09-15",
                odometer = 38200,
                cost = 320.0,
                description = "Front ceramic brake pads installed and rotors resurfaced."
            ),
            MaintenanceRecordEntity(
                id = "mock_3",
                carId = "1",
                title = "Tire Rotation & Wheel Balance",
                date = "2023-04-10",
                odometer = 33500,
                cost = 45.0,
                description = "Rotated all 4 tires and rebalanced front wheels."
            ),
            MaintenanceRecordEntity(
                id = "mock_4",
                carId = "1",
                title = "Battery Replacement",
                date = "2022-11-20",
                odometer = 28000,
                cost = 165.0,
                description = "Installed new AGM 12V battery with 3-year warranty."
            )
        )
        recordDao.insertRecords(defaultRecords)
        Log.i(TAG, "Seeded default initial vehicles (Chevy Sonic & Tesla Model 3).")
    }
}
