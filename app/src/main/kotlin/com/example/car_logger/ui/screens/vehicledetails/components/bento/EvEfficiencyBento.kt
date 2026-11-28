package com.example.car_logger.ui.screens.vehicledetails.components.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleBentoPeriodMetrics
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@Composable
internal fun EvEfficiencyBentoTile(
    metrics: VehicleBentoPeriodMetrics,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "eff_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "eff_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "eff_tile_border"
    )

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
                    text = "EV EFFICIENCY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = fg.copy(alpha = 0.78f),
                    maxLines = 1
                )
                Text(
                    text = "⚡ ${String.format(Locale.US, "%.1f", metrics.totalKwh)} kWh",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = fg.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = metrics.milesPerKwh?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = fg
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "mi/kWh",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = fg.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", metrics.totalElectricityCost)} (${metrics.periodLabel})",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (metrics.totalKwh > 0.0) {
                        Text(
                            text = "${metrics.homePct}% Home",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = fg.copy(alpha = 0.9f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EvEfficiencyDrawerContent(
    metrics: VehicleBentoPeriodMetrics,
    granularity: InsightsTimeGranularity,
    workAccent: Color,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    modifier: Modifier = Modifier
) {
    val cpmText = metrics.energyCostPerMile?.let { " · $${String.format(Locale.US, "%.3f", it)}/mi energy cost" } ?: ""
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BentoDrawerHeaderRow(
            title = "Charging & Efficiency (${metrics.windowText})",
            subtitle = "${String.format(Locale.US, "%.1f", metrics.totalKwh)} kWh total$cpmText"
        ) {
            InDrawerPeriodConnectedToggle(
                granularity = granularity,
                onSelectGranularity = onSelectGranularity
            )
        }

        if (metrics.totalKwh > 0.0) {
            ChargingVenueSplitBar(metrics = metrics, workAccent = workAccent)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VenueEfficiencyMetricCard(
                label = "HOME (${metrics.homePct}%)",
                labelColor = MaterialTheme.colorScheme.primary,
                rateText = "$${String.format(Locale.US, "%.3f", metrics.homeEffectiveRate)}/kWh",
                costText = "$${String.format(Locale.US, "%.2f", metrics.homeCost)}",
                kwhCaption = "${String.format(Locale.US, "%.1f", metrics.homeKwh)} kWh attributed",
                modifier = Modifier.weight(1f)
            )
            VenueEfficiencyMetricCard(
                label = "PUBLIC (${metrics.publicPct}%)",
                labelColor = MaterialTheme.colorScheme.tertiary,
                rateText = if (metrics.publicEffectiveRate > 0.0) "$${String.format(Locale.US, "%.3f", metrics.publicEffectiveRate)}/kWh" else null,
                costText = "$${String.format(Locale.US, "%.2f", metrics.publicCost)}",
                kwhCaption = "${String.format(Locale.US, "%.1f", metrics.publicKwh)} kWh fast charge",
                modifier = Modifier.weight(1f)
            )
        }

        if (metrics.workKwh > 0.0) {
            WorkChargingSummaryCard(metrics = metrics, workAccent = workAccent)
        }
    }
}

@Composable
private fun ChargingVenueSplitBar(
    metrics: VehicleBentoPeriodMetrics,
    workAccent: Color,
    modifier: Modifier = Modifier
) {
    val activeSegments = listOfNotNull(
        if (metrics.homeKwh > 0.0) Pair((metrics.homeKwh / metrics.totalKwh).toFloat().coerceAtLeast(0.08f), MaterialTheme.colorScheme.primary) else null,
        if (metrics.workKwh > 0.0) Pair((metrics.workKwh / metrics.totalKwh).toFloat().coerceAtLeast(0.08f), workAccent) else null,
        if (metrics.publicKwh > 0.0) Pair((metrics.publicKwh / metrics.totalKwh).toFloat().coerceAtLeast(0.08f), MaterialTheme.colorScheme.tertiary) else null
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        activeSegments.forEach { (weight, color) ->
            Surface(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight(),
                color = color,
                shape = CircleShape
            ) {}
        }
    }
}

@Composable
private fun VenueEfficiencyMetricCard(
    label: String,
    labelColor: Color,
    rateText: String?,
    costText: String,
    kwhCaption: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = labelColor
                )
                if (rateText != null) {
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = costText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = kwhCaption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkChargingSummaryCard(
    metrics: VehicleBentoPeriodMetrics,
    workAccent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "WORK (${metrics.workPct}%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = workAccent
                )
                Text(
                    text = "Free",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", metrics.workKwh)} kWh workplace J1772",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (metrics.workAvoidedCost > 0.0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "~$${String.format(Locale.US, "%.2f", metrics.workAvoidedCost)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = workAccent
                    )
                    Text(
                        text = "saved vs. home",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
