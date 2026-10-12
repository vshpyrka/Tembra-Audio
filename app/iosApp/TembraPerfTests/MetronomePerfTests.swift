import XCTest

final class MetronomePerfTests: PerfJourneyTestCase {
    func testMetronomeJourney() {
        measureJourney("metronome") { MetronomeJourney().run($0) }
    }
}
