package audio.tembra.core.tuner

/** The two instruments the tuner knows; declaration order is display order (spec FR-002). */
enum class Instrument(val id: String, val label: String, val hasStringCount: Boolean) {
    Guitar("guitar", "Guitar", true),
    Cello("cello", "Cello", false),
}
