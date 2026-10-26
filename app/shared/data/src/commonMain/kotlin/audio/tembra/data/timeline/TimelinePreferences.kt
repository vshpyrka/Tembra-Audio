package audio.tembra.data.timeline

import audio.tembra.core.project.ZoomControl
import kotlinx.coroutines.flow.Flow

/**
 * The timeline's per-device choices (spec FR-015, D1): which always-visible zoom control the timeline shows. Kept in
 * the app's one preferences file beside the tuner's and the metronome's, never in a project's `view.json`.
 */
interface TimelinePreferences {
    /** The current value first, then every change; [ZoomControl.Default] while nothing is stored. */
    val zoomControl: Flow<ZoomControl>

    suspend fun setZoomControl(control: ZoomControl)
}
