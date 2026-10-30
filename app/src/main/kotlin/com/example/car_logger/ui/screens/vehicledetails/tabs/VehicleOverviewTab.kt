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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.data.model.Tire
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleCostHistory
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleCostHistoryCard
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleHeroCard
import com.example.car_logger.ui.screens.vehicledetails.components.VehicleTireHealthCard
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale

@Composable
fun VehicleOverviewTab(
    car: Car,
    records: List<MaintenanceRecord>,
    filteredRecords: List<MaintenanceRecord>,
    costHistory: VehicleCostHistory,
    activeTire: Tire?,
    searchQuery: String,
    selectedYear: Int?,
    onSearchQueryChange: (String) -> Unit,
    onSelectYear: (Int?) -> Unit,
    onOpenPhotoCustomizer: () -> Unit,
    onLogRotationClick: () -> Unit,
    onEditIntervalClick: () -> Unit,
    onInstallNewTireClick: () -> Unit,
    onRecordClick: (MaintenanceRecord) -> Unit,
    onAttachmentClick: (MaintenanceRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableYears = remember(records) {
        records.mapNotNull { it.date.take(4).toIntOrNull() }.distinct().sortedDescending()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Vehicle Visual Card
        item {
            VehicleHeroCard(
                car = car,
                onOpenPhotoCustomizer = onOpenPhotoCustomizer
            )
        }

        // 2. Combined Tires & Next Rotation Card
        item {
            VehicleTireHealthCard(
                car = car,
                activeTire = activeTire,
                onLogRotationClick = onLogRotationClick,
                onEditIntervalClick = onEditIntervalClick,
                onInstallNewTireClick = onInstallNewTireClick
            )
        }

        // 3. Cost History Card (Per Year & Overall based on logs)
        item {
            VehicleCostHistoryCard(
                costHistory = costHistory,
                selectedYear = selectedYear,
                onSelectYear = onSelectYear
            )
        }

        // 4. Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search maintenance logs...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // 5. Year Filter Chips (if multiple years exist)
        if (availableYears.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedYear == null,
                            onClick = { onSelectYear(null) },
                            label = { Text("All Years") }
                        )
                    }
                    items(availableYears) { year ->
                        FilterChip(
                            selected = selectedYear == year,
                            onClick = { onSelectYear(if (selectedYear == year) null else year) },
                            label = { Text(year.toString()) }
                        )
                    }
                }
            }
        }

        // 6. Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedYear != null) "$selectedYear Maintenance Records" else "Maintenance Records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${filteredRecords.size} record${if (filteredRecords.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 7. Filtered Records List
        if (filteredRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (records.isEmpty()) "No maintenance logged yet" else "No matching records",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredRecords, key = { it.id }) { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .animateItem(
                            fadeInSpec = ExpressiveMotionTokens.ItemFadeSpring,
                            fadeOutSpec = ExpressiveMotionTokens.ItemFadeSpring,
                            placementSpec = ExpressiveMotionTokens.ItemPlacementSpring
                        )
                        .expressiveBounceClick(pressedScale = 0.98f) { onRecordClick(record) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
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
                                Text(
                                    text = record.date.take(10),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("•", color = MaterialTheme.colorScheme.outlineVariant)
                                val formattedOdo = "${NumberFormat.getNumberInstance(Locale.US).format(record.odometer)} mi"
                                Text(
                                    text = formattedOdo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (record.cost != null) {
                                    Text("•", color = MaterialTheme.colorScheme.outlineVariant)
                                    val formattedCost = "$${String.format(Locale.US, "%.2f", record.cost)}"
                                    Text(
                                        text = formattedCost,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (!record.receiptUrl.isNullOrBlank() || !record.driveFileId.isNullOrBlank()) {
                            IconButton(onClick = { onAttachmentClick(record) }) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = "View Attachment",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(onClick = { onDeleteRecord(record.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom space for M3 Floating Toolbar
        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
