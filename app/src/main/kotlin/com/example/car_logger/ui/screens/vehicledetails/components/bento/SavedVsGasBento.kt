package com.example.car_logger.ui.screens.vehicledetails.components.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun SavedVsGasBentoTile(
    metrics: VehicleBentoPeriodMetrics,
    isExpanded: Boolean,
    savingsBg: Color,
    savingsAccent: Color,
    savingsText: Color,
    numberFormat: NumberFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isExpanded) savingsBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "savings_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) savingsText else MaterialTheme.colorScheme.onSurface,
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "savings_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) savingsAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "savings_tile_border"
    )

    val displayedSavings = metrics.netFuelSavings ?: metrics.gasEquivalentCost
    val isPositive = displayedSavings >= 0.0
    val formattedSavings = when {
        !metrics.hasOdometerData && !metrics.hasChargingData -> "—"
        isPositive -> "+$${String.format(Locale.US, "%.2f", displayedSavings)}"
        else -> "-$${String.format(Locale.US, "%.2f", abs(displayedSavings))}"
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
                    text = "SAVED VS GAS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = savingsAccent,
                    maxLines = 1
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", metrics.gasGallonsSaved)} gal",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = savingsAccent,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Text(
                    text = formattedSavings,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPositive) savingsAccent else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "~${numberFormat.format(metrics.co2AvoidedLbs)} lbs CO₂ (${metrics.periodLabel})",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun SavedVsGasDrawerContent(
    metrics: VehicleBentoPeriodMetrics,
    granularity: InsightsTimeGranularity,
    savingsBg: Color,
    savingsAccent: Color,
    savingsText: Color,
    numberFormat: NumberFormat,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    modifier: Modifier = Modifier
) {
    val mpgStr = if (metrics.baselineMpg == metrics.baselineMpg.toInt().toFloat()) {
        "${metrics.baselineMpg.toInt()} MPG"
    } else {
        "${String.format(Locale.US, "%.1f", metrics.baselineMpg)} MPG"
    }
    val netVal = metrics.netFuelSavings ?: (metrics.gasEquivalentCost - metrics.totalElectricityCost)
    val isPositive = netVal >= 0.0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BentoDrawerHeaderRow(
            title = "Gas Comparison (${metrics.windowText})",
            subtitle = "vs. $mpgStr baseline @ $${String.format(Locale.US, "%.2f", metrics.gasPrice)}/gal"
        ) {
            InDrawerPeriodConnectedToggle(
                granularity = granularity,
                onSelectGranularity = onSelectGranularity
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GasComparisonMetricTile(
                overline = "GAS EQUIV",
                value = "$${String.format(Locale.US, "%.2f", metrics.gasEquivalentCost)}",
                footnote = "${String.format(Locale.US, "%.1f", metrics.gasGallonsSaved)} gal",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                overlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                valueColor = AmberUrgent,
                footnoteColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            GasComparisonMetricTile(
                overline = "EV CHARGING",
                value = "-$${String.format(Locale.US, "%.2f", metrics.totalElectricityCost)}",
                footnote = "${String.format(Locale.US, "%.1f", metrics.totalKwh)} kWh",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                overlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                valueColor = MaterialTheme.colorScheme.onSurface,
                footnoteColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            GasComparisonMetricTile(
                overline = if (isPositive) "NET SAVED" else "NET DIFF",
                value = if (isPositive) "+$${String.format(Locale.US, "%.2f", netVal)}" else "-$${String.format(Locale.US, "%.2f", abs(netVal))}",
                footnote = "~${numberFormat.format(metrics.co2AvoidedLbs)} lbs CO₂",
                containerColor = if (isPositive) savingsBg else MaterialTheme.colorScheme.errorContainer,
                overlineColor = if (isPositive) savingsAccent else MaterialTheme.colorScheme.onErrorContainer,
                valueColor = if (isPositive) savingsText else MaterialTheme.colorScheme.onErrorContainer,
                footnoteColor = (if (isPositive) savingsText else MaterialTheme.colorScheme.onErrorContainer).copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GasComparisonMetricTile(
    overline: String,
    value: String,
    footnote: String,
    containerColor: Color,
    overlineColor: Color,
    valueColor: Color,
    footnoteColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = overline,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = overlineColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            Text(
                text = footnote,
                style = MaterialTheme.typography.labelSmall,
                color = footnoteColor
            )
        }
    }
}
