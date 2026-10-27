package audio.tembra.data.timeline

import kotlinx.coroutines.flow.Flow

/**
 * The timeline's per-device choices (spec FR-015, D1): whether the overview strip shows above the transport bar. Kept
 * in the app's one preferences file beside the tuner's and the metronome's, never in a project's `view.json`: the strip
 * is a matter of the device and the hand that holds it, not of the song.
 */
interface TimelinePreferences {
    /** The current value first, then every change; `true` while nothing is stored, as the strip shows by default. */
    val overviewStripShown: Flow<Boolean>

    suspend fun setOverviewStripShown(shown: Boolean)
}
