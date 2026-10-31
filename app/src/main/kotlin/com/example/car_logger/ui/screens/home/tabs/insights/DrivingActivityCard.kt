package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.DrivingActivityBar
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingActivityCard(
    granularity: InsightsTimeGranularity,
    activityBars: List<DrivingActivityBar>,
    observationWindowText: String,
    totalMiles: Int,
    dailyRate: Float,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    canReset: Boolean = canGoNext,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onResetPeriod: () -> Unit = {},
    car1Name: String,
    car2Name: String,
    selectedCarId: String?,
    car1Id: String?,
    car2Id: String?,
    modifier: Modifier = Modifier
) {
    var inspectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val hasTwoCars = !car2Id.isNullOrBlank()

    val showCar1 = selectedCarId == null || selectedCarId == car1Id
    val showCar2 = hasTwoCars && (selectedCarId == null || selectedCarId == car2Id)

    val maxVal = remember(activityBars, showCar1, showCar2) {
        activityBars.maxOfOrNull { bar ->
            val v1 = if (showCar1) bar.car1Miles else 0
            val v2 = if (showCar2) bar.car2Miles else 0
            if (showCar1 && showCar2) (v1 + v2) else maxOf(v1, v2)
        }?.coerceAtLeast(1) ?: 1
    }

    val barWidth = when {
        activityBars.size <= 5 -> 30.dp
        activityBars.size <= 7 -> 24.dp
        activityBars.size <= 10 -> 18.dp
        else -> 14.dp
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Time Range Selector at the top (Google Health style: Week | Month | Year)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                InsightsTimeGranularity.values().forEachIndexed { index, gran ->
                    val isSelected = granularity == gran
                    SegmentedButton(
                        selected = isSelected,
                        onClick = {
                            inspectedBarIndex = null
                            onSelectGranularity(gran)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = InsightsTimeGranularity.values().size
                        ),
                        label = {
                            Text(
                                text = gran.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // 2. Current Time Range on Left, Navigation arrows and Reset button on Right (Google Health style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date on the left
                Text(
                    text = observationWindowText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Arrows on right with reset button on far right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            inspectedBarIndex = null
                            onPreviousPeriod()
                        },
                        enabled = canGoPrevious
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous period",
                            tint = if (canGoPrevious) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = {
                            inspectedBarIndex = null
                            onNextPeriod()
                        },
                        enabled = canGoNext
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next period",
                            tint = if (canGoNext) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = {
                            inspectedBarIndex = null
                            onResetPeriod()
                        },
                        enabled = canReset
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = "Reset to current period",
                            tint = if (canReset) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // 3. Stat Readout & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "${numberFormat.format(totalMiles)} mi",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", dailyRate)} mi/day average",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Legend when both vehicles are shown
                if (showCar1 && showCar2) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(
                                text = car1Name.split(" ").firstOrNull() ?: "Car 1",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                            Text(
                                text = car2Name.split(" ").firstOrNull() ?: "Car 2",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 4. Inspect Detail Tooltip Pill
            val activeInspectBar = inspectedBarIndex?.let { activityBars.getOrNull(it) }
            AnimatedVisibility(
                visible = activeInspectBar != null,
                enter = fadeIn(ExpressiveMotionTokens.SnappySpring),
                exit = fadeOut(ExpressiveMotionTokens.SnappySpring)
            ) {
                if (activeInspectBar != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeInspectBar.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (showCar1) {
                                    Text(
                                        text = "${car1Name.split(" ").firstOrNull() ?: "Car 1"}: ${numberFormat.format(activeInspectBar.car1Miles)} mi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (showCar2) {
                                    Text(
                                        text = "${car2Name.split(" ").firstOrNull() ?: "Car 2"}: ${numberFormat.format(activeInspectBar.car2Miles)} mi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Google Health Steps Chart Area (Capsule Tracks + Thick Filled Bars)
            if (activityBars.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No driving recorded in this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    activityBars.forEachIndexed { index, bar ->
                        val isSelected = inspectedBarIndex == index

                        val totalBarMiles = if (showCar1 && showCar2) {
                            bar.car1Miles + bar.car2Miles
                        } else if (showCar1) {
                            bar.car1Miles
                        } else {
                            bar.car2Miles
                        }

                        val targetFillPct = if (totalBarMiles > 0) {
                            (totalBarMiles.toFloat() / (maxVal * 1.15f)).coerceIn(0.06f, 1f)
                        } else {
                            0f
                        }

                        val animatedFillPct by animateFloatAsState(
                            targetValue = targetFillPct,
                            animationSpec = ExpressiveMotionTokens.SpatialSpring,
                            label = "bar_fill_pct"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    inspectedBarIndex = if (inspectedBarIndex == index) null else index
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Capsule track box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .width(barWidth),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                // Full-height subtle background capsule track
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                                        )
                                )

                                // Active filled capsule rising from the bottom
                                if (animatedFillPct > 0f) {
                                    if (showCar1 && showCar2) {
                                        // Stacked capsule for both cars
                                        val totalMilesFloat = (bar.car1Miles + bar.car2Miles).coerceAtLeast(1).toFloat()
                                        val c1Weight = (bar.car1Miles.toFloat() / totalMilesFloat).coerceAtLeast(0.001f)
                                        val c2Weight = (bar.car2Miles.toFloat() / totalMilesFloat).coerceAtLeast(0.001f)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(animatedFillPct)
                                                .clip(CircleShape)
                                        ) {
                                            if (bar.car2Miles > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(c2Weight)
                                                        .background(MaterialTheme.colorScheme.tertiary)
                                                )
                                            }
                                            if (bar.car1Miles > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(c1Weight)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    } else {
                                        // Single vehicle capsule
                                        val singleColor = if (showCar1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(animatedFillPct)
                                                .clip(CircleShape)
                                                .background(singleColor)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // X-axis label
                            Text(
                                text = bar.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
