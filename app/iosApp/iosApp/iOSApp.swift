import SwiftUI
import TembraShared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Audio opened with the app from the Files app becomes a new project (feature 011, spec FR-026a).
                .onOpenURL { url in MainViewControllerKt.openFile(url: url) }
                // A test-only switch: an unattended soak (feature 010, T096) keeps the phone awake through a 30-minute
                // take with no touches. Without the argument the system's Auto-Lock applies, as a take survives the lock.
                .onAppear {
                    if CommandLine.arguments.contains("-tembra.keepScreenOn") { UIApplication.shared.isIdleTimerDisabled = true }
                }
        }
        // The shortcut catalogue's Command chords in the menu bar (feature 011, spec FR-022).
        .commands { ShortcutCommands() }
    }
}
