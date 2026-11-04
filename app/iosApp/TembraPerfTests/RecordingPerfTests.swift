import XCTest

final class RecordingPerfTests: PerfJourneyTestCase {
    func testRecordingJourney() {
        measureJourney("recording") { RecordingJourney().run($0) }
    }
}
