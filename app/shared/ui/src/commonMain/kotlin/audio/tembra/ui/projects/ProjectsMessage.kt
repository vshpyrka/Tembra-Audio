package audio.tembra.ui.projects

/** One-shot snackbar messages of the library. */
sealed interface ProjectsMessage {
    data object CouldNotDuplicate : ProjectsMessage
    data object CouldNotOpen : ProjectsMessage

    val text: String
        get() = when (this) {
            CouldNotDuplicate -> ProjectsCopy.couldNotDuplicate
            CouldNotOpen -> ProjectsCopy.couldNotOpen
        }
}
