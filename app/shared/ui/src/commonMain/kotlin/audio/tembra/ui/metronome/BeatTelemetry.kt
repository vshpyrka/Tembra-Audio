package audio.tembra.ui.metronome

/**
 * Draw-phase access to the beat light (research R7, Constitution II telemetry rule): the beat cells call it from
 * `drawBehind` once per frame with the frame time. The ViewModel's implementation reads the engine at most once per
 * distinct [frameNanos] and never allocates.
 */
fun interface BeatTelemetry {
    /** The 0-based beat whose click is audible now, or -1 when none is. */
    fun litBeat(frameNanos: Long): Int
}
