import XCTest

/// The mixer journey (feature 012, contracts/perf-instrumentation.md "Journey `mixer`", the same fourteen steps on both
/// platforms): open Projects, open the demo card, wait for the timeline's overviews, play; open the mixer from the
/// toolbar (the overflow menu on a compact bar) and wait for the first strip's meter; drag the first fader down by a
/// third and back over a second each way; raise the sheet to its full form where the knob is not yet shown (a phone
/// opens at the half height), turn the first knob up by 60 pt and back; solo the second strip and back; mute
/// the third and back; swipe the strip row to its end and back; drag the master down by a quarter and back; close the
/// sheet; undo until nothing is left (the document is the same across iterations); pause; skip to start.
struct MixerJourney {
    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        app.tapAnchor(Anchors.projectsCardDemo)
        let root = app.waitForAnchor(Anchors.timelineRoot)
        let ready = NSPredicate(format: "value == %@", Anchors.timelineReadyState)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: root)], timeout: 10), .completed,
                       "the timeline's overviews were not ready within 10 s")
        // From the start: the playhead stays where the last iteration paused, and a start in the demo's last seconds is refused.
        app.tapAnchor(Anchors.transportSkipStart)
        app.tapAnchor(Anchors.transportPlay)
        toggleMixer(app)
        app.waitForAnchor(Anchors.mixerStripMeterFirst)

        let fader = app.waitForAnchor(Anchors.mixerStripFaderFirst)
        let faderThird = fader.frame.height / 3
        drag(app, from: centre(of: fader), by: CGVector(dx: 0, dy: faderThird), seconds: 1)
        drag(app, from: centre(of: fader).withOffset(CGVector(dx: 0, dy: faderThird)), by: CGVector(dx: 0, dy: -faderThird), seconds: 1)

        // The knob lives in the full form: a tap on the handle raises a half sheet (skipped where the knob already shows);
        // the sheet goes back to its half height before it closes, so every iteration starts the same way.
        let raised = !app.anchor(Anchors.mixerStripPanFirst).exists
        if raised { app.tapAnchor(Anchors.mixerHandle); sleep(1) }
        let knob = app.waitForAnchor(Anchors.mixerStripPanFirst)
        drag(app, from: centre(of: knob), by: CGVector(dx: 0, dy: -Self.knobTurn), seconds: 0.5)
        drag(app, from: centre(of: knob).withOffset(CGVector(dx: 0, dy: -Self.knobTurn)), by: CGVector(dx: 0, dy: Self.knobTurn), seconds: 0.5)

        app.tapAnchor(Anchors.mixerSolo(1))
        app.tapAnchor(Anchors.mixerSolo(1))
        app.tapAnchor(Anchors.mixerMute(2))
        app.tapAnchor(Anchors.mixerMute(2))

        let strips = app.waitForAnchor(Anchors.mixerStrips)
        strips.swipeLeft(velocity: .fast)
        strips.swipeRight(velocity: .fast)

        let master = app.waitForAnchor(Anchors.mixerMasterFader)
        let masterQuarter = master.frame.height / 4
        drag(app, from: centre(of: master), by: CGVector(dx: 0, dy: masterQuarter), seconds: 1)
        drag(app, from: centre(of: master).withOffset(CGVector(dx: 0, dy: masterQuarter)), by: CGVector(dx: 0, dy: -masterQuarter), seconds: 1)

        if raised { app.tapAnchor(Anchors.mixerHandle); sleep(1) }
        toggleMixer(app)
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.anchor(Anchors.mixerSheet))
        XCTAssertEqual(XCTWaiter.wait(for: [gone], timeout: 5), .completed, "the mixer sheet did not close")
        undoAll(app)
        app.tapAnchor(Anchors.transportPlay)
        app.tapAnchor(Anchors.transportSkipStart)
    }

    /// The Mixer control sits in the toolbar on a wide bar and in the overflow menu on a compact one (spec FR-017).
    private func toggleMixer(_ app: XCUIApplication) {
        let button = app.anchor(Anchors.timelineMixer)
        if button.exists {
            button.tap()
        } else {
            app.tapAnchor(Anchors.timelineOverflowButton)
            app.tapAnchor(Anchors.timelineOverflowMixer)
        }
    }

    /// Undo until the button disables: the fader and knob returns are two steps each, the toggles four, the master two.
    private func undoAll(_ app: XCUIApplication) {
        let undo = app.anchor(Anchors.timelineUndo)
        for _ in 0..<12 where undo.exists && undo.isEnabled { undo.tap() }
    }

    /// A press-and-drag from a screen point over about [seconds]: the fader and knob gestures the contract times.
    private func drag(_ app: XCUIApplication, from start: XCUICoordinate, by offset: CGVector, seconds: TimeInterval) {
        let distance = max(abs(offset.dx), abs(offset.dy))
        let velocity = XCUIGestureVelocity(rawValue: Double(distance / CGFloat(seconds)))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(offset), withVelocity: velocity, thenHoldForDuration: 0)
    }

    /// An element's centre as a coordinate the drags start from.
    private func centre(of element: XCUIElement) -> XCUICoordinate {
        element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
    }

    private static let knobTurn: CGFloat = 60
}
