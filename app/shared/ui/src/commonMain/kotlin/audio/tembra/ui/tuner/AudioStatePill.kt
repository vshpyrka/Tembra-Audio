package audio.tembra.ui.tuner

/** The three states of the audio-state pill; [label] is what accessibility hears, the pill draws it in capitals (spec FR-006). */
enum class AudioStatePill(val label: String) {
    MicActive(TunerCopy.micActive),
    ToneOut(TunerCopy.toneOut),
    TestSignal(TunerCopy.testSignal),
}
