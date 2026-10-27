package audio.tembra.core.project

/** Renames the project (spec FR-052): trimmed, 1–60 characters; empty is rejected. */
data class RenameProject(val name: String) : ProjectEdit
