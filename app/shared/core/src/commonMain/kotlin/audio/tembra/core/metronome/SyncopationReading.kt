package audio.tembra.core.metronome

/** Debug-only readings of the Syncopated style, so the alternatives can be heard before one is fixed (spec FR-037). */
enum class SyncopationReading(val id: String) {
    EvenBeats("even-beats"),
    OffBeatTicks("off-beat-ticks"),
    AllButOne("all-but-one"),
}
