package audio.tembra.perf.journeys

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * The perf anchors as UiAutomator sees them: every `testTag` of `audio.tembra.ui.perf.PerfAnchors` is a view
 * resource id (the app root sets `testTagsAsResourceId`), so `By.res(tag)` finds it. The values mirror
 * `PerfAnchors` exactly; a tag that changes there changes here.
 */
object Anchors {
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

    // Feature 009 (contracts/perf-instrumentation.md).
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
    const val TIMELINE_TRACKS = "timeline.tracks"
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

    // Feature 009's design iteration D1 (T146): the phone bar's speaker button, its pop-up's slider, the overview strip
    // and its window.
    const val TRANSPORT_MASTER_BUTTON = "transport.master.button"
    const val TRANSPORT_MASTER = "transport.master"
    const val TIMELINE_OVERVIEW = "timeline.overview"
    const val TIMELINE_OVERVIEW_WINDOW = "timeline.overview.window"

    // Feature 010 (timeline recording, contracts/perf-instrumentation.md).
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

    /** The arm toggle, the meter, Record and the clip of track [index] (the recording journey's new track is the last one). */
    fun trackArm(index: Int): String = "timeline.track.arm.$index"
    fun trackMeter(index: Int): String = "timeline.track.meter.$index"
    fun timelineLane(index: Int): String = "timeline.lane.$index"
    fun trackRecord(index: Int): String = "timeline.track.record.$index"
    // Feature 011 (contracts/perf-instrumentation.md, journey `windowing`): the first clip's menu and the shortcut sheet.
    const val TIMELINE_CLIP_MENU_FIRST = "timeline.clip.first.menu"
    const val SHORTCUT_SHEET = "shortcuts.sheet"

    // Feature 012 (contracts/perf-instrumentation.md, journey `mixer`): the Mixer button, the sheet, the first strip's
    // controls (strip i through the functions below), the master strip, the window button and the settings' pan slider.
    const val PROJECTS_CARD_STRESS = "projects.card.stressTracks"
    const val TIMELINE_OVERFLOW_BUTTON = "timeline.toolbar.overflow.button"
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

    /** The fader, pan knob, Solo, Mute, meter, clip latch and name of strip [index]. */
    fun mixerFader(index: Int): String = "mixer.strip.fader.$index"
    fun mixerPan(index: Int): String = "mixer.strip.pan.$index"
    fun mixerSolo(index: Int): String = "mixer.strip.solo.$index"
    fun mixerMute(index: Int): String = "mixer.strip.mute.$index"
    fun mixerMeter(index: Int): String = "mixer.strip.meter.$index"
    fun mixerClip(index: Int): String = "mixer.strip.clip.$index"
    fun mixerName(index: Int): String = "mixer.strip.name.$index"

    /**
     * A 1 dp marker node the Timeline shows once every visible overview is built (the `stateDescription` "ready" the
     * contract names is not reachable through UiAutomator's selectors; XCUITest reads it as the root's `value`).
     */
    const val TIMELINE_READY_MARKER = "screen.timeline.ready"

    /** Default wait for an anchor to appear, generous for a cold process on a cool phone. */
    const val TIMEOUT_MS = 10_000L

    fun selector(tag: String): BySelector = By.res(tag)

    /** Wait for the anchor, failing the journey loudly when it never appears (spec US1 scenario 4). */
    fun UiDevice.anchor(tag: String, timeoutMs: Long = TIMEOUT_MS): UiObject2 =
        wait(Until.findObject(selector(tag)), timeoutMs)
            ?: throw AssertionError("perf anchor '$tag' not found within $timeoutMs ms")

    fun UiDevice.tap(tag: String) {
        anchor(tag).click()
        waitForIdle()
    }

    /** Tap an anchor the build may not have (devtools in the release baseline); a short wait, never a failure. */
    fun UiDevice.tapIfPresent(tag: String, timeoutMs: Long = 2_000L) {
        val target = wait(Until.findObject(selector(tag)), timeoutMs) ?: return
        target.click()
        waitForIdle()
    }
}
