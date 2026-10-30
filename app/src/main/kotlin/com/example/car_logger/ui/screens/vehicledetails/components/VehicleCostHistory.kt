package com.example.car_logger.ui.screens.vehicledetails.components

data class YearlyCostSummary(
    val year: Int,
    val totalCost: Double,
    val recordCount: Int,
    val milesDriven: Int?,
    val costPerMile: Double?
)

data class VehicleCostHistory(
    val lifetimeTotalCost: Double,
    val lifetimeCostPerMile: Double?,
    val yearlyBreakdowns: List<YearlyCostSummary>,
    val averageAnnualSpend: Double
)
