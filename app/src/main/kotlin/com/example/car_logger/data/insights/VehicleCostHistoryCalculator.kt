package com.example.car_logger.data.insights

import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleCostHistory
import com.example.car_logger.ui.screens.vehicledetails.components.YearlyCostSummary

object VehicleCostHistoryCalculator {

    fun calculate(
        records: List<MaintenanceRecord>,
        car: Car?
    ): VehicleCostHistory {
        val validRecords = records.filter { it.cost != null && it.cost > 0.0 }
        val lifetimeTotal = validRecords.sumOf { it.cost ?: 0.0 }
        val recordsByYear = validRecords.groupBy { rec ->
            rec.date.take(4).toIntOrNull() ?: 0
        }.filterKeys { it > 0 }

        val yearlySummaries = recordsByYear.map { (year, recs) ->
            val yearTotal = recs.sumOf { it.cost ?: 0.0 }
            val minOdo = recs.minOfOrNull { it.odometer }
            val maxOdo = recs.maxOfOrNull { it.odometer }
            val miles = if (maxOdo != null && minOdo != null && maxOdo > minOdo) maxOdo - minOdo else null
            val cpm = if (miles != null && miles > 0 && yearTotal > 0) yearTotal / miles.toDouble() else null
            YearlyCostSummary(
                year = year,
                totalCost = yearTotal,
                recordCount = recs.size,
                milesDriven = miles,
                costPerMile = cpm
            )
        }.sortedByDescending { it.year }

        val currentOdo = car?.odometer ?: validRecords.maxOfOrNull { it.odometer } ?: 0
        val minOverallOdo = validRecords.minOfOrNull { it.odometer }
        val lifetimeMiles = if (minOverallOdo != null && currentOdo > minOverallOdo) currentOdo - minOverallOdo else currentOdo
        val lifetimeCpm = if (lifetimeMiles > 0 && lifetimeTotal > 0) lifetimeTotal / lifetimeMiles.toDouble() else null
        val avgAnnual = if (yearlySummaries.isNotEmpty()) lifetimeTotal / yearlySummaries.size else 0.0

        return VehicleCostHistory(
            lifetimeTotalCost = lifetimeTotal,
            lifetimeCostPerMile = lifetimeCpm,
            yearlyBreakdowns = yearlySummaries,
            averageAnnualSpend = avgAnnual
        )
    }
}
