import XCTest

/// Base of every performance test: runs the preflight in `setUp`, attaches `preflight.json` (kept always) and refuses
/// the round with `PERF-REFUSED:` when a check fails; launches the app with the `-perf` argument.
class PerfJourneyTestCase: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
        let preflight = Preflight.run()
        let attachment = XCTAttachment(string: preflight.json)
        attachment.name = "preflight.json"
        attachment.lifetime = .keepAlways
        add(attachment)
        if let refusal = preflight.refusal {
            XCTFail("PERF-REFUSED: \(refusal)")
            return
        }
        app = XCUIApplication()
        app.launchArguments = ["-perf"]
    }

    /// A warm launch before each measured iteration: the app is on Home when the journey starts.
    func launchWarm() {
        app.terminate()
        app.launch()
        app.waitForAnchor(Anchors.homeRoot)
    }

    /// Measures `block` over the journey's metrics, `Metrics.iterationCount` times, after a warm launch each.
    func measureJourney(_ name: String, _ block: (XCUIApplication) -> Void) {
        let metrics = Metrics.journey(name, app: app)
        measure(metrics: metrics, options: Metrics.options) {
            launchWarm()
            startMeasuring()
            block(app)
            stopMeasuring()
        }
    }
}
