package com.example.car_logger.ui.screens.home.tabs.insights.electricity

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick

@Composable
internal fun VenueConnectedButtonGroup(
    selectedVenue: ChargingVenue,
    workContainerColor: Color,
    workContentColor: Color,
    onSelectVenue: (ChargingVenue) -> Unit,
    modifier: Modifier = Modifier
) {
    val venues = ChargingVenue.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        venues.forEachIndexed { index, venue ->
            val selected = venue == selectedVenue
            val startRadius by animateDpAsState(
                targetValue = if (index == 0 || selected) 24.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "venue_start_$index"
            )
            val endRadius by animateDpAsState(
                targetValue = if (index == venues.lastIndex || selected) 24.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "venue_end_$index"
            )
            val containerColor by animateColorAsState(
                targetValue = when {
                    selected && venue == ChargingVenue.HOME -> MaterialTheme.colorScheme.primaryContainer
                    selected && venue == ChargingVenue.WORK -> workContainerColor
                    selected && venue == ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "venue_bg_$index"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    selected && venue == ChargingVenue.HOME -> MaterialTheme.colorScheme.onPrimaryContainer
                    selected && venue == ChargingVenue.WORK -> workContentColor
                    selected && venue == ChargingVenue.PUBLIC -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "venue_fg_$index"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectVenue(venue) },
                shape = RoundedCornerShape(
                    topStart = startRadius,
                    bottomStart = startRadius,
                    topEnd = endRadius,
                    bottomEnd = endRadius
                ),
                color = containerColor,
                contentColor = contentColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (venue) {
                            ChargingVenue.HOME -> Icons.Rounded.Home
                            ChargingVenue.WORK -> Icons.Rounded.Work
                            ChargingVenue.PUBLIC -> Icons.Rounded.EvStation
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = venue.shortLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
