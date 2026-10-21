package com.example.car_logger.ui.screens.vehicledetails.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Tire
import com.example.car_logger.ui.components.TreadWearGauge
import com.example.car_logger.ui.screens.dialogs.TireRotationIntervalDialog
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@Composable
fun TiresTab(
    car: Car,
    activeTire: Tire?,
    onLogRotation: (String, Int) -> Unit,
    onInstallNewTire: (Tire) -> Unit,
    onUpdateCar: ((Car) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showRotationDialog by remember { mutableStateOf(false) }
    var showNewTireDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    val currentOdo = car.odometer ?: 0
    val tire = activeTire ?: remember(car.id) {
        Tire(
            id = "default_${car.id}",
            carId = car.id,
            manufacturer = "Michelin",
            model = "Defender 2",
            dateInstalled = "2023-01-15",
            odometerInstalled = (currentOdo - 15000).coerceAtLeast(0),
            estimatedTreadLifeMiles = 60000,
            lastRotationDate = "2023-09-10",
            lastRotationOdometer = (currentOdo - 7500).coerceAtLeast(0)
        )
    }

    val remainingMiles = tire.remainingMiles(currentOdo)
    val wearPercentage = tire.treadWearPercentage(currentOdo)

    // Log Rotation Dialog
    if (showRotationDialog) {
        var rotDate by remember { mutableStateOf(LocalDate.now().toString()) }
        var rotOdo by remember { mutableStateOf(currentOdo.toString()) }

        AlertDialog(
            onDismissRequest = { showRotationDialog = false },
            title = { Text("Log Tire Rotation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rotDate,
                        onValueChange = { rotDate = it },
                        label = { Text("Rotation Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rotOdo,
                        onValueChange = { rotOdo = it },
                        label = { Text("Odometer at Rotation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val odoInt = rotOdo.toIntOrNull() ?: currentOdo
                        onLogRotation(rotDate, odoInt)
                        showRotationDialog = false
                    }
                ) {
                    Text("Save Rotation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Install New Tire Set Dialog
    if (showNewTireDialog) {
        var mfg by remember { mutableStateOf("") }
        var model by remember { mutableStateOf("") }
        var installDate by remember { mutableStateOf(LocalDate.now().toString()) }
        var installOdo by remember { mutableStateOf(currentOdo.toString()) }
        var treadLife by remember { mutableStateOf("60000") }

        AlertDialog(
            onDismissRequest = { showNewTireDialog = false },
            title = { Text("Install New Tire Set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = mfg,
                        onValueChange = { mfg = it },
                        label = { Text("Manufacturer (e.g. Michelin)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model (e.g. Defender)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = installDate,
                        onValueChange = { installDate = it },
                        label = { Text("Install Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = installOdo,
                        onValueChange = { installOdo = it },
                        label = { Text("Odometer Installed") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = treadLife,
                        onValueChange = { treadLife = it },
                        label = { Text("Estimated Tread Life (miles)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mfg.isNotBlank() && model.isNotBlank()) {
                            val newTire = Tire(
                                id = UUID.randomUUID().toString(),
                                carId = car.id,
                                manufacturer = mfg.trim(),
                                model = model.trim(),
                                dateInstalled = installDate.trim(),
                                odometerInstalled = installOdo.toIntOrNull() ?: currentOdo,
                                estimatedTreadLifeMiles = treadLife.toIntOrNull() ?: 60000
                            )
                            onInstallNewTire(newTire)
                            showNewTireDialog = false
                        }
                    }
                ) {
                    Text("Install Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewTireDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIntervalDialog) {
        TireRotationIntervalDialog(
            currentInterval = car.effectiveTireRotationInterval,
            onDismiss = { showIntervalDialog = false },
            onConfirm = { newInterval ->
                onUpdateCar?.invoke(car.copy(tireRotationInterval = newInterval))
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tread Wear Ring Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Estimated Tread Life",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TreadWearGauge(
                        percentage = wearPercentage,
                        remainingMiles = remainingMiles,
                        size = 140.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${tire.manufacturer} ${tire.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Rated for ${NumberFormat.getNumberInstance(Locale.US).format(tire.estimatedTreadLifeMiles)} miles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tire Lifecycle & Rotation Specs Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Tire Set Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Installed Date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tire.dateInstalled.take(10), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Installed Odometer", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${NumberFormat.getNumberInstance(Locale.US).format(tire.odometerInstalled)} mi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last Rotation", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val rotInfo = if (tire.lastRotationDate != null && tire.lastRotationOdometer != null) {
                            "${tire.lastRotationDate.take(10)} (${NumberFormat.getNumberInstance(Locale.US).format(tire.lastRotationOdometer)} mi)"
                        } else {
                            "Not logged"
                        }
                        Text(rotInfo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onUpdateCar != null) { showIntervalDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Rotation Interval", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Recommended interval between rotations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${NumberFormat.getNumberInstance(Locale.US).format(car.effectiveTireRotationInterval)} miles",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (onUpdateCar != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.Edit, contentDescription = "Edit Interval", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { showRotationDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Rotation")
                }

                OutlinedButton(
                    onClick = { showNewTireDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.TireRepair, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Tire Set")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp)) // Space for floating toolbar
        }
    }
}
