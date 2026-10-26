package audio.tembra.ui.timeline

import audio.tembra.core.project.GridSize
import audio.tembra.core.project.ZoomControl
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
    /** The tempo pill (spec FR-036, D1): in the toolbar at every width, opens the tempo dialog. */
    const val TOOLBAR_TEMPO = "timeline.toolbar.tempo"
    const val METRONOME_SETTINGS = "timeline.toolbar.metronomeSettings"

    /** The overflow menu; its button [OVERFLOW_BUTTON] and its rows `timeline.toolbar.overflow.<id>`. */
    const val OVERFLOW = "timeline.toolbar.overflow"
    const val OVERFLOW_BUTTON = "$OVERFLOW.button"
    const val OVERFLOW_ADD_AUDIO = "$OVERFLOW.addAudio"
    const val OVERFLOW_ADD_AUDIO_NEW_TRACK = "$OVERFLOW.addAudioNewTrack"
    /** "Snap: 1/16" on compact and landscape windows: opens the snap sheet (spec FR-018, FR-055). */
    const val OVERFLOW_SNAP = "$OVERFLOW.snap"
    const val OVERFLOW_METRONOME_SETTINGS = "$OVERFLOW.metronomeSettings"
    /** The "Zoom control" choices at every width (spec FR-015, D1): `timeline.toolbar.overflow.zoomControl.<id>`. */
    fun overflowZoomControl(control: ZoomControl): String = "$OVERFLOW.zoomControl.${control.id}"

    /** The full toolbar's "Snap 1/16" pill (spec FR-018). */
    const val GRID = "timeline.toolbar.grid"
    // The two always-visible zoom controls (spec FR-015, D1): the ruler's buttons and the overview strip.
    const val ZOOM_OUT = "timeline.zoom.out"
    const val ZOOM_LEVEL = "timeline.zoom.level"
    const val ZOOM_IN = "timeline.zoom.in"
    const val OVERVIEW = PerfAnchors.TIMELINE_OVERVIEW
    const val OVERVIEW_WINDOW = PerfAnchors.TIMELINE_OVERVIEW_WINDOW
    const val RULER = PerfAnchors.TIMELINE_RULER
    const val TRACK_LIST = "timeline.tracks"
    const val PLAYHEAD = "timeline.playhead"
    const val EMPTY = "timeline.empty"

    /** The snap sheet; its rows `timeline.snapSheet.<grid, lowercase>` (see [snapSheetRow]), the current row's check `<row>.check`. */
    const val SNAP_SHEET = "timeline.snapSheet"
    fun snapSheetRow(grid: GridSize): String = "$SNAP_SHEET.${grid.name.lowercase()}"
    fun snapSheetCheck(grid: GridSize): String = "${snapSheetRow(grid)}.check"

    /** The metronome sheet's signature menu (spec FR-037, FR-039, D1); its rows `<tag>.<signature id>`. */
    const val METRONOME_SHEET_SIGNATURE = "timeline.metronomeSheet.signature"

    fun lane(index: Int): String = "timeline.lane.$index"
    fun header(index: Int): String = "timeline.header.$index"
    fun mute(index: Int): String = "timeline.header.$index.mute"
    fun solo(index: Int): String = "timeline.header.$index.solo"
    fun gain(index: Int): String = "timeline.header.$index.gain"
    fun chevron(index: Int): String = "timeline.header.$index.chevron"
    /** The button that opens a track's menu; the open menu is [TRACK_MENU] and its rows `timeline.track.menu.<id>`. */
    fun trackMenu(index: Int): String = "timeline.track.menu.$index"
    const val TRACK_MENU = "timeline.track.menu"
    fun clip(index: Int): String = if (index == 0) PerfAnchors.TIMELINE_CLIP_FIRST else "timeline.clip.$index"
}
