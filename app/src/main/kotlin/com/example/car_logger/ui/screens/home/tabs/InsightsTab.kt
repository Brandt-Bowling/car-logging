package com.example.car_logger.ui.screens.home.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.InsightsUiState
import com.example.car_logger.data.model.Car
import com.example.car_logger.ui.screens.home.tabs.insights.DrivingActivityCard
import com.example.car_logger.ui.screens.home.tabs.insights.EditGasComparisonSheet
import com.example.car_logger.ui.screens.home.tabs.insights.FleetShareCard
import com.example.car_logger.ui.screens.home.tabs.insights.GasComparisonCard
import com.example.car_logger.ui.theme.expressiveBounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsTab(
    state: InsightsUiState,
    cars: List<Car>,
    onSelectVehicle: (String?) -> Unit,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    onUpdateGasPrice: (Float) -> Unit,
    onUpdateBaselineMpg: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditSheet by remember { mutableStateOf(false) }
    val activeCars = cars.filter { !it.isArchived }.ifEmpty { cars }

    if (cars.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Insights,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "No vehicles in garage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add a vehicle to your garage to track driving analytics and insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vehicle Selection Controls
        if (activeCars.size > 1) {
            item {
                val vehicleOptions = listOf("Both Cars" to null) + activeCars.map {
                    (it.model.ifBlank { it.displayName }) to it.id
                }

                if (vehicleOptions.size <= 3) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        vehicleOptions.forEachIndexed { index, (label, carId) ->
                            val selected = state.selectedCarId == carId
                            SegmentedButton(
                                selected = selected,
                                onClick = { onSelectVehicle(carId) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = vehicleOptions.size
                                ),
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vehicleOptions.forEach { (label, carId) ->
                            val selected = state.selectedCarId == carId
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectVehicle(carId) },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.expressiveBounceClick()
                            )
                        }
                    }
                }
            }
        }

        // Granularity Selection Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InsightsTimeGranularity.values().forEach { gran ->
                    val isSelected = state.granularity == gran
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectGranularity(gran) },
                        label = {
                            Text(
                                text = gran.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.expressiveBounceClick()
                    )
                }
            }
        }

        // Observation Window Badge
        if (state.observationWindowText.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.observationWindowText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.hasOdometerData) {
            // Card 1: Fleet Share & Overall Miles
            item {
                FleetShareCard(
                    totalMiles = state.totalMiles,
                    car1Id = state.car1Id,
                    car1Name = state.car1Name,
                    car1Miles = state.car1Miles,
                    car1SharePct = state.car1SharePct,
                    car1DailyRate = state.car1DailyRate,
                    car2Id = state.car2Id,
                    car2Name = state.car2Name,
                    car2Miles = state.car2Miles,
                    car2SharePct = state.car2SharePct,
                    car2DailyRate = state.car2DailyRate,
                    selectedCarId = state.selectedCarId,
                    onSelectVehicle = onSelectVehicle
                )
            }

            // Card 2: Driving Activity Bar Chart
            item {
                DrivingActivityCard(
                    granularity = state.granularity,
                    activityBars = state.activityBars,
                    car1Name = state.car1Name,
                    car2Name = state.car2Name,
                    selectedCarId = state.selectedCarId,
                    car1Id = state.car1Id,
                    car2Id = state.car2Id
                )
            }

            // Card 3: Gas Comparison Card
            item {
                GasComparisonCard(
                    gasComparison = state.gasComparison,
                    gasPrice = state.gasPrice,
                    baselineMpg = state.baselineMpg,
                    car1Name = state.car1Name,
                    car1Miles = state.car1Miles,
                    car2Name = state.car2Name,
                    car2Miles = state.car2Miles,
                    selectedCarId = state.selectedCarId,
                    car1Id = state.car1Id,
                    car2Id = state.car2Id,
                    onOpenEditSheet = { showEditSheet = true }
                )
            }
        } else {
            // Empty / Initial state card
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Text(
                            text = "More Odometer Readings Needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Driving insights and gas comparison charts are calculated using verified odometer telemetry over time. As your vehicle syncs or you log maintenance records with mileage, weekly, monthly, and YTD charts will populate automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Bottom spacer for comfortable navigation clearance
        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    if (showEditSheet) {
        EditGasComparisonSheet(
            currentGasPrice = state.gasPrice,
            currentBaselineMpg = state.baselineMpg,
            onDismiss = { showEditSheet = false },
            onSave = { price, mpg ->
                onUpdateGasPrice(price)
                onUpdateBaselineMpg(mpg)
                showEditSheet = false
            }
        )
    }
}
