package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedUtilityBillDraft
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

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
    var useOverrideRate by remember { mutableStateOf(false) }
    var overrideRateText by remember { mutableStateOf("") }

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
        modifier = modifier
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
                        text = if (initialDraft != null) "Review Electric Bill" else "Add Electric Bill",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "We subtract flat monthly fees so your $/kWh reflects actual electricity cost.",
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
                        Text(
                            text = "Details filled from ${initialDraft.fileName ?: "your bill"} — double-check before saving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                    Text("Upload Electric Bill (PDF or Photo)")
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
                label = { Text("Electric Provider (e.g., PG&E)") },
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
                    label = { Text("Bill Start") },
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
                    label = { Text("Bill End") },
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
                    label = { Text("Total Bill ($)") },
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
                    label = { Text("Fixed Fees ($)") },
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
                label = { Text("Total Billed Usage (kWh)") },
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "YOUR EFFECTIVE RATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (parsedKwh > 0.0) {
                                "Usage charges: $${String.format(Locale.US, "%.2f", volumetricCharges)} ÷ ${String.format(Locale.US, "%.0f", parsedKwh)} kWh"
                            } else {
                                "Enter total bill & kWh to calculate"
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

            // Optional custom rate override
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
                                text = "Use custom off-peak / EV rate",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Turn on if you want to lock in a special TOU overnight rate",
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
                        OutlinedTextField(
                            value = overrideRateText,
                            onValueChange = {
                                overrideRateText = it
                                errorMessage = null
                            },
                            label = { Text("Custom Rate ($/kWh)") },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        )
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
                            text = "You already have a saved bill (${exactMatchingBill.providerName}) for these exact dates. Saving will replace it.",
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
                            kwh == null || kwh <= 0.0 -> errorMessage = "Enter total billed usage greater than 0 kWh."
                            amount == null || amount <= 0.0 -> errorMessage = "Enter total bill amount greater than $0."
                            useOverrideRate && (custom == null || custom <= 0.0) ->
                                errorMessage = "Enter a valid custom rate greater than $0."
                            else -> onSave(
                                providerText.trim().ifBlank { "Electric Utility" },
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
                    Text(if (exactMatchingBill != null) "Save & Replace Bill" else "Save Electric Bill")
                }
            }
        }
    }
}
