package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * A track's gain in decibels: silence ([Silence], negative infinity) to +6 dB (spec FR-023). Serialised as the bare
 * float, `-Infinity` for silence (the document codec allows special floating-point values). Readouts use one decimal,
 * a sign and the Unicode minus: "+0.8dB", "−2.4dB", "−∞".
 */
@JvmInline
@Serializable
value class GainDb(val value: Float) : Comparable<GainDb> {
    init {
        require(!value.isNaN() && value <= MAX) { "Gain must be −∞..+$MAX dB, was $value" }
    }

    /** The linear factor the mixer applies: 10^(dB/20); 0 for silence. */
    fun toLinear(): Float = if (value == Float.NEGATIVE_INFINITY) 0f else 10f.pow(value / 20f)

    val readout: String
        get() {
            if (value == Float.NEGATIVE_INFINITY) return "$MINUS$INFINITY"
            val tenths = (abs(value) * 10f).roundToInt()
            val sign = if (value < 0f && tenths > 0) MINUS else "+"
            return "$sign${tenths / 10}.${tenths % 10}dB"
        }

    override fun compareTo(other: GainDb): Int = value.compareTo(other.value)

    companion object {
        const val MAX = 6.0f
        val Unity: GainDb = GainDb(0f)
        val Silence: GainDb = GainDb(Float.NEGATIVE_INFINITY)
        private const val MINUS = "−"
        private const val INFINITY = "∞"

        /** [value] held at or below the maximum; NaN becomes unity. Never throws. */
        fun clamped(value: Float): GainDb = if (value.isNaN()) Unity else GainDb(value.coerceAtMost(MAX))
    }
}
