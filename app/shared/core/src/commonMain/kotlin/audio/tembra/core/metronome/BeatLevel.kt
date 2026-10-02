package audio.tembra.core.metronome

/** How strongly one beat sounds; subdivision ticks always sound at [Sub] (spec FR-008, FR-009). */
enum class BeatLevel(val id: Char, val label: String) {
    Accent('A', "Accent"),
    Click('C', "Click"),
    Sub('S', "Sub");

    /** The level a tap on the cell selects: Accent, then Click, then Sub, then back to Accent. */
    fun next(): BeatLevel = when (this) {
        Accent -> Click
        Click -> Sub
        Sub -> Accent
    }

    companion object {
        fun byId(id: Char): BeatLevel? = entries.firstOrNull { it.id == id }
    }
}
