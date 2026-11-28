package com.example.car_logger.ui.screens.home.tabs.insights.review

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@Composable
fun LastUploadBatchCard(
    lastUploadBatch: List<ChargingSessionEntity>,
    activeCars: List<Car>,
    onUndoLastUpload: () -> Unit,
    onUndoAndRetagLastUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val carNameById = remember(activeCars) {
        activeCars.associate { it.id to it.model.ifBlank { it.displayName } }
    }
    val lastVehicleLabel = lastUploadBatch.first().carId?.let { carNameById[it] } ?: "All vehicles"
    val lastKwh = lastUploadBatch.sumOf { it.kwh }
    val formattedLastKwh = String.format(
        Locale.US,
        if (lastKwh >= 100.0) "%,.0f" else "%,.1f",
        lastKwh
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Last import · ${lastUploadBatch.size} ${if (lastUploadBatch.size == 1) "session" else "sessions"} ($formattedLastKwh kWh · $lastVehicleLabel)",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUndoLastUpload,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .expressiveBounceClick()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Undo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onUndoAndRetagLastUpload,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .expressiveBounceClick()
                ) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change vehicle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun VehicleAssignmentGroup(
    cars: List<Car>,
    selectedCarId: String?,
    onSelectCarId: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val options: List<Pair<String?, String>> = buildList {
        if (cars.size > 1) add(null to "All")
        cars.take(2).forEach { car -> add(car.id to (car.model.ifBlank { car.displayName })) }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, (id, label) ->
            val selected = selectedCarId == id
            val startR = if (index == 0) 20.dp else if (selected) 20.dp else 6.dp
            val endR = if (index == options.lastIndex) 20.dp else if (selected) 20.dp else 6.dp
            val animatedStart by animateDpAsState(startR, spring(dampingRatio = 0.8f, stiffness = 380f), label = "v_s_$index")
            val animatedEnd by animateDpAsState(endR, spring(dampingRatio = 0.8f, stiffness = 380f), label = "v_e_$index")
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "v_bg_$index"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = ExpressiveMotionTokens.ColorSpring,
                label = "v_fg_$index"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .expressiveBounceClick { onSelectCarId(id) },
                shape = RoundedCornerShape(
                    topStart = animatedStart,
                    bottomStart = animatedStart,
                    topEnd = animatedEnd,
                    bottomEnd = animatedEnd
                ),
                color = bg,
                contentColor = fg
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
