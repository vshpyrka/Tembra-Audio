package audio.tembra.ui.timeline

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.TrackId

/**
 * A clip being moved (spec FR-033; clarification 3): the lane it came from draws it ghosted, the lane it hovers
 * ([toTrack]) draws the lifted copy at the snapped [startBeats] and highlights itself; the edit happens on release.
 */
@Immutable
data class ClipDragUi(
    val clipId: ClipId,
    val fromTrack: TrackId,
    val toTrack: TrackId,
    val startBeats: Beats,
    val lengthBeats: Beats,
    val name: String,
)
