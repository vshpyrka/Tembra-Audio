package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlinx.serialization.Serializable

/**
 * A track's pan position as a whole percentage per side (feature 012, spec FR-006 to FR-008): −100 (full left)
 * through 0 (the centre) to +100 (full right). Serialised as the bare integer. Like a track's name, the range is a
 * rule of `ProjectEditor` (rejecting) and `ProjectValidator` (reporting), not of the constructor, so a document with a
 * bad pan can still be reported; [clamped] is the constructor for live input. Readouts: "C", "L 35", "R 20".
 */
@JvmInline
@Serializable
value class Pan(val percent: Int) {

    /** True when the value is one the document may hold. */
    val inRange: Boolean get() = abs(percent) <= MAX

    /** "C" at the centre, otherwise the side and the percentage: "L 35", "R 20". */
    val readout: String
        get() = when {
            percent == 0 -> CENTRE
            percent < 0 -> "L ${-percent}"
            else -> "R $percent"
        }

    /** The centre when the value is within [within] percent of it, else the value itself (spec FR-008). */
    fun snapToCentre(within: Int = SNAP_PERCENT): Pan = if (abs(percent) <= within) Centre else this

    /** −1 (left) … 1 (right) for the engine, held inside the range. */
    fun toLinear(): Float = percent.coerceIn(-MAX, MAX) / MAX.toFloat()

    companion object {
        const val MAX = 100

        /** A release this close to the centre settles on it (spec FR-008). */
        const val SNAP_PERCENT = 5
        private const val CENTRE = "C"
        val Centre: Pan = Pan(0)

        /** [percent] held inside −100..100; never throws. */
        fun clamped(percent: Int): Pan = Pan(percent.coerceIn(-MAX, MAX))
    }
}
