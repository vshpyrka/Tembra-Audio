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
        ruler.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.5)).tap()
        let lane = app.waitForAnchor(Anchors.timelineLaneFirst)
        lane.pinch(withScale: 1.6, velocity: 1.0)
        lane.pinch(withScale: 0.6, velocity: -1.0)
        app.tapAnchor(Anchors.timelineClipFirst)
        app.tapAnchor(Anchors.timelineSplit)
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
