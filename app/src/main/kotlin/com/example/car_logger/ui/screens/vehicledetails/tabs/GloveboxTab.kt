package com.example.car_logger.ui.screens.vehicledetails.tabs

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GloveboxTab(
    car: Car,
    glovebox: Glovebox?,
    onUpdateOdometer: (Int) -> Unit,
    onUpdateDocument: (registration: String?, insurance: String?, manual: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditOdometerDialog by remember { mutableStateOf(false) }

    // File pickers
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
        var tempOdo by remember { mutableStateOf((car.odometer ?: 0).toString()) }
        AlertDialog(
            onDismissRequest = { showEditOdometerDialog = false },
            title = { Text("Edit Vehicle Odometer") },
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
                TextButton(
                    onClick = {
                        val num = tempOdo.toIntOrNull()
                        if (num != null && num >= 0) {
                            onUpdateOdometer(num)
                            showEditOdometerDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditOdometerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vehicle Odometer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                            Text("Current Odometer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val odoFormatted = if (car.odometer != null) {
                                "${NumberFormat.getNumberInstance(Locale.US).format(car.odometer)} miles"
                            } else {
                                "Not logged"
                            }
                            Text(odoFormatted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { showEditOdometerDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Odometer")
                    }
                }
            }
        }

        // Glovebox Documents Header
        item {
            Text(
                text = "Vehicle Documents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Registration Card
        item {
            DocumentCard(
                title = "Vehicle Registration",
                icon = Icons.Filled.Description,
                documentUri = glovebox?.registrationUrl,
                onUploadClick = { regPicker.launch(arrayOf("application/pdf", "image/*")) },
                onViewClick = {
                    val uri = Uri.parse(glovebox!!.registrationUrl)
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            )
        }

        // Insurance Card
        item {
            DocumentCard(
                title = "Insurance Card",
                icon = Icons.Filled.HealthAndSafety,
                documentUri = glovebox?.insuranceUrl,
                onUploadClick = { insPicker.launch(arrayOf("application/pdf", "image/*")) },
                onViewClick = {
                    val uri = Uri.parse(glovebox!!.insuranceUrl)
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            )
        }

        // Owner's Manual Card
        item {
            DocumentCard(
                title = "Owner's Manual",
                icon = Icons.Filled.MenuBook,
                documentUri = glovebox?.manualUrl,
                onUploadClick = { manPicker.launch(arrayOf("application/pdf")) },
                onViewClick = {
                    val uri = Uri.parse(glovebox!!.manualUrl)
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(100.dp)) // Space for floating toolbar
        }
    }
}

@Composable
fun DocumentCard(
    title: String,
    icon: ImageVector,
    documentUri: String?,
    onUploadClick: () -> Unit,
    onViewClick: () -> Unit
) {
    val isPresent = !documentUri.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                        text = if (isPresent) "Document stored locally" else "No document uploaded",
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
                Icon(Icons.Filled.UploadFile, contentDescription = "Upload Document")
            }
        }
    }
}
