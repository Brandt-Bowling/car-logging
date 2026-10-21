package com.example.car_logger.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceRecord(
    val id: String,
    val carId: String,
    val title: String,
    val date: String, // ISO-8601 string or YYYY-MM-DD
    val odometer: Int,
    val description: String? = null,
    val receiptUrl: String? = null,
    val cost: Double? = null,
    val driveFileId: String? = null
)
