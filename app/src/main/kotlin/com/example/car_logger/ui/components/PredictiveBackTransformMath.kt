package com.example.car_logger.ui.components

import androidx.compose.ui.geometry.Rect
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/**
 * Swipe edge constants aligned with [androidx.activity.BackEventCompat].
 */
object PredictiveBackEdge {
    const val LEFT = 0
    const val RIGHT = 1
}

/**
 * Captured on-screen bounds of a source card relative to the transition host root.
 *
 * [visibleRect] is clipped to the enclosing scrollable viewport; [unclippedRect] holds the
 * card's full resting layout bounds so partially scrolled cards expand and collapse cleanly.
 */
data class CardBounds(
    val visibleRect: Rect,
    val unclippedRect: Rect = visibleRect
) {
    val isValidForMorph: Boolean
        get() = visibleRect.width >= MIN_MORPH_DIMENSION_PX &&
            visibleRect.height >= MIN_MORPH_DIMENSION_PX &&
            unclippedRect.width >= MIN_MORPH_DIMENSION_PX &&
            unclippedRect.height >= MIN_MORPH_DIMENSION_PX

    companion object {
        private const val MIN_MORPH_DIMENSION_PX = 24f
    }
}

/**
 * Complete visual state for one frame of the Gmail-style container transform and
 * predictive back gesture.
 */
data class ContainerTransformFrame(
    val rect: Rect,
    val cornerRadiusPx: Float,
    val elevationPx: Float,
    val scrimAlpha: Float,
    val detailContentAlpha: Float,
    val cardPreviewAlpha: Float,
    val borderAlpha: Float,
    val cardPreviewOffsetYPx: Float = 0f,
    val backgroundBlend: Float = 1f,
    val containerAlpha: Float = 1f
)

/**
 * Pure geometry and interpolation math for Material 3 / Gmail-style predictive back
 * (pre-commit detached floating surface + post-commit container transform morph back to card)
 * and card-to-screen container transform expansion.
 *
 * Pre-commit specs follow Android's Predictive Back Design guide:
 * - Decelerated progress via STANDARD_DECELERATE (`CubicBezierEasing(0, 0, 0, 1)`)
 * - Minimum scale: 90%
 * - Horizontal shift away from swipe edge: `((width / 20) - 8dp)`, leaving an 8dp opposite margin
 * - Vertical shift tracking finger Y: decelerated & clamped to `((height / 20) - 8dp)`
 */
object PredictiveBackTransformCalculator {
    const val DEFAULT_MIN_SCALE = 0.90f
    const val EDGE_GAP_DP = 8f
    const val DETACHED_CORNER_RADIUS_DP = 28f
    const val CARD_CORNER_RADIUS_DP = 20f
    const val PRE_COMMIT_MAX_ELEVATION_DP = 16f
    const val EXPAND_MAX_ELEVATION_DP = 12f
    const val COLLAPSE_MIN_MID_ELEVATION_DP = 10f
    const val FALLBACK_TARGET_SCALE = 0.84f
    const val LIGHT_SCRIM_ALPHA = 0.32f
    const val DARK_SCRIM_ALPHA = 0.52f

    fun createFullScreenFrame(
        containerWidth: Float,
        containerHeight: Float,
        deviceCornerRadiusPx: Float,
        baseScrimAlpha: Float
    ): ContainerTransformFrame = ContainerTransformFrame(
        rect = Rect(0f, 0f, containerWidth, containerHeight),
        cornerRadiusPx = deviceCornerRadiusPx.coerceAtLeast(0f),
        elevationPx = 0f,
        scrimAlpha = baseScrimAlpha.coerceIn(0f, 1f),
        detailContentAlpha = 1f,
        cardPreviewAlpha = 0f,
        borderAlpha = 0f,
        cardPreviewOffsetYPx = 0f,
        backgroundBlend = 1f,
        containerAlpha = 1f
    )

    /**
     * Computes the detached floating rounded window state during a live pre-commit back swipe.
     */
    fun computePreCommitFrame(
        containerWidth: Float,
        containerHeight: Float,
        rawProgress: Float,
        swipeEdge: Int,
        touchDeltaY: Float,
        edgeGapPx: Float,
        deviceCornerRadiusPx: Float,
        targetDetachedCornerRadiusPx: Float,
        maxElevationPx: Float,
        baseScrimAlpha: Float,
        minScale: Float = DEFAULT_MIN_SCALE
    ): ContainerTransformFrame {
        val clampedProgress = rawProgress.coerceIn(0f, 1f)
        val eased = ExpressiveMotionTokens.PredictiveBackDecelerateEasing.transform(clampedProgress)

        val scale = lerpFloat(1f, minScale, eased)
        val scaledWidth = containerWidth * scale
        val scaledHeight = containerHeight * scale

        val maxShiftX = max(0f, (containerWidth * (1f - minScale) / 2f) - edgeGapPx)
        val shiftDirectionX = if (swipeEdge == PredictiveBackEdge.RIGHT) -1f else 1f
        val translationX = shiftDirectionX * maxShiftX * eased

        val maxShiftY = max(0f, (containerHeight * (1f - minScale) / 2f) - edgeGapPx)
        val currentMaxShiftY = maxShiftY * eased
        val normalizedY = (abs(touchDeltaY) / max(1f, containerHeight * 0.5f)).coerceIn(0f, 1f)
        val easedY = ExpressiveMotionTokens.PredictiveBackDecelerateEasing.transform(normalizedY)
        val translationY = sign(touchDeltaY) * currentMaxShiftY * easedY

        val centerX = (containerWidth / 2f) + translationX
        val centerY = (containerHeight / 2f) + translationY
        val floatingRect = Rect(
            left = centerX - scaledWidth / 2f,
            top = centerY - scaledHeight / 2f,
            right = centerX + scaledWidth / 2f,
            bottom = centerY + scaledHeight / 2f
        )

        val safeDeviceCorner = deviceCornerRadiusPx.coerceAtLeast(0f)
        val safeTargetCorner = max(safeDeviceCorner, targetDetachedCornerRadiusPx)
        val cornerProgress = (eased * 1.35f).coerceIn(0f, 1f)
        val cornerRadiusPx = lerpFloat(safeDeviceCorner, safeTargetCorner, cornerProgress)

        return ContainerTransformFrame(
            rect = floatingRect,
            cornerRadiusPx = cornerRadiusPx,
            elevationPx = maxElevationPx * eased,
            scrimAlpha = baseScrimAlpha * lerpFloat(1f, 0.72f, eased),
            detailContentAlpha = 1f,
            cardPreviewAlpha = 0f,
            borderAlpha = eased,
            cardPreviewOffsetYPx = 0f,
            backgroundBlend = 1f,
            containerAlpha = 1f
        )
    }

    /**
     * Smoothly returns a cancelled pre-commit floating surface back to edge-to-edge full screen.
     */
    fun computeCancelFrame(
        startFrame: ContainerTransformFrame,
        progress: Float,
        containerWidth: Float,
        containerHeight: Float,
        deviceCornerRadiusPx: Float,
        baseScrimAlpha: Float
    ): ContainerTransformFrame {
        val p = progress.coerceIn(0f, 1f)
        val fullRect = Rect(0f, 0f, containerWidth, containerHeight)
        return ContainerTransformFrame(
            rect = lerpRect(startFrame.rect, fullRect, p),
            cornerRadiusPx = lerpFloat(startFrame.cornerRadiusPx, deviceCornerRadiusPx.coerceAtLeast(0f), p),
            elevationPx = lerpFloat(startFrame.elevationPx, 0f, p),
            scrimAlpha = lerpFloat(startFrame.scrimAlpha, baseScrimAlpha, p),
            detailContentAlpha = 1f,
            cardPreviewAlpha = 0f,
            borderAlpha = lerpFloat(startFrame.borderAlpha, 0f, p),
            cardPreviewOffsetYPx = 0f,
            backgroundBlend = 1f,
            containerAlpha = 1f
        )
    }

    /**
     * Computes one frame of the card-to-fullscreen container transform expansion.
     * [progress] runs from `0f` (source card) to `1f` (full screen).
     */
    fun computeExpandFrame(
        containerWidth: Float,
        containerHeight: Float,
        sourceBounds: CardBounds,
        progress: Float,
        cardCornerRadiusPx: Float,
        deviceCornerRadiusPx: Float,
        maxElevationPx: Float,
        baseScrimAlpha: Float
    ): ContainerTransformFrame {
        val p = progress.coerceIn(0f, 1f)
        val fullRect = Rect(0f, 0f, containerWidth, containerHeight)
        val arc = sin(p * PI).toFloat().coerceIn(0f, 1f)

        return ContainerTransformFrame(
            rect = lerpRect(sourceBounds.visibleRect, fullRect, p),
            cornerRadiusPx = lerpFloat(cardCornerRadiusPx, deviceCornerRadiusPx.coerceAtLeast(0f), p),
            elevationPx = maxElevationPx * arc,
            scrimAlpha = baseScrimAlpha * (p / 0.65f).coerceIn(0f, 1f),
            detailContentAlpha = ((p - 0.12f) / 0.53f).coerceIn(0f, 1f),
            cardPreviewAlpha = (1f - (p / 0.30f)).coerceIn(0f, 1f),
            borderAlpha = arc * 0.55f,
            cardPreviewOffsetYPx = lerpFloat(
                sourceBounds.unclippedRect.top - sourceBounds.visibleRect.top,
                0f,
                p
            ),
            backgroundBlend = (p / 0.55f).coerceIn(0f, 1f),
            containerAlpha = 1f
        )
    }

    /**
     * Computes one frame of the post-commit container transform collapsing from [startFrame]
     * (either the live predictive-back floating window or full screen) back into [targetBounds].
     * [progress] runs from `0f` (commit start) to `1f` (landed flush on the source card).
     */
    fun computeCollapseToCardFrame(
        startFrame: ContainerTransformFrame,
        targetBounds: CardBounds,
        progress: Float,
        cardCornerRadiusPx: Float,
        minMidElevationPx: Float
    ): ContainerTransformFrame {
        val p = progress.coerceIn(0f, 1f)
        val arc = sin(p * PI).toFloat().coerceIn(0f, 1f)
        val elevation = max(
            lerpFloat(startFrame.elevationPx, 0f, p),
            arc * minMidElevationPx
        )

        return ContainerTransformFrame(
            rect = lerpRect(startFrame.rect, targetBounds.visibleRect, p),
            cornerRadiusPx = lerpFloat(startFrame.cornerRadiusPx, cardCornerRadiusPx, p),
            elevationPx = elevation,
            scrimAlpha = startFrame.scrimAlpha * (1f - (p / 0.65f)).coerceIn(0f, 1f),
            detailContentAlpha = startFrame.detailContentAlpha * (1f - (p / 0.52f)).coerceIn(0f, 1f),
            cardPreviewAlpha = ((p - 0.25f) / 0.55f).coerceIn(0f, 1f),
            borderAlpha = lerpFloat(startFrame.borderAlpha, 0f, p),
            cardPreviewOffsetYPx = lerpFloat(
                0f,
                targetBounds.unclippedRect.top - targetBounds.visibleRect.top,
                p
            ),
            backgroundBlend = lerpFloat(
                startFrame.backgroundBlend,
                0f,
                ((p - 0.15f) / 0.65f).coerceIn(0f, 1f)
            ),
            containerAlpha = 1f
        )
    }

    /**
     * Graceful fallback dismiss when the source card is no longer visible in the list
     * (e.g., deleted or filtered out while viewing details).
     */
    fun computeFallbackCollapseFrame(
        startFrame: ContainerTransformFrame,
        progress: Float,
        targetDetachedCornerRadiusPx: Float
    ): ContainerTransformFrame {
        val p = progress.coerceIn(0f, 1f)
        val startRect = startFrame.rect
        val scale = lerpFloat(1f, FALLBACK_TARGET_SCALE, p)
        val targetW = startRect.width * scale
        val targetH = startRect.height * scale
        val cx = startRect.center.x
        val cy = startRect.center.y
        val shrunkRect = Rect(
            left = cx - targetW / 2f,
            top = cy - targetH / 2f,
            right = cx + targetW / 2f,
            bottom = cy + targetH / 2f
        )

        return ContainerTransformFrame(
            rect = shrunkRect,
            cornerRadiusPx = lerpFloat(
                startFrame.cornerRadiusPx,
                max(startFrame.cornerRadiusPx, targetDetachedCornerRadiusPx),
                p
            ),
            elevationPx = lerpFloat(startFrame.elevationPx, 0f, p),
            scrimAlpha = startFrame.scrimAlpha * (1f - (p / 0.70f)).coerceIn(0f, 1f),
            detailContentAlpha = startFrame.detailContentAlpha,
            cardPreviewAlpha = 0f,
            borderAlpha = lerpFloat(startFrame.borderAlpha, 0f, p),
            cardPreviewOffsetYPx = 0f,
            backgroundBlend = 1f,
            containerAlpha = (1f - (p / 0.85f)).coerceIn(0f, 1f)
        )
    }

    internal fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    internal fun lerpRect(start: Rect, end: Rect, fraction: Float): Rect = Rect(
        left = lerpFloat(start.left, end.left, fraction),
        top = lerpFloat(start.top, end.top, fraction),
        right = lerpFloat(start.right, end.right, fraction),
        bottom = lerpFloat(start.bottom, end.bottom, fraction)
    )
}
