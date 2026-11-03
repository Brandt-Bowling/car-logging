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

data class TireRotationStatus(
    val rotationInterval: Int,
    val currentOdometer: Int,
    val lastRotationOdometer: Int?,
    val baselineOdometer: Int,
    val nextRotationOdometer: Int,
    val remainingMilesToRotation: Int,
    val milesSinceBaseline: Int,
    val rotationProgress: Float,
    val isOverdue: Boolean,
    val isUrgent: Boolean
)

object TireRotationCalculator {
    fun isTireRotationRecord(record: MaintenanceRecord): Boolean {
        val combined = "${record.title} ${record.description.orEmpty()}".lowercase()
        return combined.contains("tire rotation") ||
            combined.contains("rotate tire") ||
            combined.contains("rotated tire") ||
            combined.contains("tires rotated") ||
            (combined.contains("tire") && combined.contains("rotat"))
    }

    fun calculateRotationStatus(
        car: Car,
        activeTire: Tire?,
        records: List<MaintenanceRecord>
    ): TireRotationStatus {
        val rotationInterval = car.effectiveTireRotationInterval
        val minValidOdo = activeTire?.odometerInstalled ?: 0

        val latestRecordRotationOdo = records
            .asSequence()
            .filter { it.carId == car.id && isTireRotationRecord(it) && it.odometer >= minValidOdo }
            .maxOfOrNull { it.odometer }

        val tireRotationOdo = activeTire?.lastRotationOdometer?.takeIf { it >= minValidOdo }
        val lastRotationOdometer = listOfNotNull(tireRotationOdo, latestRecordRotationOdo).maxOrNull()
        val baselineOdometer = lastRotationOdometer ?: activeTire?.odometerInstalled ?: 0
        val currentOdometer = maxOf(car.odometer ?: 0, baselineOdometer)

        val nextRotationOdometer = baselineOdometer + rotationInterval
        val remainingMilesToRotation = nextRotationOdometer - currentOdometer
        val milesSinceBaseline = (currentOdometer - baselineOdometer).coerceAtLeast(0)
        val rotationProgress = if (rotationInterval > 0) {
            (milesSinceBaseline.toFloat() / rotationInterval.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        return TireRotationStatus(
            rotationInterval = rotationInterval,
            currentOdometer = currentOdometer,
            lastRotationOdometer = lastRotationOdometer,
            baselineOdometer = baselineOdometer,
            nextRotationOdometer = nextRotationOdometer,
            remainingMilesToRotation = remainingMilesToRotation,
            milesSinceBaseline = milesSinceBaseline,
            rotationProgress = rotationProgress,
            isOverdue = remainingMilesToRotation < 0,
            isUrgent = remainingMilesToRotation in 0..1000
        )
    }
}
