package audio.tembra.core.metronome

/** The remembered metronome state (spec FR-026). */
data class MetronomeSettings(
    val tempo: Tempo = Tempo.Default,
    val signature: TimeSignature = TimeSignature.Default,
    val subdivision: Subdivision = Subdivision.Default,
    val pattern: AccentPattern = MetronomeStyle.Standard.pattern(TimeSignature.Default),
    /** Null when the pattern was edited by hand and matches no style. */
    val style: MetronomeStyle? = MetronomeStyle.Standard,
    val sound: ClickSound = ClickSound.BuiltIn(BuiltInClickSound.ClassicWood),
    /** Null when no file has been imported. */
    val customSound: CustomSoundRef? = null,
)
