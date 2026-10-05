package audio.tembra.core.tuner

import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

/**
 * The noise gate level the user set (feature 006 spec FR-001, FR-002; data-model.md §1): off, or a level on the scale.
 * [step] 0 is off; steps 1 to [STEPS] stand for [MIN_DB] + [STEP_DB] up to [MAX_DB]. The companion owns the scale,
 * construction and the reading of a stored value, so no caller handles a bare decibel number for the gate.
 */
@JvmInline
value class NoiseGate private constructor(val step: Int) {
    val isOff: Boolean get() = step == 0

    /** The level a sound must reach to be read, in dB below full scale; null when the gate is off. */
    val levelDb: Int? get() = if (step == 0) null else MIN_DB + step * STEP_DB

    /** Where the handle stands on the track: 0 at "Off", 1 at "High". */
    val fraction: Float get() = step.toFloat() / STEPS

    /** [fraction] as a whole percentage, the value a screen reader announces (spec FR-019). */
    val percent: Int get() = (fraction * 100f).roundToInt()

    companion object {
        const val MIN_DB = -80
        const val MAX_DB = -20
        const val STEP_DB = 2
        const val STEPS = (MAX_DB - MIN_DB) / STEP_DB

        val Off = NoiseGate(0)

        /** The gate levels the tests and captures use (spec, Terms): −60, −50 and −40 dB. */
        val CheckLevels: List<NoiseGate> = listOf(NoiseGate(10), NoiseGate(15), NoiseGate(20))

        /** The gate at [step], coerced into the scale; what the slider reports. */
        fun ofStep(step: Int): NoiseGate = NoiseGate(step.coerceIn(0, STEPS))

        /**
         * The gate at [db], or null when [db] is not a level of the scale ([MIN_DB] itself is where "Off" stands). A
         * value between two steps goes to the quieter one.
         */
        fun ofLevelDb(db: Int): NoiseGate? {
            if (db < MIN_DB + STEP_DB || db > MAX_DB) return null
            return NoiseGate((db - MIN_DB) / STEP_DB)
        }

        /** A remembered value (spec FR-014): absent or outside the scale is off. */
        fun fromStored(db: Int?): NoiseGate = db?.let(::ofLevelDb) ?: Off

        /** Where a measured level ends on the track, 0..1. Primitives only: the track calls it every frame. */
        fun fractionOfLevel(levelDb: Float): Float = ((levelDb - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
    }
}
