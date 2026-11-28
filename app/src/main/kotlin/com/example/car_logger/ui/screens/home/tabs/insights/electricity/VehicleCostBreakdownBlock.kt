package com.example.car_logger.ui.screens.home.tabs.insights.electricity

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.insights.ElectricityCostData
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import java.util.Locale

@Composable
internal fun VehicleCostBreakdownBlock(
    venue: ChargingVenue,
    data: ElectricityCostData,
    selectedCarId: String?,
    car1Id: String?,
    car1Name: String,
    car2Id: String?,
    car2Name: String,
    modifier: Modifier = Modifier
) {
    val isWork = venue == ChargingVenue.WORK
    val c1Cost = when (venue) {
        ChargingVenue.HOME -> data.homeCar1Cost
        ChargingVenue.WORK -> 0.0
        ChargingVenue.PUBLIC -> data.publicCar1Cost
    }
    val c1Kwh = when (venue) {
        ChargingVenue.HOME -> data.homeCar1Kwh
        ChargingVenue.WORK -> data.workCar1Kwh
        ChargingVenue.PUBLIC -> data.publicCar1Kwh
    }
    val c1Pct = when (venue) {
        ChargingVenue.HOME -> data.homeCar1SharePct
        ChargingVenue.WORK -> data.workCar1SharePct
        ChargingVenue.PUBLIC -> data.publicCar1SharePct
    }
    val c2Cost = when (venue) {
        ChargingVenue.HOME -> data.homeCar2Cost
        ChargingVenue.WORK -> 0.0
        ChargingVenue.PUBLIC -> data.publicCar2Cost
    }
    val c2Kwh = when (venue) {
        ChargingVenue.HOME -> data.homeCar2Kwh
        ChargingVenue.WORK -> data.workCar2Kwh
        ChargingVenue.PUBLIC -> data.publicCar2Kwh
    }
    val c2Pct = when (venue) {
        ChargingVenue.HOME -> data.homeCar2SharePct
        ChargingVenue.WORK -> data.workCar2SharePct
        ChargingVenue.PUBLIC -> data.publicCar2SharePct
    }

    val showBreakdown = selectedCarId == null && !car1Id.isNullOrBlank() && !car2Id.isNullOrBlank() && (c1Kwh > 0.0 || c2Kwh > 0.0)
    if (!showBreakdown) return

    val c1Weight by animateFloatAsState(
        targetValue = (c1Pct / 100f).coerceIn(0.05f, 0.95f),
        animationSpec = ExpressiveMotionTokens.BouncySpring,
        label = "ev_c1_bar"
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (isWork) "Energy by vehicle" else "Cost by vehicle",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (c1Kwh > 0.0) {
                Surface(modifier = Modifier.weight(c1Weight).fillMaxHeight(), color = MaterialTheme.colorScheme.primary, shape = CircleShape) {}
            }
            if (c2Kwh > 0.0) {
                Surface(modifier = Modifier.weight((1f - c1Weight).coerceAtLeast(0.05f)).fillMaxHeight(), color = MaterialTheme.colorScheme.tertiary, shape = CircleShape) {}
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VehicleElectricitySubCard(
                name = car1Name,
                cost = c1Cost,
                kwh = c1Kwh,
                sharePct = c1Pct,
                isFree = isWork,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            VehicleElectricitySubCard(
                name = car2Name,
                cost = c2Cost,
                kwh = c2Kwh,
                sharePct = c2Pct,
                isFree = isWork,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VehicleElectricitySubCard(
    name: String,
    cost: Double,
    kwh: Double,
    sharePct: Int,
    containerColor: Color,
    accentColor: Color,
    isFree: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = containerColor) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isFree) "Free" else String.format(Locale.US, "$%.2f", cost),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${formatKwh(kwh)} kWh · $sharePct%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
