package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.GasComparisonData
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GasComparisonCard(
    gasComparison: GasComparisonData,
    gasPrice: Float,
    baselineMpg: Float,
    car1Name: String,
    car1Miles: Int,
    car2Name: String,
    car2Miles: Int,
    selectedCarId: String?,
    car1Id: String?,
    car2Id: String?,
    onOpenEditSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val hasTwoCars = !car2Id.isNullOrBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Gas comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Estimated cost in a gas car",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Hero Tonal Highlight Container (Amber)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AmberUrgent.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ESTIMATED GAS COST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberUrgent,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", gasComparison.totalCost)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 32.sp
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", gasComparison.totalGallons)} gallons of gas saved",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CO₂ avoided",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "~${numberFormat.format(gasComparison.co2Lbs)} lbs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Net Savings vs. Gas banner (shown when EV charging data exists in period)
            val netSavings = gasComparison.netFuelSavings
            if (netSavings != null) {
                val isPositive = netSavings >= 0.0
                val bannerBg = if (isPositive) {
                    ExpressiveThemeTokens.savingsContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                }
                val bannerAccent = if (isPositive) {
                    ExpressiveThemeTokens.savingsAccent
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
                val bannerValueColor = if (isPositive) {
                    ExpressiveThemeTokens.savingsText
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
                val formattedDiff = if (isPositive) {
                    "+$${String.format(Locale.US, "%.2f", netSavings)}"
                } else {
                    "-$${String.format(Locale.US, "%.2f", kotlin.math.abs(netSavings))}"
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = bannerBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Savings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = bannerAccent
                                )
                                Text(
                                    text = if (isPositive) "NET SAVINGS" else "OVER GAS COST",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = bannerAccent
                                )
                            }
                            Text(
                                text = "After $${String.format(Locale.US, "%.2f", gasComparison.electricityTotalCost)} in charging",
                                style = MaterialTheme.typography.bodySmall,
                                color = bannerValueColor.copy(alpha = 0.85f)
                            )
                        }

                        Text(
                            text = formattedDiff,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = bannerValueColor
                        )
                    }
                }
            }

            // Editable Chips: Gas price & Gas MPG
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Assumptions",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Gas Price Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .expressiveBounceClick(pressedScale = 0.96f, onClick = onOpenEditSheet)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Gas price",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", gasPrice)}/gal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit gas price",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Baseline MPG Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .expressiveBounceClick(pressedScale = 0.96f, onClick = onOpenEditSheet)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Gas MPG",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                val mpgText = if (baselineMpg == baselineMpg.toInt().toFloat()) {
                                    "${baselineMpg.toInt()} MPG"
                                } else {
                                    "${String.format(Locale.US, "%.1f", baselineMpg)} MPG"
                                }
                                Text(
                                    text = mpgText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit MPG",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Per-Vehicle Breakdown Sub-rows (when both cars shown)
            if (hasTwoCars && selectedCarId == null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(
                                text = "$car1Name (${numberFormat.format(car1Miles)} mi)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", gasComparison.car1Cost)} (${String.format(Locale.US, "%.1f", gasComparison.car1Gallons)} gal)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                            Text(
                                text = "$car2Name (${numberFormat.format(car2Miles)} mi)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", gasComparison.car2Cost)} (${String.format(Locale.US, "%.1f", gasComparison.car2Gallons)} gal)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}
