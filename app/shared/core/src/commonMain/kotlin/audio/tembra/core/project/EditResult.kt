package audio.tembra.core.project

/** What [ProjectEditor.apply] answers: the next snapshot, or a named reason the edit was not applied. */
sealed interface EditResult {
    data class Applied(val project: Project) : EditResult

    data class Rejected(val reason: EditRejection) : EditResult
}
