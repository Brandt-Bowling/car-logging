package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.ui.screens.home.tabs.insights.review.ChargerImportAiBanner
import com.example.car_logger.ui.screens.home.tabs.insights.review.ChargerImportReviewContent
import com.example.car_logger.ui.screens.home.tabs.insights.review.ChargerManualEntryContent
import com.example.car_logger.ui.screens.home.tabs.insights.review.ChargerUsageReviewState
import com.example.car_logger.ui.screens.home.tabs.insights.review.LastUploadBatchCard
import com.example.car_logger.ui.screens.home.tabs.insights.review.VehicleAssignmentGroup
import com.example.car_logger.ui.screens.home.tabs.insights.review.rememberChargerUsageReviewState
import com.example.car_logger.ui.theme.expressiveBounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargerUsageReviewSheet(
    initialImport: ParsedChargerImportResult?,
    cars: List<Car>,
    existingFingerprints: Set<String>,
    onUploadFile: () -> Unit,
    onDismiss: () -> Unit,
    onSaveSessions: (sessions: List<ChargingSessionEntity>, skippedDuplicates: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedCarId: String? = null,
    initialVenue: ChargingVenue = ChargingVenue.HOME,
    lastUploadBatch: List<ChargingSessionEntity> = emptyList(),
    onUndoLastUpload: () -> Unit = {},
    onUndoAndRetagLastUpload: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = rememberChargerUsageReviewState(
        initialImport = initialImport,
        cars = cars,
        initialSelectedCarId = initialSelectedCarId,
        initialVenue = initialVenue
    )
    val importBuildResult = remember(
        state.importDrafts.toList(),
        state.selectedCarId,
        existingFingerprints,
        state.isImportReviewMode
    ) {
        state.computeImportBuildResult(existingFingerprints)
    }

    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val topClearance = maxOf(topInset + 8.dp, 72.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        modifier = modifier.padding(top = topClearance)
    ) {
        ChargerSheetBody(
            state = state,
            initialImport = initialImport,
            lastUploadBatch = lastUploadBatch,
            importBuildResult = importBuildResult,
            existingFingerprints = existingFingerprints,
            onUploadFile = onUploadFile,
            onUndoLastUpload = onUndoLastUpload,
            onUndoAndRetagLastUpload = onUndoAndRetagLastUpload,
            onDismiss = onDismiss,
            onSaveSessions = onSaveSessions
        )
    }
}

@Composable
private fun ChargerSheetBody(
    state: ChargerUsageReviewState,
    initialImport: ParsedChargerImportResult?,
    lastUploadBatch: List<ChargingSessionEntity>,
    importBuildResult: Pair<List<ChargingSessionEntity>, Int>,
    existingFingerprints: Set<String>,
    onUploadFile: () -> Unit,
    onUndoLastUpload: () -> Unit,
    onUndoAndRetagLastUpload: () -> Unit,
    onDismiss: () -> Unit,
    onSaveSessions: (sessions: List<ChargingSessionEntity>, skippedDuplicates: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ChargerSheetHeader(
            isImportReviewMode = state.isImportReviewMode,
            fileName = initialImport?.fileName
        )

        if (!state.isImportReviewMode) {
            OutlinedButton(
                onClick = onUploadFile,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import file (CSV, PDF, image)")
            }
            if (lastUploadBatch.isNotEmpty()) {
                LastUploadBatchCard(
                    lastUploadBatch = lastUploadBatch,
                    activeCars = state.activeCars,
                    onUndoLastUpload = onUndoLastUpload,
                    onUndoAndRetagLastUpload = onUndoAndRetagLastUpload
                )
            }
        } else if (initialImport?.source == "gemini") {
            ChargerImportAiBanner()
        }

        if (initialImport?.error != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = initialImport.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (state.activeCars.isNotEmpty()) {
            VehicleAssignmentSection(state = state)
        }

        if (state.isImportReviewMode) {
            ChargerImportReviewContent(
                importDrafts = state.importDrafts,
                newEntities = importBuildResult.first,
                skippedCount = importBuildResult.second,
                onCycleVenue = state::cycleDraftVenue,
                onRemoveDraft = state::removeDraftAt
            )
        } else {
            ChargerManualEntryContent(state = state)
        }

        state.errorMessage?.let { error ->
            Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        ChargerSheetActionRow(
            isImportReviewMode = state.isImportReviewMode,
            importReadyCount = importBuildResult.first.size,
            onDismiss = onDismiss,
            onConfirm = {
                if (state.isImportReviewMode) {
                    onSaveSessions(importBuildResult.first, importBuildResult.second)
                } else {
                    state.submitManualEntry(existingFingerprints, onSaveSessions)
                }
            }
        )
    }
}

@Composable
private fun ChargerSheetHeader(isImportReviewMode: Boolean, fileName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (isImportReviewMode) "Review import" else "Add charging",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (isImportReviewMode) {
                "From ${fileName ?: "file"}. Choose a vehicle and confirm."
            } else {
                "Import a file, or enter a session or monthly total."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VehicleAssignmentSection(state: ChargerUsageReviewState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Which vehicle charged?",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        VehicleAssignmentGroup(
            cars = state.activeCars,
            selectedCarId = state.selectedCarId,
            onSelectCarId = state::selectCarId
        )
        if (state.selectedCarId == null && state.activeCars.size > 1) {
            Text(
                text = "Split across vehicles by miles driven.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChargerSheetActionRow(
    isImportReviewMode: Boolean,
    importReadyCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Cancel")
        }
        Button(
            onClick = onConfirm,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1.4f)
                .expressiveBounceClick()
        ) {
            val label = if (isImportReviewMode) {
                if (importReadyCount == 0) {
                    "Done"
                } else {
                    "Save $importReadyCount ${if (importReadyCount == 1) "session" else "sessions"}"
                }
            } else {
                "Save"
            }
            Text(label)
        }
    }
}
