package com.example.car_logger.data.vehicle

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.car_logger.data.local.AndroidUriContentReader
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.model.Car
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VehicleImageRepository internal constructor(
    private val imagesDir: File,
    private val uriContentReader: UriContentReader,
    private val carDao: CarDao
) {
    constructor(
        context: Context,
        carDao: CarDao,
        uriContentReader: UriContentReader = AndroidUriContentReader(context.applicationContext ?: context)
    ) : this(
        imagesDir = File((context.applicationContext ?: context).filesDir, DIR_NAME),
        uriContentReader = uriContentReader,
        carDao = carDao
    )

    companion object {
        private const val TAG = "VehicleImageRepository"
        internal const val DIR_NAME = "vehicle_images"
    }

    private fun getVehicleImagesDir(): File {
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        return imagesDir
    }

    suspend fun saveCustomImage(car: Car, imageBytes: ByteArray): Result<Car> = withContext(Dispatchers.IO) {
        try {
            val dir = getVehicleImagesDir()
            deleteOldImageFiles(car.id)

            val file = File(dir, "${car.id}_${System.currentTimeMillis()}.jpg")
            file.writeBytes(imageBytes)

            val updatedCar = car.copy(
                localImagePath = file.absolutePath,
                imageUrl = null
            )
            carDao.updateCar(CarEntity.fromModel(updatedCar))
            Result.success(updatedCar)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom vehicle image for ${car.displayName}", e)
            Result.failure(e)
        }
    }

    suspend fun saveImageFromUri(car: Car, uri: Uri): Result<Car> = withContext(Dispatchers.IO) {
        try {
            val imageBytes = uriContentReader.readBytes(uri)
                ?: return@withContext Result.failure(IllegalStateException("Could not open input stream from URI"))

            val dir = getVehicleImagesDir()
            deleteOldImageFiles(car.id)

            val file = File(dir, "${car.id}_${System.currentTimeMillis()}.jpg")
            file.writeBytes(imageBytes)

            val updatedCar = car.copy(
                localImagePath = file.absolutePath,
                imageUrl = null
            )
            carDao.updateCar(CarEntity.fromModel(updatedCar))
            Result.success(updatedCar)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import image from URI for ${car.displayName}", e)
            Result.failure(e)
        }
    }

    suspend fun resetToDefaultImage(car: Car): Result<Car> = withContext(Dispatchers.IO) {
        try {
            deleteOldImageFiles(car.id)
            val defaultImage = VehicleData.getDefaultImageUrl(car.make, car.model)
            val updatedCar = car.copy(
                localImagePath = null,
                imageUrl = defaultImage
            )
            carDao.updateCar(CarEntity.fromModel(updatedCar))
            Result.success(updatedCar)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset image for ${car.displayName}", e)
            Result.failure(e)
        }
    }

    private fun deleteOldImageFiles(carId: String) {
        try {
            val dir = getVehicleImagesDir()
            dir.listFiles()?.filter { it.name.startsWith(carId) }?.forEach {
                it.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not clean old image files for $carId", e)
        }
    }
}
