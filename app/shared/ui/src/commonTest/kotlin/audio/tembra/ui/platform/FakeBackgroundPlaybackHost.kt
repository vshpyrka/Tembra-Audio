package audio.tembra.ui.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Scripted host for ViewModel tests (contracts/background-playback.md): [allow] answers [keepAlive], [alive] follows
 * the last [keepAlive] or [release], and [requestStop] plays the Android notification's Stop action.
 */
class FakeBackgroundPlaybackHost(var allow: Boolean = true) : BackgroundPlaybackHost {
    private val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val stopRequests: Flow<Unit> = stops.asSharedFlow()

    var alive: Boolean = false
        private set
    var keepAliveCount: Int = 0
        private set
    var releaseCount: Int = 0
        private set

    /** The last notification [keepAlive] was given. */
    var notification: PlaybackNotification? = null
        private set

    override fun keepAlive(notification: PlaybackNotification): Boolean {
        keepAliveCount++
        this.notification = notification
        alive = allow
        return allow
    }

    override fun release() {
        releaseCount++
        alive = false
    }

    /** Emits one stop request to the current collector; the buffer of one keeps the call non-suspending. */
    fun requestStop() {
        check(stops.tryEmit(Unit)) { "the previous stop request has not been collected yet" }
    }
}
