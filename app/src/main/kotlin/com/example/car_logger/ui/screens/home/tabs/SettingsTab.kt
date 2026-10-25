package com.example.car_logger.ui.screens.home.tabs

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(
    preferencesManager: PreferencesManager,
    googleDriveService: GoogleDriveService,
    onOpenFolderPicker: () -> Unit,
    odometerSyncService: OdometerSyncService? = null,
    cars: List<Car> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val garageName by preferencesManager.garageNameFlow.collectAsState(initial = PreferencesManager.DEFAULT_GARAGE_NAME)
    val syncFolderId by preferencesManager.syncFolderIdFlow.collectAsState(initial = null)
    val syncFolderName by preferencesManager.syncFolderNameFlow.collectAsState(initial = null)
    val geminiKey by preferencesManager.geminiApiKeyFlow.collectAsState(initial = null)
    val autoDevKey by preferencesManager.autoDevApiKeyFlow.collectAsState(initial = null)
    val currentUser by googleDriveService.currentUser.collectAsState()

    val wakePolicy by preferencesManager.wakePolicyFlow.collectAsState(initial = WakeProtectionPolicy.AWAKE_ONLY)
    val cooldownHours by preferencesManager.syncCooldownHoursFlow.collectAsState(initial = PreferencesManager.DEFAULT_COOLDOWN_HOURS)
    val smartcarConnected by preferencesManager.smartcarConnectedFlow.collectAsState(initial = false)
    val teslaConnected by preferencesManager.teslaConnectedFlow.collectAsState(initial = false)

    var showGarageNameDialog by remember { mutableStateOf(false) }
    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var showAutoDevKeyDialog by remember { mutableStateOf(false) }
    var showSmartcarDialog by remember { mutableStateOf(false) }
    var showTeslaDialog by remember { mutableStateOf(false) }
    var carPendingForceWake by remember { mutableStateOf<Car?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            googleDriveService.handleSignInResult(account)
        } catch (_: Exception) {}
    }

    // Rename Garage Dialog
    if (showGarageNameDialog) {
        var tempName by remember { mutableStateOf(garageName) }
        AlertDialog(
            onDismissRequest = { showGarageNameDialog = false },
            title = { Text("Rename Garage") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Garage Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            preferencesManager.setGarageName(tempName)
                            showGarageNameDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGarageNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Gemini API Key Dialog
    if (showGeminiKeyDialog) {
        var tempKey by remember { mutableStateOf(geminiKey ?: "") }
        AlertDialog(
            onDismissRequest = { showGeminiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text(
                        "Enter your Google AI Studio API key to enable automated receipt parsing using Gemini 2.5 Flash.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            preferencesManager.setGeminiApiKey(tempKey)
                            showGeminiKeyDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Auto.dev API Key Dialog
    if (showAutoDevKeyDialog) {
        var tempKey by remember { mutableStateOf(autoDevKey ?: "") }
        AlertDialog(
            onDismissRequest = { showAutoDevKeyDialog = false },
            title = { Text("Auto.dev API Key") },
            text = {
                Column {
                    Text(
                        "Enter your custom auto.dev API key for automotive VIN decoding and vehicle photos. If blank, the bundled key is used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            preferencesManager.setAutoDevApiKey(tempKey)
                            showAutoDevKeyDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoDevKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Smartcar Setup Dialog (Chevy Blazer EV)
    if (showSmartcarDialog) {
        var clientId by remember { mutableStateOf("") }
        var clientSecret by remember { mutableStateOf("") }
        var accessToken by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val (id, secret) = preferencesManager.getSmartcarCredentials()
            val (token, _, _) = preferencesManager.getSmartcarTokens()
            clientId = id.orEmpty()
            clientSecret = secret.orEmpty()
            accessToken = token.orEmpty()
        }

        AlertDialog(
            onDismissRequest = { showSmartcarDialog = false },
            title = { Text("Smartcar (GM / Blazer EV)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        label = { Text("Client Secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (clientId.isNotBlank() && odometerSyncService != null) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setSmartcarCredentials(clientId, clientSecret, accessToken.ifBlank { null })
                                    val url = odometerSyncService.buildSmartcarAuthUrl(clientId)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    showSmartcarDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign In with Smartcar")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            preferencesManager.setSmartcarCredentials(clientId, clientSecret, accessToken)
                            showSmartcarDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartcarDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Tesla Fleet Setup Dialog (Model 3)
    if (showTeslaDialog) {
        var clientId by remember { mutableStateOf("") }
        var clientSecret by remember { mutableStateOf("") }
        var accessToken by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val (id, secret) = preferencesManager.getTeslaCredentials()
            val (token, _, _) = preferencesManager.getTeslaTokens()
            clientId = id.orEmpty()
            clientSecret = secret.orEmpty()
            accessToken = token.orEmpty()
        }

        AlertDialog(
            onDismissRequest = { showTeslaDialog = false },
            title = { Text("Tesla Fleet API") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        label = { Text("Client Secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (clientId.isNotBlank() && odometerSyncService != null) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setTeslaCredentials(clientId, clientSecret, accessToken.ifBlank { null })
                                    val url = odometerSyncService.buildTeslaAuthUrl(clientId)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    showTeslaDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign In with Tesla")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            preferencesManager.setTeslaCredentials(clientId, clientSecret, accessToken)
                            showTeslaDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTeslaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Wake Confirmation Dialog
    if (carPendingForceWake != null && odometerSyncService != null) {
        val targetCar = carPendingForceWake!!
        AlertDialog(
            onDismissRequest = { carPendingForceWake = null },
            title = { Text("Wake Vehicle?") },
            text = {
                Text("Waking ${targetCar.displayName} draws battery power. Poll odometer now?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val carToSync = targetCar
                        carPendingForceWake = null
                        coroutineScope.launch {
                            odometerSyncService.syncVehicle(carToSync, isManual = true, forceWake = true)
                        }
                    }
                ) {
                    Text("Wake & Poll", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { carPendingForceWake = null }) {
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
        // Garage Name Card
        item {
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Garage Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(garageName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showGarageNameDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Garage Name")
                    }
                }
            }
        }

        // Google Drive Sync Card
        item {
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
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (currentUser != null) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = if (currentUser != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Google Drive Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (currentUser != null) {
                        Text("Connected: ${currentUser?.email}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        val folderLabel = syncFolderName ?: "No sync folder selected"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Folder: $folderLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onOpenFolderPicker) {
                                Icon(Icons.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Folder")
                            }
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        googleDriveService.signOut()
                                    }
                                }
                            ) {
                                Text("Disconnect")
                            }
                        }
                    } else {
                        Text(
                            "Connect Google Drive to automatically scan and import maintenance receipt documents and photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                signInLauncher.launch(googleDriveService.getSignInIntent())
                            }
                        ) {
                            Text("Sign In with Google")
                        }
                    }
                }
            }
        }

        // Gemini AI Card
        item {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Receipt AI Parsing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val status = if (!geminiKey.isNullOrBlank()) "Active (Gemini 2.5 Flash)" else "Smart regex fallback (No API key)"
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showGeminiKeyDialog = true }) {
                        Icon(Icons.Filled.Key, contentDescription = "Configure Key")
                    }
                }
            }
        }

        // Auto.dev Card
        item {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vehicle Specs API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val status = if (!autoDevKey.isNullOrBlank()) "Custom API Key" else "Default auto.dev API Key"
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showAutoDevKeyDialog = true }) {
                        Icon(Icons.Filled.Key, contentDescription = "Configure Key")
                    }
                }
            }
        }

        // Odometer Telemetry & Wake Guard Card
        item {
            val gmCar = cars.firstOrNull { OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR }
            val teslaCar = cars.firstOrNull { OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_TESLA }

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
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Odometer Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    // Wake Protection Mode
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Wake Guard", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WakeProtectionPolicy.entries.forEach { policy ->
                                FilterChip(
                                    selected = wakePolicy == policy,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setWakePolicy(policy) }
                                    },
                                    label = { Text(policy.label) }
                                )
                            }
                        }
                    }

                    // Poll Cooldown
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Min Cooldown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(24 to "24h", 72 to "72h", 168 to "7d").forEach { (hours, label) ->
                                FilterChip(
                                    selected = cooldownHours == hours,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setSyncCooldownHours(hours) }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Smartcar (Blazer EV) Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smartcar (Blazer EV)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val gmSub = when {
                                !smartcarConnected -> "Not linked"
                                gmCar != null && OdometerSyncService.isCooldownActive(gmCar.lastOdometerSyncTime, cooldownHours) ->
                                    "Cooldown (${OdometerSyncService.remainingCooldownHours(gmCar.lastOdometerSyncTime, cooldownHours)}h)"
                                gmCar?.lastOdometerSyncTime != null ->
                                    "Synced ${OdometerSyncService.formatRelativeTime(gmCar.lastOdometerSyncTime)}"
                                else -> "Connected"
                            }
                            Text(gmSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!smartcarConnected && odometerSyncService != null) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val (id, _) = odometerSyncService.getEffectiveSmartcarCredentials()
                                            if (!id.isNullOrBlank()) {
                                                val url = odometerSyncService.buildSmartcarAuthUrl(id)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } else {
                                                showSmartcarDialog = true
                                            }
                                        }
                                    }
                                ) {
                                    Text("Connect")
                                }
                            } else if (smartcarConnected && gmCar != null && odometerSyncService != null) {
                                TextButton(onClick = { carPendingForceWake = gmCar }) {
                                    Text("Poll")
                                }
                            }
                            IconButton(onClick = { showSmartcarDialog = true }) {
                                Icon(Icons.Filled.Key, contentDescription = "Configure Smartcar")
                            }
                        }
                    }

                    // Tesla Fleet (Model 3) Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tesla Fleet (Model 3)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val teslaSub = when {
                                !teslaConnected -> "Not linked"
                                teslaCar?.telemetryStatus == "Asleep" -> "Asleep (No wake)"
                                teslaCar?.lastOdometerSyncTime != null ->
                                    "Synced ${OdometerSyncService.formatRelativeTime(teslaCar.lastOdometerSyncTime)}"
                                else -> "Connected"
                            }
                            Text(teslaSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!teslaConnected && odometerSyncService != null) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val (id, _) = odometerSyncService.getEffectiveTeslaCredentials()
                                            if (!id.isNullOrBlank()) {
                                                val url = odometerSyncService.buildTeslaAuthUrl(id)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } else {
                                                showTeslaDialog = true
                                            }
                                        }
                                    }
                                ) {
                                    Text("Connect")
                                }
                            } else if (teslaConnected && teslaCar != null && odometerSyncService != null) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            odometerSyncService.syncVehicle(teslaCar, isManual = true, forceWake = false)
                                        }
                                    }
                                ) {
                                    Text("Check")
                                }
                                TextButton(onClick = { carPendingForceWake = teslaCar }) {
                                    Text("Wake")
                                }
                            }
                            IconButton(onClick = { showTeslaDialog = true }) {
                                Icon(Icons.Filled.Key, contentDescription = "Configure Tesla")
                            }
                        }
                    }
                }
            }
        }

        // About Card
        item {
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Garage Journal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version 1.0.0 (Native Jetpack Compose)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
