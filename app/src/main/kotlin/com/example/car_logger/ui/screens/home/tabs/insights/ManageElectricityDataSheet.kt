package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricBolt
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

private enum class HistoryTab(val label: String) {
    BILLS("Electric Bills"),
    SESSIONS("Charging Logs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageElectricityDataSheet(
    utilityBills: List<UtilityBillEntity>,
    chargingSessions: List<ChargingSessionEntity>,
    cars: List<Car>,
    currentDefaultRate: Float,
    onUpdateDefaultRate: (Float) -> Unit,
    onDeleteBill: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onUndoLastChargerUpload: () -> Unit = {},
    onUndoAndRetagLastChargerUpload: () -> Unit = {},
    onOpenAddBill: () -> Unit,
    onOpenAddCharger: () -> Unit,
    onDismiss: () -> Unit,
    initialShowSessionsTab: Boolean = false,
    useCustomRate: Boolean = false,
    onUseSavedBillRate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topClearance = maxOf(statusBarTop + 8.dp, 72.dp)
    var selectedTab by remember(initialShowSessionsTab) {
        mutableStateOf(
            if (initialShowSessionsTab || (utilityBills.isEmpty() && chargingSessions.isNotEmpty())) {
                HistoryTab.SESSIONS
            } else {
                HistoryTab.BILLS
            }
        )
    }
    var defaultRateText by remember(currentDefaultRate) {
        mutableStateOf(String.format(Locale.US, "%.3f", currentDefaultRate))
    }
    var rateSavedFeedback by remember { mutableStateOf(false) }

    val carNameById = remember(cars) {
        cars.associate { it.id to it.model.ifBlank { it.displayName } }
    }
    val lastUploadBatch = remember(chargingSessions) {
        ElectricityImportParser.resolveLastUploadBatch(chargingSessions)
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
            Text(
                text = "Charging & Bills History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Custom / fallback electric rate editor
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (useCustomRate) "Custom Electric Rate (Active)" else "Home Electric Rate",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            useCustomRate -> "Overrides saved electric bills as your source of truth. Saving a new bill updates this rate automatically."
                            utilityBills.isNotEmpty() -> "Used when no bill matches a date. Applying a rate here switches to your custom rate."
                            else -> "Used automatically for home charging when no electric bill is uploaded yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = defaultRateText,
                            onValueChange = {
                                defaultRateText = it
                                rateSavedFeedback = false
                            },
                            label = { Text("Rate ($/kWh)") },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val parsed = defaultRateText.toFloatOrNull()
                                if (parsed != null && parsed >= 0f) {
                                    onUpdateDefaultRate(parsed)
                                    rateSavedFeedback = true
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (rateSavedFeedback) "Saved" else "Apply")
                        }
                    }
                    if (useCustomRate && utilityBills.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                rateSavedFeedback = false
                                onUseSavedBillRate()
                            }
                        ) {
                            Text("Use saved electric bill rate instead")
                        }
                    }
                }
            }

            // Connected Button Group: Electric Bills | Charging Logs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryTab.entries.forEachIndexed { index, tab ->
                    val selected = tab == selectedTab
                    val isFirst = index == 0
                    val inner by animateDpAsState(
                        targetValue = if (selected) 20.dp else 6.dp,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                        label = "hist_r_$index"
                    )
                    val shape = if (isFirst) {
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = inner, bottomEnd = inner)
                    } else {
                        RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = 20.dp, bottomEnd = 20.dp)
                    }
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = ExpressiveMotionTokens.ColorSpring,
                        label = "hist_bg_$index"
                    )
                    val fg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = ExpressiveMotionTokens.ColorSpring,
                        label = "hist_fg_$index"
                    )

                    val count = if (tab == HistoryTab.BILLS) utilityBills.size else chargingSessions.size
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .expressiveBounceClick { selectedTab = tab },
                        shape = shape,
                        color = bg,
                        contentColor = fg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (tab == HistoryTab.BILLS) {
                                    Icons.AutoMirrored.Rounded.ReceiptLong
                                } else {
                                    Icons.Rounded.ElectricBolt
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${tab.label} ($count)",
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

            if (selectedTab == HistoryTab.BILLS) {
                if (utilityBills.isEmpty()) {
                    EmptyHistoryBox(
                        title = "No electric bills saved",
                        subtitle = "Upload a utility PDF/photo or enter your bill totals so we can calculate your true electric rate.",
                        buttonLabel = "Add Electric Bill",
                        onClick = onOpenAddBill
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        utilityBills.forEach { bill ->
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
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "${bill.providerName.ifBlank { "Electric Bill" }} · ${ElectricityImportParser.formatPeriodShort(bill.startDateMs, bill.endDateMs)}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$${String.format(Locale.US, "%.3f", bill.effectiveRate)}/kWh · ${String.format(Locale.US, "%.0f", bill.totalKwh)} kWh · $${String.format(Locale.US, "%.2f", bill.totalAmount)}" +
                                                if (bill.overrideRate != null) " (Custom)" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBill(bill.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete electric bill",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenAddBill,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .expressiveBounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Electric Bill")
                        }
                    }
                }
            } else {
                if (chargingSessions.isEmpty()) {
                    EmptyHistoryBox(
                        title = "No charging logs saved",
                        subtitle = "Upload a charger CSV/PDF/screenshot or enter a session or monthly kWh total.",
                        buttonLabel = "Add Charger Usage",
                        onClick = onOpenAddCharger
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lastUploadBatch.isNotEmpty()) {
                            val batchKwh = lastUploadBatch.sumOf { it.kwh }
                            val batchVehicleLabel = lastUploadBatch.first().carId?.let { carNameById[it] } ?: "Both EVs"
                            val minStart = lastUploadBatch.minOf { it.startDateMs }
                            val maxEnd = lastUploadBatch.maxOf { it.endDateMs }
                            val batchDateLabel = ElectricityImportParser.formatPeriodShort(minStart, maxEnd)

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Last Upload · ${lastUploadBatch.size} ${if (lastUploadBatch.size == 1) "log" else "logs"} (${String.format(Locale.US, "%.1f", batchKwh)} kWh)",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "Tagged to $batchVehicleLabel · $batchDateLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onUndoLastChargerUpload,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .expressiveBounceClick()
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Undo Upload",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        FilledTonalButton(
                                            onClick = onUndoAndRetagLastChargerUpload,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .expressiveBounceClick()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DirectionsCar,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Undo & Re-tag",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        chargingSessions.forEach { session ->
                            val isPublic = session.venue.equals("PUBLIC", ignoreCase = true)
                            val vehicleLabel = session.carId?.let { carNameById[it] } ?: "Both EVs"
                            val dateLabel = ElectricityImportParser.formatPeriodShort(session.startDateMs, session.endDateMs)
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
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(dateLabel)
                                                append(" · ")
                                                append(if (isPublic) "Public" else "Home")
                                                if (session.isSummary) append(" · Total")
                                            },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = buildString {
                                                append("${String.format(Locale.US, "%.1f", session.kwh)} kWh")
                                                if (isPublic && session.directCost != null) {
                                                    append(" · $${String.format(Locale.US, "%.2f", session.directCost)}")
                                                }
                                                append(" · $vehicleLabel")
                                                if (!session.chargerName.isNullOrBlank()) {
                                                    append(" · ${session.chargerName}")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteSession(session.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete charging log",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.expressiveBounceClick()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryBox(
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClick) {
                Text(buttonLabel)
            }
        }
    }
}
