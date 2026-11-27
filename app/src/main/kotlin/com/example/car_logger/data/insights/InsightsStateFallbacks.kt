package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object InsightsStateFallbacks {

    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun prioritizeActiveCars(
        activeCars: List<Car>,
        telemetryLogs: List<OdometerLogEntity>,
        selectedCarId: String?
    ): List<Car> = if (selectedCarId != null) {
        val selected = activeCars.find { it.id == selectedCarId }
        val remainder = activeCars.filter { it.id != selectedCarId }
        listOfNotNull(selected) + remainder
    } else if (activeCars.size <= 2) {
        activeCars
    } else {
        activeCars.sortedWith(
            compareByDescending<Car> { !it.telemetryProvider.isNullOrBlank() }
                .thenByDescending { car ->
                    telemetryLogs.filter { it.carId == car.id }.maxOfOrNull { it.timestamp }
                        ?: car.lastOdometerSyncTime
                        ?: 0L
                }
                .thenByDescending { car -> telemetryLogs.count { it.carId == car.id } }
        )
    }

    fun buildInsufficientLogsState(
        activeCars: List<Car>,
        car1: Car?,
        car2: Car?,
        car1Logs: List<OdometerLogEntity>,
        car2Logs: List<OdometerLogEntity>,
        telemetryLogs: List<OdometerLogEntity>,
        granularity: InsightsTimeGranularity,
        selectedCarId: String?,
        gasPrice: Float,
        baselineMpg: Float,
        periodOffset: Int,
        nowMs: Long,
        intervals: List<DateInterval>,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        sortedBills: List<UtilityBillEntity>,
        sortedSessions: List<ChargingSessionEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        zoneId: ZoneId
    ): InsightsUiState {
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
        return buildEmptyOrInsufficientState(
            activeCars = activeCars,
            car1 = car1,
            car2 = car2,
            car1Name = car1?.model?.ifBlank { car1.displayName } ?: "",
            car2Name = car2?.model?.ifBlank { car2.displayName } ?: "",
            observationWindowText = windowMsg,
            granularity = granularity,
            selectedCarId = selectedCarId,
            gasPrice = gasPrice,
            baselineMpg = baselineMpg,
            periodOffset = periodOffset,
            intervals = intervals,
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            sortedBills = sortedBills,
            sortedSessions = sortedSessions,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            zoneId = zoneId
        )
    }

    fun buildEmptyOrInsufficientState(
        activeCars: List<Car>,
        car1: Car?,
        car2: Car?,
        car1Name: String,
        car2Name: String,
        observationWindowText: String,
        granularity: InsightsTimeGranularity,
        selectedCarId: String?,
        gasPrice: Float,
        baselineMpg: Float,
        periodOffset: Int,
        intervals: List<DateInterval>,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        sortedBills: List<UtilityBillEntity>,
        sortedSessions: List<ChargingSessionEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        zoneId: ZoneId
    ): InsightsUiState {
        val elecResult = ElectricityCostCalculator.computeElectricityCost(
            activeCars = activeCars, car1 = car1, car2 = car2, car1Miles = 0, car2Miles = 0,
            selectedCarId = selectedCarId, intervals = intervals, utilityBills = utilityBills,
            chargingSessions = chargingSessions, defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate, zoneId = zoneId
        )
        val earliestSessionMs = chargingSessions.minOfOrNull { it.startDateMs }
        val hasElectricityHistory = chargingSessions.isNotEmpty() || utilityBills.isNotEmpty()
        return InsightsUiState(
            selectedCarId = selectedCarId,
            granularity = granularity,
            car1Id = car1?.id,
            car1Name = car1Name,
            car2Id = car2?.id,
            car2Name = car2Name,
            gasPrice = gasPrice,
            baselineMpg = baselineMpg,
            hasOdometerData = false,
            observationWindowText = observationWindowText,
            canGoPrevious = earliestSessionMs != null && intervals.isNotEmpty() && earliestSessionMs < intervals.first().startMs,
            canGoNext = hasElectricityHistory && periodOffset < 0,
            periodOffset = periodOffset,
            electricityCost = elecResult,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            utilityBills = sortedBills,
            chargingSessions = sortedSessions
        )
    }
}
