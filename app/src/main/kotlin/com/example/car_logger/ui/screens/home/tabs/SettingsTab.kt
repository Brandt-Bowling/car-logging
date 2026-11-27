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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Work
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.car_logger.BuildConfig
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ChargerSyncScheduler
import com.example.car_logger.services.DailyPollScheduler
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.WallConnectorType
import com.example.car_logger.services.WorkChargingSyncResult
import com.example.car_logger.services.WorkChargingSyncScheduler
import com.example.car_logger.services.WorkLocationCaptureResult
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
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
    val garageName by preferencesManager.garageNameFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_GARAGE_NAME)
    val geminiKey by preferencesManager.geminiApiKeyFlow.collectAsStateWithLifecycle(initialValue = null)
    val autoDevKey by preferencesManager.autoDevApiKeyFlow.collectAsStateWithLifecycle(initialValue = null)
    val currentUser by googleDriveService.currentUser.collectAsStateWithLifecycle()

    val wakePolicy by preferencesManager.wakePolicyFlow.collectAsStateWithLifecycle(initialValue = WakeProtectionPolicy.AWAKE_ONLY)
    val cooldownHours by preferencesManager.syncCooldownHoursFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_COOLDOWN_HOURS)
    val smartcarConnected by preferencesManager.smartcarConnectedFlow.collectAsStateWithLifecycle(initialValue = false)
    val teslaConnected by preferencesManager.teslaConnectedFlow.collectAsStateWithLifecycle(initialValue = false)
    val teslaAccessToken by preferencesManager.teslaAccessTokenFlow.collectAsStateWithLifecycle(initialValue = null)
    val hasTeslaLocationScope = remember(teslaAccessToken) {
        OdometerSyncService.hasTeslaLocationScope(teslaAccessToken)
    }
    val hasTeslaChargingHistoryScope = remember(teslaAccessToken) {
        OdometerSyncService.hasTeslaChargingHistoryScope(teslaAccessToken)
    }

    val dailyPollEnabled by preferencesManager.dailyPollEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val lastDailyPollTime by preferencesManager.lastDailyPollTimeFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val lastDailyPollStatus by preferencesManager.lastDailyPollStatusFlow.collectAsStateWithLifecycle(initialValue = null)
    val wallConnectorGen3CarId by preferencesManager.wallConnectorGen3CarIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val wallConnectorUniversalCarId by preferencesManager.wallConnectorUniversalCarIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val chargerSyncEnabled by preferencesManager.chargerSyncEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val chargerSyncHour by preferencesManager.chargerSyncHourFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_CHARGER_SYNC_HOUR)
    val chargerSyncMinute by preferencesManager.chargerSyncMinuteFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_CHARGER_SYNC_MINUTE)
    val lastChargerSyncTime by preferencesManager.lastChargerSyncTimeFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val lastChargerSyncStatus by preferencesManager.lastChargerSyncStatusFlow.collectAsStateWithLifecycle(initialValue = null)

    val workChargingSyncEnabled by preferencesManager.workChargingSyncEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val workWindowStartHour by preferencesManager.workChargingWindowStartHourFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_START_HOUR)
    val workWindowStartMinute by preferencesManager.workChargingWindowStartMinuteFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_START_MINUTE)
    val workWindowEndHour by preferencesManager.workChargingWindowEndHourFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_END_HOUR)
    val workWindowEndMinute by preferencesManager.workChargingWindowEndMinuteFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_END_MINUTE)
    val workChargingWeekdaysOnly by preferencesManager.workChargingWeekdaysOnlyFlow.collectAsStateWithLifecycle(initialValue = true)
    val lastWorkChargingSyncTime by preferencesManager.lastWorkChargingSyncTimeFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val lastWorkChargingSyncStatus by preferencesManager.lastWorkChargingSyncStatusFlow.collectAsStateWithLifecycle(initialValue = null)
    val workLocationLat by preferencesManager.workLocationLatFlow.collectAsStateWithLifecycle(initialValue = null)
    val workLocationLng by preferencesManager.workLocationLngFlow.collectAsStateWithLifecycle(initialValue = null)
    val workLocationRadiusMeters by preferencesManager.workLocationRadiusMetersFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_WORK_LOCATION_RADIUS_METERS)

    val superchargerSyncEnabled by preferencesManager.superchargerSyncEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val lastSuperchargerSyncTime by preferencesManager.lastSuperchargerSyncTimeFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val lastSuperchargerSyncStatus by preferencesManager.lastSuperchargerSyncStatusFlow.collectAsStateWithLifecycle(initialValue = null)

    val smartcarDcSyncEnabled by preferencesManager.smartcarDcSyncEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val smartcarDcCapability by preferencesManager.smartcarDcCapabilityFlow.collectAsStateWithLifecycle(initialValue = PreferencesManager.SMARTCAR_CAPABILITY_UNKNOWN)
    val lastSmartcarDcSyncTime by preferencesManager.lastSmartcarDcSyncTimeFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val lastSmartcarDcSyncStatus by preferencesManager.lastSmartcarDcSyncStatusFlow.collectAsStateWithLifecycle(initialValue = null)
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
    var workWindowPickerTarget by remember { mutableStateOf<String?>(null) } // "START" or "END"
    var showWorkLocationScopeDialog by remember { mutableStateOf(false) }
    var showWorkLocationWakeDialog by remember { mutableStateOf(false) }
    var showWorkChargeWakeDialog by remember { mutableStateOf(false) }
    var carPendingForceWake by remember { mutableStateOf<Car?>(null) }

    val is24Hour = DateFormat.is24HourFormat(context)
    val formattedChargerSyncTime = remember(chargerSyncHour, chargerSyncMinute, is24Hour) {
        val scheduledTime = LocalTime.of(chargerSyncHour.coerceIn(0, 23), chargerSyncMinute.coerceIn(0, 59))
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        scheduledTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
    val formattedWorkWindowStart = remember(workWindowStartHour, workWindowStartMinute, is24Hour) {
        val t = LocalTime.of(workWindowStartHour.coerceIn(0, 23), workWindowStartMinute.coerceIn(0, 59))
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        t.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
    val formattedWorkWindowEnd = remember(workWindowEndHour, workWindowEndMinute, is24Hour) {
        val t = LocalTime.of(workWindowEndHour.coerceIn(0, 23), workWindowEndMinute.coerceIn(0, 59))
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        t.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
    val formattedWorkWindowRange = "$formattedWorkWindowStart – $formattedWorkWindowEnd"

    fun launchTeslaOAuth(
        includeLocationScope: Boolean = hasTeslaLocationScope,
        includeChargingHistoryScope: Boolean = hasTeslaChargingHistoryScope
    ) {
        if (odometerSyncService == null) return
        coroutineScope.launch {
            val (id, _) = odometerSyncService.getEffectiveTeslaCredentials()
            if (!id.isNullOrBlank()) {
                val url = odometerSyncService.buildTeslaAuthUrl(
                    clientId = id,
                    includeLocationScope = includeLocationScope,
                    includeChargingHistoryScope = includeChargingHistoryScope
                )
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No web browser found to open link", Toast.LENGTH_SHORT).show()
                }
            } else {
                showTeslaDialog = true
            }
        }
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
        var includeChargingHistoryScope by remember { mutableStateOf(hasTeslaChargingHistoryScope) }
        var includeLocationScope by remember { mutableStateOf(hasTeslaLocationScope) }

        LaunchedEffect(Unit) {
            val (id, secret) = odometerSyncService?.getEffectiveTeslaCredentials()
                ?: preferencesManager.getTeslaCredentials()
            val (token, _, _) = preferencesManager.getTeslaTokens()
            clientId = id.orEmpty()
            clientSecret = secret.orEmpty()
            accessToken = token.orEmpty()
            includeChargingHistoryScope = OdometerSyncService.hasTeslaChargingHistoryScope(token)
            includeLocationScope = OdometerSyncService.hasTeslaLocationScope(token)
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
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Optional Tesla scopes (enable in portal first)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilterChip(
                                selected = includeChargingHistoryScope,
                                onClick = { includeChargingHistoryScope = !includeChargingHistoryScope },
                                leadingIcon = if (includeChargingHistoryScope) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = { Text("Supercharger history (vehicle_charging_cmds)", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                            FilterChip(
                                selected = includeLocationScope,
                                onClick = { includeLocationScope = !includeLocationScope },
                                leadingIcon = if (includeLocationScope) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = { Text("Work GPS (vehicle_location)", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setTeslaCredentials(clientId, clientSecret, accessToken = null)
                                    val url = odometerSyncService.buildTeslaAuthUrl(
                                        clientId = clientId,
                                        includeLocationScope = includeLocationScope,
                                        includeChargingHistoryScope = includeChargingHistoryScope
                                    )
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

    // Work Location Missing Scope Dialog
    if (showWorkLocationScopeDialog && odometerSyncService != null) {
        AlertDialog(
            onDismissRequest = { showWorkLocationScopeDialog = false },
            title = { Text("Enable Work GPS scope") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Saving your work location requires Tesla's optional vehicle_location scope. " +
                            "Make sure Vehicle Location is enabled for your app in the Tesla Developer Portal, then reconnect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DashboardLinkButton(
                        label = "Open Tesla developer portal",
                        url = DeveloperDashboardUrls.TESLA
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWorkLocationScopeDialog = false
                        launchTeslaOAuth(
                            includeLocationScope = true,
                            includeChargingHistoryScope = hasTeslaChargingHistoryScope
                        )
                    }
                ) {
                    Text("Reconnect with location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWorkLocationScopeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Work Location Wake Confirmation Dialog
    if (showWorkLocationWakeDialog && odometerSyncService != null) {
        AlertDialog(
            onDismissRequest = { showWorkLocationWakeDialog = false },
            title = { Text("Wake Tesla to save location?") },
            text = {
                Text("Your Tesla is currently asleep. Waking it briefly uses a small amount of battery to read its current GPS coordinates as your work location.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWorkLocationWakeDialog = false
                        Toast.makeText(context, "Waking Tesla to save work location...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val result = odometerSyncService.captureCurrentTeslaWorkLocation(cars, forceWake = true)
                            val msg = when (result) {
                                is WorkLocationCaptureResult.Saved ->
                                    "Saved work GPS (${String.format(Locale.US, "%.4f, %.4f", result.latitude, result.longitude)})"
                                is WorkLocationCaptureResult.MissingScope -> {
                                    showWorkLocationScopeDialog = true
                                    result.message
                                }
                                is WorkLocationCaptureResult.SkippedAsleep -> result.message
                                WorkLocationCaptureResult.NotConfigured -> "Connect Tesla first"
                                is WorkLocationCaptureResult.Error -> result.message
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Wake & save", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWorkLocationWakeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Work Charge Manual Sync Wake Dialog
    if (showWorkChargeWakeDialog && odometerSyncService != null) {
        AlertDialog(
            onDismissRequest = { showWorkChargeWakeDialog = false },
            title = { Text("Tesla is asleep") },
            text = {
                Text("Zero-wake check did not find cached charge data. Wake your Tesla briefly to check for a work charge now?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWorkChargeWakeDialog = false
                        Toast.makeText(context, "Waking Tesla to check work charge...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val res = odometerSyncService.syncTeslaWorkCharging(cars, forceWake = true)
                            Toast.makeText(
                                context,
                                OdometerSyncService.formatWorkChargingSyncMessage(res),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("Wake & sync", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWorkChargeWakeDialog = false }) {
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

    if (workWindowPickerTarget != null) {
        val editingStart = workWindowPickerTarget == "START"
        val timePickerState = rememberTimePickerState(
            initialHour = if (editingStart) workWindowStartHour else workWindowEndHour,
            initialMinute = if (editingStart) workWindowStartMinute else workWindowEndMinute,
            is24Hour = is24Hour
        )
        var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Scroll) }
        val supportsScrollMode = LocalConfiguration.current.screenHeightDp.dp > TimePickerDialogDefaults.MinHeightForTimePicker

        VibrantTimePickerDialog(
            onDismissRequest = { workWindowPickerTarget = null },
            confirmButton = {
                TextButton(
                    enabled = timePickerState.isInputValid,
                    onClick = {
                        val selectedHour = timePickerState.hour
                        val selectedMinute = timePickerState.minute
                        workWindowPickerTarget = null
                        val newStartH = if (editingStart) selectedHour else workWindowStartHour
                        val newStartM = if (editingStart) selectedMinute else workWindowStartMinute
                        val newEndH = if (editingStart) workWindowEndHour else selectedHour
                        val newEndM = if (editingStart) workWindowEndMinute else selectedMinute
                        coroutineScope.launch {
                            preferencesManager.setWorkChargingWindow(newStartH, newStartM, newEndH, newEndM)
                            if (workChargingSyncEnabled) {
                                WorkChargingSyncScheduler.schedule(
                                    context = context,
                                    startHour = preferencesManager.getWorkChargingWindowStartHour(),
                                    startMinute = preferencesManager.getWorkChargingWindowStartMinute(),
                                    endHour = preferencesManager.getWorkChargingWindowEndHour(),
                                    endMinute = preferencesManager.getWorkChargingWindowEndMinute()
                                )
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { workWindowPickerTarget = null }) {
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
                text = if (editingStart) "Work check window start" else "Work check window end",
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
                                        onClick = { launchTeslaOAuth() },
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

        // Work charging section (Free Tesla J1772 auto-capture)
        item {
            val workIconContainer = ExpressiveThemeTokens.workIconContainer
            val workIconTint = ExpressiveThemeTokens.workIconTint
            val workStatusText = formatWorkChargingSubtitle(
                teslaConnected = teslaConnected,
                lastSyncTimeMs = lastWorkChargingSyncTime.takeIf { it > 0L },
                lastSyncStatus = lastWorkChargingSyncStatus,
                syncEnabled = workChargingSyncEnabled,
                weekdaysOnly = workChargingWeekdaysOnly,
                formattedWindowRange = formattedWorkWindowRange
            )
            val hasWorkError = teslaConnected && isWorkChargingErrorStatus(lastWorkChargingSyncStatus)
            val hasWorkGps = workLocationLat != null && workLocationLng != null

            SettingsSectionHeader(title = "Work charging")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header row
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
                                    .background(workIconContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Work,
                                    contentDescription = null,
                                    tint = workIconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Work charging (Tesla)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = workStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        hasWorkError -> MaterialTheme.colorScheme.error
                                        teslaConnected && lastWorkChargingSyncTime > 0L -> workIconTint
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
                                        "Checking Tesla for work charge...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    coroutineScope.launch {
                                        val res = odometerSyncService.syncTeslaWorkCharging(cars, forceWake = false)
                                        if (res is WorkChargingSyncResult.SkippedAsleep) {
                                            showWorkChargeWakeDialog = true
                                        } else {
                                            Toast.makeText(
                                                context,
                                                OdometerSyncService.formatWorkChargingSyncMessage(res),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Sync")
                            }
                        }
                    }

                    // Auto-capture schedule row (hourly zero-wake window)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-capture during work",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Hourly zero-wake checks — catches variable departure times before home charging",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = workChargingSyncEnabled,
                                modifier = Modifier.semantics { contentDescription = "Auto-capture during work" },
                                onCheckedChange = { isChecked ->
                                    coroutineScope.launch {
                                        preferencesManager.setWorkChargingSyncEnabled(isChecked)
                                        if (isChecked) {
                                            WorkChargingSyncScheduler.schedule(
                                                context = context,
                                                startHour = workWindowStartHour,
                                                startMinute = workWindowStartMinute,
                                                endHour = workWindowEndHour,
                                                endMinute = workWindowEndMinute
                                            )
                                        } else {
                                            WorkChargingSyncScheduler.cancel(context)
                                        }
                                    }
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = workChargingSyncEnabled,
                                onClick = { workWindowPickerTarget = "START" },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(formattedWorkWindowStart) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit work window start time",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                            Text(
                                text = "–",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilterChip(
                                selected = workChargingSyncEnabled,
                                onClick = { workWindowPickerTarget = "END" },
                                label = { Text(formattedWorkWindowEnd) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit work window end time",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                        FilterChip(
                            selected = workChargingWeekdaysOnly,
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setWorkChargingWeekdaysOnly(!workChargingWeekdaysOnly)
                                }
                            },
                            leadingIcon = if (workChargingWeekdaysOnly) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else {
                                null
                            },
                            label = { Text("Weekdays only") }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Optional Work GPS row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = if (hasWorkGps) workIconTint else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Work location (GPS)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (hasWorkGps) {
                                        "Saved (${String.format(Locale.US, "%.4f, %.4f", workLocationLat, workLocationLng)} · ${workLocationRadiusMeters}m)"
                                    } else {
                                        "Optional • using Home Wall Connector filter"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasWorkGps) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            preferencesManager.clearWorkLocation()
                                            Toast.makeText(context, "Cleared work GPS location", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Clear")
                                }
                            }
                            if (teslaConnected && odometerSyncService != null) {
                                TextButton(
                                    onClick = {
                                        Toast.makeText(context, "Reading Tesla location...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            when (val result = odometerSyncService.captureCurrentTeslaWorkLocation(cars, forceWake = false)) {
                                                is WorkLocationCaptureResult.Saved -> {
                                                    Toast.makeText(
                                                        context,
                                                        "Saved work GPS (${String.format(Locale.US, "%.4f, %.4f", result.latitude, result.longitude)})",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                is WorkLocationCaptureResult.MissingScope -> {
                                                    showWorkLocationScopeDialog = true
                                                }
                                                is WorkLocationCaptureResult.SkippedAsleep -> {
                                                    showWorkLocationWakeDialog = true
                                                }
                                                WorkLocationCaptureResult.NotConfigured -> {
                                                    Toast.makeText(context, "Connect Tesla first", Toast.LENGTH_SHORT).show()
                                                }
                                                is WorkLocationCaptureResult.Error -> {
                                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("Set from car")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Public & DC charging section (Tesla Superchargers + Smartcar DC)
        item {
            val scSubtitle = formatSuperchargerSubtitle(
                teslaConnected = teslaConnected,
                hasChargingScope = hasTeslaChargingHistoryScope,
                lastSyncTimeMs = lastSuperchargerSyncTime.takeIf { it > 0L },
                lastSyncStatus = lastSuperchargerSyncStatus,
                syncEnabled = superchargerSyncEnabled
            )
            val showSuperchargerScopeBanner = teslaConnected &&
                (!hasTeslaChargingHistoryScope || isSuperchargerScopeError(lastSuperchargerSyncStatus))

            val smartcarDcSubtitle = formatSmartcarDcSubtitle(
                smartcarConnected = smartcarConnected,
                capability = smartcarDcCapability,
                lastSyncTimeMs = lastSmartcarDcSyncTime.takeIf { it > 0L },
                lastSyncStatus = lastSmartcarDcSyncStatus,
                syncEnabled = smartcarDcSyncEnabled
            )
            val showSmartcarPermissionBanner = smartcarConnected &&
                smartcarDcCapability == PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION

            SettingsSectionHeader(title = "Public & DC charging")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Tesla Superchargers
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
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tesla Superchargers",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = scSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        showSuperchargerScopeBanner -> MaterialTheme.colorScheme.error
                                        teslaConnected && lastSuperchargerSyncTime > 0L -> MaterialTheme.colorScheme.primary
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
                                        "Syncing Tesla Superchargers...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    coroutineScope.launch {
                                        val res = odometerSyncService.syncTeslaSuperchargerCharging(cars)
                                        Toast.makeText(
                                            context,
                                            OdometerSyncService.formatSuperchargerSyncMessage(res),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            ) {
                                Text("Sync")
                            }
                        }
                    }

                    if (showSuperchargerScopeBanner && odometerSyncService != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Enable vehicle_charging_cmds in Tesla Developer Portal, then reconnect to pull paid Supercharger sessions (Model 3 & Blazer EV).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(DeveloperDashboardUrls.TESLA))
                                                )
                                            } catch (_: ActivityNotFoundException) {
                                                Toast.makeText(context, "No web browser found to open link", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Portal", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            launchTeslaOAuth(
                                                includeLocationScope = hasTeslaLocationScope,
                                                includeChargingHistoryScope = true
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1.3f)
                                    ) {
                                        Text("Enable & reconnect", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-sync Superchargers",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Zero-wake pull with daily charging sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = superchargerSyncEnabled,
                            modifier = Modifier.semantics { contentDescription = "Auto-sync Superchargers" },
                            onCheckedChange = { isChecked ->
                                coroutineScope.launch {
                                    preferencesManager.setSuperchargerSyncEnabled(isChecked)
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Smartcar DC Charging (Blazer EV)
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
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EvStation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Smartcar DC charging",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = smartcarDcSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        showSmartcarPermissionBanner -> MaterialTheme.colorScheme.error
                                        smartcarConnected && lastSmartcarDcSyncTime > 0L &&
                                            smartcarDcCapability != PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED ->
                                            MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        if (smartcarConnected && odometerSyncService != null) {
                            TextButton(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "Checking Smartcar DC charging...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    coroutineScope.launch {
                                        val res = odometerSyncService.syncSmartcarDcCharging(
                                            cars = cars,
                                            forceCapabilityRecheck = true,
                                            allowLiveProbe = true
                                        )
                                        Toast.makeText(
                                            context,
                                            OdometerSyncService.formatSmartcarDcSyncMessage(res),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            ) {
                                Text("Check / Sync")
                            }
                        }
                    }

                    if (showSmartcarPermissionBanner && odometerSyncService != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Enable charge records or fast-charger signals under Vehicle Access in the Smartcar dashboard, then reconnect.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(DeveloperDashboardUrls.SMARTCAR))
                                                )
                                            } catch (_: ActivityNotFoundException) {
                                                Toast.makeText(context, "No web browser found to open link", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Dashboard", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val (clientId, _) = odometerSyncService.getEffectiveSmartcarCredentials()
                                                val url = odometerSyncService.resolveSmartcarAuthUrl(clientId.orEmpty())
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                } catch (_: ActivityNotFoundException) {
                                                    Toast.makeText(context, "No web browser found to open link", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Text("Reconnect", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-sync Smartcar DC",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (smartcarDcCapability == PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED) {
                                    "Skipped in background — vehicle does not expose charge history"
                                } else {
                                    "Runs with daily sync when supported by vehicle"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = smartcarDcSyncEnabled,
                            modifier = Modifier.semantics { contentDescription = "Auto-sync Smartcar DC" },
                            onCheckedChange = { isChecked ->
                                coroutineScope.launch {
                                    preferencesManager.setSmartcarDcSyncEnabled(isChecked)
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

internal fun isWorkChargingErrorStatus(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    return status.contains("failed", ignoreCase = true) ||
        status.contains("expired", ignoreCase = true) ||
        status.contains("unauthorized", ignoreCase = true) ||
        status.contains("401") ||
        status.contains("403") ||
        status.contains("error", ignoreCase = true) ||
        status.contains("reconnect", ignoreCase = true)
}

internal fun formatWorkChargingSubtitle(
    teslaConnected: Boolean,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    syncEnabled: Boolean,
    weekdaysOnly: Boolean,
    formattedWindowRange: String,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!teslaConnected) return "Connect Tesla to auto-capture free work charges"

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    if (isWorkChargingErrorStatus(lastSyncStatus)) {
        val status = lastSyncStatus.orEmpty()
        val needsReconnect = status.contains("expired", ignoreCase = true) ||
            status.contains("unauthorized", ignoreCase = true) ||
            status.contains("401") ||
            status.contains("403") ||
            status.contains("reconnect", ignoreCase = true)
        val shortError = if (needsReconnect) "Reconnect required" else "Check failed"
        return if (syncTimeText != null) "Checked $syncTimeText • $shortError" else shortError
    }

    if (syncTimeText != null) {
        val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
        return if (detail != null) {
            "Checked $syncTimeText • $detail"
        } else {
            "Checked $syncTimeText"
        }
    }

    return if (syncEnabled) {
        val freq = if (weekdaysOnly) "weekdays" else "daily"
        "Checks hourly $freq, $formattedWindowRange (zero-wake)"
    } else {
        "Auto-capture off"
    }
}

internal fun isSuperchargerScopeError(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    return status.contains("vehicle_charging_cmds", ignoreCase = true) ||
        status.contains("missing", ignoreCase = true) ||
        status.contains("403") ||
        status.contains("forbidden", ignoreCase = true) ||
        status.contains("reconnect", ignoreCase = true)
}

internal fun formatSuperchargerSubtitle(
    teslaConnected: Boolean,
    hasChargingScope: Boolean,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    syncEnabled: Boolean,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!teslaConnected) return "Connect Tesla to pull paid Supercharger sessions"
    if (!hasChargingScope || isSuperchargerScopeError(lastSyncStatus)) {
        return "Enable vehicle_charging_cmds scope to sync"
    }

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    if (syncTimeText != null) {
        val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
        return if (detail != null) {
            "Synced $syncTimeText • $detail"
        } else {
            "Synced $syncTimeText"
        }
    }

    return if (syncEnabled) {
        "Ready • Zero-wake sync with daily charging sync"
    } else {
        "Auto-sync off"
    }
}

internal fun formatSmartcarDcSubtitle(
    smartcarConnected: Boolean,
    capability: String,
    lastSyncTimeMs: Long?,
    lastSyncStatus: String?,
    syncEnabled: Boolean,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (!smartcarConnected) return "Connect Smartcar to check DC fast charging"

    val syncTimeText = OdometerSyncService.formatRelativeTime(lastSyncTimeMs, nowMs)
    return when (capability) {
        PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION ->
            "Enable charging permissions in Smartcar dashboard"
        PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED ->
            "History unavailable on this vehicle • use receipt scan or Tesla Supercharger sync"
        PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY -> {
            val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
            if (syncTimeText != null && detail != null) {
                "Live DC only • Checked $syncTimeText • $detail"
            } else {
                "Live DC fast-charge detection available (no history)"
            }
        }
        PreferencesManager.SMARTCAR_CAPABILITY_CHARGE_RECORDS -> {
            val detail = lastSyncStatus?.trim()?.takeIf { it.isNotEmpty() }
            when {
                syncTimeText != null && detail != null -> "Synced $syncTimeText • $detail"
                syncTimeText != null -> "Synced $syncTimeText"
                syncEnabled -> "Supported • Syncs with daily charging sync"
                else -> "Supported • Auto-sync off"
            }
        }
        else -> {
            if (syncTimeText != null && !lastSyncStatus.isNullOrBlank()) {
                "Checked $syncTimeText • ${lastSyncStatus.trim()}"
            } else {
                "Tap Check / Sync to probe vehicle DC charging support"
            }
        }
    }
}
