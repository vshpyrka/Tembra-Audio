package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A musical position or length in beats of the time signature's unit (Constitution V: everything musical is stored in
 * beats). Plain beats, never samples; the tempo map turns them into frames at render time. Serialised as a bare
 * double (`"startBeats": 4.0`).
 */
@JvmInline
@Serializable
value class Beats(val value: Double) : Comparable<Beats> {
    operator fun plus(other: Beats): Beats = Beats(value + other.value)

    operator fun minus(other: Beats): Beats = Beats(value - other.value)

    operator fun times(factor: Double): Beats = Beats(value * factor)

    override fun compareTo(other: Beats): Int = value.compareTo(other.value)

    /** This position held at or after zero: a clip never starts before the project. A negative zero becomes [Zero] too. */
    fun coerceAtLeastZero(): Beats = if (value <= 0.0) Zero else this

    companion object {
        val Zero: Beats = Beats(0.0)
    }
}
