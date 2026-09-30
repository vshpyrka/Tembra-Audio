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
 * [release] stops the service with `stopService` rather than sending [MetronomeForegroundService.ACTION_STOP]: a
 * background `startService` throws on API 26+ once the service has gone, and the Stop action would echo back to the
 * ViewModel as a [stopRequests] item it did not ask for.
 */
class AndroidBackgroundPlaybackHost(private val context: Context) : BackgroundPlaybackHost {

    private fun serviceIntent() = Intent(context, MetronomeForegroundService::class.java)

    override fun keepAlive(): Boolean = try {
        context.startForegroundService(serviceIntent().setAction(MetronomeForegroundService.ACTION_START))
        true
    } catch (e: IllegalStateException) {
        // API 31+ refuses a foreground-service start while the app is not visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) false else throw e
    }

    override fun release() {
        context.stopService(serviceIntent())
    }

    /** The notification's Stop action; items emitted while nobody collects are dropped (the metronome is not running then). */
    override val stopRequests: Flow<Unit> = stopSignals.asSharedFlow()

    internal companion object {
        /**
         * Process-wide, because the service is created by the OS and cannot be handed the graph's host instance. No
         * replay: a stop request only means something to a metronome that is running and collecting.
         */
        internal val stopSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }
}
