package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlin.math.floor
import kotlin.math.round

/**
 * The only path from beats to time (research R3; Constitution V): one tempo and one signature for the whole project.
 * The beat is the signature's unit, as the metronome defines it, so 120 BPM in 6/8 counts 120 eighth notes a minute
 * (`secondsPerBeat = 60 / bpm` whatever the unit). Frames are at the sample rate the caller names: the asset's own
 * rate for clip offsets and lengths, the stream rate for the transport.
 */
data class TempoMap(val tempo: Tempo, val timeSignature: TimeSignature) {
    val beatsPerBar: Int get() = timeSignature.beats

    val secondsPerBeat: Double get() = 60.0 / tempo.bpm

    fun beatsToSeconds(beats: Beats): Double = beats.value * secondsPerBeat

    fun secondsToBeats(seconds: Double): Beats = Beats(seconds / secondsPerBeat)

    /** Rounded to the nearest frame at [sampleRate]. */
    fun beatsToFrames(beats: Beats, sampleRate: Int): Long = round(beats.value * secondsPerBeat * sampleRate).toLong()

    fun framesToBeats(frames: Long, sampleRate: Int): Beats = Beats(frames.toDouble() / sampleRate / secondsPerBeat)

    /** One-based bar number of a position. */
    fun barOf(beats: Beats): Int = floor(beats.value / beatsPerBar + EPSILON).toInt() + 1

    /** One-based beat inside its bar. */
    fun beatInBar(beats: Beats): Int {
        val inBar = beats.value - (barOf(beats) - 1) * beatsPerBar
        return floor(inBar + EPSILON).toInt().coerceIn(0, beatsPerBar - 1) + 1
    }

    /** The first beat of a one-based bar. */
    fun barStart(bar: Int): Beats = Beats((bar - 1).toDouble() * beatsPerBar)

    private companion object {
        /** Absorbs the last-bit error of beat arithmetic so 16.0 never lands in bar 4. */
        const val EPSILON = 1e-9
    }
}
