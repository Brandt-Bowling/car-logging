package com.example.car_logger.ui.screens.home.tabs.insights.electricity

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.expressiveBounceClick

@Composable
internal fun ExpressiveChargingSplitButton(
    onPrimaryAction: () -> Unit,
    onUploadChargerUsage: () -> Unit,
    onOpenManualBillSheet: () -> Unit,
    modifier: Modifier = Modifier,
    onSyncWallConnectors: () -> Unit = {},
    onSyncWorkCharging: () -> Unit = {},
    onSyncPublicCharging: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val innerRadius by animateDpAsState(
        targetValue = if (menuExpanded) 24.dp else 6.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "split_inner_radius"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = ExpressiveMotionTokens.SnappySpring,
        label = "split_chevron"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .expressiveBounceClick { onPrimaryAction() },
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = innerRadius, bottomEnd = innerRadius),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add charging",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .width(52.dp)
                    .height(48.dp)
                    .expressiveBounceClick { menuExpanded = !menuExpanded },
                shape = RoundedCornerShape(topStart = innerRadius, bottomStart = innerRadius, topEnd = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "More upload and sync options",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }
        }

        ChargingSplitActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onSyncWallConnectors = onSyncWallConnectors,
            onSyncWorkCharging = onSyncWorkCharging,
            onSyncPublicCharging = onSyncPublicCharging,
            onOpenManualBillSheet = onOpenManualBillSheet,
            onUploadChargerUsage = onUploadChargerUsage
        )
    }
}

@Composable
private fun ChargingSplitActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSyncWallConnectors: () -> Unit,
    onSyncWorkCharging: () -> Unit,
    onSyncPublicCharging: () -> Unit,
    onOpenManualBillSheet: () -> Unit,
    onUploadChargerUsage: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp)
    ) {
        DropdownMenuItem(
            text = { Text("Sync Wall Connectors") },
            leadingIcon = { Icon(Icons.Rounded.EvStation, contentDescription = null) },
            onClick = { onDismiss(); onSyncWallConnectors() }
        )
        DropdownMenuItem(
            text = { Text("Sync work charge") },
            leadingIcon = { Icon(Icons.Rounded.Work, contentDescription = null) },
            onClick = { onDismiss(); onSyncWorkCharging() }
        )
        DropdownMenuItem(
            text = { Text("Sync Superchargers & DC") },
            leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
            onClick = { onDismiss(); onSyncPublicCharging() }
        )
        DropdownMenuItem(
            text = { Text("Add electric bill") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
            onClick = { onDismiss(); onOpenManualBillSheet() }
        )
        DropdownMenuItem(
            text = { Text("Import file") },
            leadingIcon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
            onClick = { onDismiss(); onUploadChargerUsage() }
        )
    }
}
