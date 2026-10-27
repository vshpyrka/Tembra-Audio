package audio.tembra.core.timeline

import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TempoMap
import audio.tembra.core.metronome.Tempo
import kotlin.math.floor

/**
 * The readout and chip formats (spec FR-009, FR-010): "mm:ss.hh" with the hundredths truncated, never rounded
 * (30.9677 s is "00:30.96"), and "bar.beat" for the playhead chip. Pure, locale-free, tabular-friendly.
 */
object TimeFormat {

    /** "mm:ss" plus [decimals] (0, 1 or 2) truncated fraction digits; negative seconds read as zero. */
    fun clock(seconds: Double, decimals: Int = 2): String {
        require(decimals in 0..2) { "decimals must be 0..2, was $decimals" }
        val clamped = seconds.coerceAtLeast(0.0)
        val scale = when (decimals) {
            0 -> 1L
            1 -> 10L
            else -> 100L
        }
        val units = floor(clamped * scale + TRUNCATION_SLACK).toLong()
        val wholeSeconds = units / scale
        val fraction = units % scale
        val minutes = wholeSeconds / 60
        val secondsInMinute = wholeSeconds % 60
        val base = "${pad(minutes)}:${pad(secondsInMinute)}"
        return when (decimals) {
            0 -> base
            1 -> "$base.$fraction"
            else -> "$base.${pad(fraction)}"
        }
    }

    /** "5.1": the one-based bar and beat of a position in [signature]. */
    fun barBeat(beats: Beats, signature: TimeSignature): String {
        val map = TempoMap(Tempo.Default, signature)
        return "${map.barOf(beats)}.${map.beatInBar(beats)}"
    }

    /** "00:00.00 / 00:42.20". */
    fun readout(positionSeconds: Double, lengthSeconds: Double): String = "${clock(positionSeconds)} / ${clock(lengthSeconds)}"

    private fun pad(value: Long): String = value.toString().padStart(2, '0')

    /** Absorbs the representation error of a product like 0.1 × 7 so a value that is exactly a hundredth stays one. */
    private const val TRUNCATION_SLACK = 1e-6
}
