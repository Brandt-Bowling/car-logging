package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity

enum class InsightsTimeGranularity(val label: String) {
    WEEKLY("Week"),
    MONTHLY("Month"),
    YTD("Year")
}

enum class ChargingVenue(val label: String) {
    HOME("Home Charging"),
    PUBLIC("Public Charging")
}

data class DrivingActivityBar(
    val label: String,
    val shortLabel: String = label,
    val car1Miles: Int,
    val car2Miles: Int,
    val totalMiles: Int,
    val startDateMs: Long,
    val endDateMs: Long
)

data class GasComparisonData(
    val totalCost: Double,
    val totalGallons: Double,
    val co2Lbs: Int,
    val car1Cost: Double,
    val car1Gallons: Double,
    val car2Cost: Double,
    val car2Gallons: Double,
    val electricityTotalCost: Double = 0.0,
    val netFuelSavings: Double? = null
)

data class ElectricityCostData(
    val hasHomeData: Boolean = false,
    val hasPublicData: Boolean = false,
    val hasAnyData: Boolean = false,
    // Home charging metrics for the current period & vehicle filter
    val homeTotalCost: Double = 0.0,
    val homeTotalKwh: Double = 0.0,
    val homeMilesPerKwh: Double? = null,
    val homeCostPerMile: Double? = null,
    val homeEffectiveRate: Double = 0.16,
    val homeRateSourceLabel: String = "Default rate",
    val homeIsEstimatedRate: Boolean = true,
    val homeHasMatchedBill: Boolean = false,
    val homeCar1Cost: Double = 0.0,
    val homeCar1Kwh: Double = 0.0,
    val homeCar1SharePct: Int = 0,
    val homeCar2Cost: Double = 0.0,
    val homeCar2Kwh: Double = 0.0,
    val homeCar2SharePct: Int = 0,
    val homeSessionCount: Int = 0,
    // Public charging metrics for the current period & vehicle filter
    val publicTotalCost: Double = 0.0,
    val publicTotalKwh: Double = 0.0,
    val publicMilesPerKwh: Double? = null,
    val publicCostPerMile: Double? = null,
    val publicEffectiveRate: Double = 0.0,
    val publicRateSourceLabel: String = "Receipt totals",
    val publicCar1Cost: Double = 0.0,
    val publicCar1Kwh: Double = 0.0,
    val publicCar1SharePct: Int = 0,
    val publicCar2Cost: Double = 0.0,
    val publicCar2Kwh: Double = 0.0,
    val publicCar2SharePct: Int = 0,
    val publicSessionCount: Int = 0,
    // Combined (Home + Public) metrics for the current period & vehicle filter
    val combinedTotalCost: Double = 0.0,
    val combinedTotalKwh: Double = 0.0,
    val combinedMilesPerKwh: Double? = null,
    val combinedCostPerMile: Double? = null,
    // Overall bill status
    val latestBillEffectiveRate: Double? = null,
    val latestBillLabel: String? = null
)

data class InsightsUiState(
    val selectedCarId: String? = null, // null for both cars
    val granularity: InsightsTimeGranularity = InsightsTimeGranularity.WEEKLY,
    val totalMiles: Int = 0,
    val fleetTotalMiles: Int = 0,
    val car1Id: String? = null,
    val car1Name: String = "",
    val car1Miles: Int = 0,
    val car1SharePct: Int = 0,
    val car1DailyRate: Float = 0f,
    val car2Id: String? = null,
    val car2Name: String = "",
    val car2Miles: Int = 0,
    val car2SharePct: Int = 0,
    val car2DailyRate: Float = 0f,
    val observationWindowText: String = "",
    val activityBars: List<DrivingActivityBar> = emptyList(),
    val gasComparison: GasComparisonData = GasComparisonData(0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0),
    val gasPrice: Float = 3.65f,
    val baselineMpg: Float = 28.0f,
    val hasOdometerData: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val periodOffset: Int = 0,
    val electricityCost: ElectricityCostData = ElectricityCostData(),
    val defaultElectricityRate: Float = 0.16f,
    val utilityBills: List<UtilityBillEntity> = emptyList(),
    val chargingSessions: List<ChargingSessionEntity> = emptyList()
)
