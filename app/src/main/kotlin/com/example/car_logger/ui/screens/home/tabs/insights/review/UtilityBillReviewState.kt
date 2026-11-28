package com.example.car_logger.ui.screens.home.tabs.insights.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedUtilityBillDraft
import java.time.YearMonth
import java.util.Locale

fun formatRateForInput(rate: Double): String {
    val fourDecimals = String.format(Locale.US, "%.4f", rate)
    return if (fourDecimals.endsWith("0")) fourDecimals.dropLast(1) else fourDecimals
}

@Stable
class UtilityBillReviewState(
    initialDraft: ParsedUtilityBillDraft?,
    defaultStart: String,
    defaultEnd: String,
    initialTouOffPeakTotal: Double?
) {
    var providerText by mutableStateOf(initialDraft?.providerName?.ifBlank { "" } ?: "")
        private set

    var startDateText by mutableStateOf(initialDraft?.startDateIso?.ifBlank { defaultStart } ?: defaultStart)
        private set

    var endDateText by mutableStateOf(initialDraft?.endDateIso?.ifBlank { defaultEnd } ?: defaultEnd)
        private set

    var totalAmountText by mutableStateOf(
        initialDraft?.totalAmount?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.2f", it) } ?: ""
    )
        private set

    var fixedChargesText by mutableStateOf(
        initialDraft?.fixedCharges?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.2f", it) } ?: "0.00"
    )
        private set

    var totalKwhText by mutableStateOf(
        initialDraft?.totalKwh?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) } ?: ""
    )
        private set

    var offPeakSupplyText by mutableStateOf(
        initialDraft?.offPeakSupplyRate?.takeIf { it > 0.0 }?.let(::formatRateForInput) ?: ""
    )
        private set

    var distributionRateText by mutableStateOf(
        initialDraft?.distributionRatePerKwh?.takeIf { it >= 0.0 }?.let(::formatRateForInput) ?: ""
    )
        private set

    var useOverrideRate by mutableStateOf(initialDraft?.isTouPlan == true && initialTouOffPeakTotal != null)
        private set

    var overrideRateText by mutableStateOf(initialTouOffPeakTotal?.let(::formatRateForInput) ?: "")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val parsedAmount: Double get() = totalAmountText.toDoubleOrNull() ?: 0.0
    val parsedFixed: Double get() = fixedChargesText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val parsedKwh: Double get() = totalKwhText.toDoubleOrNull() ?: 0.0
    val volumetricCharges: Double get() = (parsedAmount - parsedFixed).coerceAtLeast(0.0)
    val calculatedRate: Double
        get() = ElectricityImportParser.calculateVariableBlendedRate(
            totalAmount = parsedAmount,
            fixedCharges = parsedFixed,
            totalKwh = parsedKwh
        )
    val parsedSupplyComponent: Double? get() = offPeakSupplyText.toDoubleOrNull()?.takeIf { it >= 0.0 }
    val parsedDistComponent: Double? get() = distributionRateText.toDoubleOrNull()?.takeIf { it >= 0.0 }
    val parsedOverride: Double? get() = if (useOverrideRate) overrideRateText.toDoubleOrNull() else null
    val effectivePreviewRate: Double get() = parsedOverride ?: calculatedRate

    fun updateProvider(value: String) {
        providerText = value
        errorMessage = null
    }

    fun updateStartDate(value: String) {
        startDateText = value
        errorMessage = null
    }

    fun updateEndDate(value: String) {
        endDateText = value
        errorMessage = null
    }

    fun updateTotalAmount(value: String) {
        totalAmountText = value
        errorMessage = null
    }

    fun updateFixedCharges(value: String) {
        fixedChargesText = value
        errorMessage = null
    }

    fun updateTotalKwh(value: String) {
        totalKwhText = value
        errorMessage = null
    }

    fun updateUseOverrideRate(enabled: Boolean) {
        useOverrideRate = enabled
    }

    fun updateOffPeakSupply(newVal: String) {
        offPeakSupplyText = newVal
        errorMessage = null
        val s = newVal.toDoubleOrNull()
        val d = distributionRateText.toDoubleOrNull() ?: 0.0
        if (s != null && s >= 0.0) {
            overrideRateText = formatRateForInput(s + d.coerceAtLeast(0.0))
        }
    }

    fun updateDistributionRate(newVal: String) {
        distributionRateText = newVal
        errorMessage = null
        val s = offPeakSupplyText.toDoubleOrNull() ?: 0.0
        val d = newVal.toDoubleOrNull()
        if (d != null && d >= 0.0) {
            overrideRateText = formatRateForInput(s.coerceAtLeast(0.0) + d)
        } else if (newVal.isBlank() && offPeakSupplyText.toDoubleOrNull() != null) {
            overrideRateText = formatRateForInput(s.coerceAtLeast(0.0))
        }
    }

    fun updateOverrideRate(value: String) {
        overrideRateText = value
        errorMessage = null
    }

    fun findExactMatchingBill(existingBills: List<UtilityBillEntity>): UtilityBillEntity? {
        val parsedStartMs = ElectricityImportParser.parseDateToStartOfDayMs(startDateText) ?: return null
        val parsedEndMs = ElectricityImportParser.parseDateToEndOfDayMs(endDateText) ?: return null
        val startIso = ElectricityImportParser.formatEpochMsToIsoDate(parsedStartMs)
        val endIso = ElectricityImportParser.formatEpochMsToIsoDate(parsedEndMs)
        return existingBills.firstOrNull { bill ->
            ElectricityImportParser.formatEpochMsToIsoDate(bill.startDateMs) == startIso &&
                ElectricityImportParser.formatEpochMsToIsoDate(bill.endDateMs) == endIso
        }
    }

    fun submit(
        replaceExistingId: String?,
        onSave: (
            providerName: String,
            startDateMs: Long,
            endDateMs: Long,
            totalKwh: Double,
            totalAmount: Double,
            fixedCharges: Double,
            overrideRate: Double?,
            replaceExistingId: String?
        ) -> Unit
    ) {
        val sMs = ElectricityImportParser.parseDateToStartOfDayMs(startDateText)
        val eMs = ElectricityImportParser.parseDateToEndOfDayMs(endDateText)
        val kwh = totalKwhText.toDoubleOrNull()
        val amount = totalAmountText.toDoubleOrNull()
        val fixed = fixedChargesText.toDoubleOrNull() ?: 0.0
        val custom = if (useOverrideRate) overrideRateText.toDoubleOrNull() else null

        when {
            sMs == null || eMs == null -> errorMessage = "Enter valid start and end dates (YYYY-MM-DD)."
            eMs < sMs -> errorMessage = "End date cannot be before start date."
            kwh == null || kwh <= 0.0 -> errorMessage = "Enter billed usage above 0 kWh."
            amount == null || amount <= 0.0 -> errorMessage = "Enter a bill amount above $0."
            useOverrideRate && (custom == null || custom <= 0.0) ->
                errorMessage = "Enter a custom rate above $0."
            else -> onSave(
                providerText.trim().ifBlank { "Electric bill" },
                sMs,
                eMs,
                kwh,
                amount,
                fixed.coerceAtLeast(0.0),
                custom,
                replaceExistingId
            )
        }
    }
}

@Composable
fun rememberUtilityBillReviewState(initialDraft: ParsedUtilityBillDraft?): UtilityBillReviewState {
    val defaultMonth = remember { YearMonth.now() }
    val defaultStart = remember { defaultMonth.atDay(1).toString() }
    val defaultEnd = remember { defaultMonth.atEndOfMonth().toString() }
    val initialTouOffPeakTotal = remember(initialDraft) {
        ElectricityImportParser.calculateTouOffPeakRate(
            offPeakSupplyRate = initialDraft?.offPeakSupplyRate,
            distributionRatePerKwh = initialDraft?.distributionRatePerKwh
        )
    }
    return remember(initialDraft) {
        UtilityBillReviewState(
            initialDraft = initialDraft,
            defaultStart = defaultStart,
            defaultEnd = defaultEnd,
            initialTouOffPeakTotal = initialTouOffPeakTotal
        )
    }
}
