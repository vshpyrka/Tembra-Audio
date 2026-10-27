package audio.tembra.android

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

/**
 * The media-playback foreground service that keeps the process alive while audio plays with the screen off: the
 * metronome's click (feature 004, contracts/background-playback.md, research R6) and the project transport
 * (feature 009, contracts/timeline-audio-kotlin.md). It is an OS entry point only: it holds no engine state and never
 * touches the engine. [ACTION_START] posts the ongoing notification (its title and subtitle from the
 * [EXTRA_TITLE] and [EXTRA_SUBTITLE] extras: "Metronome" / the tempo, "Playing" / the project name, "Recording" / the
 * project name while a take runs, feature 010) and enters the foreground; [ACTION_STOP] (the notification's Stop action) tells the owner through
 * [AndroidBackgroundPlaybackHost.stopRequests] and stops. No broadcast receiver is involved: the Stop action is a
 * service intent, and the stop request reaches the owner through the host's process-wide flow.
 */
class PlaybackForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                AndroidBackgroundPlaybackHost.rememberNotification(
                    title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.playback_notification_title),
                    subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "",
                )
                enterForeground()
            }
            ACTION_STOP -> {
                AndroidBackgroundPlaybackHost.stopSignals.tryEmit(Unit)
                AndroidBackgroundPlaybackHost.stopped()
                stop()
            }
            else -> stop()
        }
        return START_NOT_STICKY
    }

    /** Swiping the app away ends the session; the engine stops with the activity's ViewModel or the process. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AndroidBackgroundPlaybackHost.stopped()
        stop()
    }

    /** However the service ends, the host must not keep believing it is in the foreground. */
    override fun onDestroy() {
        AndroidBackgroundPlaybackHost.serviceDestroyed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = PlaybackServiceBinder(this)

    /**
     * Always enters the foreground first, as `startForegroundService` requires, then stops at once when the host was
     * released while this start was queued.
     */
    private fun enterForeground() {
        val notification = buildNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Feature 010 (FR-021): the microphone type keeps a take recording with the screen off; declared in the
                // manifest and always started from the visible Timeline, as Android 14's while-in-use rule requires.
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: IllegalStateException) {
            // API 31+: the system may refuse the foreground start after the app left the screen; give up quietly.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                AndroidBackgroundPlaybackHost.stopped()
                stopSelf()
                return
            }
            throw e
        }
        if (AndroidBackgroundPlaybackHost.enteredForeground()) stop()
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification = buildNotification(this)

    companion object {
        /** Enter the foreground with the ongoing notification whose text the [EXTRA_TITLE] and [EXTRA_SUBTITLE] extras carry. */
        const val ACTION_START = "audio.tembra.android.playback.START"

        /** The notification's Stop action: ask the owner to stop, then leave the foreground and stop. */
        const val ACTION_STOP = "audio.tembra.android.playback.STOP"

        const val EXTRA_TITLE = "audio.tembra.android.playback.TITLE"
        const val EXTRA_SUBTITLE = "audio.tembra.android.playback.SUBTITLE"

        private const val CHANNEL_ID = "playback"
        internal const val NOTIFICATION_ID = 4004

        /**
         * The ongoing notification with its Stop action, titled by what the host last remembered ("Metronome" / the
         * tempo, "Playing" / the project name); also re-posted once notifications are allowed or the payload changes.
         */
        internal fun buildNotification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.playback_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val stop = PendingIntent.getService(
                context,
                0,
                Intent(context, PlaybackForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val icon = Icon.createWithResource(context, R.drawable.ic_stat_metronome)
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(AndroidBackgroundPlaybackHost.notificationTitle.ifEmpty { context.getString(R.string.playback_notification_title) })
                .setContentText(AndroidBackgroundPlaybackHost.notificationSubtitle)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(icon, context.getString(R.string.playback_notification_stop), stop).build())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()
        }
    }
}
