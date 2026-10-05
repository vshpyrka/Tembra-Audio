package audio.tembra.ui.tuner

import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.core.tuner.TargetString
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPreset
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.engine.AudioState
import audio.tembra.engine.TestSignal
import audio.tembra.ui.platform.MicrophonePermissionStatus

/** The one immutable state of the Tuner destination (data-model.md §7). */
data class TunerUiState(
    /** Preferences read; the screen shows nothing audio-related before. */
    val loaded: Boolean = false,
    val instrument: Instrument = Instrument.Guitar,
    val guitarStrings: GuitarStringCount = GuitarStringCount.Six,
    val preset: TuningPreset = TuningPresets.default(Instrument.Guitar, GuitarStringCount.Six),
    /** The presets of the current instrument and string count. */
    val presets: List<TuningPreset> = TuningPresets.forInstrument(Instrument.Guitar, GuitarStringCount.Six),
    val mode: TunerMode = TunerMode.AutoListen,
    val loopTone: Boolean = true,
    /** The noise gate of Auto Listen (feature 006, data-model.md §5); off until the user sets it. */
    val noiseGate: NoiseGate = NoiseGate.Off,
    val microphone: MicrophonePermissionStatus = MicrophonePermissionStatus.NotDetermined,
    val audio: AudioState = AudioState.Idle,
    val interrupted: Boolean = false,
    /** 10 Hz sample; null is the resting state. */
    val reading: MeterReading? = null,
    val audition: AuditionState? = null,
    /** Debug launch argument (data-model.md §9). */
    val testSignal: TestSignal? = null,
) {
    /** What the audio-state pill reads; null hides it (spec FR-006). */
    val pill: AudioStatePill?
        get() = when (audio) {
            AudioState.Listening if testSignal != null -> AudioStatePill.TestSignal
            AudioState.Listening -> AudioStatePill.MicActive
            AudioState.Tone -> AudioStatePill.ToneOut
            AudioState.Idle -> null
            // The metronome's own state: it stops before the tuner can start, so the tuner never shows it.
            AudioState.Metronome -> null
        }

    /** The meter is replaced by the denied panel (spec FR-015); a test signal never needs the microphone. */
    val showDeniedState: Boolean
        get() = mode == TunerMode.AutoListen && microphone.isDenied && testSignal == null

    /** The noise gate control is shown wherever Auto Listen shows its meter (feature 006, spec FR-001). */
    val showNoiseGate: Boolean
        get() = mode == TunerMode.AutoListen && !showDeniedState

    val strings: List<TargetString>
        get() = preset.strings
}
