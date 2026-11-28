package com.example.car_logger.ui.screens.home.tabs.insights.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedChargingSessionDraft
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@Composable
fun ChargerImportAiBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tap a venue badge to cycle Home / Work / Public, or × to skip rows already synced.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChargerImportReviewContent(
    importDrafts: List<ParsedChargingSessionDraft>,
    newEntities: List<ChargingSessionEntity>,
    skippedCount: Int,
    onCycleVenue: (Int) -> Unit,
    onRemoveDraft: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ChargerImportSummaryCard(
            newEntities = newEntities,
            skippedCount = skippedCount
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            importDrafts.take(15).forEachIndexed { idx, row ->
                ChargerImportDraftRow(
                    row = row,
                    onCycleVenue = { onCycleVenue(idx) },
                    onRemove = { onRemoveDraft(idx) }
                )
            }
            if (importDrafts.size > 15) {
                Text(
                    text = "+${importDrafts.size - 15} more sessions included",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChargerImportSummaryCard(
    newEntities: List<ChargingSessionEntity>,
    skippedCount: Int
) {
    val totalImportKwh = newEntities.sumOf { it.kwh }
    val formattedImportKwh = String.format(
        Locale.US,
        if (totalImportKwh >= 100.0) "%,.0f" else "%,.1f",
        totalImportKwh
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (newEntities.size == 1) {
                    "1 new session ready ($formattedImportKwh kWh)"
                } else {
                    "${newEntities.size} new sessions ready ($formattedImportKwh kWh)"
                },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (skippedCount > 0) {
                Text(
                    text = "$skippedCount ${if (skippedCount == 1) "duplicate" else "duplicates"} already saved (will be skipped).",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberUrgent
                )
            }
        }
    }
}

@Composable
private fun ChargerImportDraftRow(
    row: ParsedChargingSessionDraft,
    onCycleVenue: () -> Unit,
    onRemove: () -> Unit
) {
    val normalizedVenue = ElectricityImportParser.normalizeVenue(row.venue)
    val workChipBg = ExpressiveThemeTokens.savingsContainer
    val workChipFg = ExpressiveThemeTokens.savingsAccent
    val (badgeBg, badgeFg, badgeLabel) = when (normalizedVenue) {
        ElectricityImportParser.VENUE_WORK -> Triple(workChipBg, workChipFg, "Work")
        ElectricityImportParser.VENUE_PUBLIC -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Public"
        )
        else -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Home"
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DraftRowLabels(row = row, normalizedVenue = normalizedVenue, modifier = Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeBg,
                modifier = Modifier.expressiveBounceClick(onClick = onCycleVenue)
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove row from import",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DraftRowLabels(
    row: ParsedChargingSessionDraft,
    normalizedVenue: String,
    modifier: Modifier = Modifier
) {
    val dateDisplay = if (row.startDateIso == row.endDateIso) {
        row.startDateIso
    } else {
        "${row.startDateIso} – ${row.endDateIso}"
    }
    val subtitle = buildString {
        append("${String.format(Locale.US, "%.1f", row.kwh)} kWh")
        when (normalizedVenue) {
            ElectricityImportParser.VENUE_WORK -> append(" · Free")
            ElectricityImportParser.VENUE_PUBLIC -> if (row.directCost != null) {
                append(" · $${String.format(Locale.US, "%.2f", row.directCost)}")
            }
        }
        if (!row.chargerName.isNullOrBlank()) {
            append(" · ${row.chargerName}")
        }
    }

    Column(modifier = modifier) {
        Text(
            text = buildString {
                append(dateDisplay)
                if (row.isSummary) append(" · Total")
            },
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
