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
    val tireRotationInterval: Int? = null
) {
    fun toModel(): Car = Car(
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
        bodyStyle = bodyStyle,
        tireRotationInterval = tireRotationInterval
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
            tireRotationInterval = car.tireRotationInterval
        )
    }
}
