package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.model.Car
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick

@Composable
fun NoVehiclesInsightsEmptyState(modifier: Modifier = Modifier) {
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
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text("No vehicles yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Add a vehicle to see mileage and charging insights.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InsightsVehicleFilterRow(
    activeCars: List<Car>,
    selectedCarId: String?,
    onSelectVehicle: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val vehicleOptions = listOf("All" to null) + activeCars.map {
        (it.model.ifBlank { it.displayName }) to it.id
    }
    if (vehicleOptions.size <= 3) {
        VehicleFilterConnectedButtonGroup(
            options = vehicleOptions,
            selectedCarId = selectedCarId,
            onSelectVehicle = onSelectVehicle,
            modifier = modifier
        )
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vehicleOptions.forEach { (label, carId) ->
                val selected = selectedCarId == carId
                FilterChip(
                    selected = selected,
                    onClick = { onSelectVehicle(carId) },
                    label = { Text(text = label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
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

@Composable
private fun VehicleFilterConnectedButtonGroup(
    options: List<Pair<String, String?>>,
    selectedCarId: String?,
    onSelectVehicle: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, (label, carId) ->
            val selected = selectedCarId == carId
            val startTarget = if (index == 0 || selected) 24.dp else 6.dp
            val endTarget = if (index == options.lastIndex || selected) 24.dp else 6.dp

            val startRadius by animateDpAsState(startTarget, spring(dampingRatio = 0.8f, stiffness = 380f), label = "veh_start_$index")
            val endRadius by animateDpAsState(endTarget, spring(dampingRatio = 0.8f, stiffness = 380f), label = "veh_end_$index")
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "veh_bg_$index"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "veh_fg_$index"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectVehicle(carId) },
                shape = RoundedCornerShape(topStart = startRadius, bottomStart = startRadius, topEnd = endRadius, bottomEnd = endRadius),
                color = bg,
                contentColor = fg
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
fun CompactElectricityPeriodBar(
    granularity: InsightsTimeGranularity,
    periodText: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                InsightsTimeGranularity.entries.forEachIndexed { index, gran ->
                    val selected = gran == granularity
                    val startR by animateDpAsState(if (index == 0 || selected) 20.dp else 6.dp, spring(dampingRatio = 0.8f, stiffness = 380f), label = "c_s_$index")
                    val endR by animateDpAsState(if (index == InsightsTimeGranularity.entries.lastIndex || selected) 20.dp else 6.dp, spring(dampingRatio = 0.8f, stiffness = 380f), label = "c_e_$index")
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .expressiveBounceClick { onSelectGranularity(gran) },
                        shape = RoundedCornerShape(topStart = startR, bottomStart = startR, topEnd = endR, bottomEnd = endR),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = gran.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousPeriod, enabled = canGoPrevious) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous period")
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = periodText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                IconButton(onClick = onNextPeriod, enabled = canGoNext) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next period")
                }
            }
        }
    }
}

@Composable
fun WaitingForMileageCard(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
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
                text = "Waiting for more mileage data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Charts appear automatically after a few odometer updates.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
