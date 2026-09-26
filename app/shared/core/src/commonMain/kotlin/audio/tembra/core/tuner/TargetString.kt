package audio.tembra.core.tuner

/** One string of a preset: what By Ear sounds (spec Key Entities). */
data class TargetString(val numeral: String, val note: Note) {
    val frequencyHz: Double
        get() = note.frequencyHz()
}
