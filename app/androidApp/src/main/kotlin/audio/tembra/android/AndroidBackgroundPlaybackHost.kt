package audio.tembra.android

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import audio.tembra.ui.platform.BackgroundPlaybackHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Keeps the metronome playing in the background on Android through [MetronomeForegroundService]
 * (contracts/background-playback.md, research R6).
 *
 * [release] never reaches the system before the service has entered the foreground: stopping a service started with
 * `startForegroundService` before its `startForeground` kills the process, and the ViewModel may release right after
 * [keepAlive] (a failed engine start, an immediate Pause). The process-wide [ForegroundState] records such a release and
 * the service carries it out once it is in the foreground.
 *
 * [release] stops the service with `stopService` rather than sending [MetronomeForegroundService.ACTION_STOP]: a
 * background `startService` throws on API 26+ once the service has gone, and the Stop action would echo back to the
 * ViewModel as a [stopRequests] item it did not ask for.
 */
class AndroidBackgroundPlaybackHost(private val context: Context) : BackgroundPlaybackHost {

    private fun serviceIntent() = Intent(context, MetronomeForegroundService::class.java)

    override fun keepAlive(): Boolean = synchronized(lock) {
        when (state) {
            ForegroundState.Foreground, ForegroundState.Starting -> true
            // The queued start will now keep the service instead of stopping it.
            ForegroundState.ReleaseRequested -> {
                state = ForegroundState.Starting
                true
            }
            ForegroundState.Idle -> start()
        }
    }

    private fun start(): Boolean = try {
        context.startForegroundService(serviceIntent().setAction(MetronomeForegroundService.ACTION_START))
        state = ForegroundState.Starting
        true
    } catch (e: IllegalStateException) {
        // API 31+ refuses a foreground-service start while the app is not visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) false else throw e
    }

    override fun release() {
        synchronized(lock) {
            when (state) {
                ForegroundState.Foreground -> {
                    state = ForegroundState.Idle
                    context.stopService(serviceIntent())
                }
                ForegroundState.Starting -> state = ForegroundState.ReleaseRequested
                ForegroundState.Idle, ForegroundState.ReleaseRequested -> Unit
            }
        }
    }

    /** The notification's Stop action; items emitted while nobody collects are dropped (the metronome is not running then). */
    override val stopRequests: Flow<Unit> = stopSignals.asSharedFlow()

    internal companion object {
        /**
         * Process-wide, because the service is created by the OS and cannot be handed the graph's host instance. No
         * replay: a stop request only means something to a metronome that is running and collecting.
         */
        internal val stopSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        private val lock = Any()

        /** Guarded by [lock]; process-wide for the same reason as [stopSignals]. */
        private var state = ForegroundState.Idle

        /**
         * Called by the service once it is in the foreground: true when a release arrived meanwhile and the service must
         * stop now (the state is then [ForegroundState.Idle]), false when it stays ([ForegroundState.Foreground]).
         */
        internal fun enteredForeground(): Boolean = synchronized(lock) {
            val released = state == ForegroundState.ReleaseRequested
            state = if (released) ForegroundState.Idle else ForegroundState.Foreground
            released
        }

        /** Called by the service when it stops on its own (the Stop action, task removal, a refused foreground start). */
        internal fun stopped() {
            synchronized(lock) { state = ForegroundState.Idle }
        }

        /**
         * Called from the service's `onDestroy`, which also covers a stop the host did not ask for (the system, or a
         * direct `stopService`): a [ForegroundState.Foreground] state becomes [ForegroundState.Idle] so the next
         * [keepAlive] starts a new service. [ForegroundState.Starting] and [ForegroundState.ReleaseRequested] belong to
         * a start that is already queued behind this destroy, so they are kept.
         */
        internal fun serviceDestroyed() {
            synchronized(lock) {
                if (state == ForegroundState.Foreground) state = ForegroundState.Idle
            }
        }
    }
}
