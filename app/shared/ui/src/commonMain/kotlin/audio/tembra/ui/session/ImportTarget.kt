package audio.tembra.ui.session

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId

/** Where imported audio lands (spec FR-043): on one track at [atBeats], or as one new track per file. */
sealed interface ImportTarget {
    val atBeats: Beats

    data class Track(val trackId: TrackId, override val atBeats: Beats) : ImportTarget
    data class NewTracks(override val atBeats: Beats) : ImportTarget
}
