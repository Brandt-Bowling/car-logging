package com.example.car_logger.ui.screens.home.tabs.insights.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ParsedUtilityBillDraft
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@Composable
fun UtilityBillHeaderSection(
    initialDraft: ParsedUtilityBillDraft?,
    onUploadDifferentFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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

        if (initialDraft?.source == "gemini" && initialDraft.error == null) {
            GeminiBillBanner(initialDraft = initialDraft)
        } else {
            OutlinedButton(
                onClick = onUploadDifferentFile,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
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
    }
}

@Composable
private fun GeminiBillBanner(initialDraft: ParsedUtilityBillDraft) {
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
                        initialDraft.offPeakSupplyRate?.let { append(" · Off-peak supply $${formatRateForInput(it)}") }
                        initialDraft.distributionRatePerKwh?.let { append(" + Delivery $${formatRateForInput(it)}") }
                        initialDraft.onPeakSupplyRate?.let { append(" (On-peak $${formatRateForInput(it)})") }
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
}

@Composable
fun UtilityBillCoreFieldsSection(
    state: UtilityBillReviewState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = state.providerText,
            onValueChange = state::updateProvider,
            label = { Text("Electric provider (e.g., PG&E)") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.startDateText,
                onValueChange = state::updateStartDate,
                label = { Text("Bill start") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.endDateText,
                onValueChange = state::updateEndDate,
                label = { Text("Bill end") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.totalAmountText,
                onValueChange = state::updateTotalAmount,
                label = { Text("Total bill ($)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.fixedChargesText,
                onValueChange = state::updateFixedCharges,
                label = { Text("Fixed fees ($)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = state.totalKwhText,
            onValueChange = state::updateTotalKwh,
            label = { Text("Total billed usage (kWh)") },
            suffix = { Text("kWh") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun UtilityBillEffectiveRateCard(
    state: UtilityBillReviewState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
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
                    text = if (state.useOverrideRate && state.parsedOverride != null) "OFF-PEAK EV RATE" else "EFFECTIVE RATE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = when {
                        state.useOverrideRate && state.parsedSupplyComponent != null -> {
                            val dist = state.parsedDistComponent ?: 0.0
                            "Supply $${formatRateForInput(state.parsedSupplyComponent!!)} + Delivery $${formatRateForInput(dist)}"
                        }
                        state.useOverrideRate -> "Custom off-peak rate"
                        state.parsedKwh > 0.0 ->
                            "$${String.format(Locale.US, "%.2f", state.volumetricCharges)} ÷ ${String.format(Locale.US, "%,.0f", state.parsedKwh)} kWh"
                        else -> "Enter total bill & kWh to calculate"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$${String.format(Locale.US, "%.3f", state.effectivePreviewRate)}/kWh",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun UtilityBillTouOverrideCard(
    state: UtilityBillReviewState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
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
                Switch(checked = state.useOverrideRate, onCheckedChange = state::updateUseOverrideRate)
            }

            AnimatedVisibility(visible = state.useOverrideRate) {
                TouOverrideFields(state = state)
            }
        }
    }
}

@Composable
private fun TouOverrideFields(state: UtilityBillReviewState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.offPeakSupplyText,
                onValueChange = state::updateOffPeakSupply,
                label = { Text("Off-peak supply") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.distributionRateText,
                onValueChange = state::updateDistributionRate,
                label = { Text("Delivery / kWh") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = state.overrideRateText,
            onValueChange = state::updateOverrideRate,
            label = { Text("Effective off-peak rate ($/kWh)") },
            prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun UtilityBillReplaceWarningBanner(
    exactMatchingBill: UtilityBillEntity,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AmberUrgent.copy(alpha = 0.16f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = AmberUrgent)
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

@Composable
fun UtilityBillActionRow(
    isReplacing: Boolean,
    onDismiss: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Cancel")
        }
        Button(
            onClick = onSaveClick,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1.4f)
                .expressiveBounceClick()
        ) {
            Text(if (isReplacing) "Replace bill" else "Save bill")
        }
    }
}
