package com.example.car_logger.ui.screens.vehicledetails.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.screens.dialogs.TireRotationIntervalDialog
import com.example.car_logger.ui.theme.AmberUrgent
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MaintenanceTab(
    car: Car,
    records: List<MaintenanceRecord>,
    onRecordClick: (MaintenanceRecord) -> Unit,
    onAttachmentClick: (MaintenanceRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onUpdateCar: ((Car) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    val availableYears = remember(records) {
        records.mapNotNull { it.date.take(4).toIntOrNull() }.distinct().sortedDescending()
    }

    val filteredRecords = remember(records, searchQuery, selectedYear) {
        records.filter { rec ->
            val matchesSearch = searchQuery.isBlank() ||
                    rec.title.contains(searchQuery, ignoreCase = true) ||
                    rec.description?.contains(searchQuery, ignoreCase = true) == true
            val year = rec.date.take(4).toIntOrNull()
            val matchesYear = selectedYear == null || year == selectedYear
            matchesSearch && matchesYear
        }
    }

    // Next scheduled service calculation
    val latestOdo = records.maxOfOrNull { it.odometer } ?: (car.odometer ?: 0)
    val interval = if (car.isEv) car.effectiveTireRotationInterval else 5000
    val nextServiceOdo = latestOdo + interval
    val currentOdo = car.odometer ?: latestOdo
    val remainingMiles = nextServiceOdo - currentOdo
    val isUrgent = remainingMiles in 0..500
    val isOverdue = remainingMiles < 0

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
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Next Scheduled Service Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        isUrgent -> AmberUrgent.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Next Scheduled Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (car.isEv && onUpdateCar != null) {
                                IconButton(
                                    onClick = { showIntervalDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Configure Interval",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            if (isUrgent || isOverdue) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isOverdue) MaterialTheme.colorScheme.error else AmberUrgent
                                ) {
                                    Text(
                                        text = if (isOverdue) "Overdue" else "Due Soon",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOverdue) MaterialTheme.colorScheme.onError else androidx.compose.ui.graphics.Color.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val serviceName = if (car.isEv) "Tire Rotation" else "Oil Change"
                    Text(
                        text = "$serviceName at ${NumberFormat.getNumberInstance(Locale.US).format(nextServiceOdo)} miles",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val remainingText = if (isOverdue) {
                        "${NumberFormat.getNumberInstance(Locale.US).format(-remainingMiles)} miles overdue"
                    } else {
                        "${NumberFormat.getNumberInstance(Locale.US).format(remainingMiles)} miles remaining"
                    }
                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val progress = when {
                        isOverdue -> 1f
                        remainingMiles > interval -> 0.1f
                        else -> (interval - remainingMiles).toFloat() / interval.toFloat()
                    }.coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = when {
                            isOverdue -> MaterialTheme.colorScheme.error
                            isUrgent -> AmberUrgent
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Search bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search records...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Filter chips
        if (availableYears.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedYear == null,
                            onClick = { selectedYear = null },
                            label = { Text("All Years") }
                        )
                    }
                    items(availableYears) { year ->
                        FilterChip(
                            selected = selectedYear == year,
                            onClick = { selectedYear = if (selectedYear == year) null else year },
                            label = { Text(year.toString()) }
                        )
                    }
                }
            }
        }

        // Records list
        if (filteredRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (records.isEmpty()) "No maintenance logged yet" else "No matching records",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } else {
            items(filteredRecords, key = { it.id }) { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onRecordClick(record) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!record.description.isNullOrBlank()) {
                                Text(
                                    text = record.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(record.date.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("•", color = MaterialTheme.colorScheme.outlineVariant)
                                val formattedOdo = "${NumberFormat.getNumberInstance(Locale.US).format(record.odometer)} mi"
                                Text(formattedOdo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (record.cost != null) {
                                    Text("•", color = MaterialTheme.colorScheme.outlineVariant)
                                    val formattedCost = "$${String.format(Locale.US, "%.2f", record.cost)}"
                                    Text(formattedCost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (!record.receiptUrl.isNullOrBlank() || !record.driveFileId.isNullOrBlank()) {
                            IconButton(onClick = { onAttachmentClick(record) }) {
                                Icon(Icons.Filled.AttachFile, contentDescription = "View Attachment", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        IconButton(onClick = { onDeleteRecord(record.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp)) // Space for floating toolbar
        }
    }
}
