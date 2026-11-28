package com.example.car_logger.ui.screens.home.tabs.insights.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedChargingSessionDraft
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class ManualEntryMode(val label: String) {
    SINGLE_SESSION("Single session"),
    MONTHLY_TOTAL("Monthly total")
}

@Stable
class ChargerUsageReviewState(
    val activeCars: List<Car>,
    val isImportReviewMode: Boolean,
    initialSelectedCarId: String?,
    initialVenue: ChargingVenue,
    initialDrafts: List<ParsedChargingSessionDraft>
) {
    var selectedCarId by mutableStateOf(initialSelectedCarId)
        private set

    private val _importDrafts: SnapshotStateList<ParsedChargingSessionDraft> =
        mutableStateListOf<ParsedChargingSessionDraft>().apply { addAll(initialDrafts) }

    val importDrafts: List<ParsedChargingSessionDraft>
        get() = _importDrafts

    var entryMode by mutableStateOf(ManualEntryMode.SINGLE_SESSION)
        private set

    var venue by mutableStateOf(venueToStorageValue(initialVenue))
        private set

    var singleDateText by mutableStateOf(LocalDate.now().toString())
        private set

    var rangeStartText by mutableStateOf(YearMonth.now().atDay(1).toString())
        private set

    var rangeEndText by mutableStateOf(YearMonth.now().atEndOfMonth().toString())
        private set

    var kwhText by mutableStateOf("")
        private set

    var directCostText by mutableStateOf("")
        private set

    var chargerNameText by mutableStateOf("")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun selectCarId(carId: String?) {
        selectedCarId = carId
    }

    fun selectEntryMode(mode: ManualEntryMode) {
        entryMode = mode
        errorMessage = null
    }

    fun selectVenue(newVenue: String) {
        venue = newVenue
    }

    fun updateSingleDate(value: String) {
        singleDateText = value
        errorMessage = null
    }

    fun updateRangeStart(value: String) {
        rangeStartText = value
        errorMessage = null
    }

    fun updateRangeEnd(value: String) {
        rangeEndText = value
        errorMessage = null
    }

    fun updateKwh(value: String) {
        kwhText = value
        errorMessage = null
    }

    fun updateDirectCost(value: String) {
        directCostText = value
        errorMessage = null
    }

    fun updateChargerName(value: String) {
        chargerNameText = value
    }

    fun cycleDraftVenue(index: Int) {
        val row = _importDrafts.getOrNull(index) ?: return
        val normalized = ElectricityImportParser.normalizeVenue(row.venue)
        val nextVenue = when (normalized) {
            ElectricityImportParser.VENUE_HOME -> ElectricityImportParser.VENUE_WORK
            ElectricityImportParser.VENUE_WORK -> ElectricityImportParser.VENUE_PUBLIC
            else -> ElectricityImportParser.VENUE_HOME
        }
        _importDrafts[index] = row.copy(
            venue = nextVenue,
            directCost = when (nextVenue) {
                ElectricityImportParser.VENUE_WORK -> 0.0
                ElectricityImportParser.VENUE_HOME -> null
                else -> row.directCost
            }
        )
    }

    fun removeDraftAt(index: Int) {
        if (index in _importDrafts.indices) {
            _importDrafts.removeAt(index)
        }
    }

    fun computeImportBuildResult(
        existingFingerprints: Set<String>
    ): Pair<List<ChargingSessionEntity>, Int> {
        if (!isImportReviewMode) return Pair(emptyList(), 0)
        return buildEntitiesFromDrafts(
            drafts = _importDrafts,
            carId = selectedCarId,
            existingFingerprints = existingFingerprints
        )
    }

    fun submitManualEntry(
        existingFingerprints: Set<String>,
        onSaveSessions: (sessions: List<ChargingSessionEntity>, skippedDuplicates: Int) -> Unit
    ) {
        val isSummary = entryMode == ManualEntryMode.MONTHLY_TOTAL
        val sMs = ElectricityImportParser.parseDateToStartOfDayMs(
            if (isSummary) rangeStartText else singleDateText
        )
        val eMs = ElectricityImportParser.parseDateToEndOfDayMs(
            if (isSummary) rangeEndText else singleDateText
        )
        val kwh = kwhText.toDoubleOrNull()
        val parsedCost = when (venue) {
            ElectricityImportParser.VENUE_PUBLIC -> directCostText.toDoubleOrNull()
            ElectricityImportParser.VENUE_WORK -> 0.0
            else -> null
        }

        when {
            sMs == null || eMs == null -> errorMessage = "Enter valid dates (YYYY-MM-DD)."
            eMs < sMs -> errorMessage = "End date cannot be before start date."
            kwh == null || kwh <= 0.0 -> errorMessage = "Enter energy above 0 kWh."
            venue == ElectricityImportParser.VENUE_PUBLIC && (parsedCost == null || parsedCost < 0.0) ->
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
                    errorMessage = "This charging session is already saved."
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
                            when (venue) {
                                ElectricityImportParser.VENUE_HOME -> "Home charger"
                                ElectricityImportParser.VENUE_WORK -> "Work J1772"
                                else -> "Public charger"
                            }
                        },
                        isSummary = isSummary,
                        fingerprint = fingerprint
                    )
                    onSaveSessions(listOf(entity), 0)
                }
            }
        }
    }
}

@Composable
fun rememberChargerUsageReviewState(
    initialImport: ParsedChargerImportResult?,
    cars: List<Car>,
    initialSelectedCarId: String?,
    initialVenue: ChargingVenue
): ChargerUsageReviewState {
    val activeCars = remember(cars) { cars.filter { !it.isArchived }.ifEmpty { cars } }
    val resolvedCarId = remember(activeCars, initialSelectedCarId) {
        val validInitial = initialSelectedCarId?.takeIf { id -> activeCars.any { it.id == id } }
        validInitial ?: if (activeCars.size == 1) activeCars.first().id else null
    }
    val isImportReviewMode = initialImport != null && initialImport.sessions.isNotEmpty()
    return remember(initialImport, activeCars, resolvedCarId, initialVenue) {
        ChargerUsageReviewState(
            activeCars = activeCars,
            isImportReviewMode = isImportReviewMode,
            initialSelectedCarId = resolvedCarId,
            initialVenue = initialVenue,
            initialDrafts = initialImport?.sessions.orEmpty()
        )
    }
}

private fun venueToStorageValue(venue: ChargingVenue): String = when (venue) {
    ChargingVenue.HOME -> ElectricityImportParser.VENUE_HOME
    ChargingVenue.WORK -> ElectricityImportParser.VENUE_WORK
    ChargingVenue.PUBLIC -> ElectricityImportParser.VENUE_PUBLIC
}

fun buildEntitiesFromDrafts(
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
        val venue = ElectricityImportParser.normalizeVenue(draft.venue)
        val directCost = when (venue) {
            ElectricityImportParser.VENUE_WORK -> 0.0
            ElectricityImportParser.VENUE_PUBLIC -> draft.directCost
            else -> null
        }
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
