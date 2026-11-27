package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.OdometerLogEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

internal data class DateInterval(
    val label: String,
    val shortLabel: String,
    val startMs: Long,
    val endMs: Long
)

internal object InsightsDateIntervals {

    private val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private const val NIGHTLY_SNAPSHOT_HOUR_CUTOFF = 22 // 10:00 PM
    private const val MORNING_RECOVERY_HOUR_CUTOFF = 12 // 12:00 PM (Noon)

    fun formatObservationWindowText(
        granularity: InsightsTimeGranularity,
        periodOffset: Int,
        now: LocalDate
    ): String = when (granularity) {
        InsightsTimeGranularity.WEEKLY -> {
            val targetWeekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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

    fun resolvePeriodBounds(
        granularity: InsightsTimeGranularity,
        periodOffset: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long> {
        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return when (granularity) {
            InsightsTimeGranularity.WEEKLY -> {
                val targetWeekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusWeeks(periodOffset.toLong())
                val targetWeekEnd = targetWeekStart.plusDays(6)
                Pair(
                    targetWeekStart.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    targetWeekEnd.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()
                )
            }
            InsightsTimeGranularity.MONTHLY -> {
                val targetYm = YearMonth.from(now).plusMonths(periodOffset.toLong())
                Pair(
                    targetYm.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    targetYm.atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()
                )
            }
            InsightsTimeGranularity.YTD -> {
                val targetYear = now.year + periodOffset
                Pair(
                    LocalDate.of(targetYear, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    LocalDate.of(targetYear, 12, 31).atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()
                )
            }
        }
    }

    fun generateIntervals(
        granularity: InsightsTimeGranularity,
        periodOffset: Int,
        nowMs: Long,
        zoneId: ZoneId
    ): List<DateInterval> {
        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()

        return when (granularity) {
            InsightsTimeGranularity.WEEKLY -> {
                val targetWeekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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

    fun normalizeSegmentBounds(rawT1: Long, rawT2: Long, zoneId: ZoneId): Pair<Long, Long> {
        val zdt1 = Instant.ofEpochMilli(rawT1).atZone(zoneId)
        val zdt2 = Instant.ofEpochMilli(rawT2).atZone(zoneId)
        val date1 = zdt1.toLocalDate()
        val date2 = zdt2.toLocalDate()

        if (!date1.isBefore(date2)) {
            return Pair(rawT1, rawT2)
        }

        var effectiveT1 = rawT1
        var effectiveT2 = rawT2

        // 1. If the starting reading was captured at or after the nightly snapshot window (10:00 PM+),
        // that day's driving is already closed—advance the start of the subsequent interval to midnight
        // of the next day so late-night hours (10 PM–midnight) don't bleed tomorrow's miles into today.
        if (zdt1.hour >= NIGHTLY_SNAPSHOT_HOUR_CUTOFF) {
            val nextDayStartMs = date1.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            if (nextDayStartMs < effectiveT2) {
                effectiveT1 = nextDayStartMs
            }
        }

        // 2. If the ending reading was captured in the morning (before 12:00 PM) AND the previous
        // night did not have a nightly snapshot (rawT1 was before yesterday at 10:00 PM),
        // treat this morning sync as a catch-up snapshot for driving that finished by the end of yesterday.
        val prevNightCutoffMs = date2.minusDays(1)
            .atTime(NIGHTLY_SNAPSHOT_HOUR_CUTOFF, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        if (zdt2.hour < MORNING_RECOVERY_HOUR_CUTOFF && rawT1 < prevNightCutoffMs) {
            val prevDayEndMs = date2.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
            if (prevDayEndMs > effectiveT1) {
                effectiveT2 = prevDayEndMs
            }
        }

        return Pair(effectiveT1, effectiveT2)
    }

    fun calculateMilesInWindow(
        logs: List<OdometerLogEntity>,
        windowStartMs: Long,
        windowEndMs: Long,
        zoneId: ZoneId
    ): Int {
        if (logs.size < 2) return 0
        var accumulatedMiles = 0.0

        for (i in 0 until logs.size - 1) {
            val l1 = logs[i]
            val l2 = logs[i + 1]

            val rawT1 = l1.timestamp
            val rawT2 = l2.timestamp
            val deltaOdo = (l2.odometer - l1.odometer).coerceAtLeast(0)

            if (deltaOdo <= 0 || rawT2 <= rawT1) continue

            val (t1, t2) = normalizeSegmentBounds(rawT1, rawT2, zoneId)
            if (t2 <= t1) continue

            val segmentDuration = (t2 - t1).toDouble()
            val overlapStart = maxOf(t1, windowStartMs)
            val overlapEnd = minOf(t2, windowEndMs)

            if (overlapEnd > overlapStart) {
                val overlapDuration = (overlapEnd - overlapStart).toDouble()
                accumulatedMiles += deltaOdo * (overlapDuration / segmentDuration)
            }
        }

        return accumulatedMiles.roundToInt()
    }
}
