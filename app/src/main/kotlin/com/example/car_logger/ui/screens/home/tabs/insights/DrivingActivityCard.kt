package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.DrivingActivityBar
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DrivingActivityCard(
    granularity: InsightsTimeGranularity,
    activityBars: List<DrivingActivityBar>,
    car1Name: String,
    car2Name: String,
    selectedCarId: String?,
    car1Id: String?,
    car2Id: String?,
    modifier: Modifier = Modifier
) {
    var inspectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val hasTwoCars = !car2Id.isNullOrBlank()

    val showCar1 = selectedCarId == null || selectedCarId == car1Id
    val showCar2 = hasTwoCars && (selectedCarId == null || selectedCarId == car2Id)

    val maxVal = remember(activityBars, showCar1, showCar2) {
        activityBars.maxOfOrNull { bar ->
            val v1 = if (showCar1) bar.car1Miles else 0
            val v2 = if (showCar2) bar.car2Miles else 0
            maxOf(v1, v2)
        }?.coerceAtLeast(1) ?: 1
    }

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
            // Header with title and legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Driving Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (granularity) {
                            InsightsTimeGranularity.WEEKLY -> "Miles driven per week"
                            InsightsTimeGranularity.MONTHLY -> "Miles driven per month"
                            InsightsTimeGranularity.YTD -> "Miles driven by month"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCar1) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(
                                text = car1Name.split(" ").firstOrNull() ?: "Car 1",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (showCar2) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                            Text(
                                text = car2Name.split(" ").firstOrNull() ?: "Car 2",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Inspect detail tooltip pill
            val activeInspectBar = inspectedBarIndex?.let { activityBars.getOrNull(it) }
            AnimatedVisibility(
                visible = activeInspectBar != null,
                enter = fadeIn(ExpressiveMotionTokens.SnappySpring),
                exit = fadeOut(ExpressiveMotionTokens.SnappySpring)
            ) {
                if (activeInspectBar != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeInspectBar.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (showCar1) {
                                    Text(
                                        text = "${car1Name.split(" ").firstOrNull() ?: "Car 1"}: ${numberFormat.format(activeInspectBar.car1Miles)} mi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (showCar2) {
                                    Text(
                                        text = "${car2Name.split(" ").firstOrNull() ?: "Car 2"}: ${numberFormat.format(activeInspectBar.car2Miles)} mi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Capsule Bars Chart Area
            if (activityBars.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No driving intervals available yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    activityBars.forEachIndexed { index, bar ->
                        val isSelected = inspectedBarIndex == index
                        val c1HeightPct by animateFloatAsState(
                            targetValue = if (showCar1) (bar.car1Miles.toFloat() / (maxVal * 1.25f)).coerceIn(0.04f, 1f) else 0f,
                            animationSpec = ExpressiveMotionTokens.SpatialSpring,
                            label = "c1_bar_height"
                        )
                        val c2HeightPct by animateFloatAsState(
                            targetValue = if (showCar2) (bar.car2Miles.toFloat() / (maxVal * 1.25f)).coerceIn(0.04f, 1f) else 0f,
                            animationSpec = ExpressiveMotionTokens.SpatialSpring,
                            label = "c2_bar_height"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    inspectedBarIndex = if (inspectedBarIndex == index) null else index
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Car 1 Capsule Pill
                                if (showCar1) {
                                    Box(
                                        modifier = Modifier
                                            .width(if (showCar2) 10.dp else 16.dp)
                                            .fillMaxHeight(c1HeightPct)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                            )
                                    )
                                }

                                // Car 2 Capsule Pill
                                if (showCar2) {
                                    Box(
                                        modifier = Modifier
                                            .width(if (showCar1) 10.dp else 16.dp)
                                            .fillMaxHeight(c2HeightPct)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.tertiary
                                                else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // X-axis label
                            val shortLabel = bar.label.split(" ").let { parts ->
                                if (parts.size >= 2) parts[1] else parts[0]
                            }
                            Text(
                                text = shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
