package audio.tembra.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iPadOS gives every window full screen through its own window controls and offers apps no request for it (feature
 * 011, research R21), so the Timeline's menu never shows the item here.
 */
class IosWindowControls : WindowControls {
    override val fullScreenAvailable: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val isFullScreen: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun enterFullScreen() = Unit

    override fun exitFullScreen() = Unit
}
