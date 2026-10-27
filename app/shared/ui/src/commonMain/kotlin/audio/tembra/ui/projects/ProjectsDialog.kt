package audio.tembra.ui.projects

import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.ProjectId

/** The one dialog the library may show (spec FR-052); [error] is the validation message after a refused confirm. */
sealed interface ProjectsDialog {
    val projectId: ProjectId

    data class Rename(override val projectId: ProjectId, val name: String, val error: String? = null) : ProjectsDialog
    data class Describe(override val projectId: ProjectId, val description: String, val error: String? = null) : ProjectsDialog
    data class Key(override val projectId: ProjectId, val key: MusicalKey?) : ProjectsDialog
    data class Delete(override val projectId: ProjectId, val name: String) : ProjectsDialog
}
