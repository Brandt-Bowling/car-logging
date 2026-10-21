package com.example.car_logger.ui.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.InputChip
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
import com.example.car_logger.data.model.MaintenanceRecord
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaintenanceRecordDialog(
    cars: List<Car>,
    initialCarId: String?,
    record: MaintenanceRecord? = null,
    onDismissRequest: () -> Unit,
    onSave: (MaintenanceRecord) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val isEditing = record != null

    var selectedCarId by remember { mutableStateOf(record?.carId ?: initialCarId ?: cars.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf(record?.title ?: "") }
    var date by remember { mutableStateOf(record?.date?.take(10) ?: LocalDate.now().toString()) }
    var odometer by remember {
        val defaultOdo = record?.odometer?.toString() ?: cars.find { it.id == selectedCarId }?.odometer?.toString() ?: ""
        mutableStateOf(defaultOdo)
    }
    var cost by remember { mutableStateOf(record?.cost?.toString() ?: "") }
    var description by remember { mutableStateOf(record?.description ?: "") }

    val suggestions = listOf(
        "Oil & Filter Change",
        "Tire Rotation",
        "Brake Pad Replacement",
        "State Inspection",
        "Cabin Air Filter",
        "Battery Replacement"
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(if (isEditing) "Edit Service Record" else "Add Service Record") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vehicle selection dropdown (if multiple vehicles)
                if (cars.size > 1) {
                    var carDropdownExpanded by remember { mutableStateOf(false) }
                    val selectedCar = cars.find { it.id == selectedCarId }

                    ExposedDropdownMenuBox(
                        expanded = carDropdownExpanded,
                        onExpandedChange = { carDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCar?.displayName ?: "Select Vehicle",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Vehicle") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = carDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = carDropdownExpanded,
                            onDismissRequest = { carDropdownExpanded = false }
                        ) {
                            cars.forEach { car ->
                                DropdownMenuItem(
                                    text = { Text(car.displayName) },
                                    onClick = {
                                        selectedCarId = car.id
                                        if (odometer.isBlank() && car.odometer != null) {
                                            odometer = car.odometer.toString()
                                        }
                                        carDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Service Title") },
                    placeholder = { Text("e.g. Oil Change") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick suggestions flow row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { chip ->
                        InputChip(
                            selected = title == chip,
                            onClick = { title = chip },
                            label = { Text(chip, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Date
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Odometer
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it },
                    label = { Text("Odometer (miles)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Cost
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Total Cost ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && selectedCarId.isNotBlank()) {
                        val newRecord = MaintenanceRecord(
                            id = record?.id ?: UUID.randomUUID().toString(),
                            carId = selectedCarId,
                            title = title.trim(),
                            date = date.trim(),
                            odometer = odometer.toIntOrNull() ?: 0,
                            cost = cost.toDoubleOrNull(),
                            description = description.trim().ifEmpty { null },
                            receiptUrl = record?.receiptUrl,
                            driveFileId = record?.driveFileId
                        )
                        onSave(newRecord)
                    }
                },
                enabled = title.isNotBlank() && selectedCarId.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (isEditing && onDelete != null) {
                    TextButton(onClick = { onDelete(record!!.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    )
}
