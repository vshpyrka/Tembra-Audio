package audio.tembra.android

/**
 * Where [PlaybackForegroundService] is, as [AndroidBackgroundPlaybackHost] sees it. A service started with
 * `startForegroundService` must reach `startForeground` before it may be stopped, so a release that arrives while the
 * start is still queued is recorded as [ReleaseRequested] and carried out by the service right after it has entered
 * the foreground.
 */
internal enum class ForegroundState {
    /** No service, or one that is stopping. */
    Idle,

    /** `startForegroundService` was called; `onStartCommand` has not run yet. */
    Starting,

    /** The service is in the foreground; `stopService` is safe. */
    Foreground,

    /** Released while [Starting]; the service stops itself once it is in the foreground. */
    ReleaseRequested,
}
