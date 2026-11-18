package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.ElectricityCostData
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.insights.PeriodChargingLogItem
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodChargingLogsSheet(
    venue: ChargingVenue,
    data: ElectricityCostData,
    granularity: InsightsTimeGranularity,
    periodText: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    selectedCarId: String?,
    cars: List<Car>,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenAddCharger: () -> Unit,
    onOpenAllHistory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topClearance = maxOf(statusBarTop + 8.dp, 72.dp)
    val isHome = venue == ChargingVenue.HOME
    val logs = if (isHome) data.homePeriodLogs else data.publicPeriodLogs
    val totalCost = if (isHome) data.homeTotalCost else data.publicTotalCost
    val totalKwh = if (isHome) data.homeTotalKwh else data.publicTotalKwh
    val sessionCount = if (isHome) data.homeSessionCount else data.publicSessionCount
    val effectiveRate = if (isHome) data.homeEffectiveRate else data.publicEffectiveRate
    val rateSourceLabel = if (isHome) data.homeRateSourceLabel else data.publicRateSourceLabel

    val carNameById = remember(cars) {
        cars.associate { it.id to it.model.ifBlank { it.displayName } }
    }
    val selectedVehicleLabel = remember(selectedCarId, carNameById) {
        selectedCarId?.let { carNameById[it] } ?: "All vehicles"
    }

    val bannerBg = if (isHome) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    }
    val bannerAccent = if (isHome) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.padding(top = topClearance)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bannerBg
                ) {
                    Icon(
                        imageVector = if (isHome) Icons.Rounded.Home else Icons.Rounded.EvStation,
                        contentDescription = null,
                        tint = bannerAccent,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHome) "Home Charging Logs" else "Public Charging Logs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Filtered for $selectedVehicleLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Inline Period Switcher
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousPeriod,
                        enabled = canGoPrevious
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous period"
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = "${granularity.label} · $periodText",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = onNextPeriod,
                        enabled = canGoNext
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Next period"
                        )
                    }
                }
            }

            // Period Summary Banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bannerBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "PERIOD TOTAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = bannerAccent
                        )
                        Text(
                            text = String.format(Locale.US, "$%.2f", totalCost),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val countLabel = if (sessionCount == 1) "1 log" else "$sessionCount logs"
                        Text(
                            text = "${formatSheetKwh(totalKwh)} kWh · $countLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (effectiveRate > 0.0) {
                                String.format(Locale.US, "$%.3f / kWh", effectiveRate) +
                                    if (isHome && data.homeIsEstimatedRate) " (est.)" else ""
                            } else {
                                "— / kWh"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = rateSourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Filtered Charging Logs List or Empty State
            if (logs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No ${if (isHome) "home" else "public"} charging logs in $periodText",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedCarId != null) {
                                "Try switching the period above, selecting Both vehicles, or viewing all charging history."
                            } else {
                                "Try switching the period above, logging usage, or viewing all charging history."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onOpenAddCharger,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.expressiveBounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Charger Usage")
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    logs.forEach { item ->
                        PeriodChargingLogRow(
                            item = item,
                            isHome = isHome,
                            carNameById = carNameById,
                            onDelete = { onDeleteSession(item.session.id) }
                        )
                    }
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenAllHistory,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .expressiveBounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "All History",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .expressiveBounceClick()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun PeriodChargingLogRow(
    item: PeriodChargingLogItem,
    isHome: Boolean,
    carNameById: Map<String, String>,
    onDelete: () -> Unit
) {
    val session = item.session
    val dateLabel = remember(session.startDateMs, session.endDateMs, session.isSummary) {
        formatLogTimestamp(session)
    }
    val vehicleLabel = when {
        session.carId == null && item.isSharedSplit -> "Both EVs (split)"
        session.carId == null -> "Both EVs"
        else -> carNameById[session.carId] ?: "Vehicle"
    }
    val sourceTag = when {
        !session.chargerName.isNullOrBlank() -> session.chargerName
        session.uploadBatchId == ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID -> "Wall Connector"
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = buildString {
                        append(dateLabel)
                        if (item.isProratedSummary) {
                            append(" · Prorated")
                        } else if (session.isSummary) {
                            append(" · Summary")
                        }
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildString {
                        append("${formatSheetKwh(item.attributedKwh)} kWh")
                        if (item.isSharedSplit || item.isProratedSummary) {
                            append(" (of ${formatSheetKwh(session.kwh)} kWh)")
                        }
                        append(" · $${String.format(Locale.US, "%.2f", item.attributedCost)}")
                        if (isHome && item.isEstimatedRate) {
                            append(" est.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildString {
                        append(vehicleLabel)
                        if (item.effectiveRate > 0.0) {
                            append(" · @$${String.format(Locale.US, "%.3f", item.effectiveRate)}/kWh")
                        }
                        if (!sourceTag.isNullOrBlank()) {
                            append(" · $sourceTag")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete charging log",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatLogTimestamp(
    session: ChargingSessionEntity,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    if (session.isSummary) {
        return ElectricityImportParser.formatPeriodShort(session.startDateMs, session.endDateMs, zoneId)
    }
    val startZdt = Instant.ofEpochMilli(session.startDateMs).atZone(zoneId)
    val endZdt = Instant.ofEpochMilli(session.endDateMs).atZone(zoneId)
    val sameDay = startZdt.toLocalDate() == endZdt.toLocalDate()
    val hasStartTime = startZdt.hour != 0 || startZdt.minute != 0
    if (sameDay && hasStartTime) {
        val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        val endIsMeaningful = session.endDateMs > session.startDateMs + 60_000L &&
            !(endZdt.hour == 23 && endZdt.minute == 59)
        return if (endIsMeaningful) {
            "${startZdt.format(dateFmt)} · ${startZdt.format(timeFmt)} – ${endZdt.format(timeFmt)}"
        } else {
            "${startZdt.format(dateFmt)} · ${startZdt.format(timeFmt)}"
        }
    }
    return ElectricityImportParser.formatPeriodShort(session.startDateMs, session.endDateMs, zoneId)
}

private fun formatSheetKwh(kwh: Double): String {
    return if (kwh >= 100.0) {
        String.format(Locale.US, "%.0f", kwh)
    } else {
        String.format(Locale.US, "%.1f", kwh)
    }
}
