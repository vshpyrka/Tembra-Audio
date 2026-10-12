import XCTest

/// The metronome journey (spec FR-007): open Metronome, start, tempo up x5, tempo down x5, stop.
struct MetronomeJourney {
    func run(_ app: XCUIApplication) {
        app.tapAnchor(Anchors.navMetronome)
        app.waitForAnchor(Anchors.metronomeRoot)
        app.waitForAnchor(Anchors.metronomeTempoReadout)
        app.tapAnchor(Anchors.metronomeStartStop)
        for _ in 0..<5 { app.tapAnchor(Anchors.metronomeTempoUp) }
        for _ in 0..<5 { app.tapAnchor(Anchors.metronomeTempoDown) }
        app.tapAnchor(Anchors.metronomeStartStop)
    }
}
