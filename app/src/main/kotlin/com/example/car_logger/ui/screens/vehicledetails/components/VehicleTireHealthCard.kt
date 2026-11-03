package com.example.car_logger.ui.screens.vehicledetails.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.Tire
import com.example.car_logger.data.model.TireRotationCalculator
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.sin

@Composable
fun VehicleTireHealthCard(
    car: Car,
    activeTire: Tire?,
    records: List<MaintenanceRecord> = emptyList(),
    onLogRotationClick: () -> Unit,
    onEditIntervalClick: () -> Unit,
    onInstallNewTireClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    val tire = activeTire ?: remember(car.id) {
        Tire(
            id = "default_${car.id}",
            carId = car.id,
            manufacturer = "OEM / All Season",
            model = "Standard Tires",
            dateInstalled = "",
            odometerInstalled = 0,
            estimatedTreadLifeMiles = 50000,
            lastRotationDate = null,
            lastRotationOdometer = null
        )
    }

    val status = remember(car, activeTire, records) {
        TireRotationCalculator.calculateRotationStatus(
            car = car,
            activeTire = activeTire,
            records = records
        )
    }

    val currentOdo = status.currentOdometer
    val remainingTreadMiles = tire.remainingMiles(currentOdo)
    val wearPercentage = tire.treadWearPercentage(currentOdo)

    val animatedRotationProgress by animateFloatAsState(
        targetValue = if (status.isOverdue) 1f else status.rotationProgress,
        animationSpec = ExpressiveMotionTokens.SoftSpring,
        label = "rotation_wavy_progress"
    )

    val statusColor = when {
        status.isOverdue -> MaterialTheme.colorScheme.error
        status.isUrgent -> AmberUrgent
        else -> MaterialTheme.colorScheme.primary
    }

    val headerSubtitle = when {
        status.lastRotationOdometer != null ->
            "Last rotated at ${numberFormat.format(status.lastRotationOdometer)} mi"
        tire.odometerInstalled > 0 ->
            "Installed at ${numberFormat.format(tire.odometerInstalled)} mi"
        else ->
            "No rotation logged yet"
    }

    val statusBadgeText = when {
        status.isOverdue ->
            "${numberFormat.format(-status.remainingMilesToRotation)} mi overdue"
        status.isUrgent ->
            "Due in ${numberFormat.format(status.remainingMilesToRotation)} mi"
        else ->
            "In ${numberFormat.format(status.remainingMilesToRotation)} mi"
    }

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
            // 1. Clean Icon-Free Header Row (matches VehicleCostHistoryCard)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${tire.manufacturer} ${tire.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = headerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = when {
                        status.isOverdue -> MaterialTheme.colorScheme.errorContainer
                        status.isUrgent -> AmberUrgent.copy(alpha = 0.22f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = statusBadgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            status.isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                            status.isUrgent -> AmberUrgent
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 2. Middle Full-Width Wavy Rotation & Tread Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rotate at ${numberFormat.format(status.nextRotationOdometer)} mi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Every ${numberFormat.format(status.rotationInterval)} mi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ExpressiveWavyProgressIndicator(
                        progress = animatedRotationProgress,
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val percentInt = (wearPercentage * 100).toInt()
                        Text(
                            text = "Tread: $percentInt% (~${numberFormat.format(remainingTreadMiles)} mi left)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Installed at ${numberFormat.format(tire.odometerInstalled)} mi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Bottom Connected M3 Expressive ButtonGroup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading primary connected button: + Rotate
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        bottomStart = 16.dp,
                        topEnd = 6.dp,
                        bottomEnd = 6.dp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                bottomStart = 16.dp,
                                topEnd = 6.dp,
                                bottomEnd = 6.dp
                            )
                        )
                        .expressiveBounceClick(pressedScale = 0.96f, onClick = onLogRotationClick)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Rotate",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                // Middle connected button: Interval
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .expressiveBounceClick(pressedScale = 0.96f, onClick = onEditIntervalClick)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Interval",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                // Trailing connected button: New Tires
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 6.dp,
                        bottomStart = 6.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 6.dp,
                                bottomStart = 6.dp,
                                topEnd = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .expressiveBounceClick(pressedScale = 0.96f, onClick = onInstallNewTireClick)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "New Tires",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive linear wavy progress indicator.
 * Renders an organic sinusoidal wave for the active progress segment and a flat rounded track
 * for the remaining segment, flattening smoothly near 0% and 100%.
 */
@Composable
fun ExpressiveWavyProgressIndicator(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val strokeWidthPx = 3.5.dp.toPx()
        val halfStroke = strokeWidthPx / 2f
        val startX = halfStroke
        val endX = (size.width - halfStroke).coerceAtLeast(startX)
        val totalWidth = endX - startX
        val centerY = size.height / 2f

        val activeEndX = startX + totalWidth * clampedProgress
        val gapPx = 6.dp.toPx()

        // Draw remaining flat track
        val trackStartX = if (clampedProgress > 0.01f) {
            (activeEndX + gapPx).coerceAtMost(endX)
        } else {
            startX
        }
        if (trackStartX < endX) {
            drawLine(
                color = trackColor,
                start = Offset(trackStartX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        // Draw active wavy segment
        if (clampedProgress > 0.005f) {
            val wavelengthPx = 22.dp.toPx().coerceAtLeast(1f)
            // Dampen amplitude smoothly at very small progress so tiny segments don't clip
            val envelopeScale = when {
                clampedProgress < 0.08f -> clampedProgress / 0.08f
                else -> 1f
            }
            val maxAmplitudePx = ((size.height - strokeWidthPx) / 2f).coerceAtLeast(0f) * 0.85f * envelopeScale

            val wavePath = Path()
            val stepPx = 2f
            var x = startX
            var isFirst = true
            while (x <= activeEndX) {
                val phase = ((x - startX) / wavelengthPx) * (2f * Math.PI.toFloat())
                val y = centerY + sin(phase) * maxAmplitudePx
                if (isFirst) {
                    wavePath.moveTo(x, y)
                    isFirst = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += stepPx
            }
            val finalPhase = ((activeEndX - startX) / wavelengthPx) * (2f * Math.PI.toFloat())
            wavePath.lineTo(activeEndX, centerY + sin(finalPhase) * maxAmplitudePx)

            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}
