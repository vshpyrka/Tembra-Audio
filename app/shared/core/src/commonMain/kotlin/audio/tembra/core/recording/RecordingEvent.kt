package audio.tembra.core.recording

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId

/**
 * What reaches the recording policy (data-model.md §2): the user's intents (arm, disarm, record, stop, cancel, the
 * monitor toggle), the engine's answers (the input opened or failed, the pre-roll ticks, the take started, ended or
 * was finalised), the permission, the routes, an interruption, and the session's own signals (a track deleted, the
 * project closing, the host hidden, the transport closed). [Arm] carries the permission status the controller read
 * because the policy is pure.
 */
sealed interface RecordingEvent {
    data class Arm(val trackId: TrackId, val permission: PermissionStatus) : RecordingEvent

    data object Disarm : RecordingEvent

    data class InputOpened(val channels: Int, val sampleRate: Int) : RecordingEvent

    data object InputFailed : RecordingEvent

    /** Record pressed: [countInBeats] (0 = none, or while [playing]) and the staging [takeId] the controller chose. */
    data class Record(val takeId: String, val countInBeats: Int, val playing: Boolean) : RecordingEvent

    data object Stop : RecordingEvent

    data object Cancel : RecordingEvent

    data class PreRollTick(val beatsLeft: Int) : RecordingEvent

    data class TakeStarted(val startBeats: Beats, val since: Long) : RecordingEvent

    /** The engine ended the take itself. */
    data class TakeEnded(val reason: TakeStopReason) : RecordingEvent

    data object TakeFinalised : RecordingEvent

    data object PermissionDenied : RecordingEvent

    data object PermissionRevoked : RecordingEvent

    data object InputChanged : RecordingEvent

    data class OutputChanged(val isSpeaker: Boolean) : RecordingEvent

    data object Interrupted : RecordingEvent

    data class TrackDeleted(val trackId: TrackId) : RecordingEvent

    data object ProjectClosing : RecordingEvent

    data object HostHidden : RecordingEvent

    data object TransportClosed : RecordingEvent

    /** The headphones toggle, with whether the output is the speaker right now. */
    data class MonitorToggle(val isSpeaker: Boolean) : RecordingEvent

    /** The microphone permission as the controller read it when the user asked to arm. */
    enum class PermissionStatus {
        Granted,
        NotDetermined,
        DeniedCanAsk,
        DeniedOpenSettings,
    }
}
