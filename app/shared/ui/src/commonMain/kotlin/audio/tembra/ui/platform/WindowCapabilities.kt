package audio.tembra.ui.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the platform gives the app a second window and how the mixer gets one (feature 012, spec FR-021a, research
 * R10; the `AudioRouteMonitor` pattern). Android binds it to free-form window management (desktop windowing, a
 * Chromebook, a tablet in desktop mode) and a second activity of the app; iOS has no second host, so its binding is
 * never available. The Timeline's ViewModel reads it for the handle's "Open in a window" button and for the Mixer
 * button's meaning while the window is open (bring it to the front, not the sheet).
 */
interface WindowCapabilities {
    /** True where the app may open a second window of its own. */
    val multipleWindows: Boolean

    /** True while the mixer window is open; the Mixer button and X bring it to the front then. */
    val mixerWindowOpen: StateFlow<Boolean>

    /** Opens the mixer window, or brings it to the front while it is open; nothing where [multipleWindows] is false. */
    fun openMixerWindow()
}
