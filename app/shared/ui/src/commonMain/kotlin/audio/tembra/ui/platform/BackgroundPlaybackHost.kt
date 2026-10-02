package audio.tembra.ui.platform

import kotlinx.coroutines.flow.Flow

/**
 * Keeps the metronome playing with the screen locked or the app in the background (contracts/background-playback.md,
 * research R6): the Android implementation (a media-playback foreground service) lives in `:androidApp`, the iOS one in
 * this module's `iosMain`; tests use `FakeBackgroundPlaybackHost`.
 */
interface BackgroundPlaybackHost {
    /** Makes the process eligible to keep playing after the screen locks or the app is backgrounded. Returns false when the platform refuses. */
    fun keepAlive(): Boolean

    /** Gives the eligibility back; called after the engine has stopped. */
    fun release()

    /**
     * The user asked to stop from outside the app (the Android notification's Stop action). Hot, without replay: a
     * request made while nobody collects is dropped, since only a running metronome, whose ViewModel collects, has
     * anything to stop. One collector (the metronome ViewModel).
     */
    val stopRequests: Flow<Unit>
}
