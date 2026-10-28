import XCTest

/// The recording journey (feature 010, contracts/perf-instrumentation.md): open Projects and the demo, add a track (the
/// new one is selected and last), arm it, toggle monitoring on and off, record one bar of count-in plus four bars at
/// 124 BPM, end the take, pause, undo the take and the track, skip to start.
struct RecordingJourney {
    /// The demo project's track count: the added track's index.
    static let demoTrackCount = 4

    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        app.tapAnchor(Anchors.projectsCardDemo)
        let root = app.waitForAnchor(Anchors.timelineRoot)
        let ready = NSPredicate(format: "value == %@", Anchors.timelineReadyState)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: root)], timeout: 10), .completed,
                       "the timeline's overviews were not ready within 10 s")
        app.tapAnchor(Anchors.timelineAddTrack)
        let last = Self.demoTrackCount
        app.tapAnchor(Anchors.trackArm(last))
        // The microphone is granted before the round; an alert that still shows is answered without waiting for one.
        answerMicrophoneAlertIfShown(timeout: 0)
        app.waitForAnchor(Anchors.trackMeter(last))
        toggleMonitor(app)
        let readout = app.waitForAnchor(Anchors.transportReadout)
        tapRecord(app, last)
        let countIn = NSPredicate(format: "label BEGINSWITH 'Count-in'")
        _ = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: countIn, object: readout)], timeout: 5)
        // One bar of count-in (1.9 s) plus four bars at 124 BPM (7.7 s): the clock passes 00:07.
        let passed = NSPredicate(format: "label MATCHES %@", "00:0[7-9].*|00:[1-5].*")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: passed, object: readout)], timeout: 30), .completed,
                       "the readout never passed 00:07 while recording")
        tapRecord(app, last)
        let take = app.anchor(Anchors.timelineClipTake)
        let gone = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: take)], timeout: 10), .completed,
                       "the growing take never ended")
        app.waitForAnchor(Anchors.trackClip(last))
        app.tapAnchor(Anchors.transportPlay)
        // The take, then the track: the document is unchanged across iterations.
        app.tapAnchor(Anchors.timelineUndo)
        app.tapAnchor(Anchors.timelineUndo)
        app.tapAnchor(Anchors.transportSkipStart)
    }

    /// The monitor toggle (toolbar or overflow entry) on and off again; the snackbar a refusal shows (no headphones on the
    /// bench) is left to time out. Skipped when neither control exists.
    private func toggleMonitor(_ app: XCUIApplication) {
        let monitor = app.anchor(Anchors.timelineMonitor)
        guard monitor.exists else { return }
        monitor.tap()
        if app.anchor(Anchors.snackbarMicrophoneSettings).exists { return }
        monitor.tap()
    }

    /// Record in the bar when it is wide enough, else in the armed track's compact header.
    private func tapRecord(_ app: XCUIApplication, _ index: Int) {
        let bar = app.anchor(Anchors.transportRecord)
        if bar.waitForExistence(timeout: 2) {
            bar.tap()
        } else {
            app.tapAnchor(Anchors.trackRecord(index))
        }
    }

    private func answerMicrophoneAlertIfShown(timeout: TimeInterval) {
        PerfJourneyTestCase.answerMicrophoneAlertIfShown(timeout: timeout)
    }
}
