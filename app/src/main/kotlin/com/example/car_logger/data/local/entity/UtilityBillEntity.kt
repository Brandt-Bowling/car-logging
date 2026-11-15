package com.example.car_logger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "utility_bills")
data class UtilityBillEntity(
    @PrimaryKey val id: String,
    val providerName: String,
    val startDateMs: Long,
    val endDateMs: Long,
    val totalKwh: Double,
    val totalAmount: Double,
    val fixedCharges: Double,
    val calculatedRate: Double,
    val overrideRate: Double? = null,
    val effectiveRate: Double,
    val uploadedAtMs: Long
)
