import SwiftUI
import UIKit

/// The window's minimum on iPad (feature 011, spec FR-004, contracts/platform-shells.md): 360 × 480 points, set on the
/// scene once the hosting view is in a window. iPadOS 26 honours it for resizable windows; earlier systems ignore it.
/// Debug builds launched with `-tembra.reportWindowMinimum` expose the minimum the scene holds as the accessibility
/// value of this invisible view, so the desktop tests can read it (nothing can resize an iPad window from a test).
struct WindowMinimum: UIViewRepresentable {
    static let size = CGSize(width: 360, height: 480)

    func makeUIView(context: Context) -> SceneMinimumView { SceneMinimumView() }

    func updateUIView(_ uiView: SceneMinimumView, context: Context) {}
}

final class SceneMinimumView: UIView {
    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard let scene = window?.windowScene else { return }
        scene.sizeRestrictions?.minimumSize = WindowMinimum.size
        #if DEBUG
        if CommandLine.arguments.contains("-tembra.reportWindowMinimum"), let minimum = scene.sizeRestrictions?.minimumSize {
            isAccessibilityElement = true
            accessibilityIdentifier = "tembra.windowMinimum"
            accessibilityValue = "\(Int(minimum.width))x\(Int(minimum.height))"
        }
        #endif
    }
}
