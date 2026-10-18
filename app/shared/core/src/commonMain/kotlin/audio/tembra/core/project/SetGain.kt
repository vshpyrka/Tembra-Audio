package audio.tembra.core.project

/** Sets a track's gain (spec FR-023); the session applies it through the engine's track fast path. */
data class SetGain(val id: TrackId, val gainDb: GainDb) : ProjectEdit
