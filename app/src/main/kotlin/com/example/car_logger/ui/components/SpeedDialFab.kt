package com.example.car_logger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.FabSquircleShape
import com.example.car_logger.ui.theme.expressiveBounceClick

/**
 * Material 3 Expressive Speed Dial FAB matching classic Google application design.
 * Options are elevated unified pills containing leading icons and descriptive text,
 * expanding upward with physics-based spring animations and supporting predictive back.
 */
@Composable
fun SpeedDialFab(
    isOpen: Boolean,
    onToggle: () -> Unit,
    onAddCar: () -> Unit,
    onAddRecord: () -> Unit,
    modifier: Modifier = Modifier,
    predictiveBackProgress: Float = 0f
) {
    // Spring-driven rotation between '+' and 'x'
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 45f else 0f,
        animationSpec = ExpressiveMotionTokens.BouncySpring,
        label = "fab_rotation"
    )

    // Interactive predictive back gesture scaling
    val predictiveScale = (1f - predictiveBackProgress * 0.35f).coerceIn(0.6f, 1f)
    val predictiveAlpha = (1f - predictiveBackProgress * 0.8f).coerceIn(0.2f, 1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.graphicsLayer {
                scaleX = if (isOpen) predictiveScale else 1f
                scaleY = if (isOpen) predictiveScale else 1f
                alpha = if (isOpen) predictiveAlpha else 1f
                transformOrigin = TransformOrigin(1f, 1f)
            }
        ) {
            // Speed Dial Action Options (Pills with text and icon inside)
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(animationSpec = ExpressiveMotionTokens.SoftSpring) +
                        slideInVertically(
                            animationSpec = ExpressiveMotionTokens.BouncyOffsetSpring,
                            initialOffsetY = { it / 2 }
                        ) +
                        scaleIn(
                            animationSpec = ExpressiveMotionTokens.BouncySpring,
                            initialScale = 0.75f,
                            transformOrigin = TransformOrigin(1f, 1f)
                        ),
                exit = fadeOut(animationSpec = ExpressiveMotionTokens.SnappySpring) +
                       slideOutVertically(
                           animationSpec = ExpressiveMotionTokens.SnappyOffsetSpring,
                           targetOffsetY = { it / 2 }
                       ) +
                       scaleOut(
                           animationSpec = ExpressiveMotionTokens.SnappySpring,
                           targetScale = 0.8f,
                           transformOrigin = TransformOrigin(1f, 1f)
                       )
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option 1: Add Service Record Pill
                    GoogleSpeedDialPill(
                        label = "Add Service Record",
                        icon = Icons.Filled.Build,
                        onClick = onAddRecord
                    )

                    // Option 2: Add Vehicle Pill
                    GoogleSpeedDialPill(
                        label = "Add Vehicle",
                        icon = Icons.Filled.DirectionsCar,
                        onClick = onAddCar
                    )
                }
            }

            // Primary FAB button with expressive spring feedback
            FloatingActionButton(
                onClick = onToggle,
                shape = FabSquircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp
                ),
                modifier = Modifier
                    .size(56.dp)
                    .expressiveBounceClick(pressedScale = 0.92f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (isOpen) "Close actions" else "Add",
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

/**
 * Standard Google app style action pill for Speed Dial options.
 * A single elevated pill container with leading icon and text inside.
 */
@Composable
private fun GoogleSpeedDialPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier.expressiveBounceClick(pressedScale = 0.96f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
