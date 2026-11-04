import XCTest

/// The recording soak (feature 010, T096, SC-003), run by hand and never by a round (`orchestrate_ios` skips it): the
/// demo project plus a new track, one take of 29 min 30 s on it (under the engine's 30:00 cap) with the project playing
/// (the music stream silent under the run conditions; the input is the room), then the measurement build's health
/// counters written to `engine-health.json`, which `xcrun devicectl device copy from` reads off the phone. The phone
/// stays awake through the `-tembra.keepScreenOn` launch argument; no query touches the app while the take runs.
///
/// `TembraPerf.xctestplan` caps every test at 30 minutes, which the take plus the setup exceeds, so lift the cap for this
/// run: `xcodebuild test-without-building -xctestrun <TembraPerf xctestrun> -destination 'platform=iOS,id=<udid>'
/// -only-testing:TembraPerfTests/RecordingSoakTests -maximum-test-execution-time-allowance 2700`, then
/// `xcrun devicectl device copy from --device <udid> --source Documents/engine-health.json --domain-type appDataContainer
/// --domain-identifier <bundle id> --destination soak-health.json`.
final class RecordingSoakTests: PerfJourneyTestCase {
    static let takeSeconds: TimeInterval = 29 * 60 + 30

    func testThirtyMinuteTake() {
        executionTimeAllowance = 45 * 60
        app.launchArguments = ["-perf", "-tembra.keepScreenOn"]
        launchWarm()
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        app.tapAnchor(Anchors.projectsCardDemo)
        app.waitForAnchor(Anchors.timelineRoot)
        app.tapAnchor(Anchors.timelineAddTrack)
        let last = RecordingJourney.demoTrackCount
        app.tapAnchor(Anchors.trackArm(last))
        Self.answerMicrophoneAlertIfShown(timeout: 0)
        app.waitForAnchor(Anchors.trackMeter(last))
        tapRecord(last)
        let readout = app.waitForAnchor(Anchors.transportReadout)
        let rolling = NSPredicate(format: "label MATCHES %@", "00:0[1-9].*")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: rolling, object: readout)], timeout: 30), .completed,
                       "the take never started rolling")
        let started = Date()
        Thread.sleep(forTimeInterval: Self.takeSeconds)
        tapRecord(last)
        let take = app.anchor(Anchors.timelineClipTake)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: take)], timeout: 60),
                       .completed, "the growing take never ended")
        let undo = app.anchor(Anchors.timelineUndo)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "isEnabled == true"), object: undo)], timeout: 60),
                       .completed, "the take was not adopted within a minute")
        let seconds = Int(Date().timeIntervalSince(started))
        let report = XCTAttachment(string: "take of about \(seconds) s; readout at the stop: \(readout.label)")
        report.name = "soak.txt"
        report.lifetime = .keepAlways
        add(report)
        // The counters cover the stream session that recorded the take: write them before anything else takes the audio.
        app.tapAnchor(Anchors.devtoolsWriteHealth)
        app.tapAnchor(Anchors.transportPlay)
    }

    /// Record in the bar when it is wide enough, else in the armed track's compact header.
    private func tapRecord(_ index: Int) {
        let bar = app.anchor(Anchors.transportRecord)
        if bar.waitForExistence(timeout: 2) { bar.tap() } else { app.tapAnchor(Anchors.trackRecord(index)) }
    }
}
