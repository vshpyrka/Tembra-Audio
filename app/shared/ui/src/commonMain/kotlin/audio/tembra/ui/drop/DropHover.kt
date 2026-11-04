package audio.tembra.ui.drop

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId

/** What a drop would do where files hover now (feature 011, spec FR-026): shown while hovering, cleared on exit or end. */
data class DropHover(val zone: DropZone, val preview: DropPreview)

/**
 * A [DropPlan] before the items are read: platforms name the dragged types before the drop, and iOS their count; the
 * drop itself resolves the plan from what it reads ([DropPlanner.plan]).
 */
sealed interface DropPreview {
    data class AddToTrack(val trackId: TrackId, val atBeats: Beats) : DropPreview

    data class NewTracks(val atBeats: Beats) : DropPreview

    data object NewProject : DropPreview
}
