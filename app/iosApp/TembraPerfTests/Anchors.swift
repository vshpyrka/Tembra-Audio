import XCTest

/// The perf anchors as XCUITest sees them: every `testTag` of `PerfAnchors` (shared/ui) reaches the accessibility
/// identifier of its element (Compose Multiplatform maps `testTag` to `accessibilityIdentifier`). The values mirror
/// `PerfAnchors` exactly; a tag that changes there changes here.
enum Anchors {
    static let startRoot = "screen.timeline"
    static let navTuner = "nav.item.tuner"
    static let navMetronome = "nav.item.metronome"
    static let tunerRoot = "screen.tuner"
    static let tunerModeAutoListen = "tuner.mode.autoListen"
    static let tunerModeByEar = "tuner.mode.byEar"
    static let tunerNoteReadout = "tuner.meter"
    static let metronomeRoot = "screen.metronome"
    static let metronomeStartStop = "metronome.play"
    static let metronomeTempoUp = "metronome.dial.up"
    static let metronomeTempoDown = "metronome.dial.down"
    static let metronomeTempoReadout = "metronome.tempoReadout"
    static let devtoolsWriteHealth = "devtools.writeHealth"

    // Feature 009 (contracts/perf-instrumentation.md).
    static let navProjects = "nav.item.projects"
    static let projectsRoot = "screen.projects"
    static let projectsSearch = "projects.search"
    static let projectsCardDemo = "projects.card.demo"
    static let navTimeline = "nav.item.timeline"
    static let timelineRoot = "screen.timeline"
    static let timelineAddTrack = "timeline.toolbar.addTrack"
    static let timelineSplit = "timeline.toolbar.split"
    static let timelineUndo = "timeline.toolbar.undo"
    static let timelineRuler = "timeline.ruler"
    static let timelineLaneFirst = "timeline.lane.0"
    static let timelineClipFirst = "timeline.clip.first"
    static let timelineTrackMenuFirst = "timeline.track.menu.0"
    static let timelineTrackSettings = "timeline.track.menu.settings"
    static let timelineSettingsClose = "timeline.settings.close"
    static let transportPlay = "transport.play"
    static let transportSkipStart = "transport.skipStart"
    static let transportMetronome = "transport.metronome"
    static let transportReadout = "transport.readout"
    static let dialogStopAndLeaveConfirm = "dialog.stopAndLeave.confirm"

    // Feature 009's design iteration D1 (T146): the phone bar's speaker button, its pop-up's slider, the overview strip
    // and its window.
    static let transportMasterButton = "transport.master.button"
    static let transportMaster = "transport.master"
    static let timelineOverview = "timeline.overview"
    static let timelineOverviewWindow = "timeline.overview.window"

    // Feature 010 (timeline recording, contracts/perf-instrumentation.md).
    static let timelineTrackArmFirst = "timeline.track.arm.0"
    static let timelineTrackMeterFirst = "timeline.track.meter.0"
    static let timelineTrackRecordFirst = "timeline.track.record.0"
    static let timelineTrackClipFirst = "timeline.track.clip.0"
    static let transportRecord = "transport.record"
    static let timelineMonitor = "timeline.toolbar.monitor"
    static let timelineOverflowMonitor = "timeline.toolbar.overflow.monitor"
    static let metronomeCountIn = "metronome.sheet.countIn"
    static let timelineClipTake = "timeline.clip.take"
    static let snackbarMicrophoneSettings = "snackbar.microphone.settings"
    static let dialogStopRecordingConfirm = "dialog.stopRecording.confirm"

    /// The arm toggle, the meter and Record of track `index` (the recording journey's new track is the last one).
    static func timelineLane(_ index: Int) -> String { "timeline.lane.\(index)" }
    static func trackArm(_ index: Int) -> String { "timeline.track.arm.\(index)" }
    static func trackMeter(_ index: Int) -> String { "timeline.track.meter.\(index)" }
    static func trackClip(_ index: Int) -> String { "timeline.track.clip.\(index)" }
    static func trackRecord(_ index: Int) -> String { "timeline.track.record.\(index)" }
    // Feature 011 (contracts/perf-instrumentation.md, journey `windowing`): the first clip's menu and the shortcut sheet.
    static let timelineClipMenuFirst = "timeline.clip.first.menu"
    static let shortcutSheet = "shortcuts.sheet"

    // Feature 012 (contracts/perf-instrumentation.md, journey `mixer`): the Mixer button, the sheet, the first strip's
    // controls (strip i through the functions below), the master strip, the window button and the settings' pan slider.
    static let timelineMixer = "timeline.toolbar.mixer"
    static let timelineOverflowMixer = "timeline.toolbar.overflow.mixer"
    static let mixerSheet = "mixer.sheet"
    static let mixerHandle = "mixer.handle"
    static let mixerStrips = "mixer.strips"
    static let mixerStripFaderFirst = "mixer.strip.fader.0"
    static let mixerStripPanFirst = "mixer.strip.pan.0"
    static let mixerStripSoloFirst = "mixer.strip.solo.0"
    static let mixerStripMuteFirst = "mixer.strip.mute.0"
    static let mixerStripMeterFirst = "mixer.strip.meter.0"
    static let mixerStripClipFirst = "mixer.strip.clip.0"
    static let mixerStripNameFirst = "mixer.strip.name.0"
    static let mixerMasterFader = "mixer.master.fader"
    static let mixerMasterMeter = "mixer.master.meter"
    static let mixerMasterClip = "mixer.master.clip"
    static let mixerWindowOpen = "mixer.window.open"
    static let trackSettingsPan = "trackSettings.pan"

    /// The fader, pan knob, Solo, Mute, meter, clip latch and name of strip `index`.
    static func mixerFader(_ index: Int) -> String { "mixer.strip.fader.\(index)" }
    static func mixerPan(_ index: Int) -> String { "mixer.strip.pan.\(index)" }
    static func mixerSolo(_ index: Int) -> String { "mixer.strip.solo.\(index)" }
    static func mixerMute(_ index: Int) -> String { "mixer.strip.mute.\(index)" }
    static func mixerMeter(_ index: Int) -> String { "mixer.strip.meter.\(index)" }
    static func mixerClip(_ index: Int) -> String { "mixer.strip.clip.\(index)" }
    static func mixerName(_ index: Int) -> String { "mixer.strip.name.\(index)" }

    /// The `screen.timeline` value once every visible overview is built; "loading" before (not an anchor of its own).
    static let timelineReadyState = "ready"

    /// Generous for a cold process on a cool phone.
    static let timeout: TimeInterval = 10
}

extension XCUIApplication {
    /// The element behind an anchor, whatever its role (Compose nodes are generic `other` elements).
    func anchor(_ tag: String) -> XCUIElement {
        descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    /// Waits for the anchor and fails the journey loudly when it never appears (spec US1 scenario 4).
    @discardableResult
    func waitForAnchor(_ tag: String, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let element = anchor(tag)
        XCTAssertTrue(element.waitForExistence(timeout: Anchors.timeout), "perf anchor '\(tag)' not found", file: file, line: line)
        return element
    }

    func tapAnchor(_ tag: String, file: StaticString = #filePath, line: UInt = #line) {
        waitForAnchor(tag, file: file, line: line).tap()
    }
}
