package audio.tembra.data.tuner

import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode

/**
 * The remembered tuner choices with the first-launch defaults of spec FR-005. [presetId] is stored verbatim and
 * validated by the ViewModel against `TuningPresets`; unknown stored strings decode to the field's default.
 */
data class TunerPreferencesSnapshot(
    val instrument: Instrument = Instrument.Guitar,
    val guitarStrings: GuitarStringCount = GuitarStringCount.Six,
    val presetId: String = "guitar6-standard",
    val mode: TunerMode = TunerMode.AutoListen,
    val loopTone: Boolean = true,
) {
    /** The five keys as strings (the boolean as `"true"`/`"false"`), for stores and tests. */
    fun encode(): Map<String, String> = mapOf(
        TunerPreferenceKeys.INSTRUMENT to instrument.id,
        TunerPreferenceKeys.GUITAR_STRINGS to guitarStrings.count.toString(),
        TunerPreferenceKeys.PRESET_ID to presetId,
        TunerPreferenceKeys.MODE to mode.id,
        TunerPreferenceKeys.LOOP_TONE to loopTone.toString(),
    )

    companion object {
        /** Builds a snapshot from stored strings and the stored boolean; absent or unknown values fall back to the defaults. */
        fun decode(values: Map<String, String?>, loopTone: Boolean?): TunerPreferencesSnapshot {
            val defaults = TunerPreferencesSnapshot()
            return TunerPreferencesSnapshot(
                instrument = Instrument.entries.firstOrNull { it.id == values[TunerPreferenceKeys.INSTRUMENT] } ?: defaults.instrument,
                guitarStrings = GuitarStringCount.entries.firstOrNull { it.count.toString() == values[TunerPreferenceKeys.GUITAR_STRINGS] }
                    ?: defaults.guitarStrings,
                presetId = values[TunerPreferenceKeys.PRESET_ID] ?: defaults.presetId,
                mode = TunerMode.entries.firstOrNull { it.id == values[TunerPreferenceKeys.MODE] } ?: defaults.mode,
                loopTone = loopTone ?: defaults.loopTone,
            )
        }
    }
}
