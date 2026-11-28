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
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleInsightsBentoState
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun TrueCostPerMileBentoTile(
    state: VehicleInsightsBentoState,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "tco_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "tco_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "tco_tile_border"
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
                    text = "TRUE COST/MI",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = fg.copy(alpha = 0.78f),
                    maxLines = 1
                )
                Text(
                    text = "All-In",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = fg.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.lifetimeAllInCostPerMile?.let { "$${String.format(Locale.US, "%.3f", it)}" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = fg
                    )
                    if (state.lifetimeAllInCostPerMile != null) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "/mi",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fg.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Maint + Charging",
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
internal fun TrueCostPerMileDrawerContent(
    state: VehicleInsightsBentoState,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val milesSubtitle = if (state.trackedMiles > 0) {
        "Maintenance + EV Charging across ${numberFormat.format(state.trackedMiles)} mi"
    } else {
        "Combined Maintenance & EV Charging totals"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BentoDrawerHeaderRow(
            title = "Lifetime All-In Operating Cost",
            subtitle = milesSubtitle
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "$${String.format(Locale.US, "%,.2f", state.lifetimeTotalCost)} Total",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        if (state.lifetimeTotalCost > 0.0) {
            val maintWeight = (state.maintenanceSharePct / 100f).coerceIn(0.08f, 0.92f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (state.lifetimeMaintenanceCost > 0.0) {
                    Surface(
                        modifier = Modifier
                            .weight(if (state.lifetimeChargingCost > 0.0) maintWeight else 1f)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {}
                }
                if (state.lifetimeChargingCost > 0.0) {
                    Surface(
                        modifier = Modifier
                            .weight(if (state.lifetimeMaintenanceCost > 0.0) (1f - maintWeight).coerceAtLeast(0.08f) else 1f)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = CircleShape
                    ) {}
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TcoBreakdownCard(
                label = "MAINT (${state.maintenanceSharePct}%)",
                labelColor = MaterialTheme.colorScheme.primary,
                totalText = "$${String.format(Locale.US, "%,.2f", state.lifetimeMaintenanceCost)}",
                perMileText = state.lifetimeMaintenanceCostPerMile?.let { "$${String.format(Locale.US, "%.3f", it)}/mi" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            TcoBreakdownCard(
                label = "CHARGING (${state.chargingSharePct}%)",
                labelColor = MaterialTheme.colorScheme.tertiary,
                totalText = "$${String.format(Locale.US, "%,.2f", state.lifetimeChargingCost)}",
                perMileText = state.lifetimeChargingCostPerMile?.let { "$${String.format(Locale.US, "%.3f", it)}/mi" } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TcoBreakdownCard(
    label: String,
    labelColor: Color,
    totalText: String,
    perMileText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = labelColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = perMileText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
