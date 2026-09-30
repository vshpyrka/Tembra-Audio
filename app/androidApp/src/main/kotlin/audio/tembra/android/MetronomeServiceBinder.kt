package audio.tembra.android

import android.os.Binder

/**
 * The in-process binder [MetronomeForegroundService] returns, so an instrumented test can reach the running service
 * and read its foreground type. The service is not exported; nothing outside the app can bind it.
 */
internal class MetronomeServiceBinder(val service: MetronomeForegroundService) : Binder()
