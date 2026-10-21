package com.example.car_logger.data.migration

import android.content.Context
import android.util.Log
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object FlutterDataMigrator {
    private const val TAG = "FlutterDataMigrator"
    private const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun migrateIfNeeded(context: Context, database: AppDatabase, preferencesManager: PreferencesManager) {
        if (preferencesManager.isMigrationComplete()) {
            Log.d(TAG, "Migration already completed previously.")
            return
        }

        val flutterPrefs = context.getSharedPreferences(FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyCarsJson = flutterPrefs.getString("flutter.car_logger_cars", null)

        val carDao = database.carDao()
        val recordDao = database.maintenanceRecordDao()

        var migratedCarsCount = 0
        var migratedRecordsCount = 0

        if (!legacyCarsJson.isNullOrBlank()) {
            try {
                val carsArray = json.parseToJsonElement(legacyCarsJson).jsonArray
                val carEntities = mutableListOf<CarEntity>()

                for (elem in carsArray) {
                    val obj = elem.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: continue
                    val make = obj["make"]?.jsonPrimitive?.content ?: ""
                    val model = obj["model"]?.jsonPrimitive?.content ?: ""
                    val year = obj["year"]?.jsonPrimitive?.intOrNull ?: 0
                    val imageUrl = obj["imageUrl"]?.jsonPrimitive?.content
                    val localImagePath = obj["localImagePath"]?.jsonPrimitive?.content
                    val licensePlate = obj["licensePlate"]?.jsonPrimitive?.content
                    val vin = obj["vin"]?.jsonPrimitive?.content
                    val odometer = obj["odometer"]?.jsonPrimitive?.intOrNull
                    val isEv = obj["isEv"]?.jsonPrimitive?.booleanOrNull ?: false
                    val isArchived = obj["isArchived"]?.jsonPrimitive?.booleanOrNull ?: false
                    val trim = obj["trim"]?.jsonPrimitive?.content
                    val bodyStyle = obj["bodyStyle"]?.jsonPrimitive?.content

                    val carEntity = CarEntity(
                        id = id,
                        make = make,
                        model = model,
                        year = year,
                        imageUrl = imageUrl,
                        localImagePath = localImagePath,
                        licensePlate = licensePlate,
                        vin = vin,
                        odometer = odometer,
                        isEv = isEv,
                        isArchived = isArchived,
                        trim = trim,
                        bodyStyle = bodyStyle
                    )
                    carEntities.add(carEntity)

                    // Migrate records for this car
                    val recordsKey = "flutter.car_logger_records_$id"
                    val recordsJson = flutterPrefs.getString(recordsKey, null)
                    if (!recordsJson.isNullOrBlank()) {
                        try {
                            val recordsArray = json.parseToJsonElement(recordsJson).jsonArray
                            val recordEntities = mutableListOf<MaintenanceRecordEntity>()
                            for (recElem in recordsArray) {
                                val recObj = recElem.jsonObject
                                val recId = recObj["id"]?.jsonPrimitive?.content ?: continue
                                val recCarId = recObj["carId"]?.jsonPrimitive?.content ?: id
                                val recTitle = recObj["title"]?.jsonPrimitive?.content ?: ""
                                val recDate = recObj["date"]?.jsonPrimitive?.content ?: ""
                                val recOdometer = recObj["odometer"]?.jsonPrimitive?.intOrNull ?: 0
                                val recDesc = recObj["description"]?.jsonPrimitive?.content
                                val recReceipt = recObj["receiptUrl"]?.jsonPrimitive?.content
                                val recCost = recObj["cost"]?.jsonPrimitive?.doubleOrNull
                                val recDriveId = recObj["driveFileId"]?.jsonPrimitive?.content

                                recordEntities.add(
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
                            if (recordEntities.isNotEmpty()) {
                                recordDao.insertRecords(recordEntities)
                                migratedRecordsCount += recordEntities.size
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse records for car $id: ${e.message}", e)
                        }
                    }
                }

                if (carEntities.isNotEmpty()) {
                    carDao.insertCars(carEntities)
                    migratedCarsCount = carEntities.size
                }

                // Migrate preferences and settings
                flutterPrefs.getString("flutter.car_logger_garage_name", null)?.let {
                    preferencesManager.setGarageName(it)
                }
                val syncFolderId = flutterPrefs.getString("flutter.car_logger_sync_folder_id", null)
                val syncFolderName = flutterPrefs.getString("flutter.car_logger_sync_folder_name", null)
                if (syncFolderId != null) {
                    preferencesManager.setSyncFolder(syncFolderId, syncFolderName)
                }
                flutterPrefs.getString("flutter.car_logger_gemini_api_key", null)?.let {
                    preferencesManager.setGeminiApiKey(it)
                }
                flutterPrefs.getString("flutter.car_logger_auto_dev_api_key", null)?.let {
                    preferencesManager.setAutoDevApiKey(it)
                }

                // Migrate imported files
                val importedFilesSet = flutterPrefs.getStringSet("flutter.car_logger_imported_files", null)
                if (importedFilesSet != null) {
                    preferencesManager.setImportedFileIds(importedFilesSet)
                } else {
                    val importedFilesJson = flutterPrefs.getString("flutter.car_logger_imported_files", null)
                    if (!importedFilesJson.isNullOrBlank()) {
                        try {
                            val parsedSet = json.parseToJsonElement(importedFilesJson)
                                .jsonArray
                                .map { it.jsonPrimitive.content }
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

        // If no cars existed in database after migration attempt, seed initial default cars
        if (carDao.getCarCount() == 0) {
            seedDefaultCars(carDao, recordDao)
        }

        preferencesManager.setMigrationComplete(true)
    }

    private suspend fun seedDefaultCars(carDao: com.example.car_logger.data.local.dao.CarDao, recordDao: com.example.car_logger.data.local.dao.MaintenanceRecordDao) {
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
