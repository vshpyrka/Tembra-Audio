import XCTest

final class StressPerfTests: PerfJourneyTestCase {
    func testStressJourney() {
        // Feature 012: the measurement build builds the 24-track stress project on this request (once per install).
        app.launchArguments += ["--tembra-stress-tracks=\(StressJourney.stressTrackCount)"]
        measureJourney("stress") { StressJourney().run($0) }
    }
}
