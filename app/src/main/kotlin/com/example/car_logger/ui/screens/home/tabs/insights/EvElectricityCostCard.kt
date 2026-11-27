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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.ElectricityCostData
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
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
    onUploadChargerUsage: () -> Unit,
    onOpenManualChargerSheet: () -> Unit,
    onOpenManualBillSheet: () -> Unit,
    onOpenManageSheet: () -> Unit,
    onSaveCustomRate: (Float) -> Unit = {},
    onSyncWallConnectors: () -> Unit = {},
    onSyncWorkCharging: () -> Unit = {},
    onSyncPublicCharging: () -> Unit = {},
    onOpenPeriodLogs: (ChargingVenue) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val workContainerColor = ExpressiveThemeTokens.savingsContainer
    val workContentColor = ExpressiveThemeTokens.savingsAccent

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
                        text = "Charging costs",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (selectedVenue) {
                            ChargingVenue.HOME -> "Home charging & electric rate"
                            ChargingVenue.WORK -> "Free workplace J1772 charging"
                            ChargingVenue.PUBLIC -> "Superchargers & DC fast charging"
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
                            contentDescription = "Manage bills and sessions",
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

            // M3 Expressive 3-Segment Connected Button Group (Home | Work | Public)
            VenueConnectedButtonGroup(
                selectedVenue = selectedVenue,
                workContainerColor = workContainerColor,
                workContentColor = workContentColor,
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
                val hasVenueData = when (venue) {
                    ChargingVenue.HOME -> data.hasHomeData
                    ChargingVenue.WORK -> data.hasWorkData
                    ChargingVenue.PUBLIC -> data.hasPublicData
                }

                if (!hasVenueData) {
                    EmptyVenueState(
                        venue = venue,
                        workAccent = workContentColor,
                        data = data,
                        onOpenManualBillSheet = onOpenManualBillSheet,
                        onSaveCustomRate = onSaveCustomRate
                    )
                } else {
                    PopulatedVenueContent(
                        venue = venue,
                        workContainerColor = workContainerColor,
                        workContentColor = workContentColor,
                        data = data,
                        selectedCarId = selectedCarId,
                        car1Id = car1Id,
                        car1Name = car1Name,
                        car2Id = car2Id,
                        car2Name = car2Name,
                        onOpenManageSheet = onOpenManageSheet,
                        onSaveCustomRate = onSaveCustomRate,
                        onOpenPeriodLogs = { onOpenPeriodLogs(venue) }
                    )
                }
            }

            // M3 Expressive Split Button (+ Add charging | Dropdown)
            ExpressiveChargingSplitButton(
                onPrimaryAction = onOpenManualChargerSheet,
                onUploadChargerUsage = onUploadChargerUsage,
                onOpenManualBillSheet = onOpenManualBillSheet,
                onSyncWallConnectors = onSyncWallConnectors,
                onSyncWorkCharging = onSyncWorkCharging,
                onSyncPublicCharging = onSyncPublicCharging
            )
        }
    }
}

@Composable
private fun VenueConnectedButtonGroup(
    selectedVenue: ChargingVenue,
    workContainerColor: Color,
    workContentColor: Color,
    onSelectVenue: (ChargingVenue) -> Unit
) {
    val venues = ChargingVenue.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        venues.forEachIndexed { index, venue ->
            val selected = venue == selectedVenue
            val isFirst = index == 0
            val isLast = index == venues.lastIndex

            val startRadius by animateDpAsState(
                targetValue = if (isFirst || selected) 24.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "venue_start_$index"
            )
            val endRadius by animateDpAsState(
                targetValue = if (isLast || selected) 24.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "venue_end_$index"
            )

            val shape = RoundedCornerShape(
                topStart = startRadius,
                bottomStart = startRadius,
                topEnd = endRadius,
                bottomEnd = endRadius
            )

            val containerColor by animateColorAsState(
                targetValue = when {
                    selected && venue == ChargingVenue.HOME -> MaterialTheme.colorScheme.primaryContainer
                    selected && venue == ChargingVenue.WORK -> workContainerColor
                    selected && venue == ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "venue_bg_$index"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    selected && venue == ChargingVenue.HOME -> MaterialTheme.colorScheme.onPrimaryContainer
                    selected && venue == ChargingVenue.WORK -> workContentColor
                    selected && venue == ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "venue_fg_$index"
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
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (venue) {
                            ChargingVenue.HOME -> Icons.Rounded.Home
                            ChargingVenue.WORK -> Icons.Rounded.Work
                            ChargingVenue.PUBLIC -> Icons.Rounded.EvStation
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = venue.shortLabel,
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
    venue: ChargingVenue,
    workAccent: Color,
    data: ElectricityCostData,
    onOpenManualBillSheet: () -> Unit,
    onSaveCustomRate: (Float) -> Unit
) {
    val isHome = venue == ChargingVenue.HOME
    val isWork = venue == ChargingVenue.WORK
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
                        imageVector = when (venue) {
                            ChargingVenue.HOME -> Icons.Rounded.ElectricBolt
                            ChargingVenue.WORK -> Icons.Rounded.Work
                            ChargingVenue.PUBLIC -> Icons.Rounded.EvStation
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when (venue) {
                            ChargingVenue.HOME -> MaterialTheme.colorScheme.primary
                            ChargingVenue.WORK -> workAccent
                            ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Text(
                        text = when {
                            !data.hasAnyData && isHome -> "Track charging costs"
                            isHome -> "No home charging in this period"
                            isWork -> "No work charging in this period"
                            else -> "No public charging in this period"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = when {
                        !data.hasAnyData && isHome -> "Add an electric bill or charging session to see efficiency and savings."
                        isHome -> "Sync your Wall Connectors, import a file, or add a home session."
                        isWork -> "Sync your Tesla after work, import a Charge Stats screenshot, or add a free J1772 session."
                        else -> "Sync Superchargers & DC charging, import a receipt screenshot, or add a session."
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
                isEditable = true,
                leftOverline = "ELECTRIC RATE",
                rightOverline = "SOURCE",
                onSaveCustomRate = onSaveCustomRate,
                onLeftClick = {},
                onRightClick = onOpenManualBillSheet
            )
        }
    }
}

@Composable
private fun PopulatedVenueContent(
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
    onOpenPeriodLogs: () -> Unit
) {
    val isHome = venue == ChargingVenue.HOME
    val isWork = venue == ChargingVenue.WORK

    val totalCost = when (venue) {
        ChargingVenue.HOME -> data.homeTotalCost
        ChargingVenue.WORK -> 0.0
        ChargingVenue.PUBLIC -> data.publicTotalCost
    }
    val totalKwh = when (venue) {
        ChargingVenue.HOME -> data.homeTotalKwh
        ChargingVenue.WORK -> data.workTotalKwh
        ChargingVenue.PUBLIC -> data.publicTotalKwh
    }
    val sessionCount = when (venue) {
        ChargingVenue.HOME -> data.homeSessionCount
        ChargingVenue.WORK -> data.workSessionCount
        ChargingVenue.PUBLIC -> data.publicSessionCount
    }

    // Overall combined efficiency (Home + Work + Public) stays consistent and accurate across all 3 tabs
    val milesPerKwh = data.combinedMilesPerKwh
    val costPerMile = data.combinedCostPerMile

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 20.dp Hero Tonal Highlight Container (tappable to view sessions for active period)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = heroBg,
            modifier = Modifier
                .fillMaxWidth()
                .expressiveBounceClick { onOpenPeriodLogs() }
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
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
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
                            Surface(
                                shape = CircleShape,
                                color = workContentColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "FREE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp
                                    ),
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
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
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
                        text = costPerMile?.let { String.format(Locale.US, "$%.3f / mi", it) } ?: "Needs mileage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 16.dp Tonal Source & Rate Parameter Chips (equal height)
        if (isWork) {
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
                        Text(
                            text = "WORK RATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
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
                            text = "~$${String.format(Locale.US, "%.2f", data.workAvoidedHomeCost)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = workContentColor,
                            maxLines = 1
                        )
                    }
                }
            }
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

        // Cost / Energy by Vehicle Breakdown (when All selected and both cars exist)
        val c1Cost = when (venue) {
            ChargingVenue.HOME -> data.homeCar1Cost
            ChargingVenue.WORK -> 0.0
            ChargingVenue.PUBLIC -> data.publicCar1Cost
        }
        val c1Kwh = when (venue) {
            ChargingVenue.HOME -> data.homeCar1Kwh
            ChargingVenue.WORK -> data.workCar1Kwh
            ChargingVenue.PUBLIC -> data.publicCar1Kwh
        }
        val c1Pct = when (venue) {
            ChargingVenue.HOME -> data.homeCar1SharePct
            ChargingVenue.WORK -> data.workCar1SharePct
            ChargingVenue.PUBLIC -> data.publicCar1SharePct
        }
        val c2Cost = when (venue) {
            ChargingVenue.HOME -> data.homeCar2Cost
            ChargingVenue.WORK -> 0.0
            ChargingVenue.PUBLIC -> data.publicCar2Cost
        }
        val c2Kwh = when (venue) {
            ChargingVenue.HOME -> data.homeCar2Kwh
            ChargingVenue.WORK -> data.workCar2Kwh
            ChargingVenue.PUBLIC -> data.publicCar2Kwh
        }
        val c2Pct = when (venue) {
            ChargingVenue.HOME -> data.homeCar2SharePct
            ChargingVenue.WORK -> data.workCar2SharePct
            ChargingVenue.PUBLIC -> data.publicCar2SharePct
        }

        val showBreakdown = selectedCarId == null &&
            !car1Id.isNullOrBlank() &&
            !car2Id.isNullOrBlank() &&
            (c1Kwh > 0.0 || c2Kwh > 0.0)

        if (showBreakdown) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isWork) "Energy by vehicle" else "Cost by vehicle",
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
                        isFree = isWork,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    VehicleElectricitySubCard(
                        name = car2Name,
                        cost = c2Cost,
                        kwh = c2Kwh,
                        sharePct = c2Pct,
                        isFree = isWork,
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
    isEditable: Boolean,
    leftOverline: String,
    rightOverline: String,
    onSaveCustomRate: (Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    var isEditing by remember(isEditable) { mutableStateOf(false) }
    var rateDraft by remember(rateValue) {
        mutableStateOf(String.format(Locale.US, "%.3f", rateValue))
    }
    var hasInputError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    fun commitRate() {
        val parsed = rateDraft.trim().toFloatOrNull()
        if (parsed != null && parsed >= 0f) {
            hasInputError = false
            isEditing = false
            keyboardController?.hide()
            onSaveCustomRate(parsed)
        } else {
            hasInputError = true
        }
    }

    fun cancelEdit() {
        hasInputError = false
        rateDraft = String.format(Locale.US, "%.3f", rateValue)
        isEditing = false
        keyboardController?.hide()
    }

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
                .then(
                    if (isEditing) {
                        Modifier
                    } else {
                        Modifier.expressiveBounceClick {
                            if (isEditable) {
                                rateDraft = String.format(Locale.US, "%.3f", rateValue)
                                hasInputError = false
                                isEditing = true
                            } else {
                                onLeftClick()
                            }
                        }
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            color = if (isEditing) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = when {
                isEditing && hasInputError -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                isEditing -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                else -> null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "SET RATE ($/kWh)" else leftOverline,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isEditing) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel editing rate",
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { cancelEdit() },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isEditable) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit electric rate",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BasicTextField(
                            value = rateDraft,
                            onValueChange = {
                                rateDraft = it
                                hasInputError = false
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { commitRate() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .expressiveBounceClick { commitRate() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Save electric rate",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$${String.format(Locale.US, "%.3f", rateValue)}/kWh",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .expressiveBounceClick { onRightClick() },
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
    isFree: Boolean = false,
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
                text = if (isFree) "Free" else String.format(Locale.US, "$%.2f", cost),
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
    onUploadChargerUsage: () -> Unit,
    onOpenManualBillSheet: () -> Unit,
    onSyncWallConnectors: () -> Unit = {},
    onSyncWorkCharging: () -> Unit = {},
    onSyncPublicCharging: () -> Unit = {}
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
                        text = "Add charging",
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
                        contentDescription = "More upload and sync options",
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
                text = { Text("Sync Wall Connectors") },
                leadingIcon = { Icon(Icons.Rounded.EvStation, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onSyncWallConnectors()
                }
            )
            DropdownMenuItem(
                text = { Text("Sync work charge") },
                leadingIcon = { Icon(Icons.Rounded.Work, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onSyncWorkCharging()
                }
            )
            DropdownMenuItem(
                text = { Text("Sync Superchargers & DC") },
                leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onSyncPublicCharging()
                }
            )
            DropdownMenuItem(
                text = { Text("Add electric bill") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onOpenManualBillSheet()
                }
            )
            DropdownMenuItem(
                text = { Text("Import file") },
                leadingIcon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onUploadChargerUsage()
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
