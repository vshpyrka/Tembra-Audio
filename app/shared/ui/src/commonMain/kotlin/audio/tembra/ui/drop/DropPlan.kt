package audio.tembra.ui.drop

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import audio.tembra.data.projects.PickedFile

/** What a drop does (feature 011, spec FR-026; resolved by [DropPlanner]). */
sealed interface DropPlan {
    /** One file joins [trackId] at [atBeats]. */
    data class AddToTrack(val trackId: TrackId, val atBeats: Beats, val file: PickedFile) : DropPlan

    /** One new track per file, each starting at [atBeats]. */
    data class NewTracks(val files: List<PickedFile>, val atBeats: Beats) : DropPlan

    /** A new project with one track per file. */
    data class NewProject(val files: List<PickedFile>) : DropPlan

    /** No audio file among the items. */
    data object Nothing : DropPlan
}
