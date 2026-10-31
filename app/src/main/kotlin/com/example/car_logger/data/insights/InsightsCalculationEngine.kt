package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

object InsightsCalculationEngine {

    private val zoneId = ZoneId.systemDefault()
    private val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun computeUiState(
        cars: List<Car>,
        allLogs: List<OdometerLogEntity>,
        granularity: InsightsTimeGranularity,
        selectedCarId: String?,
        gasPrice: Float,
        baselineMpg: Float,
        periodOffset: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): InsightsUiState {
        val activeCars = cars.filter { !it.isArchived }.ifEmpty { cars }
        if (activeCars.isEmpty() || allLogs.isEmpty()) {
            return InsightsUiState(
                selectedCarId = selectedCarId,
                granularity = granularity,
                gasPrice = gasPrice,
                baselineMpg = baselineMpg,
                hasOdometerData = false,
                observationWindowText = "No odometer readings logged yet"
            )
        }

        // Strictly filter to verified telemetry ping logs only (exclude maintenance and fake/seeded entries)
        val telemetryLogs = allLogs.filter {
            it.source != "MAINTENANCE" &&
            it.source != "INITIAL" &&
            !it.id.startsWith("rec_") &&
            !it.id.startsWith("car_odo_")
        }

        val car1 = activeCars.firstOrNull()
        val car2 = activeCars.getOrNull(1)

        val car1Logs = if (car1 != null) telemetryLogs.filter { it.carId == car1.id }.sortedBy { it.timestamp } else emptyList()
        val car2Logs = if (car2 != null) telemetryLogs.filter { it.carId == car2.id }.sortedBy { it.timestamp } else emptyList()

        val car1HasData = car1Logs.size >= 2
        val car2HasData = car2Logs.size >= 2

        val hasSufficientLogs = when (selectedCarId) {
            car1?.id -> car1HasData
            car2?.id -> car2HasData
            else -> car1HasData || car2HasData
        }

        if (!hasSufficientLogs) {
            val relevantLogs = when (selectedCarId) {
                car1?.id -> car1Logs
                car2?.id -> car2Logs
                else -> telemetryLogs
            }
            val singleTs = relevantLogs.firstOrNull()?.timestamp ?: nowMs
            val singleDateStr = LocalDate.ofInstant(Instant.ofEpochMilli(singleTs), zoneId).format(shortDateFormatter)
            val windowMsg = if (relevantLogs.isEmpty()) {
                "No telemetry readings logged yet (waiting for first vehicle sync)"
            } else {
                "Initial sync logged on $singleDateStr (more readings needed to compute driving activity)"
            }
            return InsightsUiState(
                selectedCarId = selectedCarId,
                granularity = granularity,
                car1Id = car1?.id,
                car1Name = car1?.displayName ?: "",
                car2Id = car2?.id,
                car2Name = car2?.displayName ?: "",
                gasPrice = gasPrice,
                baselineMpg = baselineMpg,
                hasOdometerData = false,
                observationWindowText = windowMsg
            )
        }

        val eligibleLogs = when (selectedCarId) {
            car1?.id -> car1Logs
            car2?.id -> car2Logs
            else -> {
                val list = mutableListOf<OdometerLogEntity>()
                if (car1HasData) list.addAll(car1Logs)
                if (car2HasData) list.addAll(car2Logs)
                list
            }
        }

        val minTimestamp = eligibleLogs.minOf { it.timestamp }
        val maxTimestamp = eligibleLogs.maxOf { it.timestamp }

        val now = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)

        // Generate intervals based on granularity and periodOffset
        val intervals = generateIntervals(granularity, periodOffset, nowMs)
        if (intervals.isEmpty()) {
            return InsightsUiState(
                selectedCarId = selectedCarId,
                granularity = granularity,
                car1Id = car1?.id,
                car1Name = car1?.displayName ?: "",
                car2Id = car2?.id,
                car2Name = car2?.displayName ?: "",
                gasPrice = gasPrice,
                baselineMpg = baselineMpg,
                hasOdometerData = true,
                observationWindowText = "No intervals in this period",
                canGoPrevious = false,
                canGoNext = periodOffset < 0,
                periodOffset = periodOffset
            )
        }

        val canGoNext = periodOffset < 0
        val earliestShownStartMs = intervals.first().startMs
        val canGoPrevious = minTimestamp < earliestShownStartMs

        val activityBars = intervals.map { interval ->
            val c1Miles = if (car1 != null) calculateMilesInWindow(car1Logs, interval.startMs, interval.endMs) else 0
            val c2Miles = if (car2 != null) calculateMilesInWindow(car2Logs, interval.startMs, interval.endMs) else 0
            DrivingActivityBar(
                label = interval.label,
                shortLabel = interval.shortLabel,
                car1Miles = c1Miles,
                car2Miles = c2Miles,
                totalMiles = c1Miles + c2Miles,
                startDateMs = interval.startMs,
                endDateMs = interval.endMs
            )
        }

        // Sum miles over all generated intervals
        val totalC1Miles = activityBars.sumOf { it.car1Miles }
        val totalC2Miles = activityBars.sumOf { it.car2Miles }

        val filteredTotalMiles = when (selectedCarId) {
            car1?.id -> totalC1Miles
            car2?.id -> totalC2Miles
            else -> totalC1Miles + totalC2Miles
        }

        val totalFleetMiles = (totalC1Miles + totalC2Miles).coerceAtLeast(1)
        val car1Pct = ((totalC1Miles.toFloat() / totalFleetMiles) * 100f).roundToInt().coerceIn(0, 100)
        val car2Pct = (100 - car1Pct).coerceIn(0, 100)

        val daysCount = when (granularity) {
            InsightsTimeGranularity.WEEKLY -> 7.0
            InsightsTimeGranularity.MONTHLY -> {
                val targetYm = YearMonth.from(now).plusMonths(periodOffset.toLong())
                targetYm.lengthOfMonth().toDouble()
            }
            InsightsTimeGranularity.YTD -> {
                val targetYear = now.year + periodOffset
                if (targetYear == now.year) now.dayOfYear.toDouble().coerceAtLeast(1.0)
                else (if (targetYear % 4 == 0) 366.0 else 365.0)
            }
        }

        val car1Daily = (totalC1Miles / daysCount).toFloat()
        val car2Daily = (totalC2Miles / daysCount).toFloat()

        // Observation window text for Google Health header
        val windowText = when (granularity) {
            InsightsTimeGranularity.WEEKLY -> {
                val targetWeekStart = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .plusWeeks(periodOffset.toLong())
                val targetWeekEnd = targetWeekStart.plusDays(6)
                if (targetWeekStart.year == now.year && targetWeekEnd.year == now.year) {
                    "${targetWeekStart.format(shortDateFormatter)} – ${targetWeekEnd.format(shortDateFormatter)}"
                } else {
                    "${targetWeekStart.format(shortDateFormatter)}, ${targetWeekStart.year} – ${targetWeekEnd.format(shortDateFormatter)}, ${targetWeekEnd.year}"
                }
            }
            InsightsTimeGranularity.MONTHLY -> {
                val targetYm = YearMonth.from(now).plusMonths(periodOffset.toLong())
                if (targetYm.year == now.year) {
                    targetYm.format(DateTimeFormatter.ofPattern("MMMM", Locale.US))
                } else {
                    targetYm.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
                }
            }
            InsightsTimeGranularity.YTD -> {
                val targetYear = now.year + periodOffset
                "$targetYear"
            }
        }

        // Gas comparison
        val effectiveMpg = baselineMpg.coerceAtLeast(1.0f)
        val totalGallons = filteredTotalMiles / effectiveMpg.toDouble()
        val totalGasCost = totalGallons * gasPrice.toDouble()
        val co2Lbs = (totalGallons * 19.64).roundToInt()

        val c1Gal = totalC1Miles / effectiveMpg.toDouble()
        val c1Cost = c1Gal * gasPrice.toDouble()
        val c2Gal = totalC2Miles / effectiveMpg.toDouble()
        val c2Cost = c2Gal * gasPrice.toDouble()

        return InsightsUiState(
            selectedCarId = selectedCarId,
            granularity = granularity,
            totalMiles = filteredTotalMiles,
            car1Id = car1?.id,
            car1Name = car1?.model ?: "Vehicle 1",
            car1Miles = totalC1Miles,
            car1SharePct = car1Pct,
            car1DailyRate = car1Daily,
            car2Id = car2?.id,
            car2Name = car2?.model ?: "Vehicle 2",
            car2Miles = totalC2Miles,
            car2SharePct = car2Pct,
            car2DailyRate = car2Daily,
            observationWindowText = windowText,
            activityBars = activityBars,
            gasComparison = GasComparisonData(
                totalCost = totalGasCost,
                totalGallons = totalGallons,
                co2Lbs = co2Lbs,
                car1Cost = c1Cost,
                car1Gallons = c1Gal,
                car2Cost = c2Cost,
                car2Gallons = c2Gal
            ),
            gasPrice = gasPrice,
            baselineMpg = baselineMpg,
            hasOdometerData = true,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            periodOffset = periodOffset
        )
    }

    private data class DateInterval(
        val label: String,
        val shortLabel: String,
        val startMs: Long,
        val endMs: Long
    )

    private fun generateIntervals(
        granularity: InsightsTimeGranularity,
        periodOffset: Int,
        nowMs: Long
    ): List<DateInterval> {
        val now = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)

        return when (granularity) {
            InsightsTimeGranularity.WEEKLY -> {
                // Single week (Mon-Sun) shifted by periodOffset weeks
                val targetWeekStart = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .plusWeeks(periodOffset.toLong())
                val intervals = mutableListOf<DateInterval>()

                for (i in 0 until 7) {
                    val day = targetWeekStart.plusDays(i.toLong())
                    val startMs = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endMs = day.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                    intervals.add(
                        DateInterval(
                            label = day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)),
                            shortLabel = day.format(DateTimeFormatter.ofPattern("EEE", Locale.US)),
                            startMs = startMs,
                            endMs = endMs
                        )
                    )
                }
                intervals
            }
            InsightsTimeGranularity.MONTHLY -> {
                // Single month shifted by periodOffset months, broken into ~7-day blocks
                val targetYm = YearMonth.from(now).plusMonths(periodOffset.toLong())
                val intervals = mutableListOf<DateInterval>()
                val daysInMonth = targetYm.lengthOfMonth()
                val monthName = targetYm.format(shortMonthFormatter)

                var dayStart = 1
                while (dayStart <= daysInMonth) {
                    val dayEnd = minOf(dayStart + 6, daysInMonth)
                    val wStart = targetYm.atDay(dayStart)
                    val wEnd = targetYm.atDay(dayEnd)
                    val startMs = wStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endMs = wEnd.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                    intervals.add(
                        DateInterval(
                            label = "${wStart.format(shortDateFormatter)} – ${wEnd.format(shortDateFormatter)}",
                            shortLabel = "$monthName $dayStart",
                            startMs = startMs,
                            endMs = endMs
                        )
                    )
                    dayStart = dayEnd + 1
                }
                intervals
            }
            InsightsTimeGranularity.YTD -> {
                // 12 months for target year
                val targetYear = now.year + periodOffset
                val intervals = mutableListOf<DateInterval>()

                for (m in 1..12) {
                    val ym = YearMonth.of(targetYear, m)
                    val mStart = ym.atDay(1)
                    val mEnd = ym.atEndOfMonth()
                    val startMs = mStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endMs = mEnd.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                    intervals.add(
                        DateInterval(
                            label = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                            shortLabel = ym.format(shortMonthFormatter),
                            startMs = startMs,
                            endMs = endMs
                        )
                    )
                }
                intervals
            }
        }
    }

    private fun calculateMilesInWindow(
        logs: List<OdometerLogEntity>,
        windowStartMs: Long,
        windowEndMs: Long
    ): Int {
        if (logs.size < 2) return 0
        var accumulatedMiles = 0.0

        for (i in 0 until logs.size - 1) {
            val l1 = logs[i]
            val l2 = logs[i + 1]

            val t1 = l1.timestamp
            val t2 = l2.timestamp
            val deltaOdo = (l2.odometer - l1.odometer).coerceAtLeast(0)

            if (deltaOdo <= 0 || t2 <= t1) continue

            val segmentDuration = (t2 - t1).toDouble()
            val overlapStart = maxOf(t1, windowStartMs)
            val overlapEnd = minOf(t2, windowEndMs)

            if (overlapEnd > overlapStart) {
                val overlapDuration = (overlapEnd - overlapStart).toDouble()
                val segmentContribution = deltaOdo * (overlapDuration / segmentDuration)
                accumulatedMiles += segmentContribution
            }
        }

        return accumulatedMiles.roundToInt()
    }
}
