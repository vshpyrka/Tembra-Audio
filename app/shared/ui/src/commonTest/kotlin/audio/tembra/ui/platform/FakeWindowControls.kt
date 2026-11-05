package audio.tembra.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scripted window controls for the Timeline's full-screen item (feature 011, research R21). */
class FakeWindowControls(available: Boolean = false, fullScreen: Boolean = false) : WindowControls {
    override val fullScreenAvailable = MutableStateFlow(available)
    override val isFullScreen = MutableStateFlow(fullScreen)
    val requests = mutableListOf<Boolean>()

    override fun enterFullScreen() {
        requests += true
    }

    override fun exitFullScreen() {
        requests += false
    }
}
