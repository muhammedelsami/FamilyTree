package com.familytree.core.diagram.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.familytree.core.diagram.DIAGRAM_MARGIN_DP
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the original opens every diagram at, unless the whole of it already fits the screen. */
private const val OPENING_SCALE = 0.7f

/**
 * A viewport that can be dragged and pinched over a diagram.
 *
 * Replaces the original's hand-written `MoveLayout`, which combined a
 * `ScaleGestureDetector`, an `OverScroller` and a `VelocityTracker` across 219 lines.
 * Compose's transform gestures cover the gesture arithmetic, so what is left here is what
 * actually needs thought: how far the content may travel, and where it opens.
 *
 * Both answers are the original's, because both are load-bearing and neither is obvious:
 *
 * - **A diagram opens at [OPENING_SCALE], not zoomed to fit.** Fitting sounds better — the
 *   whole tree at a glance — and it quietly disables the finger: content the exact size of
 *   the viewport has nowhere to go, so a zoomed-to-fit tree cannot be nudged sideways at
 *   all, and it is precisely the outermost cards, the ones under the app bar and in the
 *   corners, that the reader wants to drag into view.
 * - **A diagram small enough to fit is enlarged until it touches the edges**, which is why
 *   [fitScale] is deliberately allowed above 1. Left at natural size a two-card tree is a
 *   stamp in the middle of an empty screen that also cannot be moved, since there is nothing
 *   offscreen to move towards.
 * - **The finger may always overrun the resting bounds** by a quarter of the viewport, and
 *   the content springs back when it is released. So the drag never feels dead, even where
 *   there is genuinely nowhere to go, and the diagram still cannot be parked out of sight.
 *
 * @param contentSize the drawn size in pixels. The zoom limits are derived from it.
 * @param contentPadding empty space kept around the content on all four sides. Without it
 *   the outermost cards sit flush against the edges of the screen with nothing round them,
 *   which reads as though the tree has been cut off rather than ended. The original
 *   reserved the same margin by giving the box holding the cards a 50dp padding and adding
 *   it twice to the scrollable size; this does the same arithmetic in one place. It is part
 *   of the content, so it zooms with it.
 * @param focusOn a point in content coordinates to centre on when it first appears.
 * @param content receives the current scale and offset and is expected to apply them
 *   itself. The offset already carries the padding, so content coordinates stay the
 *   engine's — nothing downstream has to know this parameter exists.
 *
 * The transform is handed to the content rather than applied to a wrapper here, and that
 * is not a matter of taste. A `graphicsLayer` is backed by a render node, and a render
 * node larger than the GPU's maximum texture — commonly 4096 px — silently draws nothing
 * at all. A family tree five generations wide is 6000 px across at natural size, so
 * wrapping it in one transformed layer produces a blank screen on exactly the trees this
 * feature exists for. Letting the content place its own pieces keeps every layer the size
 * of a card.
 */
@Composable
fun PannableZoomable(
    contentSize: IntSize,
    modifier: Modifier = Modifier,
    contentPadding: Dp = DIAGRAM_MARGIN_DP.dp,
    focusOn: Offset? = null,
    maxScale: Float = 5f,
    content: @Composable (scale: Float, offset: Offset) -> Unit,
) {
    val padding = with(LocalDensity.current) { contentPadding.toPx() }
    // Everything below works on the padded box, so the margin is pannable and zoomable
    // exactly like the diagram inside it.
    val paddedSize = remember(contentSize, padding) {
        if (contentSize == IntSize.Zero) {
            IntSize.Zero
        } else {
            IntSize(
                (contentSize.width + padding * 2).roundToInt(),
                (contentSize.height + padding * 2).roundToInt(),
            )
        }
    }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(OPENING_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var settled by remember(paddedSize) { mutableStateOf(false) }

    /** The zoom at which the whole diagram is on screen; also the furthest it can be zoomed out. */
    val fitScale = remember(viewport, paddedSize) {
        if (viewport == IntSize.Zero || paddedSize.width == 0 || paddedSize.height == 0) {
            1f
        } else {
            min(
                viewport.width.toFloat() / paddedSize.width,
                viewport.height.toFloat() / paddedSize.height,
            )
        }
    }

    fun clamp(candidate: Offset, atScale: Float, overrun: Boolean): Offset {
        // A quarter of the viewport, shrinking with the zoom exactly as the original's
        // `calcOverScroll` does, so the give under the finger stays proportionate to what
        // is on screen rather than to the size of the tree.
        val slack = if (overrun) atScale / 4f else 0f
        return Offset(
            clampAxis(
                candidate = candidate.x,
                viewport = viewport.width.toFloat(),
                scaled = paddedSize.width * atScale,
                overrun = viewport.width * slack,
            ),
            clampAxis(
                candidate = candidate.y,
                viewport = viewport.height.toFloat(),
                scaled = paddedSize.height * atScale,
                overrun = viewport.height * slack,
            ),
        )
    }

    // Open centred on the point of interest — usually the person the diagram is about.
    // [focusOn] arrives in the engine's coordinates, so the padding has to be added before
    // it is used against the padded box.
    LaunchedEffect(viewport, paddedSize, focusOn, fitScale) {
        if (settled || viewport == IntSize.Zero || paddedSize == IntSize.Zero) return@LaunchedEffect
        // Seven tenths, unless the whole diagram already fits at seven tenths — then it is
        // scaled to the screen instead, up as readily as down.
        val opening = if (
            paddedSize.width * OPENING_SCALE < viewport.width &&
            paddedSize.height * OPENING_SCALE < viewport.height
        ) {
            fitScale
        } else {
            OPENING_SCALE
        }
        scale = opening
        offset = if (focusOn == null) {
            clamp(Offset.Zero, opening, overrun = false)
        } else {
            clamp(
                Offset(
                    viewport.width / 2f - (focusOn.x + padding) * opening,
                    viewport.height / 2f - (focusOn.y + padding) * opening,
                ),
                opening,
                overrun = false,
            )
        }
        settled = true
    }

    val decay = rememberSplineBasedDecay<Offset>()

    /**
     * Carries the release: throws the content on with the finger's own speed, and returns it
     * inside its bounds if the drag left them. The original got both from one `OverScroller`
     * fling; here the two cases are separate animations because they are separate feelings.
     */
    suspend fun settle(velocity: Velocity) {
        val resting = clamp(offset, scale, overrun = false)
        if (resting != offset) {
            animate(
                typeConverter = Offset.VectorConverter,
                initialValue = offset,
                targetValue = resting,
                initialVelocity = Offset(velocity.x, velocity.y),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offset = value }
        } else {
            AnimationState(
                typeConverter = Offset.VectorConverter,
                initialValue = offset,
                initialVelocity = Offset(velocity.x, velocity.y),
            ).animateDecay(decay) {
                val bounded = clamp(value, scale, overrun = false)
                offset = bounded
                // Stop dead at the edge rather than gliding along it.
                if (bounded != value) cancelAnimation()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(paddedSize, fitScale) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(fitScale, max(fitScale, maxScale))
                    // Zoom about the pinch centre: the point under the fingers stays put.
                    val focus = (centroid - offset) / scale
                    val candidate = centroid - focus * newScale + pan
                    scale = newScale
                    offset = clamp(candidate, newScale, overrun = true)
                }
            }
            // A second watcher, because the transform detector above reports what the fingers
            // did but not that they have gone, and the release is half the behaviour. It reads
            // the raw event stream rather than one of the gesture helpers: those are written to
            // recognise one gesture and hand back, and the handing back is what breaks here —
            // the next recogniser starts before the coroutine it just launched has run a line,
            // and cancels the settle it was supposed to protect. A plain loop over the events
            // has one state to keep, the fingers being down, and cannot get that wrong.
            .pointerInput(paddedSize, fitScale) {
                coroutineScope {
                    var settling: Job? = null
                    val tracker = VelocityTracker()
                    var touching = false
                    awaitPointerEventScope {
                        while (true) {
                            // The initial pass: consuming nothing and looking before the
                            // detector above does, so nothing it decides can hide a finger.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.any { it.pressed }
                            if (down) {
                                if (!touching) {
                                    settling?.cancel()
                                    tracker.resetTracking()
                                }
                                event.changes.firstOrNull { it.pressed }
                                    ?.let(tracker::addPointerInputChange)
                            } else if (touching) {
                                settling = launch { settle(tracker.calculateVelocity()) }
                            }
                            touching = down
                        }
                    }
                }
            },
    ) {
        // The padding is folded into the offset, so the content goes on placing things at
        // the engine's own coordinates and lands inside the margin without knowing it.
        content(scale, offset + Offset(padding * scale, padding * scale))
    }
}

/**
 * How far the content may sit along one axis, in screen pixels.
 *
 * At rest the rule is the original's: content larger than the viewport must cover it, and
 * content smaller than it is centred — a single position, not a range. Under the finger the
 * whole thing may be dragged [overrun] further in either direction, which is what stops a
 * diagram that has nowhere to go from feeling broken. Nothing keeps it there once the finger
 * lifts; the caller settles it back.
 */
internal fun clampAxis(candidate: Float, viewport: Float, scaled: Float, overrun: Float = 0f): Float {
    val low: Float
    val high: Float
    if (scaled >= viewport) {
        low = viewport - scaled
        high = 0f
    } else {
        val centred = (viewport - scaled) / 2f
        low = centred
        high = centred
    }
    return candidate.coerceIn(low - overrun, high + overrun)
}
