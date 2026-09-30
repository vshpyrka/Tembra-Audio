package audio.tembra.android

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

/**
 * The media-playback foreground service that keeps the process alive while the metronome clicks with the screen off
 * (contracts/background-playback.md, research R6). It is an OS entry point only: it holds no engine state and never
 * touches the engine. [ACTION_START] posts the ongoing notification and enters the foreground; [ACTION_STOP] (the
 * notification's Stop action) tells the ViewModel through [AndroidBackgroundPlaybackHost.stopRequests] and stops. No
 * broadcast receiver is involved: the Stop action is a service intent, and the stop request reaches the ViewModel
 * through the host's process-wide flow.
 */
class MetronomeForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> enterForeground()
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

    override fun onBind(intent: Intent?): IBinder = MetronomeServiceBinder(this)

    /**
     * Always enters the foreground first, as `startForegroundService` requires, then stops at once when the host was
     * released while this start was queued.
     */
    private fun enterForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.metronome_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, MetronomeForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_stat_metronome)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.metronome_notification_title))
            .setContentText(getString(R.string.metronome_notification_text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(icon, getString(R.string.metronome_notification_stop), stop).build())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        /** Enter the foreground with the ongoing notification. */
        const val ACTION_START = "audio.tembra.android.metronome.START"

        /** The notification's Stop action: ask the ViewModel to stop, then leave the foreground and stop. */
        const val ACTION_STOP = "audio.tembra.android.metronome.STOP"

        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 4004
    }
}
