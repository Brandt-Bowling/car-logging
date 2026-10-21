package com.example.car_logger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.car_logger.data.model.MaintenanceRecord

@Entity(
    tableName = "maintenance_records",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["carId"]), Index(value = ["driveFileId"])]
)
data class MaintenanceRecordEntity(
    @PrimaryKey val id: String,
    val carId: String,
    val title: String,
    val date: String,
    val odometer: Int,
    val description: String? = null,
    val receiptUrl: String? = null,
    val cost: Double? = null,
    val driveFileId: String? = null
) {
    fun toModel(): MaintenanceRecord = MaintenanceRecord(
        id = id,
        carId = carId,
        title = title,
        date = date,
        odometer = odometer,
        description = description?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        receiptUrl = receiptUrl?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        cost = cost,
        driveFileId = driveFileId?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
    )

    fun cleanNullStrings(): MaintenanceRecordEntity = copy(
        description = description?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        receiptUrl = receiptUrl?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() },
        driveFileId = driveFileId?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
    )

    companion object {
        fun fromModel(record: MaintenanceRecord): MaintenanceRecordEntity = MaintenanceRecordEntity(
            id = record.id,
            carId = record.carId,
            title = record.title,
            date = record.date,
            odometer = record.odometer,
            description = record.description,
            receiptUrl = record.receiptUrl,
            cost = record.cost,
            driveFileId = record.driveFileId
        )
    }
}
