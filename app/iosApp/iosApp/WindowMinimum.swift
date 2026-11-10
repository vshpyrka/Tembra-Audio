import SwiftUI
import UIKit

/// The window's minimum on iPad (feature 011, spec FR-004, contracts/platform-shells.md): 360 × 480 points, set on the
/// scene once the hosting view is in a window. iPadOS 26 honours it for resizable windows; earlier systems ignore it.
/// A phone has no resizable window (`sizeRestrictions` is nil there), and its screen in landscape is shorter than the
/// minimum, so the SwiftUI frame that backs the minimum holds none on a phone ([frameMinimum]).
/// Debug builds launched with `-tembra.reportWindowMinimum` expose the minimum the scene holds as the accessibility
/// value of this invisible view, so the desktop tests can read it (nothing can resize an iPad window from a test).
struct WindowMinimum: UIViewRepresentable {
    static let size = CGSize(width: 360, height: 480)

    /// The minimum the hosting SwiftUI frame holds: [size] where a window can be resized, none on a phone.
    static var frameMinimum: CGSize? { UIDevice.current.userInterfaceIdiom == .phone ? nil : size }

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
