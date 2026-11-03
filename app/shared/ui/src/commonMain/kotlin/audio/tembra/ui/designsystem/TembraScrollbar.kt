package audio.tembra.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.unit.IntSize
import audio.tembra.ui.theme.LocalDawColors
import audio.tembra.ui.theme.LocalTembraSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether the scrollbar of a [tembraScrollbar] element is showing; for tests (feature 011, FR-014a). */
internal val ScrollbarShown = SemanticsPropertyKey<Boolean>("ScrollbarShown")

/**
 * The design system's scrollbar on a scrolling element (feature 011, spec FR-014a, research R18): place it before the
 * element's own scroll modifier (or on a lazy list or grid). It draws a thumb along the element's [orientation] edge
 * while the content scrolls from a mouse wheel or a trackpad, fades out [HIDE_AFTER_MS] after the scrolling stops, and
 * can be dragged with the mouse; touch scrolling never shows it. Compose Multiplatform's own scrollbar exists only on
 * its Skia targets, not on Android, hence this one, drawn from the design tokens on both platforms.
 */
internal fun Modifier.tembraScrollbar(adapter: ScrollbarAdapter, orientation: Orientation): Modifier =
    this then ScrollbarElement(adapter, orientation)

private data class ScrollbarElement(val adapter: ScrollbarAdapter, val orientation: Orientation) : ModifierNodeElement<ScrollbarNode>() {
    override fun create() = ScrollbarNode(adapter, orientation)

    override fun update(node: ScrollbarNode) {
        node.adapter = adapter
        node.orientation = orientation
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tembraScrollbar"
        properties["orientation"] = orientation
    }
}

private class ScrollbarNode(var adapter: ScrollbarAdapter, var orientation: Orientation) :
    Modifier.Node(),
    DrawModifierNode,
    PointerInputModifierNode,
    SemanticsModifierNode,
    CompositionLocalConsumerModifierNode {

    private val alpha = Animatable(0f)
    private var shown = false
    private var hideJob: Job? = null
    private var dragging = false
    private var lastDrag = Offset.Zero
    private var boundsSize = IntSize.Zero

    override fun ContentDrawScope.draw() {
        drawContent()
        boundsSize = IntSize(size.width.toInt(), size.height.toInt())
        if (alpha.value <= 0f) return
        val thumb = thumbRect() ?: return
        val radius = thumb.minDimension / 2
        drawRoundRect(
            color = currentValueOf(LocalDawColors).surfaceLevel4,
            topLeft = thumb.topLeft,
            size = thumb.size,
            cornerRadius = CornerRadius(radius),
            alpha = alpha.value,
        )
    }

    /** The thumb along the trailing edge, or null when everything fits. */
    private fun thumbRect(): Rect? {
        val spacing = currentValueOf(LocalTembraSpacing)
        val content = adapter.contentSize
        val viewport = adapter.viewportSize
        if (content <= viewport || viewport <= 0f) return null
        val thickness = spacing.scrollbarThickness.toPx()
        val inset = spacing.scrollbarInset.toPx()
        val track = viewport - 2 * inset
        val length = (track * viewport / content).coerceAtLeast(spacing.scrollbarMinThumb.toPx()).coerceAtMost(track)
        val start = inset + (track - length) * (adapter.scrollOffset / (content - viewport)).coerceIn(0f, 1f)
        return when (orientation) {
            Orientation.Vertical -> Rect(Offset(boundsSize.width - inset - thickness, start), Size(thickness, length))
            Orientation.Horizontal -> Rect(Offset(start, boundsSize.height - inset - thickness), Size(length, thickness))
        }
    }

    private fun androidx.compose.ui.unit.Dp.toPx(): Float = value * currentValueOf(androidx.compose.ui.platform.LocalDensity).density

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        boundsSize = bounds
        when (pass) {
            PointerEventPass.Initial -> {
                val type = pointerEvent.type
                if (type == PointerEventType.Scroll || type == PointerEventType.PanStart || type == PointerEventType.PanMove) show()
            }
            PointerEventPass.Main -> dragThumb(pointerEvent)
            else -> Unit
        }
    }

    private fun dragThumb(event: PointerEvent) {
        val change = event.changes.firstOrNull() ?: return
        when {
            event.type == PointerEventType.Press && shown && event.buttons.isPrimaryPressed -> {
                val thumb = thumbRect() ?: return
                // A little slack around the thin thumb: it is meant to be grabbed with a mouse.
                val slack = currentValueOf(LocalTembraSpacing).scrollbarThickness.toPx()
                if (!thumb.inflate(slack).contains(change.position)) return
                dragging = true
                lastDrag = change.position
                hideJob?.cancel()
                change.consume()
            }
            dragging && event.type == PointerEventType.Move -> {
                val delta = change.position - lastDrag
                lastDrag = change.position
                val thumb = thumbRect() ?: return
                val track = adapter.viewportSize - thumb.let { if (orientation == Orientation.Vertical) it.height else it.width }
                val along = if (orientation == Orientation.Vertical) delta.y else delta.x
                if (track > 0f) adapter.scrollBy(along * (adapter.contentSize - adapter.viewportSize) / track)
                invalidateDraw()
                change.consume()
            }
            dragging && event.type == PointerEventType.Release -> {
                dragging = false
                change.consume()
                scheduleHide()
            }
        }
    }

    override fun onCancelPointerInput() {
        dragging = false
    }

    private fun show() {
        if (!shown) {
            shown = true
            invalidateSemantics()
        }
        coroutineScope.launch { alpha.snapTo(1f) }
        invalidateDraw()
        scheduleHide()
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(HIDE_AFTER_MS)
            if (dragging) return@launch
            alpha.animateTo(0f, tween(FADE_MS)) { invalidateDraw() }
            shown = false
            invalidateSemantics()
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        this[ScrollbarShown] = shown
    }

    private companion object {
        const val HIDE_AFTER_MS = 1_000L
        const val FADE_MS = 250
    }
}
