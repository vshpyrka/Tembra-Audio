package audio.tembra.ui.session

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.TrackId
import audio.tembra.core.recording.MonitorState
import audio.tembra.core.recording.RecordingState
import audio.tembra.ui.platform.MicrophonePermissionStatus

/**
 * The recording session state the controller publishes (feature 010, data-model.md §6): the armed track, the phase,
 * the monitor toggle, the input's channels, the growing take and the permission as last read. Immutable; the meter's
 * level and the take's length never pass through here (they are read per frame from the capture telemetry).
 */
@Immutable
data class RecordingUi(
    val armedTrack: TrackId? = null,
    val phase: RecordingState = RecordingState.Disarmed,
    val monitor: MonitorState = MonitorState.Off,
    val inputChannels: Int = 0,
    val liveTake: LiveTakeUi? = null,
    val permission: MicrophonePermissionStatus = MicrophonePermissionStatus.NotDetermined,
) {
    /** True while a count-in or a take runs (FR-018). */
    val takeRunning: Boolean get() = phase.takeRunning
}
