package com.example.car_logger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "charging_sessions",
    indices = [
        Index(value = ["startDateMs", "endDateMs"]),
        Index(value = ["fingerprint"], unique = true)
    ]
)
data class ChargingSessionEntity(
    @PrimaryKey val id: String,
    val carId: String? = null,
    val startDateMs: Long,
    val endDateMs: Long,
    val kwh: Double,
    val venue: String,
    val directCost: Double? = null,
    val chargerName: String? = null,
    val isSummary: Boolean = false,
    val fingerprint: String,
    val uploadBatchId: String? = null,
    val uploadedAtMs: Long = 0L
)
