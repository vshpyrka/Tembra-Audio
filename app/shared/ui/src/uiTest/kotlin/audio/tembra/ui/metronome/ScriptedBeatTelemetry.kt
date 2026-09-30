package audio.tembra.ui.metronome

/** Telemetry the UI tests script (the model is the tuner's `ScriptedTelemetry`): every read returns [lit] and is counted. */
internal class ScriptedBeatTelemetry(litBeat: Int = -1) : BeatTelemetry {
    /** The beat the next reads report; -1 lights nothing. */
    var lit: Int = litBeat

    /** How many times the cells asked. */
    var calls: Int = 0
        private set

    override fun litBeat(frameNanos: Long): Int {
        calls++
        return lit
    }
}
