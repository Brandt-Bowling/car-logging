package com.example.car_logger.ui.screens.home.tabs

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.screens.dialogs.TireRotationIntervalDialog
import com.example.car_logger.ui.screens.dialogs.VehiclePhotoCustomizerSheet
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageTab(
    cars: List<Car>,
    onCarClick: (Car) -> Unit,
    onToggleArchive: (String) -> Unit,
    onDeleteCar: (String) -> Unit,
    onAddCarClick: () -> Unit,
    onUpdateCar: ((Car) -> Unit)? = null,
    onGenerateAiImage: (suspend (car: Car, color: String, trim: String?, env: String?) -> Result<ByteArray>)? = null,
    onSaveCustomImage: ((car: Car, bytes: ByteArray) -> Unit)? = null,
    onSaveImageFromUri: ((car: Car, uri: Uri) -> Unit)? = null,
    onResetVehicleImage: ((car: Car) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var carToDelete by remember { mutableStateOf<Car?>(null) }
    var carForRotationInterval by remember { mutableStateOf<Car?>(null) }
    var carForPhotoCustomizer by remember { mutableStateOf<Car?>(null) }
    val photoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (carForPhotoCustomizer != null) {
        val car = carForPhotoCustomizer!!
        VehiclePhotoCustomizerSheet(
            car = car,
            sheetState = photoSheetState,
            onGenerateAiImage = { color, trim, env ->
                onGenerateAiImage?.invoke(car, color, trim, env)
                    ?: Result.failure(IllegalStateException("AI generation not available"))
            },
            onSaveImageBytes = { bytes ->
                onSaveCustomImage?.invoke(car, bytes)
            },
            onPickImageUri = { uri ->
                onSaveImageFromUri?.invoke(car, uri)
            },
            onResetToStock = {
                onResetVehicleImage?.invoke(car)
            },
            onDismissRequest = { carForPhotoCustomizer = null }
        )
    }

    if (carForRotationInterval != null) {
        val car = carForRotationInterval!!
        TireRotationIntervalDialog(
            currentInterval = car.effectiveTireRotationInterval,
            onDismiss = { carForRotationInterval = null },
            onConfirm = { newInterval ->
                onUpdateCar?.invoke(car.copy(tireRotationInterval = newInterval))
                carForRotationInterval = null
            }
        )
    }

    if (carToDelete != null) {
        val car = carToDelete!!
        AlertDialog(
            onDismissRequest = { carToDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Vehicle") },
            text = {
                Text("Are you sure you want to remove your ${car.year} ${car.make} ${car.model}? This will also delete associated records.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCar(car.id)
                        carToDelete = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { carToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (cars.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No vehicles in your garage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add a vehicle using your VIN or select make and model manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                FilledTonalButton(onClick = onAddCarClick) {
                    Text("Add Vehicle")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(cars, key = { it.id }) { car ->
                VehicleCard(
                    car = car,
                    onClick = { onCarClick(car) },
                    onToggleArchive = { onToggleArchive(car.id) },
                    onDelete = { carToDelete = car },
                    onEditRotationInterval = { carForRotationInterval = car },
                    onOpenPhotoPicker = { carForPhotoCustomizer = car },
                    modifier = Modifier.animateItem(
                        fadeInSpec = ExpressiveMotionTokens.ItemFadeSpring,
                        fadeOutSpec = ExpressiveMotionTokens.ItemFadeSpring,
                        placementSpec = ExpressiveMotionTokens.ItemPlacementSpring
                    )
                )
            }
            item {
                Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
            }
        }
    }
}

@Composable
fun VehicleCard(
    car: Car,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onEditRotationInterval: (() -> Unit)? = null,
    onOpenPhotoPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .expressiveBounceClick(pressedScale = 0.98f, onClick = onClick)
            .alpha(if (car.isArchived) 0.65f else 1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Vehicle Photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                val imageModel = car.localImagePath ?: car.imageUrl
                if (!imageModel.isNullOrBlank()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = car.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }

                // Badges overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (car.isEv) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = "EV", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("EV", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }

                        if (car.isArchived) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Context menu
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            tonalElevation = 6.dp,
                            shadowElevation = 3.dp
                        ) {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(22.dp))
                        ) {
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier
                                    .width(230.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        shape = RoundedCornerShape(22.dp)
                                    ),
                                shape = RoundedCornerShape(22.dp),
                                tonalElevation = 6.dp,
                                shadowElevation = 10.dp
                            ) {
                                // 1. Featured Action: Vehicle Photo Customization
                                if (onOpenPhotoPicker != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Vehicle Photo",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Generate with AI or upload",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        onClick = {
                                            menuExpanded = false
                                            onOpenPhotoPicker()
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }

                                if (onEditRotationInterval != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Rotation Interval",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        onClick = {
                                            menuExpanded = false
                                            onEditRotationInterval()
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (car.isArchived) "Unarchive" else "Archive",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (car.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    onClick = {
                                        menuExpanded = false
                                        onToggleArchive()
                                    }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete Vehicle",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Info details
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = car.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val cleanTrim = car.trim?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
                val cleanBodyStyle = car.bodyStyle?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
                if (cleanTrim != null || cleanBodyStyle != null) {
                    val subtitle = listOfNotNull(cleanTrim, cleanBodyStyle).joinToString(" • ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Odometer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val formattedOdo = if (car.odometer != null) {
                            "${NumberFormat.getNumberInstance(Locale.US).format(car.odometer)} mi"
                        } else {
                            "No mileage logged"
                        }
                        Text(
                            text = formattedOdo,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val syncBadge = when {
                            car.telemetryStatus == "Asleep" -> " • Asleep"
                            car.lastOdometerSyncTime != null ->
                                OdometerSyncService.formatRelativeTime(car.lastOdometerSyncTime)?.let { " • $it" }
                            else -> null
                        }
                        if (syncBadge != null) {
                            Text(
                                text = syncBadge,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // License Plate
                    val cleanPlate = car.licensePlate?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
                    if (cleanPlate != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = cleanPlate.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
