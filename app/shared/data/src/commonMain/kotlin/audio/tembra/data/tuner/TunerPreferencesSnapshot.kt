package audio.tembra.data.tuner

import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.core.tuner.TunerMode

/**
 * The remembered tuner choices with the first-launch defaults of spec FR-005. [presetId] is stored verbatim and
 * validated by the ViewModel against `TuningPresets`; unknown stored strings decode to the field's default. The
 * [noiseGate] of Auto Listen (feature 006, spec FR-014) is off until the user sets it.
 */
data class TunerPreferencesSnapshot(
    val instrument: Instrument = Instrument.Guitar,
    val guitarStrings: GuitarStringCount = GuitarStringCount.Six,
    val presetId: String = "guitar6-standard",
    val mode: TunerMode = TunerMode.AutoListen,
    val loopTone: Boolean = true,
    val noiseGate: NoiseGate = NoiseGate.Off,
) {
    /**
     * The keys as strings (the boolean as `"true"`/`"false"`), for stores and tests: the five of feature 003 always,
     * and the noise gate's level only while a level is set.
     */
    fun encode(): Map<String, String> = buildMap {
        put(TunerPreferenceKeys.INSTRUMENT, instrument.id)
        put(TunerPreferenceKeys.GUITAR_STRINGS, guitarStrings.count.toString())
        put(TunerPreferenceKeys.PRESET_ID, presetId)
        put(TunerPreferenceKeys.MODE, mode.id)
        put(TunerPreferenceKeys.LOOP_TONE, loopTone.toString())
        noiseGate.levelDb?.let { put(TunerPreferenceKeys.NOISE_GATE_DB, it.toString()) }
    }

    companion object {
        /**
         * Builds a snapshot from stored strings, the stored boolean and the stored gate level; absent or unknown values
         * fall back to the defaults, and [NoiseGate.fromStored] reads the level (off when absent or off the scale).
         */
        fun decode(values: Map<String, String?>, loopTone: Boolean?, noiseGateDb: Int?): TunerPreferencesSnapshot {
            val defaults = TunerPreferencesSnapshot()
            return TunerPreferencesSnapshot(
                instrument = Instrument.entries.firstOrNull { it.id == values[TunerPreferenceKeys.INSTRUMENT] } ?: defaults.instrument,
                guitarStrings = GuitarStringCount.entries.firstOrNull { it.count.toString() == values[TunerPreferenceKeys.GUITAR_STRINGS] }
                    ?: defaults.guitarStrings,
                presetId = values[TunerPreferenceKeys.PRESET_ID] ?: defaults.presetId,
                mode = TunerMode.entries.firstOrNull { it.id == values[TunerPreferenceKeys.MODE] } ?: defaults.mode,
                loopTone = loopTone ?: defaults.loopTone,
                noiseGate = NoiseGate.fromStored(noiseGateDb),
            )
        }
    }
}
