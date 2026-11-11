import XCTest

/// The timeline journey (feature 009, contracts/perf-instrumentation.md): open Projects, open the demo card, wait for
/// every visible overview (`screen.timeline`'s value reads "ready"), play eight bars, seek on the ruler, pinch the
/// first lane in and out, split the first clip, open and close the first track's settings, toggle the metronome
/// twice, on a phone open the master volume pop-up, drag its slider and close it, drag the overview strip's window
/// there and back, drag the strip up and down from its centre to zoom in and out, double-tap it to fit the whole project
/// (all three unless the device hid the strip), pause, skip to start.
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
        // Clear of the ruler's left third (the tempo and signature pills that overlaid it before design iteration D1
        // would have opened a menu), and early enough in the demo's clips (they end at bar 17 of the 32 the fitted ruler
        // shows) that the playhead, still moving through the two pinches, is inside the first clip when the split comes.
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
        adjustMasterVolume(app)
        dragOverviewWindow(app)
        zoomOverviewStrip(app)
        fitOverviewStrip(app)
        app.tapAnchor(Anchors.transportPlay)
        app.tapAnchor(Anchors.transportSkipStart)
    }

    /// Phones only (design iteration D1): the speaker button opens the master volume pop-up (`transport.masterPopup`), its
    /// slider is dragged a little, a tap outside the pop-up closes it, and undo restores the volume: the drag is one undo
    /// step, and the document persists between iterations. From 600 pt of bar width the slider is inline and there is no
    /// button, so the step is skipped.
    private func adjustMasterVolume(_ app: XCUIApplication) {
        let button = app.anchor(Anchors.transportMasterButton)
        guard button.exists else { return }
        // The readout is outside the pop-up, which opens above the bar; its frame is read before the pop-up shows.
        let outside = app.waitForAnchor(Anchors.transportReadout).frame
        button.tap()
        // From the slider's centre (its node's edges are Material's focus-ring room, outside the touch area), an eighth of
        // its width to the right.
        let slider = app.waitForAnchor(Anchors.transportMaster)
        let centre = slider.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        centre.press(forDuration: 0.05, thenDragTo: centre.withOffset(CGVector(dx: slider.frame.width / 8, dy: 0)))
        screenPoint(app, outside.midX, outside.midY).tap()
        let closed = NSPredicate(format: "exists == false")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: closed, object: slider)], timeout: 5), .completed,
                       "the master volume pop-up did not close")
        app.tapAnchor(Anchors.timelineUndo)
    }

    /// The overview strip shows unless the device hid it from the overflow menu (spec FR-015; T151), and then the step is
    /// skipped: the strip's window is dragged from its centre by a quarter of its width, inside `timeline.scroll`, then
    /// back by the distance it moved (it stops at the project's end), so the view returns.
    private func dragOverviewWindow(_ app: XCUIApplication) {
        guard app.anchor(Anchors.timelineOverview).exists else { return }
        let window = app.waitForAnchor(Anchors.timelineOverviewWindow)
        let before = window.frame
        let start = screenPoint(app, before.midX, before.midY)
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: before.width / 4, dy: 0)))
        let after = window.frame
        let distance = after.minX - before.minX
        if abs(distance) >= 1 {
            let from = screenPoint(app, after.midX, after.midY)
            from.press(forDuration: 0.05, thenDragTo: from.withOffset(CGVector(dx: -distance, dy: 0)))
        }
    }

    /// Step 8c, with the strip shown: a press at the strip's centre dragged straight up by a third of the screen's height
    /// zooms in about 2^(h/3/60 pt) around the window's centre (the drag locks to its first direction, and straight
    /// vertical picks zoom), then the same drag straight down zooms back out to about the same level.
    private func zoomOverviewStrip(_ app: XCUIApplication) {
        guard app.anchor(Anchors.timelineOverview).exists else { return }
        let strip = app.waitForAnchor(Anchors.timelineOverview).frame
        let distance = app.frame.height / 3
        let centre = screenPoint(app, strip.midX, strip.midY)
        centre.press(forDuration: 0.05, thenDragTo: centre.withOffset(CGVector(dx: 0, dy: -distance)))
        let top = screenPoint(app, strip.midX, strip.midY - distance)
        top.press(forDuration: 0.05, thenDragTo: top.withOffset(CGVector(dx: 0, dy: distance)))
    }

    /// Step 8d, with the strip shown: `doubleTap()` at the strip's centre animates over 300 ms to the fit (the whole
    /// project from its start, the window spanning the strip), so every iteration ends at the same view whatever zoom the
    /// earlier steps and the saved view state left. Waits up to 4 s for the window's width to match the strip's.
    private func fitOverviewStrip(_ app: XCUIApplication) {
        guard app.anchor(Anchors.timelineOverview).exists else { return }
        let strip = app.waitForAnchor(Anchors.timelineOverview)
        let width = strip.frame.width
        screenPoint(app, strip.frame.midX, strip.frame.midY).doubleTap()
        let window = app.anchor(Anchors.timelineOverviewWindow)
        let deadline = Date().addingTimeInterval(4)
        while abs(window.frame.width - width) > 4, Date() < deadline { Thread.sleep(forTimeInterval: 0.1) }
        XCTAssertLessThanOrEqual(abs(window.frame.width - width), 4, "the double tap did not fit the overview strip")
    }

    /// A point in screen coordinates (an element's `frame`), reached from the app's origin rather than through an element
    /// a pop-up may cover.
    private func screenPoint(_ app: XCUIApplication, _ x: CGFloat, _ y: CGFloat) -> XCUICoordinate {
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: x, dy: y))
    }
}
