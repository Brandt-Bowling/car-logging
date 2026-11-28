package com.example.car_logger.ui.screens.vehicledetails.components.bento

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.car_logger.data.insights.InsightsTimeGranularity

@Composable
internal fun BentoDrawerHeaderRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

@Composable
internal fun InDrawerPeriodConnectedToggle(
    granularity: InsightsTimeGranularity,
    onSelectGranularity: (InsightsTimeGranularity) -> Unit,
    modifier: Modifier = Modifier
) {
    val isWeekly = granularity == InsightsTimeGranularity.WEEKLY
    val leftRightRadius by animateDpAsState(
        targetValue = if (isWeekly) 16.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "drawer_period_left_r"
    )
    val rightLeftRadius by animateDpAsState(
        targetValue = if (!isWeekly) 16.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "drawer_period_right_r"
    )

    val leftShape = RoundedCornerShape(
        topStart = 16.dp,
        bottomStart = 16.dp,
        topEnd = leftRightRadius,
        bottomEnd = leftRightRadius
    )
    val rightShape = RoundedCornerShape(
        topStart = rightLeftRadius,
        bottomStart = rightLeftRadius,
        topEnd = 16.dp,
        bottomEnd = 16.dp
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = leftShape,
                color = if (isWeekly) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isWeekly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(leftShape)
                    .clickable { onSelectGranularity(InsightsTimeGranularity.WEEKLY) }
            ) {
                Text(
                    text = "Weekly (7D)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }

            Surface(
                shape = rightShape,
                color = if (!isWeekly) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (!isWeekly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(rightShape)
                    .clickable { onSelectGranularity(InsightsTimeGranularity.MONTHLY) }
            ) {
                Text(
                    text = "Monthly (30D)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}
