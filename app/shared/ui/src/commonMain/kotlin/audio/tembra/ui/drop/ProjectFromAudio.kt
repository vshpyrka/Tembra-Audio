package audio.tembra.ui.drop

import audio.tembra.core.project.Beats
import audio.tembra.data.projects.PickedFile
import audio.tembra.data.projects.ProjectRepository
import audio.tembra.ui.session.ImportTarget
import audio.tembra.ui.session.ProjectSession
import kotlinx.coroutines.flow.first

/**
 * A new project from audio files (feature 011, spec FR-026, FR-026a): files dropped on Projects or opened with the app
 * become "Project N" with one track per file from the start, and it is opened. [skipped] joins the import's message.
 */
fun interface ProjectFromAudio {
    /** False when the new project could not be opened (nothing is imported then). */
    suspend fun start(files: List<PickedFile>, skipped: List<SkippedItem>): Boolean
}

/**
 * [ProjectFromAudio] over the library and the session, as Projects' "New project" makes one (spec FR-052 of 009).
 * [beforeStart] runs first: the app passes its startup's wait, so files opened at launch are not replaced by the
 * startup reopening the last project.
 */
class SessionProjectFromAudio(
    private val repository: ProjectRepository,
    private val session: ProjectSession,
    private val beforeStart: suspend () -> Unit = {},
) : ProjectFromAudio {
    override suspend fun start(files: List<PickedFile>, skipped: List<SkippedItem>): Boolean {
        beforeStart()
        val project = repository.create(repository.library().first().nextProjectName())
        session.open(project.id)
        if (session.state.value.project?.id != project.id) return false
        session.importAudio(files, ImportTarget.NewTracks(Beats.Zero), skipped)
        return true
    }
}
