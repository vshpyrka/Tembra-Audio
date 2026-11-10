package audio.tembra.ui.projects

/**
 * The launch argument that builds the stress project of feature 012's performance contract (`tembra.stress.tracks=24`
 * on Android, `--tembra-stress-tracks=24` on iOS; contracts/perf-instrumentation.md "Journey `stress`"): a project of
 * [trackCount] tracks, each the demo's loops in turn, imported through the same path a picked file takes. Debug and
 * measurement builds only; parsed like the other debug requests, an unparsable or out-of-range value is ignored.
 */
data class StressProjectRequest(val trackCount: Int) {
    companion object {
        /** The engine mixes at most this many strips (`TEMBRA_MAX_TRACKS`). */
        const val MAX = 64

        fun parse(value: String?): StressProjectRequest? {
            val count = value?.trim()?.toIntOrNull() ?: return null
            return if (count in 1..MAX) StressProjectRequest(count) else null
        }
    }
}
