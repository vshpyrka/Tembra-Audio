package audio.tembra.ui.platform

import audio.tembra.data.projects.PickedFile

/** What [AudioFilePicker.pickOne] or [AudioFilePicker.pickMany] produced. */
sealed interface AudioPickResult {
    /** The chosen files in picker order; each opens its bytes on demand while the platform grant lasts. */
    data class Picked(val files: List<PickedFile>) : AudioPickResult

    /** The user dismissed the picker. */
    data object Cancelled : AudioPickResult

    /** The platform could not present the picker or hand over the choice. */
    data class Failed(val reason: AudioPickFailure) : AudioPickResult
}
