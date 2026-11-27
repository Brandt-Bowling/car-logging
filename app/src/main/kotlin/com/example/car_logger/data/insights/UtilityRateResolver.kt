package com.example.car_logger.data.insights

import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ElectricityImportParser
import java.time.Instant
import java.time.ZoneId

internal data class ResolvedBillRate(
    val rate: Double,
    val isEstimated: Boolean,
    val matchedInRange: Boolean,
    val bill: UtilityBillEntity?
)

internal object UtilityRateResolver {

    fun resolveRateForTimestamp(
        targetMs: Long,
        utilityBills: List<UtilityBillEntity>,
        defaultElectricityRate: Float,
        useCustomElectricityRate: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ResolvedBillRate {
        if (useCustomElectricityRate) {
            return ResolvedBillRate(
                rate = defaultElectricityRate.toDouble().coerceAtLeast(0.0),
                isEstimated = false,
                matchedInRange = false,
                bill = null
            )
        }

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

    fun buildHomeRateSourceLabel(
        matchedBills: List<UtilityBillEntity>,
        anyEstimated: Boolean,
        periodFallbackRate: ResolvedBillRate,
        useCustomElectricityRate: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (useCustomElectricityRate) {
            return "Custom rate"
        }
        if (matchedBills.size == 1 && !anyEstimated) {
            val bill = matchedBills.first()
            val provider = bill.providerName.ifBlank { "Electric bill" }
            val period = ElectricityImportParser.formatPeriodShort(bill.startDateMs, bill.endDateMs, zoneId)
            return "$provider · $period"
        }
        if (matchedBills.size > 1 && !anyEstimated) {
            return "${matchedBills.size} bills"
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
            return "$provider (est.)"
        }
        return "Default rate"
    }
}
