package audio.tembra.core.project

/** Sets or clears (null) a track's instrument label (spec FR-021). */
data class SetLabel(val id: TrackId, val label: InstrumentLabel?) : ProjectEdit
