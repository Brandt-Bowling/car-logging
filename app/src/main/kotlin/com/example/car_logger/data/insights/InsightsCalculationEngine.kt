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

        val car1 = activeCars.firstOrNull()
        val car2 = activeCars.getOrNull(1)

        val car1Logs = if (car1 != null) allLogs.filter { it.carId == car1.id }.sortedBy { it.timestamp } else emptyList()
        val car2Logs = if (car2 != null) allLogs.filter { it.carId == car2.id }.sortedBy { it.timestamp } else emptyList()

        val relevantLogs = when (selectedCarId) {
            car1?.id -> car1Logs
            car2?.id -> car2Logs
            else -> allLogs
        }

        if (relevantLogs.size < 2) {
            val singleTs = relevantLogs.firstOrNull()?.timestamp ?: nowMs
            val singleDateStr = LocalDate.ofInstant(Instant.ofEpochMilli(singleTs), zoneId).format(shortDateFormatter)
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
                observationWindowText = "Initial reading logged on $singleDateStr (more readings needed for activity)"
            )
        }

        val minTimestamp = relevantLogs.minOf { it.timestamp }
        val maxTimestamp = relevantLogs.maxOf { it.timestamp }

        // Generate intervals based on granularity
        val intervals = generateIntervals(granularity, minTimestamp, maxTimestamp, nowMs)
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
                observationWindowText = "Reading intervals pending"
            )
        }

        val activityBars = intervals.map { interval ->
            val c1Miles = if (car1 != null) calculateMilesInWindow(car1Logs, interval.startMs, interval.endMs) else 0
            val c2Miles = if (car2 != null) calculateMilesInWindow(car2Logs, interval.startMs, interval.endMs) else 0
            DrivingActivityBar(
                label = interval.label,
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

        val totalDays = ((intervals.last().endMs - intervals.first().startMs) / 86400000.0).coerceAtLeast(1.0)
        val car1Daily = (totalC1Miles / totalDays).toFloat()
        val car2Daily = (totalC2Miles / totalDays).toFloat()

        // Observation window text
        val windowStartDate = LocalDate.ofInstant(Instant.ofEpochMilli(intervals.first().startMs), zoneId)
        val windowEndDate = LocalDate.ofInstant(Instant.ofEpochMilli(intervals.last().endMs), zoneId)
        val windowText = when (granularity) {
            InsightsTimeGranularity.WEEKLY -> "Tracking: ${windowStartDate.format(shortDateFormatter)} – ${windowEndDate.format(shortDateFormatter)} (${intervals.size} completed weeks)"
            InsightsTimeGranularity.MONTHLY -> "Tracking: ${windowStartDate.format(shortDateFormatter)} – ${windowEndDate.format(shortDateFormatter)} (${intervals.size} completed months)"
            InsightsTimeGranularity.YTD -> "Tracking: ${windowStartDate.format(shortDateFormatter)} – ${windowEndDate.format(shortDateFormatter)} (${totalDays.toInt()} verified days)"
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
            hasOdometerData = true
        )
    }

    private data class DateInterval(
        val label: String,
        val startMs: Long,
        val endMs: Long
    )

    private fun generateIntervals(
        granularity: InsightsTimeGranularity,
        minTimestamp: Long,
        maxTimestamp: Long,
        nowMs: Long
    ): List<DateInterval> {
        val now = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        val minDate = LocalDate.ofInstant(Instant.ofEpochMilli(minTimestamp), zoneId)
        val maxDate = LocalDate.ofInstant(Instant.ofEpochMilli(maxTimestamp), zoneId)

        return when (granularity) {
            InsightsTimeGranularity.WEEKLY -> {
                // Generate last up to 7 completed Monday-Sunday weeks
                val intervals = mutableListOf<DateInterval>()
                // Start with Sunday of the most recently completed week
                var currentWeekStart = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

                for (i in 0 until 7) {
                    val wStart = currentWeekStart.minusWeeks(i.toLong())
                    val wEnd = wStart.plusDays(6)
                    val wStartMs = wStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val wEndMs = wEnd.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

                    // Only include week if it overlaps with verified reading window
                    if (wEndMs >= minTimestamp && wStartMs <= maxTimestamp) {
                        intervals.add(
                            DateInterval(
                                label = "W${7 - i} ${wStart.format(shortDateFormatter)}",
                                startMs = wStartMs,
                                endMs = wEndMs
                            )
                        )
                    }
                }
                intervals.reversed()
            }
            InsightsTimeGranularity.MONTHLY -> {
                // Generate last up to 6 completed calendar months
                val intervals = mutableListOf<DateInterval>()
                var ym = YearMonth.from(now)

                for (i in 0 until 6) {
                    val targetYm = ym.minusMonths(i.toLong())
                    val mStart = targetYm.atDay(1)
                    val mEnd = targetYm.atEndOfMonth()
                    val mStartMs = mStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val mEndMs = mEnd.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

                    if (mEndMs >= minTimestamp && mStartMs <= maxTimestamp) {
                        intervals.add(
                            DateInterval(
                                label = targetYm.format(shortMonthFormatter),
                                startMs = mStartMs,
                                endMs = mEndMs
                            )
                        )
                    }
                }
                intervals.reversed()
            }
            InsightsTimeGranularity.YTD -> {
                // Months in current year up to now
                val intervals = mutableListOf<DateInterval>()
                val startOfYear = LocalDate.of(now.year, 1, 1)
                var currentMonth = YearMonth.from(startOfYear)
                val endMonth = YearMonth.from(now)

                while (!currentMonth.isAfter(endMonth)) {
                    val mStart = currentMonth.atDay(1)
                    val mEnd = currentMonth.atEndOfMonth()
                    val mStartMs = mStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val mEndMs = mEnd.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

                    if (mEndMs >= minTimestamp && mStartMs <= maxTimestamp) {
                        intervals.add(
                            DateInterval(
                                label = currentMonth.format(shortMonthFormatter),
                                startMs = mStartMs,
                                endMs = mEndMs
                            )
                        )
                    }
                    currentMonth = currentMonth.plusMonths(1)
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
