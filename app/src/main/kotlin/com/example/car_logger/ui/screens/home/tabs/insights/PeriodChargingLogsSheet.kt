package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    onUpdateSessionCost: (String, Double) -> Unit = { _, _ -> },
    onOpenAddCharger: () -> Unit,
    onOpenAllHistory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val topClearance = maxOf(topInset + 8.dp, 72.dp)
    val isDark = isSystemInDarkTheme()
    val workContainerColor = if (isDark) Color(0xFF0F4020) else Color(0xFFD3F5DC)
    val workAccentColor = if (isDark) Color(0xFF81E6A1) else Color(0xFF0B5F2A)

    val isHome = venue == ChargingVenue.HOME
    val isWork = venue == ChargingVenue.WORK
    val isPublic = venue == ChargingVenue.PUBLIC

    val logs = when (venue) {
        ChargingVenue.HOME -> data.homePeriodLogs
        ChargingVenue.WORK -> data.workPeriodLogs
        ChargingVenue.PUBLIC -> data.publicPeriodLogs
    }
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
    val effectiveRate = when (venue) {
        ChargingVenue.HOME -> data.homeEffectiveRate
        ChargingVenue.WORK -> 0.0
        ChargingVenue.PUBLIC -> data.publicEffectiveRate
    }
    val rateSourceLabel = when (venue) {
        ChargingVenue.HOME -> data.homeRateSourceLabel
        ChargingVenue.WORK -> "Free workplace"
        ChargingVenue.PUBLIC -> data.publicRateSourceLabel
    }

    var sessionPendingCostEdit by remember { mutableStateOf<ChargingSessionEntity?>(null) }

    val carNameById = remember(cars) {
        cars.associate { it.id to it.model.ifBlank { it.displayName } }
    }
    val selectedVehicleLabel = remember(selectedCarId, carNameById) {
        selectedCarId?.let { carNameById[it] } ?: "All vehicles"
    }

    val bannerBg = when (venue) {
        ChargingVenue.HOME -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ChargingVenue.WORK -> workContainerColor.copy(alpha = 0.72f)
        ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    }
    val bannerAccent = when (venue) {
        ChargingVenue.HOME -> MaterialTheme.colorScheme.primary
        ChargingVenue.WORK -> workAccentColor
        ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiary
    }

    sessionPendingCostEdit?.let { target ->
        var costInput by remember(target.id) {
            mutableStateOf(target.directCost?.let { String.format(Locale.US, "%.2f", it) }.orEmpty())
        }
        var inputError by remember(target.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { sessionPendingCostEdit = null },
            title = { Text("Set charging cost") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${formatSheetKwh(target.kwh)} kWh · ${target.chargerName ?: "Public charger"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = costInput,
                        onValueChange = {
                            costInput = it
                            inputError = null
                        },
                        label = { Text("Total paid ($)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = costInput.trim().toDoubleOrNull()
                        if (parsed == null || parsed < 0.0) {
                            inputError = "Enter a valid amount ($0.00 or higher)."
                        } else {
                            onUpdateSessionCost(target.id, parsed)
                            sessionPendingCostEdit = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingCostEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
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
                        imageVector = when (venue) {
                            ChargingVenue.HOME -> Icons.Rounded.Home
                            ChargingVenue.WORK -> Icons.Rounded.Work
                            ChargingVenue.PUBLIC -> Icons.Rounded.EvStation
                        },
                        contentDescription = null,
                        tint = bannerAccent,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = venue.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedVehicleLabel,
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
                        val countLabel = if (sessionCount == 1) "1 session" else "$sessionCount sessions"
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
                        if (isWork) {
                            Text(
                                text = "~$${String.format(Locale.US, "%.2f", data.workAvoidedHomeCost)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = bannerAccent
                            )
                            Text(
                                text = "saved vs. home",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
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
            }

            // Filtered Charging Sessions List or Empty State
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
                            text = "No ${venue.shortLabel.lowercase(Locale.US)} charging in $periodText",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedCarId != null) {
                                "Try another period, switch vehicles, or view all history."
                            } else {
                                "Try another period, add a session, or view all history."
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
                            Text("Add charging")
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    logs.forEach { item ->
                        PeriodChargingLogRow(
                            item = item,
                            venue = venue,
                            workAccentColor = workAccentColor,
                            carNameById = carNameById,
                            onEditCost = if (isPublic && item.session.directCost == null) {
                                { sessionPendingCostEdit = item.session }
                            } else {
                                null
                            },
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
                        text = "All history",
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
    venue: ChargingVenue,
    workAccentColor: Color,
    carNameById: Map<String, String>,
    onEditCost: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val session = item.session
    val isHome = venue == ChargingVenue.HOME
    val isWork = venue == ChargingVenue.WORK
    val dateLabel = remember(session.startDateMs, session.endDateMs, session.isSummary) {
        formatLogTimestamp(session)
    }
    val vehicleLabel = when {
        session.carId == null && item.isSharedSplit -> "All vehicles (split)"
        session.carId == null -> "All vehicles"
        else -> carNameById[session.carId] ?: "Vehicle"
    }
    val sourceTag = when {
        !session.chargerName.isNullOrBlank() -> session.chargerName
        session.uploadBatchId == ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID -> "Wall Connector"
        session.uploadBatchId == ElectricityImportParser.AUTO_WORK_CHARGING_BATCH_ID -> "Tesla Work Auto"
        session.uploadBatchId == ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID -> "Tesla Supercharger"
        session.uploadBatchId == ElectricityImportParser.AUTO_SMARTCAR_CHARGING_BATCH_ID -> "Smartcar DC"
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
                        if (isWork) {
                            append(" · Free")
                            if (item.avoidedHomeCost > 0.0) {
                                append(" · ~$${String.format(Locale.US, "%.2f", item.avoidedHomeCost)} saved")
                            }
                        } else if (venue == ChargingVenue.PUBLIC && session.directCost == null) {
                            append(" · Cost pending")
                        } else {
                            append(" · $${String.format(Locale.US, "%.2f", item.attributedCost)}")
                            if (isHome && item.isEstimatedRate) {
                                append(" est.")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isWork) workAccentColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildString {
                        append(vehicleLabel)
                        if (!isWork && item.effectiveRate > 0.0) {
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

            if (onEditCost != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .expressiveBounceClick { onEditCost() }
                ) {
                    Text(
                        text = "Set cost",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete charging session",
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
