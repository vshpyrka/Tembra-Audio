package audio.tembra.ui.projects

import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.DemoAudioSource
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.PickedFile
import audio.tembra.data.projects.ProjectRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Builds the stress project once per process (feature 012, contracts/perf-instrumentation.md "Journey `stress`"): after
 * [beforeStart] (the app's startup has opened its project), a project under [StressProject.ID] with
 * [StressProjectRequest.trackCount] tracks, each one of the demo's bundled loops in turn, handed to [importInto] as
 * picked files, so they take the import path a chosen file takes (one track per file, at the start). A launch that
 * finds the project in the library builds nothing. One launch on the session's scope.
 */
class StressProjectSeeder(
    private val repository: ProjectRepository,
    private val demoAudio: DemoAudioSource,
    private val scope: CoroutineScope,
    private val beforeStart: suspend () -> Unit = {},
    private val importInto: suspend (ProjectId, List<PickedFile>) -> Unit,
) {
    private var job: Job? = null

    fun seed(request: StressProjectRequest) {
        if (job != null) return
        job = scope.launch(CoroutineName("seedStressProject")) {
            try {
                beforeStart()
                if (repository.library().first().entry(StressProject.ID) != null) return@launch
                val project = repository.create(StressProject.name(request.trackCount), StressProject.ID)
                val loops = DemoProject.LOOPS
                val files = List(request.trackCount) { index ->
                    val loop = loops[index % loops.size]
                    PickedFile(loop.fileName) { demoAudio.open(loop.name) }
                }
                importInto(project.id, files)
            } catch (failure: Exception) {
                job = null // a debug build that failed (I/O) may be asked again; the app itself is unaffected
            }
        }
    }
}
