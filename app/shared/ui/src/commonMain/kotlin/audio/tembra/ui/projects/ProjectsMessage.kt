package audio.tembra.ui.projects

import audio.tembra.ui.drop.DropCopy
import audio.tembra.ui.drop.SkippedItem

/** One-shot snackbar messages of the library. */
sealed interface ProjectsMessage {
    data object CouldNotDuplicate : ProjectsMessage
    data object CouldNotOpen : ProjectsMessage

    /** A drop held no audio file (feature 011, spec FR-026); [skipped] names what it held. */
    data class NoAudioFound(val skipped: List<SkippedItem>) : ProjectsMessage

    val text: String
        get() = when (this) {
            CouldNotDuplicate -> ProjectsCopy.couldNotDuplicate
            CouldNotOpen -> ProjectsCopy.couldNotOpen
            is NoAudioFound -> DropCopy.noAudio(skipped)
        }
}
