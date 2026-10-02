import SwiftUI
import UIKit
import TembraShared

/// Hosts the Compose Multiplatform UI. All screens live in the shared Kotlin module.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            developerView: launchArgument(prefix: "--tembra-developer-view="),
            tunerTestSignal: launchArgument(prefix: "--tembra-tuner-test-signal="),
            metronomeSyncopation: launchArgument(prefix: "--tembra-metronome-syncopation=")
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Debug builds accept `--tembra-developer-view=<value>` (spec FR-022 of feature 002), `--tembra-tuner-test-signal=<value>`
/// (spec FR-032 of feature 003) and `--tembra-metronome-syncopation=<value>` (spec FR-037 of feature 004) on the command
/// line; Release passes nil for all three.
private func launchArgument(prefix: String) -> String? {
    #if DEBUG
    return CommandLine.arguments.first { $0.hasPrefix(prefix) }.map { String($0.dropFirst(prefix.count)) }
    #else
    return nil
    #endif
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
