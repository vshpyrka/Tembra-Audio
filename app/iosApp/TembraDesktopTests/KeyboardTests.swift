import XCTest

/// Hardware keys on iPad (feature 011, spec FR-017, FR-022; contracts/keyboard-shortcuts.md rule 5): the menu bar's
/// Command chords open the destinations and the shortcut sheet, ⌘. closes it, and a Command chord runs once even
/// though the key also reaches Compose (research R6, T006).
final class KeyboardTests: DesktopTestCase {

    func testCommandDigitsOpenTheDestinationsAndCommandSlashTheSheet() throws {
        try requireIpad()
        launchApp()
        XCTAssertTrue(element("screen.timeline").waitForExistence(timeout: 30), "the Timeline did not appear")
        app.typeKey("2", modifierFlags: .command)
        XCTAssertTrue(element("screen.tuner").waitForExistence(timeout: 10), "⌘2 did not open the Tuner")
        app.typeKey("1", modifierFlags: .command)
        XCTAssertTrue(element("screen.timeline").waitForExistence(timeout: 10), "⌘1 did not open the Timeline")
        app.typeKey("/", modifierFlags: .command)
        XCTAssertTrue(element("shortcuts.sheet").waitForExistence(timeout: 10), "⌘/ did not open the shortcut sheet")
        // UIKit keeps a hardware Escape from the app on iPad (research R6 follow-up): ⌘., Apple's cancel chord, closes it.
        app.typeKey(".", modifierFlags: .command)
        XCTAssertTrue(element("shortcuts.sheet").waitForNonExistence(timeout: 10), "⌘. did not close the sheet")
    }

    func testACommandChordRunsOnce() throws {
        // ⌘N makes a project: had both the menu and Compose run it, the library would gain two.
        try requireIpad()
        launchApp()
        XCTAssertTrue(element("screen.timeline").waitForExistence(timeout: 30), "the Timeline did not appear")
        app.typeKey("4", modifierFlags: .command)
        let count = element("projects.count")
        XCTAssertTrue(count.waitForExistence(timeout: 10), "⌘4 did not open Projects")
        let before = projectCount(count)
        let cardsBefore = cardIdentifiers()

        app.typeKey("n", modifierFlags: .command)
        XCTAssertTrue(element("screen.timeline").waitForExistence(timeout: 10), "⌘N did not open the new project")
        app.typeKey("4", modifierFlags: .command)
        XCTAssertTrue(count.waitForExistence(timeout: 10))
        XCTAssertEqual(projectCount(count), before + 1, "⌘N ran \(projectCount(count) - before) times")

        // Leave the library as it was: delete the project the chord made.
        let made = cardIdentifiers().subtracting(cardsBefore)
        XCTAssertEqual(made.count, 1, "new cards: \(made)")
        for card in made {
            element("\(card).menu.button").click()
            element("\(card).menu.delete").click()
            element("dialog.confirm").click()
        }
        expectation(for: NSPredicate { _, _ in self.projectCount(count) == before }, evaluatedWith: nil)
        waitForExpectations(timeout: 10)
    }

    /// The number in "N projects".
    private func projectCount(_ element: XCUIElement) -> Int {
        Int(element.label.split(separator: " ").first.map(String.init) ?? "") ?? -1
    }

    /// The identifiers of the project cards on screen (`projects.card.<id>`, not their parts).
    private func cardIdentifiers() -> Set<String> {
        let cards = app.descendants(matching: .any).matching(NSPredicate(format: "identifier MATCHES %@", #"projects\.card\.[^.]+"#))
        return Set(cards.allElementsBoundByIndex.map(\.identifier))
    }
}
