import XCTest

/// Base of every performance test: runs the preflight in `setUp`, attaches `preflight.json` (kept always) and refuses
/// the round with `PERF-REFUSED:` when a check fails; launches the app with the `-perf` argument.
class PerfJourneyTestCase: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
        // A round runs at the lowest brightness (the owner's rule for every target); the runner is the only remote
        // control a physical phone offers, and simulators ignore the value.
        UIScreen.main.brightness = 0
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
        grantMicrophoneOnce()
    }

    fileprivate static let allowButton = NSPredicate(format: "label BEGINSWITH 'Allow' OR label == 'OK'")

    /// A warm launch before each measured iteration: the app is on its start destination (Timeline) when the journey starts.
    func launchWarm() {
        app.terminate()
        app.launch()
        app.waitForAnchor(Anchors.startRoot)
    }

    /// Every round reinstalls the app, so every round is asked for the microphone once, with a SpringBoard alert that
    /// sits over the app and swallows a journey's taps until it is answered (on a fresh install iOS 27 asks on the
    /// start destination right after launch; otherwise when the tuner starts listening). XCTest's interruption monitors never see this
    /// alert ("Failed to construct element query matching interruption"), so it is answered through SpringBoard
    /// directly, at both moments, outside any measurement; the granted permission holds for the rest of the round.
    /// Simulators are granted by the pipeline before the run, so there the visit costs a few seconds and sees no alert.
    func grantMicrophoneOnce() {
        launchWarm()
        answerMicrophoneAlertIfShown()
        app.tapAnchor(Anchors.navTuner)
        app.waitForAnchor(Anchors.tunerRoot)
        answerMicrophoneAlertIfShown()
        app.waitForAnchor(Anchors.tunerNoteReadout)
    }

    private func answerMicrophoneAlertIfShown() {
        Self.answerMicrophoneAlertIfShown(timeout: 3)
    }

    /// Answers the system microphone alert wherever it appears (the Tuner, the start destination, the Timeline's first
    /// arm); with `timeout: 0` it only taps an alert that is already up.
    static func answerMicrophoneAlertIfShown(timeout: TimeInterval) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allow = springboard.alerts.buttons.matching(allowButton).firstMatch
        if timeout > 0 ? allow.waitForExistence(timeout: timeout) : allow.exists {
            allow.tap()
        }
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
