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

    func testMatrix() {
        waitReady()
        if want("demo-stopped") { shot("demo-stopped") }
        if want("demo-playing") {
            tapIfPresent(Anchors.transportPlay); sleep(6); shot("demo-playing")
            tapIfPresent(Anchors.transportPlay); tapIfPresent(Anchors.transportSkipStart)
        }
        if want("tempo-dialog"), tapIfPresent("timeline.toolbar.tempo") { _ = app.anchor("dialog.tempo").waitForExistence(timeout: 5); shot("tempo-dialog"); tapIfPresent("timeline.tempo.done") }
        if want("metronome-sheet") {
            app.anchor(Anchors.transportMetronome).press(forDuration: 0.8)
            if app.anchor("timeline.metronomeSheet").waitForExistence(timeout: 5) { shot("metronome-sheet"); app.swipeDown(); sleep(1) }
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
        if want("grid-picker") {
            if app.anchor("timeline.toolbar.grid").exists { tapIfPresent("timeline.toolbar.grid") } else if tapIfPresent("timeline.toolbar.overflow.button") { tapIfPresent("timeline.toolbar.overflow.snap") }
            sleep(1); shot("grid-picker"); dismiss()
        }
        if want("clip-selected"), tapIfPresent(Anchors.timelineClipFirst) { shot("clip-selected") }
        if want("split-result") {
            // The seek lands on the ruler's left: the zoom buttons overlay its right end (D1).
            let ruler = app.anchor(Anchors.timelineRuler)
            if ruler.waitForExistence(timeout: 5) {
                ruler.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.5)).tap()
                if tapIfPresent(Anchors.timelineClipFirst), tapIfPresent(Anchors.timelineSplit) { shot("split-result"); tapIfPresent(Anchors.timelineUndo) }
            }
        }
        let lane = app.anchor(Anchors.timelineLaneFirst)
        if want("zoom-closest"), lane.exists { for _ in 0..<4 { lane.pinch(withScale: 2.5, velocity: 2.0) }; shot("zoom-closest") }
        if want("zoom-widest"), lane.exists { for _ in 0..<6 { lane.pinch(withScale: 0.3, velocity: -2.0) }; shot("zoom-widest") }
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
