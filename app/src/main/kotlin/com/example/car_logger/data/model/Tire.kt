package com.example.car_logger.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Tire(
    val id: String,
    val carId: String,
    val manufacturer: String,
    val model: String,
    val dateInstalled: String,
    val odometerInstalled: Int,
    val estimatedTreadLifeMiles: Int,
    val lastRotationDate: String? = null,
    val lastRotationOdometer: Int? = null,
    val receiptUrl: String? = null
) {
    fun remainingMiles(currentOdometer: Int): Int {
        val maxMiles = odometerInstalled + estimatedTreadLifeMiles
        return (maxMiles - currentOdometer).coerceAtLeast(0)
    }

    fun treadWearPercentage(currentOdometer: Int): Float {
        if (estimatedTreadLifeMiles <= 0) return 0f
        val remaining = remainingMiles(currentOdometer).toFloat()
        return (remaining / estimatedTreadLifeMiles.toFloat()).coerceIn(0f, 1f)
    }
}
