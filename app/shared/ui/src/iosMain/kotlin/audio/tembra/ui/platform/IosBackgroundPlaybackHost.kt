package audio.tembra.ui.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Background playback on iOS (contracts/background-playback.md, research R6): `UIBackgroundModes = [audio]` in the
 * app's Info.plist plus the `.playback` session the engine activates keep the app running while the engine renders, so
 * there is nothing to acquire or give back. Nothing outside the app asks the metronome to stop, so [stopRequests] is
 * empty; interruptions reach the ViewModel as engine events instead.
 */
class IosBackgroundPlaybackHost : BackgroundPlaybackHost {
    override fun keepAlive(notification: PlaybackNotification): Boolean = true

    override fun release() = Unit

    override val stopRequests: Flow<Unit> = emptyFlow()
}
