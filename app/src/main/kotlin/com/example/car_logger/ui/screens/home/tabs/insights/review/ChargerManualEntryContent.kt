package com.example.car_logger.ui.screens.home.tabs.insights.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import com.example.car_logger.ui.theme.expressiveBounceClick

@Composable
fun ChargerManualEntryContent(
    state: ChargerUsageReviewState,
    modifier: Modifier = Modifier
) {
    val workChipFg = ExpressiveThemeTokens.savingsAccent
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ManualModeConnectedGroup(
            selectedMode = state.entryMode,
            onSelectMode = state::selectEntryMode
        )

        ChargerVenueChipsRow(
            venue = state.venue,
            onSelectVenue = state::selectVenue
        )

        ManualDateFieldsSection(state = state)

        ManualEnergyAndCostRow(state = state)

        AnimatedVisibility(visible = state.venue == ElectricityImportParser.VENUE_HOME) {
            Text(
                text = "Uses your electric bill rate, or your default home rate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = state.venue == ElectricityImportParser.VENUE_WORK) {
            Text(
                text = "Free workplace charging — logged at $0.00 and included in overall efficiency.",
                style = MaterialTheme.typography.bodySmall,
                color = workChipFg
            )
        }

        OutlinedTextField(
            value = state.chargerNameText,
            onValueChange = state::updateChargerName,
            label = { Text(chargerNameLabel(state.venue)) },
            placeholder = { Text(chargerNamePlaceholder(state.venue)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ManualDateFieldsSection(state: ChargerUsageReviewState) {
    if (state.entryMode == ManualEntryMode.SINGLE_SESSION) {
        OutlinedTextField(
            value = state.singleDateText,
            onValueChange = state::updateSingleDate,
            label = { Text("Date") },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.rangeStartText,
                    onValueChange = state::updateRangeStart,
                    label = { Text("Period start") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.rangeEndText,
                    onValueChange = state::updateRangeEnd,
                    label = { Text("Period end") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Spread evenly across these dates for weekly and monthly charts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManualEnergyAndCostRow(state: ChargerUsageReviewState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = state.kwhText,
            onValueChange = state::updateKwh,
            label = { Text("Energy added (kWh)") },
            suffix = { Text("kWh") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f)
        )

        if (state.venue == ElectricityImportParser.VENUE_PUBLIC) {
            OutlinedTextField(
                value = state.directCostText,
                onValueChange = state::updateDirectCost,
                label = { Text("Total paid ($)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChargerVenueChipsRow(
    venue: String,
    onSelectVenue: (String) -> Unit
) {
    val workChipBg = ExpressiveThemeTokens.savingsContainer
    val workChipFg = ExpressiveThemeTokens.savingsAccent
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = venue == ElectricityImportParser.VENUE_HOME,
            onClick = { onSelectVenue(ElectricityImportParser.VENUE_HOME) },
            label = { Text("Home", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    imageVector = if (venue == ElectricityImportParser.VENUE_HOME) Icons.Rounded.Check else Icons.Rounded.Home,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = venue == ElectricityImportParser.VENUE_WORK,
            onClick = { onSelectVenue(ElectricityImportParser.VENUE_WORK) },
            label = { Text("Work", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    imageVector = if (venue == ElectricityImportParser.VENUE_WORK) Icons.Rounded.Check else Icons.Rounded.Work,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = workChipBg,
                selectedLabelColor = workChipFg,
                selectedLeadingIconColor = workChipFg
            ),
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = venue == ElectricityImportParser.VENUE_PUBLIC,
            onClick = { onSelectVenue(ElectricityImportParser.VENUE_PUBLIC) },
            label = { Text("Public", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    imageVector = if (venue == ElectricityImportParser.VENUE_PUBLIC) Icons.Rounded.Check else Icons.Rounded.EvStation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ManualModeConnectedGroup(
    selectedMode: ManualEntryMode,
    onSelectMode: (ManualEntryMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ManualEntryMode.entries.forEachIndexed { index, mode ->
            val selected = mode == selectedMode
            val isFirst = index == 0
            val inner by animateDpAsState(
                targetValue = if (selected) 20.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "mode_r_$index"
            )
            val shape = if (isFirst) {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = inner, bottomEnd = inner)
            } else {
                RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = 20.dp, bottomEnd = 20.dp)
            }
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "mode_bg_$index"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "mode_fg_$index"
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectMode(mode) },
                shape = shape,
                color = bg,
                contentColor = fg
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun chargerNameLabel(venue: String): String = when (venue) {
    ElectricityImportParser.VENUE_HOME -> "Charger name (optional)"
    ElectricityImportParser.VENUE_WORK -> "Work charger (optional)"
    else -> "Station name (optional)"
}

private fun chargerNamePlaceholder(venue: String): String = when (venue) {
    ElectricityImportParser.VENUE_HOME -> "e.g., Garage Wall Connector"
    ElectricityImportParser.VENUE_WORK -> "e.g., Work J1772"
    else -> "e.g., Supercharger or Electrify America"
}
