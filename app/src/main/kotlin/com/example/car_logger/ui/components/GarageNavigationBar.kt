package com.example.car_logger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.car_logger.ui.theme.AmberUrgent
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens

enum class GarageNavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    GARAGE("Garage", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
    ACTIVITY("Activity", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    SCHEDULE("Schedule", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun GarageNavigationBar(
    currentTab: GarageNavigationTab,
    onTabSelected: (GarageNavigationTab) -> Unit,
    hasUrgentService: Boolean,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = ExpressiveThemeTokens.shellColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        GarageNavigationTab.values().forEach { tab ->
            val selected = tab == currentTab
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1.0f,
                animationSpec = ExpressiveMotionTokens.BouncySpring,
                label = "nav_icon_scale"
            )

            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    val iconModifier = Modifier.scale(iconScale)
                    if (tab == GarageNavigationTab.SCHEDULE && hasUrgentService) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = AmberUrgent)
                            }
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                modifier = iconModifier
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title,
                            modifier = iconModifier
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}
