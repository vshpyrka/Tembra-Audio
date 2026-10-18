package audio.tembra.ui.timeline

import audio.tembra.ui.perf.PerfAnchors

/** Every `testTag` of the timeline's controls (contracts/timeline-screen.md "Semantics"); the journey anchors come from [PerfAnchors]. */
object TimelineTestTags {
    const val ROOT = PerfAnchors.TIMELINE_ROOT

    /** A 1 dp marker present once every visible overview is ready (the Android journey waits on it). */
    const val READY_MARKER = "screen.timeline.ready"
    const val TOOLBAR = "timeline.toolbar"
    const val ADD_TRACK = PerfAnchors.TIMELINE_ADD_TRACK
    const val ADD_AUDIO = "timeline.toolbar.addAudio"
    const val ADD_AUDIO_NEW_TRACK = "timeline.toolbar.addAudioNewTrack"
    const val SPLIT = PerfAnchors.TIMELINE_SPLIT
    const val UNDO = PerfAnchors.TIMELINE_UNDO
    const val REDO = "timeline.toolbar.redo"
    const val OVERFLOW = "timeline.toolbar.overflow"
    const val GRID = "timeline.toolbar.grid"
    const val ZOOM = "timeline.toolbar.zoom"
    const val RULER = PerfAnchors.TIMELINE_RULER
    const val TEMPO_PILL = "timeline.ruler.tempo"
    const val SIGNATURE_PILL = "timeline.ruler.signature"
    const val TRACK_LIST = "timeline.tracks"
    const val PLAYHEAD = "timeline.playhead"
    const val EMPTY = "timeline.empty"

    fun lane(index: Int): String = "timeline.lane.$index"
    fun header(index: Int): String = "timeline.header.$index"
    fun mute(index: Int): String = "timeline.header.$index.mute"
    fun solo(index: Int): String = "timeline.header.$index.solo"
    fun gain(index: Int): String = "timeline.header.$index.gain"
    fun chevron(index: Int): String = "timeline.header.$index.chevron"
    fun trackMenu(index: Int): String = "timeline.track.menu.$index"
    fun clip(index: Int): String = if (index == 0) PerfAnchors.TIMELINE_CLIP_FIRST else "timeline.clip.$index"
}
