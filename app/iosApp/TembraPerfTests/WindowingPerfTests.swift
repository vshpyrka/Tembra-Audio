import XCTest

final class WindowingPerfTests: PerfJourneyTestCase {
    func testWindowingJourney() {
        measureJourney("windowing") { WindowingJourney().run($0) }
    }
}
