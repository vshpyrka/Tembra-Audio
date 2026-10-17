import XCTest

/// The perf anchors as XCUITest sees them: every `testTag` of `PerfAnchors` (shared/ui) reaches the accessibility
/// identifier of its element (Compose Multiplatform maps `testTag` to `accessibilityIdentifier`). The values mirror
/// `PerfAnchors` exactly; a tag that changes there changes here.
enum Anchors {
    static let startRoot = "screen.timeline"
    static let navTuner = "nav.item.tuner"
    static let navMetronome = "nav.item.metronome"
    static let tunerRoot = "screen.tuner"
    static let tunerModeAutoListen = "tuner.mode.autoListen"
    static let tunerModeByEar = "tuner.mode.byEar"
    static let tunerNoteReadout = "tuner.meter"
    static let metronomeRoot = "screen.metronome"
    static let metronomeStartStop = "metronome.play"
    static let metronomeTempoUp = "metronome.dial.up"
    static let metronomeTempoDown = "metronome.dial.down"
    static let metronomeTempoReadout = "metronome.tempoReadout"
    static let devtoolsWriteHealth = "devtools.writeHealth"

    /// Generous for a cold process on a cool phone.
    static let timeout: TimeInterval = 10
}

extension XCUIApplication {
    /// The element behind an anchor, whatever its role (Compose nodes are generic `other` elements).
    func anchor(_ tag: String) -> XCUIElement {
        descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    /// Waits for the anchor and fails the journey loudly when it never appears (spec US1 scenario 4).
    @discardableResult
    func waitForAnchor(_ tag: String, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let element = anchor(tag)
        XCTAssertTrue(element.waitForExistence(timeout: Anchors.timeout), "perf anchor '\(tag)' not found", file: file, line: line)
        return element
    }

    func tapAnchor(_ tag: String, file: StaticString = #filePath, line: UInt = #line) {
        waitForAnchor(tag, file: file, line: line).tap()
    }
}
