package com.example.car_logger.ui.screens.home.tabs.insights

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.car_logger.data.insights.DrivingActivityBar
import com.example.car_logger.data.insights.GasComparisonData
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.InsightsUiState
import com.example.car_logger.data.model.Car
import com.example.car_logger.ui.screens.home.tabs.InsightsTab
import com.example.car_logger.ui.theme.CarLoggerTheme

private val sampleCars = listOf(
    Car(
        id = "car_1",
        year = 2023,
        make = "Tesla",
        model = "Model 3",
        trim = "Long Range AWD",
        odometer = 24500,
        isArchived = false
    ),
    Car(
        id = "car_2",
        year = 2022,
        make = "Chevrolet",
        model = "Bolt EUV",
        trim = "Premier",
        odometer = 18200,
        isArchived = false
    )
)

private val sampleUiState = InsightsUiState(
    selectedCarId = null,
    granularity = InsightsTimeGranularity.WEEKLY,
    totalMiles = 559,
    car1Id = "car_1",
    car1Name = "Model 3",
    car1Miles = 307,
    car1SharePct = 55,
    car1DailyRate = 43.9f,
    car2Id = "car_2",
    car2Name = "Bolt EUV",
    car2Miles = 252,
    car2SharePct = 45,
    car2DailyRate = 36.0f,
    observationWindowText = "Oct 21 – Oct 27",
    activityBars = listOf(
        DrivingActivityBar("Mon, Oct 21", "Mon", 42, 25, 67, 0, 0),
        DrivingActivityBar("Tue, Oct 22", "Tue", 55, 30, 85, 0, 0),
        DrivingActivityBar("Wed, Oct 23", "Wed", 38, 12, 50, 0, 0),
        DrivingActivityBar("Thu, Oct 24", "Thu", 60, 40, 100, 0, 0),
        DrivingActivityBar("Fri, Oct 25", "Fri", 72, 35, 107, 0, 0),
        DrivingActivityBar("Sat, Oct 26", "Sat", 25, 65, 90, 0, 0),
        DrivingActivityBar("Sun, Oct 27", "Sun", 15, 45, 60, 0, 0)
    ),
    gasComparison = GasComparisonData(
        totalCost = 72.86,
        totalGallons = 19.96,
        co2Lbs = 392,
        car1Cost = 40.00,
        car1Gallons = 10.96,
        car2Cost = 32.86,
        car2Gallons = 9.00
    ),
    gasPrice = 3.65f,
    baselineMpg = 28.0f,
    hasOdometerData = true,
    canGoPrevious = true,
    canGoNext = false,
    periodOffset = 0
)

@Preview(name = "Insights Tab - Light", showBackground = true)
@Composable
fun InsightsTabLightPreview() {
    CarLoggerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            InsightsTab(
                state = sampleUiState,
                cars = sampleCars,
                onSelectVehicle = {},
                onSelectGranularity = {},
                onPreviousPeriod = {},
                onNextPeriod = {},
                onUpdateGasPrice = {},
                onUpdateBaselineMpg = {}
            )
        }
    }
}

@Preview(name = "Insights Tab - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun InsightsTabDarkPreview() {
    CarLoggerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            InsightsTab(
                state = sampleUiState,
                cars = sampleCars,
                onSelectVehicle = {},
                onSelectGranularity = {},
                onPreviousPeriod = {},
                onNextPeriod = {},
                onUpdateGasPrice = {},
                onUpdateBaselineMpg = {}
            )
        }
    }
}

@Preview(name = "Insights Tab - Single Vehicle", showBackground = true)
@Composable
fun InsightsTabSingleVehiclePreview() {
    CarLoggerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            InsightsTab(
                state = sampleUiState.copy(
                    selectedCarId = "car_1",
                    totalMiles = 307,
                    gasComparison = sampleUiState.gasComparison.copy(
                        totalCost = 40.00,
                        totalGallons = 10.96,
                        co2Lbs = 215
                    )
                ),
                cars = sampleCars,
                onSelectVehicle = {},
                onSelectGranularity = {},
                onPreviousPeriod = {},
                onNextPeriod = {},
                onUpdateGasPrice = {},
                onUpdateBaselineMpg = {}
            )
        }
    }
}

@Preview(name = "Driving Activity Card - Google Health Style", showBackground = true)
@Composable
fun DrivingActivityCardGoogleHealthPreview() {
    CarLoggerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            DrivingActivityCard(
                granularity = InsightsTimeGranularity.WEEKLY,
                activityBars = sampleUiState.activityBars,
                observationWindowText = "Oct 21 – Oct 27",
                totalMiles = sampleUiState.totalMiles,
                dailyRate = sampleUiState.car1DailyRate + sampleUiState.car2DailyRate,
                canGoPrevious = true,
                canGoNext = false,
                onSelectGranularity = {},
                onPreviousPeriod = {},
                onNextPeriod = {},
                onResetPeriod = {},
                car1Name = "Model 3",
                car2Name = "Bolt EUV",
                selectedCarId = null,
                car1Id = "car_1",
                car2Id = "car_2"
            )
        }
    }
}

@Preview(name = "Insights Tab - Empty State", showBackground = true)
@Composable
fun InsightsTabEmptyPreview() {
    CarLoggerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            InsightsTab(
                state = InsightsUiState(
                    hasOdometerData = false,
                    observationWindowText = "Initial reading logged on Oct 29 (more readings needed for activity)"
                ),
                cars = sampleCars,
                onSelectVehicle = {},
                onSelectGranularity = {},
                onPreviousPeriod = {},
                onNextPeriod = {},
                onUpdateGasPrice = {},
                onUpdateBaselineMpg = {}
            )
        }
    }
}
