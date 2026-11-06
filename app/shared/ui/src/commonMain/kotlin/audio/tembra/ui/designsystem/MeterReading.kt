package audio.tembra.ui.designsystem

import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.session.InputTelemetry

/**
 * Where a [LevelMeter] takes its per-frame levels (feature 012, research R6; spec FR-012, FR-014): a strip's post-fader
 * output or the master's from the sheet's shared [MixSnapshot], which the sheet root fills once per frame, or the armed
 * track's input from feature 010's capture telemetry. Called in the draw phase: no allocation, no snapshot writes.
 */
internal fun interface MeterReading {
    fun read(frameNanos: Long, into: MeterSample)

    companion object {
        /** Strip [index]'s post-fader peaks and latch from [levels]; [MixSnapshot.MASTER_INDEX] reads the master's. */
        fun playback(levels: MixSnapshot, index: Int): MeterReading = MeterReading { _, into ->
            if (index == MixSnapshot.MASTER_INDEX) {
                into.left = levels.masterPeak[0]
                into.right = levels.masterPeak[1]
                into.clipped = levels.masterClipped
            } else {
                into.left = levels.peak[index * 2]
                into.right = levels.peak[index * 2 + 1]
                into.clipped = levels.clipped(index)
            }
        }

        /** The armed track's input (feature 010's capture snapshot) through [telemetry], read once per frame into [capture]. */
        fun input(telemetry: InputTelemetry, capture: CaptureSnapshot): MeterReading = MeterReading { frameNanos, into ->
            telemetry.read(frameNanos, capture)
            into.left = capture.peak[0]
            into.right = capture.peak[1]
            into.clipped = capture.clipped
        }
    }
}
