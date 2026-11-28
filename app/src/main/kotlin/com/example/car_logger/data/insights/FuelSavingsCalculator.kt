package com.example.car_logger.data.insights

import com.example.car_logger.data.model.Car

internal object FuelSavingsCalculator {

    fun computeNetFuelSavings(
        activeCars: List<Car>,
        car1: Car?,
        car2: Car?,
        car1Miles: Int,
        car2Miles: Int,
        selectedCarId: String?,
        effectiveMpg: Float,
        gasPrice: Float,
        electricityCost: ElectricityCostData
    ): Double? {
        val totalSessionsInWindow =
            electricityCost.homeSessionCount + electricityCost.workSessionCount + electricityCost.publicSessionCount
        if (totalSessionsInWindow <= 0 || electricityCost.combinedTotalKwh <= 0.0) return null

        val evMilesForGasBaseline = when (selectedCarId) {
            null -> {
                val c1IsEv = car1?.isEv == true ||
                    (electricityCost.homeCar1Kwh + electricityCost.workCar1Kwh + electricityCost.publicCar1Kwh) > 0.0
                val c2IsEv = car2?.isEv == true ||
                    (electricityCost.homeCar2Kwh + electricityCost.workCar2Kwh + electricityCost.publicCar2Kwh) > 0.0
                val evMiles = (if (c1IsEv) car1Miles else 0) + (if (c2IsEv) car2Miles else 0)
                if (activeCars.none { it.isEv } && evMiles == 0) car1Miles + car2Miles else evMiles
            }
            car1?.id -> car1Miles
            car2?.id -> car2Miles
            else -> 0
        }
        if (evMilesForGasBaseline <= 0) return null

        val gasBaselineCostForEvs = (evMilesForGasBaseline / effectiveMpg.toDouble()) * gasPrice.toDouble()
        return gasBaselineCostForEvs - electricityCost.combinedTotalCost
    }
}
