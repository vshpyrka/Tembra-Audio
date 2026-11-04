package audio.tembra.ui.drop

import audio.tembra.data.projects.PickedFile

/**
 * One thing dropped or opened with the app (feature 011, spec FR-026, FR-026a), as a platform reader saw it: an audio
 * file the importer can read, a folder, or something else, named so the skipped message can list it.
 */
sealed interface DroppedItem {
    data class AudioFile(val file: PickedFile) : DroppedItem

    /** A folder and its direct children; null [contents] when the platform does not let the app read it. */
    data class Folder(val name: String, val contents: List<DroppedItem>?) : DroppedItem

    data class Other(val name: String) : DroppedItem
}
