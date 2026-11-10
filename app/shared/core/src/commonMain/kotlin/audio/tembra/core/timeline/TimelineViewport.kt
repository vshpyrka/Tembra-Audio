package audio.tembra.core.timeline

import audio.tembra.core.project.Beats
import audio.tembra.core.project.ZoomLevel
import kotlin.math.max

/**
 * The pure mapping between beats and horizontal device pixels (data-model.md section 2; spec FR-015, FR-016):
 * [pixelsPerBeat] is the zoom at 1x density, [densityScale] turns it into device pixels, [scrollBeats] is the beat at
 * the left edge. Zoom is clamped to `[fitPixelsPerBeat, closestPixelsPerBeat]`: at the widest the whole project and at
 * least 32 bars fit the width after the lead-in, at the closest one beat spans the width. A viewport not yet measured
 * (`widthPx ≤ 0`) has no bounds. Value semantics: every operation returns a new viewport.
 *
 * [leadInPx] is empty timeline before the project's start, in device pixels. At rest the left edge is that far before
 * beat zero ([startScrollBeats], a negative beat), so 0:00 sits clear of the window's edge, where a finger can reach it
 * and a back swipe does not take it; the lead-in is part of the scroll, so it leaves with the clips as the view moves on.
 */
data class TimelineViewport(
    val pixelsPerBeat: Double,
    val scrollBeats: Beats,
    val widthPx: Double,
    val densityScale: Double,
    val projectLengthBeats: Beats = Beats.Zero,
    val beatsPerBar: Int = 4,
    val leadInPx: Double = 0.0,
) {
    val zoom: ZoomLevel get() = ZoomLevel(pixelsPerBeat)

    val devicePixelsPerBeat: Double get() = pixelsPerBeat * densityScale

    /** The width the fitted project spreads over: what the lead-in leaves. */
    private val contentWidthPx: Double get() = if (leadInPx < widthPx) widthPx - leadInPx else widthPx

    /** The widest zoom: the whole project, and never fewer than 32 bars, across the width after the lead-in. */
    val fitPixelsPerBeat: Double get() = contentWidthPx / (max(projectLengthBeats.value, MIN_FIT_BARS * beatsPerBar) * densityScale)

    /** The closest zoom: one beat across the width. */
    val closestPixelsPerBeat: Double get() = widthPx / densityScale

    val visibleBeats: Double get() = widthPx / devicePixelsPerBeat

    /** The beat at the right edge. */
    val endBeats: Beats get() = Beats(scrollBeats.value + visibleBeats)

    /** The scroll at rest and the earliest there is: the left edge the lead-in before beat zero. */
    val startScrollBeats: Beats get() = if (leadInPx > 0.0) Beats(-leadInPx / devicePixelsPerBeat) else Beats.Zero

    private val isAtStart: Boolean get() = scrollBeats.value <= startScrollBeats.value + AT_START_TOLERANCE

    fun xOf(beats: Beats): Double = (beats.value - scrollBeats.value) * devicePixelsPerBeat

    /** The beat under [x]: negative inside the lead-in, so callers that place something hold it at zero. */
    fun beatsAt(x: Double): Beats = Beats(scrollBeats.value + x / devicePixelsPerBeat)

    /**
     * Multiplies the zoom by [factor] keeping the beat under [centreX] in place (pinch, wheel, trackpad). Over the empty
     * lead-in there is no beat to hold still, so a view resting at the start stays there.
     */
    fun zoomAround(centreX: Double, factor: Double): TimelineViewport {
        require(factor > 0.0) { "Zoom factor must be positive, was $factor" }
        val anchor = beatsAt(centreX)
        val nextPixelsPerBeat = clampZoom(pixelsPerBeat * factor)
        val zoomed = copy(pixelsPerBeat = nextPixelsPerBeat)
        if (isAtStart && anchor.value < 0.0) return zoomed.scrollToStart()
        return zoomed.scrollTo(Beats(anchor.value - centreX / (nextPixelsPerBeat * densityScale)))
    }

    /** The zoom [level] clamped to the bounds; the scroll is kept, and a view resting at the start stays there. */
    fun withZoom(level: ZoomLevel): TimelineViewport {
        val zoomed = copy(pixelsPerBeat = clampZoom(level.pixelsPerBeat))
        return if (isAtStart) zoomed.scrollToStart() else zoomed.scrollTo(scrollBeats)
    }

    /**
     * The span from [start] to [end] across the lanes, the zoom clamped to the bounds and [start] at the left edge. A span
     * from the project's start fills what the lead-in leaves and rests at the start; an empty span changes nothing.
     */
    fun zoomToSpan(start: Beats, end: Beats): TimelineViewport {
        if (widthPx <= 0.0 || end <= start) return this
        if (start.value > 0.0) return withZoom(ZoomLevel(widthPx / ((end.value - start.value) * densityScale))).scrollTo(start)
        if (end.value <= 0.0) return this
        return copy(pixelsPerBeat = clampZoom(contentWidthPx / (end.value * densityScale))).scrollToStart()
    }

    /** "Fit project": the widest zoom from the start. */
    fun fit(): TimelineViewport = copy(pixelsPerBeat = clampZoom(if (widthPx > 0.0) fitPixelsPerBeat else pixelsPerBeat)).scrollToStart()

    /** The viewport at a new size: a view resting at the start stays there, any other keeps its left-edge beat; the zoom is kept within the new bounds. */
    fun remeasured(widthPx: Double, densityScale: Double, leadInPx: Double): TimelineViewport {
        val measured = copy(widthPx = widthPx, densityScale = densityScale, leadInPx = leadInPx)
        val zoomed = measured.copy(pixelsPerBeat = measured.clampZoom(pixelsPerBeat))
        return if (isAtStart) zoomed.scrollToStart() else zoomed.scrollTo(scrollBeats)
    }

    fun scrollBy(px: Double): TimelineViewport = scrollTo(Beats(scrollBeats.value + px / devicePixelsPerBeat))

    fun scrollTo(beats: Beats): TimelineViewport = copy(scrollBeats = if (beats < startScrollBeats) startScrollBeats else beats)

    /** Back to the rest position, with the lead-in showing. */
    fun scrollToStart(): TimelineViewport = copy(scrollBeats = startScrollBeats)

    private fun clampZoom(value: Double): Double = if (widthPx <= 0.0) value else value.coerceIn(fitPixelsPerBeat, closestPixelsPerBeat)

    companion object {
        /** At the widest zoom at least this many bars fit the window (spec FR-015). */
        const val MIN_FIT_BARS = 32.0

        /** Rounding noise in beats that must not take a view resting at the start for a scrolled one. */
        private const val AT_START_TOLERANCE = 1e-9

        /** The widest viewport for a project, at scroll zero. */
        fun fitted(widthPx: Double, densityScale: Double, projectLengthBeats: Beats, beatsPerBar: Int): TimelineViewport =
            TimelineViewport(pixelsPerBeat = 1.0, scrollBeats = Beats.Zero, widthPx = widthPx, densityScale = densityScale, projectLengthBeats = projectLengthBeats, beatsPerBar = beatsPerBar).fit()
    }
}
