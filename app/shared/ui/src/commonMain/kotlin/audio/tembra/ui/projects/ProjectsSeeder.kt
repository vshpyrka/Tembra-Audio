package audio.tembra.ui.projects

import audio.tembra.data.projects.ProjectRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fills the library with [ProjectsSeedRequest.count] empty projects once per process (T121): "Seed project N", each
 * a plain document with no audio, so a 200-project library can be timed. One `seedProjects` launch on the session's scope.
 */
@Inject
@SingleIn(AppScope::class)
class ProjectsSeeder(
    private val repository: ProjectRepository,
    @Named("session") private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun seed(request: ProjectsSeedRequest) {
        if (job != null) return
        job = scope.launch(CoroutineName("seedProjects")) {
            try {
                for (index in 1..request.count) repository.create("Seed project $index")
            } catch (failure: Exception) {
                job = null // a debug seed that failed (I/O) may be asked for again; the app itself is unaffected
            }
        }
    }
}
