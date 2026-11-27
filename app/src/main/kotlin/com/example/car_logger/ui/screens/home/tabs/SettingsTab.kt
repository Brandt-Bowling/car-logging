package com.example.car_logger.ui.screens.home.tabs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.car_logger.ui.screens.home.tabs.settings.AboutSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.ApiKeysSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.BackupSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.GeneralSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.HomeChargingSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.PublicChargingSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsActiveDialogHost
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsListCallbacks
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsUiEvent
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsUiState
import com.example.car_logger.ui.screens.home.tabs.settings.SettingsViewModel
import com.example.car_logger.ui.screens.home.tabs.settings.VehicleSyncSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.WorkChargingSettingsSection
import com.example.car_logger.ui.screens.home.tabs.settings.buildDialogCallbacks
import com.example.car_logger.ui.screens.home.tabs.settings.buildListCallbacks
import com.google.android.gms.auth.api.signin.GoogleSignIn

@Composable
fun SettingsTab(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val is24Hour = DateFormat.is24HourFormat(context)

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshAuthStatus()
        onPauseOrDispose {}
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            viewModel.handleGoogleSignInResult(task.result)
        } catch (_: Exception) {}
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.ShowToast -> {
                    val duration = if (event.longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    Toast.makeText(context, event.message, duration).show()
                }
                is SettingsUiEvent.OpenExternalUrl -> {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No web browser found to open link", Toast.LENGTH_SHORT).show()
                    }
                }
                SettingsUiEvent.LaunchGoogleSignIn -> signInLauncher.launch(viewModel.googleSignInIntent())
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsContentList(
            uiState = uiState,
            is24Hour = is24Hour,
            callbacks = viewModel.buildListCallbacks(
                hasPublicLocationScope = uiState.publicCharging.hasTeslaLocationScope
            )
        )
        SettingsActiveDialogHost(
            dialog = uiState.dialog,
            is24Hour = is24Hour,
            callbacks = viewModel.buildDialogCallbacks(
                hasWorkChargingHistoryScope = uiState.workCharging.hasTeslaChargingHistoryScope
            )
        )
    }
}

@Composable
private fun SettingsContentList(
    uiState: SettingsUiState,
    is24Hour: Boolean,
    callbacks: SettingsListCallbacks,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { GeneralSettingsSection(uiState.generalBackup.garageName, callbacks.onEditGarageName) }
        item {
            BackupSettingsSection(
                isDriveConnected = uiState.generalBackup.isDriveConnected,
                driveEmail = uiState.generalBackup.driveEmail,
                onSignInClick = callbacks.onSignInDrive,
                onDisconnectClick = callbacks.onDisconnectDrive
            )
        }
        item {
            VehicleSyncSettingsSection(
                state = uiState.vehicleSync,
                onSelectWakePolicy = callbacks.onSelectWakePolicy,
                onSelectCooldownHours = callbacks.onSelectCooldownHours,
                onToggleDailyPoll = callbacks.onToggleDailyPoll,
                onConnectSmartcar = callbacks.onConnectSmartcar,
                onConnectTesla = callbacks.onConnectTesla,
                onRequestForceWakeSync = callbacks.onRequestForceWakeSync
            )
        }
        item {
            HomeChargingSettingsSection(
                state = uiState.homeCharging,
                is24Hour = is24Hour,
                onSyncWallConnectors = callbacks.onSyncWallConnectors,
                onReconnectTesla = callbacks.onReconnectTesla,
                onAssignWallConnector = callbacks.onAssignWallConnector,
                onEditSyncTime = callbacks.onEditChargerSyncTime,
                onToggleChargerSync = callbacks.onToggleChargerSync
            )
        }
        item {
            WorkChargingSettingsSection(
                state = uiState.workCharging,
                is24Hour = is24Hour,
                onSyncWorkCharging = callbacks.onSyncWorkCharging,
                onToggleWorkChargingSync = callbacks.onToggleWorkChargingSync,
                onEditWorkWindowStart = callbacks.onEditWorkWindowStart,
                onEditWorkWindowEnd = callbacks.onEditWorkWindowEnd,
                onToggleWeekdaysOnly = callbacks.onToggleWeekdaysOnly,
                onClearWorkLocation = callbacks.onClearWorkLocation,
                onCaptureWorkLocation = callbacks.onCaptureWorkLocation
            )
        }
        item {
            PublicChargingSettingsSection(
                state = uiState.publicCharging,
                onSyncSuperchargers = callbacks.onSyncSuperchargers,
                onEnableAndReconnectTesla = callbacks.onEnableSuperchargerScope,
                onToggleSuperchargerSync = callbacks.onToggleSuperchargerSync,
                onSyncSmartcarDc = callbacks.onSyncSmartcarDc,
                onReconnectSmartcarDc = callbacks.onReconnectSmartcarDc,
                onToggleSmartcarDcSync = callbacks.onToggleSmartcarDcSync,
                onOpenUrl = callbacks.onOpenUrl
            )
        }
        item {
            ApiKeysSettingsSection(
                state = uiState.generalBackup,
                onOpenGeminiDialog = callbacks.onOpenGeminiDialog,
                onOpenAutoDevDialog = callbacks.onOpenAutoDevDialog,
                onOpenSmartcarDialog = callbacks.onOpenSmartcarDialog,
                onOpenTeslaDialog = callbacks.onOpenTeslaDialog
            )
        }
        item { AboutSettingsSection() }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
