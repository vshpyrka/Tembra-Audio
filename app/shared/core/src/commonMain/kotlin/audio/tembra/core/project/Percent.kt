package audio.tembra.core.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/** A whole percentage 0–100: the master volume (spec FR-007). Serialised as the bare integer. */
@JvmInline
@Serializable
value class Percent(val value: Int) {
    init {
        require(value in 0..100) { "Percent must be 0..100, was $value" }
    }

    /** 0..1 for the mixer. */
    fun toLinear(): Float = value / 100f

    val readout: String get() = "$value %"

    companion object {
        /** [value] held inside 0..100; never throws. */
        fun clamped(value: Int): Percent = Percent(value.coerceIn(0, 100))
    }
}
