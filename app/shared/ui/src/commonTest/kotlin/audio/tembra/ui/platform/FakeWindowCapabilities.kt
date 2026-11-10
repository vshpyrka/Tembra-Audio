package audio.tembra.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow

/** Scripted window capabilities for the mixer's second window (feature 012, spec FR-021a): counts the open requests. */
class FakeWindowCapabilities(override val multipleWindows: Boolean = false) : WindowCapabilities {
    override val mixerWindowOpen = MutableStateFlow(false)
    var opens = 0
        private set

    override fun openMixerWindow() {
        opens++
    }
}
