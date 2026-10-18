package audio.tembra.ui.timeline

import audio.tembra.core.project.ClipId
import audio.tembra.core.project.TrackId

/** What is selected on the timeline (spec FR-031): a track (its header), a clip (and the track it sits on), or nothing. */
sealed interface Selection {
    data object None : Selection
    data class TrackSelected(val trackId: TrackId) : Selection
    data class ClipSelected(val trackId: TrackId, val clipId: ClipId) : Selection

    /** The track a selection names, if any. */
    val trackIdOrNull: TrackId?
        get() = when (this) {
            None -> null
            is TrackSelected -> trackId
            is ClipSelected -> trackId
        }
}
