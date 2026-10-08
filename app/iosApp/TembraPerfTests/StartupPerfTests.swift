import XCTest

/// Cold startup: the launch metric to the first frame (`waitUntilResponsive: false`) and to the responsive point
/// (`true`), 20 iterations each, the app terminated before every launch.
final class StartupPerfTests: PerfJourneyTestCase {
    func testColdLaunchToFirstFrame() {
        measure(metrics: [XCTApplicationLaunchMetric(waitUntilResponsive: false)], options: launchOptions) {
            app.terminate()
            startMeasuring()
            app.launch()
            stopMeasuring()
        }
    }

    func testColdLaunchToResponsive() {
        measure(metrics: [XCTApplicationLaunchMetric(waitUntilResponsive: true)], options: launchOptions) {
            app.terminate()
            startMeasuring()
            app.launch()
            stopMeasuring()
        }
    }

    private var launchOptions: XCTMeasureOptions {
        let options = XCTMeasureOptions()
        options.iterationCount = Metrics.iterationCount
        options.invocationOptions = [.manuallyStart, .manuallyStop]
        return options
    }
}
