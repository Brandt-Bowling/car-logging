package com.example.car_logger.ui.screens.addcar

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.vehicle.VehicleData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCarModalSheet(
    sheetState: SheetState,
    viewModel: AddCarViewModel,
    onDismissRequest: () -> Unit,
    onCarAdded: (Car) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = VIN, 1 = Manual

    val vinInput by viewModel.vinInput.collectAsState()
    val isLoadingVin by viewModel.isLoadingVin.collectAsState()
    val vinError by viewModel.vinError.collectAsState()
    val decodedInfo by viewModel.decodedInfo.collectAsState()

    val selectedMake by viewModel.selectedMake.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedOdometer by viewModel.selectedOdometer.collectAsState()
    val licensePlate by viewModel.licensePlate.collectAsState()
    val isEv by viewModel.isEv.collectAsState()
    val selectedImageUrl by viewModel.selectedImageUrl.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Add Vehicle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("VIN Auto-Detect") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Manual Selection") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedTab == 0) {
                // VIN Input Section
                OutlinedTextField(
                    value = vinInput,
                    onValueChange = { viewModel.onVinChange(it) },
                    label = { Text("Vehicle Identification Number (VIN)") },
                    placeholder = { Text("17 characters") },
                    leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) },
                    isError = vinError != null,
                    supportingText = vinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.decodeVin() },
                    enabled = !isLoadingVin && vinInput.length >= 11,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoadingVin) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decoding VIN...")
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decode Vehicle Details")
                    }
                }

                if (decodedInfo != null) {
                    val info = decodedInfo!!
                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${info.year ?: ""} ${info.make ?: ""} ${info.model ?: ""}".trim(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!info.trim.isNullOrBlank() || !info.body.isNullOrBlank()) {
                                val subtitle = listOfNotNull(info.trim, info.body, info.engine).joinToString(" • ")
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Photo Carousel Picker
                    if (info.photoUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select Vehicle Photo", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(info.photoUrls) { photoUrl ->
                                val isSelected = photoUrl == selectedImageUrl
                                Box(
                                    modifier = Modifier
                                        .size(width = 140.dp, height = 95.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.selectedImageUrl.value = photoUrl }
                                ) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isSelected) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Manual Make & Model Dropdowns
                var makeExpanded by remember { mutableStateOf(false) }
                val allMakes = remember { VehicleData.getAllMakes() }

                ExposedDropdownMenuBox(
                    expanded = makeExpanded,
                    onExpandedChange = { makeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMake ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Make") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = makeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = makeExpanded,
                        onDismissRequest = { makeExpanded = false }
                    ) {
                        allMakes.forEach { make ->
                            DropdownMenuItem(
                                text = { Text(make) },
                                onClick = {
                                    viewModel.onMakeSelected(make)
                                    makeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedMake != null) {
                    var modelExpanded by remember { mutableStateOf(false) }
                    val modelsForMake = remember(selectedMake) { VehicleData.getModelsForMake(selectedMake!!) }

                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedModel ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            modelsForMake.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.onModelSelected(model)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Year Text Field
                var yearInput by remember { mutableStateOf(selectedYear.toString()) }
                OutlinedTextField(
                    value = yearInput,
                    onValueChange = {
                        yearInput = it
                        it.toIntOrNull()?.let { y -> viewModel.selectedYear.value = y }
                    },
                    label = { Text("Year") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Common fields (Odometer, License Plate, EV Toggle)
            var odoText by remember { mutableStateOf("") }
            OutlinedTextField(
                value = odoText,
                onValueChange = {
                    odoText = it
                    viewModel.selectedOdometer.value = it.toIntOrNull()
                },
                label = { Text("Current Odometer (miles)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            var plateText by remember { mutableStateOf(licensePlate) }
            OutlinedTextField(
                value = plateText,
                onValueChange = {
                    plateText = it
                    viewModel.licensePlate.value = it
                },
                label = { Text("License Plate (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Electric Vehicle (EV)", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = isEv,
                    onCheckedChange = { viewModel.isEv.value = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Vehicle Button
            Button(
                onClick = {
                    val car = viewModel.buildCar()
                    if (car != null) {
                        onCarAdded(car)
                    }
                },
                enabled = selectedMake != null && selectedModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Vehicle to Garage")
            }
        }
    }
}
