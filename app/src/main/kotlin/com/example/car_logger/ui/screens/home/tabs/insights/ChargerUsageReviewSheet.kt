package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateListOf
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
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedChargingSessionDraft
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

private enum class ManualEntryMode(val label: String) {
    SINGLE_SESSION("Single Session"),
    MONTHLY_TOTAL("Monthly Total")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargerUsageReviewSheet(
    initialImport: ParsedChargerImportResult?,
    cars: List<Car>,
    existingFingerprints: Set<String>,
    onUploadFile: () -> Unit,
    onDismiss: () -> Unit,
    onSaveSessions: (sessions: List<ChargingSessionEntity>, skippedDuplicates: Int) -> Unit,
    initialSelectedCarId: String? = null,
    lastUploadBatch: List<ChargingSessionEntity> = emptyList(),
    onUndoLastUpload: () -> Unit = {},
    onUndoAndRetagLastUpload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeCars = remember(cars) { cars.filter { !it.isArchived }.ifEmpty { cars } }

    // Default vehicle assignment: active filter carId if set, or single car id, or Both EVs (null)
    var selectedCarId by remember(activeCars, initialSelectedCarId) {
        val validInitial = initialSelectedCarId?.takeIf { id -> activeCars.any { it.id == id } }
        mutableStateOf(validInitial ?: if (activeCars.size == 1) activeCars.first().id else null)
    }

    val isImportReviewMode = initialImport != null && initialImport.sessions.isNotEmpty()

    // Import review mutable venue overrides so user can flip any parsed row HOME <-> PUBLIC
    val importDrafts = remember(initialImport) {
        mutableStateListOf<ParsedChargingSessionDraft>().apply {
            if (initialImport != null) addAll(initialImport.sessions)
        }
    }

    // Manual entry fields
    var entryMode by remember { mutableStateOf(ManualEntryMode.SINGLE_SESSION) }
    var venue by remember { mutableStateOf("HOME") }
    val todayIso = remember { LocalDate.now().toString() }
    val monthStartIso = remember { YearMonth.now().atDay(1).toString() }
    val monthEndIso = remember { YearMonth.now().atEndOfMonth().toString() }

    var singleDateText by remember { mutableStateOf(todayIso) }
    var rangeStartText by remember { mutableStateOf(monthStartIso) }
    var rangeEndText by remember { mutableStateOf(monthEndIso) }
    var kwhText by remember { mutableStateOf("") }
    var directCostText by remember { mutableStateOf("") }
    var chargerNameText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Compute prepared entities & duplicate count live when in import review mode
    val importBuildResult = remember(importDrafts.toList(), selectedCarId, existingFingerprints, isImportReviewMode) {
        if (!isImportReviewMode) {
            Pair(emptyList<ChargingSessionEntity>(), 0)
        } else {
            buildEntitiesFromDrafts(
                drafts = importDrafts,
                carId = selectedCarId,
                existingFingerprints = existingFingerprints
            )
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topClearance = maxOf(statusBarTop + 8.dp, 72.dp)

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isImportReviewMode) "Review Charger Usage" else "Add Charger Usage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isImportReviewMode) {
                    "Loaded from ${initialImport?.fileName ?: "file"}. Pick which vehicle used this charger and confirm below."
                } else {
                    "Upload a charger CSV, PDF, or screenshot — or type a single session or monthly kWh total."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isImportReviewMode) {
                OutlinedButton(
                    onClick = onUploadFile,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import CSV, PDF, or Screenshot")
                }

                if (lastUploadBatch.isNotEmpty()) {
                    val carNameById = remember(activeCars) {
                        activeCars.associate { it.id to it.model.ifBlank { it.displayName } }
                    }
                    val lastVehicleLabel = lastUploadBatch.first().carId?.let { carNameById[it] } ?: "Both EVs"
                    val lastKwh = lastUploadBatch.sumOf { it.kwh }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Last upload: ${lastUploadBatch.size} ${if (lastUploadBatch.size == 1) "log" else "logs"} (${String.format(Locale.US, "%.1f", lastKwh)} kWh · $lastVehicleLabel)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onUndoLastUpload,
                                    shape = RoundedCornerShape(12.dp),
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
                                    Text("Undo Upload", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                FilledTonalButton(
                                    onClick = onUndoAndRetagLastUpload,
                                    shape = RoundedCornerShape(12.dp),
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
                                    Text("Undo & Re-tag", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            } else if (initialImport?.source == "gemini") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tap any row's Home/Public tag if a location needs adjusting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (initialImport?.error != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = initialImport.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Vehicle Assignment Connected Button Group
            if (activeCars.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Which vehicle charged?",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    VehicleAssignmentGroup(
                        cars = activeCars,
                        selectedCarId = selectedCarId,
                        onSelectCarId = { selectedCarId = it }
                    )
                    if (selectedCarId == null && activeCars.size > 1) {
                        Text(
                            text = "Shared across your EVs proportionally by miles driven.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isImportReviewMode) {
                val (newEntities, skippedCount) = importBuildResult
                val totalImportKwh = newEntities.sumOf { it.kwh }

                // Summary & Duplicate Detection Pill
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (newEntities.size == 1) {
                                "1 new entry ready (${String.format(Locale.US, "%.1f", totalImportKwh)} kWh)"
                            } else {
                                "${newEntities.size} new entries ready (${String.format(Locale.US, "%.1f", totalImportKwh)} kWh total)"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (skippedCount > 0) {
                            Text(
                                text = "$skippedCount duplicate ${if (skippedCount == 1) "entry" else "entries"} already logged — will be skipped automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberUrgent
                            )
                        }
                    }
                }

                // Preview of parsed rows (up to 15 shown cleanly)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    importDrafts.take(15).forEachIndexed { idx, row ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val dateDisplay = if (row.startDateIso == row.endDateIso) {
                                        row.startDateIso
                                    } else {
                                        "${row.startDateIso} – ${row.endDateIso}"
                                    }
                                    Text(
                                        text = buildString {
                                            append(dateDisplay)
                                            if (row.isSummary) append(" · Total")
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val subtitle = buildString {
                                        append("${String.format(Locale.US, "%.1f", row.kwh)} kWh")
                                        if (row.venue == "PUBLIC" && row.directCost != null) {
                                            append(" · $${String.format(Locale.US, "%.2f", row.directCost)}")
                                        }
                                        if (!row.chargerName.isNullOrBlank()) {
                                            append(" · ${row.chargerName}")
                                        }
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val isHome = row.venue != "PUBLIC"
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isHome) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    modifier = Modifier.expressiveBounceClick {
                                        val toggled = if (isHome) "PUBLIC" else "HOME"
                                        importDrafts[idx] = row.copy(venue = toggled)
                                    }
                                ) {
                                    Text(
                                        text = if (isHome) "Home" else "Public",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isHome) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (importDrafts.size > 15) {
                        Text(
                            text = "+${importDrafts.size - 15} more entries included in this upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Manual Entry Mode
                ManualModeConnectedGroup(
                    selectedMode = entryMode,
                    onSelectMode = {
                        entryMode = it
                        errorMessage = null
                    }
                )

                // Home vs Public FilterChips (14.dp radius)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = venue == "HOME",
                        onClick = { venue = "HOME" },
                        label = { Text("Home Charger") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (venue == "HOME") Icons.Rounded.Check else Icons.Rounded.Home,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = venue == "PUBLIC",
                        onClick = { venue = "PUBLIC" },
                        label = { Text("Public Charger") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (venue == "PUBLIC") Icons.Rounded.Check else Icons.Rounded.EvStation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (entryMode == ManualEntryMode.SINGLE_SESSION) {
                    OutlinedTextField(
                        value = singleDateText,
                        onValueChange = {
                            singleDateText = it
                            errorMessage = null
                        },
                        label = { Text("Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = rangeStartText,
                            onValueChange = {
                                rangeStartText = it
                                errorMessage = null
                            },
                            label = { Text("Period Start") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rangeEndText,
                            onValueChange = {
                                rangeEndText = it
                                errorMessage = null
                            },
                            label = { Text("Period End") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Monthly totals are spread evenly across these days so weekly and monthly views stay accurate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = kwhText,
                        onValueChange = {
                            kwhText = it
                            errorMessage = null
                        },
                        label = { Text("Energy Added (kWh)") },
                        suffix = { Text("kWh") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )

                    if (venue == "PUBLIC") {
                        OutlinedTextField(
                            value = directCostText,
                            onValueChange = {
                                directCostText = it
                                errorMessage = null
                            },
                            label = { Text("Total Paid ($)") },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                AnimatedVisibility(visible = venue == "HOME") {
                    Text(
                        text = "Home cost automatically uses your electric bill rate (or $0.16/kWh fallback until a bill is uploaded).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = chargerNameText,
                    onValueChange = { chargerNameText = it },
                    label = { Text(if (venue == "HOME") "Charger Name (optional)" else "Station Name (optional)") },
                    placeholder = { Text(if (venue == "HOME") "e.g., Garage Wall Connector" else "e.g., Supercharger") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (isImportReviewMode) {
                            val (newEntities, skippedCount) = importBuildResult
                            onSaveSessions(newEntities, skippedCount)
                        } else {
                            val isSummary = entryMode == ManualEntryMode.MONTHLY_TOTAL
                            val sMs = ElectricityImportParser.parseDateToStartOfDayMs(
                                if (isSummary) rangeStartText else singleDateText
                            )
                            val eMs = ElectricityImportParser.parseDateToEndOfDayMs(
                                if (isSummary) rangeEndText else singleDateText
                            )
                            val kwh = kwhText.toDoubleOrNull()
                            val parsedCost = if (venue == "PUBLIC") directCostText.toDoubleOrNull() else null

                            when {
                                sMs == null || eMs == null -> errorMessage = "Enter valid dates (YYYY-MM-DD)."
                                eMs < sMs -> errorMessage = "End date cannot be before start date."
                                kwh == null || kwh <= 0.0 -> errorMessage = "Enter energy greater than 0 kWh."
                                venue == "PUBLIC" && (parsedCost == null || parsedCost < 0.0) ->
                                    errorMessage = "Enter total paid ($) for public charging (0.00 if free)."
                                else -> {
                                    val fingerprint = ElectricityImportParser.buildFingerprint(
                                        startDateMs = sMs,
                                        endDateMs = eMs,
                                        kwh = kwh,
                                        venue = venue,
                                        carId = selectedCarId
                                    )
                                    if (existingFingerprints.contains(fingerprint)) {
                                        errorMessage = "This charging entry is already saved."
                                    } else {
                                        val entity = ChargingSessionEntity(
                                            id = "chg_${UUID.randomUUID()}",
                                            carId = selectedCarId,
                                            startDateMs = sMs,
                                            endDateMs = eMs,
                                            kwh = kwh,
                                            venue = venue,
                                            directCost = parsedCost,
                                            chargerName = chargerNameText.trim().ifBlank {
                                                if (venue == "HOME") "Home Charger" else "Public Charger"
                                            },
                                            isSummary = isSummary,
                                            fingerprint = fingerprint
                                        )
                                        onSaveSessions(listOf(entity), 0)
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .expressiveBounceClick()
                ) {
                    val label = if (isImportReviewMode) {
                        val count = importBuildResult.first.size
                        if (count == 0) "All Duplicates (Done)" else "Save $count ${if (count == 1) "Entry" else "Entries"}"
                    } else {
                        "Save Usage"
                    }
                    Text(label)
                }
            }
        }
    }
}

private fun buildEntitiesFromDrafts(
    drafts: List<ParsedChargingSessionDraft>,
    carId: String?,
    existingFingerprints: Set<String>
): Pair<List<ChargingSessionEntity>, Int> {
    val seen = HashSet<String>(existingFingerprints)
    val entities = mutableListOf<ChargingSessionEntity>()
    var skipped = 0

    for (draft in drafts) {
        if (draft.kwh <= 0.0) continue
        val sMs = ElectricityImportParser.parseDateToStartOfDayMs(draft.startDateIso) ?: continue
        val eMs = ElectricityImportParser.parseDateToEndOfDayMs(draft.endDateIso) ?: sMs
        val effectiveEnd = maxOf(sMs, eMs)
        val venue = draft.venue.uppercase(Locale.US).let { if (it == "PUBLIC") "PUBLIC" else "HOME" }
        val directCost = if (venue == "PUBLIC") draft.directCost else null
        val fp = ElectricityImportParser.buildFingerprint(
            startDateMs = sMs,
            endDateMs = effectiveEnd,
            kwh = draft.kwh,
            venue = venue,
            carId = carId
        )
        if (!seen.add(fp)) {
            skipped++
            continue
        }
        entities.add(
            ChargingSessionEntity(
                id = "chg_${UUID.randomUUID()}",
                carId = carId,
                startDateMs = sMs,
                endDateMs = effectiveEnd,
                kwh = draft.kwh,
                venue = venue,
                directCost = directCost,
                chargerName = draft.chargerName,
                isSummary = draft.isSummary,
                fingerprint = fp
            )
        )
    }
    return Pair(entities, skipped)
}

@Composable
private fun VehicleAssignmentGroup(
    cars: List<Car>,
    selectedCarId: String?,
    onSelectCarId: (String?) -> Unit
) {
    val options: List<Pair<String?, String>> = buildList {
        if (cars.size > 1) add(null to "Both EVs")
        cars.take(2).forEach { car ->
            add(car.id to (car.model.ifBlank { car.displayName }))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, (id, label) ->
            val selected = selectedCarId == id
            val startR = if (index == 0) 20.dp else if (selected) 20.dp else 6.dp
            val endR = if (index == options.lastIndex) 20.dp else if (selected) 20.dp else 6.dp
            val animatedStart by animateDpAsState(startR, spring(dampingRatio = 0.8f, stiffness = 380f), label = "v_s_$index")
            val animatedEnd by animateDpAsState(endR, spring(dampingRatio = 0.8f, stiffness = 380f), label = "v_e_$index")
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "v_bg_$index"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "v_fg_$index"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectCarId(id) },
                shape = RoundedCornerShape(
                    topStart = animatedStart,
                    bottomStart = animatedStart,
                    topEnd = animatedEnd,
                    bottomEnd = animatedEnd
                ),
                color = bg,
                contentColor = fg
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ManualModeConnectedGroup(
    selectedMode: ManualEntryMode,
    onSelectMode: (ManualEntryMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ManualEntryMode.entries.forEachIndexed { index, mode ->
            val selected = mode == selectedMode
            val isFirst = index == 0
            val inner by animateDpAsState(
                targetValue = if (selected) 20.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "mode_r_$index"
            )
            val shape = if (isFirst) {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = inner, bottomEnd = inner)
            } else {
                RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = 20.dp, bottomEnd = 20.dp)
            }
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "mode_bg_$index"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "mode_fg_$index"
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectMode(mode) },
                shape = shape,
                color = bg,
                contentColor = fg
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
