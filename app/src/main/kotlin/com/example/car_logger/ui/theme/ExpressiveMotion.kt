package com.example.car_logger.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Material 3 Expressive motion tokens and physics-based spring specifications.
 * Aligned with Google Material 3 Expressive design specifications.
 */
object ExpressiveMotionTokens {
    /**
     * Bouncy spring with medium damping and medium-low stiffness (~400).
     * Ideal for FAB expansion, speed dial action pills, dials, and interactive popups.
     */
    val BouncySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val BouncyOffsetSpring: SpringSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Spatial spring for destination transitions, subview switches, and sheet expansions.
     * Offers natural ease with a subtle settling bounce.
     */
    val SpatialSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    val SpatialOffsetSpring: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    val SpatialSizeSpring: SpringSpec<IntSize> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    /**
     * Soft spring with low bounce, ideal for progress bars, gauges, and sliding indicators.
     */
    val SoftSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /**
     * Snappy non-bouncy spring for rapid micro-interactions, icons, and color tints.
     */
    val SnappySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val SnappyOffsetSpring: SpringSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val SnappySizeSpring: SpringSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Spring specification for list item reordering and placement.
     */
    val ItemPlacementSpring: SpringSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Spring specification for item fade-in / fade-out in lazy lists.
     */
    val ItemFadeSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Color transition spring.
     */
    val ColorSpring: SpringSpec<Color> = spring(
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Adds an expressive spring scale effect when a composable is pressed.
 * Gives cards, pills, and action surfaces a physical, bouncy feel.
 */
fun Modifier.expressiveBounceClick(
    pressedScale: Float = 0.96f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = if (isPressed) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
            stiffness = if (isPressed) Spring.StiffnessHigh else Spring.StiffnessMediumLow
        ),
        label = "expressive_bounce_scale"
    )

    this
        .scale(scale)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}
