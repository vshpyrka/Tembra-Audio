package audio.tembra.core.timeline

import audio.tembra.core.project.TrackId

/** One track lane as laid out: its vertical extent in device pixels (top inclusive, bottom exclusive) and its clips. */
data class LaneGeometry(val trackId: TrackId, val top: Double, val bottom: Double, val clips: List<ClipGeometry>)
