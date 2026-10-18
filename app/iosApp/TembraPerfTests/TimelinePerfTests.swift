import XCTest

final class TimelinePerfTests: PerfJourneyTestCase {
    func testTimelineJourney() {
        measureJourney("timeline") { TimelineJourney().run($0) }
    }
}
