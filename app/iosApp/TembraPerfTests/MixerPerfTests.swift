import XCTest

final class MixerPerfTests: PerfJourneyTestCase {
    func testMixerJourney() {
        measureJourney("mixer") { MixerJourney().run($0) }
    }
}
