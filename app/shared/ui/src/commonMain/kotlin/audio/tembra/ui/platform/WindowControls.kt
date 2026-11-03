package audio.tembra.ui.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * App-requested full screen for a desktop window (feature 011, spec FR-010a, research R21). Android binds it to
 * `Activity.requestFullscreenMode` (API 34+), available only while the activity is in a free-form or multi-window mode;
 * iPadOS offers full screen through its own window controls, so its binding is never available. The Timeline's menu
 * reads it through its ViewModel.
 */
interface WindowControls {
    /** True while the window can be made full screen by the app (or is full screen and can be restored). */
    val fullScreenAvailable: StateFlow<Boolean>

    /** True while the app made the window full screen. */
    val isFullScreen: StateFlow<Boolean>

    fun enterFullScreen()

    fun exitFullScreen()
}
