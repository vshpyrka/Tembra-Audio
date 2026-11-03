import XCTest

/// The windowing journey (feature 011, contracts/perf-instrumentation.md): open Metronome and start it; rotate across
/// the width classes and back three times (an iPad window cannot be resized from a test); stop; open the demo project,
/// play, rotate once more, right-click the first clip (a long press on an iPhone) and dismiss its menu, pause, then ⌘2
/// and ⌘1 on an iPad with a hardware keyboard (the navigation items on an iPhone). The pause comes before the
/// destination chords, as on Android. The device ends upright.
struct WindowingJourney {
    func run(_ app: XCUIApplication) {
        let device = XCUIDevice.shared
        let pad = UIDevice.current.userInterfaceIdiom == .pad
        defer { device.orientation = .portrait }

        app.tapAnchor(Anchors.navMetronome)
        app.waitForAnchor(Anchors.metronomeRoot)
        app.tapAnchor(Anchors.metronomeStartStop)
        for _ in 0..<3 {
            device.orientation = .landscapeLeft
            app.waitForAnchor(Anchors.metronomeRoot)
            device.orientation = .portrait
            app.waitForAnchor(Anchors.metronomeRoot)
        }
        app.tapAnchor(Anchors.metronomeStartStop)

        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        app.tapAnchor(Anchors.projectsCardDemo)
        app.waitForAnchor(Anchors.timelineRoot)
        app.tapAnchor(Anchors.transportPlay)
        device.orientation = .landscapeLeft
        app.waitForAnchor(Anchors.timelineRoot)
        device.orientation = .portrait

        let clip = app.waitForAnchor(Anchors.timelineClipFirst)
        if pad { clip.rightClick() } else { clip.press(forDuration: 0.8) }
        app.waitForAnchor(Anchors.timelineClipMenuFirst)
        // The open menu hides the rest of the screen from accessibility: ⌘. (close-overlay) on an iPad, a tap at the top
        // edge, outside the menu, on an iPhone.
        if pad {
            app.typeKey(".", modifierFlags: .command)
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.04)).tap()
        }
        XCTAssertTrue(app.anchor(Anchors.timelineClipMenuFirst).waitForNonExistence(timeout: Anchors.timeout), "the clip menu did not close")
        app.tapAnchor(Anchors.transportPlay)

        if pad {
            app.typeKey("2", modifierFlags: .command)
            app.waitForAnchor(Anchors.tunerRoot)
            app.typeKey("1", modifierFlags: .command)
        } else {
            app.tapAnchor(Anchors.navTuner)
            app.waitForAnchor(Anchors.tunerRoot)
            app.tapAnchor(Anchors.navTimeline)
        }
        app.waitForAnchor(Anchors.timelineRoot)
    }
}
