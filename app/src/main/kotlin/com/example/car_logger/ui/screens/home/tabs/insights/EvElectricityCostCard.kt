package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.ElectricityCostData
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@Composable
fun EvElectricityCostCard(
    data: ElectricityCostData,
    selectedVenue: ChargingVenue,
    onSelectVenue: (ChargingVenue) -> Unit,
    selectedCarId: String?,
    car1Id: String?,
    car1Name: String,
    car2Id: String?,
    car2Name: String,
    isParsingFile: Boolean,
    billCount: Int,
    sessionCount: Int,
    onUploadUtilityBill: () -> Unit,
    onUploadChargerUsage: () -> Unit,
    onOpenManualChargerSheet: () -> Unit,
    onOpenManualBillSheet: () -> Unit,
    onOpenManageSheet: () -> Unit,
    lastUploadCount: Int = 0,
    onUndoLastChargerUpload: () -> Unit = {},
    onUndoAndRetagLastChargerUpload: () -> Unit = {},
    onSyncWallConnectors: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Charging Costs",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedVenue == ChargingVenue.HOME) {
                            "Home charger costs & electric rate"
                        } else {
                            "Public fast chargers & away from home"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Manage Bills & Logs pill button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.expressiveBounceClick { onOpenManageSheet() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Manage bills and logs",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        val totalItems = billCount + sessionCount
                        Text(
                            text = if (totalItems > 0) "History ($totalItems)" else "History",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // M3 Expressive Connected Button Group (Home Charging | Public Charging)
            VenueConnectedButtonGroup(
                selectedVenue = selectedVenue,
                onSelectVenue = onSelectVenue
            )

            if (isParsingFile) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Reading your file...",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = selectedVenue,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "venue_content"
            ) { venue ->
                val isHome = venue == ChargingVenue.HOME
                val hasVenueData = if (isHome) data.hasHomeData else data.hasPublicData

                if (!hasVenueData) {
                    EmptyVenueState(
                        isHome = isHome,
                        data = data,
                        onOpenManualBillSheet = onOpenManualBillSheet
                    )
                } else {
                    PopulatedVenueContent(
                        isHome = isHome,
                        data = data,
                        selectedCarId = selectedCarId,
                        car1Id = car1Id,
                        car1Name = car1Name,
                        car2Id = car2Id,
                        car2Name = car2Name,
                        onOpenManageSheet = onOpenManageSheet,
                        onOpenManualBillSheet = onOpenManualBillSheet
                    )
                }
            }

            // M3 Expressive Split Button (+ Add Charger Usage | Dropdown)
            ExpressiveChargingSplitButton(
                onPrimaryAction = onOpenManualChargerSheet,
                onUploadUtilityBill = onUploadUtilityBill,
                onUploadChargerUsage = onUploadChargerUsage,
                onOpenManualChargerSheet = onOpenManualChargerSheet,
                onOpenManualBillSheet = onOpenManualBillSheet,
                onOpenManageSheet = onOpenManageSheet,
                lastUploadCount = lastUploadCount,
                onUndoLastChargerUpload = onUndoLastChargerUpload,
                onUndoAndRetagLastChargerUpload = onUndoAndRetagLastChargerUpload,
                onSyncWallConnectors = onSyncWallConnectors
            )
        }
    }
}

@Composable
private fun VenueConnectedButtonGroup(
    selectedVenue: ChargingVenue,
    onSelectVenue: (ChargingVenue) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChargingVenue.entries.forEachIndexed { index, venue ->
            val selected = venue == selectedVenue
            val isFirst = index == 0

            val startOuterRadius by animateDpAsState(
                targetValue = 24.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "start_outer"
            )
            val innerCornerRadius by animateDpAsState(
                targetValue = if (selected) 24.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "inner_corner"
            )

            val shape = if (isFirst) {
                RoundedCornerShape(
                    topStart = startOuterRadius,
                    bottomStart = startOuterRadius,
                    topEnd = innerCornerRadius,
                    bottomEnd = innerCornerRadius
                )
            } else {
                RoundedCornerShape(
                    topStart = innerCornerRadius,
                    bottomStart = innerCornerRadius,
                    topEnd = startOuterRadius,
                    bottomEnd = startOuterRadius
                )
            }

            val containerColor by animateColorAsState(
                targetValue = when {
                    selected && venue == ChargingVenue.HOME -> MaterialTheme.colorScheme.primaryContainer
                    selected && venue == ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "venue_bg"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    selected && venue == ChargingVenue.HOME -> MaterialTheme.colorScheme.onPrimaryContainer
                    selected && venue == ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "venue_fg"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectVenue(venue) },
                shape = shape,
                color = containerColor,
                contentColor = contentColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (venue == ChargingVenue.HOME) {
                            Icons.Rounded.Home
                        } else {
                            Icons.Rounded.EvStation
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = venue.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyVenueState(
    isHome: Boolean,
    data: ElectricityCostData,
    onOpenManualBillSheet: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isHome) Icons.Rounded.ElectricBolt else Icons.Rounded.EvStation,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = when {
                            !data.hasAnyData -> "Track your EV charging costs"
                            isHome -> "No home charging in this period"
                            else -> "No public charging in this period"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = when {
                        !data.hasAnyData -> "Upload your electric bill and home charger log (or enter a monthly total) to see your real electric rate, mi/kWh, and how much you've saved vs. gas."
                        isHome -> "Upload a charger export CSV, PDF, screenshot, or enter your monthly kWh total for this period."
                        else -> "Upload Supercharger, Electrify America, or EVgo receipts to keep fast charging separate from your home electric bill."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isHome) {
            RateChipsRow(
                rateValue = data.homeEffectiveRate,
                rateSourceLabel = data.homeRateSourceLabel,
                isEstimated = data.homeIsEstimatedRate,
                leftOverline = "ELECTRIC RATE",
                rightOverline = "RATE SOURCE",
                onLeftClick = onOpenManualBillSheet,
                onRightClick = onOpenManualBillSheet
            )
        }
    }
}

@Composable
private fun PopulatedVenueContent(
    isHome: Boolean,
    data: ElectricityCostData,
    selectedCarId: String?,
    car1Id: String?,
    car1Name: String,
    car2Id: String?,
    car2Name: String,
    onOpenManageSheet: () -> Unit,
    onOpenManualBillSheet: () -> Unit
) {
    val totalCost = if (isHome) data.homeTotalCost else data.publicTotalCost
    val totalKwh = if (isHome) data.homeTotalKwh else data.publicTotalKwh
    val milesPerKwh = if (isHome) data.homeMilesPerKwh else data.publicMilesPerKwh
    val costPerMile = if (isHome) data.homeCostPerMile else data.publicCostPerMile
    val sessionCount = if (isHome) data.homeSessionCount else data.publicSessionCount

    val heroBg = if (isHome) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f)
    }
    val heroAccent = if (isHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 20.dp Hero Tonal Highlight Container
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = heroBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHome) "HOME CHARGING" else "PUBLIC CHARGING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = heroAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "$%.2f", totalCost),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sessionLabel = if (sessionCount == 1) "1 log" else "$sessionCount logs"
                    Text(
                        text = "${formatKwh(totalKwh)} kWh · $sessionLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "EFFICIENCY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = milesPerKwh?.let { String.format(Locale.US, "%.1f mi/kWh", it) } ?: "— mi/kWh",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = costPerMile?.let { String.format(Locale.US, "$%.3f / mi", it) } ?: "Sync odometer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 16.dp Tonal Source & Rate Parameter Chips
        RateChipsRow(
            rateValue = if (isHome) data.homeEffectiveRate else data.publicEffectiveRate,
            rateSourceLabel = if (isHome) data.homeRateSourceLabel else data.publicRateSourceLabel,
            isEstimated = isHome && data.homeIsEstimatedRate,
            leftOverline = if (isHome) "ELECTRIC RATE" else "AVG PAID RATE",
            rightOverline = if (isHome) "RATE SOURCE" else "RECEIPTS",
            onLeftClick = if (isHome) onOpenManualBillSheet else onOpenManageSheet,
            onRightClick = onOpenManageSheet
        )

        // Cost by Vehicle Breakdown (when Both selected and both cars exist)
        val c1Cost = if (isHome) data.homeCar1Cost else data.publicCar1Cost
        val c1Kwh = if (isHome) data.homeCar1Kwh else data.publicCar1Kwh
        val c1Pct = if (isHome) data.homeCar1SharePct else data.publicCar1SharePct
        val c2Cost = if (isHome) data.homeCar2Cost else data.publicCar2Cost
        val c2Kwh = if (isHome) data.homeCar2Kwh else data.publicCar2Kwh
        val c2Pct = if (isHome) data.homeCar2SharePct else data.publicCar2SharePct

        val showBreakdown = selectedCarId == null &&
            !car1Id.isNullOrBlank() &&
            !car2Id.isNullOrBlank() &&
            (c1Kwh > 0.0 || c2Kwh > 0.0)

        if (showBreakdown) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Cost by Vehicle",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 14.dp Proportional Capsule Bar (matches FleetShareCard)
                val c1Weight by animateFloatAsState(
                    targetValue = (c1Pct / 100f).coerceIn(0.05f, 0.95f),
                    animationSpec = ExpressiveMotionTokens.BouncySpring,
                    label = "ev_c1_bar"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (c1Kwh > 0.0) {
                        Surface(
                            modifier = Modifier
                                .weight(c1Weight)
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {}
                    }
                    if (c2Kwh > 0.0) {
                        Surface(
                            modifier = Modifier
                                .weight((1f - c1Weight).coerceAtLeast(0.05f))
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = CircleShape
                        ) {}
                    }
                }

                // Dual 20.dp Vehicle Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VehicleElectricitySubCard(
                        name = car1Name,
                        cost = c1Cost,
                        kwh = c1Kwh,
                        sharePct = c1Pct,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    VehicleElectricitySubCard(
                        name = car2Name,
                        cost = c2Cost,
                        kwh = c2Kwh,
                        sharePct = c2Pct,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RateChipsRow(
    rateValue: Double,
    rateSourceLabel: String,
    isEstimated: Boolean,
    leftOverline: String,
    rightOverline: String,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .expressiveBounceClick { onLeftClick() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = leftOverline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$${String.format(Locale.US, "%.3f", rateValue)}/kWh",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isEstimated) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "Est.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .expressiveBounceClick { onRightClick() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = rightOverline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = rateSourceLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VehicleElectricitySubCard(
    name: String,
    cost: Double,
    kwh: Double,
    sharePct: Int,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = String.format(Locale.US, "$%.2f", cost),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${formatKwh(kwh)} kWh · $sharePct%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ExpressiveChargingSplitButton(
    onPrimaryAction: () -> Unit,
    onUploadUtilityBill: () -> Unit,
    onUploadChargerUsage: () -> Unit,
    onOpenManualChargerSheet: () -> Unit,
    onOpenManualBillSheet: () -> Unit,
    onOpenManageSheet: () -> Unit,
    lastUploadCount: Int = 0,
    onUndoLastChargerUpload: () -> Unit = {},
    onUndoAndRetagLastChargerUpload: () -> Unit = {},
    onSyncWallConnectors: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val innerRadius by animateDpAsState(
        targetValue = if (menuExpanded) 24.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "split_inner_radius"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = ExpressiveMotionTokens.SnappySpring,
        label = "split_chevron"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading primary segment
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .expressiveBounceClick { onPrimaryAction() },
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    bottomStart = 24.dp,
                    topEnd = innerRadius,
                    bottomEnd = innerRadius
                ),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Charging or Bill",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Trailing chevron segment (morphs to full circle when menu opens)
            Surface(
                modifier = Modifier
                    .width(52.dp)
                    .height(48.dp)
                    .expressiveBounceClick { menuExpanded = !menuExpanded },
                shape = RoundedCornerShape(
                    topStart = innerRadius,
                    bottomStart = innerRadius,
                    topEnd = 24.dp,
                    bottomEnd = 24.dp
                ),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "More upload and entry options",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = RoundedCornerShape(20.dp)
        ) {
            DropdownMenuItem(
                text = { Text("Sync Tesla Wall Connectors") },
                leadingIcon = { Icon(Icons.Rounded.EvStation, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onSyncWallConnectors()
                }
            )
            DropdownMenuItem(
                text = { Text("Upload Electric Bill (PDF / Photo)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onUploadUtilityBill()
                }
            )
            DropdownMenuItem(
                text = { Text("Upload Charger Log (CSV / PDF / Photo)") },
                leadingIcon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onUploadChargerUsage()
                }
            )
            DropdownMenuItem(
                text = { Text("Enter Charger Usage Manually") },
                leadingIcon = { Icon(Icons.Rounded.ElectricBolt, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onOpenManualChargerSheet()
                }
            )
            DropdownMenuItem(
                text = { Text("Enter Electric Bill Manually") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onOpenManualBillSheet()
                }
            )
            if (lastUploadCount > 0) {
                DropdownMenuItem(
                    text = {
                        Text("Undo Last Charger Upload ($lastUploadCount ${if (lastUploadCount == 1) "log" else "logs"})")
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onUndoLastChargerUpload()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text("Undo & Re-tag Last Upload ($lastUploadCount ${if (lastUploadCount == 1) "log" else "logs"})")
                    },
                    leadingIcon = { Icon(Icons.Rounded.DirectionsCar, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onUndoAndRetagLastChargerUpload()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Charging & Bills History") },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onOpenManageSheet()
                }
            )
        }
    }
}

private fun formatKwh(kwh: Double): String {
    return if (kwh >= 100.0) {
        String.format(Locale.US, "%.0f", kwh)
    } else {
        String.format(Locale.US, "%.1f", kwh)
    }
}
