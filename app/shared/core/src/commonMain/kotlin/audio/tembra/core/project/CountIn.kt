package audio.tembra.core.project

/**
 * The count-in before a take (feature 010, spec FR-009; data-model.md section 1): none, one bar or two, a setting of
 * the project. [bars] is what the engine's click scheduler counts before the take starts.
 */
enum class CountIn(val bars: Int) {
    OFF(0),
    ONE_BAR(1),
    TWO_BARS(2),
}
