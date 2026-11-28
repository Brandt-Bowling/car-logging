package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.ElectricityCostData
import com.example.car_logger.ui.screens.home.tabs.insights.electricity.EmptyVenueState
import com.example.car_logger.ui.screens.home.tabs.insights.electricity.ExpressiveChargingSplitButton
import com.example.car_logger.ui.screens.home.tabs.insights.electricity.PopulatedVenueContent
import com.example.car_logger.ui.screens.home.tabs.insights.electricity.VenueConnectedButtonGroup
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import com.example.car_logger.ui.theme.expressiveBounceClick

@Composable
fun EvElectricityCostCard(
    data: ElectricityCostData,
    selectedVenue: ChargingVenue,
    onSelectVenue: (ChargingVenue) -> Unit,
    selectedCarId: String?,
    car1Id: String?,
    car1Name: String,
    car2Id: String?,
    car2Name: String,
    isParsingFile: Boolean,
    billCount: Int,
    sessionCount: Int,
    onUploadChargerUsage: () -> Unit,
    onOpenManualChargerSheet: () -> Unit,
    onOpenManualBillSheet: () -> Unit,
    onOpenManageSheet: () -> Unit,
    onSaveCustomRate: (Float) -> Unit = {},
    onSyncWallConnectors: () -> Unit = {},
    onSyncWorkCharging: () -> Unit = {},
    onSyncPublicCharging: () -> Unit = {},
    onOpenPeriodLogs: (ChargingVenue) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val workContainerColor = ExpressiveThemeTokens.savingsContainer
    val workContentColor = ExpressiveThemeTokens.savingsAccent

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChargingCostCardHeader(
                selectedVenue = selectedVenue,
                billCount = billCount,
                sessionCount = sessionCount,
                onOpenManageSheet = onOpenManageSheet
            )

            VenueConnectedButtonGroup(
                selectedVenue = selectedVenue,
                workContainerColor = workContainerColor,
                workContentColor = workContentColor,
                onSelectVenue = onSelectVenue
            )

            if (isParsingFile) {
                ParsingElectricityFileBanner()
            }

            AnimatedContent(
                targetState = selectedVenue,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "venue_content"
            ) { venue ->
                val hasVenueData = when (venue) {
                    ChargingVenue.HOME -> data.hasHomeData
                    ChargingVenue.WORK -> data.hasWorkData
                    ChargingVenue.PUBLIC -> data.hasPublicData
                }

                if (!hasVenueData) {
                    EmptyVenueState(
                        venue = venue,
                        workAccent = workContentColor,
                        data = data,
                        onOpenManualBillSheet = onOpenManualBillSheet,
                        onSaveCustomRate = onSaveCustomRate
                    )
                } else {
                    PopulatedVenueContent(
                        venue = venue,
                        workContainerColor = workContainerColor,
                        workContentColor = workContentColor,
                        data = data,
                        selectedCarId = selectedCarId,
                        car1Id = car1Id,
                        car1Name = car1Name,
                        car2Id = car2Id,
                        car2Name = car2Name,
                        onOpenManageSheet = onOpenManageSheet,
                        onSaveCustomRate = onSaveCustomRate,
                        onOpenPeriodLogs = { onOpenPeriodLogs(venue) }
                    )
                }
            }

            ExpressiveChargingSplitButton(
                onPrimaryAction = onOpenManualChargerSheet,
                onUploadChargerUsage = onUploadChargerUsage,
                onOpenManualBillSheet = onOpenManualBillSheet,
                onSyncWallConnectors = onSyncWallConnectors,
                onSyncWorkCharging = onSyncWorkCharging,
                onSyncPublicCharging = onSyncPublicCharging
            )
        }
    }
}

@Composable
private fun ChargingCostCardHeader(
    selectedVenue: ChargingVenue,
    billCount: Int,
    sessionCount: Int,
    onOpenManageSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Charging costs",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (selectedVenue) {
                    ChargingVenue.HOME -> "Home charging & electric rate"
                    ChargingVenue.WORK -> "Free workplace J1772 charging"
                    ChargingVenue.PUBLIC -> "Superchargers & DC fast charging"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.expressiveBounceClick { onOpenManageSheet() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Manage bills and sessions",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                val totalItems = billCount + sessionCount
                Text(
                    text = if (totalItems > 0) "History ($totalItems)" else "History",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ParsingElectricityFileBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Reading your file...",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
            )
        }
    }
}
