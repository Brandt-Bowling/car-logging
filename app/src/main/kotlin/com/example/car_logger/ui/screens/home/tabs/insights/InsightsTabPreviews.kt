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
    totalMiles = 2038,
    car1Id = "car_1",
    car1Name = "Model 3",
    car1Miles = 1290,
    car1SharePct = 63,
    car1DailyRate = 46.1f,
    car2Id = "car_2",
    car2Name = "Bolt EUV",
    car2Miles = 748,
    car2SharePct = 37,
    car2DailyRate = 26.7f,
    observationWindowText = "Tracking: Oct 1 – Oct 28 (4 completed weeks)",
    activityBars = listOf(
        DrivingActivityBar("Oct 1–7", 340, 180, 520, 0, 0),
        DrivingActivityBar("Oct 8–14", 290, 210, 500, 0, 0),
        DrivingActivityBar("Oct 15–21", 380, 160, 540, 0, 0),
        DrivingActivityBar("Oct 22–28", 280, 198, 478, 0, 0)
    ),
    gasComparison = GasComparisonData(
        totalCost = 265.66,
        totalGallons = 72.78,
        co2Lbs = 1430,
        car1Cost = 168.10,
        car1Gallons = 46.07,
        car2Cost = 97.56,
        car2Gallons = 26.71
    ),
    gasPrice = 3.65f,
    baselineMpg = 28.0f,
    hasOdometerData = true
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
                    totalMiles = 1290,
                    gasComparison = sampleUiState.gasComparison.copy(
                        totalCost = 168.10,
                        totalGallons = 46.07,
                        co2Lbs = 905
                    )
                ),
                cars = sampleCars,
                onSelectVehicle = {},
                onSelectGranularity = {},
                onUpdateGasPrice = {},
                onUpdateBaselineMpg = {}
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
                onUpdateGasPrice = {},
                onUpdateBaselineMpg = {}
            )
        }
    }
}
