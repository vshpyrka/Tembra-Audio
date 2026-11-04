package audio.tembra.ui.timeline.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import kotlin.math.abs
import kotlin.math.pow

/**
 * The timeline's viewport gestures on a lane or the ruler (spec FR-015, FR-016; clarification 3): a two-finger pinch
 * zooms around its centroid and pans while it lasts; a plain horizontal drag scrolls when [dragScrolls] (the ruler's
 * drag scrubs instead); a wheel or trackpad scroll pans, and with Ctrl or Command (or a trackpad pinch, which arrives
 * the same way) zooms around the pointer. A tap never reaches here: the slop decides. Each gesture runs inside
 * `PerfTrace.animation(TIMELINE_ZOOM)` or `TIMELINE_SCROLL` (contracts/perf-instrumentation.md).
 *
 * Desktop pointers (feature 011, spec FR-014; research R9): a plain vertical wheel notch is left unconsumed, so the
 * track list scrolls; a horizontal swipe, or a vertical notch with Shift (neither platform swaps the axes itself),
 * scrolls time; Ctrl (Command on iPad) zooms around the pointer. Android trackpads deliver a pinch as `Scale` events and
 * a two-finger swipe as `Pan` events, which the touch detectors ignore: they zoom around the pointer and scroll time.
 */
internal fun Modifier.viewportGestures(
    dragScrolls: Boolean,
    onZoomAround: (centreX: Double, factor: Double) -> Unit,
    onScrollBy: (px: Double) -> Unit,
): Modifier = this
    .pointerInput(dragScrolls, onZoomAround, onScrollBy) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var zoomAccumulated = 1f
            var panAccumulated = 0f
            var mode: GestureMode? = null
            while (mode == null) {
                val event = awaitPointerEvent()
                // A clip that consumed the change owns this gesture (its lift and move); the lane stays out of it.
                if (event.changes.any { it.isConsumed }) return@awaitEachGesture
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) return@awaitEachGesture
                zoomAccumulated *= event.calculateZoom()
                panAccumulated += event.calculatePan().x
                val spread = event.calculateCentroidSize()
                mode = when {
                    pressed.size >= 2 && abs(1f - zoomAccumulated) * spread > slop -> GestureMode.Zoom
                    pressed.size >= 2 && abs(panAccumulated) > slop -> GestureMode.Zoom
                    dragScrolls && pressed.size == 1 && abs(panAccumulated) > slop -> GestureMode.Scroll
                    else -> null
                }
            }
            val section = if (mode == GestureMode.Zoom) PerfSection.TIMELINE_ZOOM else PerfSection.TIMELINE_SCROLL
            PerfTrace.animation(section) {
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (event.changes.any { it.isConsumed }) continue
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (mode == GestureMode.Zoom && zoom != 1f) onZoomAround(event.calculateCentroid().x.toDouble(), zoom.toDouble())
                    if (pan.x != 0f) onScrollBy(-pan.x.toDouble())
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
    .pointerInput(onZoomAround, onScrollBy) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Scroll -> onWheel(event, onZoomAround, onScrollBy)
                    PointerEventType.ScaleChange -> {
                        val x = event.changes.first().position.x.toDouble()
                        PerfTrace.animation(PerfSection.TIMELINE_ZOOM) { onZoomAround(x, event.changes.first().scaleFactor.toDouble()) }
                        event.changes.forEach { it.consume() }
                    }
                    PointerEventType.PanMove -> {
                        // The pan offset reads like a wheel delta (positive moves later), opposite to the fingers' path.
                        val dx = event.changes.first().panOffset.x
                        if (dx != 0f) PerfTrace.animation(PerfSection.TIMELINE_SCROLL) { onScrollBy(dx.toDouble()) }
                        event.changes.forEach { it.consume() }
                    }
                    else -> Unit
                }
            }
        }
    }

/** One wheel event: zoom with the zoom modifier; time with Shift or a horizontal delta; a plain vertical notch passes on. */
private fun onWheel(event: PointerEvent, onZoomAround: (Double, Double) -> Unit, onScrollBy: (Double) -> Unit) {
    val deltaX = event.changes.sumOf { it.scrollDelta.x.toDouble() }
    val deltaY = event.changes.sumOf { it.scrollDelta.y.toDouble() }
    if (deltaX == 0.0 && deltaY == 0.0) return
    val x = event.changes.first().position.x.toDouble()
    val modifiers = event.keyboardModifiers
    when {
        modifiers.isCtrlPressed || modifiers.isMetaPressed ->
            PerfTrace.animation(PerfSection.TIMELINE_ZOOM) { onZoomAround(x, WHEEL_ZOOM_BASE.pow(-deltaY)) }
        modifiers.isShiftPressed && deltaY != 0.0 ->
            PerfTrace.animation(PerfSection.TIMELINE_SCROLL) { onScrollBy(deltaY * WHEEL_SCROLL_PX) }
        deltaX != 0.0 ->
            PerfTrace.animation(PerfSection.TIMELINE_SCROLL) { onScrollBy(deltaX * WHEEL_SCROLL_PX) }
        // A plain vertical notch belongs to the track list.
        else -> return
    }
    event.changes.forEach { it.consume() }
}

private enum class GestureMode { Zoom, Scroll }

/** One wheel notch away from the user doubles the zoom over about four notches. */
private const val WHEEL_ZOOM_BASE = 1.19
/** One wheel notch scrolls 48 px. */
private const val WHEEL_SCROLL_PX = 48.0
