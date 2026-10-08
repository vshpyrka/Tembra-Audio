import XCTest

final class TunerPerfTests: PerfJourneyTestCase {
    func testTunerJourney() {
        measureJourney("tuner") { TunerJourney().run($0) }
    }
}
