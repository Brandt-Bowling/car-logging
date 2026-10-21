package com.example.car_logger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.car_logger.ui.theme.FabSquircleShape
import com.example.car_logger.ui.theme.FloatingToolbarShape

enum class VehicleSubView(
    val title: String,
    val icon: ImageVector
) {
    MAINTENANCE("Maintenance", Icons.Filled.Build),
    TIRES("Tires", Icons.Filled.TireRepair),
    GLOVEBOX("Glovebox", Icons.Filled.Folder)
}

/**
 * Material 3 Expressive Floating Toolbar with attached FAB.
 * Replaces the bottom navigation bar on VehicleDetailsScreen with an elevated,
 * pill-shaped floating container (64dp container height) holding the subview destinations
 * and an attached 56dp squircle FAB, adhering to Google Material 3 design specifications.
 * Supports a collapsible state (expanded = false) that collapses the toolbar destinations
 * into the FAB when scrolling.
 */
@Composable
fun VehicleFloatingToolbar(
    currentSubView: VehicleSubView,
    onSubViewSelected: (VehicleSubView) -> Unit,
    onFabClick: () -> Unit,
    fabIcon: ImageVector = Icons.Filled.Add,
    fabContentDescription: String = "Add Record",
    expanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Collapsible floating pill toolbar for sub views (64dp height per M3 specs)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(200)) + expandHorizontally(
                animationSpec = tween(300),
                expandFrom = Alignment.End
            ),
            exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally(
                animationSpec = tween(250),
                shrinkTowards = Alignment.End
            )
        ) {
            Box(
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 12.dp)
            ) {
                Surface(
                    modifier = Modifier.height(64.dp),
                    shape = FloatingToolbarShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VehicleSubView.values().forEach { subView ->
                            val isSelected = subView == currentSubView
                            val backgroundTint by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                label = "pill_background"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                label = "pill_content"
                            )

                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(backgroundTint)
                                    .clickable { onSubViewSelected(subView) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = subView.icon,
                                    contentDescription = subView.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                AnimatedVisibility(visible = isSelected) {
                                    Row {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = subView.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = contentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Attached/Docked Floating Action Button (56dp squircle per M3 FAB specs)
        FloatingActionButton(
            onClick = onFabClick,
            shape = FabSquircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = fabIcon,
                contentDescription = fabContentDescription,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
