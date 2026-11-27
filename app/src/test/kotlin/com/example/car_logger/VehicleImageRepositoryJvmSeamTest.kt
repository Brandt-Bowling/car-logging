package com.example.car_logger

import android.net.Uri
import android.net.testUri
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.vehicle.VehicleData
import com.example.car_logger.data.vehicle.VehicleImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class VehicleImageRepositoryJvmSeamTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun saveCustomImageCreatesMissingDirWritesBytesClearsImageUrlAndUpdatesCarDao() = runTest {
        val imagesDir = File(tempFolder.root, "nested/vehicle_images")
        val carDao = RecordingCarDao()
        val repository = VehicleImageRepository(
            imagesDir = imagesDir,
            uriContentReader = FakeUriContentReader(),
            carDao = carDao
        )
        val car = sampleCar(id = "car-alpha", imageUrl = "https://example.com/default.jpg")
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)

        val result = repository.saveCustomImage(car, bytes)

        assertTrue(result.isSuccess)
        val updatedCar = result.getOrThrow()
        assertNull(updatedCar.imageUrl)
        assertNotNull(updatedCar.localImagePath)
        val savedFile = File(updatedCar.localImagePath!!)
        assertTrue(savedFile.exists())
        assertEquals(imagesDir.canonicalPath, savedFile.parentFile?.canonicalPath)
        assertArrayEquals(bytes, savedFile.readBytes())
        assertEquals(1, carDao.updatedCars.size)
        assertEquals(savedFile.absolutePath, carDao.updatedCars.single().localImagePath)
        assertNull(carDao.updatedCars.single().imageUrl)
    }

    @Test
    fun saveCustomImageRemovesOldFilesForSameCarWhilePreservingSiblingCarFiles() = runTest {
        val imagesDir = tempFolder.newFolder("vehicle_images")
        val oldAlpha = File(imagesDir, "car-alpha_1000.jpg").apply { writeBytes(byteArrayOf(9, 9)) }
        val siblingBeta = File(imagesDir, "car-beta_1000.jpg").apply { writeBytes(byteArrayOf(7, 7)) }
        val carDao = RecordingCarDao()
        val repository = VehicleImageRepository(
            imagesDir = imagesDir,
            uriContentReader = FakeUriContentReader(),
            carDao = carDao
        )

        val result = repository.saveCustomImage(sampleCar(id = "car-alpha"), byteArrayOf(1, 2, 3))

        assertTrue(result.isSuccess)
        assertFalse(oldAlpha.exists())
        assertTrue(siblingBeta.exists())
        assertArrayEquals(byteArrayOf(7, 7), siblingBeta.readBytes())
        val savedFile = File(result.getOrThrow().localImagePath!!)
        assertTrue(savedFile.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), savedFile.readBytes())
    }

    @Test
    fun saveImageFromUriReadsViaUriContentReaderReplacesOldImageAndUpdatesCarDao() = runTest {
        val imagesDir = tempFolder.newFolder("vehicle_images_uri")
        val oldFile = File(imagesDir, "car-alpha_1000.jpg").apply { writeBytes(byteArrayOf(4, 4)) }
        val uri = testUri("content://com.example.car_logger/images/photo.jpg")
        val payload = byteArrayOf(10, 20, 30, 40)
        val carDao = RecordingCarDao()
        val reader = FakeUriContentReader(bytesByUri = mapOf(uri.toString() to payload))
        val repository = VehicleImageRepository(
            imagesDir = imagesDir,
            uriContentReader = reader,
            carDao = carDao
        )

        val result = repository.saveImageFromUri(sampleCar(id = "car-alpha", imageUrl = "https://old"), uri)

        assertTrue(result.isSuccess)
        assertFalse(oldFile.exists())
        val updatedCar = result.getOrThrow()
        assertNull(updatedCar.imageUrl)
        val savedFile = File(updatedCar.localImagePath!!)
        assertTrue(savedFile.exists())
        assertArrayEquals(payload, savedFile.readBytes())
        assertEquals(1, carDao.updatedCars.size)
    }

    @Test
    fun saveImageFromUriPreservesExistingImageAndSkipsDaoUpdateWhenReaderReturnsNull() = runTest {
        val imagesDir = tempFolder.newFolder("vehicle_images_null")
        val existingFile = File(imagesDir, "car-alpha_1000.jpg").apply { writeBytes(byteArrayOf(5, 6, 7)) }
        val uri = testUri("content://com.example.car_logger/images/missing.jpg")
        val carDao = RecordingCarDao()
        val repository = VehicleImageRepository(
            imagesDir = imagesDir,
            uriContentReader = FakeUriContentReader(bytesByUri = emptyMap()),
            carDao = carDao
        )

        val result = repository.saveImageFromUri(
            sampleCar(id = "car-alpha", localImagePath = existingFile.absolutePath),
            uri
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("Could not open input stream from URI", error?.message)
        assertTrue(existingFile.exists())
        assertArrayEquals(byteArrayOf(5, 6, 7), existingFile.readBytes())
        assertTrue(carDao.updatedCars.isEmpty())
    }

    @Test
    fun saveImageFromUriPreservesExistingImageAndSkipsDaoUpdateWhenReaderThrows() = runTest {
        val imagesDir = tempFolder.newFolder("vehicle_images_err")
        val existingFile = File(imagesDir, "car-alpha_1000.jpg").apply { writeBytes(byteArrayOf(8, 8)) }
        val uri = testUri("content://com.example.car_logger/images/broken.jpg")
        val carDao = RecordingCarDao()
        val repository = VehicleImageRepository(
            imagesDir = imagesDir,
            uriContentReader = FakeUriContentReader(throwOnRead = IOException("Disk unavailable")),
            carDao = carDao
        )

        val result = repository.saveImageFromUri(
            sampleCar(id = "car-alpha", localImagePath = existingFile.absolutePath),
            uri
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(existingFile.exists())
        assertArrayEquals(byteArrayOf(8, 8), existingFile.readBytes())
        assertTrue(carDao.updatedCars.isEmpty())
    }

    @Test
    fun resetToDefaultImageDeletesCarFilesAndRestoresKnownVehicleDefaultUrl() = runTest {
        val imagesDir = tempFolder.newFolder("vehicle_images_reset")
        val alphaFile = File(imagesDir, "car-alpha_1000.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        val betaFile = File(imagesDir, "car-beta_1000.jpg").apply { writeBytes(byteArrayOf(3, 4)) }
        val carDao = RecordingCarDao()
        val repository = VehicleImageRepository(
            imagesDir = imagesDir,
            uriContentReader = FakeUriContentReader(),
            carDao = carDao
        )
        val car = sampleCar(
            id = "car-alpha",
            make = "Chevrolet",
            model = "Blazer EV",
            localImagePath = alphaFile.absolutePath
        )

        val result = repository.resetToDefaultImage(car)

        assertTrue(result.isSuccess)
        val updatedCar = result.getOrThrow()
        assertFalse(alphaFile.exists())
        assertTrue(betaFile.exists())
        assertNull(updatedCar.localImagePath)
        assertEquals(VehicleData.getDefaultImageUrl("Chevrolet", "Blazer EV"), updatedCar.imageUrl)
        assertEquals(1, carDao.updatedCars.size)
        assertNull(carDao.updatedCars.single().localImagePath)
    }

    @Test
    fun resetToDefaultImageSetsNullImageUrlForUnknownVehicleModel() = runTest {
        val imagesDir = tempFolder.newFolder("vehicle_images_reset_unknown")
        val alphaFile = File(imagesDir, "car-alpha_1000.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        val carDao = RecordingCarDao()
        val repository = VehicleImageRepository(
            imagesDir = imagesDir,
            uriContentReader = FakeUriContentReader(),
            carDao = carDao
        )
        val car = sampleCar(
            id = "car-alpha",
            make = "CustomMake",
            model = "PrototypeX",
            localImagePath = alphaFile.absolutePath
        )

        val result = repository.resetToDefaultImage(car)

        assertTrue(result.isSuccess)
        val updatedCar = result.getOrThrow()
        assertFalse(alphaFile.exists())
        assertNull(updatedCar.localImagePath)
        assertNull(updatedCar.imageUrl)
        assertEquals(1, carDao.updatedCars.size)
    }

    private fun sampleCar(
        id: String,
        make: String = "Tesla",
        model: String = "Model 3",
        imageUrl: String? = null,
        localImagePath: String? = null
    ): Car = Car(
        id = id,
        make = make,
        model = model,
        year = 2024,
        imageUrl = imageUrl,
        localImagePath = localImagePath,
        isEv = true
    )

    private class FakeUriContentReader(
        private val bytesByUri: Map<String, ByteArray> = emptyMap(),
        private val throwOnRead: Throwable? = null
    ) : UriContentReader {
        override fun resolveFileName(uri: Uri): String? = uri.lastPathSegment
        override fun resolveMimeType(uri: Uri, fileName: String): String = "image/jpeg"
        override fun readBytes(uri: Uri): ByteArray? {
            throwOnRead?.let { throw it }
            return bytesByUri[uri.toString()]
        }
    }

    private class RecordingCarDao : CarDao {
        val updatedCars = mutableListOf<CarEntity>()

        override fun getAllCars(): Flow<List<CarEntity>> = flowOf(emptyList())
        override suspend fun getAllCarsList(): List<CarEntity> = emptyList()
        override suspend fun getCarById(carId: String): CarEntity? = null
        override fun observeCarById(carId: String): Flow<CarEntity?> = flowOf(null)
        override suspend fun insertCar(car: CarEntity) = Unit
        override suspend fun insertCars(cars: List<CarEntity>) = Unit
        override suspend fun updateCar(car: CarEntity) {
            updatedCars += car
        }
        override suspend fun toggleArchive(carId: String) = Unit
        override suspend fun updateOdometer(carId: String, odometer: Int) = Unit
        override suspend fun updateOdometerFromTelemetry(
            carId: String,
            odometer: Int,
            provider: String,
            status: String,
            syncTime: Long
        ) = Unit
        override suspend fun updateTelemetryStatus(carId: String, provider: String, status: String) = Unit
        override suspend fun updateDriveFolder(carId: String, folderId: String?, folderName: String?) = Unit
        override suspend fun deleteCar(car: CarEntity) = Unit
        override suspend fun deleteCarById(carId: String) = Unit
        override suspend fun getCarCount(): Int = 0
    }
}
