package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

object InsightsCalculationEngine {

    private val zoneId = ZoneId.systemDefault()

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
        defaultElectricityRate: Float = 0.16f,
        useCustomElectricityRate: Boolean = false
    ): InsightsUiState {
        val activeCars = cars.filter { !it.isArchived }.ifEmpty { cars }
        val sortedBills = utilityBills.sortedByDescending { it.endDateMs }
        val sortedSessions = chargingSessions.sortedByDescending { it.startDateMs }
        val intervals = InsightsDateIntervals.generateIntervals(granularity, periodOffset, nowMs, zoneId)

        if (activeCars.isEmpty() || allLogs.isEmpty()) {
            val car1 = activeCars.firstOrNull()
            val car2 = activeCars.getOrNull(1)
            return InsightsStateFallbacks.buildEmptyOrInsufficientState(
                activeCars = activeCars, car1 = car1, car2 = car2,
                car1Name = car1?.model ?: car1?.displayName ?: "",
                car2Name = car2?.model ?: car2?.displayName ?: "",
                observationWindowText = "No odometer readings logged yet",
                granularity = granularity, selectedCarId = selectedCarId, gasPrice = gasPrice,
                baselineMpg = baselineMpg, periodOffset = periodOffset, intervals = intervals,
                utilityBills = utilityBills, chargingSessions = chargingSessions,
                sortedBills = sortedBills, sortedSessions = sortedSessions,
                defaultElectricityRate = defaultElectricityRate,
                useCustomElectricityRate = useCustomElectricityRate, zoneId = zoneId
            )
        }

        val telemetryLogs = allLogs.filter {
            it.source != "MAINTENANCE" &&
                it.source != "INITIAL" &&
                !it.id.startsWith("rec_") &&
                !it.id.startsWith("car_odo_")
        }

        val sortedCars = InsightsStateFallbacks.prioritizeActiveCars(activeCars, telemetryLogs, selectedCarId)
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
            return InsightsStateFallbacks.buildInsufficientLogsState(
                activeCars = activeCars, car1 = car1, car2 = car2,
                car1Logs = car1Logs, car2Logs = car2Logs, telemetryLogs = telemetryLogs,
                granularity = granularity, selectedCarId = selectedCarId, gasPrice = gasPrice,
                baselineMpg = baselineMpg, periodOffset = periodOffset, nowMs = nowMs,
                intervals = intervals, utilityBills = utilityBills, chargingSessions = chargingSessions,
                sortedBills = sortedBills, sortedSessions = sortedSessions,
                defaultElectricityRate = defaultElectricityRate,
                useCustomElectricityRate = useCustomElectricityRate, zoneId = zoneId
            )
        }

        val eligibleLogs = when (selectedCarId) {
            null -> buildList {
                if (car1HasData) addAll(car1Logs)
                if (car2HasData) addAll(car2Logs)
            }
            car1?.id -> car1Logs
            car2?.id -> car2Logs
            else -> buildList {
                if (car1HasData) addAll(car1Logs)
                if (car2HasData) addAll(car2Logs)
            }
        }

        val minTimestamp = eligibleLogs.minOf { it.timestamp }
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
                useCustomElectricityRate = useCustomElectricityRate,
                utilityBills = sortedBills,
                chargingSessions = sortedSessions
            )
        }

        val earliestShownStartMs = intervals.first().startMs
        val earliestSessionMs = chargingSessions.minOfOrNull { it.startDateMs }
        val canGoPrevious = minTimestamp < earliestShownStartMs ||
            (earliestSessionMs != null && earliestSessionMs < earliestShownStartMs)

        val activityBars = intervals.map { interval ->
            val c1Miles = if (car1 != null) InsightsDateIntervals.calculateMilesInWindow(car1Logs, interval.startMs, interval.endMs, zoneId) else 0
            val c2Miles = if (car2 != null) InsightsDateIntervals.calculateMilesInWindow(car2Logs, interval.startMs, interval.endMs, zoneId) else 0
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
            InsightsTimeGranularity.WEEKLY -> if (periodOffset == 0) now.dayOfWeek.value.toDouble().coerceAtLeast(1.0) else 7.0
            InsightsTimeGranularity.MONTHLY -> {
                val targetYm = YearMonth.from(now).plusMonths(periodOffset.toLong())
                if (periodOffset == 0) now.dayOfMonth.toDouble().coerceAtLeast(1.0) else targetYm.lengthOfMonth().toDouble()
            }
            InsightsTimeGranularity.YTD -> {
                val targetYear = now.year + periodOffset
                if (periodOffset == 0) now.dayOfYear.toDouble().coerceAtLeast(1.0) else Year.of(targetYear).length().toDouble()
            }
        }

        val allEvMilesByCarId = if (selectedCarId == null) {
            buildMap {
                if (car1 != null) put(car1.id, totalC1Miles)
                if (car2 != null) put(car2.id, totalC2Miles)
            }
        } else {
            activeCars.filter { it.isEv }.associate { ev ->
                val evLogs = telemetryLogs.filter { it.carId == ev.id }.sortedBy { it.timestamp }
                val miles = intervals.sumOf { InsightsDateIntervals.calculateMilesInWindow(evLogs, it.startMs, it.endMs, zoneId) }
                ev.id to miles
            }
        }

        val electricityCost = ElectricityCostCalculator.computeElectricityCost(
            activeCars = activeCars,
            car1 = car1,
            car2 = car2,
            car1Miles = totalC1Miles,
            car2Miles = totalC2Miles,
            selectedCarId = selectedCarId,
            intervals = intervals,
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            evMilesByCarId = allEvMilesByCarId,
            zoneId = zoneId
        )

        val effectiveMpg = baselineMpg.coerceAtLeast(1.0f)
        val c1Gal = totalC1Miles / effectiveMpg.toDouble()
        val c2Gal = totalC2Miles / effectiveMpg.toDouble()
        val rC1Cost = (c1Gal * gasPrice.toDouble() * 100.0).roundToInt() / 100.0
        val rC2Cost = (c2Gal * gasPrice.toDouble() * 100.0).roundToInt() / 100.0
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

        val netFuelSavings = FuelSavingsCalculator.computeNetFuelSavings(
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
            car1DailyRate = (totalC1Miles / daysCount).toFloat(),
            car2Id = car2?.id,
            car2Name = car2?.model?.ifBlank { car2.displayName } ?: "Vehicle 2",
            car2Miles = totalC2Miles,
            car2SharePct = car2Pct,
            car2DailyRate = (totalC2Miles / daysCount).toFloat(),
            observationWindowText = formatObservationWindowText(granularity, periodOffset, now),
            activityBars = activityBars,
            gasComparison = GasComparisonData(
                totalCost = totalGasCost,
                totalGallons = totalGallons,
                co2Lbs = (totalGallons * 19.64).roundToInt(),
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
            canGoNext = periodOffset < 0,
            periodOffset = periodOffset,
            electricityCost = electricityCost,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            utilityBills = sortedBills,
            chargingSessions = sortedSessions
        )
    }

    fun formatObservationWindowText(
        granularity: InsightsTimeGranularity,
        periodOffset: Int,
        now: LocalDate = LocalDate.now(zoneId)
    ): String = InsightsDateIntervals.formatObservationWindowText(granularity, periodOffset, now)

    fun resolvePeriodBounds(
        granularity: InsightsTimeGranularity,
        periodOffset: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = this.zoneId
    ): Pair<Long, Long> = InsightsDateIntervals.resolvePeriodBounds(granularity, periodOffset, nowMs, zoneId)
}
