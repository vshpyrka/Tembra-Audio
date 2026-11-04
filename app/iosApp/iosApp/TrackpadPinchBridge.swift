import UIKit
import TembraShared

/// The iPad's trackpad pinch for the timeline (feature 011, spec FR-014, research R9). Compose Multiplatform 1.12
/// delivers no trackpad pinch on iOS, so this recogniser, limited to indirect pointer input (a trackpad, never touch),
/// reports each change of a pinch to Kotlin (`reportTrackpadPinch`): the scale since the previous change and the
/// pinch's centre in the Compose view's pixels. Touch keeps going to Compose untouched.
///
/// Delete this file and `TrackpadPinchSource` when the app moves to Compose Multiplatform 1.13, which adds the pinch.
final class TrackpadPinchBridge: NSObject, UIGestureRecognizerDelegate {
    private var lastScale: CGFloat = 1

    func attach(to view: UIView) {
        let recognizer = UIPinchGestureRecognizer(target: self, action: #selector(pinched(_:)))
        recognizer.allowedTouchTypes = [NSNumber(value: UITouch.TouchType.indirectPointer.rawValue)]
        recognizer.cancelsTouchesInView = false
        recognizer.delaysTouchesBegan = false
        recognizer.delegate = self
        view.addGestureRecognizer(recognizer)
    }

    @objc private func pinched(_ recognizer: UIPinchGestureRecognizer) {
        switch recognizer.state {
        case .began:
            lastScale = 1
        case .changed:
            let step = recognizer.scale / lastScale
            lastScale = recognizer.scale
            guard let view = recognizer.view, step > 0 else { return }
            let location = recognizer.location(in: view)
            let pixels = view.contentScaleFactor
            MainViewControllerKt.reportTrackpadPinch(scale: Double(step), x: Double(location.x * pixels), y: Double(location.y * pixels))
        default:
            break
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }
}
