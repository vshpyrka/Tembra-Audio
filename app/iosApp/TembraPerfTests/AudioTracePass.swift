import XCTest

/// The audio-trace pass (feature 007, US5; research spike 3): one unmeasured stress journey while the orchestrator's
/// `xctrace record --template 'Audio System Trace' --attach` listens to the app it launched with `devicectl`. The app
/// is attached to, never relaunched (a relaunch would leave the trace's pid behind), and the microphone was granted
/// by the measured run of the same round. The measured run skips this class (`-skip-testing`); the orchestrator
/// runs it alone (`-only-testing`) and reads nothing from its result bundle but the pass/fail.
final class AudioTracePass: XCTestCase {
    func testOneStressJourneyUnderTheAudioTrace() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.activate()
        app.waitForAnchor(Anchors.homeRoot)
        StressJourney().run(app)
    }
}
