package com.example.car_logger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "odometer_logs",
    indices = [Index(value = ["carId", "timestamp"])]
)
data class OdometerLogEntity(
    @PrimaryKey val id: String,
    val carId: String,
    val odometer: Int,
    val timestamp: Long,
    val source: String
)
