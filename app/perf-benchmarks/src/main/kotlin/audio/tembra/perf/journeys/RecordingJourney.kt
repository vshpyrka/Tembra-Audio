package audio.tembra.perf.journeys

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap
import audio.tembra.perf.journeys.Anchors.tapIfPresent
import audio.tembra.perf.journeys.Anchors.tapProjectCard

/**
 * The recording journey (feature 010, contracts/perf-instrumentation.md): open Projects and the demo, add a track
 * (selected, last), arm it, toggle monitoring twice, press Record, wait through the count-in until the clock passes
 * 00:07, press Record again (the take ends, playback continues), wait for the growing take to go, pause, undo twice
 * (the take, then the track: the document is unchanged across iterations), skip to start. The runner has granted
 * the microphone before the round (`pm grant ... RECORD_AUDIO`).
 */
class RecordingJourney {
    fun run(device: UiDevice) {
        device.tap(Anchors.NAV_PROJECTS)
        device.anchor(Anchors.PROJECTS_ROOT)
        device.tapProjectCard(Anchors.PROJECTS_CARD_DEMO)
        device.anchor(Anchors.TIMELINE_ROOT)
        check(device.wait(Until.hasObject(By.res(Anchors.TIMELINE_READY_MARKER)), READY_TIMEOUT_MS)) {
            "the timeline's overviews were not ready within $READY_TIMEOUT_MS ms"
        }
        device.tap(Anchors.TIMELINE_ADD_TRACK)
        device.tap(Anchors.trackArm(NEW_TRACK_INDEX))
        device.anchor(Anchors.trackMeter(NEW_TRACK_INDEX))
        // Monitoring on and off again; without headphones the engine refuses and the second tap is a second refusal.
        device.tapIfPresent(Anchors.TIMELINE_MONITOR)
        device.tapIfPresent(Anchors.TIMELINE_MONITOR)
        device.tapRecord(NEW_TRACK_INDEX)
        val readout = device.anchor(Anchors.TRANSPORT_READOUT)
        // One bar of count-in is about 1.9 s at 124 BPM and may pass between two readout events: not fatal.
        readout.wait(Until.descMatches("Count-in.*"), COUNT_IN_TIMEOUT_MS)
        check(readout.wait(Until.descMatches("00:0[7-9].*|00:[1-5].*"), RECORD_TIMEOUT_MS)) { "the readout never passed 00:07 while recording" }
        device.tapRecord(NEW_TRACK_INDEX)
        check(device.wait(Until.gone(By.res(Anchors.TIMELINE_CLIP_TAKE)), ADOPT_TIMEOUT_MS)) { "the take was not adopted within $ADOPT_TIMEOUT_MS ms" }
        // Undo is refused during a running or finalising take (FR-018): enabled again means the adoption has finished.
        check(device.wait(Until.findObject(By.res(Anchors.TIMELINE_UNDO).enabled(true)), ADOPT_TIMEOUT_MS) != null) { "undo was not enabled within $ADOPT_TIMEOUT_MS ms of the take ending" }
        // Disarm (refused while a take runs, so only now) so that every iteration starts disarmed.
        device.tap(Anchors.trackArm(NEW_TRACK_INDEX))
        device.tap(Anchors.TRANSPORT_PLAY)
        device.tap(Anchors.TIMELINE_UNDO)
        device.tap(Anchors.TIMELINE_UNDO)
        device.tap(Anchors.TRANSPORT_SKIP_START)
    }

    companion object {
        /** The demo has four tracks; the added one is index 4 and the last. */
        const val NEW_TRACK_INDEX = 4
        private const val READY_TIMEOUT_MS = 10_000L
        private const val COUNT_IN_TIMEOUT_MS = 5_000L
        private const val RECORD_TIMEOUT_MS = 30_000L
        private const val ADOPT_TIMEOUT_MS = 10_000L
    }
}

/** Record sits in the transport bar from 432 dp wide, else in the armed track's compact header. */
internal fun UiDevice.tapRecord(trackIndex: Int) {
    if (wait(Until.hasObject(By.res(Anchors.TRANSPORT_RECORD)), 2_000L)) {
        tap(Anchors.TRANSPORT_RECORD)
    } else {
        tapIfPresent(Anchors.trackRecord(trackIndex))
    }
}
