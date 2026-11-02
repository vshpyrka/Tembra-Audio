import XCTest

/// The stress journey (spec FR-007; extended by feature 009): the demo project plays with the click on while the lane
/// scrolls for five seconds, the tuner cycle then takes the audio (leaving the Timeline through the stop-and-leave
/// dialog), the metronome clicks while the tuner listens, then the metronome stops; the last tap, the measurement
/// build's "write engine health" control, dumps the audio-health counters to `engine-health.json` (feature 007, US5).
/// A Release baseline round (SC-007) has no such control, so the tap is skipped when the anchor is absent.
struct StressJourney {
    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        app.tapAnchor(Anchors.projectsCardDemo)
        app.waitForAnchor(Anchors.timelineRoot)
        app.tapAnchor(Anchors.transportMetronome)
        app.tapAnchor(Anchors.transportPlay)
        let lane = app.waitForAnchor(Anchors.timelineLaneFirst)
        let scrollUntil = Date().addingTimeInterval(5)
        while Date() < scrollUntil {
            lane.swipeLeft(velocity: .fast)
            lane.swipeRight(velocity: .fast)
        }
        recordWhileScrolling(app)
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
    private func recordWhileScrolling(_ app: XCUIApplication) {
        let add = app.anchor(Anchors.timelineAddTrack)
        guard add.exists else { return }
        add.tap()
        let last = RecordingJourney.demoTrackCount
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
}
