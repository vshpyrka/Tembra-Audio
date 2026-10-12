import XCTest

/// The facade seen from XCTest (spike 7, spec SC-006): a known section measured by name yields one value per
/// iteration, and the tempo change's animation interval is measurable by the same metric (its hitch columns are the
/// frames family on iOS; absent columns mean `XCTHitchMetric(application:)` is the fallback, recorded in research.md).
final class FacadeSelfCheckTests: PerfJourneyTestCase {
    func testModeSwitchSectionIsMeasuredByName() {
        let metric = Metrics.section("tuner.modeSwitch")
        measure(metrics: [metric], options: Metrics.options) {
            launchWarm()
            app.tapAnchor(Anchors.navTuner)
            app.waitForAnchor(Anchors.tunerNoteReadout)
            startMeasuring()
            app.tapAnchor(Anchors.tunerModeByEar)
            app.tapAnchor(Anchors.tunerModeAutoListen)
            stopMeasuring()
        }
    }

    func testTempoChangeAnimationIntervalIsMeasured() {
        let metric = Metrics.section("metronome.tempoChange")
        measure(metrics: [metric], options: Metrics.options) {
            launchWarm()
            app.tapAnchor(Anchors.navMetronome)
            app.waitForAnchor(Anchors.metronomeTempoReadout)
            startMeasuring()
            for _ in 0..<5 { app.tapAnchor(Anchors.metronomeTempoUp) }
            stopMeasuring()
        }
    }
}
