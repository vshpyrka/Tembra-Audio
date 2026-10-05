package audio.tembra.ui.tuner

import audio.tembra.core.tuner.NoiseGate

/**
 * The tuner's debug launch argument that sets the noise gate for one launch (feature 006, data-model.md §8), so the
 * control can be captured in a known state without a touch. Only debug builds parse it (Android extra
 * `tembra.tuner.noiseGate`, iOS argument `--tembra-tuner-noise-gate=`); it reaches the screen through
 * [LocalTunerNoiseGate]. Like the metronome's request, it is a type of this module, so the platform shells hand it on
 * without seeing the domain type inside.
 */
data class TunerNoiseGateRequest(val gate: NoiseGate) {
    companion object {
        private const val OFF = "off"

        /**
         * `"off"`, or a whole decibel number on the gate's scale such as `"-50"` (a number between two steps goes to
         * the quieter one); any other value, or none, is ignored.
         */
        fun parse(value: String?): TunerNoiseGateRequest? = when (value) {
            null -> null
            OFF -> TunerNoiseGateRequest(NoiseGate.Off)
            else -> value.toIntOrNull()?.let(NoiseGate::ofLevelDb)?.let(::TunerNoiseGateRequest)
        }
    }
}
