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
        let writeHealth = app.anchor(Anchors.devtoolsWriteHealth)
        if writeHealth.waitForExistence(timeout: 2) {
            writeHealth.tap()
        }
    }
}
