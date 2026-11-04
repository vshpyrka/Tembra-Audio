import XCTest

/// The window's minimum on iPad (feature 011, spec FR-004, contracts/platform-shells.md): the shell sets the scene's
/// minimum size to 360 × 480 points. Nothing can resize an iPad window from a test, so a debug-only launch argument
/// makes the shell report the minimum it set as the accessibility value of an invisible element.
final class WindowMinimumTests: DesktopTestCase {
    override var extraLaunchArguments: [String] { ["-tembra.reportWindowMinimum"] }

    func testTheSceneMinimumIs360By480() throws {
        try requireIpad()
        guard #available(iOS 26.0, *) else {
            throw XCTSkip("scene size restrictions need iPadOS 26")
        }
        launchApp()
        let report = element("tembra.windowMinimum")
        XCTAssertTrue(report.waitForExistence(timeout: 10), "the shell reported no window minimum")
        XCTAssertEqual(report.value as? String, "360x480")
    }
}
