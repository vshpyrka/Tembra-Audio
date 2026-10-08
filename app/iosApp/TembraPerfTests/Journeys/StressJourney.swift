import XCTest

/// The stress journey (spec FR-007, placeholder until the audio spine): the metronome clicks while the tuner listens,
/// then the metronome stops; the last tap, the measurement build's "write engine health" control, dumps the
/// audio-health counters to `engine-health.json` (feature 007, US5).
struct StressJourney {
    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navMetronome)
        app.waitForAnchor(Anchors.metronomeRoot)
        app.tapAnchor(Anchors.metronomeStartStop)
        app.tapAnchor(Anchors.navTuner)
        app.waitForAnchor(Anchors.tunerNoteReadout)
        for _ in 0..<3 {
            app.tapAnchor(Anchors.tunerModeByEar)
            app.tapAnchor(Anchors.tunerModeAutoListen)
        }
        app.waitForAnchor(Anchors.tunerNoteReadout)
        app.tapAnchor(Anchors.navMetronome)
        app.tapAnchor(Anchors.metronomeStartStop)
        app.tapAnchor(Anchors.devtoolsWriteHealth)
    }
}
