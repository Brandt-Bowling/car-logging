package com.example.car_logger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.car_logger.data.model.Glovebox

@Entity(
    tableName = "glovebox",
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
data class GloveboxEntity(
    @PrimaryKey val carId: String,
    val registrationUrl: String? = null,
    val insuranceUrl: String? = null,
    val manualUrl: String? = null
) {
    fun toModel(): Glovebox = Glovebox(
        carId = carId,
        registrationUrl = registrationUrl,
        insuranceUrl = insuranceUrl,
        manualUrl = manualUrl
    )

    companion object {
        fun fromModel(glovebox: Glovebox): GloveboxEntity = GloveboxEntity(
            carId = glovebox.carId,
            registrationUrl = glovebox.registrationUrl,
            insuranceUrl = glovebox.insuranceUrl,
            manualUrl = glovebox.manualUrl
        )
    }
}
