package audio.tembra.ui.timeline

import audio.tembra.core.project.EditRejection
import audio.tembra.ui.drop.DropCopy
import audio.tembra.ui.drop.SkippedItem

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
    /** A drop held no audio file (feature 011, spec FR-026); [skipped] names what it held. */
    data class NoAudioFound(val skipped: List<SkippedItem>) : TimelineMessage
    /** The sheet offers the sounds the Metronome holds; importing a new one is the Metronome's job. */
    data object CustomSoundFromMetronome : TimelineMessage

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
            is NoAudioFound -> DropCopy.noAudio(skipped)
            CustomSoundFromMetronome -> TimelineCopy.customSoundFromMetronome
        }
}
