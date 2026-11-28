package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

internal object ElectricityCostCalculator {

    private data class AttributedMetrics(
        val fleetKwh: Double,
        val fleetCost: Double,
        val car1Kwh: Double,
        val car1Cost: Double,
        val car2Kwh: Double,
        val car2Cost: Double
    ) {
        fun filteredKwh(selectedCarId: String?, car1Id: String?, car2Id: String?): Double = when (selectedCarId) {
            null -> fleetKwh
            car1Id -> car1Kwh
            car2Id -> car2Kwh
            else -> fleetKwh
        }

        fun filteredCost(selectedCarId: String?, car1Id: String?, car2Id: String?): Double = when (selectedCarId) {
            null -> fleetCost
            car1Id -> car1Cost
            car2Id -> car2Cost
            else -> fleetCost
        }

        fun sharePcts(): Pair<Int, Int> {
            val allocatedSum = car1Kwh + car2Kwh
            val c1Pct = if (allocatedSum > 0.0) ((car1Kwh / allocatedSum) * 100.0).roundToInt().coerceIn(0, 100) else 0
            val c2Pct = if (allocatedSum > 0.0) (100 - c1Pct).coerceIn(0, 100) else 0
            return c1Pct to c2Pct
        }
    }

    fun computeElectricityCost(
        activeCars: List<Car>,
        car1: Car?,
        car2: Car?,
        car1Miles: Int,
        car2Miles: Int,
        selectedCarId: String?,
        intervals: List<DateInterval>,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean = false,
        evMilesByCarId: Map<String, Int> = emptyMap(),
        zoneId: ZoneId = ZoneId.systemDefault()
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
                homeEffectiveRate = if (useCustomElectricityRate) defaultRateVal else (latestBill?.effectiveRate ?: defaultRateVal),
                homeRateSourceLabel = if (useCustomElectricityRate) {
                    "Custom rate"
                } else {
                    latestBillLabel ?: "Default rate"
                },
                homeIsEstimatedRate = !useCustomElectricityRate && latestBill == null,
                homeIsCustomRate = useCustomElectricityRate,
                latestBillEffectiveRate = latestBill?.effectiveRate,
                latestBillLabel = latestBillLabel
            )
        }

        val windowStartMs = intervals.first().startMs
        val windowEndMs = intervals.last().endMs
        val windowStartDay = Instant.ofEpochMilli(windowStartMs).atZone(zoneId).toLocalDate()
        val windowEndDay = Instant.ofEpochMilli(windowEndMs).atZone(zoneId).toLocalDate()
        val windowMidMs = (windowStartMs + windowEndMs) / 2

        val milesByCarId = buildMap {
            if (car1 != null) put(car1.id, car1Miles)
            if (car2 != null) put(car2.id, car2Miles)
            putAll(evMilesByCarId)
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

        fun attributionForSelectedCar(sessionCarId: String?): Pair<Double, Boolean>? = when (selectedCarId) {
            null -> 1.0 to false
            car1?.id -> when (sessionCarId) {
                null -> c1SharedWeight to (c1SharedWeight < 0.9999)
                car1?.id -> 1.0 to false
                else -> null
            }
            car2?.id -> when (sessionCarId) {
                null -> c2SharedWeight to (c2SharedWeight < 0.9999)
                car2?.id -> 1.0 to false
                else -> null
            }
            else -> 1.0 to false
        }

        val orderedSessions = chargingSessions.sortedWith(
            compareByDescending<ChargingSessionEntity> { it.startDateMs }
                .thenByDescending { it.uploadedAtMs }
                .thenByDescending { it.id }
        )

        val accumulated = ElectricitySessionSlicer.sliceSessions(
            orderedSessions = orderedSessions,
            car1Id = car1?.id,
            car2Id = car2?.id,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            windowStartDay = windowStartDay,
            windowEndDay = windowEndDay,
            utilityBills = utilityBills,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            attributionForSelectedCar = ::attributionForSelectedCar,
            zoneId = zoneId
        )

        return combineVenueTotals(
            activeCars = activeCars,
            car1 = car1,
            car2 = car2,
            car1Miles = car1Miles,
            car2Miles = car2Miles,
            selectedCarId = selectedCarId,
            milesByCarId = milesByCarId,
            c1SharedWeight = c1SharedWeight,
            c2SharedWeight = c2SharedWeight,
            accumulated = accumulated,
            windowMidMs = windowMidMs,
            utilityBills = utilityBills,
            chargingSessions = chargingSessions,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            latestBill = latestBill,
            latestBillLabel = latestBillLabel,
            zoneId = zoneId
        )
    }

    private fun attributeSlices(
        slices: List<EnergySlice>,
        car1Id: String?,
        car2Id: String?,
        c1SharedWeight: Double,
        c2SharedWeight: Double
    ): AttributedMetrics {
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
                car1Id -> {
                    c1Kwh += s.kwh
                    c1Cost += s.cost
                }
                car2Id -> {
                    c2Kwh += s.kwh
                    c2Cost += s.cost
                }
            }
        }
        return AttributedMetrics(fleetKwh, fleetCost, c1Kwh, c1Cost, c2Kwh, c2Cost)
    }

    private fun combineVenueTotals(
        activeCars: List<Car>,
        car1: Car?,
        car2: Car?,
        car1Miles: Int,
        car2Miles: Int,
        selectedCarId: String?,
        milesByCarId: Map<String, Int>,
        c1SharedWeight: Double,
        c2SharedWeight: Double,
        accumulated: SliceAccumulationResult,
        windowMidMs: Long,
        utilityBills: List<UtilityBillEntity>,
        chargingSessions: List<ChargingSessionEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean,
        latestBill: UtilityBillEntity?,
        latestBillLabel: String?,
        zoneId: ZoneId
    ): ElectricityCostData {
        val homeAttr = attributeSlices(accumulated.home.slices, car1?.id, car2?.id, c1SharedWeight, c2SharedWeight)
        val workAttr = attributeSlices(accumulated.work.slices, car1?.id, car2?.id, c1SharedWeight, c2SharedWeight)
        val publicAttr = attributeSlices(accumulated.publicVenue.slices, car1?.id, car2?.id, c1SharedWeight, c2SharedWeight)

        val homeFilteredKwh = homeAttr.filteredKwh(selectedCarId, car1?.id, car2?.id)
        val homeFilteredCost = homeAttr.filteredCost(selectedCarId, car1?.id, car2?.id)
        val workFilteredKwh = workAttr.filteredKwh(selectedCarId, car1?.id, car2?.id)
        val workFilteredAvoidedCost = workAttr.filteredCost(selectedCarId, car1?.id, car2?.id)
        val publicFilteredKwh = publicAttr.filteredKwh(selectedCarId, car1?.id, car2?.id)
        val publicFilteredCost = publicAttr.filteredCost(selectedCarId, car1?.id, car2?.id)

        val evMilesInFleet = activeCars.filter { car ->
            car.isEv ||
                (car.id == car1?.id && (homeAttr.car1Kwh + workAttr.car1Kwh + publicAttr.car1Kwh) > 0.0) ||
                (car.id == car2?.id && (homeAttr.car2Kwh + workAttr.car2Kwh + publicAttr.car2Kwh) > 0.0)
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

        val combinedKwh = homeFilteredKwh + workFilteredKwh + publicFilteredKwh
        val combinedCost = homeFilteredCost + publicFilteredCost
        val combinedMilesPerKwh = if (milesForEfficiency > 0 && combinedKwh > 0.0) milesForEfficiency / combinedKwh else null
        val combinedCostPerMile = if (milesForEfficiency > 0 && combinedKwh > 0.0) combinedCost / milesForEfficiency else null

        val relevantHomeSlices = when (selectedCarId) {
            null -> accumulated.home.slices
            car1?.id -> accumulated.home.slices.filter { it.carId == null || it.carId == car1?.id }
            car2?.id -> accumulated.home.slices.filter { it.carId == null || it.carId == car2?.id }
            else -> accumulated.home.slices
        }
        val matchedBills = relevantHomeSlices.mapNotNull { it.resolvedRate?.takeIf { r -> r.matchedInRange }?.bill }.distinctBy { it.id }
        val anyUnmatchedInPeriod = relevantHomeSlices.any { it.resolvedRate?.isEstimated == true }
        val periodMidRate = UtilityRateResolver.resolveRateForTimestamp(
            targetMs = windowMidMs,
            utilityBills = utilityBills,
            defaultElectricityRate = defaultElectricityRate,
            useCustomElectricityRate = useCustomElectricityRate,
            zoneId = zoneId
        )

        val displayHomeRate = when {
            useCustomElectricityRate -> defaultElectricityRate.toDouble().coerceAtLeast(0.0)
            homeFilteredKwh > 0.0 -> homeFilteredCost / homeFilteredKwh
            else -> periodMidRate.rate
        }
        val homeIsEstimated = when {
            useCustomElectricityRate -> false
            relevantHomeSlices.isNotEmpty() -> anyUnmatchedInPeriod
            else -> periodMidRate.isEstimated
        }
        val homeHasMatchedBill = when {
            useCustomElectricityRate -> false
            relevantHomeSlices.isNotEmpty() -> matchedBills.isNotEmpty()
            else -> periodMidRate.matchedInRange
        }
        val homeRateSourceLabel = UtilityRateResolver.buildHomeRateSourceLabel(
            matchedBills = matchedBills,
            anyEstimated = homeIsEstimated,
            periodFallbackRate = periodMidRate,
            useCustomElectricityRate = useCustomElectricityRate,
            zoneId = zoneId
        )

        val (homeC1Pct, homeC2Pct) = homeAttr.sharePcts()
        val (workC1Pct, workC2Pct) = workAttr.sharePcts()
        val (publicC1Pct, publicC2Pct) = publicAttr.sharePcts()

        fun effectiveCount(state: VenueSliceState): Int = when (selectedCarId) {
            null -> state.sessionCountAllCars
            car1?.id -> state.sessionCountCar1
            car2?.id -> state.sessionCountCar2
            else -> state.sessionCountAllCars
        }

        val effectiveHomeSessionCount = effectiveCount(accumulated.home)
        val effectiveWorkSessionCount = effectiveCount(accumulated.work)
        val effectivePublicSessionCount = effectiveCount(accumulated.publicVenue)
        val publicEffectiveRate = if (publicFilteredKwh > 0.0) publicFilteredCost / publicFilteredKwh else 0.0

        return ElectricityCostData(
            hasHomeData = homeFilteredKwh > 0.0 || effectiveHomeSessionCount > 0,
            hasWorkData = workFilteredKwh > 0.0 || effectiveWorkSessionCount > 0,
            hasPublicData = publicFilteredKwh > 0.0 || effectivePublicSessionCount > 0,
            hasAnyData = chargingSessions.isNotEmpty() || utilityBills.isNotEmpty(),
            homeTotalCost = homeFilteredCost,
            homeTotalKwh = homeFilteredKwh,
            homeMilesPerKwh = homeMilesPerKwh,
            homeCostPerMile = homeCostPerMile,
            homeEffectiveRate = displayHomeRate,
            homeRateSourceLabel = homeRateSourceLabel,
            homeIsEstimatedRate = homeIsEstimated,
            homeIsCustomRate = useCustomElectricityRate,
            homeHasMatchedBill = homeHasMatchedBill,
            homeCar1Cost = homeAttr.car1Cost,
            homeCar1Kwh = homeAttr.car1Kwh,
            homeCar1SharePct = homeC1Pct,
            homeCar2Cost = homeAttr.car2Cost,
            homeCar2Kwh = homeAttr.car2Kwh,
            homeCar2SharePct = homeC2Pct,
            homeSessionCount = effectiveHomeSessionCount,
            homePeriodLogs = accumulated.home.periodLogs,
            workTotalKwh = workFilteredKwh,
            workAvoidedHomeCost = workFilteredAvoidedCost,
            workCar1Kwh = workAttr.car1Kwh,
            workCar1SharePct = workC1Pct,
            workCar2Kwh = workAttr.car2Kwh,
            workCar2SharePct = workC2Pct,
            workSessionCount = effectiveWorkSessionCount,
            workPeriodLogs = accumulated.work.periodLogs,
            publicTotalCost = publicFilteredCost,
            publicTotalKwh = publicFilteredKwh,
            publicMilesPerKwh = publicMilesPerKwh,
            publicCostPerMile = publicCostPerMile,
            publicEffectiveRate = publicEffectiveRate,
            publicRateSourceLabel = if (effectivePublicSessionCount == 1) "1 session" else "$effectivePublicSessionCount sessions",
            publicCar1Cost = publicAttr.car1Cost,
            publicCar1Kwh = publicAttr.car1Kwh,
            publicCar1SharePct = publicC1Pct,
            publicCar2Cost = publicAttr.car2Cost,
            publicCar2Kwh = publicAttr.car2Kwh,
            publicCar2SharePct = publicC2Pct,
            publicSessionCount = effectivePublicSessionCount,
            publicPeriodLogs = accumulated.publicVenue.periodLogs,
            combinedTotalCost = combinedCost,
            combinedTotalKwh = combinedKwh,
            combinedMilesPerKwh = combinedMilesPerKwh,
            combinedCostPerMile = combinedCostPerMile,
            latestBillEffectiveRate = latestBill?.effectiveRate,
            latestBillLabel = latestBillLabel
        )
    }
}
