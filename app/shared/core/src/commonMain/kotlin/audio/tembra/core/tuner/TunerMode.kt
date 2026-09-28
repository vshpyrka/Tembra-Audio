package audio.tembra.core.tuner

/** Auto Listen owns the microphone, By Ear owns the reference tone; exactly one is active (spec Key Entities). */
enum class TunerMode(val id: String, val label: String) {
    AutoListen("autoListen", "Auto Listen"),
    ByEar("byEar", "By Ear"),
}
