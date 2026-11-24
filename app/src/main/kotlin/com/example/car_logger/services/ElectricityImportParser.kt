package com.example.car_logger.services

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToLong

data class ParsedBillLineItem(
    val label: String,
    val category: String,
    val kwh: Double? = null,
    val ratePerKwh: Double? = null,
    val amount: Double? = null
)

data class ParsedUtilityBillDraft(
    val providerName: String,
    val startDateIso: String,
    val endDateIso: String,
    val totalKwh: Double,
    val totalAmount: Double,
    val fixedCharges: Double,
    val source: String = "manual",
    val fileName: String? = null,
    val error: String? = null,
    val isTouPlan: Boolean = false,
    val offPeakSupplyRate: Double? = null,
    val onPeakSupplyRate: Double? = null,
    val distributionRatePerKwh: Double? = null,
    val offPeakKwh: Double? = null,
    val onPeakKwh: Double? = null,
    val breakdownItems: List<ParsedBillLineItem> = emptyList()
)

data class ParsedChargingSessionDraft(
    val startDateIso: String,
    val endDateIso: String,
    val kwh: Double,
    val venue: String, // "HOME", "WORK", or "PUBLIC"
    val directCost: Double? = null,
    val chargerName: String? = null,
    val isSummary: Boolean = false
)

data class ParsedChargerImportResult(
    val sessions: List<ParsedChargingSessionDraft>,
    val fileName: String,
    val source: String = "csv",
    val error: String? = null
)

object ElectricityImportParser {

    const val VENUE_HOME = "HOME"
    const val VENUE_WORK = "WORK"
    const val VENUE_PUBLIC = "PUBLIC"

    const val AUTO_WALL_CONNECTOR_BATCH_ID = "auto_wall_connector"
    const val AUTO_WORK_CHARGING_BATCH_ID = "auto_work_charging"
    const val AUTO_SUPERCHARGER_BATCH_ID = "auto_supercharger"
    const val AUTO_SMARTCAR_CHARGING_BATCH_ID = "auto_smartcar_charging"

    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val shortDisplayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    private val dateCandidateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yy", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/M/d", Locale.US),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    )

    private val publicVenueKeywords = listOf(
        "supercharger", "electrify", "evgo", "chargepoint", "blink",
        "rivian adventure", "public", "fast charger", "dcfc", "ccs",
        "shell recharge", "bp pulse", "volta", "circle k", "ev connect",
        "francis energy", "ionna", "red e", "flo "
    )

    private val homeVenueKeywords = listOf(
        "home", "garage", "residence", "residential", "wall connector",
        "mobile connector", "emporia", "wallbox", "my charger", "house", "driveway"
    )

    private val workVenueKeywords = listOf(
        "work", "office", "workplace", "employer", "company", "campus",
        "free charge", "free charging", "complimentary", "free j1772"
    )

    fun normalizeVenue(raw: String?): String = when (raw?.trim()?.uppercase(Locale.US)) {
        VENUE_WORK -> VENUE_WORK
        VENUE_PUBLIC -> VENUE_PUBLIC
        else -> VENUE_HOME
    }

    fun isAutomatedSyncBatch(session: ChargingSessionEntity): Boolean {
        return session.uploadBatchId == AUTO_WALL_CONNECTOR_BATCH_ID ||
            session.uploadBatchId == AUTO_WORK_CHARGING_BATCH_ID ||
            session.uploadBatchId == AUTO_SUPERCHARGER_BATCH_ID ||
            session.uploadBatchId == AUTO_SMARTCAR_CHARGING_BATCH_ID ||
            session.id.startsWith("twc_") ||
            session.id.startsWith("tesla_work_") ||
            session.id.startsWith("sc_tesla_") ||
            session.id.startsWith("sc_smartcar_")
    }

    fun calculateVariableBlendedRate(
        totalAmount: Double,
        fixedCharges: Double,
        totalKwh: Double
    ): Double {
        if (totalKwh <= 0.0) return 0.0
        val volumetric = (totalAmount - fixedCharges).coerceAtLeast(0.0)
        return volumetric / totalKwh
    }

    fun calculateTouOffPeakRate(
        offPeakSupplyRate: Double?,
        distributionRatePerKwh: Double?
    ): Double? {
        val supply = offPeakSupplyRate?.takeIf { it > 0.0 } ?: return null
        val distribution = (distributionRatePerKwh ?: 0.0).coerceAtLeast(0.0)
        return supply + distribution
    }

    fun buildFingerprint(
        startDateMs: Long,
        endDateMs: Long,
        kwh: Double,
        venue: String,
        carId: String?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val startDay = Instant.ofEpochMilli(startDateMs).atZone(zoneId).toLocalDate().toString()
        val endDay = Instant.ofEpochMilli(endDateMs).atZone(zoneId).toLocalDate().toString()
        val quantizedKwh = (kwh * 10.0).roundToLong()
        val normalizedVenue = normalizeVenue(venue)
        val normalizedCar = carId?.takeIf { it.isNotBlank() } ?: "shared"
        return "$startDay|$endDay|$quantizedKwh|$normalizedVenue|$normalizedCar"
    }

    fun parseDateToLocalDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // Strip trailing timestamp portion if ISO datetime or space-separated time
        val dateToken = when {
            trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}.*""")) -> trimmed.substring(0, 10)
            trimmed.matches(Regex("""^\d{1,2}/\d{1,2}/\d{2,4}\s+\d{1,2}:\d{2}.*""")) -> trimmed.substringBefore(" ").trim()
            else -> trimmed
        }

        for (fmt in dateCandidateFormats) {
            try {
                return LocalDate.parse(dateToken, fmt)
            } catch (_: DateTimeParseException) {
                // Try next format
            }
        }

        // Fallback regex scan for YYYY-MM-DD or MM/DD/YYYY inside text
        val isoMatch = Regex("""(20\d{2})[-/](\d{1,2})[-/](\d{1,2})""").find(trimmed)
        if (isoMatch != null) {
            val (y, m, d) = isoMatch.destructured
            return runCatching {
                LocalDate.of(y.toInt(), m.toInt(), d.toInt())
            }.getOrNull()
        }

        val usMatch = Regex("""(\d{1,2})/(\d{1,2})/(20\d{2})""").find(trimmed)
        if (usMatch != null) {
            val (m, d, y) = usMatch.destructured
            return runCatching {
                LocalDate.of(y.toInt(), m.toInt(), d.toInt())
            }.getOrNull()
        }

        return null
    }

    fun parseDateToStartOfDayMs(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val ld = parseDateToLocalDate(raw) ?: return null
        return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun parseDateToEndOfDayMs(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val ld = parseDateToLocalDate(raw) ?: return null
        return ld.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun formatEpochMsToIsoDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().format(isoDateFormatter)
    }

    fun formatPeriodShort(
        startMs: Long,
        endMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val start = Instant.ofEpochMilli(startMs).atZone(zoneId).toLocalDate()
        val end = Instant.ofEpochMilli(endMs).atZone(zoneId).toLocalDate()
        return if (start == end) {
            start.format(shortDisplayFormatter)
        } else {
            "${start.format(shortDisplayFormatter)}–${end.format(shortDisplayFormatter)}"
        }
    }

    fun classifyVenue(chargerName: String?, directCost: Double?): String {
        val lower = chargerName.orEmpty().lowercase(Locale.US)
        if (homeVenueKeywords.any { lower.contains(it) }) {
            return VENUE_HOME
        }
        if (publicVenueKeywords.any { lower.contains(it) }) {
            return VENUE_PUBLIC
        }
        if (workVenueKeywords.any { lower.contains(it) } && (directCost == null || directCost <= 0.0)) {
            return VENUE_WORK
        }
        if (directCost != null && directCost > 0.0) {
            return VENUE_PUBLIC
        }
        return VENUE_HOME
    }

    fun parseChargerCsv(fileName: String, csvContent: String): List<ParsedChargingSessionDraft> {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()

        // Find the header row (some exports prepend metadata rows)
        var headerIndex = -1
        var headers: List<String> = emptyList()

        for (i in 0 until minOf(lines.size, 10)) {
            val cols = splitCsvLine(lines[i]).map { it.trim().lowercase(Locale.US) }
            val hasEnergy = cols.any { it.contains("kwh") || it.contains("energy") || it.contains("charged") }
            val hasDate = cols.any { it.contains("date") || it.contains("time") || it.contains("start") || it.contains("day") }
            if (cols.size >= 2 && hasEnergy && hasDate) {
                headerIndex = i
                headers = cols
                break
            }
        }

        if (headerIndex == -1) return emptyList()

        val startDateCol = headers.indexOfFirst {
            it.contains("start") && (it.contains("date") || it.contains("time"))
        }.takeIf { it >= 0 } ?: headers.indexOfFirst {
            it.contains("date") || it.contains("plug in") || it.contains("timestamp") || it.contains("day")
        }

        val endDateCol = headers.indexOfFirst {
            (it.contains("end") || it.contains("stop")) && (it.contains("date") || it.contains("time"))
        }

        val kwhCol = headers.indexOfFirst {
            it.contains("kwh") || it.contains("energy") || it.contains("charged")
        }

        val costCol = headers.indexOfFirst {
            it.contains("cost") || it.contains("fee") || it.contains("amount") || it.contains("price") || it.contains("total")
        }

        val locationCol = headers.indexOfFirst {
            it.contains("location") || it.contains("station") || it.contains("charger") ||
                it.contains("site") || it.contains("facility") || it.contains("venue") || it.contains("name")
        }

        val typeCol = headers.indexOfFirst {
            it == "type" || it.contains("session type") || it.contains("charge type")
        }

        if (startDateCol < 0 || kwhCol < 0) return emptyList()

        val drafts = mutableListOf<ParsedChargingSessionDraft>()
        for (i in (headerIndex + 1) until lines.size) {
            val row = splitCsvLine(lines[i])
            if (row.size <= maxOf(startDateCol, kwhCol)) continue

            val startDate = parseDateToLocalDate(row.getOrNull(startDateCol)) ?: continue
            val endDate = if (endDateCol >= 0) {
                parseDateToLocalDate(row.getOrNull(endDateCol)) ?: startDate
            } else {
                startDate
            }

            val kwh = parseNumeric(row.getOrNull(kwhCol)) ?: continue
            if (kwh <= 0.0) continue

            val parsedCost = if (costCol >= 0) {
                parseNumeric(row.getOrNull(costCol))?.takeIf { it >= 0.0 }
            } else {
                null
            }

            val locationText = listOfNotNull(
                row.getOrNull(locationCol)?.trim()?.takeIf { it.isNotBlank() },
                row.getOrNull(typeCol)?.trim()?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { null }

            val venue = classifyVenue(locationText, parsedCost)
            val isSummary = endDate.isAfter(startDate.plusDays(1))

            drafts.add(
                ParsedChargingSessionDraft(
                    startDateIso = startDate.format(isoDateFormatter),
                    endDateIso = endDate.format(isoDateFormatter),
                    kwh = kwh,
                    venue = venue,
                    directCost = when (venue) {
                        VENUE_PUBLIC -> parsedCost
                        VENUE_WORK -> 0.0
                        else -> null
                    },
                    chargerName = locationText ?: fileName.substringBeforeLast("."),
                    isSummary = isSummary
                )
            )
        }

        return drafts
    }

    fun fallbackBillDraftFromFilename(fileName: String, error: String? = null): ParsedUtilityBillDraft {
        val clean = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
        val now = LocalDate.now()
        var endDate = now
        var startDate = now.minusMonths(1).plusDays(1)

        val datePattern = Pattern.compile("""(?<!\d)(20\d{2})[-_/.](0?[1-9]|1[0-2])[-_/.](0?[1-9]|[12]\d|3[01])(?!\d)""")
        val matcher = datePattern.matcher(fileName)
        if (matcher.find()) {
            val parsed = runCatching {
                LocalDate.of(
                    matcher.group(1)!!.toInt(),
                    matcher.group(2)!!.toInt(),
                    matcher.group(3)!!.toInt()
                )
            }.getOrNull()
            if (parsed != null) {
                endDate = parsed
                startDate = parsed.minusMonths(1).plusDays(1)
            }
        }

        return ParsedUtilityBillDraft(
            providerName = clean.take(28).trim().ifBlank { "Electric Utility" },
            startDateIso = startDate.format(isoDateFormatter),
            endDateIso = endDate.format(isoDateFormatter),
            totalKwh = 0.0,
            totalAmount = 0.0,
            fixedCharges = 0.0,
            source = "manual",
            fileName = fileName,
            error = error
        )
    }

    /**
     * Identifies the most recently uploaded batch of charging sessions so the user can
     * undo their last upload in a single action (or re-tag it with a different vehicle).
     *
     * For sessions saved with an explicit [ChargingSessionEntity.uploadBatchId], all sessions
     * sharing the latest upload's batch ID are returned. For legacy sessions created prior to
     * batch tracking, sessions are ordered by [ChargingSessionEntity.uploadedAtMs] descending
     * (backfilled from SQLite `rowid` insertion order) and the contiguous latest group sharing
     * the same `carId` is returned.
     */
    fun resolveLastUploadBatch(sessions: List<ChargingSessionEntity>): List<ChargingSessionEntity> {
        val manualSessions = sessions.filterNot { isAutomatedSyncBatch(it) }
        if (manualSessions.isEmpty()) return emptyList()
        val latest = manualSessions.maxWithOrNull(
            compareBy<ChargingSessionEntity> { it.uploadedAtMs }
                .thenBy { it.startDateMs }
                .thenBy { it.id }
        ) ?: return emptyList()

        val batchId = latest.uploadBatchId
        if (!batchId.isNullOrBlank()) {
            return manualSessions
                .filter { it.uploadBatchId == batchId }
                .sortedByDescending { it.startDateMs }
        }

        val legacyOrdered = manualSessions
            .filter { it.uploadBatchId.isNullOrBlank() }
            .sortedWith(
                compareByDescending<ChargingSessionEntity> { it.uploadedAtMs }
                    .thenByDescending { it.startDateMs }
                    .thenByDescending { it.id }
            )
        val targetCarId = latest.carId
        return legacyOrdered
            .takeWhile { it.carId == targetCarId }
            .sortedByDescending { it.startDateMs }
    }

    /**
     * Converts a batch of saved [ChargingSessionEntity] rows back into a [ParsedChargerImportResult]
     * so an undone upload can be immediately reopened in the review sheet to pick a vehicle tag.
     */
    fun sessionsToImportResult(
        sessions: List<ChargingSessionEntity>,
        fileName: String = "last upload",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ParsedChargerImportResult {
        val drafts = sessions
            .sortedByDescending { it.startDateMs }
            .map { session ->
                ParsedChargingSessionDraft(
                    startDateIso = formatEpochMsToIsoDate(session.startDateMs, zoneId),
                    endDateIso = formatEpochMsToIsoDate(session.endDateMs, zoneId),
                    kwh = session.kwh,
                    venue = session.venue,
                    directCost = session.directCost,
                    chargerName = session.chargerName,
                    isSummary = session.isSummary
                )
            }
        return ParsedChargerImportResult(
            sessions = drafts,
            fileName = fileName,
            source = "undo"
        )
    }

    private fun parseNumeric(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace("$", "")
            .replace(",", "")
            .replace("kWh", "", ignoreCase = true)
            .replace("kwh", "", ignoreCase = true)
            .trim()
        return cleaned.toDoubleOrNull()
    }

    internal fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }
}
