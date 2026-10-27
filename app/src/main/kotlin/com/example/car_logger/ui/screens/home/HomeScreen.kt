package com.example.car_logger.ui.screens.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.MaintenanceRecord
import com.example.car_logger.ui.components.EnvelopedBodyCanvas
import com.example.car_logger.ui.components.GarageNavigationBar
import com.example.car_logger.ui.components.GarageNavigationTab
import com.example.car_logger.ui.components.SpeedDialFab
import com.example.car_logger.ui.screens.home.tabs.ActivityTab
import com.example.car_logger.ui.screens.home.tabs.GarageTab
import com.example.car_logger.ui.screens.home.tabs.ScheduleTab
import com.example.car_logger.ui.screens.home.tabs.SettingsTab
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCarDetails: (String) -> Unit,
    onOpenAddCarSheet: () -> Unit,
    onOpenAddRecordDialog: (String?) -> Unit,
    onOpenRecordDialog: (MaintenanceRecord) -> Unit,
    onOpenAttachmentViewer: (MaintenanceRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isSpeedDialOpen by viewModel.isSpeedDialOpen.collectAsState()
    val garageName by viewModel.garageName.collectAsState()
    val cars by viewModel.cars.collectAsState()
    val allRecords by viewModel.allRecords.collectAsState()
    val scheduledServices by viewModel.scheduledServices.collectAsState()
    val hasUrgentService by viewModel.hasUrgentService.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showArchivedOnly by remember { mutableStateOf(false) }

    // Predictive back gesture state for Speed Dial FAB
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    if (isSpeedDialOpen) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    predictiveBackProgress = backEvent.progress
                }
                viewModel.closeSpeedDial()
            } catch (e: CancellationException) {
                predictiveBackProgress = 0f
            } finally {
                predictiveBackProgress = 0f
            }
        }
    }

    if (showRenameDialog) {
        var tempName by remember { mutableStateOf(garageName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
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
                        viewModel.updateGarageName(tempName)
                        showRenameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ExpressiveThemeTokens.shellColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showRenameDialog = true }
                        ) {
                            Text(
                                text = garageName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    },
                    actions = {
                        if (currentTab == GarageNavigationTab.GARAGE) {
                            val hasArchivedCars = cars.any { it.isArchived }
                            if (hasArchivedCars || showArchivedOnly) {
                                IconButton(onClick = { showArchivedOnly = !showArchivedOnly }) {
                                    Icon(
                                        imageVector = if (showArchivedOnly) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                        contentDescription = if (showArchivedOnly) "Show All Vehicles" else "Filter Archived Only",
                                        tint = if (showArchivedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ExpressiveThemeTokens.shellColor,
                        scrolledContainerColor = ExpressiveThemeTokens.shellColor
                    )
                )
            },
            bottomBar = {
                GarageNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.setTab(it) },
                    hasUrgentService = hasUrgentService
                )
            },
            floatingActionButton = {
                if (currentTab == GarageNavigationTab.GARAGE || currentTab == GarageNavigationTab.ACTIVITY) {
                    SpeedDialFab(
                        isOpen = isSpeedDialOpen,
                        onToggle = { viewModel.toggleSpeedDial() },
                        onAddCar = {
                            viewModel.closeSpeedDial()
                            onOpenAddCarSheet()
                        },
                        onAddRecord = {
                            viewModel.closeSpeedDial()
                            val defaultCarId = cars.firstOrNull { !it.isArchived }?.id ?: cars.firstOrNull()?.id
                            onOpenAddRecordDialog(defaultCarId)
                        },
                        predictiveBackProgress = predictiveBackProgress
                    )
                }
            }
        ) { innerPadding ->
            EnvelopedBodyCanvas(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Springy animated content transitions between navigation tabs
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> direction * fullWidth / 4 },
                            animationSpec = ExpressiveMotionTokens.SpatialOffsetSpring
                        ) + fadeIn(
                            animationSpec = ExpressiveMotionTokens.SpatialSpring
                        )).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -direction * fullWidth / 4 },
                                animationSpec = ExpressiveMotionTokens.SpatialOffsetSpring
                            ) + fadeOut(
                                animationSpec = ExpressiveMotionTokens.SpatialSpring
                            )
                        )
                    },
                    label = "home_tab_transition",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        GarageNavigationTab.GARAGE -> {
                            val displayedCars = if (showArchivedOnly) {
                                cars.filter { it.isArchived }
                            } else {
                                cars
                            }
                            GarageTab(
                                cars = displayedCars,
                                onCarClick = { onNavigateToCarDetails(it.id) },
                                onToggleArchive = { viewModel.toggleArchiveCar(it) },
                                onDeleteCar = { viewModel.deleteCar(it) },
                                onAddCarClick = onOpenAddCarSheet,
                                onUpdateCar = { viewModel.updateCar(it) }
                            )
                        }
                        GarageNavigationTab.ACTIVITY -> {
                            ActivityTab(
                                records = allRecords,
                                cars = cars,
                                onRecordClick = onOpenRecordDialog,
                                onAttachmentClick = onOpenAttachmentViewer
                            )
                        }
                        GarageNavigationTab.SCHEDULE -> {
                            ScheduleTab(
                                scheduledServices = scheduledServices
                            )
                        }
                        GarageNavigationTab.SETTINGS -> {
                            SettingsTab(
                                preferencesManager = viewModel.preferencesManager,
                                googleDriveService = viewModel.googleDriveService,
                                odometerSyncService = viewModel.odometerSyncService,
                                cars = cars
                            )
                        }
                    }
                }
            }
        }

        // Google-style scrim backdrop behind Speed Dial
        AnimatedVisibility(
            visible = isSpeedDialOpen,
            enter = fadeIn(animationSpec = ExpressiveMotionTokens.SnappySpring),
            exit = fadeOut(animationSpec = ExpressiveMotionTokens.SnappySpring)
        ) {
            val scrimAlpha = (0.42f * (1f - predictiveBackProgress)).coerceIn(0f, 0.42f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.closeSpeedDial() }
                    )
            )
        }
    }
}
