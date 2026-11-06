package audio.tembra.ui.designsystem

import androidx.compose.runtime.Immutable
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.timeline.LevelHold

/**
 * A meter's level as the 10 Hz mirror reports it for the semantics (feature 012, research R6; data-model.md §4's
 * `StripLevel`): the louder side's peak on the meter's −60…0 dB scale, the louder side's rms in dBFS for the VU dial,
 * and the clip latch. Never read in the draw phase, which takes the live snapshot instead.
 */
@Immutable
internal data class MeterLevel(
    val peakDb: Float,
    val rmsDb: Float,
    val clipped: Boolean,
) {
    companion object {
        val Silent = MeterLevel(peakDb = LevelHold.FLOOR_DB, rmsDb = MixSnapshot.SILENCE_DB, clipped = false)
    }
}
