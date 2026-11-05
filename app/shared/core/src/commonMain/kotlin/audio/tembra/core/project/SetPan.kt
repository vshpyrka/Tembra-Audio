package audio.tembra.core.project

/** Sets a track's pan (feature 012, spec FR-006 to FR-008); the session applies it through the engine's lock-free pan path. */
data class SetPan(val id: TrackId, val pan: Pan) : ProjectEdit
