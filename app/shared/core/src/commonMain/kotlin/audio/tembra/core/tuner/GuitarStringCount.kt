package audio.tembra.core.tuner

/** Six or seven strings; shown for the guitar only (spec FR-002). */
enum class GuitarStringCount(val count: Int, val label: String) {
    Six(6, "6-String"),
    Seven(7, "7-String"),
}
