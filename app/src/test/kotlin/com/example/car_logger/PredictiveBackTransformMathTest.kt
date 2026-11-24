package com.example.car_logger

import androidx.compose.ui.geometry.Rect
import com.example.car_logger.ui.components.CardBounds
import com.example.car_logger.ui.components.PredictiveBackEdge
import com.example.car_logger.ui.components.PredictiveBackTransformCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackTransformMathTest {

    private val screenW = 1080f
    private val screenH = 2400f
    private val edgeGapPx = 24f // 8dp at 3x density
    private val cardCornerPx = 60f // 20dp at 3x density
    private val detachedCornerPx = 84f // 28dp at 3x density
    private val maxPreCommitElevPx = 48f // 16dp at 3x density
    private val maxExpandElevPx = 36f // 12dp at 3x density
    private val minCollapseMidElevPx = 30f // 10dp at 3x density
    private val baseScrim = 0.32f

    @Test
    fun preCommitAtZeroProgress_isFullScreen() {
        val frame = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 0f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = 0f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 0f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )

        assertEquals(0f, frame.rect.left, 0.01f)
        assertEquals(0f, frame.rect.top, 0.01f)
        assertEquals(screenW, frame.rect.right, 0.01f)
        assertEquals(screenH, frame.rect.bottom, 0.01f)
        assertEquals(0f, frame.cornerRadiusPx, 0.01f)
        assertEquals(0f, frame.elevationPx, 0.01f)
        assertEquals(1f, frame.detailContentAlpha, 0.001f)
        assertEquals(0f, frame.cardPreviewAlpha, 0.001f)
    }

    @Test
    fun preCommitFromLeftEdge_scalesTo90PercentAndLeaves8dpGapOnRight() {
        val frame = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 1f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = 0f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 0f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )

        assertEquals(screenW * 0.90f, frame.rect.width, 0.1f)
        assertEquals(screenH * 0.90f, frame.rect.height, 0.1f)
        // Right edge leaves exact 8dp gap; left edge has wider reveal
        assertEquals(edgeGapPx, screenW - frame.rect.right, 0.1f)
        assertTrue(frame.rect.left > edgeGapPx)
        assertEquals(detachedCornerPx, frame.cornerRadiusPx, 0.1f)
        assertEquals(maxPreCommitElevPx, frame.elevationPx, 0.1f)
    }

    @Test
    fun preCommitFromRightEdge_scalesTo90PercentAndLeaves8dpGapOnLeft() {
        val frame = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 1f,
            swipeEdge = PredictiveBackEdge.RIGHT,
            touchDeltaY = 0f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 0f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )

        assertEquals(screenW * 0.90f, frame.rect.width, 0.1f)
        // Left edge leaves exact 8dp gap; right edge has wider reveal
        assertEquals(edgeGapPx, frame.rect.left, 0.1f)
        assertTrue(screenW - frame.rect.right > edgeGapPx)
    }

    @Test
    fun preCommitVerticalTracking_followsFingerAndNeverPassesEdgeGap() {
        val downward = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 1f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = 2000f, // Extreme downward drag
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 0f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )
        val upward = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 1f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = -2000f, // Extreme upward drag
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 0f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )

        assertTrue(downward.rect.center.y > screenH / 2f)
        assertTrue(upward.rect.center.y < screenH / 2f)
        assertEquals(edgeGapPx, screenH - downward.rect.bottom, 0.1f)
        assertEquals(edgeGapPx, upward.rect.top, 0.1f)

        // Even at small intermediate progress, extreme touchDeltaY must stay strictly inside screen bounds
        val earlyDrag = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 0.08f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = -1500f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 0f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )
        assertTrue(earlyDrag.rect.top >= 0f)
        assertTrue(earlyDrag.rect.bottom <= screenH)
        assertTrue(earlyDrag.rect.left >= 0f)
        assertTrue(earlyDrag.rect.right <= screenW)
    }

    @Test
    fun preCommitUsesDecelerateCurveAndPreservesLargerDeviceCorners() {
        val largerDeviceCornerPx = 110f
        val quarterDrag = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 0.25f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = 0f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = largerDeviceCornerPx,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )

        // Decelerate curve should already be past halfway by 25% linear drag
        val scale = quarterDrag.rect.width / screenW
        assertTrue("Expected scale < 0.95 at 25% raw progress, got $scale", scale < 0.95f)
        assertEquals(largerDeviceCornerPx, quarterDrag.cornerRadiusPx, 0.1f)
    }

    @Test
    fun cancelFrame_smoothlyReturnsToFullScreen() {
        val dragged = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 0.6f,
            swipeEdge = PredictiveBackEdge.RIGHT,
            touchDeltaY = 120f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 30f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )

        val atStart = PredictiveBackTransformCalculator.computeCancelFrame(
            startFrame = dragged,
            progress = 0f,
            containerWidth = screenW,
            containerHeight = screenH,
            deviceCornerRadiusPx = 30f,
            baseScrimAlpha = baseScrim
        )
        assertEquals(dragged.rect, atStart.rect)
        assertEquals(dragged.cornerRadiusPx, atStart.cornerRadiusPx, 0.01f)

        val atEnd = PredictiveBackTransformCalculator.computeCancelFrame(
            startFrame = dragged,
            progress = 1f,
            containerWidth = screenW,
            containerHeight = screenH,
            deviceCornerRadiusPx = 30f,
            baseScrimAlpha = baseScrim
        )
        assertEquals(Rect(0f, 0f, screenW, screenH), atEnd.rect)
        assertEquals(30f, atEnd.cornerRadiusPx, 0.01f)
        assertEquals(0f, atEnd.elevationPx, 0.01f)
        assertEquals(0f, atEnd.borderAlpha, 0.01f)
    }

    @Test
    fun expandFrame_morphsFromCardToFullScreenWithCrossFade() {
        val cardBounds = CardBounds(
            visibleRect = Rect(48f, 600f, 1032f, 1380f),
            unclippedRect = Rect(48f, 540f, 1032f, 1380f) // Top 60px scrolled behind header
        )

        val start = PredictiveBackTransformCalculator.computeExpandFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            sourceBounds = cardBounds,
            progress = 0f,
            cardCornerRadiusPx = cardCornerPx,
            deviceCornerRadiusPx = 40f,
            maxElevationPx = maxExpandElevPx,
            baseScrimAlpha = baseScrim
        )
        assertEquals(cardBounds.visibleRect, start.rect)
        assertEquals(cardCornerPx, start.cornerRadiusPx, 0.01f)
        assertEquals(1f, start.cardPreviewAlpha, 0.001f)
        assertEquals(0f, start.detailContentAlpha, 0.001f)
        assertEquals(-60f, start.cardPreviewOffsetYPx, 0.01f)
        assertEquals(0f, start.scrimAlpha, 0.001f)

        val mid = PredictiveBackTransformCalculator.computeExpandFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            sourceBounds = cardBounds,
            progress = 0.22f,
            cardCornerRadiusPx = cardCornerPx,
            deviceCornerRadiusPx = 40f,
            maxElevationPx = maxExpandElevPx,
            baseScrimAlpha = baseScrim
        )
        assertTrue(mid.cardPreviewAlpha in 0.01f..0.99f)
        assertTrue(mid.detailContentAlpha in 0.01f..0.99f)
        assertTrue(mid.elevationPx > 0f)

        val end = PredictiveBackTransformCalculator.computeExpandFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            sourceBounds = cardBounds,
            progress = 1f,
            cardCornerRadiusPx = cardCornerPx,
            deviceCornerRadiusPx = 40f,
            maxElevationPx = maxExpandElevPx,
            baseScrimAlpha = baseScrim
        )
        assertEquals(Rect(0f, 0f, screenW, screenH), end.rect)
        assertEquals(40f, end.cornerRadiusPx, 0.01f)
        assertEquals(0f, end.cardPreviewAlpha, 0.001f)
        assertEquals(1f, end.detailContentAlpha, 0.001f)
        assertEquals(0f, end.elevationPx, 0.01f)
        assertEquals(baseScrim, end.scrimAlpha, 0.001f)
        assertEquals(0f, end.cardPreviewOffsetYPx, 0.01f)
    }

    @Test
    fun collapseToCard_isContinuousFromDraggedWindowAndLandsExactOnCard() {
        val dragged = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 0.7f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = 180f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 30f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )
        val targetCard = CardBounds(
            visibleRect = Rect(48f, 920f, 1032f, 1700f),
            unclippedRect = Rect(48f, 920f, 1032f, 1700f)
        )

        val start = PredictiveBackTransformCalculator.computeCollapseToCardFrame(
            startFrame = dragged,
            targetBounds = targetCard,
            progress = 0f,
            cardCornerRadiusPx = cardCornerPx,
            minMidElevationPx = minCollapseMidElevPx
        )
        assertEquals(dragged.rect, start.rect)
        assertEquals(dragged.cornerRadiusPx, start.cornerRadiusPx, 0.01f)
        assertEquals(dragged.elevationPx, start.elevationPx, 0.01f)
        assertEquals(dragged.scrimAlpha, start.scrimAlpha, 0.001f)
        assertEquals(1f, start.detailContentAlpha, 0.001f)
        assertEquals(0f, start.cardPreviewAlpha, 0.001f)

        val end = PredictiveBackTransformCalculator.computeCollapseToCardFrame(
            startFrame = dragged,
            targetBounds = targetCard,
            progress = 1f,
            cardCornerRadiusPx = cardCornerPx,
            minMidElevationPx = minCollapseMidElevPx
        )
        assertEquals(targetCard.visibleRect, end.rect)
        assertEquals(cardCornerPx, end.cornerRadiusPx, 0.01f)
        assertEquals(0f, end.elevationPx, 0.01f)
        assertEquals(0f, end.scrimAlpha, 0.001f)
        assertEquals(0f, end.detailContentAlpha, 0.001f)
        assertEquals(1f, end.cardPreviewAlpha, 0.001f)
        assertEquals(0f, end.backgroundBlend, 0.001f)
    }

    @Test
    fun collapseFromFullScreenButtonBack_liftsElevationMidFlightAndLandsAtZero() {
        val fullScreen = PredictiveBackTransformCalculator.createFullScreenFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            deviceCornerRadiusPx = 30f,
            baseScrimAlpha = baseScrim
        )
        val targetCard = CardBounds(Rect(48f, 300f, 1032f, 1080f))

        val start = PredictiveBackTransformCalculator.computeCollapseToCardFrame(
            startFrame = fullScreen,
            targetBounds = targetCard,
            progress = 0f,
            cardCornerRadiusPx = cardCornerPx,
            minMidElevationPx = minCollapseMidElevPx
        )
        val mid = PredictiveBackTransformCalculator.computeCollapseToCardFrame(
            startFrame = fullScreen,
            targetBounds = targetCard,
            progress = 0.5f,
            cardCornerRadiusPx = cardCornerPx,
            minMidElevationPx = minCollapseMidElevPx
        )
        val end = PredictiveBackTransformCalculator.computeCollapseToCardFrame(
            startFrame = fullScreen,
            targetBounds = targetCard,
            progress = 1f,
            cardCornerRadiusPx = cardCornerPx,
            minMidElevationPx = minCollapseMidElevPx
        )

        assertEquals(0f, start.elevationPx, 0.01f)
        assertEquals(minCollapseMidElevPx, mid.elevationPx, 0.1f)
        assertEquals(0f, end.elevationPx, 0.01f)
    }

    @Test
    fun fallbackCollapse_shrinksCenteredAndFadesOutCleanly() {
        val dragged = PredictiveBackTransformCalculator.computePreCommitFrame(
            containerWidth = screenW,
            containerHeight = screenH,
            rawProgress = 0.5f,
            swipeEdge = PredictiveBackEdge.LEFT,
            touchDeltaY = 0f,
            edgeGapPx = edgeGapPx,
            deviceCornerRadiusPx = 0f,
            targetDetachedCornerRadiusPx = detachedCornerPx,
            maxElevationPx = maxPreCommitElevPx,
            baseScrimAlpha = baseScrim
        )

        val end = PredictiveBackTransformCalculator.computeFallbackCollapseFrame(
            startFrame = dragged,
            progress = 1f,
            targetDetachedCornerRadiusPx = detachedCornerPx
        )
        assertEquals(dragged.rect.center.x, end.rect.center.x, 0.1f)
        assertEquals(dragged.rect.center.y, end.rect.center.y, 0.1f)
        assertEquals(
            dragged.rect.width * PredictiveBackTransformCalculator.FALLBACK_TARGET_SCALE,
            end.rect.width,
            0.1f
        )
        assertEquals(0f, end.containerAlpha, 0.001f)
        assertEquals(0f, end.scrimAlpha, 0.001f)
        assertEquals(0f, end.elevationPx, 0.01f)
    }

    @Test
    fun cardBoundsValidation_rejectsDegenerateBounds() {
        assertFalse(CardBounds(Rect(0f, 0f, 0f, 0f)).isValidForMorph)
        assertFalse(CardBounds(Rect(10f, 10f, 20f, 20f)).isValidForMorph)
        assertTrue(CardBounds(Rect(48f, 300f, 1032f, 1080f)).isValidForMorph)
    }
}
