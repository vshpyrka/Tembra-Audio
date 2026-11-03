import SwiftUI
import TembraShared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Audio opened with the app from the Files app becomes a new project (feature 011, spec FR-026a).
                .onOpenURL { url in MainViewControllerKt.openFile(url: url) }
        }
        // The shortcut catalogue's Command chords in the menu bar (feature 011, spec FR-022).
        .commands { ShortcutCommands() }
    }
}
