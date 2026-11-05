package audio.tembra.ui.designsystem

import audio.tembra.core.project.FaderTaper
import audio.tembra.core.project.FaderTick
import audio.tembra.core.project.GainDb
import kotlin.math.roundToInt

/**
 * The value domain of a [Fader] (feature 012, research R3): how a value maps to the throw (0 at the bottom, 1 at the
 * top) and back, where the handle returns on a double tap or Home ([unity]), the key and wheel [step], the Page [page],
 * the resolution a drag settles on ([quantize]) and the scale marks. The semantics work in this domain, never in throw.
 */
internal sealed interface FaderScale {
    val range: ClosedFloatingPointRange<Float>
    val unity: Float
    val step: Float
    val page: Float
    val ticks: List<FaderTick>

    fun throwOf(value: Float): Float

    fun valueOf(throwPosition: Float): Float

    fun quantize(value: Float): Float

    /** Gain in decibels through the console taper: −60 (silence) … +6, unity at 0 dB, 0.5 dB steps, 6 dB pages, tenths. */
    data object Gain : FaderScale {
        /** The bottom of the range stands for silence (`GainDb.Silence`), as `GainReadout` and `GainSlider` read it. */
        const val SILENCE_DB = -60f

        override val range: ClosedFloatingPointRange<Float> = SILENCE_DB..GainDb.MAX
        override val unity: Float = 0f
        override val step: Float = 0.5f
        override val page: Float = 6f
        override val ticks: List<FaderTick> = FaderTaper.ticks

        override fun throwOf(value: Float): Float = FaderTaper.throwOf(gainOf(value))

        override fun valueOf(throwPosition: Float): Float = valueOf(FaderTaper.dbOf(throwPosition))

        override fun quantize(value: Float): Float = (value * 10f).roundToInt() / 10f

        /** The gain for a fader value: silence at or under −60 dB, else the clamped decibels. */
        fun gainOf(value: Float): GainDb = if (value <= SILENCE_DB) GainDb.Silence else GainDb.clamped(value)

        /** The fader value for a gain: −60 for silence. */
        fun valueOf(gain: GainDb): Float = if (gain == GainDb.Silence) SILENCE_DB else gain.value.coerceIn(SILENCE_DB, GainDb.MAX)
    }

    /** Whole percent 0 … 100 along a linear throw (the master volume): home at feature 009's default 80 %, 1 % steps, 10 % pages. */
    data object Percent : FaderScale {
        /** Feature 009's default master volume (its spec FR-007): the master fader's home. */
        const val HOME_PERCENT = 80

        override val range: ClosedFloatingPointRange<Float> = 0f..100f
        override val unity: Float = HOME_PERCENT.toFloat()
        override val step: Float = 1f
        override val page: Float = 10f
        override val ticks: List<FaderTick> = listOf(100, 80, 60, 40, 20, 0).map { FaderTick(it / 100f, it.toString()) }

        override fun throwOf(value: Float): Float = value.coerceIn(range) / 100f

        override fun valueOf(throwPosition: Float): Float = throwPosition.coerceIn(0f, 1f) * 100f

        override fun quantize(value: Float): Float = value.roundToInt().toFloat()
    }
}
