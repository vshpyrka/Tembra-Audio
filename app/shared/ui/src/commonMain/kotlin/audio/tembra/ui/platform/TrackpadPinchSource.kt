package audio.tembra.ui.platform

import kotlinx.coroutines.flow.Flow

/**
 * Trackpad pinches that Compose does not deliver itself (feature 011, research R9): Compose Multiplatform 1.12 has no
 * trackpad pinch on iOS, so the iPad shell's `UIPinchGestureRecognizer` (indirect pointer only) feeds the iOS binding;
 * Android delivers trackpad pinches as Compose `Scale` pointer events, so its binding emits nothing. The bridge and this
 * seam go when the app moves to Compose Multiplatform 1.13, which adds the pinch.
 */
interface TrackpadPinchSource {
    val pinches: Flow<TrackpadPinch>
}
