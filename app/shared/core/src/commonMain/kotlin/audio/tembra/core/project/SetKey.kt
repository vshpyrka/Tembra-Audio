package audio.tembra.core.project

/** Sets or clears (null, "None") the project key (spec FR-052). */
data class SetKey(val key: MusicalKey?) : ProjectEdit
