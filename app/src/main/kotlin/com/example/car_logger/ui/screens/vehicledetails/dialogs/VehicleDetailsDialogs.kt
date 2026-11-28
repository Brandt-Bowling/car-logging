package com.example.car_logger.ui.screens.vehicledetails.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Tire
import java.time.LocalDate
import java.util.UUID

@Composable
fun WakeOdometerConfirmDialog(
    carDisplayName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wake vehicle?") },
        text = { Text("Waking $carDisplayName uses a small amount of battery. Sync odometer now?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Wake & sync", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogTireRotationDialog(
    currentOdometer: Int,
    onSave: (date: String, odometer: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var rotDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var rotOdo by remember { mutableStateOf(currentOdometer.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log tire rotation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rotDate,
                    onValueChange = { rotDate = it },
                    label = { Text("Rotation date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rotOdo,
                    onValueChange = { rotOdo = it },
                    label = { Text("Odometer at rotation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(rotDate, rotOdo.toIntOrNull() ?: currentOdometer) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NewTireSetDialog(
    carId: String,
    currentOdometer: Int,
    onSave: (Tire) -> Unit,
    onDismiss: () -> Unit
) {
    var mfg by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var installDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var installOdo by remember { mutableStateOf(currentOdometer.toString()) }
    var treadLife by remember { mutableStateOf("60000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tire set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = mfg,
                    onValueChange = { mfg = it },
                    label = { Text("Manufacturer (e.g., Michelin)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (e.g., Defender)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = installDate,
                    onValueChange = { installDate = it },
                    label = { Text("Install date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = installOdo,
                    onValueChange = { installOdo = it },
                    label = { Text("Odometer installed") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = treadLife,
                    onValueChange = { treadLife = it },
                    label = { Text("Estimated tread life (miles)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mfg.isNotBlank() && model.isNotBlank()) {
                        onSave(
                            Tire(
                                id = UUID.randomUUID().toString(),
                                carId = carId,
                                manufacturer = mfg.trim(),
                                model = model.trim(),
                                dateInstalled = installDate.trim(),
                                odometerInstalled = installOdo.toIntOrNull() ?: currentOdometer,
                                estimatedTreadLifeMiles = treadLife.toIntOrNull() ?: 60000
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditVehicleDetailsDialog(
    car: Car,
    onSave: (Car) -> Unit,
    onDismiss: () -> Unit
) {
    var yearText by remember { mutableStateOf(car.year.toString()) }
    var makeText by remember { mutableStateOf(car.make) }
    var modelText by remember { mutableStateOf(car.model) }
    var trimText by remember { mutableStateOf(car.trim ?: "") }
    var yearError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = {
                        yearText = it
                        yearError = it.toIntOrNull() == null || it.toInt() !in 1900..2100
                    },
                    label = { Text("Year") },
                    isError = yearError,
                    supportingText = if (yearError) { { Text("Enter a 4-digit year") } } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = makeText,
                    onValueChange = { makeText = it },
                    label = { Text("Make (e.g., Tesla)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("Model (e.g., Model 3)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trimText,
                    onValueChange = { trimText = it },
                    label = { Text("Trim (optional, e.g., Long Range)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedYear = yearText.toIntOrNull()
                    if (parsedYear != null && parsedYear in 1900..2100 && makeText.isNotBlank() && modelText.isNotBlank()) {
                        onSave(
                            car.copy(
                                year = parsedYear,
                                make = makeText.trim(),
                                model = modelText.trim(),
                                trim = trimText.trim().ifBlank { null }
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditVinDialog(
    currentVin: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var vinText by remember { mutableStateOf(currentVin ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit VIN") },
        text = {
            OutlinedTextField(
                value = vinText,
                onValueChange = { vinText = it.uppercase() },
                label = { Text("17-character VIN") },
                placeholder = { Text("Enter VIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(vinText.trim().uppercase().ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditLicensePlateDialog(
    currentPlate: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var plateText by remember { mutableStateOf(currentPlate ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit license plate") },
        text = {
            OutlinedTextField(
                value = plateText,
                onValueChange = { plateText = it.uppercase() },
                label = { Text("License plate number") },
                placeholder = { Text("e.g., 7XYZ89") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(plateText.trim().uppercase().ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
