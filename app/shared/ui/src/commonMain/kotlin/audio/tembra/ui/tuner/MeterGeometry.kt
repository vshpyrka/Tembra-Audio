package audio.tembra.ui.tuner

import audio.tembra.core.tuner.PitchMath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The arc gauge of the design's 400 × 230 view box scaled to [width] (data-model.md §8, research R9): pivot at
 * (0.5, 0.913), outer radius 0.4 of the width, end marks at ±90°, quarter marks at ±45°, a top mark at 0°, the
 * in-tune zone of ±3 cents, a dashed circle, an inner arc and the needle. Angles are degrees from the top, positive
 * towards sharp; every method is pure so the meter test can probe exact pixels.
 */
class MeterGeometry(val width: Float) {
    private val scale = width / DESIGN_WIDTH

    val height: Float = DESIGN_HEIGHT * scale
    val pivotX: Float = width / 2f
    val pivotY: Float = 210f * scale
    val outerRadius: Float = 160f * scale
    val endMarkLength: Float = 18f * scale
    val quarterMarkLength: Float = 12f * scale
    val topMarkLength: Float = 24f * scale
    val dashedCircleRadius: Float = 115f * scale
    val innerArcRadius: Float = 90f * scale
    val needleLength: Float = 172f * scale
    val needleBackLength: Float = 2f * scale
    val pivotRingRadius: Float = 12f * scale
    val pivotDotRadius: Float = 5f * scale
    val outerStroke: Float = 4f * scale
    val endMarkStroke: Float = 2.5f * scale
    val quarterMarkStroke: Float = 2f * scale
    val topMarkStroke: Float = 3.5f * scale
    val zoneStroke: Float = 8f * scale
    val dashedStroke: Float = 1.5f * scale
    val innerArcStroke: Float = 10f * scale
    val dashedInnerStroke: Float = 5f * scale
    val needleStroke: Float = 3f * scale
    val pivotRingStroke: Float = 3f * scale
    val innerArcHalfAngle: Float = 68f
    val dashedInnerHalfAngle: Float = 36f

    /** Half the angular width of the in-tune zone: 3 cents × 1.8° per cent. */
    val inTuneHalfAngle: Float = (PitchMath.IN_TUNE_CENTS * DEGREES_PER_CENT).toFloat()

    /** Needle angle for an offset, clamped to the ±50 cent scale. */
    fun needleAngleDegrees(cents: Double): Float =
        (cents.coerceIn(-PitchMath.METER_RANGE_CENTS, PitchMath.METER_RANGE_CENTS) * DEGREES_PER_CENT).toFloat()

    /** A point at [radius] from the pivot at [angleDegrees]; 0° is straight up. */
    fun pointAt(angleDegrees: Float, radius: Float): Pair<Float, Float> {
        val radians = angleDegrees * PI / 180.0
        return Pair(pivotX + (radius * sin(radians)).toFloat(), pivotY - (radius * cos(radians)).toFloat())
    }

    fun needleTip(cents: Double): Pair<Float, Float> = pointAt(needleAngleDegrees(cents), needleLength)

    /** A point along the needle at [fraction] of its length, for pixel probes that must land on the stroke. */
    fun needlePoint(cents: Double, fraction: Float): Pair<Float, Float> = pointAt(needleAngleDegrees(cents), needleLength * fraction)

    fun endMarkStart(angleDegrees: Float): Pair<Float, Float> = pointAt(angleDegrees, outerRadius)

    fun endMarkEnd(angleDegrees: Float): Pair<Float, Float> = pointAt(angleDegrees, outerRadius + endMarkLength)

    fun quarterMark(angleDegrees: Float): Pair<Pair<Float, Float>, Pair<Float, Float>> =
        Pair(pointAt(angleDegrees, outerRadius), pointAt(angleDegrees, outerRadius + quarterMarkLength))

    fun topMark(): Pair<Pair<Float, Float>, Pair<Float, Float>> = Pair(pointAt(0f, outerRadius), pointAt(0f, outerRadius + topMarkLength))

    companion object {
        const val DESIGN_WIDTH = 400f
        const val DESIGN_HEIGHT = 230f
        const val DEGREES_PER_CENT = 1.8
        private const val SMOOTHING_MILLIS = 80.0

        /** Critically damped approach of the drawn angle to its target with an 80 ms time constant (research R8). */
        fun smoothAngle(current: Float, target: Float, dtMillis: Long): Float =
            (target + (current - target) * exp(-dtMillis / SMOOTHING_MILLIS)).toFloat()
    }
}
