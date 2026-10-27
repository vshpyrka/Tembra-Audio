package audio.tembra.ui.session

import audio.tembra.core.project.EditRejection

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
}
