package com.example.car_logger.ui.screens.vehicledetails.components

import com.example.car_logger.data.insights.DrivingActivityBar
import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.services.ElectricityImportParser
import kotlin.math.roundToInt

enum class VehicleBentoTile {
    PACE,
    EFFICIENCY,
    SAVINGS,
    TCO
}

data class VehicleBentoPeriodMetrics(
    val granularity: InsightsTimeGranularity,
    val periodLabel: String,
    val windowText: String,
    val hasOdometerData: Boolean = false,
    val totalMiles: Int = 0,
    val dailyRate: Float = 0f,
    val fleetSharePct: Int = 100,
    val activityBars: List<DrivingActivityBar> = emptyList(),
    val hasChargingData: Boolean = false,
    val milesPerKwh: Double? = null,
    val energyCostPerMile: Double? = null,
    val totalKwh: Double = 0.0,
    val totalElectricityCost: Double = 0.0,
    val homeKwh: Double = 0.0,
    val homeCost: Double = 0.0,
    val homePct: Int = 0,
    val homeEffectiveRate: Double = 0.16,
    val workKwh: Double = 0.0,
    val workPct: Int = 0,
    val workAvoidedCost: Double = 0.0,
    val publicKwh: Double = 0.0,
    val publicCost: Double = 0.0,
    val publicPct: Int = 0,
    val publicEffectiveRate: Double = 0.0,
    val gasEquivalentCost: Double = 0.0,
    val gasGallonsSaved: Double = 0.0,
    val netFuelSavings: Double? = null,
    val co2AvoidedLbs: Int = 0,
    val gasPrice: Float = 3.65f,
    val baselineMpg: Float = 28.0f
)

data class VehicleInsightsBentoState(
    val weekly: VehicleBentoPeriodMetrics = VehicleBentoPeriodMetrics(
        granularity = InsightsTimeGranularity.WEEKLY,
        periodLabel = "7D",
        windowText = ""
    ),
    val monthly: VehicleBentoPeriodMetrics = VehicleBentoPeriodMetrics(
        granularity = InsightsTimeGranularity.MONTHLY,
        periodLabel = "30D",
        windowText = ""
    ),
    val lifetimeMaintenanceCost: Double = 0.0,
    val lifetimeChargingCost: Double = 0.0,
    val lifetimeTotalCost: Double = 0.0,
    val lifetimeMaintenanceCostPerMile: Double? = null,
    val lifetimeChargingCostPerMile: Double? = null,
    val lifetimeAllInCostPerMile: Double? = null,
    val maintenanceSharePct: Int = 100,
    val chargingSharePct: Int = 0,
    val trackedMiles: Int = 0
) {
    fun metricsFor(granularity: InsightsTimeGranularity): VehicleBentoPeriodMetrics {
        return when (granularity) {
            InsightsTimeGranularity.MONTHLY -> monthly
            else -> weekly
        }
    }
}

object VehicleInsightsBentoCalculator {

    fun compute(
        targetCar: Car?,
        allCars: List<Car>,
        allLogs: List<OdometerLogEntity>,
        records: List<MaintenanceRecord>,
        costHistory: VehicleCostHistory,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        gasPrice: Float,
        baselineMpg: Float,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): VehicleInsightsBentoState {
        if (targetCar == null) return VehicleInsightsBentoState()

        val activeCars = allCars.filter { !it.isArchived }.ifEmpty { listOf(targetCar) }
        val carsWithTarget = if (activeCars.any { it.id == targetCar.id }) {
            activeCars
        } else {
            listOf(targetCar) + activeCars
        }

        val weeklyPeriod = computePeriodMetrics(
            targetCar = targetCar,
            cars = carsWithTarget,
            allLogs = allLogs,
            granularity = InsightsTimeGranularity.WEEKLY,
            periodLabel = "7D",
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            gasPrice = gasPrice,
            baselineMpg = baselineMpg,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            nowMs = nowMs
        )

        val monthlyPeriod = computePeriodMetrics(
            targetCar = targetCar,
            cars = carsWithTarget,
            allLogs = allLogs,
            granularity = InsightsTimeGranularity.MONTHLY,
            periodLabel = "30D",
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            gasPrice = gasPrice,
            baselineMpg = baselineMpg,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            nowMs = nowMs
        )

        val lifetimeMaintCost = costHistory.lifetimeTotalCost
        val lifetimeChargingCost = computeLifetimeChargingCostForCar(
            targetCar = targetCar,
            activeCars = carsWithTarget,
            allLogs = allLogs,
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate
        )

        val lifetimeTotalCost = lifetimeMaintCost + lifetimeChargingCost

        // Compute tracked miles for lifetime $/mi calculation
        val validMaintRecords = records.filter { it.cost != null && it.cost > 0.0 && it.odometer > 0 }
        val carTelemetryOdos = allLogs
            .filter { it.carId == targetCar.id && it.odometer > 0 }
            .map { it.odometer }
        val candidateMinOdos = (validMaintRecords.map { it.odometer } + carTelemetryOdos).filter { it > 0 }
        val minRecordedOdo = candidateMinOdos.minOrNull()
        val currentOdo = targetCar.odometer
            ?: candidateMinOdos.maxOrNull()
            ?: 0

        val trackedMiles = when {
            minRecordedOdo != null && currentOdo > minRecordedOdo -> currentOdo - minRecordedOdo
            currentOdo > 0 -> currentOdo
            else -> 0
        }

        val maintCpm = costHistory.lifetimeCostPerMile
            ?: if (trackedMiles > 0 && lifetimeMaintCost > 0.0) lifetimeMaintCost / trackedMiles.toDouble() else null
        val chargingCpm = if (trackedMiles > 0 && lifetimeChargingCost > 0.0) {
            lifetimeChargingCost / trackedMiles.toDouble()
        } else {
            monthlyPeriod.energyCostPerMile ?: weeklyPeriod.energyCostPerMile
        }

        val allInCpm = when {
            maintCpm != null && chargingCpm != null -> maintCpm + chargingCpm
            maintCpm != null -> maintCpm
            chargingCpm != null -> chargingCpm
            trackedMiles > 0 && lifetimeTotalCost > 0.0 -> lifetimeTotalCost / trackedMiles.toDouble()
            else -> null
        }

        val maintPct = when {
            lifetimeTotalCost <= 0.0 -> 100
            else -> ((lifetimeMaintCost / lifetimeTotalCost) * 100.0).roundToInt().coerceIn(0, 100)
        }
        val chargingPct = if (lifetimeTotalCost <= 0.0) 0 else (100 - maintPct).coerceIn(0, 100)

        return VehicleInsightsBentoState(
            weekly = weeklyPeriod,
            monthly = monthlyPeriod,
            lifetimeMaintenanceCost = lifetimeMaintCost,
            lifetimeChargingCost = lifetimeChargingCost,
            lifetimeTotalCost = lifetimeTotalCost,
            lifetimeMaintenanceCostPerMile = maintCpm,
            lifetimeChargingCostPerMile = chargingCpm,
            lifetimeAllInCostPerMile = allInCpm,
            maintenanceSharePct = maintPct,
            chargingSharePct = chargingPct,
            trackedMiles = trackedMiles
        )
    }

    private fun computePeriodMetrics(
        targetCar: Car,
        cars: List<Car>,
        allLogs: List<OdometerLogEntity>,
        granularity: InsightsTimeGranularity,
        periodLabel: String,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        gasPrice: Float,
        baselineMpg: Float,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        nowMs: Long
    ): VehicleBentoPeriodMetrics {
        // Car-scoped insights state (selectedCarId = targetCar.id)
        val carState = InsightsCalculationEngine.computeUiState(
            cars = cars,
            allLogs = allLogs,
            granularity = granularity,
            selectedCarId = targetCar.id,
            gasPrice = gasPrice,
            baselineMpg = baselineMpg,
            periodOffset = 0,
            nowMs = nowMs,
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate
        )

        // Fleet-wide insights state to compute this vehicle's true share of garage miles in the period
        val fleetMilesInPeriod = cars.sumOf { car ->
            if (car.id == targetCar.id) {
                carState.totalMiles
            } else {
                InsightsCalculationEngine.computeUiState(
                    cars = cars,
                    allLogs = allLogs,
                    granularity = granularity,
                    selectedCarId = car.id,
                    gasPrice = gasPrice,
                    baselineMpg = baselineMpg,
                    periodOffset = 0,
                    nowMs = nowMs,
                    utilityBills = utilityBills,
                    chargingSessions = chargingSessions,
                    defaultElectricityRate = defaultElectricityRate,
                    useCustomElectricityRate = useCustomElectricityRate
                ).totalMiles
            }
        }

        val sharePct = when {
            cars.size <= 1 -> 100
            fleetMilesInPeriod <= 0 -> (100.0 / cars.size.toDouble()).roundToInt()
            else -> ((carState.totalMiles.toDouble() / fleetMilesInPeriod.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        }

        val elec = carState.electricityCost
        val totalKwh = elec.combinedTotalKwh
        val (homePct, workPct, publicPct) = if (totalKwh > 0.0) {
            val h = ((elec.homeTotalKwh / totalKwh) * 100.0).roundToInt().coerceIn(0, 100)
            if (elec.workTotalKwh <= 0.0) {
                Triple(h, 0, (100 - h).coerceIn(0, 100))
            } else if (elec.publicTotalKwh <= 0.0) {
                Triple(h, (100 - h).coerceIn(0, 100), 0)
            } else {
                val w = ((elec.workTotalKwh / totalKwh) * 100.0).roundToInt().coerceIn(0, 100 - h)
                val p = (100 - h - w).coerceIn(0, 100)
                Triple(h, w, p)
            }
        } else {
            Triple(0, 0, 0)
        }

        return VehicleBentoPeriodMetrics(
            granularity = granularity,
            periodLabel = periodLabel,
            windowText = InsightsCalculationEngine.formatObservationWindowText(granularity, 0),
            hasOdometerData = carState.hasOdometerData,
            totalMiles = carState.totalMiles,
            dailyRate = carState.car1DailyRate,
            fleetSharePct = sharePct,
            activityBars = carState.activityBars,
            hasChargingData = elec.hasHomeData || elec.hasWorkData || elec.hasPublicData || totalKwh > 0.0,
            milesPerKwh = elec.combinedMilesPerKwh ?: elec.homeMilesPerKwh ?: elec.publicMilesPerKwh,
            energyCostPerMile = elec.combinedCostPerMile ?: elec.homeCostPerMile ?: elec.publicCostPerMile,
            totalKwh = totalKwh,
            totalElectricityCost = elec.combinedTotalCost,
            homeKwh = elec.homeTotalKwh,
            homeCost = elec.homeTotalCost,
            homePct = homePct,
            homeEffectiveRate = elec.homeEffectiveRate,
            workKwh = elec.workTotalKwh,
            workPct = workPct,
            workAvoidedCost = elec.workAvoidedHomeCost,
            publicKwh = elec.publicTotalKwh,
            publicCost = elec.publicTotalCost,
            publicPct = publicPct,
            publicEffectiveRate = elec.publicEffectiveRate,
            gasEquivalentCost = carState.gasComparison.totalCost,
            gasGallonsSaved = carState.gasComparison.totalGallons,
            netFuelSavings = carState.gasComparison.netFuelSavings,
            co2AvoidedLbs = carState.gasComparison.co2Lbs,
            gasPrice = gasPrice,
            baselineMpg = baselineMpg
        )
    }

    private fun computeLifetimeChargingCostForCar(
        targetCar: Car,
        activeCars: List<Car>,
        allLogs: List<OdometerLogEntity>,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean
    ): Double {
        if (chargingSessions.isEmpty()) return 0.0

        val evCars = activeCars.filter { it.isEv }
        val totalMilesByEv = evCars.associate { ev ->
            val logs = allLogs.filter { it.carId == ev.id }.sortedBy { it.timestamp }
            val spanMiles = if (logs.size >= 2) {
                (logs.last().odometer - logs.first().odometer).coerceAtLeast(0)
            } else {
                0
            }
            ev.id to spanMiles
        }
        val totalEvFleetMiles = totalMilesByEv.values.sum()
        val sharedWeight = when {
            !targetCar.isEv && chargingSessions.none { it.carId == targetCar.id } -> 0.0
            evCars.isEmpty() -> 1.0
            totalEvFleetMiles > 0 -> (totalMilesByEv[targetCar.id] ?: 0).toDouble() / totalEvFleetMiles.toDouble()
            evCars.any { it.id == targetCar.id } -> 1.0 / evCars.size.toDouble()
            else -> 0.0
        }

        val latestBillRate = utilityBills
            .maxWithOrNull(compareBy<UtilityBillEntity> { it.endDateMs }.thenBy { it.uploadedAtMs })
            ?.effectiveRate

        var totalAttributedCost = 0.0
        for (session in chargingSessions) {
            val multiplier = when (session.carId) {
                targetCar.id -> 1.0
                null -> sharedWeight
                else -> 0.0
            }
            if (multiplier <= 0.0) continue

            val rawSessionCost = when (ElectricityImportParser.normalizeVenue(session.venue)) {
                ElectricityImportParser.VENUE_WORK -> 0.0
                ElectricityImportParser.VENUE_PUBLIC -> session.directCost ?: 0.0
                else -> {
                    val midMs = (session.startDateMs + session.endDateMs) / 2
                    val rate = if (useCustomElectricityRate) {
                        defaultElectricityRate.toDouble()
                    } else {
                        val matchedBill = utilityBills
                            .filter { midMs in it.startDateMs..it.endDateMs }
                            .maxWithOrNull(compareBy<UtilityBillEntity> { it.uploadedAtMs }.thenBy { it.id })
                        matchedBill?.effectiveRate ?: latestBillRate ?: defaultElectricityRate.toDouble()
                    }
                    session.kwh * rate.coerceAtLeast(0.0)
                }
            }
            totalAttributedCost += rawSessionCost * multiplier
        }

        return totalAttributedCost
    }
}
