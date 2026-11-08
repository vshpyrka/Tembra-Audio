package audio.tembra.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The iOS shell hosts one Compose view in one window and declares no second scene (feature 012, research R10: "Android
 * first; iPad later"), so the mixer's window is never offered here.
 */
class IosWindowCapabilities : WindowCapabilities {
    override val multipleWindows: Boolean = false
    override val mixerWindowOpen: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun openMixerWindow() = Unit
}
