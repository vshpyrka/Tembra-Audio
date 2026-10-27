package audio.tembra.core.timeline

import audio.tembra.core.project.Beats
import audio.tembra.core.project.ZoomLevel
import kotlin.math.max

/**
 * The pure mapping between beats and horizontal device pixels (data-model.md section 2; spec FR-015, FR-016):
 * [pixelsPerBeat] is the zoom at 1x density, [densityScale] turns it into device pixels, [scrollBeats] is the beat at
 * the left edge. Zoom is clamped to `[fitPixelsPerBeat, closestPixelsPerBeat]`: at the widest the whole project and at
 * least 32 bars fit the width, at the closest one beat spans the width. A viewport not yet measured (`widthPx ≤ 0`)
 * has no bounds. Value semantics: every operation returns a new viewport.
 */
data class TimelineViewport(
    val pixelsPerBeat: Double,
    val scrollBeats: Beats,
    val widthPx: Double,
    val densityScale: Double,
    val projectLengthBeats: Beats = Beats.Zero,
    val beatsPerBar: Int = 4,
) {
    val zoom: ZoomLevel get() = ZoomLevel(pixelsPerBeat)

    val devicePixelsPerBeat: Double get() = pixelsPerBeat * densityScale

    /** The widest zoom: the whole project, and never fewer than 32 bars, across the width. */
    val fitPixelsPerBeat: Double get() = widthPx / (max(projectLengthBeats.value, MIN_FIT_BARS * beatsPerBar) * densityScale)

    /** The closest zoom: one beat across the width. */
    val closestPixelsPerBeat: Double get() = widthPx / densityScale

    val visibleBeats: Double get() = widthPx / devicePixelsPerBeat

    /** The beat at the right edge. */
    val endBeats: Beats get() = Beats(scrollBeats.value + visibleBeats)

    fun xOf(beats: Beats): Double = (beats.value - scrollBeats.value) * devicePixelsPerBeat

    fun beatsAt(x: Double): Beats = Beats(scrollBeats.value + x / devicePixelsPerBeat)

    /** Multiplies the zoom by [factor] keeping the beat under [centreX] in place (pinch, wheel, trackpad). */
    fun zoomAround(centreX: Double, factor: Double): TimelineViewport {
        require(factor > 0.0) { "Zoom factor must be positive, was $factor" }
        val anchor = beatsAt(centreX)
        val nextPixelsPerBeat = clampZoom(pixelsPerBeat * factor)
        val nextScroll = anchor.value - centreX / (nextPixelsPerBeat * densityScale)
        return copy(pixelsPerBeat = nextPixelsPerBeat, scrollBeats = Beats(nextScroll).coerceAtLeastZero())
    }

    /** The zoom [level] clamped to the bounds; the scroll is kept. */
    fun withZoom(level: ZoomLevel): TimelineViewport = copy(pixelsPerBeat = clampZoom(level.pixelsPerBeat))

    /** "Fit project": the widest zoom from the start. */
    fun fit(): TimelineViewport = copy(pixelsPerBeat = clampZoom(if (widthPx > 0.0) fitPixelsPerBeat else pixelsPerBeat), scrollBeats = Beats.Zero)

    fun scrollBy(px: Double): TimelineViewport = scrollTo(Beats(scrollBeats.value + px / devicePixelsPerBeat))

    fun scrollTo(beats: Beats): TimelineViewport = copy(scrollBeats = beats.coerceAtLeastZero())

    private fun clampZoom(value: Double): Double = if (widthPx <= 0.0) value else value.coerceIn(fitPixelsPerBeat, closestPixelsPerBeat)

    companion object {
        /** At the widest zoom at least this many bars fit the window (spec FR-015). */
        const val MIN_FIT_BARS = 32.0

        /** The widest viewport for a project, at scroll zero. */
        fun fitted(widthPx: Double, densityScale: Double, projectLengthBeats: Beats, beatsPerBar: Int): TimelineViewport =
            TimelineViewport(pixelsPerBeat = 1.0, scrollBeats = Beats.Zero, widthPx = widthPx, densityScale = densityScale, projectLengthBeats = projectLengthBeats, beatsPerBar = beatsPerBar).fit()
    }
}
