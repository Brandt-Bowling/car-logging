package com.example.car_logger.ui.screens.home.tabs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TimeScroll
import androidx.compose.material3.VibrantTimePickerDialog
import androidx.compose.material3.isInputValid
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.car_logger.BuildConfig
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ChargerSyncScheduler
import com.example.car_logger.services.DailyPollScheduler
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.WallConnectorType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val wallConnectorGen3CarId by preferencesManager.wallConnectorGen3CarIdFlow.collectAsState(initial = null)
    val wallConnectorUniversalCarId by preferencesManager.wallConnectorUniversalCarIdFlow.collectAsState(initial = null)
    val chargerSyncEnabled by preferencesManager.chargerSyncEnabledFlow.collectAsState(initial = true)
    val chargerSyncHour by preferencesManager.chargerSyncHourFlow.collectAsState(initial = PreferencesManager.DEFAULT_CHARGER_SYNC_HOUR)
    val chargerSyncMinute by preferencesManager.chargerSyncMinuteFlow.collectAsState(initial = PreferencesManager.DEFAULT_CHARGER_SYNC_MINUTE)
    val lastChargerSyncTime by preferencesManager.lastChargerSyncTimeFlow.collectAsState(initial = 0L)
    val lastChargerSyncStatus by preferencesManager.lastChargerSyncStatusFlow.collectAsState(initial = null)
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
    var showChargerSyncTimePicker by remember { mutableStateOf(false) }
    var carPendingForceWake by remember { mutableStateOf<Car?>(null) }

    val is24Hour = DateFormat.is24HourFormat(context)
    val formattedChargerSyncTime = remember(chargerSyncHour, chargerSyncMinute, is24Hour) {
        val scheduledTime = LocalTime.of(chargerSyncHour.coerceIn(0, 23), chargerSyncMinute.coerceIn(0, 59))
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        scheduledTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

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
            title = { Text("Rename garage") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Garage name") },
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
            title = { Text("Gemini API key") },
            text = {
                Column {
                    Text(
                        "Add a Google AI Studio key to scan receipts and electric bills automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DashboardLinkButton(
                        label = "Open Google AI Studio",
                        url = DeveloperDashboardUrls.GEMINI
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API key") },
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
            title = { Text("Auto.dev API key") },
            text = {
                Column {
                    Text(
                        "Optional key for VIN lookup and stock photos. Leave blank to use the default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DashboardLinkButton(
                        label = "Open Auto.dev",
                        url = DeveloperDashboardUrls.AUTO_DEV
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API key") },
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
            title = { Text("Smartcar (Blazer EV)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardLinkButton(
                        label = "Open Smartcar dashboard",
                        url = DeveloperDashboardUrls.SMARTCAR
                    )
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
                        label = { Text("Client secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access token (optional)") },
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
                            Text(if (smartcarConnected) "Reconnect Smartcar" else "Sign in with Smartcar")
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
            title = { Text("Tesla Fleet (Model 3)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardLinkButton(
                        label = "Open Tesla developer portal",
                        url = DeveloperDashboardUrls.TESLA
                    )
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
                        label = { Text("Client secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access token (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (clientId.isNotBlank() && odometerSyncService != null) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setTeslaCredentials(clientId, clientSecret, accessToken = null)
                                    val url = odometerSyncService.buildTeslaAuthUrl(clientId)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    showTeslaDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (teslaConnected) "Reconnect Tesla" else "Sign in with Tesla")
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
            title = { Text("Wake vehicle?") },
            text = {
                Text("Waking ${targetCar.displayName} uses a small amount of battery. Sync odometer now?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val carToSync = targetCar
                        carPendingForceWake = null
                        Toast.makeText(
                            context,
                            "Syncing ${carToSync.displayName} odometer...",
                            Toast.LENGTH_SHORT
                        ).show()
                        coroutineScope.launch {
                            val result = odometerSyncService.syncVehicle(carToSync, isManual = true, forceWake = true)
                            Toast.makeText(
                                context,
                                OdometerSyncService.formatSyncResultMessage(carToSync, result),
                                Toast.LENGTH_LONG
                            ).show()
                            odometerSyncService.syncWallConnectorCharging(cars)
                        }
                    }
                ) {
                    Text("Wake & sync", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { carPendingForceWake = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showChargerSyncTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = chargerSyncHour,
            initialMinute = chargerSyncMinute,
            is24Hour = is24Hour
        )
        var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Scroll) }
        val supportsScrollMode = LocalConfiguration.current.screenHeightDp.dp > TimePickerDialogDefaults.MinHeightForTimePicker

        VibrantTimePickerDialog(
            onDismissRequest = { showChargerSyncTimePicker = false },
            confirmButton = {
                TextButton(
                    enabled = timePickerState.isInputValid,
                    onClick = {
                        val selectedHour = timePickerState.hour
                        val selectedMinute = timePickerState.minute
                        showChargerSyncTimePicker = false
                        coroutineScope.launch {
                            preferencesManager.setChargerSyncTime(selectedHour, selectedMinute)
                            if (chargerSyncEnabled) {
                                ChargerSyncScheduler.schedule(
                                    context = context,
                                    targetHour = selectedHour,
                                    targetMinute = selectedMinute
                                )
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChargerSyncTimePicker = false }) {
                    Text("Cancel")
                }
            },
            modeToggleButton = {
                if (supportsScrollMode) {
                    TimePickerDialogDefaults.ScrollDisplayModeToggle(
                        onDisplayModeChange = {
                            displayMode = if (displayMode == TimePickerDisplayMode.Scroll) {
                                TimePickerDisplayMode.Input
                            } else {
                                TimePickerDisplayMode.Scroll
                            }
                        },
                        displayMode = displayMode
                    )
                }
            }
        ) {
            Text(
                text = "Charging sync time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (displayMode == TimePickerDisplayMode.Scroll && supportsScrollMode) {
                TimeScroll(
                    state = timePickerState,
                    shapes = TimePickerDefaults.shapes()
                )
            } else {
                TimeInput(
                    state = timePickerState,
                    shapes = TimePickerDefaults.shapes()
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // General section
        item {
            SettingsSectionHeader(title = "General")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                SettingsRowItem(
                    icon = Icons.Filled.DirectionsCar,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = "Garage name",
                    subtitle = garageName,
                    onClick = { showGarageNameDialog = true },
                    trailing = {
                        IconButton(onClick = { showGarageNameDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit garage name")
                        }
                    }
                )
            }
        }

        // Backup section
        item {
            SettingsSectionHeader(title = "Backup")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                val isConnected = currentUser != null
                SettingsRowItem(
                    icon = if (isConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    iconContainerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    title = "Google Drive sync",
                    subtitle = if (isConnected) currentUser?.email.orEmpty() else "Back up and import service receipts",
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
                                Text("Sign in")
                            }
                        }
                    }
                )
            }
        }

        // Vehicle sync section
        item {
            val gmCar = cars.firstOrNull { OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR }
            val teslaCar = cars.firstOrNull { OdometerSyncService.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_TESLA }

            SettingsSectionHeader(title = "Vehicle sync")
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
                                text = "Odometer sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Automatic mileage updates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Sleep protection mode
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Sleep protection",
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

                    // Minimum wait
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Minimum wait",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(24 to "24 hr", 72 to "3 days", 168 to "7 days").forEach { (hours, label) ->
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

                    // Nightly mileage check
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Nightly mileage check",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Sync odometer daily at 11:00 PM",
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
                            modifier = Modifier.semantics { contentDescription = "Nightly mileage check" },
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
                            val gmSub = formatVehicleSyncSubtitle(
                                isConnected = smartcarConnected,
                                lastSyncTimeMs = gmCar?.lastOdometerSyncTime,
                                cooldownHours = cooldownHours,
                                showSyncedWhenDisconnected = true
                            )
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
                                    Text("Sync")
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
                            val teslaSub = formatVehicleSyncSubtitle(
                                isConnected = teslaConnected,
                                lastSyncTimeMs = teslaCar?.lastOdometerSyncTime,
                                cooldownHours = cooldownHours,
                                showSyncedWhenDisconnected = false
                            )
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
                                TextButton(onClick = { carPendingForceWake = teslaCar }) {
                                    Text("Sync")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Home charging section
        item {
            val activeEvs = remember(cars) { cars.filter { !it.isArchived }.ifEmpty { cars } }
            val mappedGen3Car = OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.GEN_3,
                cars = activeEvs,
                gen3OverrideCarId = wallConnectorGen3CarId,
                universalOverrideCarId = wallConnectorUniversalCarId
            )
            val mappedUniversalCar = OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.UNIVERSAL,
                cars = activeEvs,
                gen3OverrideCarId = wallConnectorGen3CarId,
                universalOverrideCarId = wallConnectorUniversalCarId
            )
            val chargerSyncTimeText = OdometerSyncService.formatRelativeTime(lastChargerSyncTime)
            val hasChargerError = teslaConnected && isWallConnectorErrorStatus(lastChargerSyncStatus)
            val chargerStatusText = formatWallConnectorSubtitle(
                teslaConnected = teslaConnected,
                lastSyncTimeMs = lastChargerSyncTime.takeIf { it > 0L },
                lastSyncStatus = lastChargerSyncStatus,
                chargerSyncEnabled = chargerSyncEnabled,
                formattedSyncTime = formattedChargerSyncTime
            )

            SettingsSectionHeader(title = "Home charging")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header row with EV charger icon & Sync action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EvStation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wall Connectors",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = chargerStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        hasChargerError -> MaterialTheme.colorScheme.error
                                        teslaConnected && chargerSyncTimeText != null -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        if (teslaConnected && odometerSyncService != null) {
                            TextButton(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "Syncing Wall Connectors...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    coroutineScope.launch {
                                        val res = odometerSyncService.syncWallConnectorCharging(cars)
                                        Toast.makeText(
                                            context,
                                            OdometerSyncService.formatWallConnectorSyncMessage(res),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            ) {
                                Text("Sync")
                            }
                        }
                    }

                    // Inline error banner with Reconnect action when auth/scope error occurs
                    if (hasChargerError && !lastChargerSyncStatus.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = lastChargerSyncStatus.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (odometerSyncService != null) {
                                    Button(
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
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Reconnect", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // Side-by-side Bento charger assignment tiles
                    if (activeEvs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Assigned vehicles",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                WallConnectorAssignmentTile(
                                    chargerName = "Gen 3",
                                    selectedCar = mappedGen3Car,
                                    fallbackLabel = "Model 3",
                                    vehicles = activeEvs,
                                    onSelectVehicle = { selected ->
                                        coroutineScope.launch {
                                            val currentUniversalId = wallConnectorUniversalCarId ?: mappedUniversalCar?.id
                                            preferencesManager.setWallConnectorCarMapping(
                                                gen3CarId = selected.id,
                                                universalCarId = currentUniversalId
                                            )
                                            val vehicleLabel = selected.model.ifBlank { selected.displayName }
                                            Toast.makeText(
                                                context,
                                                "Gen 3 assigned to $vehicleLabel",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                WallConnectorAssignmentTile(
                                    chargerName = "Universal",
                                    selectedCar = mappedUniversalCar,
                                    fallbackLabel = "Blazer EV",
                                    vehicles = activeEvs,
                                    onSelectVehicle = { selected ->
                                        coroutineScope.launch {
                                            val currentGen3Id = wallConnectorGen3CarId ?: mappedGen3Car?.id
                                            preferencesManager.setWallConnectorCarMapping(
                                                gen3CarId = currentGen3Id,
                                                universalCarId = selected.id
                                            )
                                            val vehicleLabel = selected.model.ifBlank { selected.displayName }
                                            Toast.makeText(
                                                context,
                                                "Universal assigned to $vehicleLabel",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Daily charging sync
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Daily charging sync",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            FilterChip(
                                selected = chargerSyncEnabled,
                                onClick = { showChargerSyncTimePicker = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        if (chargerSyncEnabled) {
                                            "Daily at $formattedChargerSyncTime"
                                        } else {
                                            "Paused ($formattedChargerSyncTime)"
                                        }
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit charging sync time",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                        Switch(
                            checked = chargerSyncEnabled,
                            modifier = Modifier.semantics { contentDescription = "Daily charging sync" },
                            onCheckedChange = { isChecked ->
                                coroutineScope.launch {
                                    preferencesManager.setChargerSyncEnabled(isChecked)
                                    if (isChecked) {
                                        ChargerSyncScheduler.schedule(
                                            context = context,
                                            targetHour = chargerSyncHour,
                                            targetMinute = chargerSyncMinute
                                        )
                                    } else {
                                        ChargerSyncScheduler.cancel(context)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // API keys section
        item {
            SettingsSectionHeader(title = "API keys")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                // Gemini AI (Receipt scanning)
                val geminiStatus = if (!geminiKey.isNullOrBlank()) {
                    "Gemini AI enabled"
                } else {
                    "Basic (filename only)"
                }
                SettingsRowItem(
                    icon = Icons.Filled.AutoAwesome,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Receipt scanning",
                    subtitle = geminiStatus,
                    onClick = { showGeminiKeyDialog = true },
                    trailing = {
                        IconButton(onClick = { showGeminiKeyDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Gemini key")
                        }
                    }
                )

                SettingsItemDivider()

                // Auto.dev (VIN lookup & photos)
                val autoDevStatus = if (!autoDevKey.isNullOrBlank()) {
                    "Custom key"
                } else {
                    "Default key"
                }
                SettingsRowItem(
                    icon = Icons.Filled.DirectionsCar,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = "VIN lookup",
                    subtitle = autoDevStatus,
                    onClick = { showAutoDevKeyDialog = true },
                    trailing = {
                        IconButton(onClick = { showAutoDevKeyDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Auto.dev key")
                        }
                    }
                )

                SettingsItemDivider()

                // Smartcar Developer Credentials
                val smartcarStatus = if (smartcarConnected) {
                    "Connected"
                } else {
                    "Not set up"
                }
                SettingsRowItem(
                    icon = Icons.Filled.Key,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = "Smartcar (Blazer EV)",
                    subtitle = smartcarStatus,
                    onClick = { showSmartcarDialog = true },
                    trailing = {
                        IconButton(onClick = { showSmartcarDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Smartcar credentials")
                        }
                    }
                )

                SettingsItemDivider()

                // Tesla Fleet Developer Credentials
                val teslaStatus = if (teslaConnected) {
                    "Connected"
                } else {
                    "Not set up"
                }
                SettingsRowItem(
                    icon = Icons.Filled.Key,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Tesla Fleet (Model 3)",
                    subtitle = teslaStatus,
                    onClick = { showTeslaDialog = true },
                    trailing = {
                        IconButton(onClick = { showTeslaDialog = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "Configure Tesla credentials")
                        }
                    }
                )
            }
        }

        // About section
        item {
            SettingsSectionHeader(title = "About")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                SettingsRowItem(
                    icon = Icons.Filled.Info,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Garage Journal",
                    subtitle = "Version ${BuildConfig.VERSION_NAME}"
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

@Composable
private fun DashboardLinkButton(
    label: String,
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No web browser found to open link", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun WallConnectorAssignmentTile(
    chargerName: String,
    selectedCar: Car?,
    fallbackLabel: String,
    vehicles: List<Car>,
    onSelectVehicle: (Car) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedCar?.model?.ifBlank { selectedCar.displayName } ?: fallbackLabel

    Box(modifier = modifier) {
        Surface(
            onClick = { if (vehicles.isNotEmpty()) expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$chargerName Wall Connector assigned to $selectedLabel" }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EvStation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = chargerName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            vehicles.forEach { carOption ->
                val optionLabel = carOption.model.ifBlank { carOption.displayName }
                val isSelected = selectedCar?.id == carOption.id
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (!isSelected) {
                            onSelectVehicle(carOption)
                        }
                    }
                )
            }
        }
    }
}

private val WALL_CONNECTOR_SYNCED_REGEX = Regex(
    pattern = """^Synced\s+(\d+)\s+Wall Connector\s+(sessions?)(.*)$""",
    option = RegexOption.IGNORE_CASE
)

internal fun formatVehicleSyncSubtitle(
    isConnected: Boolean,
    lastSyncTimeMs: Long?,
    cooldownHours: Int,
    showSyncedWhenDisconnected: Boolean = false,
    nowMs: Long = System.currentTimeMillis()
): String {
    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    return when {
        !isConnected && (!showSyncedWhenDisconnected || syncTimeText == null) -> "Not connected"
        syncTimeText != null && OdometerSyncService.isCooldownActive(lastSyncTimeMs, cooldownHours, nowMs) ->
            "Synced $syncTimeText • Next auto-sync in ${OdometerSyncService.remainingCooldownHours(lastSyncTimeMs, cooldownHours, nowMs)}h"
        syncTimeText != null -> "Synced $syncTimeText"
        else -> "Connected • Not yet synced"
    }
}

internal fun isWallConnectorErrorStatus(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    return status.contains("energy_device_data", ignoreCase = true) ||
        status.contains("failed", ignoreCase = true) ||
        status.contains("expired", ignoreCase = true) ||
        status.contains("unauthorized", ignoreCase = true) ||
        status.contains("401") ||
        status.contains("403") ||
        status.contains("error", ignoreCase = true) ||
        status.contains("forbidden", ignoreCase = true) ||
        status.contains("reconnect", ignoreCase = true)
}

internal fun formatWallConnectorSubtitle(
    teslaConnected: Boolean,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    chargerSyncEnabled: Boolean,
    formattedSyncTime: String,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!teslaConnected) return "Connect Tesla to sync home charging"

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    if (isWallConnectorErrorStatus(lastSyncStatus)) {
        val status = lastSyncStatus.orEmpty()
        val needsReconnect = status.contains("energy_device_data", ignoreCase = true) ||
            status.contains("expired", ignoreCase = true) ||
            status.contains("unauthorized", ignoreCase = true) ||
            status.contains("401") ||
            status.contains("403") ||
            status.contains("forbidden", ignoreCase = true) ||
            status.contains("reconnect", ignoreCase = true)
        val shortError = if (needsReconnect) "Reconnect required" else "Sync failed"
        return if (syncTimeText != null) "Last sync $syncTimeText • $shortError" else shortError
    }

    if (syncTimeText != null) {
        val compactDetail = compactWallConnectorStatus(lastSyncStatus)
        return if (compactDetail != null) {
            "Synced $syncTimeText • $compactDetail"
        } else {
            "Synced $syncTimeText"
        }
    }

    return if (chargerSyncEnabled) {
        "Syncs daily at $formattedSyncTime"
    } else {
        "Auto-sync off"
    }
}

private fun compactWallConnectorStatus(status: String?): String? {
    val trimmed = status?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    WALL_CONNECTOR_SYNCED_REGEX.matchEntire(trimmed)?.let { match ->
        val count = match.groupValues[1]
        val unit = match.groupValues[2]
        val suffix = match.groupValues[3]
        return "$count $unit$suffix"
    }

    if (trimmed.startsWith("Wall Connectors up to date", ignoreCase = true) ||
        trimmed.startsWith("Wall Connectors synced", ignoreCase = true)
    ) {
        return "Up to date"
    }

    return trimmed
}

