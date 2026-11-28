package com.example.car_logger.ui.screens.home.tabs.insights.electricity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.ElectricityCostData
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@Composable
internal fun PopulatedVenueContent(
    venue: ChargingVenue,
    workContainerColor: Color,
    workContentColor: Color,
    data: ElectricityCostData,
    selectedCarId: String?,
    car1Id: String?,
    car1Name: String,
    car2Id: String?,
    car2Name: String,
    onOpenManageSheet: () -> Unit,
    onSaveCustomRate: (Float) -> Unit,
    onOpenPeriodLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHome = venue == ChargingVenue.HOME
    val isWork = venue == ChargingVenue.WORK

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VenueHeroSummaryCard(
            venue = venue,
            workContainerColor = workContainerColor,
            workContentColor = workContentColor,
            totalCost = when (venue) {
                ChargingVenue.HOME -> data.homeTotalCost
                ChargingVenue.WORK -> 0.0
                ChargingVenue.PUBLIC -> data.publicTotalCost
            },
            totalKwh = when (venue) {
                ChargingVenue.HOME -> data.homeTotalKwh
                ChargingVenue.WORK -> data.workTotalKwh
                ChargingVenue.PUBLIC -> data.publicTotalKwh
            },
            sessionCount = when (venue) {
                ChargingVenue.HOME -> data.homeSessionCount
                ChargingVenue.WORK -> data.workSessionCount
                ChargingVenue.PUBLIC -> data.publicSessionCount
            },
            milesPerKwh = data.combinedMilesPerKwh,
            costPerMile = data.combinedCostPerMile,
            onClick = onOpenPeriodLogs
        )

        if (isWork) {
            WorkRateChipsRow(
                workContentColor = workContentColor,
                avoidedHomeCost = data.workAvoidedHomeCost,
                onOpenPeriodLogs = onOpenPeriodLogs,
                onOpenManageSheet = onOpenManageSheet
            )
        } else {
            RateChipsRow(
                rateValue = if (isHome) data.homeEffectiveRate else data.publicEffectiveRate,
                rateSourceLabel = if (isHome) data.homeRateSourceLabel else data.publicRateSourceLabel,
                isEstimated = isHome && data.homeIsEstimatedRate,
                isEditable = isHome,
                leftOverline = if (isHome) "ELECTRIC RATE" else "AVG RATE",
                rightOverline = if (isHome) "SOURCE" else "SESSIONS",
                onSaveCustomRate = onSaveCustomRate,
                onLeftClick = onOpenManageSheet,
                onRightClick = onOpenManageSheet
            )
        }

        VehicleCostBreakdownBlock(
            venue = venue,
            data = data,
            selectedCarId = selectedCarId,
            car1Id = car1Id,
            car1Name = car1Name,
            car2Id = car2Id,
            car2Name = car2Name
        )
    }
}

@Composable
private fun VenueHeroSummaryCard(
    venue: ChargingVenue,
    workContainerColor: Color,
    workContentColor: Color,
    totalCost: Double,
    totalKwh: Double,
    sessionCount: Int,
    milesPerKwh: Double?,
    costPerMile: Double?,
    onClick: () -> Unit
) {
    val isWork = venue == ChargingVenue.WORK
    val heroBg = when (venue) {
        ChargingVenue.HOME -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
        ChargingVenue.WORK -> workContainerColor.copy(alpha = 0.72f)
        ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f)
    }
    val heroAccent = when (venue) {
        ChargingVenue.HOME -> MaterialTheme.colorScheme.primary
        ChargingVenue.WORK -> workContentColor
        ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiary
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = heroBg,
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (venue) {
                            ChargingVenue.HOME -> "HOME CHARGING"
                            ChargingVenue.WORK -> "WORK CHARGING"
                            ChargingVenue.PUBLIC -> "PUBLIC CHARGING"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                        color = heroAccent
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "View sessions for period",
                        modifier = Modifier.size(14.dp),
                        tint = heroAccent
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "$%.2f", totalCost),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isWork) {
                        Surface(shape = CircleShape, color = workContentColor.copy(alpha = 0.16f)) {
                            Text(
                                text = "FREE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
                                color = workContentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                val sessionLabel = if (sessionCount == 1) "1 session" else "$sessionCount sessions"
                Text(
                    text = "${formatKwh(totalKwh)} kWh · $sessionLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "OVERALL EFFICIENCY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = milesPerKwh?.let { String.format(Locale.US, "%.1f mi/kWh", it) } ?: "— mi/kWh",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = costPerMile?.let { String.format(Locale.US, "$%.3f / mi", it) } ?: "Needs mileage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkRateChipsRow(
    workContentColor: Color,
    avoidedHomeCost: Double,
    onOpenPeriodLogs: () -> Unit,
    onOpenManageSheet: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .expressiveBounceClick { onOpenPeriodLogs() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("WORK RATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    text = "$0.000/kWh",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = workContentColor,
                    maxLines = 1
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .expressiveBounceClick { onOpenManageSheet() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "EST. SAVED VS. HOME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "~$${String.format(Locale.US, "%.2f", avoidedHomeCost)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = workContentColor,
                    maxLines = 1
                )
            }
        }
    }
}

internal fun formatKwh(kwh: Double): String {
    return if (kwh >= 100.0) String.format(Locale.US, "%.0f", kwh) else String.format(Locale.US, "%.1f", kwh)
}
