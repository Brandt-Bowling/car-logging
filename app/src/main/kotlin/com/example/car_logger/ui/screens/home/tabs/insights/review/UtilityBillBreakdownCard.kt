package com.example.car_logger.ui.screens.home.tabs.insights.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.services.ParsedBillLineItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BillBreakdownCard(
    breakdownItems: List<ParsedBillLineItem>,
    offPeakKwh: Double?,
    onPeakKwh: Double?,
    offPeakSupplyRate: Double?,
    onPeakSupplyRate: Double?,
    distributionRatePerKwh: Double?,
    totalAmount: Double,
    fixedCharges: Double,
    totalKwh: Double,
    calculatedBlendedRate: Double,
    useTouRate: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember(breakdownItems) { mutableStateOf(false) }

    val touKwhTotal = ((offPeakKwh ?: 0.0) + (onPeakKwh ?: 0.0)).takeIf { it > 0.0 }
    val offPeakSharePct = if (offPeakKwh != null && touKwhTotal != null) {
        ((offPeakKwh / touKwhTotal) * 100.0).roundToInt().coerceIn(0, 100)
    } else {
        null
    }

    val subtitle = buildString {
        if (breakdownItems.isNotEmpty()) {
            append("${breakdownItems.size} ${if (breakdownItems.size == 1) "line item" else "line items"}")
        } else {
            append("Rate calculation")
        }
        if (offPeakSharePct != null) {
            append(" · $offPeakSharePct% off-peak")
        } else if (fixedCharges > 0.0 && totalAmount > 0.0) {
            append(" · $${String.format(Locale.US, "%.2f", fixedCharges)} fixed excluded")
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            BreakdownCardHeader(subtitle = subtitle, expanded = expanded)

            AnimatedVisibility(visible = expanded) {
                BreakdownExpandedContent(
                    breakdownItems = breakdownItems,
                    offPeakKwh = offPeakKwh,
                    onPeakKwh = onPeakKwh,
                    offPeakSharePct = offPeakSharePct,
                    offPeakSupplyRate = offPeakSupplyRate,
                    onPeakSupplyRate = onPeakSupplyRate,
                    distributionRatePerKwh = distributionRatePerKwh,
                    totalAmount = totalAmount,
                    fixedCharges = fixedCharges,
                    totalKwh = totalKwh,
                    calculatedBlendedRate = calculatedBlendedRate,
                    useTouRate = useTouRate
                )
            }
        }
    }
}

@Composable
private fun BreakdownCardHeader(subtitle: String, expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Breakdown",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse breakdown" else "Expand breakdown",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BreakdownExpandedContent(
    breakdownItems: List<ParsedBillLineItem>,
    offPeakKwh: Double?,
    onPeakKwh: Double?,
    offPeakSharePct: Int?,
    offPeakSupplyRate: Double?,
    onPeakSupplyRate: Double?,
    distributionRatePerKwh: Double?,
    totalAmount: Double,
    fixedCharges: Double,
    totalKwh: Double,
    calculatedBlendedRate: Double,
    useTouRate: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        if (offPeakKwh != null || onPeakKwh != null) {
            TouKwhSplitPill(offPeakKwh = offPeakKwh, onPeakKwh = onPeakKwh, offPeakSharePct = offPeakSharePct)
        }

        if (breakdownItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                breakdownItems.forEach { item -> BreakdownLineItemRow(item = item) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }

        BreakdownSummaryFooter(
            offPeakSupplyRate = offPeakSupplyRate,
            onPeakSupplyRate = onPeakSupplyRate,
            distributionRatePerKwh = distributionRatePerKwh,
            totalAmount = totalAmount,
            fixedCharges = fixedCharges,
            totalKwh = totalKwh,
            calculatedBlendedRate = calculatedBlendedRate,
            useTouRate = useTouRate
        )
    }
}

@Composable
private fun TouKwhSplitPill(
    offPeakKwh: Double?,
    onPeakKwh: Double?,
    offPeakSharePct: Int?
) {
    val splitText = buildList {
        offPeakKwh?.let { kwh ->
            val pctPart = offPeakSharePct?.let { " ($it%)" } ?: ""
            add("Off-peak: ${String.format(Locale.US, "%.1f", kwh)} kWh$pctPart")
        }
        onPeakKwh?.let { kwh ->
            val onPct = offPeakSharePct?.let { " (${(100 - it).coerceIn(0, 100)}%)" } ?: ""
            add("On-peak: ${String.format(Locale.US, "%.1f", kwh)} kWh$onPct")
        }
    }.joinToString(" · ")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = splitText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BreakdownLineItemRow(item: ParsedBillLineItem) {
    val detailParts = buildList {
        add(breakdownCategoryLabel(item.category))
        if (item.kwh != null && item.ratePerKwh != null) {
            add("${String.format(Locale.US, "%.1f", item.kwh)} kWh × $${formatRateForInput(item.ratePerKwh)}")
        } else if (item.ratePerKwh != null) {
            add("$${formatRateForInput(item.ratePerKwh)}/kWh")
        } else if (item.kwh != null) {
            add("${String.format(Locale.US, "%.1f", item.kwh)} kWh")
        }
    }

    val rightValueText = when {
        item.amount != null && item.amount < 0.0 -> "-$${String.format(Locale.US, "%.2f", abs(item.amount))}"
        item.amount != null -> "$${String.format(Locale.US, "%.2f", item.amount)}"
        item.ratePerKwh != null -> "$${formatRateForInput(item.ratePerKwh)}/kWh"
        else -> "—"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detailParts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = rightValueText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BreakdownSummaryFooter(
    offPeakSupplyRate: Double?,
    onPeakSupplyRate: Double?,
    distributionRatePerKwh: Double?,
    totalAmount: Double,
    fixedCharges: Double,
    totalKwh: Double,
    calculatedBlendedRate: Double,
    useTouRate: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (useTouRate && offPeakSupplyRate != null) {
            val dist = (distributionRatePerKwh ?: 0.0).coerceAtLeast(0.0)
            Text(
                text = "Off-peak EV rate: $${formatRateForInput(offPeakSupplyRate)} supply + $${formatRateForInput(dist)} delivery = $${formatRateForInput(offPeakSupplyRate + dist)}/kWh",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            onPeakSupplyRate?.let { onPeak ->
                Text(
                    text = "On-peak supply (excluded): $${formatRateForInput(onPeak)}/kWh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val usageAmount = (totalAmount - fixedCharges).coerceAtLeast(0.0)
        if (totalAmount > 0.0 && totalKwh > 0.0) {
            Text(
                text = "Blended rate: ($${String.format(Locale.US, "%.2f", totalAmount)} − $${String.format(Locale.US, "%.2f", fixedCharges)} fixed = $${String.format(Locale.US, "%.2f", usageAmount)}) ÷ ${String.format(Locale.US, "%,.1f", totalKwh)} kWh = $${formatRateForInput(calculatedBlendedRate)}/kWh",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun breakdownCategoryLabel(category: String): String = when (category) {
    "OFF_PEAK_SUPPLY" -> "Off-peak supply"
    "ON_PEAK_SUPPLY" -> "On-peak supply"
    "DISTRIBUTION" -> "Delivery"
    "FIXED" -> "Fixed fee"
    else -> "Tax / other"
}
