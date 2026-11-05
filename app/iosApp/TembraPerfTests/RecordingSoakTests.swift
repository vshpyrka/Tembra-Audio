import XCTest

/// The recording soak (feature 010, T096, SC-003), run by hand and never by a round (`orchestrate_ios` skips it). The
/// demo project is restored first, then four new tracks get a short take each (iOS cannot hand the app the fixture
/// stems: the app shares no folder the Files picker could reach, so recorded takes stand in for the imported ones), so
/// the project has eight tracks with audio; a ninth track records one take of 29 minutes (under the engine's 30:00 cap)
/// with the room as the input and the music stream silent. With the track still armed (one stream session), the take
/// plays back for eight seconds at its start, middle and end (the overview strip moves the window, the ruler seeks),
/// then the measurement build writes its health counters to `engine-health.json`: zero starvation and xruns there mean
/// no dropout while recording or playing back. Last, the demo is restored again, which deletes the soak's audio.
/// The phone stays awake through the `-tembra.keepScreenOn` launch argument; no query touches the app while the take runs.
///
/// `TembraPerf.xctestplan` caps every test at 30 minutes, which the take plus the setup exceeds, so lift the cap for this
/// run: `xcodebuild test-without-building -xctestrun <TembraPerf xctestrun> -destination 'platform=iOS,id=<udid>'
/// -only-testing:TembraPerfTests/RecordingSoakTests -maximum-test-execution-time-allowance 2700`, then
/// `xcrun devicectl device copy from --device <udid> --source Documents/engine-health.json --domain-type appDataContainer
/// --domain-identifier <bundle id> --destination soak-health.json`.
final class RecordingSoakTests: PerfJourneyTestCase {
    static let takeSeconds: TimeInterval = 29 * 60
    static let shortTakeSeconds: TimeInterval = 10
    static let stemTracks = 4

    private static let demoMenu = "projects.card.demo.menu.button"
    private static let restoreDemo = "projects.card.demo.menu.restoreDemo"

    func testNineTrackTake() {
        executionTimeAllowance = 45 * 60
        app.launchArguments = ["-perf", "-tembra.keepScreenOn"]
        launchWarm()
        restoreTheDemo()
        if !app.anchor(Anchors.timelineRoot).exists { app.tapAnchor(Anchors.projectsCardDemo) }
        app.waitForAnchor(Anchors.timelineRoot)
        let first = RecordingJourney.demoTrackCount
        for index in first..<(first + Self.stemTracks) {
            record(onNewTrack: index, for: Self.shortTakeSeconds)
            app.tapAnchor(Anchors.trackArm(index))  // disarm: only the long take's track stays armed
        }
        let last = first + Self.stemTracks
        let readout = record(onNewTrack: last, for: Self.takeSeconds)
        var notes = ["nine tracks; the long take stopped at \(readout.label)"]
        for (name, fraction) in [("start", 0.0), ("middle", 0.5), ("end", 0.97)] {
            notes.append("\(name): \(playBack(at: fraction))")
        }
        let report = XCTAttachment(string: notes.joined(separator: "\n"))
        report.name = "soak.txt"
        report.lifetime = .keepAlways
        add(report)
        // The counters cover the stream session that recorded and played the take: write them before disarming.
        app.tapAnchor(Anchors.devtoolsWriteHealth)
        app.tapAnchor(Anchors.trackArm(last))
        restoreTheDemo()
    }

    /// Projects → the demo's menu → "Restore demo project": a fresh demo, the previous one's audio deleted. When the demo
    /// was the open project the fresh one opens on the Timeline (spec 009 FR-045), otherwise Projects stays.
    private func restoreTheDemo() {
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        app.tapAnchor(Self.demoMenu)
        app.tapAnchor(Self.restoreDemo)
        Thread.sleep(forTimeInterval: 2)  // the old card stays until the new demo is installed
        let settled = NSPredicate { _, _ in self.app.anchor(Anchors.timelineRoot).exists || self.app.anchor(Anchors.projectsCardDemo).exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: settled, object: nil)], timeout: 15), .completed,
                       "neither the Timeline nor the demo card came back after the restore")
    }

    /// "+ Track" (the new track is selected and scrolled into view), arm it, record from the start for [seconds], stop,
    /// wait for the take to be adopted and pause the transport, which keeps playing after a stop. Returns the readout.
    @discardableResult
    private func record(onNewTrack index: Int, for seconds: TimeInterval) -> XCUIElement {
        app.tapAnchor(Anchors.transportSkipStart)
        app.tapAnchor(Anchors.timelineAddTrack)
        app.tapAnchor(Anchors.trackArm(index))
        Self.answerMicrophoneAlertIfShown(timeout: 0)
        app.waitForAnchor(Anchors.trackMeter(index))
        tapRecord(index)
        let readout = app.waitForAnchor(Anchors.transportReadout)
        let rolling = NSPredicate(format: "label MATCHES %@", "00:0[1-9].*")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: rolling, object: readout)], timeout: 30), .completed,
                       "the take on track \(index) never started rolling")
        Thread.sleep(forTimeInterval: seconds)
        tapRecord(index)
        let take = app.anchor(Anchors.timelineClipTake)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: take)], timeout: 60),
                       .completed, "the growing take on track \(index) never ended")
        let undo = app.anchor(Anchors.timelineUndo)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "isEnabled == true"), object: undo)], timeout: 60),
                       .completed, "the take on track \(index) was not adopted within a minute")
        app.tapAnchor(Anchors.transportPlay)
        return readout
    }

    /// Move the overview window to [fraction] of the project, seek to the middle of the visible ruler, play eight seconds.
    private func playBack(at fraction: Double) -> String {
        app.waitForAnchor(Anchors.timelineOverview).coordinate(withNormalizedOffset: CGVector(dx: fraction, dy: 0.5)).tap()
        app.waitForAnchor(Anchors.timelineRuler).coordinate(withNormalizedOffset: CGVector(dx: fraction == 0 ? 0.02 : 0.5, dy: 0.5)).tap()
        let readout = app.waitForAnchor(Anchors.transportReadout)
        let from = readout.label
        app.tapAnchor(Anchors.transportPlay)
        Thread.sleep(forTimeInterval: 8)
        app.tapAnchor(Anchors.transportPlay)
        return "\(from) → \(readout.label)"
    }

    /// Record in the bar when it is wide enough, else in the armed track's compact header.
    private func tapRecord(_ index: Int) {
        let bar = app.anchor(Anchors.transportRecord)
        if bar.waitForExistence(timeout: 2) { bar.tap() } else { app.tapAnchor(Anchors.trackRecord(index)) }
    }
}
