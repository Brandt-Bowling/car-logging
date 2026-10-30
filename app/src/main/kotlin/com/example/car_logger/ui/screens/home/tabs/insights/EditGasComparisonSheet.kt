package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGasComparisonSheet(
    currentGasPrice: Float,
    currentBaselineMpg: Float,
    onDismiss: () -> Unit,
    onSave: (gasPrice: Float, baselineMpg: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var gasPriceText by remember(currentGasPrice) {
        mutableStateOf(String.format(Locale.US, "%.2f", currentGasPrice))
    }
    var baselineMpgText by remember(currentBaselineMpg) {
        mutableStateOf(String.format(Locale.US, "%.1f", currentBaselineMpg))
    }

    var gasPriceError by remember { mutableStateOf<String?>(null) }
    var baselineMpgError by remember { mutableStateOf<String?>(null) }

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
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Gas Comparison Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Configure baseline fuel economy and regional gas prices to see how much fuel and cost your electric miles saved compared to a gas vehicle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Gas Price input
            OutlinedTextField(
                value = gasPriceText,
                onValueChange = {
                    gasPriceText = it
                    gasPriceError = null
                },
                label = { Text("Average Gas Price") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AttachMoney,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Text(
                        text = "/ gal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                isError = gasPriceError != null,
                supportingText = gasPriceError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Baseline MPG input
            OutlinedTextField(
                value = baselineMpgText,
                onValueChange = {
                    baselineMpgText = it
                    baselineMpgError = null
                },
                label = { Text("Baseline Gas Efficiency") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.LocalGasStation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Text(
                        text = "MPG",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                isError = baselineMpgError != null,
                supportingText = baselineMpgError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.expressiveBounceClick()
                ) {
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.height(0.dp).padding(horizontal = 4.dp))

                Button(
                    onClick = {
                        val parsedPrice = gasPriceText.toFloatOrNull()
                        val parsedMpg = baselineMpgText.toFloatOrNull()

                        var valid = true
                        if (parsedPrice == null || parsedPrice <= 0f) {
                            gasPriceError = "Please enter a valid price greater than 0"
                            valid = false
                        }
                        if (parsedMpg == null || parsedMpg <= 0f) {
                            baselineMpgError = "Please enter MPG greater than 0"
                            valid = false
                        }

                        if (valid && parsedPrice != null && parsedMpg != null) {
                            onSave(parsedPrice, parsedMpg)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.expressiveBounceClick()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
