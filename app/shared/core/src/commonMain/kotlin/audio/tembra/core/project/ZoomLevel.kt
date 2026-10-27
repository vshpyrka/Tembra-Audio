package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A timeline zoom level: pixels per beat at 1x density (spec FR-015). Remembered per project on the device
 * (`ProjectViewState`, where null means "fit"), never in the document.
 */
@JvmInline
@Serializable
value class ZoomLevel(val pixelsPerBeat: Double) {
    init {
        require(pixelsPerBeat > 0.0 && pixelsPerBeat.isFinite()) { "Zoom must be a positive number of pixels per beat, was $pixelsPerBeat" }
    }
}
