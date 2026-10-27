package audio.tembra.core.project

/** Sets the one-line description (spec FR-052): trimmed, up to 120 characters, may be empty. */
data class SetDescription(val description: String) : ProjectEdit
