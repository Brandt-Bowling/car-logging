package com.example.car_logger.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Car(
    val id: String,
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
    val displayName: String
        get() = "$year $make $model"

    val effectiveTireRotationInterval: Int
        get() = tireRotationInterval ?: 7500
}
