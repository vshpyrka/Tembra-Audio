package audio.tembra.engine

/** Mutable mirror of `tembra_pitch_reading`, one per reader, filled by [TunerAudio.readPitch] without allocation (data-model.md §4). */
class PitchSnapshot {
    var sequence: Long = 0L
    var state: AudioState = AudioState.Idle
    var sampleRate: Int = 0
    var frequencyHz: Float = 0f
    var clarity: Float = 0f
    var inputPeak: Float = 0f
    var hasPitch: Boolean = false
    var synthetic: Boolean = false

    fun copyFrom(other: PitchSnapshot) {
        sequence = other.sequence
        state = other.state
        sampleRate = other.sampleRate
        frequencyHz = other.frequencyHz
        clarity = other.clarity
        inputPeak = other.inputPeak
        hasPitch = other.hasPitch
        synthetic = other.synthetic
    }
}
