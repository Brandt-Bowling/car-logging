package com.example.car_logger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.car_logger.data.model.Tire

@Entity(
    tableName = "tires",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["carId"])]
)
data class TireEntity(
    @PrimaryKey val id: String,
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
    fun toModel(): Tire = Tire(
        id = id,
        carId = carId,
        manufacturer = manufacturer,
        model = model,
        dateInstalled = dateInstalled,
        odometerInstalled = odometerInstalled,
        estimatedTreadLifeMiles = estimatedTreadLifeMiles,
        lastRotationDate = lastRotationDate,
        lastRotationOdometer = lastRotationOdometer,
        receiptUrl = receiptUrl
    )

    companion object {
        fun fromModel(tire: Tire): TireEntity = TireEntity(
            id = tire.id,
            carId = tire.carId,
            manufacturer = tire.manufacturer,
            model = tire.model,
            dateInstalled = tire.dateInstalled,
            odometerInstalled = tire.odometerInstalled,
            estimatedTreadLifeMiles = tire.estimatedTreadLifeMiles,
            lastRotationDate = tire.lastRotationDate,
            lastRotationOdometer = tire.lastRotationOdometer,
            receiptUrl = tire.receiptUrl
        )
    }
}
