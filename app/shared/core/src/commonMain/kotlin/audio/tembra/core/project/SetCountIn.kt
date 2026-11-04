package audio.tembra.core.project

/**
 * Sets the project's count-in (feature 010, spec FR-009). A setting like the metronome toggle: the session applies it
 * without a history step and carries it over on undo and redo.
 */
data class SetCountIn(val countIn: CountIn) : ProjectEdit
