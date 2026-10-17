package audio.tembra.perf.journeys

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * The perf anchors as UiAutomator sees them: every `testTag` of `audio.tembra.ui.perf.PerfAnchors` is a view
 * resource id (the app root sets `testTagsAsResourceId`), so `By.res(tag)` finds it. The values mirror
 * `PerfAnchors` exactly; a tag that changes there changes here.
 */
object Anchors {
    const val START_ROOT = "screen.timeline"
    const val NAV_TUNER = "nav.item.tuner"
    const val NAV_METRONOME = "nav.item.metronome"
    const val TUNER_ROOT = "screen.tuner"
    const val TUNER_MODE_AUTO_LISTEN = "tuner.mode.autoListen"
    const val TUNER_MODE_BY_EAR = "tuner.mode.byEar"
    const val TUNER_NOTE_READOUT = "tuner.meter"
    const val METRONOME_ROOT = "screen.metronome"
    const val METRONOME_START_STOP = "metronome.play"
    const val METRONOME_TEMPO_UP = "metronome.dial.up"
    const val METRONOME_TEMPO_DOWN = "metronome.dial.down"
    const val METRONOME_TEMPO_READOUT = "metronome.tempoReadout"
    const val DEVTOOLS_WRITE_HEALTH = "devtools.writeHealth"

    /** Default wait for an anchor to appear, generous for a cold process on a cool phone. */
    const val TIMEOUT_MS = 10_000L

    fun selector(tag: String): BySelector = By.res(tag)

    /** Wait for the anchor, failing the journey loudly when it never appears (spec US1 scenario 4). */
    fun UiDevice.anchor(tag: String, timeoutMs: Long = TIMEOUT_MS): UiObject2 =
        wait(Until.findObject(selector(tag)), timeoutMs)
            ?: throw AssertionError("perf anchor '$tag' not found within $timeoutMs ms")

    fun UiDevice.tap(tag: String) {
        anchor(tag).click()
        waitForIdle()
    }

    /** Tap an anchor the build may not have (devtools in the release baseline); a short wait, never a failure. */
    fun UiDevice.tapIfPresent(tag: String, timeoutMs: Long = 2_000L) {
        val target = wait(Until.findObject(selector(tag)), timeoutMs) ?: return
        target.click()
        waitForIdle()
    }
}
