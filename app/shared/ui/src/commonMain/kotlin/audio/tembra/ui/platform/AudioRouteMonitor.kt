package audio.tembra.ui.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * The query side of the routes (feature 010, research R12; `kotlin-api-design`: an interface with a platform
 * binding, no `expect`): the Android binding in `:androidApp` reads `AudioManager.getDevices` and listens through an
 * `AudioDeviceCallback`, the iOS one in this module's `iosMain` reads `AVAudioSession.currentRoute` and the
 * route-change notification; tests use `FakeAudioRouteMonitor`. [route] is a `StateFlow`: renderable state with a
 * current value, read synchronously when the user taps the monitor toggle and collected by the controller.
 */
interface AudioRouteMonitor {
    val route: StateFlow<AudioRoute>
}
