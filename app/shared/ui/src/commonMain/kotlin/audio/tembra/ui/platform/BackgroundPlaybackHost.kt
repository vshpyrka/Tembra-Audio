package audio.tembra.ui.platform

import kotlinx.coroutines.flow.Flow

/**
 * Keeps audio playing with the screen locked or the app in the background (contracts/background-playback.md of
 * feature 004, generalised for the project transport in feature 009): the Android implementation (a media-playback
 * foreground service) lives in `:androidApp`, the iOS one in this module's `iosMain`; tests use
 * `FakeBackgroundPlaybackHost`.
 */
interface BackgroundPlaybackHost {
    /**
     * Makes the process eligible to keep playing after the screen locks or the app is backgrounded, showing
     * [notification] where the platform shows one. Returns false when the platform refuses.
     */
    fun keepAlive(notification: PlaybackNotification): Boolean

    /** Gives the eligibility back; called after the engine has stopped. */
    fun release()

    /**
     * The user asked to stop from outside the app (the Android notification's Stop action). Hot, without replay: a
     * request made while nobody collects is dropped, since only running playback, whose owner collects, has anything
     * to stop. One collector at a time (the metronome ViewModel or the project session).
     */
    val stopRequests: Flow<Unit>
}
