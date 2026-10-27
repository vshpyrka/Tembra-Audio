import XCTest

/// The screenshot matrix of spec FR-062 on a simulator (feature 009, T125), driven through the same accessibility
/// identifiers as the journeys: one PNG per state into `TEMBRA_CAPTURE_DIR` (a simulator shares the Mac's file system),
/// named `<TEMBRA_CAPTURE_PREFIX>-<state>.png`; `TEMBRA_STATES` (comma separated) selects states, `TEMBRA_SEED` fills
/// the library. Not a performance test: no preflight, no `-perf`; skipped unless the directory is set.
final class ScreenshotMatrix: XCTestCase {
    private var app: XCUIApplication!
    private var dir: URL!
    private var prefix = "ios"
    private var states: Set<String>?

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = true
        let env = ProcessInfo.processInfo.environment
        guard let path = env["TEMBRA_CAPTURE_DIR"] else { throw XCTSkip("TEMBRA_CAPTURE_DIR is not set: the matrix runs only when asked for") }
        dir = URL(fileURLWithPath: path)
        prefix = env["TEMBRA_CAPTURE_PREFIX"] ?? "ios"
        if let list = env["TEMBRA_STATES"], !list.isEmpty { states = Set(list.split(separator: ",").map(String.init)) }
        app = XCUIApplication()
        if let seed = env["TEMBRA_SEED"], !seed.isEmpty { app.launchArguments = ["--tembra-projects-seed=\(seed)"] }
        app.launch()
        app.waitForAnchor(Anchors.startRoot)
    }

    private func want(_ state: String) -> Bool { states == nil || states!.contains(state) }

    private func shot(_ state: String) {
        sleep(1)
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: dir.appendingPathComponent("\(prefix)-\(state).png"))
    }

    private func waitReady() {
        let root = app.waitForAnchor(Anchors.timelineRoot)
        let ready = NSPredicate(format: "value == %@", Anchors.timelineReadyState)
        _ = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: root)], timeout: 15)
    }

    private func openDemo() {
        app.tapAnchor(Anchors.navProjects)
        app.waitForAnchor(Anchors.projectsRoot)
        let demo = app.anchor(Anchors.projectsCardDemo)
        if !demo.waitForExistence(timeout: 2) { app.swipeUp() }
        demo.tap()
        waitReady()
    }

    private func dismiss() {
        // Menus and sheets live in their own windows: a relaunch is the one dismissal that works everywhere, and the
        // app reopens the same project.
        app.terminate(); app.launch(); app.waitForAnchor(Anchors.startRoot); waitReady()
    }

    /// Taps the element when it exists within [timeout]; a missing element skips the state instead of ending the run.
    @discardableResult
    private func tapIfPresent(_ tag: String, timeout: TimeInterval = 5) -> Bool {
        let element = app.anchor(tag)
        guard element.waitForExistence(timeout: timeout) else { print("ScreenshotMatrix: no \(tag)"); return false }
        element.tap(); return true
    }

    /// Opens the toolbar's overflow menu and taps its row `timeline.toolbar.overflow.<row>`; false when either is missing.
    @discardableResult
    private func overflow(_ row: String) -> Bool {
        tapIfPresent("timeline.toolbar.overflow.button") && tapIfPresent("timeline.toolbar.overflow.\(row)")
    }

    /// Waits up to [timeout] for the element tagged [tag] to leave the tree; false when it is still there.
    private func waitGone(_ tag: String, timeout: TimeInterval = 5) -> Bool {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.anchor(tag))
        return XCTWaiter.wait(for: [gone], timeout: timeout) == .completed
    }

    /// Drags the overview strip's window by its right handle (8 pt wide; a press within 16 pt of the window's right edge
    /// takes it) to the screen x [toX], through screen coordinates from the app's origin; false when there is no strip.
    /// The press lands 1 pt inside the edge, so the edge follows the finger to within less than the closest level's
    /// one beat (about 2.6 pt on a phone's strip) and a drag onto either limit lands exactly on it.
    @discardableResult
    private func dragWindowRightHandle(toX: CGFloat) -> Bool {
        let window = app.anchor(Anchors.timelineOverviewWindow)
        guard app.anchor(Anchors.timelineOverview).exists, window.waitForExistence(timeout: 2) else { print("ScreenshotMatrix: no overview strip"); return false }
        let frame = window.frame
        let origin = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
        let handle = origin.withOffset(CGVector(dx: frame.maxX - 1, dy: frame.midY))
        handle.press(forDuration: 0.1, thenDragTo: origin.withOffset(CGVector(dx: toX, dy: frame.midY)))
        return true
    }

    func testMatrix() {
        waitReady()
        // Every state starts with the playhead at the start; the overview strip shows unless the device hid it (T151).
        tapIfPresent(Anchors.transportSkipStart, timeout: 2)
        if want("demo-stopped") { shot("demo-stopped") }
        if want("overview-strip"), app.anchor(Anchors.timelineOverview).exists { shot("overview-strip") }
        if want("overflow-menu"), tapIfPresent("timeline.toolbar.overflow.button") {
            _ = app.anchor("timeline.toolbar.overflow.overviewStrip").waitForExistence(timeout: 5); shot("overflow-menu"); dismiss()
        }
        if want("strip-hidden"), app.anchor(Anchors.timelineOverview).waitForExistence(timeout: 2) {
            // The overflow's "Overview strip" hides the strip (a per-device preference, T151); shown again afterwards so
            // the states below keep it.
            if overflow("overviewStrip"), waitGone(Anchors.timelineOverview) { shot("strip-hidden") }
            if !app.anchor(Anchors.timelineOverview).exists, overflow("overviewStrip"), !app.anchor(Anchors.timelineOverview).waitForExistence(timeout: 5) {
                print("ScreenshotMatrix: the overview strip did not come back")
            }
        }
        if want("snap-sheet") {
            // The full toolbar's "Snap" pill opens its menu of sizes; compact and landscape windows open the snap sheet
            // from the overflow menu's "Snap: <value>".
            if app.anchor("timeline.toolbar.grid").exists {
                if tapIfPresent("timeline.toolbar.grid"), app.anchor("timeline.toolbar.grid.sixteenth").waitForExistence(timeout: 5) { shot("snap-sheet") }
            } else if overflow("snap"), app.anchor("timeline.snapSheet").waitForExistence(timeout: 5) {
                shot("snap-sheet")
            }
            dismiss()
        }
        // Phones only: from 600 pt of bar width the slider is inline and there is no speaker button.
        if want("master-popup"), tapIfPresent(Anchors.transportMasterButton, timeout: 2) {
            if app.anchor("transport.master.popup").waitForExistence(timeout: 5) { shot("master-popup") }
            dismiss()
        }
        if want("demo-playing") {
            tapIfPresent(Anchors.transportPlay); sleep(6); shot("demo-playing")
            tapIfPresent(Anchors.transportPlay); tapIfPresent(Anchors.transportSkipStart)
        }
        if want("tempo-dialog"), tapIfPresent("timeline.toolbar.tempo") { _ = app.anchor("dialog.tempo").waitForExistence(timeout: 5); shot("tempo-dialog"); tapIfPresent("timeline.tempo.done") }
        if want("metronome-sheet") {
            app.anchor(Anchors.transportMetronome).press(forDuration: 0.8)
            if app.anchor("timeline.metronomeSheet").waitForExistence(timeout: 5) { shot("metronome-sheet") }
            dismiss()
        }
        if want("settings-screen"), tapIfPresent(Anchors.timelineTrackMenuFirst), tapIfPresent(Anchors.timelineTrackSettings) {
            sleep(1); shot(app.anchor("timeline.settings.inline").exists ? "settings-inline" : "settings-screen")
            tapIfPresent(Anchors.timelineSettingsClose); waitReady()
        }
        if want("stop-and-leave"), tapIfPresent(Anchors.transportPlay) {
            sleep(1); tapIfPresent(Anchors.navTuner)
            if app.anchor("dialog.stopAndLeave").waitForExistence(timeout: 5) { shot("stop-and-leave"); tapIfPresent("dialog.stopAndLeave.cancel") }
            sleep(1); tapIfPresent(Anchors.transportPlay); tapIfPresent(Anchors.transportSkipStart)
        }
        if want("clip-selected"), tapIfPresent(Anchors.timelineClipFirst) { shot("clip-selected") }
        if want("split-result") {
            // The seek lands early on the ruler, inside the first clip.
            let ruler = app.anchor(Anchors.timelineRuler)
            if ruler.waitForExistence(timeout: 5) {
                ruler.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.5)).tap()
                if tapIfPresent(Anchors.timelineClipFirst), tapIfPresent(Anchors.timelineSplit) { shot("split-result"); tapIfPresent(Anchors.timelineUndo) }
            }
        }
        if want("zoom-widest") || want("zoom-closest") {
            // The strip's right handle zooms with the window's left edge fixed: dragged onto that edge it reaches the
            // closest level, dragged to the strip's right end the widest (a second drag once the first left it short,
            // as the drawn window is never narrower than 24 pt).
            tapIfPresent(Anchors.transportSkipStart, timeout: 2)
            let window = app.anchor(Anchors.timelineOverviewWindow)
            if want("zoom-closest"), window.waitForExistence(timeout: 2), dragWindowRightHandle(toX: window.frame.minX) {
                tapIfPresent(Anchors.transportSkipStart); sleep(1)
                shot("zoom-closest")
            }
            let strip = app.anchor(Anchors.timelineOverview)
            if strip.exists {
                for _ in 0..<2 where window.frame.maxX < strip.frame.maxX - 1 { dragWindowRightHandle(toX: strip.frame.maxX) }
                sleep(1)
                if want("zoom-widest") { shot("zoom-widest") }
            }
        }
        if want("empty-project"), tapIfPresent(Anchors.navProjects) {
            app.waitForAnchor(Anchors.projectsRoot); if tapIfPresent("projects.new") { waitReady(); shot("empty-project") }
        }
        if want("library"), tapIfPresent(Anchors.navProjects) { app.waitForAnchor(Anchors.projectsRoot); sleep(1); shot("library") }
        if want("card-menu"), tapIfPresent(Anchors.navProjects) {
            app.waitForAnchor(Anchors.projectsRoot)
            let button = app.anchor("projects.card.demo.menu.button")
            if !button.waitForExistence(timeout: 2) { app.swipeUp() }
            if button.waitForExistence(timeout: 2) { button.tap(); sleep(1); shot("card-menu"); dismiss() }
            tapIfPresent(Anchors.navProjects); app.waitForAnchor(Anchors.projectsRoot)
        }
        if want("search-results"), tapIfPresent(Anchors.navProjects) {
            app.waitForAnchor(Anchors.projectsRoot)
            if tapIfPresent(Anchors.projectsSearch) { app.typeText("Seed"); sleep(1); shot("search-results"); tapIfPresent("projects.search.clear") }
        }
        if want("search-none"), tapIfPresent(Anchors.projectsSearch) { app.typeText("zzz"); sleep(1); shot("search-none"); tapIfPresent("projects.search.clear") }
        if want("landscape") {
            openDemo()
            XCUIDevice.shared.orientation = .landscapeLeft; sleep(2); shot("landscape")
            XCUIDevice.shared.orientation = .portrait; sleep(2)
        }
    }
}
