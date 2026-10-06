package audio.tembra.engine

/** Mutable mirror of `tembra_pitch_reading`, one per reader, filled by [TunerAudio.readPitch] without allocation (data-model.md §4). */
class PitchSnapshot {
    var sequence: Long = 0L
    var state: AudioState = AudioState.Idle
    var sampleRate: Int = 0
    var frequencyHz: Float = 0f
    var clarity: Float = 0f
    var inputPeak: Float = 0f

    /** The level the noise gate judges: the RMS of the analysis window after the pre-filter, in dB below full scale. */
    var inputLevelDb: Float = -120f

    /** False while the noise gate holds the sound back; true with no gate, an open gate, and when not listening. */
    var gateOpen: Boolean = true
    var hasPitch: Boolean = false
    var synthetic: Boolean = false

    fun copyFrom(other: PitchSnapshot) {
        sequence = other.sequence
        state = other.state
        sampleRate = other.sampleRate
        frequencyHz = other.frequencyHz
        clarity = other.clarity
        inputPeak = other.inputPeak
        inputLevelDb = other.inputLevelDb
        gateOpen = other.gateOpen
        hasPitch = other.hasPitch
        synthetic = other.synthetic
    }
}
