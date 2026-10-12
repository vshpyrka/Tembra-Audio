import XCTest

/// The tuner journey (spec FR-007): open Tuner, wait for the note readout, switch to By Ear, back to Auto Listen.
struct TunerJourney {
    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navTuner)
        app.waitForAnchor(Anchors.tunerRoot)
        app.waitForAnchor(Anchors.tunerNoteReadout)
        app.tapAnchor(Anchors.tunerModeByEar)
        app.tapAnchor(Anchors.tunerModeAutoListen)
        app.waitForAnchor(Anchors.tunerNoteReadout)
    }
}
