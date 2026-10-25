package com.example.car_logger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.car_logger.data.model.Car

@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey val id: String,
    val make: String,
    val model: String,
    val year: Int,
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val licensePlate: String? = null,
    val vin: String? = null,
    val odometer: Int? = null,
    val isEv: Boolean = false,
    val isArchived: Boolean = false,
    val trim: String? = null,
    val bodyStyle: String? = null,
    val tireRotationInterval: Int? = null,
    val driveFolderId: String? = null,
    val driveFolderName: String? = null
) {
    fun toModel(): Car = Car(
        id = id,
        make = make,
        model = model,
        year = year,
        imageUrl = imageUrl?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        localImagePath = localImagePath?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        licensePlate = licensePlate?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        vin = vin?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        odometer = odometer,
        isEv = isEv,
        isArchived = isArchived,
        trim = trim?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        bodyStyle = bodyStyle?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        tireRotationInterval = tireRotationInterval,
        driveFolderId = driveFolderId?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        driveFolderName = driveFolderName?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
    )

    fun cleanNullStrings(): CarEntity = copy(
        imageUrl = imageUrl?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        localImagePath = localImagePath?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        licensePlate = licensePlate?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        vin = vin?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        trim = trim?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        bodyStyle = bodyStyle?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        driveFolderId = driveFolderId?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        driveFolderName = driveFolderName?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
    )

    companion object {
        fun fromModel(car: Car): CarEntity = CarEntity(
            id = car.id,
            make = car.make,
            model = car.model,
            year = car.year,
            imageUrl = car.imageUrl,
            localImagePath = car.localImagePath,
            licensePlate = car.licensePlate,
            vin = car.vin,
            odometer = car.odometer,
            isEv = car.isEv,
            isArchived = car.isArchived,
            trim = car.trim,
            bodyStyle = car.bodyStyle,
            tireRotationInterval = car.tireRotationInterval,
            driveFolderId = car.driveFolderId,
            driveFolderName = car.driveFolderName
        )
    }
}
