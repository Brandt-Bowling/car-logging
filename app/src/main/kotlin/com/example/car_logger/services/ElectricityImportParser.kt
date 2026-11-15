package com.example.car_logger.services

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToLong

data class ParsedUtilityBillDraft(
    val providerName: String,
    val startDateIso: String,
    val endDateIso: String,
    val totalKwh: Double,
    val totalAmount: Double,
    val fixedCharges: Double,
    val source: String = "manual",
    val fileName: String? = null,
    val error: String? = null
)

data class ParsedChargingSessionDraft(
    val startDateIso: String,
    val endDateIso: String,
    val kwh: Double,
    val venue: String, // "HOME" or "PUBLIC"
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

    fun calculateVariableBlendedRate(
        totalAmount: Double,
        fixedCharges: Double,
        totalKwh: Double
    ): Double {
        if (totalKwh <= 0.0) return 0.0
        val volumetric = (totalAmount - fixedCharges).coerceAtLeast(0.0)
        return volumetric / totalKwh
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
        val normalizedVenue = venue.uppercase(Locale.US).ifBlank { "HOME" }
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
            return "HOME"
        }
        if (publicVenueKeywords.any { lower.contains(it) }) {
            return "PUBLIC"
        }
        if (directCost != null && directCost > 0.0) {
            return "PUBLIC"
        }
        return "HOME"
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

            val directCost = if (costCol >= 0) {
                parseNumeric(row.getOrNull(costCol))?.takeIf { it > 0.0 }
            } else {
                null
            }

            val locationText = listOfNotNull(
                row.getOrNull(locationCol)?.trim()?.takeIf { it.isNotBlank() },
                row.getOrNull(typeCol)?.trim()?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { null }

            val venue = classifyVenue(locationText, directCost)
            val isSummary = endDate.isAfter(startDate.plusDays(1))

            drafts.add(
                ParsedChargingSessionDraft(
                    startDateIso = startDate.format(isoDateFormatter),
                    endDateIso = endDate.format(isoDateFormatter),
                    kwh = kwh,
                    venue = venue,
                    directCost = if (venue == "PUBLIC") directCost else null,
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
