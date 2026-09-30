package audio.tembra.core.metronome

/** The levels of every tick in one bar; tick 0 is beat 1 and `levels.size == beats * ticksPerBeat`. */
data class TickGrid(val ticksPerBeat: Int, val levels: List<BeatLevel>) {
    val beats: Int get() = levels.size / ticksPerBeat

    companion object {
        /**
         * Lays the [pattern] over [subdivision]. A [reading] other than
         * [SyncopationReading.EvenBeats] applies only while [style] is [MetronomeStyle.Syncopated].
         */
        fun from(
            signature: TimeSignature,
            subdivision: Subdivision,
            pattern: AccentPattern,
            style: MetronomeStyle?,
            reading: SyncopationReading = SyncopationReading.EvenBeats,
        ): TickGrid {
            val ticksPerBeat = subdivision.ticksPerBeat
            val effective = if (style == MetronomeStyle.Syncopated) reading else SyncopationReading.EvenBeats
            val levels = List(signature.beats * ticksPerBeat) { tick ->
                val beat = tick / ticksPerBeat
                val onBeat = tick % ticksPerBeat == 0
                when (effective) {
                    SyncopationReading.EvenBeats ->
                        if (onBeat) pattern.levels[beat] else BeatLevel.Sub
                    SyncopationReading.OffBeatTicks ->
                        if (ticksPerBeat == 1) pattern.levels[beat]
                        else if (onBeat) BeatLevel.Sub else BeatLevel.Accent
                    SyncopationReading.AllButOne ->
                        if (onBeat) (if (beat == 0) BeatLevel.Sub else BeatLevel.Accent) else BeatLevel.Click
                }
            }
            return TickGrid(ticksPerBeat, levels)
        }
    }
}
