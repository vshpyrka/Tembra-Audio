import SwiftUI
import UIKit
import TembraShared

/// Hosts the Compose Multiplatform UI. All screens live in the shared Kotlin module.
struct ComposeView: UIViewControllerRepresentable {
    /// The trackpad pinch bridge lives as long as the view (feature 011; removed with Compose Multiplatform 1.13).
    func makeCoordinator() -> TrackpadPinchBridge { TrackpadPinchBridge() }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(
            developerView: launchArgument(prefix: "--tembra-developer-view="),
            tunerTestSignal: launchArgument(prefix: "--tembra-tuner-test-signal="),
            tunerNoiseGate: launchArgument(prefix: "--tembra-tuner-noise-gate="),
            metronomeSyncopation: launchArgument(prefix: "--tembra-metronome-syncopation="),
            projectsSeed: launchArgument(prefix: "--tembra-projects-seed="),
            stressTracks: launchArgument(prefix: "--tembra-stress-tracks=")
        )
        context.coordinator.attach(to: controller.view)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Debug builds accept `--tembra-developer-view=<value>` (spec FR-022 of feature 002), `--tembra-tuner-test-signal=<value>`
/// (spec FR-032 of feature 003), `--tembra-tuner-noise-gate=<value>` (spec FR-023 of feature 006) and
/// `--tembra-metronome-syncopation=<value>` (spec FR-037 of feature 004) and `--tembra-projects-seed=<count>` (feature 009,
/// T121) and `--tembra-stress-tracks=<count>` (feature 012, the stress journey's project) on the command line; Release passes nil for all six.
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
            // Feature 011 (spec FR-004): no window smaller than 360 × 480 points.
            .frame(minWidth: WindowMinimum.size.width, minHeight: WindowMinimum.size.height)
            .background(WindowMinimum().frame(width: 1, height: 1).allowsHitTesting(false))
    }
}
