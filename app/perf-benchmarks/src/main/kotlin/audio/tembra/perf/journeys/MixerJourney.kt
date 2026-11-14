package audio.tembra.perf.journeys

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap
import audio.tembra.perf.journeys.Anchors.tapProjectCard

/**
 * The mixer journey (feature 012, contracts/perf-instrumentation.md "Journey `mixer`", the same fourteen steps on both
 * platforms): open Projects, open the demo card, wait for the timeline, play; open the mixer from the toolbar (the
 * overflow menu on a compact bar) and wait for the first strip's meter; drag the first fader down by a third and back
 * over a second each way; raise the sheet to its full form where the knob is not yet shown (a phone opens at the half
 * height), turn the first knob up by 60 dp and back; solo the second strip and back; mute the third and
 * back; swipe the strip row to its end and back; drag the master down by a quarter and back; close the sheet; undo
 * until nothing is left (the document is the same across iterations); pause; skip to start.
 */
class MixerJourney {
    fun run(device: UiDevice) {
        // The sheet's meters keep the UI from reading idle for the whole journey (see `withShortIdleWaits`).
        withShortIdleWaits { runSteps(device) }
    }

    private fun runSteps(device: UiDevice) {
        device.tap(Anchors.NAV_PROJECTS)
        device.anchor(Anchors.PROJECTS_ROOT)
        device.tapProjectCard(Anchors.PROJECTS_CARD_DEMO)
        device.anchor(Anchors.TIMELINE_ROOT)
        check(device.wait(Until.hasObject(By.res(Anchors.TIMELINE_READY_MARKER)), READY_TIMEOUT_MS)) {
            "the timeline's overviews were not ready within $READY_TIMEOUT_MS ms"
        }
        // From the start: the playhead stays where the last iteration paused, and a start in the demo's last seconds is refused.
        device.tap(Anchors.TRANSPORT_SKIP_START)
        device.tap(Anchors.TRANSPORT_PLAY)
        toggleMixer(device)
        device.anchor(Anchors.MIXER_STRIP_METER_FIRST)

        val fader = device.anchor(Anchors.MIXER_STRIP_FADER_FIRST).visibleBounds
        val faderThird = fader.height() / 3
        device.swipe(fader.centerX(), fader.centerY(), fader.centerX(), fader.centerY() + faderThird, DRAG_STEPS)
        device.waitForIdle()
        device.swipe(fader.centerX(), fader.centerY() + faderThird, fader.centerX(), fader.centerY(), DRAG_STEPS)
        device.waitForIdle()

        // The knob lives in the full form: a tap on the handle raises a half sheet (skipped where the knob already shows);
        // the sheet goes back to its half height before it closes, so every iteration starts the same way.
        val raised = !device.hasObject(By.res(Anchors.MIXER_STRIP_PAN_FIRST))
        if (raised) {
            device.tap(Anchors.MIXER_HANDLE)
            device.waitForIdle()
        }
        val knob = device.anchor(Anchors.MIXER_STRIP_PAN_FIRST).visibleBounds
        val knobTurn = (KNOB_TURN_DP * device.displayMetrics().density).toInt()
        device.swipe(knob.centerX(), knob.centerY(), knob.centerX(), knob.centerY() - knobTurn, DRAG_STEPS / 2)
        device.waitForIdle()
        device.swipe(knob.centerX(), knob.centerY() - knobTurn, knob.centerX(), knob.centerY(), DRAG_STEPS / 2)
        device.waitForIdle()

        device.tap(Anchors.mixerSolo(1))
        device.tap(Anchors.mixerSolo(1))
        device.tap(Anchors.mixerMute(2))
        device.tap(Anchors.mixerMute(2))

        val strips = device.anchor(Anchors.MIXER_STRIPS)
        strips.fling(androidx.test.uiautomator.Direction.RIGHT)
        device.waitForIdle()
        strips.fling(androidx.test.uiautomator.Direction.LEFT)
        device.waitForIdle()

        val master = device.anchor(Anchors.MIXER_MASTER_FADER).visibleBounds
        val masterQuarter = master.height() / 4
        device.swipe(master.centerX(), master.centerY(), master.centerX(), master.centerY() + masterQuarter, DRAG_STEPS)
        device.waitForIdle()
        device.swipe(master.centerX(), master.centerY() + masterQuarter, master.centerX(), master.centerY(), DRAG_STEPS)
        device.waitForIdle()

        if (raised) {
            device.tap(Anchors.MIXER_HANDLE)
            device.waitForIdle()
        }
        toggleMixer(device)
        check(device.wait(Until.gone(By.res(Anchors.MIXER_SHEET)), CLOSE_TIMEOUT_MS)) { "the mixer sheet did not close" }
        undoAll(device)
        device.tap(Anchors.TRANSPORT_PLAY)
        device.tap(Anchors.TRANSPORT_SKIP_START)
    }

    /** The Mixer control sits in the toolbar on a wide bar and in the overflow menu on a compact one (spec FR-017). */
    private fun toggleMixer(device: UiDevice) {
        if (device.hasObject(By.res(Anchors.TIMELINE_MIXER))) {
            device.tap(Anchors.TIMELINE_MIXER)
        } else {
            device.tap(Anchors.TIMELINE_OVERFLOW_BUTTON)
            device.tap(Anchors.TIMELINE_OVERFLOW_MIXER)
        }
    }

    /** Undo until the button disables: the fader and knob returns are two steps each, the toggles four, the master two. */
    private fun undoAll(device: UiDevice) {
        repeat(MAX_UNDO_STEPS) {
            val undo: UiObject2 = device.findObject(By.res(Anchors.TIMELINE_UNDO)) ?: return
            if (!undo.isEnabled) return
            undo.click()
            device.waitForIdle()
        }
    }

    private fun UiDevice.displayMetrics() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics

    private companion object {
        const val READY_TIMEOUT_MS = 10_000L
        const val CLOSE_TIMEOUT_MS = 5_000L
        /** Move events of a one-second drag, 5 ms apart. */
        const val DRAG_STEPS = 200
        const val KNOB_TURN_DP = 60f
        const val MAX_UNDO_STEPS = 12
    }
}
