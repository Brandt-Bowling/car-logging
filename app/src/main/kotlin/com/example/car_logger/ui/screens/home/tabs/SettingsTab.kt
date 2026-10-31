package com.example.car_logger.ui.screens.home.tabs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.DailyPollScheduler
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(
    preferencesManager: PreferencesManager,
    googleDriveService: GoogleDriveService,
    odometerSyncService: OdometerSyncService? = null,
    cars: List<Car> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val garageName by preferencesManager.garageNameFlow.collectAsState(initial = PreferencesManager.DEFAULT_GARAGE_NAME)
    val geminiKey by preferencesManager.geminiApiKeyFlow.collectAsState(initial = null)
    val autoDevKey by preferencesManager.autoDevApiKeyFlow.collectAsState(initial = null)
    val currentUser by googleDriveService.currentUser.collectAsState()

    val wakePolicy by preferencesManager.wakePolicyFlow.collectAsState(initial = WakeProtectionPolicy.AWAKE_ONLY)
    val cooldownHours by preferencesManager.syncCooldownHoursFlow.collectAsState(initial = PreferencesManager.DEFAULT_COOLDOWN_HOURS)
    val smartcarConnected by preferencesManager.smartcarConnectedFlow.collectAsState(initial = false)
    val teslaConnected by preferencesManager.teslaConnectedFlow.collectAsState(initial = false)
    val dailyPollEnabled by preferencesManager.dailyPollEnabledFlow.collectAsState(initial = true)
    val lastDailyPollTime by preferencesManager.lastDailyPollTimeFlow.collectAsState(initial = 0L)
    val lastDailyPollStatus by preferencesManager.lastDailyPollStatusFlow.collectAsState(initial = null)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, odometerSyncService, cars) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && odometerSyncService != null) {
                coroutineScope.launch {
                    val isLinked = odometerSyncService.refreshSmartcarConnectionStatus()
                    val gmCar = cars.firstOrNull {
                        OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR
                    }
                    if (isLinked && gmCar != null && gmCar.lastOdometerSyncTime == null) {
                        val result = odometerSyncService.syncVehicle(gmCar, isManual = true, forceWake = true)
                        Toast.makeText(
                            context,
                            OdometerSyncService.formatSyncResultMessage(gmCar, result),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            val (id, secret) = odometerSyncService?.getEffectiveSmartcarCredentials()
                ?: preferencesManager.getSmartcarCredentials()
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
                    if (odometerSyncService != null) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    val tokenToSave = if (clientId.trim().startsWith("client_")) "" else accessToken.ifBlank { null }
                                    preferencesManager.setSmartcarCredentials(clientId, clientSecret, tokenToSave)
                                    val url = odometerSyncService.resolveSmartcarAuthUrl(clientId)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    showSmartcarDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (smartcarConnected) "Reconnect Smartcar" else "Sign In with Smartcar")
                        }
                    }
                    if (smartcarConnected) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.disconnectSmartcar()
                                    Toast.makeText(context, "Smartcar disconnected", Toast.LENGTH_SHORT).show()
                                    showSmartcarDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Smartcar")
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
            val (id, secret) = odometerSyncService?.getEffectiveTeslaCredentials()
                ?: preferencesManager.getTeslaCredentials()
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
                    if (teslaConnected) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.disconnectTesla()
                                    Toast.makeText(context, "Tesla disconnected", Toast.LENGTH_SHORT).show()
                                    showTeslaDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Tesla")
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
                        Toast.makeText(
                            context,
                            "Polling ${carToSync.displayName} odometer...",
                            Toast.LENGTH_SHORT
                        ).show()
                        coroutineScope.launch {
                            val result = odometerSyncService.syncVehicle(carToSync, isManual = true, forceWake = true)
                            Toast.makeText(
                                context,
                                OdometerSyncService.formatSyncResultMessage(carToSync, result),
                                Toast.LENGTH_LONG
                            ).show()
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
        // GENERAL Section
        item {
            SettingsSectionHeader(title = "GENERAL")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                SettingsRowItem(
                    icon = Icons.Filled.DirectionsCar,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = "Garage Name",
                    subtitle = garageName,
                    onClick = { showGarageNameDialog = true },
                    trailing = {
                        IconButton(onClick = { showGarageNameDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Garage Name")
                        }
                    }
                )
            }
        }

        // CLOUD & BACKUP Section
        item {
            SettingsSectionHeader(title = "CLOUD & BACKUP")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                val isConnected = currentUser != null
                SettingsRowItem(
                    icon = if (isConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    iconContainerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    title = "Google Drive Sync",
                    subtitle = if (isConnected) "Connected: ${currentUser?.email}" else "Auto-scan & import receipts and documents",
                    trailing = {
                        if (isConnected) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        googleDriveService.signOut()
                                    }
                                }
                            ) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(
                                onClick = {
                                    signInLauncher.launch(googleDriveService.getSignInIntent())
                                }
                            ) {
                                Text("Sign In")
                            }
                        }
                    }
                )
            }
        }

        // VEHICLE TELEMETRY Section
        item {
            val gmCar = cars.firstOrNull { OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR }
            val teslaCar = cars.firstOrNull { OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_TESLA }

            SettingsSectionHeader(title = "VEHICLE TELEMETRY")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header row with Icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Odometer Sync & Policies",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Battery-safe vehicle polling controls",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Wake Protection Mode
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Wake Guard",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Text(
                            "Min Cooldown",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Nightly End-of-Day Polling
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Nightly Mileage Snapshot",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Poll at 11:00 PM while plugged in overnight",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (dailyPollEnabled) {
                                val lastRunText = if (lastDailyPollTime > 0L) {
                                    val formattedTime = OdometerSyncService.formatRelativeTime(lastDailyPollTime)
                                    val statusText = lastDailyPollStatus?.let { " • $it" } ?: ""
                                    "Last run $formattedTime$statusText"
                                } else {
                                    "Scheduled for 11:00 PM tonight"
                                }
                                Text(
                                    text = lastRunText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Switch(
                            checked = dailyPollEnabled,
                            modifier = Modifier.semantics { contentDescription = "Nightly Mileage Snapshot" },
                            onCheckedChange = { isChecked ->
                                coroutineScope.launch {
                                    preferencesManager.setDailyPollEnabled(isChecked)
                                    if (isChecked) {
                                        DailyPollScheduler.schedule(context)
                                    } else {
                                        DailyPollScheduler.cancel(context)
                                    }
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Smartcar (Blazer EV) Row
                    val gmSyncTimeText = OdometerSyncService.formatRelativeTime(gmCar?.lastOdometerSyncTime)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Smartcar (Blazer EV)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val gmSub = when {
                                !smartcarConnected && gmSyncTimeText == null -> "Not linked"
                                gmSyncTimeText != null && gmCar != null && OdometerSyncService.isCooldownActive(gmCar.lastOdometerSyncTime, cooldownHours) ->
                                    "Synced $gmSyncTimeText • Cooldown (${OdometerSyncService.remainingCooldownHours(gmCar.lastOdometerSyncTime, cooldownHours)}h)"
                                gmSyncTimeText != null ->
                                    "Synced $gmSyncTimeText"
                                else -> "Connected • Not yet synced"
                            }
                            Text(
                                gmSub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if ((!smartcarConnected || gmSyncTimeText == null) && odometerSyncService != null) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val (id, _) = odometerSyncService.getEffectiveSmartcarCredentials()
                                            if (!id.isNullOrBlank() || OdometerSyncService.DEFAULT_SMARTCAR_APPLICATION_ID.isNotBlank()) {
                                                val url = odometerSyncService.resolveSmartcarAuthUrl(id.orEmpty())
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } else {
                                                showSmartcarDialog = true
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (smartcarConnected) "Reconnect" else "Connect")
                                }
                            }
                            if (gmCar != null && odometerSyncService != null) {
                                TextButton(onClick = { carPendingForceWake = gmCar }) {
                                    Text("Poll")
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Tesla Fleet (Model 3) Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tesla Fleet (Model 3)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val teslaSyncTimeText = OdometerSyncService.formatRelativeTime(teslaCar?.lastOdometerSyncTime)
                            val teslaSub = when {
                                !teslaConnected -> "Not linked"
                                teslaCar?.telemetryStatus == "Asleep" && teslaSyncTimeText != null ->
                                    "Synced $teslaSyncTimeText • Asleep (No wake)"
                                teslaCar?.telemetryStatus == "Asleep" -> "Asleep (No wake)"
                                teslaSyncTimeText != null ->
                                    "Synced $teslaSyncTimeText"
                                else -> "Connected • Not yet synced"
                            }
                            Text(
                                teslaSub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                        Toast.makeText(
                                            context,
                                            "Checking ${teslaCar.displayName} status...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        coroutineScope.launch {
                                            val result = odometerSyncService.syncVehicle(teslaCar, isManual = true, forceWake = false)
                                            Toast.makeText(
                                                context,
                                                OdometerSyncService.formatSyncResultMessage(teslaCar, result),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                ) {
                                    Text("Check")
                                }
                                TextButton(onClick = { carPendingForceWake = teslaCar }) {
                                    Text("Wake")
                                }
                            }
                        }
                    }
                }
            }
        }

        // API KEYS & INTEGRATIONS Section
        item {
            SettingsSectionHeader(title = "API KEYS & INTEGRATIONS")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                // Gemini AI (Receipt Parsing)
                val geminiStatus = if (!geminiKey.isNullOrBlank()) {
                    "Active (Gemini 2.5 Flash)"
                } else {
                    "Smart regex fallback (No custom API key)"
                }
                SettingsRowItem(
                    icon = Icons.Filled.AutoAwesome,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Receipt AI Parsing",
                    subtitle = geminiStatus,
                    onClick = { showGeminiKeyDialog = true },
                    trailing = {
                        IconButton(onClick = { showGeminiKeyDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Gemini Key")
                        }
                    }
                )

                SettingsItemDivider()

                // Auto.dev (Vehicle Specs & Photos)
                val autoDevStatus = if (!autoDevKey.isNullOrBlank()) {
                    "Custom auto.dev API key configured"
                } else {
                    "Default bundled auto.dev API key"
                }
                SettingsRowItem(
                    icon = Icons.Filled.DirectionsCar,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = "Vehicle Specs API",
                    subtitle = autoDevStatus,
                    onClick = { showAutoDevKeyDialog = true },
                    trailing = {
                        IconButton(onClick = { showAutoDevKeyDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Auto.dev Key")
                        }
                    }
                )

                SettingsItemDivider()

                // Smartcar Developer Credentials
                val smartcarStatus = if (smartcarConnected) {
                    "Linked & configured (Tap to edit credentials)"
                } else {
                    "Client ID & Client Secret (Tap to configure)"
                }
                SettingsRowItem(
                    icon = Icons.Filled.Key,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = "Smartcar API (GM / Blazer EV)",
                    subtitle = smartcarStatus,
                    onClick = { showSmartcarDialog = true },
                    trailing = {
                        IconButton(onClick = { showSmartcarDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Smartcar Credentials")
                        }
                    }
                )

                SettingsItemDivider()

                // Tesla Fleet Developer Credentials
                val teslaStatus = if (teslaConnected) {
                    "Linked & configured (Tap to edit credentials)"
                } else {
                    "Client ID & Client Secret (Tap to configure)"
                }
                SettingsRowItem(
                    icon = Icons.Filled.Key,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Tesla Fleet API (Model 3)",
                    subtitle = teslaStatus,
                    onClick = { showTeslaDialog = true },
                    trailing = {
                        IconButton(onClick = { showTeslaDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Tesla Credentials")
                        }
                    }
                )
            }
        }

        // ABOUT Section
        item {
            SettingsSectionHeader(title = "ABOUT")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                SettingsRowItem(
                    icon = Icons.Filled.Info,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Garage Journal",
                    subtitle = "Version 1.0.0 • Native Jetpack Compose"
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 12.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingsItemDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 70.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}
