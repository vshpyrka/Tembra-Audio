import XCTest

/// Fresh-launch smoothness driver: the app is terminated, launched cold and taken to Metronome or Tuner, ten times per
/// test, while Instruments records the Hitches instrument system-wide on the device (`xctrace record --instrument
/// Hitches --all-processes`); the hitches and the gaps in the app's frame updates are read from that recording, one
/// app process per launch. `XCTHitchMetric(application:)` is not used: on the iPhone 14 (iOS 27.0.1, Xcode 27.1 beta)
/// it reported zero hitches in all 20 launches of 2026-10-19 in which the Hitches instrument counted at least one late
/// frame per launch, and zero again in two controls built to cause hitches.
/// The navigation tap goes to a coordinate read before measuring, and nothing inside the measurement queries the
/// accessibility tree: on a Compose tree such a query costs about a second of the app's main thread, which would
/// show up as hitches no user ever sees.
/// A performance round skips this class (`-skip-testing`, tracepulse); run it alone with
/// `-only-testing:TembraPerfTests/ColdLaunchHitchTests` against a Release build.
final class ColdLaunchHitchTests: PerfJourneyTestCase {
    /// Time from `launch()` returning to the tap, and from the tap to the end of the window. The tuner's needle draws
    /// every frame while it listens, so the settle window keeps the most demanding screen on display.
    private static let beforeTap: TimeInterval = 1.0
    private static let afterTap: TimeInterval = 3.0
    private static let iterations = 10

    func testColdLaunchThenMetronome() {
        measureColdLaunch(opening: Anchors.navMetronome)
    }

    func testColdLaunchThenTuner() {
        measureColdLaunch(opening: Anchors.navTuner)
    }

    private func measureColdLaunch(opening navItem: String) {
        launchWarm()
        let frame = app.waitForAnchor(navItem).frame
        // The tap is placed in SpringBoard's screen space, not the app's: resolving an app coordinate makes XCTest take an
        // accessibility snapshot of the app before every tap, and Compose then keeps its accessibility tree in sync while
        // the screen opens, work no user without an assistive technology causes (iPhone 14, 2026-10-21).
        let screen = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let target = screen.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: frame.midX, dy: frame.midY))

        let options = XCTMeasureOptions()
        options.iterationCount = Self.iterations
        options.invocationOptions = [.manuallyStart, .manuallyStop]
        measure(metrics: [XCTClockMetric()], options: options) {
            app.terminate()
            startMeasuring()
            app.launch()
            Thread.sleep(forTimeInterval: Self.beforeTap)
            target.tap()
            Thread.sleep(forTimeInterval: Self.afterTap)
            stopMeasuring()
        }
        // The window ends on the destination; a miss here means the coordinate tap went elsewhere.
        app.waitForAnchor(navItem == Anchors.navTuner ? Anchors.tunerRoot : Anchors.metronomeRoot)
    }
}
