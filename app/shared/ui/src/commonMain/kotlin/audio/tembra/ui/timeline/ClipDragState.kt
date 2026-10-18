package audio.tembra.ui.timeline

import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.TrackId

/** The ViewModel's record of a clip move in progress: where it started and how far the finger has gone, in beats and rows. */
internal data class ClipDragState(
    val clipId: ClipId,
    val originTrack: TrackId,
    val originStart: Beats,
    val deltaBeats: Double = 0.0,
    val deltaTracks: Int = 0,
)
