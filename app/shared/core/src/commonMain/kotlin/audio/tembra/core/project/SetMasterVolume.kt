package audio.tembra.core.project

/** Sets the master volume (spec FR-007), remembered per project. */
data class SetMasterVolume(val volume: Percent) : ProjectEdit
