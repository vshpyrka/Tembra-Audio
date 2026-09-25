import SwiftUI
import UIKit
import TembraShared

/// Hosts the Compose Multiplatform UI. All screens live in the shared Kotlin module.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(developerView: developerViewArgument())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Debug builds accept `--tembra-developer-view=<value>` on the command line (spec FR-022); Release passes nil.
private func developerViewArgument() -> String? {
    #if DEBUG
    let prefix = "--tembra-developer-view="
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
