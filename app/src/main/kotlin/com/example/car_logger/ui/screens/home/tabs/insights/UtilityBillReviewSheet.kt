package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedBillLineItem
import com.example.car_logger.services.ParsedUtilityBillDraft
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun formatRateForInput(rate: Double): String {
    val fourDecimals = String.format(Locale.US, "%.4f", rate)
    return if (fourDecimals.endsWith("0")) {
        fourDecimals.dropLast(1)
    } else {
        fourDecimals
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilityBillReviewSheet(
    initialDraft: ParsedUtilityBillDraft?,
    existingBills: List<UtilityBillEntity>,
    onUploadDifferentFile: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        providerName: String,
        startDateMs: Long,
        endDateMs: Long,
        totalKwh: Double,
        totalAmount: Double,
        fixedCharges: Double,
        overrideRate: Double?,
        replaceExistingId: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val topClearance = maxOf(topInset + 8.dp, 72.dp)
    val defaultMonth = remember { YearMonth.now() }
    val defaultStart = remember { defaultMonth.atDay(1).toString() }
    val defaultEnd = remember { defaultMonth.atEndOfMonth().toString() }

    var providerText by remember(initialDraft) {
        mutableStateOf(initialDraft?.providerName?.ifBlank { "" } ?: "")
    }
    var startDateText by remember(initialDraft) {
        mutableStateOf(initialDraft?.startDateIso?.ifBlank { defaultStart } ?: defaultStart)
    }
    var endDateText by remember(initialDraft) {
        mutableStateOf(initialDraft?.endDateIso?.ifBlank { defaultEnd } ?: defaultEnd)
    }
    var totalAmountText by remember(initialDraft) {
        mutableStateOf(
            initialDraft?.totalAmount?.takeIf { it > 0.0 }
                ?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        )
    }
    var fixedChargesText by remember(initialDraft) {
        mutableStateOf(
            initialDraft?.fixedCharges?.takeIf { it > 0.0 }
                ?.let { String.format(Locale.US, "%.2f", it) } ?: "0.00"
        )
    }
    var totalKwhText by remember(initialDraft) {
        mutableStateOf(
            initialDraft?.totalKwh?.takeIf { it > 0.0 }
                ?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        )
    }

    val initialTouOffPeakTotal = remember(initialDraft) {
        ElectricityImportParser.calculateTouOffPeakRate(
            offPeakSupplyRate = initialDraft?.offPeakSupplyRate,
            distributionRatePerKwh = initialDraft?.distributionRatePerKwh
        )
    }
    var offPeakSupplyText by remember(initialDraft) {
        mutableStateOf(
            initialDraft?.offPeakSupplyRate?.takeIf { it > 0.0 }
                ?.let { formatRateForInput(it) } ?: ""
        )
    }
    var distributionRateText by remember(initialDraft) {
        mutableStateOf(
            initialDraft?.distributionRatePerKwh?.takeIf { it >= 0.0 }
                ?.let { formatRateForInput(it) } ?: ""
        )
    }
    var useOverrideRate by remember(initialDraft) {
        mutableStateOf(initialDraft?.isTouPlan == true && initialTouOffPeakTotal != null)
    }
    var overrideRateText by remember(initialDraft) {
        mutableStateOf(initialTouOffPeakTotal?.let { formatRateForInput(it) } ?: "")
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedAmount = totalAmountText.toDoubleOrNull() ?: 0.0
    val parsedFixed = fixedChargesText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val parsedKwh = totalKwhText.toDoubleOrNull() ?: 0.0
    val calculatedRate = ElectricityImportParser.calculateVariableBlendedRate(
        totalAmount = parsedAmount,
        fixedCharges = parsedFixed,
        totalKwh = parsedKwh
    )
    val volumetricCharges = (parsedAmount - parsedFixed).coerceAtLeast(0.0)
    val parsedSupplyComponent = offPeakSupplyText.toDoubleOrNull()?.takeIf { it >= 0.0 }
    val parsedDistComponent = distributionRateText.toDoubleOrNull()?.takeIf { it >= 0.0 }
    val parsedOverride = if (useOverrideRate) overrideRateText.toDoubleOrNull() else null
    val effectivePreviewRate = parsedOverride ?: calculatedRate

    val parsedStartMs = ElectricityImportParser.parseDateToStartOfDayMs(startDateText)
    val parsedEndMs = ElectricityImportParser.parseDateToEndOfDayMs(endDateText)
    val exactMatchingBill = remember(parsedStartMs, parsedEndMs, existingBills) {
        if (parsedStartMs == null || parsedEndMs == null) null
        else existingBills.firstOrNull { bill ->
            ElectricityImportParser.formatEpochMsToIsoDate(bill.startDateMs) ==
                ElectricityImportParser.formatEpochMsToIsoDate(parsedStartMs) &&
                ElectricityImportParser.formatEpochMsToIsoDate(bill.endDateMs) ==
                ElectricityImportParser.formatEpochMsToIsoDate(parsedEndMs)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        modifier = modifier.padding(top = topClearance)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (initialDraft != null) "Review electric bill" else "Add electric bill",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Excludes flat fees or uses off-peak rates to find your real $/kWh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Upload / Re-scan button or AI badge
            if (initialDraft?.source == "gemini" && initialDraft.error == null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Filled from ${initialDraft.fileName ?: "your bill"}. Review before saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (initialDraft.isTouPlan) {
                                val touSummary = buildString {
                                    append("TOU plan detected")
                                    initialDraft.offPeakSupplyRate?.let {
                                        append(" · Off-peak supply $${formatRateForInput(it)}")
                                    }
                                    initialDraft.distributionRatePerKwh?.let {
                                        append(" + Delivery $${formatRateForInput(it)}")
                                    }
                                    initialDraft.onPeakSupplyRate?.let {
                                        append(" (On-peak $${formatRateForInput(it)})")
                                    }
                                }
                                Text(
                                    text = touSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onUploadDifferentFile,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload electric bill (PDF or photo)")
                }
            }

            if (initialDraft?.error != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = initialDraft.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = providerText,
                onValueChange = {
                    providerText = it
                    errorMessage = null
                },
                label = { Text("Electric provider (e.g., PG&E)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = startDateText,
                    onValueChange = {
                        startDateText = it
                        errorMessage = null
                    },
                    label = { Text("Bill start") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endDateText,
                    onValueChange = {
                        endDateText = it
                        errorMessage = null
                    },
                    label = { Text("Bill end") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = totalAmountText,
                    onValueChange = {
                        totalAmountText = it
                        errorMessage = null
                    },
                    label = { Text("Total bill ($)") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fixedChargesText,
                    onValueChange = {
                        fixedChargesText = it
                        errorMessage = null
                    },
                    label = { Text("Fixed fees ($)") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = totalKwhText,
                onValueChange = {
                    totalKwhText = it
                    errorMessage = null
                },
                label = { Text("Total billed usage (kWh)") },
                suffix = { Text("kWh") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Live math breakdown card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (useOverrideRate && parsedOverride != null) {
                                "OFF-PEAK EV RATE"
                            } else {
                                "EFFECTIVE RATE"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when {
                                useOverrideRate && parsedSupplyComponent != null -> {
                                    val dist = parsedDistComponent ?: 0.0
                                    "Supply $${formatRateForInput(parsedSupplyComponent)} + Delivery $${formatRateForInput(dist)}"
                                }
                                useOverrideRate -> "Custom off-peak rate"
                                parsedKwh > 0.0 ->
                                    "$${String.format(Locale.US, "%.2f", volumetricCharges)} ÷ ${String.format(Locale.US, "%,.0f", parsedKwh)} kWh"
                                else -> "Enter total bill & kWh to calculate"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$${String.format(Locale.US, "%.3f", effectivePreviewRate)}/kWh",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (initialDraft != null || parsedAmount > 0.0 || parsedKwh > 0.0) {
                BillBreakdownCard(
                    breakdownItems = initialDraft?.breakdownItems.orEmpty(),
                    offPeakKwh = initialDraft?.offPeakKwh,
                    onPeakKwh = initialDraft?.onPeakKwh,
                    offPeakSupplyRate = parsedSupplyComponent ?: initialDraft?.offPeakSupplyRate,
                    onPeakSupplyRate = initialDraft?.onPeakSupplyRate,
                    distributionRatePerKwh = parsedDistComponent ?: initialDraft?.distributionRatePerKwh,
                    totalAmount = parsedAmount,
                    fixedCharges = parsedFixed,
                    totalKwh = parsedKwh,
                    calculatedBlendedRate = calculatedRate,
                    useTouRate = useOverrideRate
                )
            }

            // Optional TOU off-peak + distribution rate override
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use off-peak rate",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Combine off-peak supply and delivery per kWh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useOverrideRate,
                            onCheckedChange = { useOverrideRate = it }
                        )
                    }

                    AnimatedVisibility(visible = useOverrideRate) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = offPeakSupplyText,
                                    onValueChange = { newVal ->
                                        offPeakSupplyText = newVal
                                        errorMessage = null
                                        val s = newVal.toDoubleOrNull()
                                        val d = distributionRateText.toDoubleOrNull() ?: 0.0
                                        if (s != null && s >= 0.0) {
                                            overrideRateText = formatRateForInput(s + d.coerceAtLeast(0.0))
                                        }
                                    },
                                    label = { Text("Off-peak supply") },
                                    prefix = { Text("$") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = distributionRateText,
                                    onValueChange = { newVal ->
                                        distributionRateText = newVal
                                        errorMessage = null
                                        val s = offPeakSupplyText.toDoubleOrNull() ?: 0.0
                                        val d = newVal.toDoubleOrNull()
                                        if (d != null && d >= 0.0) {
                                            overrideRateText = formatRateForInput(s.coerceAtLeast(0.0) + d)
                                        } else if (newVal.isBlank() && offPeakSupplyText.toDoubleOrNull() != null) {
                                            overrideRateText = formatRateForInput(s.coerceAtLeast(0.0))
                                        }
                                    },
                                    label = { Text("Delivery / kWh") },
                                    prefix = { Text("$") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = overrideRateText,
                                onValueChange = {
                                    overrideRateText = it
                                    errorMessage = null
                                },
                                label = { Text("Effective off-peak rate ($/kWh)") },
                                prefix = { Text("$") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (exactMatchingBill != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AmberUrgent.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AmberUrgent
                        )
                        Text(
                            text = if (exactMatchingBill.providerName.isNotBlank()) {
                                "Replaces your saved ${exactMatchingBill.providerName} bill for these dates."
                            } else {
                                "Replaces your saved bill for these dates."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
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
                                exactMatchingBill?.id
                            )
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .expressiveBounceClick()
                ) {
                    Text(if (exactMatchingBill != null) "Replace bill" else "Save bill")
                }
            }
        }
    }
}

@Composable
private fun BillBreakdownCard(
    breakdownItems: List<ParsedBillLineItem>,
    offPeakKwh: Double?,
    onPeakKwh: Double?,
    offPeakSupplyRate: Double?,
    onPeakSupplyRate: Double?,
    distributionRatePerKwh: Double?,
    totalAmount: Double,
    fixedCharges: Double,
    totalKwh: Double,
    calculatedBlendedRate: Double,
    useTouRate: Boolean
) {
    var expanded by remember(breakdownItems) { mutableStateOf(false) }

    val touKwhTotal = ((offPeakKwh ?: 0.0) + (onPeakKwh ?: 0.0)).takeIf { it > 0.0 }
    val offPeakSharePct = if (offPeakKwh != null && touKwhTotal != null) {
        ((offPeakKwh / touKwhTotal) * 100.0).roundToInt().coerceIn(0, 100)
    } else {
        null
    }

    val subtitle = buildString {
        if (breakdownItems.isNotEmpty()) {
            append("${breakdownItems.size} ${if (breakdownItems.size == 1) "line item" else "line items"}")
        } else {
            append("Rate calculation")
        }
        if (offPeakSharePct != null) {
            append(" · $offPeakSharePct% off-peak")
        } else if (fixedCharges > 0.0 && totalAmount > 0.0) {
            append(" · $${String.format(Locale.US, "%.2f", fixedCharges)} fixed excluded")
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Breakdown",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse breakdown" else "Expand breakdown",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    if (offPeakKwh != null || onPeakKwh != null) {
                        val splitText = buildList {
                            offPeakKwh?.let { kwh ->
                                val pctPart = offPeakSharePct?.let { " ($it%)" } ?: ""
                                add("Off-peak: ${String.format(Locale.US, "%.1f", kwh)} kWh$pctPart")
                            }
                            onPeakKwh?.let { kwh ->
                                val onPct = offPeakSharePct?.let { " (${(100 - it).coerceIn(0, 100)}%)" } ?: ""
                                add("On-peak: ${String.format(Locale.US, "%.1f", kwh)} kWh$onPct")
                            }
                        }.joinToString(" · ")

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = splitText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (breakdownItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            breakdownItems.forEach { item ->
                                val detailParts = buildList {
                                    add(breakdownCategoryLabel(item.category))
                                    if (item.kwh != null && item.ratePerKwh != null) {
                                        add("${String.format(Locale.US, "%.1f", item.kwh)} kWh × $${formatRateForInput(item.ratePerKwh)}")
                                    } else if (item.ratePerKwh != null) {
                                        add("$${formatRateForInput(item.ratePerKwh)}/kWh")
                                    } else if (item.kwh != null) {
                                        add("${String.format(Locale.US, "%.1f", item.kwh)} kWh")
                                    }
                                }

                                val rightValueText = when {
                                    item.amount != null && item.amount < 0.0 ->
                                        "-$${String.format(Locale.US, "%.2f", abs(item.amount))}"
                                    item.amount != null ->
                                        "$${String.format(Locale.US, "%.2f", item.amount)}"
                                    item.ratePerKwh != null ->
                                        "$${formatRateForInput(item.ratePerKwh)}/kWh"
                                    else -> "—"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = detailParts.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = rightValueText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Summary mapping how extracted parts combine into the saved rate
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (useTouRate && offPeakSupplyRate != null) {
                            val dist = (distributionRatePerKwh ?: 0.0).coerceAtLeast(0.0)
                            Text(
                                text = "Off-peak EV rate: $${formatRateForInput(offPeakSupplyRate)} supply + $${formatRateForInput(dist)} delivery = $${formatRateForInput(offPeakSupplyRate + dist)}/kWh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            onPeakSupplyRate?.let { onPeak ->
                                Text(
                                    text = "On-peak supply (excluded): $${formatRateForInput(onPeak)}/kWh",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val usageAmount = (totalAmount - fixedCharges).coerceAtLeast(0.0)
                        if (totalAmount > 0.0 && totalKwh > 0.0) {
                            Text(
                                text = "Blended rate: ($${String.format(Locale.US, "%.2f", totalAmount)} − $${String.format(Locale.US, "%.2f", fixedCharges)} fixed = $${String.format(Locale.US, "%.2f", usageAmount)}) ÷ ${String.format(Locale.US, "%,.1f", totalKwh)} kWh = $${formatRateForInput(calculatedBlendedRate)}/kWh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun breakdownCategoryLabel(category: String): String = when (category) {
    "OFF_PEAK_SUPPLY" -> "Off-peak supply"
    "ON_PEAK_SUPPLY" -> "On-peak supply"
    "DISTRIBUTION" -> "Delivery"
    "FIXED" -> "Fixed fee"
    else -> "Tax / other"
}
