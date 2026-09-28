package audio.tembra.core.tuner

/** The twelve pitch classes, spelled with sharps everywhere the app names a note (spec FR-009). */
enum class PitchClass(val label: String) {
    C("C"),
    CSharp("C#"),
    D("D"),
    DSharp("D#"),
    E("E"),
    F("F"),
    FSharp("F#"),
    G("G"),
    GSharp("G#"),
    A("A"),
    ASharp("A#"),
    B("B"),
}
