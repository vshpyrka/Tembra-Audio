package audio.tembra.android

import android.os.Binder

/**
 * The in-process binder [PlaybackForegroundService] returns, so an instrumented test can reach the running service
 * and read its foreground type. The service is not exported; nothing outside the app can bind it.
 */
internal class PlaybackServiceBinder(val service: PlaybackForegroundService) : Binder()
