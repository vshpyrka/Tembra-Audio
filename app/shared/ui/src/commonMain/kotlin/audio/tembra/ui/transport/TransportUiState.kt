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
) {
    val isPlaying: Boolean get() = playback is PlaybackState.Playing || playback is PlaybackState.Starting

    /** "00:00.00 / 00:30.96". */
    val readoutText: String get() = "${TimeFormat.clock(positionSeconds)} / ${TimeFormat.clock(lengthSeconds)}"

    val playLabel: String get() = if (isPlaying) TransportCopy.pause else TransportCopy.play

    val metronomeLabel: String get() = if (metronomeOn) TransportCopy.metronomeOff else TransportCopy.metronomeOn

    val transportEnabled: Boolean get() = hasProject
}
