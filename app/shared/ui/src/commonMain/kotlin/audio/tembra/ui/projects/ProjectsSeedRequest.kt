package audio.tembra.ui.projects

/**
 * The debug launch argument that fills the library with empty projects for the SC-010 timing (`tembra.projects.seed=200`
 * on Android, `--tembra-projects-seed=200` on iOS; spec FR-062, task T121). Debug builds only; parsed like the other
 * debug requests, an unparsable or out-of-range value is ignored.
 */
data class ProjectsSeedRequest(val count: Int) {
    companion object {
        const val MAX = 1_000

        fun parse(value: String?): ProjectsSeedRequest? {
            val count = value?.trim()?.toIntOrNull() ?: return null
            return if (count in 1..MAX) ProjectsSeedRequest(count) else null
        }
    }
}
