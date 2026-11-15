package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

object InsightsCalculationEngine {

    private val zoneId = ZoneId.systemDefault()
    private val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private const val NIGHTLY_SNAPSHOT_HOUR_CUTOFF = 22 // 10:00 PM
    private const val MORNING_RECOVERY_HOUR_CUTOFF = 12 // 12:00 PM (Noon)

    fun computeUiState(
        cars: List<Car>,
        allLogs: List<OdometerLogEntity>,
        granularity: InsightsTimeGranularity,
        selectedCarId: String?,
        gasPrice: Float,
        baselineMpg: Float,
        periodOffset: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        utilityBills: List<UtilityBillEntity> = emptyList(),
        chargingSessions: List<ChargingSessionEntity> = emptyList(),
        defaultElectricityRate: Float = 0.16f
    ): InsightsUiState {
        val activeCars = cars.filter { !it.isArchived }.ifEmpty { cars }
        val sortedBills = utilityBills.sortedByDescending { it.endDateMs }
        val sortedSessions = chargingSessions.sortedByDescending { it.startDateMs }
        val intervals = generateIntervals(granularity, periodOffset, nowMs)

        if (activeCars.isEmpty() || allLogs.isEmpty()) {
            val car1 = activeCars.firstOrNull()
            val car2 = activeCars.getOrNull(1)
            val emptyTelemetryResult = computeElectricityCost(
                activeCars = activeCars,
                car1 = car1,
                car2 = car2,
                car1Miles = 0,
                car2Miles = 0,
                selectedCarId = selectedCarId,
                intervals = intervals,
                utilityBills = utilityBills,
                chargingSessions = chargingSessions,
                defaultElectricityRate = defaultElectricityRate
            )
            val earliestSessionMs = chargingSessions.minOfOrNull { it.startDateMs }
            val hasElectricityHistory = chargingSessions.isNotEmpty() || utilityBills.isNotEmpty()
            return InsightsUiState(
                selectedCarId = selectedCarId,
                granularity = granularity,
                car1Id = car1?.id,
                car1Name = car1?.model ?: car1?.displayName ?: "",
                car2Id = car2?.id,
                car2Name = car2?.model ?: car2?.displayName ?: "",
                gasPrice = gasPrice,
                baselineMpg = baselineMpg,
                hasOdometerData = false,
                observationWindowText = "No odometer readings logged yet",
                canGoPrevious = earliestSessionMs != null && intervals.isNotEmpty() && earliestSessionMs < intervals.first().startMs,
                canGoNext = hasElectricityHistory && periodOffset < 0,
                periodOffset = periodOffset,
                electricityCost = emptyTelemetryResult,
                defaultElectricityRate = defaultElectricityRate,
                utilityBills = sortedBills,
                chargingSessions = sortedSessions
            )
        }

        // Strictly filter to verified telemetry ping logs only (exclude maintenance and fake/seeded entries)
        val telemetryLogs = allLogs.filter {
            it.source != "MAINTENANCE" &&
            it.source != "INITIAL" &&
            !it.id.startsWith("rec_") &&
            !it.id.startsWith("car_odo_")
        }

        val sortedCars = if (selectedCarId != null) {
            val selected = activeCars.find { it.id == selectedCarId }
            val remainder = activeCars.filter { it.id != selectedCarId }
            listOfNotNull(selected) + remainder
        } else if (activeCars.size <= 2) {
            activeCars
        } else {
            // More than 2 cars in fleet: prioritize cars with active telemetry and recent odometer activity
            activeCars.sortedWith(
                compareByDescending<Car> { !it.telemetryProvider.isNullOrBlank() }
                    .thenByDescending { car ->
                        telemetryLogs.filter { it.carId == car.id }.maxOfOrNull { it.timestamp }
                            ?: car.lastOdometerSyncTime
                            ?: 0L
                    }
                    .thenByDescending { car ->
                        telemetryLogs.count { it.carId == car.id }
                    }
            )
        }

        val car1 = sortedCars.firstOrNull()
        val car2 = if (selectedCarId != null) null else sortedCars.getOrNull(1)

        val car1Logs = if (car1 != null) telemetryLogs.filter { it.carId == car1.id }.sortedBy { it.timestamp } else emptyList()
        val car2Logs = if (car2 != null) telemetryLogs.filter { it.carId == car2.id }.sortedBy { it.timestamp } else emptyList()

        val car1HasData = car1Logs.size >= 2
        val car2HasData = car2Logs.size >= 2

        val hasSufficientLogs = when (selectedCarId) {
            null -> car1HasData || car2HasData
            car1?.id -> car1HasData
            car2?.id -> car2HasData
            else -> car1HasData || car2HasData
        }

        if (!hasSufficientLogs) {
            val relevantLogs = when (selectedCarId) {
                null -> telemetryLogs
                car1?.id -> car1Logs
                car2?.id -> car2Logs
                else -> telemetryLogs
            }
            val singleTs = relevantLogs.firstOrNull()?.timestamp ?: nowMs
            val singleDateStr = Instant.ofEpochMilli(singleTs).atZone(zoneId).toLocalDate().format(shortDateFormatter)
            val windowMsg = if (relevantLogs.isEmpty()) {
                "No telemetry readings logged yet (waiting for first vehicle sync)"
            } else {
                "Initial sync logged on $singleDateStr (more readings needed to compute driving activity)"
            }
            val elecResult = computeElectricityCost(
                activeCars = activeCars,
                car1 = car1,
                car2 = car2,
                car1Miles = 0,
                car2Miles = 0,
                selectedCarId = selectedCarId,
                intervals = intervals,
                utilityBills = utilityBills,
                chargingSessions = chargingSessions,
                defaultElectricityRate = defaultElectricityRate
            )
            val earliestSessionMs = chargingSessions.minOfOrNull { it.startDateMs }
            val hasElectricityHistory = chargingSessions.isNotEmpty() || utilityBills.isNotEmpty()
            return InsightsUiState(
                selectedCarId = selectedCarId,
                granularity = granularity,
                car1Id = car1?.id,
                car1Name = car1?.model?.ifBlank { car1.displayName } ?: "",
                car2Id = car2?.id,
                car2Name = car2?.model?.ifBlank { car2.displayName } ?: "",
                gasPrice = gasPrice,
                baselineMpg = baselineMpg,
                hasOdometerData = false,
                observationWindowText = windowMsg,
                canGoPrevious = earliestSessionMs != null && intervals.isNotEmpty() && earliestSessionMs < intervals.first().startMs,
                canGoNext = hasElectricityHistory && periodOffset < 0,
                periodOffset = periodOffset,
                electricityCost = elecResult,
                defaultElectricityRate = defaultElectricityRate,
                utilityBills = sortedBills,
                chargingSessions = sortedSessions
            )
        }

        val eligibleLogs = when (selectedCarId) {
            null -> {
                val list = mutableListOf<OdometerLogEntity>()
                if (car1HasData) list.addAll(car1Logs)
                if (car2HasData) list.addAll(car2Logs)
                list
            }
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

        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()

        if (intervals.isEmpty()) {
            return InsightsUiState(
                selectedCarId = selectedCarId,
                granularity = granularity,
                car1Id = car1?.id,
                car1Name = car1?.model?.ifBlank { car1.displayName } ?: "",
                car2Id = car2?.id,
                car2Name = car2?.model?.ifBlank { car2.displayName } ?: "",
                gasPrice = gasPrice,
                baselineMpg = baselineMpg,
                hasOdometerData = true,
                observationWindowText = "No intervals in this period",
                canGoPrevious = false,
                canGoNext = periodOffset < 0,
                periodOffset = periodOffset,
                defaultElectricityRate = defaultElectricityRate,
                utilityBills = sortedBills,
                chargingSessions = sortedSessions
            )
        }

        val canGoNext = periodOffset < 0
        val earliestShownStartMs = intervals.first().startMs
        val earliestSessionMs = chargingSessions.minOfOrNull { it.startDateMs }
        val canGoPrevious = minTimestamp < earliestShownStartMs ||
            (earliestSessionMs != null && earliestSessionMs < earliestShownStartMs)

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
            null -> totalC1Miles + totalC2Miles
            car1?.id -> totalC1Miles
            car2?.id -> totalC2Miles
            else -> totalC1Miles + totalC2Miles
        }

        val totalFleetMiles = (totalC1Miles + totalC2Miles).coerceAtLeast(1)
        val car1Pct = when {
            car2 == null -> 100
            (totalC1Miles + totalC2Miles) == 0 -> 50
            else -> ((totalC1Miles.toFloat() / totalFleetMiles) * 100f).roundToInt().coerceIn(0, 100)
        }
        val car2Pct = if (car2 == null) 0 else (100 - car1Pct).coerceIn(0, 100)

        val daysCount = when (granularity) {
            InsightsTimeGranularity.WEEKLY -> {
                if (periodOffset == 0) {
                    now.dayOfWeek.value.toDouble().coerceAtLeast(1.0)
                } else {
                    7.0
                }
            }
            InsightsTimeGranularity.MONTHLY -> {
                val targetYm = YearMonth.from(now).plusMonths(periodOffset.toLong())
                if (periodOffset == 0) {
                    now.dayOfMonth.toDouble().coerceAtLeast(1.0)
                } else {
                    targetYm.lengthOfMonth().toDouble()
                }
            }
            InsightsTimeGranularity.YTD -> {
                val targetYear = now.year + periodOffset
                if (periodOffset == 0) {
                    now.dayOfYear.toDouble().coerceAtLeast(1.0)
                } else {
                    java.time.Year.of(targetYear).length().toDouble()
                }
            }
        }

        val car1Daily = (totalC1Miles / daysCount).toFloat()
        val car2Daily = (totalC2Miles / daysCount).toFloat()

        // Observation window text for Google Health header
        val windowText = formatObservationWindowText(granularity, periodOffset, now)

        val electricityCost = computeElectricityCost(
            activeCars = activeCars,
            car1 = car1,
            car2 = car2,
            car1Miles = totalC1Miles,
            car2Miles = totalC2Miles,
            selectedCarId = selectedCarId,
            intervals = intervals,
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            defaultElectricityRate = defaultElectricityRate
        )

        // Gas comparison
        val effectiveMpg = baselineMpg.coerceAtLeast(1.0f)
        val c1Gal = totalC1Miles / effectiveMpg.toDouble()
        val c1Cost = c1Gal * gasPrice.toDouble()
        val c2Gal = totalC2Miles / effectiveMpg.toDouble()
        val c2Cost = c2Gal * gasPrice.toDouble()

        val rC1Cost = (c1Cost * 100.0).roundToInt() / 100.0
        val rC2Cost = (c2Cost * 100.0).roundToInt() / 100.0

        val totalGallons = when (selectedCarId) {
            null -> c1Gal + c2Gal
            car1?.id -> c1Gal
            car2?.id -> c2Gal
            else -> c1Gal + c2Gal
        }
        val totalGasCost = when (selectedCarId) {
            null -> rC1Cost + rC2Cost
            car1?.id -> rC1Cost
            car2?.id -> rC2Cost
            else -> rC1Cost + rC2Cost
        }
        val co2Lbs = (totalGallons * 19.64).roundToInt()

        val netFuelSavings = computeNetFuelSavings(
            activeCars = activeCars,
            car1 = car1,
            car2 = car2,
            car1Miles = totalC1Miles,
            car2Miles = totalC2Miles,
            selectedCarId = selectedCarId,
            effectiveMpg = effectiveMpg,
            gasPrice = gasPrice,
            electricityCost = electricityCost
        )

        return InsightsUiState(
            selectedCarId = selectedCarId,
            granularity = granularity,
            totalMiles = filteredTotalMiles,
            fleetTotalMiles = totalFleetMiles,
            car1Id = car1?.id,
            car1Name = car1?.model?.ifBlank { car1.displayName } ?: "Vehicle 1",
            car1Miles = totalC1Miles,
            car1SharePct = car1Pct,
            car1DailyRate = car1Daily,
            car2Id = car2?.id,
            car2Name = car2?.model?.ifBlank { car2.displayName } ?: "Vehicle 2",
            car2Miles = totalC2Miles,
            car2SharePct = car2Pct,
            car2DailyRate = car2Daily,
            observationWindowText = windowText,
            activityBars = activityBars,
            gasComparison = GasComparisonData(
                totalCost = totalGasCost,
                totalGallons = totalGallons,
                co2Lbs = co2Lbs,
                car1Cost = rC1Cost,
                car1Gallons = c1Gal,
                car2Cost = rC2Cost,
                car2Gallons = c2Gal,
                electricityTotalCost = electricityCost.combinedTotalCost,
                netFuelSavings = netFuelSavings
            ),
            gasPrice = gasPrice,
            baselineMpg = baselineMpg,
            hasOdometerData = true,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            periodOffset = periodOffset,
            electricityCost = electricityCost,
            defaultElectricityRate = defaultElectricityRate,
            utilityBills = sortedBills,
            chargingSessions = sortedSessions
        )
    }

    fun formatObservationWindowText(
        granularity: InsightsTimeGranularity,
        periodOffset: Int,
        now: LocalDate = LocalDate.now(zoneId)
    ): String {
        return when (granularity) {
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
    }

    private data class ResolvedBillRate(
        val rate: Double,
        val isEstimated: Boolean,
        val matchedInRange: Boolean,
        val bill: UtilityBillEntity?
    )

    private fun resolveRateForTimestamp(
        targetMs: Long,
        utilityBills: List<UtilityBillEntity>,
        defaultElectricityRate: Float
    ): ResolvedBillRate {
        val targetDay = Instant.ofEpochMilli(targetMs).atZone(zoneId).toLocalDate()
        val matchingBills = utilityBills.filter { bill ->
            val billStart = Instant.ofEpochMilli(bill.startDateMs).atZone(zoneId).toLocalDate()
            val billEnd = Instant.ofEpochMilli(bill.endDateMs).atZone(zoneId).toLocalDate()
            !targetDay.isBefore(billStart) && !targetDay.isAfter(billEnd)
        }
        if (matchingBills.isNotEmpty()) {
            // Overlap rule: most recently uploaded bill takes precedence for matched dates
            val chosen = matchingBills.maxWithOrNull(compareBy<UtilityBillEntity> { it.uploadedAtMs }.thenBy { it.id })!!
            return ResolvedBillRate(
                rate = chosen.effectiveRate.coerceAtLeast(0.0),
                isEstimated = false,
                matchedInRange = true,
                bill = chosen
            )
        }

        val latestBill = utilityBills.maxWithOrNull(compareBy<UtilityBillEntity> { it.endDateMs }.thenBy { it.uploadedAtMs })
        if (latestBill != null) {
            return ResolvedBillRate(
                rate = latestBill.effectiveRate.coerceAtLeast(0.0),
                isEstimated = true,
                matchedInRange = false,
                bill = latestBill
            )
        }

        return ResolvedBillRate(
            rate = defaultElectricityRate.toDouble().coerceAtLeast(0.0),
            isEstimated = true,
            matchedInRange = false,
            bill = null
        )
    }

    private data class EnergySlice(
        val kwh: Double,
        val cost: Double,
        val carId: String?,
        val venue: String,
        val resolvedRate: ResolvedBillRate?
    )

    private fun computeElectricityCost(
        activeCars: List<Car>,
        car1: Car?,
        car2: Car?,
        car1Miles: Int,
        car2Miles: Int,
        selectedCarId: String?,
        intervals: List<DateInterval>,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        defaultElectricityRate: Float
    ): ElectricityCostData {
        val latestBill = utilityBills.maxWithOrNull(compareBy<UtilityBillEntity> { it.endDateMs }.thenBy { it.uploadedAtMs })
        val latestBillLabel = latestBill?.let { bill ->
            val period = ElectricityImportParser.formatPeriodShort(bill.startDateMs, bill.endDateMs, zoneId)
            val provider = bill.providerName.ifBlank { "Electric bill" }
            "$provider · $period"
        }

        if (intervals.isEmpty()) {
            val defaultRateVal = defaultElectricityRate.toDouble().coerceAtLeast(0.0)
            return ElectricityCostData(
                homeEffectiveRate = latestBill?.effectiveRate ?: defaultRateVal,
                homeRateSourceLabel = latestBillLabel ?: "Default est. ($${String.format(Locale.US, "%.2f", defaultRateVal)}/kWh)",
                latestBillEffectiveRate = latestBill?.effectiveRate,
                latestBillLabel = latestBillLabel
            )
        }

        val windowStartMs = intervals.first().startMs
        val windowEndMs = intervals.last().endMs
        val windowStartDay = Instant.ofEpochMilli(windowStartMs).atZone(zoneId).toLocalDate()
        val windowEndDay = Instant.ofEpochMilli(windowEndMs).atZone(zoneId).toLocalDate()
        val windowMidMs = (windowStartMs + windowEndMs) / 2

        // Build per-slice breakdown within the active observation window
        val homeSlices = mutableListOf<EnergySlice>()
        val publicSlices = mutableListOf<EnergySlice>()
        var homeSessionCountAllCars = 0
        var publicSessionCountAllCars = 0
        var homeSessionCountCar1 = 0
        var homeSessionCountCar2 = 0
        var publicSessionCountCar1 = 0
        var publicSessionCountCar2 = 0

        for (session in chargingSessions) {
            val venue = session.venue.uppercase(Locale.US).let { if (it == "PUBLIC") "PUBLIC" else "HOME" }
            if (session.isSummary) {
                val sStartDay = Instant.ofEpochMilli(session.startDateMs).atZone(zoneId).toLocalDate()
                val sEndDay = Instant.ofEpochMilli(session.endDateMs).atZone(zoneId).toLocalDate()
                val effectiveEndDay = if (sEndDay.isBefore(sStartDay)) sStartDay else sEndDay
                val totalDays = (ChronoUnit.DAYS.between(sStartDay, effectiveEndDay) + 1).coerceAtLeast(1)
                val overlapStart = maxOf(sStartDay, windowStartDay)
                val overlapEnd = minOf(effectiveEndDay, windowEndDay)
                if (overlapEnd.isBefore(overlapStart)) continue

                val dailyKwh = session.kwh / totalDays.toDouble()
                var overlaps = false
                var cursor = overlapStart
                while (!cursor.isAfter(overlapEnd)) {
                    overlaps = true
                    val dayMidMs = cursor.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
                    if (venue == "PUBLIC") {
                        val dailyCost = (session.directCost ?: 0.0) / totalDays.toDouble()
                        publicSlices.add(EnergySlice(dailyKwh, dailyCost, session.carId, venue, null))
                    } else {
                        val resolved = resolveRateForTimestamp(dayMidMs, utilityBills, defaultElectricityRate)
                        homeSlices.add(EnergySlice(dailyKwh, dailyKwh * resolved.rate, session.carId, venue, resolved))
                    }
                    cursor = cursor.plusDays(1)
                }
                if (overlaps) {
                    if (venue == "PUBLIC") publicSessionCountAllCars++ else homeSessionCountAllCars++
                    if (session.carId == null || session.carId == car1?.id) {
                        if (venue == "PUBLIC") publicSessionCountCar1++ else homeSessionCountCar1++
                    }
                    if (session.carId == null || session.carId == car2?.id) {
                        if (venue == "PUBLIC") publicSessionCountCar2++ else homeSessionCountCar2++
                    }
                }
            } else {
                val midMs = (session.startDateMs + session.endDateMs) / 2
                if (midMs !in windowStartMs..windowEndMs) continue

                if (venue == "PUBLIC") {
                    publicSessionCountAllCars++
                    if (session.carId == null || session.carId == car1?.id) publicSessionCountCar1++
                    if (session.carId == null || session.carId == car2?.id) publicSessionCountCar2++
                    publicSlices.add(EnergySlice(session.kwh, session.directCost ?: 0.0, session.carId, venue, null))
                } else {
                    homeSessionCountAllCars++
                    if (session.carId == null || session.carId == car1?.id) homeSessionCountCar1++
                    if (session.carId == null || session.carId == car2?.id) homeSessionCountCar2++
                    val resolved = resolveRateForTimestamp(midMs, utilityBills, defaultElectricityRate)
                    homeSlices.add(EnergySlice(session.kwh, session.kwh * resolved.rate, session.carId, venue, resolved))
                }
            }
        }

        // Compute shared-charger attribution weights only among EV-marked active cars
        val milesByCarId = buildMap {
            if (car1 != null) put(car1.id, car1Miles)
            if (car2 != null) put(car2.id, car2Miles)
        }
        val evCars = activeCars.filter { it.isEv }
        val totalEvMiles = evCars.sumOf { milesByCarId[it.id] ?: 0 }

        fun sharedWeightFor(carId: String): Double {
            if (evCars.none { it.id == carId }) return 0.0
            return if (totalEvMiles > 0) {
                (milesByCarId[carId] ?: 0).toDouble() / totalEvMiles.toDouble()
            } else {
                1.0 / evCars.size.toDouble()
            }
        }

        val c1SharedWeight = car1?.id?.let { sharedWeightFor(it) } ?: 0.0
        val c2SharedWeight = car2?.id?.let { sharedWeightFor(it) } ?: 0.0

        fun attributeSlices(slices: List<EnergySlice>): AttributedMetrics {
            var fleetKwh = 0.0
            var fleetCost = 0.0
            var c1Kwh = 0.0
            var c1Cost = 0.0
            var c2Kwh = 0.0
            var c2Cost = 0.0

            for (s in slices) {
                fleetKwh += s.kwh
                fleetCost += s.cost
                when (s.carId) {
                    null -> {
                        c1Kwh += s.kwh * c1SharedWeight
                        c1Cost += s.cost * c1SharedWeight
                        c2Kwh += s.kwh * c2SharedWeight
                        c2Cost += s.cost * c2SharedWeight
                    }
                    car1?.id -> {
                        c1Kwh += s.kwh
                        c1Cost += s.cost
                    }
                    car2?.id -> {
                        c2Kwh += s.kwh
                        c2Cost += s.cost
                    }
                }
            }
            return AttributedMetrics(fleetKwh, fleetCost, c1Kwh, c1Cost, c2Kwh, c2Cost)
        }

        val homeAttr = attributeSlices(homeSlices)
        val publicAttr = attributeSlices(publicSlices)

        val homeFilteredKwh = when (selectedCarId) {
            null -> homeAttr.fleetKwh
            car1?.id -> homeAttr.car1Kwh
            car2?.id -> homeAttr.car2Kwh
            else -> homeAttr.fleetKwh
        }
        val homeFilteredCost = when (selectedCarId) {
            null -> homeAttr.fleetCost
            car1?.id -> homeAttr.car1Cost
            car2?.id -> homeAttr.car2Cost
            else -> homeAttr.fleetCost
        }
        val publicFilteredKwh = when (selectedCarId) {
            null -> publicAttr.fleetKwh
            car1?.id -> publicAttr.car1Kwh
            car2?.id -> publicAttr.car2Kwh
            else -> publicAttr.fleetKwh
        }
        val publicFilteredCost = when (selectedCarId) {
            null -> publicAttr.fleetCost
            car1?.id -> publicAttr.car1Cost
            car2?.id -> publicAttr.car2Cost
            else -> publicAttr.fleetCost
        }

        // Miles for efficiency should include EVs (or cars that explicitly logged kWh)
        val evMilesInFleet = activeCars.filter { car ->
            car.isEv || (car.id == car1?.id && (homeAttr.car1Kwh + publicAttr.car1Kwh) > 0.0) ||
                (car.id == car2?.id && (homeAttr.car2Kwh + publicAttr.car2Kwh) > 0.0)
        }.sumOf { milesByCarId[it.id] ?: 0 }

        val milesForEfficiency = when (selectedCarId) {
            null -> evMilesInFleet
            car1?.id -> car1Miles
            car2?.id -> car2Miles
            else -> evMilesInFleet
        }

        val homeMilesPerKwh = if (milesForEfficiency > 0 && homeFilteredKwh > 0.0) milesForEfficiency / homeFilteredKwh else null
        val homeCostPerMile = if (milesForEfficiency > 0 && homeFilteredCost > 0.0) homeFilteredCost / milesForEfficiency else null

        val publicMilesPerKwh = if (milesForEfficiency > 0 && publicFilteredKwh > 0.0) milesForEfficiency / publicFilteredKwh else null
        val publicCostPerMile = if (milesForEfficiency > 0 && publicFilteredKwh > 0.0) publicFilteredCost / milesForEfficiency else null

        val combinedKwh = homeFilteredKwh + publicFilteredKwh
        val combinedCost = homeFilteredCost + publicFilteredCost
        val combinedMilesPerKwh = if (milesForEfficiency > 0 && combinedKwh > 0.0) milesForEfficiency / combinedKwh else null
        val combinedCostPerMile = if (milesForEfficiency > 0 && combinedKwh > 0.0) combinedCost / milesForEfficiency else null

        // Resolve displayed home rate source metadata
        val relevantHomeSlices = when (selectedCarId) {
            null -> homeSlices
            car1?.id -> homeSlices.filter { it.carId == null || it.carId == car1?.id }
            car2?.id -> homeSlices.filter { it.carId == null || it.carId == car2?.id }
            else -> homeSlices
        }
        val matchedBills = relevantHomeSlices.mapNotNull { it.resolvedRate?.takeIf { r -> r.matchedInRange }?.bill }.distinctBy { it.id }
        val anyUnmatchedInPeriod = relevantHomeSlices.any { it.resolvedRate?.isEstimated == true }
        val periodMidRate = resolveRateForTimestamp(windowMidMs, utilityBills, defaultElectricityRate)

        val displayHomeRate = if (homeFilteredKwh > 0.0) {
            homeFilteredCost / homeFilteredKwh
        } else {
            periodMidRate.rate
        }
        val homeIsEstimated = if (relevantHomeSlices.isNotEmpty()) anyUnmatchedInPeriod else periodMidRate.isEstimated
        val homeHasMatchedBill = if (relevantHomeSlices.isNotEmpty()) matchedBills.isNotEmpty() else periodMidRate.matchedInRange
        val homeRateSourceLabel = buildHomeRateSourceLabel(
            matchedBills = matchedBills,
            anyEstimated = homeIsEstimated,
            periodFallbackRate = periodMidRate,
            defaultElectricityRate = defaultElectricityRate
        )

        val homeAllocatedKwhSum = homeAttr.car1Kwh + homeAttr.car2Kwh
        val homeC1Pct = if (homeAllocatedKwhSum > 0.0) ((homeAttr.car1Kwh / homeAllocatedKwhSum) * 100.0).roundToInt().coerceIn(0, 100) else 0
        val homeC2Pct = if (homeAllocatedKwhSum > 0.0) (100 - homeC1Pct).coerceIn(0, 100) else 0

        val publicAllocatedKwhSum = publicAttr.car1Kwh + publicAttr.car2Kwh
        val publicC1Pct = if (publicAllocatedKwhSum > 0.0) ((publicAttr.car1Kwh / publicAllocatedKwhSum) * 100.0).roundToInt().coerceIn(0, 100) else 0
        val publicC2Pct = if (publicAllocatedKwhSum > 0.0) (100 - publicC1Pct).coerceIn(0, 100) else 0

        val effectiveHomeSessionCount = when (selectedCarId) {
            null -> homeSessionCountAllCars
            car1?.id -> homeSessionCountCar1
            car2?.id -> homeSessionCountCar2
            else -> homeSessionCountAllCars
        }
        val effectivePublicSessionCount = when (selectedCarId) {
            null -> publicSessionCountAllCars
            car1?.id -> publicSessionCountCar1
            car2?.id -> publicSessionCountCar2
            else -> publicSessionCountAllCars
        }

        val publicEffectiveRate = if (publicFilteredKwh > 0.0) publicFilteredCost / publicFilteredKwh else 0.0

        return ElectricityCostData(
            hasHomeData = homeFilteredKwh > 0.0 || effectiveHomeSessionCount > 0,
            hasPublicData = publicFilteredKwh > 0.0 || effectivePublicSessionCount > 0,
            hasAnyData = chargingSessions.isNotEmpty() || utilityBills.isNotEmpty(),
            homeTotalCost = homeFilteredCost,
            homeTotalKwh = homeFilteredKwh,
            homeMilesPerKwh = homeMilesPerKwh,
            homeCostPerMile = homeCostPerMile,
            homeEffectiveRate = displayHomeRate,
            homeRateSourceLabel = homeRateSourceLabel,
            homeIsEstimatedRate = homeIsEstimated,
            homeHasMatchedBill = homeHasMatchedBill,
            homeCar1Cost = homeAttr.car1Cost,
            homeCar1Kwh = homeAttr.car1Kwh,
            homeCar1SharePct = homeC1Pct,
            homeCar2Cost = homeAttr.car2Cost,
            homeCar2Kwh = homeAttr.car2Kwh,
            homeCar2SharePct = homeC2Pct,
            homeSessionCount = effectiveHomeSessionCount,
            publicTotalCost = publicFilteredCost,
            publicTotalKwh = publicFilteredKwh,
            publicMilesPerKwh = publicMilesPerKwh,
            publicCostPerMile = publicCostPerMile,
            publicEffectiveRate = publicEffectiveRate,
            publicRateSourceLabel = if (effectivePublicSessionCount == 1) "1 receipt total" else "$effectivePublicSessionCount receipt totals",
            publicCar1Cost = publicAttr.car1Cost,
            publicCar1Kwh = publicAttr.car1Kwh,
            publicCar1SharePct = publicC1Pct,
            publicCar2Cost = publicAttr.car2Cost,
            publicCar2Kwh = publicAttr.car2Kwh,
            publicCar2SharePct = publicC2Pct,
            publicSessionCount = effectivePublicSessionCount,
            combinedTotalCost = combinedCost,
            combinedTotalKwh = combinedKwh,
            combinedMilesPerKwh = combinedMilesPerKwh,
            combinedCostPerMile = combinedCostPerMile,
            latestBillEffectiveRate = latestBill?.effectiveRate,
            latestBillLabel = latestBillLabel
        )
    }

    private fun buildHomeRateSourceLabel(
        matchedBills: List<UtilityBillEntity>,
        anyEstimated: Boolean,
        periodFallbackRate: ResolvedBillRate,
        defaultElectricityRate: Float
    ): String {
        if (matchedBills.size == 1 && !anyEstimated) {
            val bill = matchedBills.first()
            val provider = bill.providerName.ifBlank { "Electric bill" }
            val period = ElectricityImportParser.formatPeriodShort(bill.startDateMs, bill.endDateMs, zoneId)
            return "$provider · $period"
        }
        if (matchedBills.size > 1 && !anyEstimated) {
            return "Matched to ${matchedBills.size} electric bills"
        }
        if (matchedBills.isNotEmpty()) {
            val primary = matchedBills.maxByOrNull { it.endDateMs }!!
            val provider = primary.providerName.ifBlank { "Electric bill" }
            return "$provider + est."
        }
        if (periodFallbackRate.matchedInRange && periodFallbackRate.bill != null) {
            val bill = periodFallbackRate.bill
            val provider = bill.providerName.ifBlank { "Electric bill" }
            val period = ElectricityImportParser.formatPeriodShort(bill.startDateMs, bill.endDateMs, zoneId)
            return "$provider · $period"
        }
        if (periodFallbackRate.bill != null) {
            val provider = periodFallbackRate.bill.providerName.ifBlank { "Latest bill" }
            return "Latest bill ($provider est.)"
        }
        return "Default est. ($${String.format(Locale.US, "%.2f", defaultElectricityRate)}/kWh)"
    }

    private fun computeNetFuelSavings(
        activeCars: List<Car>,
        car1: Car?,
        car2: Car?,
        car1Miles: Int,
        car2Miles: Int,
        selectedCarId: String?,
        effectiveMpg: Float,
        gasPrice: Float,
        electricityCost: ElectricityCostData
    ): Double? {
        val totalSessionsInWindow = electricityCost.homeSessionCount + electricityCost.publicSessionCount
        if (totalSessionsInWindow <= 0 || electricityCost.combinedTotalKwh <= 0.0) return null

        val evMilesForGasBaseline = when (selectedCarId) {
            null -> {
                val c1IsEv = car1?.isEv == true || (electricityCost.homeCar1Kwh + electricityCost.publicCar1Kwh) > 0.0
                val c2IsEv = car2?.isEv == true || (electricityCost.homeCar2Kwh + electricityCost.publicCar2Kwh) > 0.0
                val evMiles = (if (c1IsEv) car1Miles else 0) + (if (c2IsEv) car2Miles else 0)
                if (activeCars.none { it.isEv } && evMiles == 0) car1Miles + car2Miles else evMiles
            }
            car1?.id -> car1Miles
            car2?.id -> car2Miles
            else -> 0
        }
        if (evMilesForGasBaseline <= 0) return null

        val gasBaselineCostForEvs = (evMilesForGasBaseline / effectiveMpg.toDouble()) * gasPrice.toDouble()
        return gasBaselineCostForEvs - electricityCost.combinedTotalCost
    }

    private data class AttributedMetrics(
        val fleetKwh: Double,
        val fleetCost: Double,
        val car1Kwh: Double,
        val car1Cost: Double,
        val car2Kwh: Double,
        val car2Cost: Double
    )

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
        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()

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

    private fun normalizeSegmentBounds(rawT1: Long, rawT2: Long): Pair<Long, Long> {
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

            val rawT1 = l1.timestamp
            val rawT2 = l2.timestamp
            val deltaOdo = (l2.odometer - l1.odometer).coerceAtLeast(0)

            if (deltaOdo <= 0 || rawT2 <= rawT1) continue

            val (t1, t2) = normalizeSegmentBounds(rawT1, rawT2)
            if (t2 <= t1) continue

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
