package audio.tembra.ui.session

import audio.tembra.engine.CaptureSnapshot

/**
 * Draw-phase access to the engine's capture telemetry (feature 010, research R8; the pattern of [TransportTelemetry]):
 * [read] fills [into] from the engine's seqlock at most once per distinct [frameNanos], so the meter and the growing
 * clip of one frame share one read. Hot and replay-free by nature: a value is only ever what the engine says now.
 */
fun interface InputTelemetry {
    fun read(frameNanos: Long, into: CaptureSnapshot)
}
