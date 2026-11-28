package com.example.car_logger.insights

import java.time.LocalDate
import java.time.ZoneId

internal object InsightsTestFixtures {

    val zoneId: ZoneId = ZoneId.systemDefault()

    fun startMs(isoDate: String): Long =
        LocalDate.parse(isoDate).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun endMs(isoDate: String): Long =
        LocalDate.parse(isoDate).atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

    fun tsAtHour(isoDate: String, hour: Int): Long =
        LocalDate.parse(isoDate).atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli()
}
