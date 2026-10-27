package audio.tembra.ui.timeline

import audio.tembra.core.project.EditRejection
import audio.tembra.core.recording.TakeStopReason

/** One-shot messages the timeline shows in its snackbar (contracts/project-session.md "Messages", spec FR-044, FR-005). */
sealed interface TimelineMessage {
    /** "<n> file(s) could not be added: <name> (<reason>)". */
    data class ImportRefused(val refusals: List<Pair<String, String>>) : TimelineMessage
    data object StartFailed : TimelineMessage
    data class DocumentUnavailable(val reason: String) : TimelineMessage
    data class AssetsMissing(val count: Int) : TimelineMessage
    data object SaveFailed : TimelineMessage
    /** The engine's arrangement bounds were exceeded; the last arrangement keeps playing. */
    data object ProjectTooLarge : TimelineMessage
    /** The editor refused an edit for a reason the user can act on (spec FR-033, FR-043). */
    data class EditRejected(val reason: EditRejection) : TimelineMessage
    /** The platform picker could not hand over the chosen file. */
    data object PickFailed : TimelineMessage
    /** The sheet offers the sounds the Metronome holds; importing a new one is the Metronome's job. */
    data object CustomSoundFromMetronome : TimelineMessage

    // Feature 010 (contracts/recording-session.md "Messages").
    /** "Microphone access is needed to record", with the "Open settings" action (or "Allow" where the prompt can show again). */
    data class MicrophoneDenied(val openSettings: Boolean) : TimelineMessage
    /** "Connect headphones to monitor the input" (FR-017). */
    data object MonitorNeedsHeadphones : TimelineMessage
    /** "Not enough space to record" (FR-025). */
    data object RecordRefused : TimelineMessage
    /** "Take ended: …" (FR-021, FR-025). */
    data class TakeEnded(val reason: TakeStopReason) : TimelineMessage

    val text: String
        get() = when (this) {
            is ImportRefused -> TimelineCopy.filesRefused(refusals)
            StartFailed -> TimelineCopy.couldNotStart
            is DocumentUnavailable -> "This project cannot be opened: $reason"
            is AssetsMissing -> if (count == 1) "1 audio file is missing" else "$count audio files are missing"
            SaveFailed -> "The project could not be saved"
            ProjectTooLarge -> "The project has more tracks or clips than can play at once"
            is EditRejected -> TimelineCopy.rejected(reason)
            PickFailed -> TimelineCopy.pickFailed
            CustomSoundFromMetronome -> TimelineCopy.customSoundFromMetronome
            is MicrophoneDenied -> TimelineCopy.microphoneNeeded
            MonitorNeedsHeadphones -> TimelineCopy.monitorNeedsHeadphones
            RecordRefused -> TimelineCopy.recordRefused
            is TakeEnded -> TimelineCopy.takeEnded(reason)
        }

    /** The snackbar's action label, or null for messages without one. */
    val action: String?
        get() = when (this) {
            is MicrophoneDenied -> if (openSettings) TimelineCopy.openSettings else TimelineCopy.allowMicrophone
            is ImportRefused, StartFailed, is DocumentUnavailable, is AssetsMissing, SaveFailed, ProjectTooLarge, is EditRejected, PickFailed,
            CustomSoundFromMetronome, MonitorNeedsHeadphones, RecordRefused, is TakeEnded -> null
        }
}
