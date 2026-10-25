import XCTest

/// The timeline journey (feature 009, contracts/perf-instrumentation.md): open Projects, open the demo card, wait for
/// every visible overview (`screen.timeline`'s value reads "ready"), play eight bars, seek on the ruler, pinch the
/// first lane in and out, split the first clip, open and close the first track's settings, toggle the metronome
/// twice, pause, skip to start.
struct TimelineJourney {
    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        app.tapAnchor(Anchors.projectsCardDemo)
        let root = app.waitForAnchor(Anchors.timelineRoot)
        let ready = NSPredicate(format: "value == %@", Anchors.timelineReadyState)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: root)], timeout: 10), .completed,
                       "the timeline's overviews were not ready within 10 s")
        app.tapAnchor(Anchors.transportPlay)
        let readout = app.waitForAnchor(Anchors.transportReadout)
        // Eight bars at 124 BPM are 15.5 s: wait until the readout passes 00:15.
        let passed = NSPredicate(format: "label MATCHES %@", "00:1[5-9].*|00:[2-5].*")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: passed, object: readout)], timeout: 30), .completed,
                       "the readout never passed 00:15")
        let ruler = app.waitForAnchor(Anchors.timelineRuler)
        // Past the tempo and signature pills, which overlay the ruler's left third on a phone (they would open a menu), and
        // early enough in the demo's clips (they end at bar 17 of the 32 the fitted ruler shows) that the playhead, still
        // moving through the two pinches, is inside the first clip when the split comes.
        ruler.coordinate(withNormalizedOffset: CGVector(dx: 0.4, dy: 0.5)).tap()
        let lane = app.waitForAnchor(Anchors.timelineLaneFirst)
        lane.pinch(withScale: 1.6, velocity: 1.0)
        lane.pinch(withScale: 0.6, velocity: -1.0)
        // The split needs the playhead inside the first clip whatever zoom the pinches (and the saved view state) left:
        // back to the start, then past bar 1 while still playing (00:02 is bar 2 at 124 BPM), both through anchors.
        app.tapAnchor(Anchors.transportSkipStart)
        let restarted = NSPredicate(format: "label MATCHES %@", "00:0[01].*")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: restarted, object: readout)], timeout: 10), .completed, "skip-to-start never reached the readout")
        let pastBarOne = NSPredicate(format: "label MATCHES %@", "00:0[2-9].*")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: pastBarOne, object: readout)], timeout: 10), .completed, "the readout never passed 00:02 after skip-to-start")
        app.tapAnchor(Anchors.timelineClipFirst)
        app.tapAnchor(Anchors.timelineSplit)
        // The split is undone at once: the document persists between iterations and rounds, and a demo that keeps
        // its five clips is what every later iteration (and `timeline.clip.first`) relies on.
        app.tapAnchor(Anchors.timelineUndo)
        app.tapAnchor(Anchors.timelineTrackMenuFirst)
        app.tapAnchor(Anchors.timelineTrackSettings)
        app.tapAnchor(Anchors.timelineSettingsClose)
        app.waitForAnchor(Anchors.timelineRoot)
        app.tapAnchor(Anchors.transportMetronome)
        app.tapAnchor(Anchors.transportMetronome)
        app.tapAnchor(Anchors.transportPlay)
        app.tapAnchor(Anchors.transportSkipStart)
    }
}
