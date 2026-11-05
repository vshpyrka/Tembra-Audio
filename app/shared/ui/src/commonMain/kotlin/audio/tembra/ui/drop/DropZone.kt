package audio.tembra.ui.drop

import audio.tembra.core.project.TrackId

/** Where files hover or land (feature 011, spec FR-026; data-model.md "Drops"). */
sealed interface DropZone {
    /** A track's lane: one audio file joins the track. */
    data class TrackLane(val trackId: TrackId) : DropZone

    /** The timeline below and between the tracks: the files become new tracks. */
    data object TimelineSpace : DropZone

    /** The Projects grid: the files become a new project. */
    data object ProjectsGrid : DropZone
}
