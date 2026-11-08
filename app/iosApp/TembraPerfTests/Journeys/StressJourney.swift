import XCTest

/// The stress journey (spec FR-007; extended by feature 009): the demo project plays with the click on while the lane
/// scrolls for five seconds, the tuner cycle then takes the audio (leaving the Timeline through the stop-and-leave
/// dialog), the metronome clicks while the tuner listens, then the metronome stops; the last tap, the measurement
/// build's "write engine health" control, dumps the audio-health counters to `engine-health.json` (feature 007, US5).
/// A Release baseline round (SC-007) has no such control, so the tap is skipped when the anchor is absent. Feature 012
/// runs it on the 24-track stress project when the launch built one (`--tembra-stress-tracks=24`, the demo's loops
/// imported six times over) and adds the mixer phase before the health dump.
struct StressJourney {
    /// The stress project's tracks: the launch argument of `StressPerfTests`, the added track's index.
    static let stressTrackCount = 24

    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        // The stress project's card when the launch built it, the demo's otherwise (a Release baseline has no launch argument).
        let stressCard = app.anchor(Anchors.projectsCardStress)
        let stress = stressCard.waitForExistence(timeout: 5)
        if stress { stressCard.tap() } else { app.tapAnchor(Anchors.projectsCardDemo) }
        app.waitForAnchor(Anchors.timelineRoot)
        app.tapAnchor(Anchors.transportMetronome)
        app.tapAnchor(Anchors.transportPlay)
        let lane = app.waitForAnchor(Anchors.timelineLaneFirst)
        let scrollUntil = Date().addingTimeInterval(5)
        while Date() < scrollUntil {
            lane.swipeLeft(velocity: .fast)
            lane.swipeRight(velocity: .fast)
        }
        recordWhileScrolling(app, newTrack: stress ? Self.stressTrackCount : RecordingJourney.demoTrackCount)
        mixerPhase(app)
        // The audio-health counters are written here, while the transport still owns the device: the metronome and the
        // tuner take it next and the engine restarts the counters per stream session (the release build has no devtools).
        let writeHealth = app.anchor(Anchors.devtoolsWriteHealth)
        if writeHealth.waitForExistence(timeout: 2) {
            writeHealth.tap()
        }
        app.tapAnchor(Anchors.navMetronome)
        // Leaving the Timeline while it plays: the stop-and-leave dialog (spec FR-008a of feature 009).
        app.tapAnchor(Anchors.dialogStopAndLeaveConfirm)
        app.waitForAnchor(Anchors.metronomeRoot)
        app.tapAnchor(Anchors.metronomeStartStop)
        app.tapAnchor(Anchors.navTuner)
        app.waitForAnchor(Anchors.tunerNoteReadout)
        for _ in 0..<3 {
            app.tapAnchor(Anchors.tunerModeByEar)
            app.tapAnchor(Anchors.tunerModeAutoListen)
        }
        app.waitForAnchor(Anchors.tunerNoteReadout)
        app.tapAnchor(Anchors.navMetronome)
        app.tapAnchor(Anchors.metronomeStartStop)
    }

    /// Feature 010: add a track, arm it, record three seconds without count-in while the lane scrolls, stop, undo the take
    /// and the track, so the health dump carries the recording counters. Every step tolerates a missing element (a build
    /// without recording, a denied microphone) and leaves the document as it was.
    private func recordWhileScrolling(_ app: XCUIApplication, newTrack last: Int) {
        let add = app.anchor(Anchors.timelineAddTrack)
        guard add.exists else { return }
        add.tap()
        let arm = app.anchor(Anchors.trackArm(last))
        guard arm.waitForExistence(timeout: 2) else { return }
        arm.tap()
        PerfJourneyTestCase.answerMicrophoneAlertIfShown(timeout: 0)
        let record = app.anchor(Anchors.transportRecord).exists ? app.anchor(Anchors.transportRecord) : app.anchor(Anchors.trackRecord(last))
        guard record.waitForExistence(timeout: 2) else { return }
        record.tap()
        // The track list scrolls the new track into view, so lane 0 may be off screen: swipe the new track's lane.
        let lane = app.anchor(Anchors.timelineLane(last))
        let scrollUntil = Date().addingTimeInterval(3)
        while Date() < scrollUntil {
            lane.swipeLeft(velocity: .fast)
            lane.swipeRight(velocity: .fast)
        }
        let stop = app.anchor(Anchors.transportRecord).exists ? app.anchor(Anchors.transportRecord) : app.anchor(Anchors.trackRecord(last))
        if stop.exists { stop.tap() }
        let undo = app.anchor(Anchors.timelineUndo)
        _ = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "isEnabled == true"), object: undo)], timeout: 10)
        if arm.exists { arm.tap() }
        for _ in 0..<2 where undo.exists { undo.tap() }
    }

    /// Feature 012 (contracts/perf-instrumentation.md "Journey `stress`"): the mixer open for five seconds with the meters
    /// live while the first lane scrolls above the half sheet and the metronome runs, one drag of the first fader (undone:
    /// the document persists between iterations), then closed. The Mixer control sits in the toolbar on a wide bar and in
    /// the overflow menu on a compact one.
    private func mixerPhase(_ app: XCUIApplication) {
        toggleMixer(app)
        app.waitForAnchor(Anchors.mixerSheet)
        let lane = app.anchor(Anchors.timelineLaneFirst)
        let scrollUntil = Date().addingTimeInterval(5)
        while Date() < scrollUntil {
            lane.swipeLeft(velocity: .fast)
            lane.swipeRight(velocity: .fast)
        }
        let fader = app.anchor(Anchors.mixerStripFaderFirst)
        if fader.waitForExistence(timeout: 2) { fader.swipeUp(velocity: .slow) }
        let undo = app.anchor(Anchors.timelineUndo)
        if undo.exists && undo.isEnabled { undo.tap() }
        toggleMixer(app)
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.anchor(Anchors.mixerSheet))
        _ = XCTWaiter.wait(for: [gone], timeout: 10)
    }

    private func toggleMixer(_ app: XCUIApplication) {
        let button = app.anchor(Anchors.timelineMixer)
        if button.exists {
            button.tap()
        } else {
            app.tapAnchor(Anchors.timelineOverflowButton)
            app.tapAnchor(Anchors.timelineOverflowMixer)
        }
    }
}
