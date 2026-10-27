package audio.tembra.core.project

/**
 * The name and description rules of spec FR-003, FR-020 and FR-052, owned in one place so the editor (rejecting),
 * the validator (reporting) and the UI (field limits) agree: project names 1–60 characters trimmed, descriptions up
 * to 120, track names 1–40 with an empty name reverting to "Track N".
 */
object ProjectNames {
    const val MAX_PROJECT_NAME = 60
    const val MAX_DESCRIPTION = 120
    const val MAX_TRACK_NAME = 40

    fun isValidProjectName(name: String): Boolean = name.trim().length in 1..MAX_PROJECT_NAME

    fun isValidDescription(description: String): Boolean = description.trim().length <= MAX_DESCRIPTION

    fun isValidTrackName(name: String): Boolean = name.trim().length in 1..MAX_TRACK_NAME

    /** [name] trimmed, or "Track N" past the highest number among [otherTrackNames] when it is blank. */
    fun trackNameOrDefault(name: String, otherTrackNames: Iterable<String>): String {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) Track.nextName(otherTrackNames) else trimmed
    }
}
