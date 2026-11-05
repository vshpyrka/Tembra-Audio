package audio.tembra.ui.tuner

/** Every user-visible string of the tuner (contracts/tuner-screen.md), asserted by the tests. */
internal object TunerCopy {
    const val micActive = "Mic active"
    const val toneOut = "Tone out"
    const val testSignal = "Test signal"
    const val inTune = "In tune"
    const val listening = "Listening"
    const val reference = "Reference"
    const val flat = "Flat"
    const val sharp = "Sharp"
    const val tuningPreset = "Tuning Preset"
    const val loopTone = "Loop Tone"
    const val active = "Active"
    const val off = "Off"
    const val audition = "Audition"
    const val playing = "Playing"
    const val stop = "Stop"
    const val tunerMeter = "Tuner meter"
    const val deniedTitle = "Microphone needed"
    // Feature 011 (spec FR-002): a desktop or tablet with no microphone at all.
    const val unavailableTitle = "No microphone"
    const val unavailableText = "No microphone is available on this device. Connect one to use Auto Listen; By Ear plays every string's reference tone."
    const val deniedText = "Tembra Audio listens to your instrument to show its tuning. Allow the microphone to use Auto Listen."
    const val deniedButton = "Allow microphone"
    const val hertz = "Hz"
    const val tunerControls = "Tuner controls"
    const val noiseGate = "Noise gate"
    const val gateHigh = "High"
    const val soundAboveGate = "sound above the gate"
    const val soundBelowGate = "sound below the gate"

    /** The announced place of the noise gate's handle along its track (spec FR-019). */
    fun gatePercent(percent: Int): String = "$percent percent"
}
