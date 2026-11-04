import XCTest

/// The windowing journey (feature 011, contracts/perf-instrumentation.md): open Metronome and start it; rotate across
/// the width classes and back three times (an iPad window cannot be resized from a test); stop; open the demo project,
/// play, rotate once more, right-click the first clip and close its menu with ⌘., pause, then ⌘2 and ⌘1. XCUITest
/// synthesizes a pointer on an iPad only ("Pointer events are not supported for this device" on an iPhone, where a touch
/// has no clip menu: a long press lifts the clip) and ⌘ keys on an iPad and on any simulator (a physical iPhone drops
/// them: ⌘2 opened nothing on the iPhone 14, the iPhone simulator opens the Tuner; 2026-11-04); without them the menu
/// step is left out and the destinations are tapped, and the collector requires only the sections the device can make
/// (`tracepulse.orchestrate_ios.synthesized_inputs`). The pause comes before the destination chords, as on Android.
/// The device ends upright.
struct WindowingJourney {
    func run(_ app: XCUIApplication) {
        let device = XCUIDevice.shared
        let pointer = UIDevice.current.userInterfaceIdiom == .pad
        let keyboard = pointer || ProcessInfo.processInfo.environment["SIMULATOR_UDID"] != nil
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
        // From the start: the playhead stays where the last iteration paused, and a start in the demo's last seconds is
        // refused, after which the app never idles for XCUITest (the iPhone simulator's seventh iteration, 2026-11-04).
        app.tapAnchor(Anchors.transportSkipStart)
        app.tapAnchor(Anchors.transportPlay)
        device.orientation = .landscapeLeft
        app.waitForAnchor(Anchors.timelineRoot)
        device.orientation = .portrait

        if pointer {
            app.waitForAnchor(Anchors.timelineClipFirst).rightClick()
            app.waitForAnchor(Anchors.timelineClipMenuFirst)
            // The open menu hides the rest of the screen from accessibility: ⌘. (close-overlay) closes it.
            app.typeKey(".", modifierFlags: .command)
            XCTAssertTrue(app.anchor(Anchors.timelineClipMenuFirst).waitForNonExistence(timeout: Anchors.timeout), "the clip menu did not close")
        }
        app.tapAnchor(Anchors.transportPlay)

        if keyboard {
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
