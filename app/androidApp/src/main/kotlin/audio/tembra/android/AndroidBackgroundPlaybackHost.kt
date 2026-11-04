package audio.tembra.android

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.PlaybackNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Keeps the metronome or the project transport playing in the background on Android through
 * [PlaybackForegroundService] (contracts/background-playback.md, research R6; feature 009's rename).
 *
 * [release] never reaches the system before the service has entered the foreground: stopping a service started with
 * `startForegroundService` before its `startForeground` kills the process, and the ViewModel may release right after
 * [keepAlive] (a failed engine start, an immediate Pause). The process-wide [ForegroundState] records such a release and
 * the service carries it out once it is in the foreground.
 *
 * [release] stops the service with `stopService` rather than sending [PlaybackForegroundService.ACTION_STOP]: a
 * background `startService` throws on API 26+ once the service has gone, and the Stop action would echo back to the
 * ViewModel as a [stopRequests] item it did not ask for.
 *
 * Starting the service also asks, once per session, for the notifications permission its notification needs on
 * Android 13+ ([notifications]); playback does not wait for the answer.
 */
class AndroidBackgroundPlaybackHost(
    private val context: Context,
    private val notifications: NotificationPermissionHost? = null,
) : BackgroundPlaybackHost {

    private fun serviceIntent() = Intent(context, PlaybackForegroundService::class.java)

    override fun keepAlive(notification: PlaybackNotification): Boolean = synchronized(lock) {
        when (state) {
            // Already running: the notification follows the new payload (the metronome's tempo, a renamed project).
            ForegroundState.Foreground -> {
                rememberNotification(notification.title, notification.subtitle)
                if (notification.title == PlaybackNotification.RECORDING_TITLE && !microphoneTyped && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // A take began while playback already held the service: a second start command re-enters the
                    // foreground with the microphone type (feature 010, FR-021); the state stays Foreground throughout.
                    upgradeRunningService(notification)
                } else {
                    repostRunningNotification(context)
                }
                true
            }
            ForegroundState.Starting -> true
            // The queued start will now keep the service instead of stopping it.
            ForegroundState.ReleaseRequested -> {
                state = ForegroundState.Starting
                true
            }
            ForegroundState.Idle -> start(notification)
        }
    }

    private fun start(notification: PlaybackNotification): Boolean = try {
        context.startForegroundService(
            serviceIntent()
                .setAction(PlaybackForegroundService.ACTION_START)
                .putExtra(PlaybackForegroundService.EXTRA_TITLE, notification.title)
                .putExtra(PlaybackForegroundService.EXTRA_SUBTITLE, notification.subtitle),
        )
        state = ForegroundState.Starting
        notifications?.requestOnceForPlayback()
        true
    } catch (e: IllegalStateException) {
        // API 31+ refuses a foreground-service start while the app is not visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) false else throw e
    }

    /** The running service's start command again, with the recording payload; refused starts leave the media type in place. */
    private fun upgradeRunningService(notification: PlaybackNotification) {
        try {
            context.startForegroundService(
                serviceIntent()
                    .setAction(PlaybackForegroundService.ACTION_START)
                    .putExtra(PlaybackForegroundService.EXTRA_TITLE, notification.title)
                    .putExtra(PlaybackForegroundService.EXTRA_SUBTITLE, notification.subtitle),
            )
        } catch (e: IllegalStateException) {
            if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException)) throw e
            repostRunningNotification(context)
        }
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

        /** What the running notification shows; the service reads it when it posts, the host when it reposts. */
        @Volatile
        internal var notificationTitle: String = ""
            private set

        @Volatile
        internal var notificationSubtitle: String = ""
            private set

        /** True while the running service holds the microphone foreground type (feature 010, FR-021); set by the service. */
        @Volatile
        internal var microphoneTyped: Boolean = false

        internal fun rememberNotification(title: String, subtitle: String) {
            notificationTitle = title
            notificationSubtitle = subtitle
        }

        /**
         * Called by the service once it is in the foreground: true when a release arrived meanwhile and the service must
         * stop now (the state is then [ForegroundState.Idle]), false when it stays ([ForegroundState.Foreground]).
         */
        internal fun enteredForeground(): Boolean = synchronized(lock) {
            val released = state == ForegroundState.ReleaseRequested
            state = if (released) ForegroundState.Idle else ForegroundState.Foreground
            released
        }

        /**
         * Posts the running service's notification again, so it shows once notifications were allowed while it ran (the
         * one posted by `startForeground` stays hidden). Nothing while the service is not in the foreground.
         */
        internal fun repostRunningNotification(context: Context) {
            synchronized(lock) {
                if (state != ForegroundState.Foreground) return
                context.getSystemService(NotificationManager::class.java)
                    .notify(PlaybackForegroundService.NOTIFICATION_ID, PlaybackForegroundService.buildNotification(context))
            }
        }

        /** Called by the service when it stops on its own (the Stop action, task removal, a refused foreground start). */
        internal fun stopped() {
            synchronized(lock) {
                state = ForegroundState.Idle
                microphoneTyped = false
            }
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
                microphoneTyped = false
            }
        }
    }
}
