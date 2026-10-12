import XCTest

final class StressPerfTests: PerfJourneyTestCase {
    func testStressJourney() {
        measureJourney("stress") { StressJourney().run($0) }
    }
}
