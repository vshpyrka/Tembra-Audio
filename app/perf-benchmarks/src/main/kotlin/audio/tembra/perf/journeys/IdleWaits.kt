package audio.tembra.perf.journeys

import androidx.test.uiautomator.Configurator

/** UiAutomator's idle and acknowledgment waits while the mixer sheet's meters keep the UI busy. */
internal const val SHORT_IDLE_WAIT_MS = 500L

/**
 * Runs [block] with UiAutomator's wait-for-idle and action-acknowledgment timeouts at [millis], then restores the
 * previous values. The mixer sheet's meters update their semantics ten times a second while it is open, so UiAutomator
 * never sees the UI idle: each click's acknowledgment and each `waitForIdle` would otherwise run to the default 10 s
 * (the 2026-11-09 trial round's mixer journey took 75 s an iteration and timed out at 18 of 20; contract
 * `perf-instrumentation.md`, "Journey `mixer`").
 */
internal inline fun <T> withShortIdleWaits(millis: Long = SHORT_IDLE_WAIT_MS, block: () -> T): T {
    val configurator = Configurator.getInstance()
    val idleBefore = configurator.waitForIdleTimeout
    val ackBefore = configurator.actionAcknowledgmentTimeout
    configurator.setWaitForIdleTimeout(millis)
    configurator.setActionAcknowledgmentTimeout(millis)
    try {
        return block()
    } finally {
        configurator.setWaitForIdleTimeout(idleBefore)
        configurator.setActionAcknowledgmentTimeout(ackBefore)
    }
}
