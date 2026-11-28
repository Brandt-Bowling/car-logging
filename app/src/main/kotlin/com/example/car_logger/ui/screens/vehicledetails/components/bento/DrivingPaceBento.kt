package com.example.car_logger.ui.screens.vehicledetails.components.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.DrivingActivityBar
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleBentoPeriodMetrics
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun DrivingPaceBentoTile(
    metrics: VehicleBentoPeriodMetrics,
    isExpanded: Boolean,
    numberFormat: NumberFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "pace_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "pace_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "pace_tile_border"
    )
    val sparkBars = remember(metrics.activityBars) {
        val raw = metrics.activityBars.takeLast(5)
        if (raw.isEmpty()) {
            listOf(0.35f, 0.55f, 0.45f, 0.8f, 0.6f)
        } else {
            val maxMiles = raw.maxOfOrNull { it.car1Miles }?.coerceAtLeast(1) ?: 1
            raw.map { (it.car1Miles.toFloat() / maxMiles.toFloat()).coerceIn(0.2f, 1f) }
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .expressiveBounceClick(pressedScale = 0.96f, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DRIVING PACE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = fg.copy(alpha = 0.78f),
                    maxLines = 1
                )
                Surface(shape = CircleShape, color = fg.copy(alpha = 0.12f)) {
                    Text(
                        text = "${metrics.fleetSharePct}% share",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = fg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (metrics.hasOdometerData) String.format(Locale.US, "%.1f", metrics.dailyRate) else "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = fg
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "mi/d",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fg.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (metrics.hasOdometerData) "${numberFormat.format(metrics.totalMiles)} mi (${metrics.periodLabel})" else "Sync odometer",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DrivingPaceSparkline(sparkBars = sparkBars, baseColor = fg)
            }
        }
    }
}

@Composable
private fun DrivingPaceSparkline(
    sparkBars: List<Float>,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    val peakIdx = remember(sparkBars) {
        sparkBars.indices.maxByOrNull { sparkBars[it] } ?: (sparkBars.size - 1)
    }
    Row(
        modifier = modifier
            .height(24.dp)
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        sparkBars.forEachIndexed { idx, ratio ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight(ratio.coerceIn(0.22f, 1f))
                    .clip(CircleShape)
                    .background(
                        if (idx == peakIdx) MaterialTheme.colorScheme.primary else baseColor.copy(alpha = 0.32f)
                    )
            )
        }
    }
}

@Composable
internal fun DrivingPaceDrawerContent(
    metrics: VehicleBentoPeriodMetrics,
    granularity: InsightsTimeGranularity,
    inspectedBarIndex: Int?,
    onInspectBar: (Int) -> Unit,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BentoDrawerHeaderRow(
            title = "Driving Activity (${metrics.windowText})",
            subtitle = "${numberFormat.format(metrics.totalMiles)} mi driven · ${metrics.fleetSharePct}% of garage total"
        ) {
            InDrawerPeriodConnectedToggle(
                granularity = granularity,
                onSelectGranularity = onSelectGranularity
            )
        }

        val bars = metrics.activityBars
        if (bars.isEmpty() || !metrics.hasOdometerData) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sync at least 2 odometer readings to inspect daily and weekly mileage bars.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            val defaultIdx = remember(bars) {
                bars.indices.lastOrNull { bars[it].car1Miles > 0 } ?: bars.lastIndex
            }
            val activeIdx = (inspectedBarIndex ?: defaultIdx).coerceIn(0, bars.lastIndex)
            val activeBar = bars[activeIdx]

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeBar.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val estKwhText = metrics.milesPerKwh?.takeIf { it > 0.0 }?.let { eff ->
                        " · ~${String.format(Locale.US, "%.1f", activeBar.car1Miles / eff)} kWh"
                    } ?: ""
                    Text(
                        text = "${numberFormat.format(activeBar.car1Miles)} mi$estKwhText",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            DrivingPaceBarChart(bars = bars, activeIdx = activeIdx, onInspectBar = onInspectBar)
        }
    }
}

@Composable
private fun DrivingPaceBarChart(
    bars: List<DrivingActivityBar>,
    activeIdx: Int,
    onInspectBar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxMiles = remember(bars) { bars.maxOfOrNull { it.car1Miles }?.coerceAtLeast(1) ?: 1 }
    val barWidth = if (bars.size <= 5) 26.dp else 20.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { index, bar ->
            val isSelected = index == activeIdx
            val targetFill = if (bar.car1Miles > 0) (bar.car1Miles.toFloat() / (maxMiles * 1.12f)).coerceIn(0.14f, 1f) else 0f
            val animatedFill by animateFloatAsState(
                targetValue = targetFill,
                animationSpec = ExpressiveMotionTokens.SpatialSpring,
                label = "bento_pace_bar_$index"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onInspectBar(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(barWidth),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                    if (animatedFill > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animatedFill)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bar.shortLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
