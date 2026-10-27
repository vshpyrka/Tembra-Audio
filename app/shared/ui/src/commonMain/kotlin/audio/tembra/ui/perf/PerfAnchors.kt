package audio.tembra.ui.perf

/**
 * The journey anchors (feature 007, spec FR-017): the `testTag` values the benchmark journeys reach on both
 * platforms, UiAutomator through `testTagsAsResourceId` on the Android root ([anchorRoot]) and XCUITest through
 * Compose Multiplatform's accessibility-identifier mapping. The values are the screens' existing stable tags, so
 * the UI tests and the journeys share one name per control; a journey that needs a new control adds its tag here
 * (constitution: every feature instruments itself).
 */
object PerfAnchors {
    /** The start destination's root, where every journey begins: Timeline since Home was removed (2026-10-17). */
    /** The start destination's root: the Timeline's since Home left (feature 009). */
    const val START_ROOT = "screen.timeline"
    const val NAV_TUNER = "nav.item.tuner"
    const val NAV_METRONOME = "nav.item.metronome"
    const val TUNER_ROOT = "screen.tuner"
    const val TUNER_MODE_AUTO_LISTEN = "tuner.mode.autoListen"
    const val TUNER_MODE_BY_EAR = "tuner.mode.byEar"
    const val TUNER_NOTE_READOUT = "tuner.meter"
    const val METRONOME_ROOT = "screen.metronome"
    const val METRONOME_START_STOP = "metronome.play"
    const val METRONOME_TEMPO_UP = "metronome.dial.up"
    const val METRONOME_TEMPO_DOWN = "metronome.dial.down"
    const val METRONOME_TEMPO_READOUT = "metronome.tempoReadout"
    const val DEVTOOLS_WRITE_HEALTH = "devtools.writeHealth"

    // Feature 009 (contracts/perf-instrumentation.md): the Projects library, the Timeline, the transport bar and the
    // stop-and-leave dialog. Waveform readiness is not an anchor: `screen.timeline` carries `stateDescription`
    // "ready" once every visible overview is built ("loading" before).
    const val NAV_PROJECTS = "nav.item.projects"
    const val PROJECTS_ROOT = "screen.projects"
    const val PROJECTS_SEARCH = "projects.search"
    const val PROJECTS_CARD_DEMO = "projects.card.demo"
    const val NAV_TIMELINE = "nav.item.timeline"
    const val TIMELINE_ROOT = "screen.timeline"
    const val TIMELINE_ADD_TRACK = "timeline.toolbar.addTrack"
    const val TIMELINE_SPLIT = "timeline.toolbar.split"
    const val TIMELINE_UNDO = "timeline.toolbar.undo"
    const val TIMELINE_RULER = "timeline.ruler"
    const val TIMELINE_LANE_FIRST = "timeline.lane.0"
    const val TIMELINE_CLIP_FIRST = "timeline.clip.first"
    const val TIMELINE_TRACK_MENU_FIRST = "timeline.track.menu.0"
    const val TIMELINE_TRACK_SETTINGS = "timeline.track.menu.settings"
    const val TIMELINE_SETTINGS_CLOSE = "timeline.settings.close"
    const val TRANSPORT_PLAY = "transport.play"
    const val TRANSPORT_SKIP_START = "transport.skipStart"
    const val TRANSPORT_METRONOME = "transport.metronome"
    const val TRANSPORT_READOUT = "transport.readout"
    const val DIALOG_STOP_AND_LEAVE_CONFIRM = "dialog.stopAndLeave.confirm"

    // Feature 009's design iteration D1 (T146): the phone bar's speaker button and the slider of the pop-up it opens
    // (the wide bar's inline slider carries the same tag), and the overview strip and its window, present unless the
    // device hid the strip (T151).
    const val TRANSPORT_MASTER_BUTTON = "transport.master.button"
    const val TRANSPORT_MASTER = "transport.master"
    const val TIMELINE_OVERVIEW = "timeline.overview"
    const val TIMELINE_OVERVIEW_WINDOW = "timeline.overview.window"

    /** Every anchor, for the parity test and the journey drivers' documentation. */
    val all: List<String> = listOf(
        NAV_TUNER, NAV_METRONOME,
        TUNER_ROOT, TUNER_MODE_AUTO_LISTEN, TUNER_MODE_BY_EAR, TUNER_NOTE_READOUT,
        METRONOME_ROOT, METRONOME_START_STOP, METRONOME_TEMPO_UP, METRONOME_TEMPO_DOWN, METRONOME_TEMPO_READOUT,
        DEVTOOLS_WRITE_HEALTH,
        NAV_PROJECTS, PROJECTS_ROOT, PROJECTS_SEARCH, PROJECTS_CARD_DEMO,
        NAV_TIMELINE, TIMELINE_ROOT, TIMELINE_ADD_TRACK, TIMELINE_SPLIT, TIMELINE_UNDO, TIMELINE_RULER,
        TIMELINE_LANE_FIRST, TIMELINE_CLIP_FIRST, TIMELINE_TRACK_MENU_FIRST, TIMELINE_TRACK_SETTINGS, TIMELINE_SETTINGS_CLOSE,
        TRANSPORT_PLAY, TRANSPORT_SKIP_START, TRANSPORT_METRONOME, TRANSPORT_READOUT,
        DIALOG_STOP_AND_LEAVE_CONFIRM,
        TRANSPORT_MASTER_BUTTON, TRANSPORT_MASTER, TIMELINE_OVERVIEW, TIMELINE_OVERVIEW_WINDOW,
    )
}
