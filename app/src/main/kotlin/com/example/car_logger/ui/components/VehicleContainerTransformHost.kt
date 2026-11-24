package com.example.car_logger.ui.components

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.car_logger.data.model.Car
import com.example.car_logger.ui.theme.ExpressiveMotionTokens
import com.example.car_logger.ui.theme.ExpressiveThemeTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Tracks live screen-space bounds of vehicle cards inside the home list without triggering
 * recompositions during scroll.
 */
class VehicleCardBoundsRegistry {
    private var hostCoordinates: LayoutCoordinates? = null
    private val cardCoordinates = mutableMapOf<String, LayoutCoordinates>()
    private val cachedBounds = mutableMapOf<String, CardBounds>()

    fun setHostCoordinates(coordinates: LayoutCoordinates) {
        hostCoordinates = coordinates
    }

    fun updateCard(carId: String, coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        cardCoordinates[carId] = coordinates
        computeBounds(coordinates)?.let { cachedBounds[carId] = it }
    }

    fun removeCard(carId: String) {
        cardCoordinates.remove(carId)
        cachedBounds.remove(carId)
    }

    fun getBounds(carId: String): CardBounds? {
        val liveCoords = cardCoordinates[carId]
        if (liveCoords != null && liveCoords.isAttached) {
            val fresh = computeBounds(liveCoords)
            if (fresh != null) {
                cachedBounds[carId] = fresh
                return fresh
            }
        }
        return cachedBounds[carId]?.takeIf { it.isValidForMorph }
    }

    private fun computeBounds(cardCoords: LayoutCoordinates): CardBounds? {
        if (!cardCoords.isAttached) return null
        val host = hostCoordinates
        val bounds = if (host != null && host.isAttached) {
            CardBounds(
                visibleRect = host.localBoundingBoxOf(cardCoords, clipBounds = true),
                unclippedRect = host.localBoundingBoxOf(cardCoords, clipBounds = false)
            )
        } else {
            val pos = cardCoords.positionInRoot()
            CardBounds(
                visibleRect = cardCoords.boundsInRoot(),
                unclippedRect = Rect(
                    left = pos.x,
                    top = pos.y,
                    right = pos.x + cardCoords.size.width,
                    bottom = pos.y + cardCoords.size.height
                )
            )
        }
        return bounds.takeIf { it.isValidForMorph }
    }
}

private enum class TransformPhase {
    IDLE,
    EXPANDING,
    OPEN,
    DRAGGING_BACK,
    CANCELLING_BACK,
    COLLAPSING
}

private data class VehicleDetailSession(
    val sessionId: Long,
    val carId: String,
    val initialCar: Car?
)

private class SessionViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}

private fun View.resolveDeviceCornerRadiusPx(): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
    val insets = rootWindowInsets ?: return 0f
    val radii = listOfNotNull(
        insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius,
        insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius,
        insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius,
        insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius
    )
    return (radii.maxOrNull() ?: 0).toFloat()
}

/**
 * Gmail-style container transform & predictive back host bridging the Home garage list and
 * Vehicle Details screen.
 *
 * - Opening a car expands smoothly from that card's live on-screen bounds to full screen.
 * - Swiping back from either edge detaches the detail screen into a floating rounded card
 *   (~90% scale, shifting away from the swiped edge and tracking thumb Y) over the dimmed Home screen.
 * - Cancelling the swipe springs smoothly back to full screen without losing detail state.
 * - Releasing the swipe (or tapping the back arrow) morphs the surface directly back into
 *   the tapped car card in the Garage list.
 */
@Composable
fun VehicleContainerTransformHost(
    cars: List<Car>,
    isPredictiveBackEnabled: Boolean = true,
    homeContent: @Composable (boundsRegistry: VehicleCardBoundsRegistry, onOpenCar: (Car) -> Unit) -> Unit,
    cardPreviewContent: @Composable (car: Car) -> Unit,
    detailContent: @Composable (carId: String, initialCar: Car?, onNavigateBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseScrimAlpha = if (isDarkTheme) {
        PredictiveBackTransformCalculator.DARK_SCRIM_ALPHA
    } else {
        PredictiveBackTransformCalculator.LIGHT_SCRIM_ALPHA
    }
    val borderMaxAlpha = if (isDarkTheme) 0.30f else 0.18f
    val cardBackdropColor = ExpressiveThemeTokens.canvasColor
    val detailShellColor = ExpressiveThemeTokens.shellColor
    val borderBaseColor = MaterialTheme.colorScheme.outlineVariant

    val edgeGapPx = with(density) { PredictiveBackTransformCalculator.EDGE_GAP_DP.dp.toPx() }
    val cardCornerRadiusPx = with(density) { PredictiveBackTransformCalculator.CARD_CORNER_RADIUS_DP.dp.toPx() }
    val detachedCornerRadiusPx = with(density) { PredictiveBackTransformCalculator.DETACHED_CORNER_RADIUS_DP.dp.toPx() }
    val preCommitMaxElevationPx = with(density) { PredictiveBackTransformCalculator.PRE_COMMIT_MAX_ELEVATION_DP.dp.toPx() }
    val expandMaxElevationPx = with(density) { PredictiveBackTransformCalculator.EXPAND_MAX_ELEVATION_DP.dp.toPx() }
    val collapseMinMidElevationPx = with(density) { PredictiveBackTransformCalculator.COLLAPSE_MIN_MID_ELEVATION_DP.dp.toPx() }

    val latestCars by rememberUpdatedState(cars)
    val boundsRegistry = remember { VehicleCardBoundsRegistry() }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }

    var savedCarId by rememberSaveable { mutableStateOf<String?>(null) }
    var nextSessionId by remember { mutableLongStateOf(1L) }
    var activeSession by remember {
        mutableStateOf(
            savedCarId?.let { restoredId ->
                VehicleDetailSession(
                    sessionId = 0L,
                    carId = restoredId,
                    initialCar = cars.find { it.id == restoredId }
                )
            }
        )
    }
    var phase by remember {
        mutableStateOf(if (activeSession != null) TransformPhase.OPEN else TransformPhase.IDLE)
    }

    // Updated at display refresh rate; read only inside layout/draw modifiers to avoid recomposition.
    val currentFrameState = remember { mutableStateOf<ContainerTransformFrame?>(null) }
    var activeMorphBounds by remember { mutableStateOf<CardBounds?>(null) }
    var activeAnimationJob by remember { mutableStateOf<Job?>(null) }

    fun openCar(car: Car) {
        activeAnimationJob?.cancel()
        val sessionId = nextSessionId++
        savedCarId = car.id
        activeSession = VehicleDetailSession(
            sessionId = sessionId,
            carId = car.id,
            initialCar = car
        )

        val sourceBounds = boundsRegistry.getBounds(car.id)
        val w = hostSize.width.toFloat()
        val h = hostSize.height.toFloat()

        if (sourceBounds == null || w <= 0f || h <= 0f) {
            activeMorphBounds = null
            currentFrameState.value = null
            phase = TransformPhase.OPEN
            return
        }

        val deviceCornerPx = view.resolveDeviceCornerRadiusPx()
        activeMorphBounds = sourceBounds
        phase = TransformPhase.EXPANDING
        currentFrameState.value = PredictiveBackTransformCalculator.computeExpandFrame(
            containerWidth = w,
            containerHeight = h,
            sourceBounds = sourceBounds,
            progress = 0f,
            cardCornerRadiusPx = cardCornerRadiusPx,
            deviceCornerRadiusPx = deviceCornerPx,
            maxElevationPx = expandMaxElevationPx,
            baseScrimAlpha = baseScrimAlpha
        )

        activeAnimationJob = coroutineScope.launch {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = ExpressiveMotionTokens.ContainerExpandSpring
            ) {
                currentFrameState.value = PredictiveBackTransformCalculator.computeExpandFrame(
                    containerWidth = w,
                    containerHeight = h,
                    sourceBounds = sourceBounds,
                    progress = value,
                    cardCornerRadiusPx = cardCornerRadiusPx,
                    deviceCornerRadiusPx = deviceCornerPx,
                    maxElevationPx = expandMaxElevationPx,
                    baseScrimAlpha = baseScrimAlpha
                )
            }
            activeMorphBounds = null
            currentFrameState.value = null
            phase = TransformPhase.OPEN
        }
    }

    fun launchCollapse(fromFrame: ContainerTransformFrame?) {
        if (phase == TransformPhase.COLLAPSING) return
        val session = activeSession ?: return
        activeAnimationJob?.cancel()

        val w = hostSize.width.toFloat()
        val h = hostSize.height.toFloat()
        if (w <= 0f || h <= 0f) {
            activeSession = null
            savedCarId = null
            activeMorphBounds = null
            currentFrameState.value = null
            phase = TransformPhase.IDLE
            return
        }

        val deviceCornerPx = view.resolveDeviceCornerRadiusPx()
        val startFrame = fromFrame ?: PredictiveBackTransformCalculator.createFullScreenFrame(
            containerWidth = w,
            containerHeight = h,
            deviceCornerRadiusPx = deviceCornerPx,
            baseScrimAlpha = baseScrimAlpha
        )
        val targetBounds = boundsRegistry.getBounds(session.carId)

        activeMorphBounds = targetBounds
        phase = TransformPhase.COLLAPSING
        currentFrameState.value = startFrame

        activeAnimationJob = coroutineScope.launch {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = ExpressiveMotionTokens.ContainerCollapseSpring
            ) {
                currentFrameState.value = if (targetBounds != null) {
                    PredictiveBackTransformCalculator.computeCollapseToCardFrame(
                        startFrame = startFrame,
                        targetBounds = targetBounds,
                        progress = value,
                        cardCornerRadiusPx = cardCornerRadiusPx,
                        minMidElevationPx = collapseMinMidElevationPx
                    )
                } else {
                    PredictiveBackTransformCalculator.computeFallbackCollapseFrame(
                        startFrame = startFrame,
                        progress = value,
                        targetDetachedCornerRadiusPx = detachedCornerRadiusPx
                    )
                }
            }
            activeSession = null
            savedCarId = null
            activeMorphBounds = null
            currentFrameState.value = null
            phase = TransformPhase.IDLE
        }
    }

    fun launchCancel(fromFrame: ContainerTransformFrame) {
        if (activeSession == null) return
        activeAnimationJob?.cancel()

        val w = hostSize.width.toFloat()
        val h = hostSize.height.toFloat()
        if (w <= 0f || h <= 0f) {
            currentFrameState.value = null
            phase = TransformPhase.OPEN
            return
        }

        val deviceCornerPx = view.resolveDeviceCornerRadiusPx()
        phase = TransformPhase.CANCELLING_BACK

        activeAnimationJob = coroutineScope.launch {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = ExpressiveMotionTokens.PredictiveBackCancelSpring
            ) {
                currentFrameState.value = PredictiveBackTransformCalculator.computeCancelFrame(
                    startFrame = fromFrame,
                    progress = value,
                    containerWidth = w,
                    containerHeight = h,
                    deviceCornerRadiusPx = deviceCornerPx,
                    baseScrimAlpha = baseScrimAlpha
                )
            }
            currentFrameState.value = null
            phase = TransformPhase.OPEN
        }
    }

    // Intercept system predictive back only while a vehicle detail session is active and not already collapsing.
    PredictiveBackHandler(
        enabled = activeSession != null &&
            phase != TransformPhase.COLLAPSING &&
            isPredictiveBackEnabled
    ) { progressFlow ->
        activeAnimationJob?.cancel()
        val preGestureFrame = currentFrameState.value
        activeMorphBounds = null
        val deviceCornerPx = view.resolveDeviceCornerRadiusPx()
        var initialTouchY: Float? = null
        var receivedDragEvent = false

        try {
            progressFlow.collect { backEvent ->
                val w = hostSize.width.toFloat()
                val h = hostSize.height.toFloat()
                if (w > 0f && h > 0f) {
                    if (initialTouchY == null) {
                        initialTouchY = backEvent.touchY
                    }
                    receivedDragEvent = true
                    phase = TransformPhase.DRAGGING_BACK
                    currentFrameState.value = PredictiveBackTransformCalculator.computePreCommitFrame(
                        containerWidth = w,
                        containerHeight = h,
                        rawProgress = backEvent.progress,
                        swipeEdge = backEvent.swipeEdge,
                        touchDeltaY = backEvent.touchY - (initialTouchY ?: backEvent.touchY),
                        edgeGapPx = edgeGapPx,
                        deviceCornerRadiusPx = deviceCornerPx,
                        targetDetachedCornerRadiusPx = detachedCornerRadiusPx,
                        maxElevationPx = preCommitMaxElevationPx,
                        baseScrimAlpha = baseScrimAlpha
                    )
                }
            }
            // Gesture committed (or instant back press completed): morph back into the source card.
            val commitFrame = if (receivedDragEvent) currentFrameState.value else preGestureFrame
            coroutineScope.launch {
                launchCollapse(fromFrame = commitFrame)
            }
        } catch (e: CancellationException) {
            if (activeSession != null && phase != TransformPhase.COLLAPSING && phase != TransformPhase.IDLE) {
                val cancelStart = currentFrameState.value
                if (cancelStart != null) {
                    coroutineScope.launch {
                        launchCancel(fromFrame = cancelStart)
                    }
                } else {
                    phase = TransformPhase.OPEN
                }
            }
            throw e
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { hostSize = it }
            .onGloballyPositioned { boundsRegistry.setHostCoordinates(it) }
    ) {
        // Base layer: HomeScreen stays mounted to preserve scroll position and reveal live behind the detached window.
        val isHomeObscured = activeSession != null && phase == TransformPhase.OPEN
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    if (isHomeObscured) {
                        hideFromAccessibility()
                    }
                }
        ) {
            homeContent(boundsRegistry) { car -> openCar(car) }
        }

        // Foreground layer: VehicleDetailsScreen wrapped in the morphing & predictive-back surface.
        val session = activeSession
        if (session != null) {
            val viewModelStoreOwner = remember(session.sessionId) { SessionViewModelStoreOwner() }
            DisposableEffect(viewModelStoreOwner) {
                onDispose { viewModelStoreOwner.clear() }
            }

            val showCardPreview = (phase == TransformPhase.EXPANDING || phase == TransformPhase.COLLAPSING) &&
                activeMorphBounds != null
            val previewCar = remember(session.carId, latestCars, session.initialCar) {
                latestCars.find { it.id == session.carId } ?: session.initialCar
            }
            val blockDetailTouches = phase == TransformPhase.EXPANDING ||
                phase == TransformPhase.DRAGGING_BACK ||
                phase == TransformPhase.CANCELLING_BACK ||
                phase == TransformPhase.COLLAPSING

            Box(modifier = Modifier.fillMaxSize()) {
                // Dimming scrim and touch shield over HomeScreen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val frame = currentFrameState.value
                            if (frame != null && frame.scrimAlpha > 0.001f) {
                                drawRect(Color.Black.copy(alpha = frame.scrimAlpha))
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                )

                // Morphing / detached floating detail surface
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val frame = currentFrameState.value
                            if (frame == null) {
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, 0)
                                }
                            } else {
                                val w = frame.rect.width.roundToInt().coerceAtLeast(1)
                                val h = frame.rect.height.roundToInt().coerceAtLeast(1)
                                val placeable = measurable.measure(Constraints.fixed(w, h))
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(
                                        x = frame.rect.left.roundToInt(),
                                        y = frame.rect.top.roundToInt()
                                    )
                                }
                            }
                        }
                        .graphicsLayer {
                            val frame = currentFrameState.value
                            if (frame != null) {
                                val radiusPx = frame.cornerRadiusPx.coerceAtLeast(0f)
                                translationX = frame.rect.left - frame.rect.left.roundToInt()
                                translationY = frame.rect.top - frame.rect.top.roundToInt()
                                shape = RoundedCornerShape(radiusPx)
                                clip = true
                                shadowElevation = frame.elevationPx.coerceAtLeast(0f)
                                alpha = frame.containerAlpha
                            } else {
                                translationX = 0f
                                translationY = 0f
                                clip = false
                                shadowElevation = 0f
                                alpha = 1f
                            }
                        }
                        .drawBehind {
                            val frame = currentFrameState.value
                            if (frame != null) {
                                val bg = lerp(cardBackdropColor, detailShellColor, frame.backgroundBlend)
                                drawRect(bg)
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val frame = currentFrameState.value
                            if (frame != null && frame.borderAlpha > 0.001f) {
                                val r = frame.cornerRadiusPx.coerceAtLeast(0f)
                                val strokePx = 1.dp.toPx()
                                drawRoundRect(
                                    color = borderBaseColor.copy(alpha = borderMaxAlpha * frame.borderAlpha),
                                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                                    size = Size(
                                        (size.width - strokePx).coerceAtLeast(0f),
                                        (size.height - strokePx).coerceAtLeast(0f)
                                    ),
                                    cornerRadius = CornerRadius(r, r),
                                    style = Stroke(width = strokePx)
                                )
                            }
                        }
                ) {
                    // Layer 1: VehicleDetailsScreen measured at full-screen width/height (zero relayout)
                    Box(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val frame = currentFrameState.value
                                val targetConstraints = if (frame != null && hostSize.width > 0 && hostSize.height > 0) {
                                    Constraints.fixed(hostSize.width, hostSize.height)
                                } else {
                                    constraints
                                }
                                val placeable = measurable.measure(targetConstraints)
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(0, 0)
                                }
                            }
                            .graphicsLayer {
                                val frame = currentFrameState.value
                                if (frame != null && hostSize.width > 0) {
                                    val scale = frame.rect.width / hostSize.width.toFloat()
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = frame.detailContentAlpha
                                } else {
                                    scaleX = 1f
                                    scaleY = 1f
                                    alpha = 1f
                                }
                            }
                    ) {
                        key(session.sessionId) {
                            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                                detailContent(
                                    session.carId,
                                    session.initialCar,
                                    { launchCollapse(fromFrame = null) }
                                )
                            }
                        }
                    }

                    // Layer 2: Source VehicleCard preview cross-faded during expand & collapse morphs
                    val morphBounds = activeMorphBounds
                    if (showCardPreview && previewCar != null && morphBounds != null) {
                        val cardW = morphBounds.unclippedRect.width.roundToInt().coerceAtLeast(1)
                        val cardH = morphBounds.unclippedRect.height.roundToInt().coerceAtLeast(1)
                        Box(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(Constraints.fixed(cardW, cardH))
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        placeable.place(0, 0)
                                    }
                                }
                                .graphicsLayer {
                                    val frame = currentFrameState.value
                                    if (frame != null) {
                                        val scale = frame.rect.width / cardW.toFloat()
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = frame.cardPreviewOffsetYPx * scale
                                        alpha = frame.cardPreviewAlpha
                                    } else {
                                        alpha = 0f
                                    }
                                }
                        ) {
                            cardPreviewContent(previewCar)
                        }
                    }

                    // Prevent stray button taps inside the detail screen while animating/dragging
                    if (blockDetailTouches) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent().changes.forEach { it.consume() }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
