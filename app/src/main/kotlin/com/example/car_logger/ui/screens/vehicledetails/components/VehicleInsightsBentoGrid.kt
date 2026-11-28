package com.example.car_logger.ui.screens.vehicledetails.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.ui.screens.vehicledetails.components.bento.DrivingPaceBentoTile
import com.example.car_logger.ui.screens.vehicledetails.components.bento.DrivingPaceDrawerContent
import com.example.car_logger.ui.screens.vehicledetails.components.bento.EvEfficiencyBentoTile
import com.example.car_logger.ui.screens.vehicledetails.components.bento.EvEfficiencyDrawerContent
import com.example.car_logger.ui.screens.vehicledetails.components.bento.SavedVsGasBentoTile
import com.example.car_logger.ui.screens.vehicledetails.components.bento.SavedVsGasDrawerContent
import com.example.car_logger.ui.screens.vehicledetails.components.bento.TrueCostPerMileBentoTile
import com.example.car_logger.ui.screens.vehicledetails.components.bento.TrueCostPerMileDrawerContent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import java.text.NumberFormat
import java.util.Locale

@Composable
fun VehicleInsightsBentoGrid(
    state: VehicleInsightsBentoState,
    granularity: InsightsTimeGranularity,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTile by rememberSaveable { mutableStateOf<VehicleBentoTile?>(null) }
    var inspectedBarIndex by remember(granularity) { mutableStateOf<Int?>(null) }
    val periodMetrics = remember(state, granularity) { state.metricsFor(granularity) }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val savingsBg = ExpressiveThemeTokens.savingsContainer
    val savingsAccent = ExpressiveThemeTokens.savingsAccent
    val savingsText = ExpressiveThemeTokens.savingsText

    fun toggleTile(tile: VehicleBentoTile) {
        expandedTile = if (expandedTile == tile) null else tile
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrivingPaceBentoTile(
                metrics = periodMetrics,
                isExpanded = expandedTile == VehicleBentoTile.PACE,
                numberFormat = numberFormat,
                onClick = { toggleTile(VehicleBentoTile.PACE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            EvEfficiencyBentoTile(
                metrics = periodMetrics,
                isExpanded = expandedTile == VehicleBentoTile.EFFICIENCY,
                onClick = { toggleTile(VehicleBentoTile.EFFICIENCY) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SavedVsGasBentoTile(
                metrics = periodMetrics,
                isExpanded = expandedTile == VehicleBentoTile.SAVINGS,
                savingsBg = savingsBg,
                savingsAccent = savingsAccent,
                savingsText = savingsText,
                numberFormat = numberFormat,
                onClick = { toggleTile(VehicleBentoTile.SAVINGS) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            TrueCostPerMileBentoTile(
                state = state,
                isExpanded = expandedTile == VehicleBentoTile.TCO,
                onClick = { toggleTile(VehicleBentoTile.TCO) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        VehicleBentoDockedDrawer(
            expandedTile = expandedTile,
            state = state,
            periodMetrics = periodMetrics,
            granularity = granularity,
            inspectedBarIndex = inspectedBarIndex,
            savingsBg = savingsBg,
            savingsAccent = savingsAccent,
            savingsText = savingsText,
            numberFormat = numberFormat,
            onInspectBar = { idx -> inspectedBarIndex = if (inspectedBarIndex == idx) null else idx },
            onSelectGranularity = {
                inspectedBarIndex = null
                onSelectGranularity(it)
            }
        )
    }
}

@Composable
private fun VehicleBentoDockedDrawer(
    expandedTile: VehicleBentoTile?,
    state: VehicleInsightsBentoState,
    periodMetrics: VehicleBentoPeriodMetrics,
    granularity: InsightsTimeGranularity,
    inspectedBarIndex: Int?,
    savingsBg: Color,
    savingsAccent: Color,
    savingsText: Color,
    numberFormat: NumberFormat,
    onInspectBar: (Int) -> Unit,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = expandedTile != null,
        enter = fadeIn(animationSpec = ExpressiveMotionTokens.SnappySpring) +
            expandVertically(animationSpec = ExpressiveMotionTokens.SpatialSizeSpring),
        exit = fadeOut(animationSpec = ExpressiveMotionTokens.SnappySpring) +
            shrinkVertically(animationSpec = ExpressiveMotionTokens.SpatialSizeSpring),
        modifier = modifier
    ) {
        if (expandedTile != null) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = expandedTile,
                    transitionSpec = {
                        fadeIn(ExpressiveMotionTokens.SnappySpring) togetherWith
                            fadeOut(ExpressiveMotionTokens.SnappySpring)
                    },
                    label = "bento_docked_drawer_content"
                ) { tile ->
                    val contentModifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                    when (tile) {
                        VehicleBentoTile.PACE -> DrivingPaceDrawerContent(
                            metrics = periodMetrics,
                            granularity = granularity,
                            inspectedBarIndex = inspectedBarIndex,
                            onInspectBar = onInspectBar,
                            onSelectGranularity = onSelectGranularity,
                            numberFormat = numberFormat,
                            modifier = contentModifier
                        )
                        VehicleBentoTile.EFFICIENCY -> EvEfficiencyDrawerContent(
                            metrics = periodMetrics,
                            granularity = granularity,
                            workAccent = savingsAccent,
                            onSelectGranularity = onSelectGranularity,
                            modifier = contentModifier
                        )
                        VehicleBentoTile.SAVINGS -> SavedVsGasDrawerContent(
                            metrics = periodMetrics,
                            granularity = granularity,
                            savingsBg = savingsBg,
                            savingsAccent = savingsAccent,
                            savingsText = savingsText,
                            numberFormat = numberFormat,
                            onSelectGranularity = onSelectGranularity,
                            modifier = contentModifier
                        )
                        VehicleBentoTile.TCO -> TrueCostPerMileDrawerContent(
                            state = state,
                            numberFormat = numberFormat,
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }
}
