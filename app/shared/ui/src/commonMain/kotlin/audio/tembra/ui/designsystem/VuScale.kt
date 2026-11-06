package audio.tembra.ui.designsystem

import audio.tembra.engine.MixSnapshot
import kotlin.math.max
import kotlin.math.round

/**
 * The VU dial's scale (feature 012, spec FR-013; research R6): −20 to +3 VU, 0 VU at the reference level of −18 dBFS,
 * the needle from the louder side's 300 ms rms as the engine publishes it.
 */
internal object VuScale {
    const val REFERENCE_DBFS = -18f
    const val MIN_VU = -20f
    const val MAX_VU = 3f

    /** The reading for an rms level in dBFS, pinned at the scale's ends. */
    fun vuOf(rmsDb: Float): Float = (rmsDb - REFERENCE_DBFS).coerceIn(MIN_VU, MAX_VU)

    /** Where [vu] sits along the dial: 0 at −20 VU, 1 at +3 VU. */
    fun fractionOf(vu: Float): Float = ((vu - MIN_VU) / (MAX_VU - MIN_VU)).coerceIn(0f, 1f)

    /** The louder side's rms in dBFS for strip [index], the master's for [MixSnapshot.MASTER_INDEX]. */
    fun rmsDbOf(levels: MixSnapshot, index: Int): Float =
        if (index == MixSnapshot.MASTER_INDEX) max(levels.masterRmsDb(0), levels.masterRmsDb(1)) else max(levels.rmsDb(index, 0), levels.rmsDb(index, 1))

    /** "0 VU", "−3 VU", "+2 VU" with the typographic minus. */
    fun announce(vu: Float): String {
        val rounded = round(vu).toInt()
        return when {
            rounded < 0 -> "−${-rounded} VU"
            rounded > 0 -> "+$rounded VU"
            else -> "0 VU"
        }
    }
}
