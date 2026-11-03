import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        // The shortcut catalogue's Command chords in the menu bar (feature 011, spec FR-022).
        .commands { ShortcutCommands() }
    }
}
