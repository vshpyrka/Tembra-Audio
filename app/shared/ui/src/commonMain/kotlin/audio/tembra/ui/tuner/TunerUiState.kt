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
    /** Whether the engine is reading the sound, at the 10 Hz poll; true whenever it is not listening (feature 006, data-model.md §5). */
    val gateOpen: Boolean = true,
    val audition: AuditionState? = null,
    /** Debug launch argument (data-model.md §9). */
    val testSignal: TestSignal? = null,
) {
    /** The first content is on screen: what `ReportFirstContentDrawn` reports (feature 007). */
    val isFirstContentReady: Boolean get() = loaded

    /** What the audio-state pill reads; null hides it (spec FR-006). */
    val pill: AudioStatePill?
        get() = when (audio) {
            AudioState.Listening if testSignal != null -> AudioStatePill.TestSignal
            AudioState.Listening -> AudioStatePill.MicActive
            AudioState.Tone -> AudioStatePill.ToneOut
            AudioState.Idle -> null
            // The metronome's and the transport's own states: both stop before the tuner can start, so it never shows them.
            AudioState.Metronome, AudioState.Transport -> null
        }

    /**
     * What replaces Auto Listen's meter, or null when the meter shows: the denied panel (spec FR-015) or the
     * no-microphone message (feature 011, spec FR-002). A test signal never needs the microphone.
     */
    internal val microphoneNotice: MicrophoneNotice?
        get() = when {
            mode != TunerMode.AutoListen || testSignal != null -> null
            microphone == MicrophonePermissionStatus.Unavailable -> MicrophoneNotice.Unavailable
            microphone.isDenied -> MicrophoneNotice.Denied
            else -> null
        }

    /** The meter is replaced by the denied panel (spec FR-015). */
    val showDeniedState: Boolean
        get() = microphoneNotice == MicrophoneNotice.Denied

    /** The noise gate control is shown wherever Auto Listen shows its meter (feature 006, spec FR-001). */
    val showNoiseGate: Boolean
        get() = mode == TunerMode.AutoListen && microphoneNotice == null

    /**
     * What the noise gate control announces beside its value (spec FR-019): whether the sound is above the gate, or
     * null when there is nothing to say (not listening, or no gate set). It changes when the gate opens or closes, a
     * few times a note, never per frame.
     */
    val soundAboveGate: Boolean?
        get() = if (audio == AudioState.Listening && !noiseGate.isOff) gateOpen else null

    val strings: List<TargetString>
        get() = preset.strings
}
