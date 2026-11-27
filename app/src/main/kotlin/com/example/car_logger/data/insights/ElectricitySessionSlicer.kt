package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ElectricityImportParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class EnergySlice(
    val kwh: Double,
    val cost: Double,
    val carId: String?,
    val venue: String,
    val resolvedRate: ResolvedBillRate?
)

internal class VenueSliceState {
    val slices = mutableListOf<EnergySlice>()
    val periodLogs = mutableListOf<PeriodChargingLogItem>()
    var sessionCountAllCars = 0
    var sessionCountCar1 = 0
    var sessionCountCar2 = 0

    fun recordSessionCounts(sessionCarId: String?, car1Id: String?, car2Id: String?) {
        sessionCountAllCars++
        if (sessionCarId == null || sessionCarId == car1Id) sessionCountCar1++
        if (sessionCarId == null || sessionCarId == car2Id) sessionCountCar2++
    }
}

internal data class SliceAccumulationResult(
    val home: VenueSliceState,
    val work: VenueSliceState,
    val publicVenue: VenueSliceState
)

internal object ElectricitySessionSlicer {

    fun sliceSessions(
        orderedSessions: List<ChargingSessionEntity>,
        car1Id: String?,
        car2Id: String?,
        windowStartMs: Long,
        windowEndMs: Long,
        windowStartDay: LocalDate,
        windowEndDay: LocalDate,
        utilityBills: List<UtilityBillEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        attributionForSelectedCar: (String?) -> Pair<Double, Boolean>?,
        zoneId: ZoneId
    ): SliceAccumulationResult {
        val home = VenueSliceState()
        val work = VenueSliceState()
        val publicVenue = VenueSliceState()

        fun stateForVenue(venue: String): VenueSliceState = when (venue) {
            ElectricityImportParser.VENUE_PUBLIC -> publicVenue
            ElectricityImportParser.VENUE_WORK -> work
            else -> home
        }

        for (session in orderedSessions) {
            val venue = ElectricityImportParser.normalizeVenue(session.venue)
            val targetState = stateForVenue(venue)
            if (session.isSummary) {
                sliceSummarySession(
                    session = session,
                    venue = venue,
                    targetState = targetState,
                    car1Id = car1Id,
                    car2Id = car2Id,
                    windowStartDay = windowStartDay,
                    windowEndDay = windowEndDay,
                    utilityBills = utilityBills,
                    defaultElectricityRate = defaultElectricityRate,
                    useCustomElectricityRate = useCustomElectricityRate,
                    attributionForSelectedCar = attributionForSelectedCar,
                    zoneId = zoneId
                )
            } else {
                val midMs = (session.startDateMs + session.endDateMs) / 2
                if (midMs !in windowStartMs..windowEndMs) continue
                sliceSingleSession(
                    session = session,
                    venue = venue,
                    targetState = targetState,
                    midMs = midMs,
                    car1Id = car1Id,
                    car2Id = car2Id,
                    utilityBills = utilityBills,
                    defaultElectricityRate = defaultElectricityRate,
                    useCustomElectricityRate = useCustomElectricityRate,
                    attributionForSelectedCar = attributionForSelectedCar,
                    zoneId = zoneId
                )
            }
        }

        return SliceAccumulationResult(home = home, work = work, publicVenue = publicVenue)
    }

    private fun sliceSummarySession(
        session: ChargingSessionEntity,
        venue: String,
        targetState: VenueSliceState,
        car1Id: String?,
        car2Id: String?,
        windowStartDay: LocalDate,
        windowEndDay: LocalDate,
        utilityBills: List<UtilityBillEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        attributionForSelectedCar: (String?) -> Pair<Double, Boolean>?,
        zoneId: ZoneId
    ) {
        val sStartDay = Instant.ofEpochMilli(session.startDateMs).atZone(zoneId).toLocalDate()
        val sEndDay = Instant.ofEpochMilli(session.endDateMs).atZone(zoneId).toLocalDate()
        val effectiveEndDay = if (sEndDay.isBefore(sStartDay)) sStartDay else sEndDay
        val totalDays = (ChronoUnit.DAYS.between(sStartDay, effectiveEndDay) + 1).coerceAtLeast(1)
        val overlapStart = maxOf(sStartDay, windowStartDay)
        val overlapEnd = minOf(effectiveEndDay, windowEndDay)
        if (overlapEnd.isBefore(overlapStart)) return

        val dailyKwh = session.kwh / totalDays.toDouble()
        var overlaps = false
        var overlapDays = 0L
        var rawPeriodCost = 0.0
        var rawPeriodAvoidedCost = 0.0
        var anyDayEstimated = false
        var cursor = overlapStart
        while (!cursor.isAfter(overlapEnd)) {
            overlaps = true
            overlapDays++
            val dayMidMs = cursor.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
            when (venue) {
                ElectricityImportParser.VENUE_PUBLIC -> {
                    val dailyCost = (session.directCost ?: 0.0) / totalDays.toDouble()
                    rawPeriodCost += dailyCost
                    targetState.slices.add(EnergySlice(dailyKwh, dailyCost, session.carId, venue, null))
                }
                ElectricityImportParser.VENUE_WORK -> {
                    val resolved = UtilityRateResolver.resolveRateForTimestamp(
                        targetMs = dayMidMs,
                        utilityBills = utilityBills,
                        defaultElectricityRate = defaultElectricityRate,
                        useCustomElectricityRate = useCustomElectricityRate,
                        zoneId = zoneId
                    )
                    val avoidedDayCost = dailyKwh * resolved.rate
                    rawPeriodAvoidedCost += avoidedDayCost
                    if (resolved.isEstimated) anyDayEstimated = true
                    // Store avoided home cost in slice.cost for clean attribution; actual work spend is always $0.00
                    targetState.slices.add(EnergySlice(dailyKwh, avoidedDayCost, session.carId, venue, resolved))
                }
                else -> {
                    val resolved = UtilityRateResolver.resolveRateForTimestamp(
                        targetMs = dayMidMs,
                        utilityBills = utilityBills,
                        defaultElectricityRate = defaultElectricityRate,
                        useCustomElectricityRate = useCustomElectricityRate,
                        zoneId = zoneId
                    )
                    val dayCost = dailyKwh * resolved.rate
                    rawPeriodCost += dayCost
                    if (resolved.isEstimated) anyDayEstimated = true
                    targetState.slices.add(EnergySlice(dailyKwh, dayCost, session.carId, venue, resolved))
                }
            }
            cursor = cursor.plusDays(1)
        }
        if (!overlaps) return

        targetState.recordSessionCounts(session.carId, car1Id, car2Id)
        val attribution = attributionForSelectedCar(session.carId) ?: return
        val (multiplier, isSharedSplit) = attribution
        val rawPeriodKwh = dailyKwh * overlapDays.toDouble()
        val effectiveRate = when (venue) {
            ElectricityImportParser.VENUE_WORK -> 0.0
            else -> if (rawPeriodKwh > 0.0) rawPeriodCost / rawPeriodKwh else 0.0
        }
        targetState.periodLogs.add(
            PeriodChargingLogItem(
                session = session,
                attributedKwh = rawPeriodKwh * multiplier,
                attributedCost = if (venue == ElectricityImportParser.VENUE_WORK) 0.0 else rawPeriodCost * multiplier,
                effectiveRate = effectiveRate,
                isEstimatedRate = if (venue == ElectricityImportParser.VENUE_HOME) anyDayEstimated else false,
                isSharedSplit = isSharedSplit,
                isProratedSummary = overlapDays < totalDays,
                avoidedHomeCost = if (venue == ElectricityImportParser.VENUE_WORK) rawPeriodAvoidedCost * multiplier else 0.0
            )
        )
    }

    private fun sliceSingleSession(
        session: ChargingSessionEntity,
        venue: String,
        targetState: VenueSliceState,
        midMs: Long,
        car1Id: String?,
        car2Id: String?,
        utilityBills: List<UtilityBillEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        attributionForSelectedCar: (String?) -> Pair<Double, Boolean>?,
        zoneId: ZoneId
    ) {
        targetState.recordSessionCounts(session.carId, car1Id, car2Id)
        when (venue) {
            ElectricityImportParser.VENUE_PUBLIC -> {
                val rawCost = session.directCost ?: 0.0
                targetState.slices.add(EnergySlice(session.kwh, rawCost, session.carId, venue, null))
                val (multiplier, isSharedSplit) = attributionForSelectedCar(session.carId) ?: return
                val effectiveRate = if (session.kwh > 0.0) rawCost / session.kwh else 0.0
                targetState.periodLogs.add(
                    PeriodChargingLogItem(
                        session = session,
                        attributedKwh = session.kwh * multiplier,
                        attributedCost = rawCost * multiplier,
                        effectiveRate = effectiveRate,
                        isEstimatedRate = false,
                        isSharedSplit = isSharedSplit,
                        isProratedSummary = false
                    )
                )
            }
            ElectricityImportParser.VENUE_WORK -> {
                val resolved = UtilityRateResolver.resolveRateForTimestamp(
                    targetMs = midMs,
                    utilityBills = utilityBills,
                    defaultElectricityRate = defaultElectricityRate,
                    useCustomElectricityRate = useCustomElectricityRate,
                    zoneId = zoneId
                )
                val rawAvoidedCost = session.kwh * resolved.rate
                targetState.slices.add(EnergySlice(session.kwh, rawAvoidedCost, session.carId, venue, resolved))
                val (multiplier, isSharedSplit) = attributionForSelectedCar(session.carId) ?: return
                targetState.periodLogs.add(
                    PeriodChargingLogItem(
                        session = session,
                        attributedKwh = session.kwh * multiplier,
                        attributedCost = 0.0,
                        effectiveRate = 0.0,
                        isEstimatedRate = false,
                        isSharedSplit = isSharedSplit,
                        isProratedSummary = false,
                        avoidedHomeCost = rawAvoidedCost * multiplier
                    )
                )
            }
            else -> {
                val resolved = UtilityRateResolver.resolveRateForTimestamp(
                    targetMs = midMs,
                    utilityBills = utilityBills,
                    defaultElectricityRate = defaultElectricityRate,
                    useCustomElectricityRate = useCustomElectricityRate,
                    zoneId = zoneId
                )
                val rawCost = session.kwh * resolved.rate
                targetState.slices.add(EnergySlice(session.kwh, rawCost, session.carId, venue, resolved))
                val (multiplier, isSharedSplit) = attributionForSelectedCar(session.carId) ?: return
                targetState.periodLogs.add(
                    PeriodChargingLogItem(
                        session = session,
                        attributedKwh = session.kwh * multiplier,
                        attributedCost = rawCost * multiplier,
                        effectiveRate = resolved.rate,
                        isEstimatedRate = resolved.isEstimated,
                        isSharedSplit = isSharedSplit,
                        isProratedSummary = false
                    )
                )
            }
        }
    }
}
