package com.example.car_logger.ui.screens.home.tabs.insights.electricity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.ElectricityCostData

@Composable
internal fun EmptyVenueState(
    venue: ChargingVenue,
    workAccent: Color,
    data: ElectricityCostData,
    onOpenManualBillSheet: () -> Unit,
    onSaveCustomRate: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHome = venue == ChargingVenue.HOME
    val isWork = venue == ChargingVenue.WORK

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (venue) {
                            ChargingVenue.HOME -> Icons.Rounded.ElectricBolt
                            ChargingVenue.WORK -> Icons.Rounded.Work
                            ChargingVenue.PUBLIC -> Icons.Rounded.EvStation
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when (venue) {
                            ChargingVenue.HOME -> MaterialTheme.colorScheme.primary
                            ChargingVenue.WORK -> workAccent
                            ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Text(
                        text = when {
                            !data.hasAnyData && isHome -> "Track charging costs"
                            isHome -> "No home charging in this period"
                            isWork -> "No work charging in this period"
                            else -> "No public charging in this period"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = when {
                        !data.hasAnyData && isHome -> "Add an electric bill or charging session to see efficiency and savings."
                        isHome -> "Sync your Wall Connectors, import a file, or add a home session."
                        isWork -> "Sync your Tesla after work, import a Charge Stats screenshot, or add a free J1772 session."
                        else -> "Sync Superchargers & DC charging, import a receipt screenshot, or add a session."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isHome) {
            RateChipsRow(
                rateValue = data.homeEffectiveRate,
                rateSourceLabel = data.homeRateSourceLabel,
                isEstimated = data.homeIsEstimatedRate,
                isEditable = true,
                leftOverline = "ELECTRIC RATE",
                rightOverline = "SOURCE",
                onSaveCustomRate = onSaveCustomRate,
                onLeftClick = {},
                onRightClick = onOpenManualBillSheet
            )
        }
    }
}
