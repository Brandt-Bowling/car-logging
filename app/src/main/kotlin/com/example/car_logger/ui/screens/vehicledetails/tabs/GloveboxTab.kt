package com.example.car_logger.ui.screens.vehicledetails.tabs

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Glovebox
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GloveboxTab(
    car: Car,
    glovebox: Glovebox?,
    isDriveSignedIn: Boolean,
    isSyncingDrive: Boolean,
    onOpenFolderPicker: () -> Unit,
    onSyncDriveFolder: () -> Unit,
    onUpdateOdometer: (Int) -> Unit,
    onUpdateDocument: (registration: String?, insurance: String?, manual: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditOdometerDialog by remember { mutableStateOf(false) }

    val regPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onUpdateDocument(it.toString(), null, null) }
    }
    val insPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onUpdateDocument(null, it.toString(), null) }
    }
    val manPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onUpdateDocument(null, null, it.toString()) }
    }

    if (showEditOdometerDialog) {
        EditOdometerDialog(
            initialOdometer = car.odometer ?: 0,
            onSave = {
                onUpdateOdometer(it)
                showEditOdometerDialog = false
            },
            onDismiss = { showEditOdometerDialog = false }
        )
    }

    val openDoc: (String?) -> Unit = { url ->
        val uri = Uri.parse(url)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { OdometerGloveboxCard(car = car, onEditClick = { showEditOdometerDialog = true }) }
        item {
            DriveSyncGloveboxCard(
                car = car,
                isDriveSignedIn = isDriveSignedIn,
                isSyncingDrive = isSyncingDrive,
                onOpenFolderPicker = onOpenFolderPicker,
                onSyncDriveFolder = onSyncDriveFolder
            )
        }
        item {
            Text(
                text = "Vehicle documents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            DocumentCard(
                title = "Vehicle registration",
                icon = Icons.Filled.Description,
                documentUri = glovebox?.registrationUrl,
                onUploadClick = { regPicker.launch(arrayOf("application/pdf", "image/*")) },
                onViewClick = { openDoc(glovebox?.registrationUrl) }
            )
        }
        item {
            DocumentCard(
                title = "Insurance card",
                icon = Icons.Filled.HealthAndSafety,
                documentUri = glovebox?.insuranceUrl,
                onUploadClick = { insPicker.launch(arrayOf("application/pdf", "image/*")) },
                onViewClick = { openDoc(glovebox?.insuranceUrl) }
            )
        }
        item {
            DocumentCard(
                title = "Owner's manual",
                icon = Icons.Filled.MenuBook,
                documentUri = glovebox?.manualUrl,
                onUploadClick = { manPicker.launch(arrayOf("application/pdf")) },
                onViewClick = { openDoc(glovebox?.manualUrl) }
            )
        }
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
private fun EditOdometerDialog(
    initialOdometer: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tempOdo by remember { mutableStateOf(initialOdometer.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit odometer") },
        text = {
            OutlinedTextField(
                value = tempOdo,
                onValueChange = { tempOdo = it },
                label = { Text("Odometer (miles)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { tempOdo.toIntOrNull()?.takeIf { it >= 0 }?.let(onSave) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun OdometerGloveboxCard(
    car: Car,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Odometer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val odoFormatted = if (car.odometer != null) {
                        "${NumberFormat.getNumberInstance(Locale.US).format(car.odometer)} miles"
                    } else {
                        "No mileage"
                    }
                    Text(odoFormatted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OdometerSyncService.formatRelativeTime(car.lastOdometerSyncTime)?.let { syncRel ->
                        Text(
                            text = "Synced $syncRel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit odometer")
            }
        }
    }
}

@Composable
private fun DriveSyncGloveboxCard(
    car: Car,
    isDriveSignedIn: Boolean,
    isSyncingDrive: Boolean,
    onOpenFolderPicker: () -> Unit,
    onSyncDriveFolder: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDriveSignedIn) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (isDriveSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Google Drive sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isDriveSignedIn) {
                Text(
                    text = "Folder: ${car.driveFolderName ?: "No folder selected"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenFolderPicker) {
                        Icon(Icons.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose folder")
                    }
                    if (!car.driveFolderId.isNullOrBlank()) {
                        OutlinedButton(onClick = onSyncDriveFolder, enabled = !isSyncingDrive) {
                            Text(if (isSyncingDrive) "Syncing..." else "Sync now")
                        }
                    }
                }
            } else {
                Text(
                    text = "Sign in to Google Drive in Settings to import receipts automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DocumentCard(
    title: String,
    icon: ImageVector,
    documentUri: String?,
    onUploadClick: () -> Unit,
    onViewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPresent = !documentUri.isNullOrBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .expressiveBounceClick(pressedScale = 0.98f),
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isPresent) "Saved on device" else "Not added",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isPresent) {
                IconButton(onClick = onViewClick) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "View", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onUploadClick) {
                Icon(Icons.Filled.UploadFile, contentDescription = "Upload document")
            }
        }
    }
}
