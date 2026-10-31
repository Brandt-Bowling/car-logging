package com.example.car_logger.ui.screens.vehicledetails.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Tire
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale

@Composable
fun VehicleTireHealthCard(
    car: Car,
    activeTire: Tire?,
    onLogRotationClick: () -> Unit,
    onEditIntervalClick: () -> Unit,
    onInstallNewTireClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOdo = car.odometer ?: 0
    val tire = activeTire ?: remember(car.id) {
        Tire(
            id = "default_${car.id}",
            carId = car.id,
            manufacturer = "OEM / All Season",
            model = "Standard Tires",
            dateInstalled = "2023-01-01",
            odometerInstalled = (currentOdo - 15000).coerceAtLeast(0),
            estimatedTreadLifeMiles = 50000,
            lastRotationDate = "2023-06-01",
            lastRotationOdometer = (currentOdo - 5000).coerceAtLeast(0)
        )
    }

    var menuExpanded by remember { mutableStateOf(false) }

    // Next Rotation calculation
    val rotationInterval = car.effectiveTireRotationInterval
    val lastRotationOdo = tire.lastRotationOdometer ?: tire.odometerInstalled
    val nextRotationOdo = lastRotationOdo + rotationInterval
    val remainingMilesToRotation = nextRotationOdo - currentOdo
    val isOverdue = remainingMilesToRotation < 0
    val isUrgent = remainingMilesToRotation in 0..1000

    val milesSinceLastRotation = (currentOdo - lastRotationOdo).coerceAtLeast(0)

    // Tread wear calculation
    val remainingTreadMiles = tire.remainingMiles(currentOdo)
    val wearPercentage = tire.treadWearPercentage(currentOdo)

    val animatedWear by animateFloatAsState(
        targetValue = wearPercentage,
        animationSpec = ExpressiveMotionTokens.SoftSpring,
        label = "tread_wear_animation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Tire Title, Interval, Action Button & Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.TireRepair,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${tire.manufacturer} ${tire.model}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Rotation interval: every ${NumberFormat.getNumberInstance(Locale.US).format(rotationInterval)} mi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Tire options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Log Tire Rotation") },
                            onClick = {
                                menuExpanded = false
                                onLogRotationClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Configure Rotation Interval") },
                            onClick = {
                                menuExpanded = false
                                onEditIntervalClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Install New Tire Set") },
                            onClick = {
                                menuExpanded = false
                                onInstallNewTireClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.TireRepair, contentDescription = null) }
                        )
                    }
                }
            }

            // Combined Next Rotation Due Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    isUrgent -> AmberUrgent.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        isUrgent -> AmberUrgent.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when {
                                isOverdue -> Icons.Filled.Warning
                                isUrgent -> Icons.Filled.Warning
                                else -> Icons.Filled.Sync
                            },
                            contentDescription = null,
                            tint = when {
                                isOverdue -> MaterialTheme.colorScheme.error
                                isUrgent -> AmberUrgent
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val statusText = when {
                                isOverdue -> "Tire Rotation Overdue by ${NumberFormat.getNumberInstance(Locale.US).format(-remainingMilesToRotation)} mi"
                                else -> "Next Rotation in ${NumberFormat.getNumberInstance(Locale.US).format(remainingMilesToRotation)} mi"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isOverdue -> MaterialTheme.colorScheme.error
                                    isUrgent -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = "Target: ${NumberFormat.getNumberInstance(Locale.US).format(nextRotationOdo)} mi • Last: ${NumberFormat.getNumberInstance(Locale.US).format(milesSinceLastRotation)} mi ago",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isOverdue -> MaterialTheme.colorScheme.error
                                isUrgent -> AmberUrgent
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Text(
                                text = when {
                                    isOverdue -> "Overdue"
                                    isUrgent -> "Due Soon"
                                    else -> "Scheduled"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isOverdue -> MaterialTheme.colorScheme.onError
                                    isUrgent -> Color.Black
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Button(
                            onClick = onLogRotationClick,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.expressiveBounceClick(pressedScale = 0.94f)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Log", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tread Wear Progress & Metrics
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val percentInt = (wearPercentage * 100).toInt()
                    Text(
                        text = "Tread Life: $percentInt% Remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "~${NumberFormat.getNumberInstance(Locale.US).format(remainingTreadMiles)} mi left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedWear },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = when {
                        wearPercentage < 0.20f -> MaterialTheme.colorScheme.error
                        wearPercentage < 0.40f -> AmberUrgent
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
