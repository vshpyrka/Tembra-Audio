package audio.tembra.ui.metronome.components

import audio.tembra.core.metronome.Tempo
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * The tempo dial's ring as pure numbers (data-model.md §9, research R8), in fractions of the square dial box: a track
 * from 12 o'clock clockwise over [SWEEP] degrees, the fill proportional to the tempo, and eight tick marks.
 */
internal object TempoDialGeometry {
    /** Ring radius: 96 of the design's 240 viewBox. */
    const val RING_RADIUS = 0.4f

    /** The track's length in degrees, clockwise from the top (the SVG's dash `460 140` on its 603-unit circle). */
    const val SWEEP = 275f

    /** Where the track starts, in the draw scope's convention (0° = 3 o'clock). */
    const val START_ANGLE = -90f

    /** Tick marks from 98 to 106 of the 240 viewBox. */
    const val TICK_INNER = 0.408f
    const val TICK_OUTER = 0.442f

    /** A touch counts as on the ring within this distance of it, in fractions of the box. */
    const val RING_BAND = 0.1f

    /** Eight tick marks every 45°, clockwise from the top. */
    val tickAngles: List<Float> = List(8) { it * 45f }

    private const val SPAN = Tempo.MAX - Tempo.MIN

    /** The fill's sweep for [tempo], in degrees clockwise from the top. */
    fun sweepFor(tempo: Tempo): Float = (tempo.bpm - Tempo.MIN).toFloat() / SPAN * SWEEP

    /** The whole-number tempo at [angleFromTop] degrees; angles outside the track clamp to its ends. */
    fun tempoAt(angleFromTop: Float): Tempo =
        Tempo(Tempo.MIN + (angleFromTop.coerceIn(0f, SWEEP) / SWEEP * SPAN).roundToInt())

    /**
     * The clockwise angle from the top of the offset ([dx], [dy]) from the centre (y grows downwards). Angles in the
     * gap after the track are split at its middle: the half nearer the start comes back negative, so it clamps to the
     * slowest tempo rather than jumping to the fastest.
     */
    fun angleFromTop(dx: Float, dy: Float): Float {
        val degrees = (atan2(dx, -dy) * 180f / PI.toFloat() + 360f) % 360f
        val gapMiddle = SWEEP + (360f - SWEEP) / 2
        return if (degrees > gapMiddle) degrees - 360f else degrees
    }
}
