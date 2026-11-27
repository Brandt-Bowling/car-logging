package com.example.car_logger.ui.screens.vehicledetails.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun VehicleInsightsBentoGrid(
    state: VehicleInsightsBentoState,
    granularity: InsightsTimeGranularity,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTile by rememberSaveable { mutableStateOf<VehicleBentoTile?>(null) }
    var inspectedBarIndex by remember(granularity) { mutableStateOf<Int?>(null) }
    val periodMetrics = remember(state, granularity) { state.metricsFor(granularity) }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val savingsBg = ExpressiveThemeTokens.savingsContainer
    val savingsAccent = ExpressiveThemeTokens.savingsAccent
    val savingsText = ExpressiveThemeTokens.savingsText

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Driving Pace & EV Efficiency
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrivingPaceBentoTile(
                metrics = periodMetrics,
                isExpanded = expandedTile == VehicleBentoTile.PACE,
                numberFormat = numberFormat,
                onClick = {
                    expandedTile = if (expandedTile == VehicleBentoTile.PACE) null else VehicleBentoTile.PACE
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            EvEfficiencyBentoTile(
                metrics = periodMetrics,
                isExpanded = expandedTile == VehicleBentoTile.EFFICIENCY,
                onClick = {
                    expandedTile = if (expandedTile == VehicleBentoTile.EFFICIENCY) null else VehicleBentoTile.EFFICIENCY
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Row 2: Saved vs Gas & True Cost/Mi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SavedVsGasBentoTile(
                metrics = periodMetrics,
                isExpanded = expandedTile == VehicleBentoTile.SAVINGS,
                savingsBg = savingsBg,
                savingsAccent = savingsAccent,
                savingsText = savingsText,
                numberFormat = numberFormat,
                onClick = {
                    expandedTile = if (expandedTile == VehicleBentoTile.SAVINGS) null else VehicleBentoTile.SAVINGS
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            TrueCostPerMileBentoTile(
                state = state,
                isExpanded = expandedTile == VehicleBentoTile.TCO,
                onClick = {
                    expandedTile = if (expandedTile == VehicleBentoTile.TCO) null else VehicleBentoTile.TCO
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Shared Docked Drawer below the 2x2 Grid (Variation C1)
        AnimatedVisibility(
            visible = expandedTile != null,
            enter = fadeIn(animationSpec = ExpressiveMotionTokens.SnappySpring) +
                expandVertically(animationSpec = ExpressiveMotionTokens.SpatialSizeSpring),
            exit = fadeOut(animationSpec = ExpressiveMotionTokens.SnappySpring) +
                shrinkVertically(animationSpec = ExpressiveMotionTokens.SpatialSizeSpring)
        ) {
            val activeTile = expandedTile
            if (activeTile != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = activeTile,
                        transitionSpec = {
                            fadeIn(ExpressiveMotionTokens.SnappySpring) togetherWith
                                fadeOut(ExpressiveMotionTokens.SnappySpring)
                        },
                        label = "bento_docked_drawer_content"
                    ) { tile ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (tile) {
                                VehicleBentoTile.PACE -> {
                                    DrivingPaceDrawerContent(
                                        metrics = periodMetrics,
                                        granularity = granularity,
                                        inspectedBarIndex = inspectedBarIndex,
                                        onInspectBar = { idx ->
                                            inspectedBarIndex = if (inspectedBarIndex == idx) null else idx
                                        },
                                        onSelectGranularity = {
                                            inspectedBarIndex = null
                                            onSelectGranularity(it)
                                        },
                                        numberFormat = numberFormat
                                    )
                                }
                                VehicleBentoTile.EFFICIENCY -> {
                                    EvEfficiencyDrawerContent(
                                        metrics = periodMetrics,
                                        granularity = granularity,
                                        workAccent = savingsAccent,
                                        onSelectGranularity = onSelectGranularity
                                    )
                                }
                                VehicleBentoTile.SAVINGS -> {
                                    SavedVsGasDrawerContent(
                                        metrics = periodMetrics,
                                        granularity = granularity,
                                        savingsBg = savingsBg,
                                        savingsAccent = savingsAccent,
                                        savingsText = savingsText,
                                        numberFormat = numberFormat,
                                        onSelectGranularity = onSelectGranularity
                                    )
                                }
                                VehicleBentoTile.TCO -> {
                                    TrueCostPerMileDrawerContent(
                                        state = state,
                                        numberFormat = numberFormat
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrivingPaceBentoTile(
    metrics: VehicleBentoPeriodMetrics,
    isExpanded: Boolean,
    numberFormat: NumberFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "pace_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "pace_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
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
    val peakIdx = remember(sparkBars) {
        sparkBars.indices.maxByOrNull { sparkBars[it] } ?: (sparkBars.size - 1)
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
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DRIVING PACE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = fg.copy(alpha = 0.78f),
                    maxLines = 1
                )

                Surface(
                    shape = CircleShape,
                    color = fg.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${metrics.fleetSharePct}% share",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
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
                            text = if (metrics.hasOdometerData) {
                                String.format(Locale.US, "%.1f", metrics.dailyRate)
                            } else {
                                "—"
                            },
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
                        text = if (metrics.hasOdometerData) {
                            "${numberFormat.format(metrics.totalMiles)} mi (${metrics.periodLabel})"
                        } else {
                            "Sync odometer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Mini 5-capsule sparkline on tile face
                Row(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    sparkBars.forEachIndexed { idx, ratio ->
                        val isPeak = idx == peakIdx
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight(ratio.coerceIn(0.22f, 1f))
                                .clip(CircleShape)
                                .background(
                                    if (isPeak) MaterialTheme.colorScheme.primary
                                    else fg.copy(alpha = 0.32f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvEfficiencyBentoTile(
    metrics: VehicleBentoPeriodMetrics,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "eff_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "eff_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
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
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EV EFFICIENCY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = fg.copy(alpha = 0.78f),
                    maxLines = 1
                )

                Text(
                    text = "⚡ ${String.format(Locale.US, "%.1f", metrics.totalKwh)} kWh",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
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
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
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
private fun SavedVsGasBentoTile(
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
        targetValue = if (isExpanded) {
            savingsBg
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "savings_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) {
            savingsText
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "savings_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) {
            savingsAccent
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
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
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVED VS GAS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = savingsAccent,
                    maxLines = 1
                )

                Text(
                    text = "${String.format(Locale.US, "%.1f", metrics.gasGallonsSaved)} gal",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
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
private fun TrueCostPerMileBentoTile(
    state: VehicleInsightsBentoState,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "tco_tile_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ExpressiveMotionTokens.ColorSpring,
        label = "tco_tile_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
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
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRUE COST/MI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = fg.copy(alpha = 0.78f),
                    maxLines = 1
                )

                Text(
                    text = "All-In",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = fg.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.lifetimeAllInCostPerMile?.let {
                            "$${String.format(Locale.US, "%.3f", it)}"
                        } ?: "—",
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
private fun InDrawerPeriodConnectedToggle(
    granularity: InsightsTimeGranularity,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit
) {
    val isWeekly = granularity == InsightsTimeGranularity.WEEKLY
    val leftRightRadius by animateDpAsState(
        targetValue = if (isWeekly) 16.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "drawer_period_left_r"
    )
    val rightLeftRadius by animateDpAsState(
        targetValue = if (!isWeekly) 16.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "drawer_period_right_r"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp,
                    topEnd = leftRightRadius,
                    bottomEnd = leftRightRadius
                ),
                color = if (isWeekly) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isWeekly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            bottomStart = 16.dp,
                            topEnd = leftRightRadius,
                            bottomEnd = leftRightRadius
                        )
                    )
                    .clickable { onSelectGranularity(InsightsTimeGranularity.WEEKLY) }
            ) {
                Text(
                    text = "Weekly (7D)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = rightLeftRadius,
                    bottomStart = rightLeftRadius,
                    topEnd = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = if (!isWeekly) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (!isWeekly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = rightLeftRadius,
                            bottomStart = rightLeftRadius,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .clickable { onSelectGranularity(InsightsTimeGranularity.MONTHLY) }
            ) {
                Text(
                    text = "Monthly (30D)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun DrivingPaceDrawerContent(
    metrics: VehicleBentoPeriodMetrics,
    granularity: InsightsTimeGranularity,
    inspectedBarIndex: Int?,
    onInspectBar: (Int) -> Unit,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    numberFormat: NumberFormat
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Driving Activity (${metrics.windowText})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${numberFormat.format(metrics.totalMiles)} mi driven · ${metrics.fleetSharePct}% of garage total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
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
        return
    }

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

    val maxMiles = remember(bars) {
        bars.maxOfOrNull { it.car1Miles }?.coerceAtLeast(1) ?: 1
    }
    val barWidth = if (bars.size <= 5) 26.dp else 20.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { index, bar ->
            val isSelected = index == activeIdx
            val targetFill = if (bar.car1Miles > 0) {
                (bar.car1Miles.toFloat() / (maxMiles * 1.12f)).coerceIn(0.14f, 1f)
            } else {
                0f
            }
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

@Composable
private fun EvEfficiencyDrawerContent(
    metrics: VehicleBentoPeriodMetrics,
    granularity: InsightsTimeGranularity,
    workAccent: Color,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Charging & Efficiency (${metrics.windowText})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val cpmText = metrics.energyCostPerMile?.let {
                " · $${String.format(Locale.US, "%.3f", it)}/mi energy cost"
            } ?: ""
            Text(
                text = "${String.format(Locale.US, "%.1f", metrics.totalKwh)} kWh total$cpmText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        InDrawerPeriodConnectedToggle(
            granularity = granularity,
            onSelectGranularity = onSelectGranularity
        )
    }

    if (metrics.totalKwh > 0.0) {
        val activeSegments = listOfNotNull(
            if (metrics.homeKwh > 0.0) Pair((metrics.homeKwh / metrics.totalKwh).toFloat().coerceAtLeast(0.08f), MaterialTheme.colorScheme.primary) else null,
            if (metrics.workKwh > 0.0) Pair((metrics.workKwh / metrics.totalKwh).toFloat().coerceAtLeast(0.08f), workAccent) else null,
            if (metrics.publicKwh > 0.0) Pair((metrics.publicKwh / metrics.totalKwh).toFloat().coerceAtLeast(0.08f), MaterialTheme.colorScheme.tertiary) else null
        )
        Row(
            modifier = Modifier
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HOME (${metrics.homePct}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%.3f", metrics.homeEffectiveRate)}/kWh",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format(Locale.US, "%.2f", metrics.homeCost)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", metrics.homeKwh)} kWh attributed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PUBLIC (${metrics.publicPct}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (metrics.publicEffectiveRate > 0.0) {
                        Text(
                            text = "$${String.format(Locale.US, "%.3f", metrics.publicEffectiveRate)}/kWh",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format(Locale.US, "%.2f", metrics.publicCost)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", metrics.publicKwh)} kWh fast charge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (metrics.workKwh > 0.0) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
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
}

@Composable
private fun SavedVsGasDrawerContent(
    metrics: VehicleBentoPeriodMetrics,
    granularity: InsightsTimeGranularity,
    savingsBg: Color,
    savingsAccent: Color,
    savingsText: Color,
    numberFormat: NumberFormat,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Gas Comparison (${metrics.windowText})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val mpgStr = if (metrics.baselineMpg == metrics.baselineMpg.toInt().toFloat()) {
                "${metrics.baselineMpg.toInt()} MPG"
            } else {
                "${String.format(Locale.US, "%.1f", metrics.baselineMpg)} MPG"
            }
            Text(
                text = "vs. $mpgStr baseline @ $${String.format(Locale.US, "%.2f", metrics.gasPrice)}/gal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        InDrawerPeriodConnectedToggle(
            granularity = granularity,
            onSelectGranularity = onSelectGranularity
        )
    }

    val netVal = metrics.netFuelSavings ?: (metrics.gasEquivalentCost - metrics.totalElectricityCost)
    val isPositive = netVal >= 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "GAS EQUIV",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$${String.format(Locale.US, "%.2f", metrics.gasEquivalentCost)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberUrgent
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", metrics.gasGallonsSaved)} gal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "EV CHARGING",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "-$${String.format(Locale.US, "%.2f", metrics.totalElectricityCost)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", metrics.totalKwh)} kWh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isPositive) savingsBg else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = if (isPositive) "NET SAVED" else "NET DIFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) savingsAccent else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isPositive) {
                        "+$${String.format(Locale.US, "%.2f", netVal)}"
                    } else {
                        "-$${String.format(Locale.US, "%.2f", abs(netVal))}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPositive) savingsText else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "~${numberFormat.format(metrics.co2AvoidedLbs)} lbs CO₂",
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isPositive) savingsText else MaterialTheme.colorScheme.onErrorContainer).copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun TrueCostPerMileDrawerContent(
    state: VehicleInsightsBentoState,
    numberFormat: NumberFormat
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Lifetime All-In Operating Cost",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val milesSubtitle = if (state.trackedMiles > 0) {
                "Maintenance + EV Charging across ${numberFormat.format(state.trackedMiles)} mi"
            } else {
                "Combined Maintenance & EV Charging totals"
            }
            Text(
                text = milesSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
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
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f)
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
                        text = "MAINT (${state.maintenanceSharePct}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", state.lifetimeMaintenanceCost)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = state.lifetimeMaintenanceCostPerMile?.let {
                        "$${String.format(Locale.US, "%.3f", it)}/mi"
                    } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f)
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
                        text = "CHARGING (${state.chargingSharePct}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", state.lifetimeChargingCost)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = state.lifetimeChargingCostPerMile?.let {
                        "$${String.format(Locale.US, "%.3f", it)}/mi"
                    } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
