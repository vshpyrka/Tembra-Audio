package audio.tembra.data.tuner

/** The DataStore key names of the tuner's remembered choices (contracts/tuner-preferences.md). */
object TunerPreferenceKeys {
    const val INSTRUMENT = "tuner.instrument"
    const val GUITAR_STRINGS = "tuner.guitarStrings"
    const val PRESET_ID = "tuner.presetId"
    const val MODE = "tuner.mode"
    const val LOOP_TONE = "tuner.loopTone"

    /** The noise gate's level in decibels (feature 006); the key is absent while the gate is off. */
    const val NOISE_GATE_DB = "tuner.noiseGateDb"
}
