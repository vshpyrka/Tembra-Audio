package audio.tembra.core.timeline

import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.TrackId
import kotlin.math.abs

/**
 * What a tap or press at (x, y) lands on (spec FR-009, FR-011, FR-031): the playhead marker first (it is drawn on
 * top), then a clip, then empty lane space with the beat under the finger, else nothing.
 */
sealed interface ClipHit {
    data class OnClip(val trackId: TrackId, val clipId: ClipId) : ClipHit

    data class OnLane(val trackId: TrackId, val beats: Beats) : ClipHit

    data object OnMarker : ClipHit

    data object Outside : ClipHit

    companion object {
        fun at(x: Double, y: Double, viewport: TimelineViewport, lanes: List<LaneGeometry>, marker: MarkerGeometry?): ClipHit {
            if (marker != null && y >= marker.top && y <= marker.bottom && abs(x - marker.x) <= marker.halfWidth) return OnMarker
            val lane = lanes.firstOrNull { y >= it.top && y < it.bottom } ?: return Outside
            val beats = viewport.beatsAt(x)
            val clip = lane.clips.firstOrNull { beats >= it.startBeats && beats < it.endBeats }
            return if (clip != null) OnClip(lane.trackId, clip.clipId) else OnLane(lane.trackId, beats)
        }
    }
}
