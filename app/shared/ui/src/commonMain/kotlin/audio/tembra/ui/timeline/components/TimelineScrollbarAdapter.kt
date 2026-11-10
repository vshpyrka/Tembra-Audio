package audio.tembra.ui.timeline.components

import audio.tembra.core.project.Beats
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.designsystem.ScrollbarAdapter
import kotlin.math.max

/**
 * The timeline's time axis for a horizontal scrollbar (feature 011, spec FR-014a): the [viewport]'s left-edge beat is
 * the scroll, measured from the rest position, the lead-in before the project's [length] (or the view's end, when
 * scrolled past it) the content, the visible width the viewport, which begins past the [headersPx] the expanded layout
 * pins at the list's start; a thumb drag scrolls through [onScrollBy], as a wheel does.
 */
internal data class TimelineScrollbarAdapter(
    val viewport: TimelineViewport,
    val length: Beats,
    val headersPx: Float = 0f,
    val onScrollBy: (Float) -> Unit,
) : ScrollbarAdapter {
    override val leadingPx: Float get() = headersPx

    override val scrollOffset: Float get() = ((viewport.scrollBeats.value - viewport.startScrollBeats.value) * viewport.devicePixelsPerBeat).toFloat()
    override val contentSize: Float
        get() = ((max(length.value, viewport.endBeats.value) - viewport.startScrollBeats.value) * viewport.devicePixelsPerBeat).toFloat()
    override val viewportSize: Float get() = viewport.widthPx.toFloat()

    override fun scrollBy(delta: Float) = onScrollBy(delta)
}
