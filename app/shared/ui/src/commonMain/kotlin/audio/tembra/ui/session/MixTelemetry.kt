package audio.tembra.ui.session

import audio.tembra.engine.MixSnapshot

/**
 * Draw-phase access to the engine's meter telemetry (feature 012, research R6; the pattern of [TransportTelemetry]):
 * [read] fills [into] from the engine's seqlock at most once per distinct [frameNanos], so every strip's meter and VU
 * dial of one frame share one read. Hot and replay-free by nature: a value is only ever what the engine says now.
 */
fun interface MixTelemetry {
    fun read(frameNanos: Long, into: MixSnapshot)
}
