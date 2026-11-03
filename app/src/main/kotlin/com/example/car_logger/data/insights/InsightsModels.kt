package com.example.car_logger.data.insights

enum class InsightsTimeGranularity(val label: String) {
    WEEKLY("Week"),
    MONTHLY("Month"),
    YTD("Year")
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
    val car2Gallons: Double
)

data class InsightsUiState(
    val selectedCarId: String? = null, // null for both cars
    val granularity: InsightsTimeGranularity = InsightsTimeGranularity.WEEKLY,
    val totalMiles: Int = 0,
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
    val periodOffset: Int = 0
)
