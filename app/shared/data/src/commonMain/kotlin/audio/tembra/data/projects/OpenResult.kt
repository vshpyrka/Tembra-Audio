package audio.tembra.data.projects

import audio.tembra.core.project.ProjectId

/** What [ProjectRepository.open] answers: the project, an unavailable document with its reason, or no such project. */
sealed interface OpenResult {
    data class Opened(val opened: OpenedProject) : OpenResult

    data class Unavailable(val id: ProjectId, val reason: String) : OpenResult

    data class Missing(val id: ProjectId) : OpenResult
}
