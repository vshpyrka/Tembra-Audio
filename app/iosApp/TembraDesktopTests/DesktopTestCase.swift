import XCTest

/// Base of the iPad desktop tests (feature 011, contracts/platform-shells.md): menu commands, hardware keys, pointer
/// input and the window minimum, on an iPad simulator. Each test launches the app fresh with the debug-only launch
/// arguments it needs; a destination without pointer support (an iPhone, or an iPad simulator that reports none) skips
/// with a visible reason instead of failing.
class DesktopTestCase: XCTestCase {
    var app: XCUIApplication!

    /// Launch arguments added to every launch; subclasses append their own before calling `launchApp()`.
    var extraLaunchArguments: [String] { [] }

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
        // The owner's run conditions: the lowest brightness on every target (simulators ignore it).
        UIScreen.main.brightness = 0
        app = XCUIApplication()
    }

    override func tearDownWithError() throws {
        app?.terminate()
        try super.tearDownWithError()
    }

    /// Launches the app and waits for the start destination's root to exist.
    func launchApp() {
        app.launchArguments = extraLaunchArguments
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "the app did not reach the foreground")
    }

    /// Skips the test on a destination whose device has no pointer (iPhones, and iPads without pointer support).
    func requirePointerInteraction(file: StaticString = #filePath, line: UInt = #line) throws {
        try XCTSkipUnless(
            XCUIDevice.shared.supportsPointerInteraction,
            "this destination has no pointer interaction: run TembraDesktopTests on an iPad simulator",
            file: file,
            line: line
        )
    }

    /// Skips the test on a destination that is not an iPad (the desktop behaviour is iPad-only on iOS).
    func requireIpad(file: StaticString = #filePath, line: UInt = #line) throws {
        try XCTSkipUnless(
            UIDevice.current.userInterfaceIdiom == .pad,
            "TembraDesktopTests run on an iPad simulator",
            file: file,
            line: line
        )
    }

    /// The element whose accessibility identifier is `id` (the shared UI's test tags surface as identifiers).
    func element(_ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }
}
