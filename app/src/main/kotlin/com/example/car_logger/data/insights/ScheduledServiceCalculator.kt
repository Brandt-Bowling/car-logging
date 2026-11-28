package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.TireEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.TireRotationCalculator

data class ScheduledServiceItem(
    val car: Car,
    val title: String,
    val dueOdometer: Int,
    val remainingMiles: Int,
    val isUrgent: Boolean // <= 500 miles
)

object ScheduledServiceCalculator {

    private const val URGENT_THRESHOLD_MILES = 500
    private const val OIL_CHANGE_INTERVAL_MILES = 5000

    fun calculateScheduledServices(
        cars: List<Car>,
        records: List<MaintenanceRecord>,
        tireEntities: List<TireEntity>
    ): List<ScheduledServiceItem> {
        val activeTiresByCar = tireEntities
            .groupBy { it.carId }
            .mapValues { (_, list) -> list.firstOrNull()?.toModel() }

        val items = mutableListOf<ScheduledServiceItem>()
        for (car in cars) {
            if (car.isArchived || car.odometer == null) continue
            val carRecords = records.filter { it.carId == car.id }

            if (car.isEv) {
                val status = TireRotationCalculator.calculateRotationStatus(
                    car = car,
                    activeTire = activeTiresByCar[car.id],
                    records = carRecords
                )
                items.add(
                    ScheduledServiceItem(
                        car = car,
                        title = "Tire Rotation & Cabin Filter Inspection",
                        dueOdometer = status.nextRotationOdometer,
                        remainingMiles = status.remainingMilesToRotation,
                        isUrgent = status.remainingMilesToRotation <= URGENT_THRESHOLD_MILES
                    )
                )
            } else {
                val latestOdo = if (carRecords.isNotEmpty()) {
                    carRecords.maxOf { it.odometer }
                } else {
                    car.odometer
                }
                val nextOdo = latestOdo + OIL_CHANGE_INTERVAL_MILES
                val remaining = nextOdo - car.odometer
                items.add(
                    ScheduledServiceItem(
                        car = car,
                        title = "Oil & Filter Change",
                        dueOdometer = nextOdo,
                        remainingMiles = remaining,
                        isUrgent = remaining <= URGENT_THRESHOLD_MILES
                    )
                )
            }
        }
        return items.sortedBy { it.remainingMiles }
    }
}
