package audio.tembra.ui.timeline.components

import audio.tembra.core.timeline.TimelineViewport
import kotlin.math.roundToInt

/** The zoom relative to the widest level (spec FR-015): 1× at fit, larger closer in; 1× while the lanes are unmeasured. */
internal val TimelineViewport.zoomFactor: Double
    get() = if (widthPx <= 0.0) 1.0 else (pixelsPerBeat / fitPixelsPerBeat).coerceAtLeast(1.0)

/** The level the zoom buttons show: "1×", "2.5×", "16×", one decimal under ten and whole above. */
internal fun zoomLevelLabel(factor: Double): String {
    val rounded = if (factor < 10.0) (factor * 10).roundToInt() / 10.0 else factor.roundToInt().toDouble()
    val text = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    return "$text×"
}

/** [zoomLevelLabel] of the viewport's [zoomFactor]. */
internal fun zoomLevelLabel(viewport: TimelineViewport): String = zoomLevelLabel(viewport.zoomFactor)
