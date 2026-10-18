package audio.tembra.core.timeline

import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId

/** A clip's span in beats for hit testing (start inclusive, end exclusive). */
data class ClipGeometry(val clipId: ClipId, val startBeats: Beats, val endBeats: Beats)
