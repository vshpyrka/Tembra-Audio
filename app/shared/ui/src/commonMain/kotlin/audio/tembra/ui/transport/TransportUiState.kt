package audio.tembra.ui.transport

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.timeline.TimeFormat

/**
 * What the transport bar renders (contracts/timeline-screen.md): the playback state, the project length, the master
 * volume, the metronome toggle, and the 10 Hz position for semantics. The per-frame readout and playhead read the
 * telemetry in draw lambdas instead (Constitution II); [positionSeconds] only refreshes the accessible text.
 */
@Immutable
data class TransportUiState(
    val playback: PlaybackState = PlaybackState.Stopped(audio.tembra.core.project.Beats.Zero),
    /** The 10 Hz mirror of the audible position, for the readout's semantics and tests. */
    val positionSeconds: Double = 0.0,
    val lengthSeconds: Double = 0.0,
    /** 0–100. */
    val masterVolume: Int = 80,
    val metronomeOn: Boolean = false,
    /** False while no project is open: every control but the master volume is disabled. */
    val hasProject: Boolean = false,
    // Feature 010 (contracts/timeline-recording-ui.md): Record, the count-in and the seeking controls.
    /** The Record control's state; [RecordButtonState.Hidden] where the bar is too narrow for it (the content decides). */
    val record: RecordButtonState = RecordButtonState.Hidden,
    /** Beats of a running count-in still to sound, down to 1; -1 when none runs (the readout shows "Count-in N"). */
    val countInBeatsLeft: Int = -1,
    /** False while a take runs: skip-to-start and the step buttons are unavailable (spec FR-009). */
    val seekEnabled: Boolean = true,
) {
    val isPlaying: Boolean get() = playback is PlaybackState.Playing || playback is PlaybackState.Starting

    val isCountingIn: Boolean get() = countInBeatsLeft >= 0

    /** "00:00.00 / 00:30.96", or "Count-in 4 / 00:30.96" during the count-in. */
    val readoutText: String get() = "${positionText} / ${TimeFormat.clock(lengthSeconds)}"

    /** The position half of the readout: the clock, or "Count-in N" while the count-in runs (spec FR-015). */
    val positionText: String get() = if (isCountingIn) TransportCopy.countIn(countInBeatsLeft) else TimeFormat.clock(positionSeconds)

    /** "Record", "Recording" or "Count-in, N beats left" (spec FR-029). */
    val recordLabel: String
        get() = when (record) {
            RecordButtonState.Hidden, RecordButtonState.Idle -> TransportCopy.record
            RecordButtonState.CountIn -> TransportCopy.countInBeatsLeft(countInBeatsLeft)
            RecordButtonState.Recording -> TransportCopy.recording
        }

    val playLabel: String get() = if (isPlaying) TransportCopy.pause else TransportCopy.play

    val metronomeLabel: String get() = if (metronomeOn) TransportCopy.metronomeOff else TransportCopy.metronomeOn

    val transportEnabled: Boolean get() = hasProject
}
