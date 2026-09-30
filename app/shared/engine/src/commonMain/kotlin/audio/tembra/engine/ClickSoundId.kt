package audio.tembra.engine

/** The engine's click sounds; [native] is the `tembra_click_sound` value (data-model.md §4). */
enum class ClickSoundId(val native: Int) {
    ClassicWood(0),
    Rim808(1),
    DigitalBeep(2),
    Cowbell(3),
    Mechanical(4),

    /** The imported file; needs a successful [MetronomeAudio.loadCustomSound] first. */
    Custom(5),
}
