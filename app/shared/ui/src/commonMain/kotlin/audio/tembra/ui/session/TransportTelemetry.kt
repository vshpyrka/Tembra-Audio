package audio.tembra.ui.session

import audio.tembra.engine.TransportSnapshot

/**
 * Draw-phase access to the transport position (research R7, Constitution II telemetry rule): the playhead overlay,
 * the ruler chip and the time readout call it from their draw lambdas once per frame with the frame time. The
 * session's implementation reads the engine at most once per distinct [frameNanos] and never allocates; tests feed a
 * script of snapshots.
 */
fun interface TransportTelemetry {
    /** Fills [into] with the transport state as of [frameNanos]. */
    fun read(frameNanos: Long, into: TransportSnapshot)
}
