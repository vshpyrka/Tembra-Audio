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

    /** The stress journey's 24-track project (feature 012): `projects.card.<StressProject.ID>`, built on request by a debug or measurement build. */
    const val PROJECTS_CARD_STRESS = "projects.card.stressTracks"

    /** The library's card grid: the Android journeys scroll it when the card they open is off screen (2026-11-13). */
    const val PROJECTS_GRID = "projects.grid"
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

    // Feature 010 (timeline recording, contracts/perf-instrumentation.md): the arm toggle, the meter and the clip latch, Record
    // in the bar and in the armed compact header, the growing take, the monitor toggle and its overflow entry, the count-in
    // setting, the denied snackbar's settings action and the stop-recording dialog's confirm action.
    const val TIMELINE_TRACK_ARM_FIRST = "timeline.track.arm.0"
    const val TIMELINE_TRACK_METER_FIRST = "timeline.track.meter.0"
    const val TIMELINE_TRACK_RECORD_FIRST = "timeline.track.record.0"
    const val TIMELINE_TRACK_CLIP_FIRST = "timeline.track.clip.0"
    const val TRANSPORT_RECORD = "transport.record"
    const val TIMELINE_MONITOR = "timeline.toolbar.monitor"
    const val TIMELINE_OVERFLOW_MONITOR = "timeline.toolbar.overflow.monitor"
    const val METRONOME_COUNT_IN = "metronome.sheet.countIn"
    const val TIMELINE_CLIP_TAKE = "timeline.clip.take"
    const val SNACKBAR_MICROPHONE_SETTINGS = "snackbar.microphone.settings"
    const val DIALOG_STOP_RECORDING_CONFIRM = "dialog.stopRecording.confirm"
    // Feature 011 (contracts/perf-instrumentation.md, journey `windowing`): the first clip's menu a right-click opens,
    // and the shortcut sheet. The Metronome's start, the first clip and the library's search are anchors already.
    const val TIMELINE_CLIP_MENU_FIRST = "timeline.clip.first.menu"
    const val SHORTCUT_SHEET = "shortcuts.sheet"
    // Feature 012 (the mixer, contracts/perf-instrumentation.md, journey `mixer`): the toolbar's Mixer button and its
    // overflow twin, the sheet, its handle and strip row, the first strip's controls (the journey addresses strip i by
    // replacing the trailing index), the master strip's fader, meter and clip latch, the window button and the pan slider.
    const val TIMELINE_MIXER = "timeline.toolbar.mixer"
    const val TIMELINE_OVERFLOW_MIXER = "timeline.toolbar.overflow.mixer"
    const val MIXER_SHEET = "mixer.sheet"
    const val MIXER_HANDLE = "mixer.handle"
    const val MIXER_STRIPS = "mixer.strips"
    const val MIXER_STRIP_FADER_FIRST = "mixer.strip.fader.0"
    const val MIXER_STRIP_PAN_FIRST = "mixer.strip.pan.0"
    const val MIXER_STRIP_SOLO_FIRST = "mixer.strip.solo.0"
    const val MIXER_STRIP_MUTE_FIRST = "mixer.strip.mute.0"
    const val MIXER_STRIP_METER_FIRST = "mixer.strip.meter.0"
    const val MIXER_STRIP_CLIP_FIRST = "mixer.strip.clip.0"
    const val MIXER_STRIP_NAME_FIRST = "mixer.strip.name.0"
    const val MIXER_MASTER_FADER = "mixer.master.fader"
    const val MIXER_MASTER_METER = "mixer.master.meter"
    const val MIXER_MASTER_CLIP = "mixer.master.clip"
    const val MIXER_WINDOW_OPEN = "mixer.window.open"
    const val TRACK_SETTINGS_PAN = "trackSettings.pan"

    /** Every anchor, for the parity test and the journey drivers' documentation. */
    val all: List<String> = listOf(
        NAV_TUNER, NAV_METRONOME,
        TUNER_ROOT, TUNER_MODE_AUTO_LISTEN, TUNER_MODE_BY_EAR, TUNER_NOTE_READOUT,
        METRONOME_ROOT, METRONOME_START_STOP, METRONOME_TEMPO_UP, METRONOME_TEMPO_DOWN, METRONOME_TEMPO_READOUT,
        DEVTOOLS_WRITE_HEALTH,
        NAV_PROJECTS, PROJECTS_ROOT, PROJECTS_SEARCH, PROJECTS_CARD_DEMO, PROJECTS_CARD_STRESS, PROJECTS_GRID,
        NAV_TIMELINE, TIMELINE_ROOT, TIMELINE_ADD_TRACK, TIMELINE_SPLIT, TIMELINE_UNDO, TIMELINE_RULER,
        TIMELINE_LANE_FIRST, TIMELINE_CLIP_FIRST, TIMELINE_TRACK_MENU_FIRST, TIMELINE_TRACK_SETTINGS, TIMELINE_SETTINGS_CLOSE,
        TRANSPORT_PLAY, TRANSPORT_SKIP_START, TRANSPORT_METRONOME, TRANSPORT_READOUT,
        DIALOG_STOP_AND_LEAVE_CONFIRM,
        TRANSPORT_MASTER_BUTTON, TRANSPORT_MASTER, TIMELINE_OVERVIEW, TIMELINE_OVERVIEW_WINDOW,
        TIMELINE_TRACK_ARM_FIRST, TIMELINE_TRACK_METER_FIRST, TIMELINE_TRACK_RECORD_FIRST, TIMELINE_TRACK_CLIP_FIRST,
        TRANSPORT_RECORD, TIMELINE_MONITOR, TIMELINE_OVERFLOW_MONITOR, METRONOME_COUNT_IN, TIMELINE_CLIP_TAKE,
        SNACKBAR_MICROPHONE_SETTINGS, DIALOG_STOP_RECORDING_CONFIRM,
        TIMELINE_CLIP_MENU_FIRST, SHORTCUT_SHEET,
        TIMELINE_MIXER, TIMELINE_OVERFLOW_MIXER, MIXER_SHEET, MIXER_HANDLE, MIXER_STRIPS,
        MIXER_STRIP_FADER_FIRST, MIXER_STRIP_PAN_FIRST, MIXER_STRIP_SOLO_FIRST, MIXER_STRIP_MUTE_FIRST,
        MIXER_STRIP_METER_FIRST, MIXER_STRIP_CLIP_FIRST, MIXER_STRIP_NAME_FIRST,
        MIXER_MASTER_FADER, MIXER_MASTER_METER, MIXER_MASTER_CLIP, MIXER_WINDOW_OPEN, TRACK_SETTINGS_PAN,
    )
}
