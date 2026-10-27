package audio.tembra.ui.session

import audio.tembra.core.project.EditRejection
import audio.tembra.core.recording.TakeStopReason

/** One-shot messages the session hands to the one screen collecting them (contracts/project-session.md "Messages"). */
sealed interface SessionMessage {
    /** Files the import refused, each with the user-facing reason. */
    data class ImportRefused(val files: List<Pair<String, String>>) : SessionMessage
    data object StartFailed : SessionMessage
    data class DocumentUnavailable(val reason: String) : SessionMessage
    data class AssetsMissing(val count: Int) : SessionMessage
    data object SaveFailed : SessionMessage
    /** The project exceeds the engine's fixed arrangement; the last arrangement keeps playing. */
    data object ProjectTooLarge : SessionMessage
    /** An edit the editor refused (an import past the track or clip limit, a move onto an occupied span). */
    data class EditRejected(val reason: EditRejection) : SessionMessage

    // Feature 010 (contracts/recording-session.md "Messages").
    /** "Microphone access is needed to record"; with [openSettings] the action opens the system settings, else it asks again. */
    data class MicrophoneDenied(val openSettings: Boolean) : SessionMessage

    /** "Connect headphones to monitor the input" (FR-017). */
    data object MonitorNeedsHeadphones : SessionMessage

    /** "Take ended: …" with the reason (FR-021, FR-025). */
    data class TakeEnded(val reason: TakeStopReason) : SessionMessage

    /** "Not enough space to record" (FR-025: below 200 MB free). */
    data object RecordRefused : SessionMessage
}
