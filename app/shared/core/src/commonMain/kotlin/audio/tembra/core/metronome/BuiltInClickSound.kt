package audio.tembra.core.metronome

/** The built-in click sounds in list order (spec FR-018, FR-021). */
enum class BuiltInClickSound(val id: String, val label: String) {
    ClassicWood("classic-wood", "Classic Wood"),
    Rim808("808-rim", "808 Rim"),
    DigitalBeep("digital-beep", "Digital Beep"),
    Cowbell("cowbell", "Cowbell"),
    Mechanical("mechanical", "Mechanical"),
}
